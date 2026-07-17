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
    @Volatile private var ytdlUpdated = false
    private fun ensureYtdl() {
        if (ytdlReady) return
        synchronized(this) {
            if (ytdlReady) return
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            ytdlReady = true
        }
        // YouTube часто меняет плеер (SABR и пр.) — фиксы приходят в свежих
        // сборках yt-dlp. Один раз за сессию обновляемся ДО первого скачивания
        // (блокирующе), чтобы не ловить «android client https formats skipped».
        if (!ytdlUpdated) {
            synchronized(this) {
                if (!ytdlUpdated) {
                    try {
                        YoutubeDL.getInstance()
                            .updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
                    } catch (_: Exception) {}
                    ytdlUpdated = true
                }
            }
        }
    }

    fun startDownload(result: GrabResult, video: Boolean) {
        val dlId = result.url + (if (video) "#v" else "#a")
        setDl(GrabDownload(dlId, result.title, video, 0f, GrabState.RESOLVING))
        scope.launch {
            try {
                ensureYtdl() // первый запуск распаковывает Python, ~10-20 сек

                val req = YoutubeDLRequest(result.url)
                req.addOption("-o", engine.savePath + "/%(title).80s.%(ext)s")
                req.addOption("--no-mtime")
                req.addOption("--no-playlist")
                // Python внутри приложения не видит системные/VPN CA — иначе
                // self-signed certificate in chain при туннелировании трафика
                req.addOption("--no-check-certificates")
                // Клиентов НЕ задаём: nightly-сборки yt-dlp сами ежедневно
                // перенастраивают дефолтный набор под текущие меры YouTube
                // (SABR/DRM/бот-чек) — любой зашитый список устаревает быстрее
                if (video) {
                    // Лучшее видео + лучшее аудио, склейка ffmpeg в mp4
                    req.addOption("-f", "bestvideo+bestaudio/best")
                    req.addOption("--merge-output-format", "mp4")
                } else {
                    // Извлечь аудио и конвертировать в настоящий mp3
                    req.addOption("-x")
                    req.addOption("--audio-format", "mp3")
                    req.addOption("--audio-quality", "0")
                }

                setDl(GrabDownload(dlId, result.title, video, 0f, GrabState.DOWNLOADING))
                runYtdl(req, dlId, result.title, video)
            } catch (e: Exception) {
                if (cancelled.remove(dlId)) return@launch  // отменено пользователем
                Log.e(TAG, "yt-dlp failed: " + e)
                rawLog(result.title, e)
                setDl(GrabDownload(dlId, result.title, video, 0f,
                    GrabState.ERROR, humanError(e)))
            }
        }
    }

    /** Запрос доступных вариантов качества для ссылки. Показываем плитки перед скачиванием. */
    fun queryFormats(url: String) {
        val clean = url.trim()
        _formatQuery.value = FormatQuery(url = clean, title = clean.take(60), loading = true)
        scope.launch {
            try {
                ensureYtdl()
                val req = YoutubeDLRequest(clean)
                req.addOption("--no-check-certificates")
                req.addOption("--no-playlist")
                val info = YoutubeDL.getInstance().getInfo(req)
                val formats = buildFormats(info.formats ?: emptyList())
                _formatQuery.update {
                    it?.copy(loading = false, title = info.title ?: it.title, formats = formats)
                }
            } catch (e: Exception) {
                Log.e(TAG, "queryFormats failed: " + e)
                rawLog("format query", e)
                _formatQuery.update { it?.copy(loading = false, error = humanError(e)) }
            }
        }
    }

    fun dismissFormats() { _formatQuery.value = null }

    /**
     * Собирает список плиток. Видео-варианты (по одной строке на разрешение,
     * лучшее внутри разрешения) + один аудио-вариант mp3.
     * yt-dlp склеит выбранное видео с лучшим аудио.
     */
    private fun buildFormats(all: List<VideoFormat>): List<GrabFormat> {
        val out = mutableListOf<GrabFormat>()

        // Видео: берём форматы с картинкой, группируем по высоте, оставляем лучший fps/битрейт
        val byHeight = all
            .filter { it.vcodec != null && it.vcodec != "none" && it.height > 0 }
            .groupBy { it.height }
        byHeight.keys.sortedDescending().forEach { h ->
            val best = byHeight.getValue(h).maxByOrNull { it.fps * 100_000L + it.tbr } ?: return@forEach
            val hasAudio = best.acodec != null && best.acodec != "none"
            // если в формате нет звука — просим yt-dlp домешать лучшее аудио
            val fid = if (hasAudio) best.formatId!! else best.formatId + "+bestaudio"
            val fps = if (best.fps >= 50) best.fps.toString() else ""
            val ext = (best.ext ?: "mp4").uppercase()
            val size = pickSize(best)
            out.add(GrabFormat(
                formatId = fid,
                label    = "${h}p$fps • $ext",
                detail   = size,
                video    = true
            ))
        }
        // Фолбэк, если разрешения не распарсились
        if (out.none { it.video }) {
            out.add(GrabFormat("bestvideo+bestaudio/best", "Лучшее видео", null, true))
        }

        // Аудио: один пункт mp3 (yt-dlp сам выберет лучший источник и перекодирует)
        out.add(GrabFormat("bestaudio", "MP3 • лучшее качество", "конвертация", false))
        return out
    }

    private fun pickSize(f: VideoFormat): String? {
        val bytes = if (f.fileSize > 0) f.fileSize
                    else if (f.fileSizeApproximate > 0) f.fileSizeApproximate else 0L
        if (bytes <= 0) return null
        return "~" + when {
            bytes >= 1_073_741_824 -> "%.1f ГБ".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576     -> "%.0f МБ".format(bytes / 1_048_576.0)
            else                   -> "%.0f КБ".format(bytes / 1024.0)
        }.replace('.', ',')
    }

    /** Скачивание конкретного выбранного варианта */
    fun startFormat(url: String, title: String, fmt: GrabFormat) {
        val dlId = url + "#" + fmt.formatId
        setDl(GrabDownload(dlId, title, fmt.video, 0f, GrabState.RESOLVING))
        scope.launch {
            try {
                ensureYtdl()
                setDl(GrabDownload(dlId, title, fmt.video, 0f, GrabState.DOWNLOADING))
                val req = YoutubeDLRequest(url)
                req.addOption("-o", engine.savePath + "/%(title).80s.%(ext)s")
                req.addOption("--no-mtime")
                req.addOption("--no-playlist")
                req.addOption("--no-check-certificates")
                if (fmt.video) {
                    req.addOption("-f", fmt.formatId)
                    req.addOption("--merge-output-format", "mp4")
                } else {
                    req.addOption("-f", fmt.formatId)
                    req.addOption("-x")
                    req.addOption("--audio-format", "mp3")
                    req.addOption("--audio-quality", "0")
                }
                runYtdl(req, dlId, title, fmt.video)
            } catch (e: Exception) {
                if (cancelled.remove(dlId)) return@launch
                Log.e(TAG, "startFormat failed: " + e)
                rawLog(title, e)
                setDl(GrabDownload(dlId, title, fmt.video, 0f, GrabState.ERROR, humanError(e)))
            }
        }
    }

    // Общий прогон yt-dlp с прогрессом и фазой обработки (склейка/перекодирование).
    // 100% скачивания + строка про merge/ffmpeg -> состояние PROCESSING.
    private fun runYtdl(req: YoutubeDLRequest, dlId: String, title: String, video: Boolean) {
        YoutubeDL.getInstance().execute(req, dlId) { progress, etaSec, line ->
            val processing = line != null &&
                (line.contains("[Merger]") || line.contains("[ExtractAudio]") ||
                 line.contains("[ffmpeg]") || line.contains("[VideoConvertor]"))
            when {
                processing -> setDl(GrabDownload(dlId, title, video, 1f, GrabState.PROCESSING))
                progress >= 0f -> setDl(GrabDownload(dlId, title, video,
                    progress / 100f, GrabState.DOWNLOADING,
                    detail = formatDetail(progress, etaSec, line)))
            }
        }
        setDl(GrabDownload(dlId, title, video, 1f, GrabState.DONE))
        notifyDone(title, video)
    }

    // Уведомление «скачано» — чтобы не сидеть в приложении во время долгой загрузки
    private fun notifyDone(title: String, video: Boolean) {
        try {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            nm.createNotificationChannel(android.app.NotificationChannel(
                "grab", "Загрузки", android.app.NotificationManager.IMPORTANCE_LOW))
            val open = android.app.PendingIntent.getActivity(
                context, 0,
                android.content.Intent(context, com.komsomol.rustream.MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                android.app.PendingIntent.FLAG_IMMUTABLE)
            nm.notify(title.hashCode() and 0xffff,
                androidx.core.app.NotificationCompat.Builder(context, "grab")
                    .setSmallIcon(com.komsomol.rustream.R.drawable.ic_notification)
                    .setLargeIcon(appIconBitmap())
                    .setContentTitle(if (video) "Видео скачано" else "Аудио скачано")
                    .setContentText(title)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build())
        } catch (_: Exception) {}
    }

    // Полноцветная иконка приложения как крупная картинка в уведомлении
    private fun appIconBitmap(): android.graphics.Bitmap? = try {
        val d = context.packageManager.getApplicationIcon(context.packageName)
        val bmp = android.graphics.Bitmap.createBitmap(
            192, 192, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        d.setBounds(0, 0, c.width, c.height)
        d.draw(c)
        bmp
    } catch (_: Exception) { null }

    // Тихое автообновление при старте приложения (не блокирует ничего)
    fun autoUpdateSilently() {
        scope.launch {
            try {
                ensureYtdl()
                YoutubeDL.getInstance()
                    .updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                Log.d(TAG, "yt-dlp auto-update done")
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp auto-update skipped: " + e.message)
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
                "YouTube ограничил этот ролик. Нажми ⟳ (обновить yt-dlp) вверху и повтори"
            else -> m.take(200).ifBlank { "неизвестная ошибка" }
        }
    }
}
