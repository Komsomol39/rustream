package com.komsomol.rustream.data.grab

import android.content.Context
import android.util.Log
import com.komsomol.rustream.data.torrent.TorrentEngine
import com.komsomol.rustream.domain.model.GrabDownload
import com.komsomol.rustream.domain.model.GrabResult
import com.komsomol.rustream.domain.model.GrabState
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.komsomol.rustream.domain.model.GrabFormat
import com.komsomol.rustream.domain.model.FormatQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrabRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: TorrentEngine
) {
    private val TAG = "GrabRepo"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _downloads = MutableStateFlow<Map<String, GrabDownload>>(emptyMap())
    val downloads: StateFlow<Map<String, GrabDownload>> = _downloads.asStateFlow()

    // id загрузок, отменённых пользователем: kill процесса yt-dlp бросает
    // исключение в execute(), и по этому набору мы отличаем отмену от ошибки
    private val cancelled = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private val _formatQuery = MutableStateFlow<FormatQuery?>(null)
    val formatQuery: StateFlow<FormatQuery?> = _formatQuery.asStateFlow()

    // ---------- Поиск: NewPipe (быстрый, все сервисы параллельно) ----------

    @Volatile private var newpipeReady = false
    private fun ensureNewPipe() {
        if (newpipeReady) return
        synchronized(this) {
            if (newpipeReady) return
            NewPipe.init(NewPipeDownloaderImpl(client),
                Localization("ru", "RU"), ContentCountry("RU"))
            newpipeReady = true
        }
    }

    suspend fun search(query: String): List<GrabResult> = withContext(Dispatchers.IO) {
        ensureNewPipe()
        val out = java.util.Collections.synchronizedList(mutableListOf<GrabResult>())
        coroutineScope {
            ServiceList.all().map { service ->
                async {
                    try {
                        val info = SearchInfo.getInfo(service,
                            service.searchQHFactory.fromQuery(query))
                        info.relatedItems.filterIsInstance<StreamInfoItem>()
                            .take(10).forEach { item ->
                                out.add(GrabResult(
                                    serviceId   = service.serviceId,
                                    serviceName = service.serviceInfo.name,
                                    url         = item.url ?: return@forEach,
                                    title       = item.name ?: "Без названия",
                                    uploader    = item.uploaderName,
                                    durationSec = item.duration,
                                    thumbnailUrl = item.thumbnails
                                        ?.maxByOrNull { it.width }?.url
                                ))
                            }
                    } catch (e: Exception) {
                        Log.w(TAG, service.serviceInfo.name + " search failed: " + e.message)
                    }
                }
            }.awaitAll()
        }
        out.toList()
    }

    // ---------- Скачивание: yt-dlp + ffmpeg (надёжно, HLS, лучшее качество) ----------

    @Volatile private var ytdlReady = false
    private fun ensureYtdl() {
        if (ytdlReady) return
        synchronized(this) {
            if (ytdlReady) return
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            ytdlReady = true
        }
    }

    fun startDownload(result: GrabResult, video: Boolean) {
        val dlId = result.url + (if (video) "#v" else "#a")
        setDl(GrabDownload(dlId, result.title, video, 0f, GrabState.RESOLVING))
        scope.launch {
            try {
                ensureYtdl()
                setDl(GrabDownload(dlId, result.title, video, 0f, GrabState.DOWNLOADING))
                // Пробуем наборы YouTube-клиентов по очереди: если один упирается
                // в SABR/битые URL — берём следующий. Для не-YouTube хватит первого.
                val clientSets = listOf(
                    "tv,web_safari",
                    "ios,mweb",
                    "web,android",
                    "tv_embedded"
                )
                var lastErr: Exception? = null
                for ((idx, clients) in clientSets.withIndex()) {
                    try {
                        runYtDlp(result, video, dlId, clients)
                        setDl(GrabDownload(dlId, result.title, video, 1f, GrabState.DONE))
                        return@launch
                    } catch (e: Exception) {
                        lastErr = e
                        Log.w(TAG, "client set " + clients + " failed: " + e.message)
                        if (idx < clientSets.size - 1) {
                            setDl(GrabDownload(dlId, result.title, video, 0f,
                                GrabState.DOWNLOADING, "Пробую другой способ..."))
                        }
                    }
                }
                rawLog(result.title, lastErr ?: Exception("unknown"))
                setDl(GrabDownload(dlId, result.title, video, 0f,
                    GrabState.ERROR, humanError(lastErr ?: Exception("не удалось"))))
            } catch (e: Exception) {
                Log.e(TAG, "yt-dlp failed: " + e)
                rawLog(result.title, e)
                setDl(GrabDownload(dlId, result.title, video, 0f,
                    GrabState.ERROR, humanError(e)))
            }
        }
    }

    private fun runYtDlp(result: GrabResult, video: Boolean, dlId: String, clients: String) {
        val req = YoutubeDLRequest(result.url)
        req.addOption("-o", engine.savePath + "/%(title).80s.%(ext)s")
        req.addOption("--no-mtime")
        req.addOption("--no-playlist")
        req.addOption("--no-check-certificates")
        req.addOption("--extractor-args", "youtube:player_client=" + clients)
        if (video) {
            req.addOption("-f", "bestvideo+bestaudio/best")
            req.addOption("--merge-output-format", "mp4")
        } else {
            req.addOption("-x")
            req.addOption("--audio-format", "mp3")
            req.addOption("--audio-quality", "0")
        }
        YoutubeDL.getInstance().execute(req, dlId) { progress, _, _ ->
            if (progress >= 0f) {
                setDl(GrabDownload(dlId, result.title, video,
                    progress / 100f, GrabState.DOWNLOADING))
            }
        }
    }

        // Обновить yt-dlp без пересборки приложения (когда YouTube что-то сломает)
    suspend fun updateYtDlp(): String = withContext(Dispatchers.IO) {
        try {
            ensureYtdl()
            val status = YoutubeDL.getInstance()
                .updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            if (status == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE)
                "yt-dlp уже актуален" else "✓ yt-dlp обновлён"
        } catch (e: Exception) {
            "Ошибка обновления: " + (e.message ?: "?").take(120)
        }
    }

    // Скачивание по произвольной ссылке (любой из ~1800 сайтов yt-dlp).
    // title подтянет сам yt-dlp через шаблон %(title)s.
    fun startFromUrl(url: String, video: Boolean) {
        val clean = url.trim()
        val fake = GrabResult(
            serviceId = -1, serviceName = "URL",
            url = clean,
            title = clean.take(60)
        )
        startDownload(fake, video)
    }

    fun dismiss(id: String) = _downloads.update { it - id }

    /** Отмена активной загрузки: убиваем процесс yt-dlp, чистим недокачанное, убираем карточку */
    fun cancel(id: String) {
        cancelled.add(id)
        _downloads.update { it - id }
        try { YoutubeDL.getInstance().destroyProcessById(id) } catch (_: Exception) {}
        // yt-dlp оставляет частичные файлы (*.part, *.ytdl, *.f<id>.*) —
        // убираем их с небольшой задержкой, после того как процесс отпустит файл
        scope.launch {
            delay(1500)
            try {
                java.io.File(engine.savePath).listFiles()?.forEach { f ->
                    val n = f.name
                    if (n.endsWith(".part") || n.endsWith(".ytdl") ||
                        n.contains(".part-") || n.endsWith(".temp")) f.delete()
                }
            } catch (_: Exception) {}
        }
    }

    // «42% • 120,5 МБ • 2,3 МБ/с • осталось 0:42» из строки статуса yt-dlp:
    // "[download]  42.3% of ~120.50MiB at 2.31MiB/s ETA 00:42"
    private fun formatDetail(progressPct: Float, etaSec: Long, line: String?): String {
        val sb = StringBuilder("%.0f%%".format(progressPct))
        if (line != null) {
            Regex("of ~?([0-9.]+)(K|M|G)iB").find(line)?.let {
                sb.append(" • ").append(ruSize(it.groupValues[1], it.groupValues[2]))
            }
            Regex("at ([0-9.]+)(K|M|G)iB/s").find(line)?.let {
                sb.append(" • ").append(ruSize(it.groupValues[1], it.groupValues[2])).append("/с")
            }
        }
        if (etaSec > 0) sb.append(" • осталось ").append(fmtEta(etaSec))
        return sb.toString()
    }

    private fun ruSize(num: String, unit: String): String {
        val n = num.toDoubleOrNull() ?: return num
        val u = when (unit) { "K" -> "КБ"; "M" -> "МБ"; else -> "ГБ" }
        return (if (n >= 100) "%.0f" else "%.1f").format(n).replace('.', ',') + " " + u
    }

    private fun fmtEta(sec: Long): String {
        val m = sec / 60; val s = sec % 60
        return if (m >= 60) "%d:%02d:%02d".format(m / 60, m % 60, s)
               else "%d:%02d".format(m, s)
    }

    private fun setDl(d: GrabDownload) = _downloads.update { it + (d.id to d) }

    // Полный текст ошибки yt-dlp в файл для диагностики
    private fun rawLog(title: String, e: Exception) {
        try {
            val f = java.io.File(engine.savePath, "grab-log.txt")
            f.parentFile?.mkdirs()
            val ts = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date())
            f.appendText(ts + " [" + title.take(50) + "]" + System.lineSeparator() +
                (e.message ?: e.toString()) + System.lineSeparator() +
                "----" + System.lineSeparator())
        } catch (_: Exception) {}
    }

    private fun humanError(e: Exception): String {
        val m = e.message ?: ""
        return when {
            e is javax.net.ssl.SSLHandshakeException || m.contains("Trust anchor") ||
            m.contains("CERTIFICATE_VERIFY_FAILED") ->
                "Провайдер вмешивается в соединение (подмена сертификата) — " +
                "так обычно блокируют YouTube. Попробуй с VPN"
            e is java.net.SocketTimeoutException || m.contains("timed out") ->
                "Соединение оборвалось по таймауту — сервис может замедляться провайдером"
            e is java.net.UnknownHostException ->
                "Сервер не найден — проверь интернет или включи VPN"
            m.contains("SABR") || m.contains("missing a URL") ->
                "YouTube ограничил доступ к этому ролику. Обнови yt-dlp (кнопка ⟳) или попробуй позже"
            else -> m.take(200).ifBlank { "неизвестная ошибка" }
        }
    }
}
