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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
                ensureYtdl() // первый запуск распаковывает Python, ~10-20 сек

                val req = YoutubeDLRequest(result.url)
                req.addOption("-o", engine.savePath + "/%(title).80s.%(ext)s")
                req.addOption("--no-mtime")
                req.addOption("--no-playlist")
                // Python внутри приложения не видит системные/VPN CA — иначе
                // self-signed certificate in chain при туннелировании трафика
                req.addOption("--no-check-certificates")
                // Клиенты android/ios не требуют решения n-challenge через JS,
                // который ломается на новых плеерах YouTube (found 0 n function)
                req.addOption("--extractor-args", "youtube:player_client=android,ios,web")
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
                YoutubeDL.getInstance().execute(req, dlId) { progress, _, _ ->
                    if (progress >= 0f) {
                        setDl(GrabDownload(dlId, result.title, video,
                            progress / 100f, GrabState.DOWNLOADING))
                    }
                }
                setDl(GrabDownload(dlId, result.title, video, 1f, GrabState.DONE))
            } catch (e: Exception) {
                Log.e(TAG, "yt-dlp failed: " + e)
                rawLog(result.title, e)
                setDl(GrabDownload(dlId, result.title, video, 0f,
                    GrabState.ERROR, humanError(e)))
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

    fun dismiss(id: String) = _downloads.update { it - id }

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
            else -> m.take(200).ifBlank { "неизвестная ошибка" }
        }
    }
}
