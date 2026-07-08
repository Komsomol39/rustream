package com.komsomol.rustream.data.grab

import android.util.Log
import com.komsomol.rustream.data.torrent.TorrentEngine
import com.komsomol.rustream.domain.model.GrabDownload
import com.komsomol.rustream.domain.model.GrabResult
import com.komsomol.rustream.domain.model.GrabState
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
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrabRepository @Inject constructor(
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

    @Volatile private var initialized = false
    private fun ensureInit() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(NewPipeDownloaderImpl(client),
                Localization("ru", "RU"), ContentCountry("RU"))
            initialized = true
        }
    }

    // Поиск по всем сервисам NewPipe параллельно (YouTube, SoundCloud, PeerTube...)
    suspend fun search(query: String): List<GrabResult> = withContext(Dispatchers.IO) {
        ensureInit()
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
                                    durationSec = item.duration
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

    // video=true: лучший видеопоток одним файлом; video=false: лучший аудиопоток
    fun startDownload(result: GrabResult, video: Boolean) {
        val dlId = result.url + (if (video) "#v" else "#a")
        setDl(GrabDownload(dlId, result.title, video, 0f, GrabState.RESOLVING))
        scope.launch {
            try {
                ensureInit()
                val service = NewPipe.getService(result.serviceId)
                val info = StreamInfo.getInfo(service, result.url)
                // Только прямые файлы. HLS/DASH — это плейлисты из кусочков,
                // их нельзя сохранить как один файл без склейки
                val direct = org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP
                val stream = if (video) {
                    info.videoStreams
                        .filter { it.isUrl && it.deliveryMethod == direct }
                        .maxByOrNull { parseRes(it.getResolution()) }
                } else {
                    info.audioStreams
                        .filter { it.isUrl && it.deliveryMethod == direct }
                        .maxByOrNull { it.averageBitrate }
                }
                if (stream == null) {
                    setDl(GrabDownload(dlId, result.title, video, 0f,
                        GrabState.ERROR,
                        "Сервис не отдаёт этот контент одним файлом (только HLS-поток)"))
                    return@launch
                }

                val suffix = try { stream.format?.suffix ?: defExt(video) }
                             catch (_: Exception) { defExt(video) }
                val safe = sanitize(result.title)
                val fileName = safe + (if (video) "" else " [audio]") + "." + suffix
                val target = File(engine.savePath, fileName)
                target.parentFile?.mkdirs()

                setDl(GrabDownload(dlId, result.title, video, 0f, GrabState.DOWNLOADING))

                val req = okhttp3.Request.Builder().url(stream.content)
                    .addHeader("User-Agent", NewPipeDownloaderImpl.USER_AGENT)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        setDl(GrabDownload(dlId, result.title, video, 0f,
                            GrabState.ERROR, "HTTP " + resp.code))
                        return@launch
                    }
                    val bodyStream = resp.body
                    if (bodyStream == null) {
                        setDl(GrabDownload(dlId, result.title, video, 0f,
                            GrabState.ERROR, "Пустой ответ"))
                        return@launch
                    }
                    val total = bodyStream.contentLength()
                    var lastPct = -1
                    bodyStream.byteStream().use { input ->
                        target.outputStream().use { outS ->
                            val buf = ByteArray(65536)
                            var done = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                outS.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val pct = (done * 100 / total).toInt()
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        setDl(GrabDownload(dlId, result.title, video,
                                            done.toFloat() / total, GrabState.DOWNLOADING))
                                    }
                                }
                            }
                        }
                    }
                }
                setDl(GrabDownload(dlId, result.title, video, 1f, GrabState.DONE))
            } catch (e: Exception) {
                Log.e(TAG, "download failed: " + e)
                setDl(GrabDownload(dlId, result.title, video, 0f,
                    GrabState.ERROR, e.message ?: "ошибка"))
            }
        }
    }

    fun dismiss(id: String) = _downloads.update { it - id }

    private fun setDl(d: GrabDownload) = _downloads.update { it + (d.id to d) }

    private fun defExt(video: Boolean) = if (video) "mp4" else "m4a"

    private fun sanitize(title: String): String = buildString {
        for (c in title) {
            if (c.isLetterOrDigit() || c == ' ' || c == '.' || c == '-' || c == '_') append(c)
        }
    }.trim().take(80).ifBlank { "media" }

    private fun parseRes(r: String?): Int {
        if (r == null) return 0
        var v = 0
        for (c in r) { if (c.isDigit()) v = v * 10 + (c - '0') else break }
        return v
    }
}
