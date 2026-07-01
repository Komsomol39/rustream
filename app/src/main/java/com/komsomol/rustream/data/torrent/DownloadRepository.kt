package com.komsomol.rustream.data.torrent

import android.util.Log
import com.komsomol.rustream.data.search.NnmCookieStore
import com.komsomol.rustream.data.search.RuTrackerCookieStore
import com.komsomol.rustream.domain.model.DownloadItem
import com.komsomol.rustream.domain.model.DownloadState
import com.komsomol.rustream.domain.model.SearchResult
import com.komsomol.rustream.domain.model.SearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    val engine: TorrentEngine,
    private val rtCookies: RuTrackerCookieStore,
    private val nnmCookies: NnmCookieStore
) {
    private val TAG = "DownloadRepo"

    private val plainClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // .torrent с приватных трекеров нужно качать С КУКАМИ авторизации
    private val rtClient  = plainClient.newBuilder().cookieJar(rtCookies).build()
    private val nnmClient = plainClient.newBuilder().cookieJar(nnmCookies).build()

    private fun clientFor(source: SearchSource): OkHttpClient = when (source) {
        SearchSource.RUTRACKER -> rtClient
        SearchSource.NNM       -> nnmClient
        else                   -> plainClient
    }

    val downloads: StateFlow<Map<String, DownloadItem>> = engine.downloads
    val dhtNodes: StateFlow<Long> = engine.dhtNodes

    fun start() = engine.start()

    // Актуальные живые публичные трекеры
    private val PUBLIC_TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker-udp.gbitt.info:80/announce",
        "udp://explodie.org:6969/announce",
        "udp://opentracker.io:6969/announce",
        "http://tracker.opentrackr.org:1337/announce"
    )

    // ВСЕГДА подмешиваем живые трекеры: в магнетах с сайтов трекеры часто мёртвые
    private fun enrichMagnet(magnet: String): String {
        val sb = StringBuilder(magnet)
        for (t in PUBLIC_TRACKERS) {
            val enc = java.net.URLEncoder.encode(t, "UTF-8")
            if (!magnet.contains(enc) && !magnet.contains(t)) {
                sb.append("&tr=").append(enc)
            }
        }
        return sb.toString()
    }

    // Запуск через magnet
    suspend fun startMagnet(result: SearchResult): String? = withContext(Dispatchers.IO) {
        val rawMagnet = result.magnetUri ?: return@withContext null
        val magnet = enrichMagnet(rawMagnet)
        val id = TorrentEngine.extractHash(magnet) ?: java.util.UUID.randomUUID().toString()
        val item = DownloadItem(
            id         = id,
            title      = result.title,
            magnetUri  = magnet,
            torrentUrl = null,
            savePath   = engine.savePath,
            state      = DownloadState.FETCHING_META
        )
        engine.addMagnet(item)
        Log.d(TAG, "Started magnet: " + id)
        id
    }

    // Запуск через .torrent файл
    suspend fun startTorrentUrl(result: SearchResult): String? = withContext(Dispatchers.IO) {
        val torrentUrl = result.torrentUrl ?: return@withContext null
        val id = java.util.UUID.randomUUID().toString()
        val item = DownloadItem(
            id         = id,
            title      = result.title,
            magnetUri  = null,
            torrentUrl = torrentUrl,
            savePath   = engine.savePath,
            state      = DownloadState.DOWNLOADING
        )
        try {
            val req = Request.Builder().url(torrentUrl)
                .header("User-Agent", UA)
                .header("Referer", result.detailUrl)
                .build()
            val bytes = clientFor(result.source).newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    engine.addFailed(item, "Сервер вернул " + resp.code + " при скачивании .torrent")
                    return@withContext id
                }
                resp.body?.bytes()
            }
            if (bytes == null || bytes.isEmpty()) {
                engine.addFailed(item, "Пустой ответ при скачивании .torrent")
                return@withContext id
            }
            // .torrent всегда начинается с bencode-словаря (символ d).
            // Если пришёл HTML — это страница логина, а не торрент.
            if (bytes[0] != 'd'.code.toByte()) {
                engine.addFailed(item, "Вместо .torrent пришёл HTML — нужна авторизация на " +
                    result.source.displayName)
                return@withContext id
            }
            engine.addTorrentFile(item, bytes)
            Log.d(TAG, "Started torrent file: " + id)
            id
        } catch (e: Exception) {
            Log.e(TAG, "startTorrentUrl failed: " + e.message)
            engine.addFailed(item, "Ошибка скачивания .torrent: " + (e.message ?: "?"))
            id
        }
    }

    fun pause(id: String)  = engine.pause(id)
    fun resume(id: String) = engine.resume(id)
    fun remove(id: String, deleteFiles: Boolean = false) = engine.remove(id, deleteFiles)

    companion object {
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
    }
}
