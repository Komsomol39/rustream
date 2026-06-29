package com.komsomol.rustream.data.torrent

import android.util.Log
import com.komsomol.rustream.domain.model.DownloadItem
import com.komsomol.rustream.domain.model.DownloadState
import com.komsomol.rustream.domain.model.SearchResult
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
    val engine: TorrentEngine
) {
    private val TAG = "DownloadRepo"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val downloads: StateFlow<Map<String, DownloadItem>> = engine.downloads

    fun start() = engine.start()

    // Публичные трекеры — помогают находить сиды быстрее
    private val PUBLIC_TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://9.rarbg.to:2710/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "http://tracker.opentrackr.org:1337/announce"
    )

    private fun enrichMagnet(magnet: String): String {
        val trackers = PUBLIC_TRACKERS.joinToString("") {
            "&tr=${java.net.URLEncoder.encode(it, "UTF-8")}"
        }
        return if (magnet.contains("&tr=")) magnet else magnet + trackers
    }

    // Запуск через magnet
    suspend fun startMagnet(result: SearchResult): String? = withContext(Dispatchers.IO) {
        val rawMagnet = result.magnetUri ?: return@withContext null
        val magnet = enrichMagnet(rawMagnet)
        val id = extractHash(magnet) ?: java.util.UUID.randomUUID().toString()
        val item = DownloadItem(
            id         = id,
            title      = result.title,
            magnetUri  = magnet,
            torrentUrl = null,
            savePath   = engine.savePath,
            state      = DownloadState.FETCHING_META
        )
        engine.addMagnet(item)
        Log.d(TAG, "Started magnet: $id")
        id
    }

    // Запуск через .torrent файл
    suspend fun startTorrentUrl(result: SearchResult): String? = withContext(Dispatchers.IO) {
        val torrentUrl = result.torrentUrl ?: return@withContext null
        try {
            val req = Request.Builder().url(torrentUrl)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", result.detailUrl)
                .build()
            val bytes = client.newCall(req).execute().use { resp ->
                resp.body?.bytes() ?: return@withContext null
            }
            val id = java.util.UUID.randomUUID().toString()
            val item = DownloadItem(
                id         = id,
                title      = result.title,
                magnetUri  = null,
                torrentUrl = torrentUrl,
                savePath   = engine.savePath,
                state      = DownloadState.DOWNLOADING
            )
            engine.addTorrentFile(item, bytes)
            Log.d(TAG, "Started torrent file: $id")
            id
        } catch (e: Exception) {
            Log.e(TAG, "startTorrentUrl failed: ${e.message}")
            null
        }
    }

    fun pause(id: String)  = engine.pause(id)
    fun resume(id: String) = engine.resume(id)
    fun remove(id: String, deleteFiles: Boolean = false) = engine.remove(id, deleteFiles)

    private fun extractHash(magnet: String): String? =
        Regex("urn:btih:([a-fA-F0-9]{40})", RegexOption.IGNORE_CASE)
            .find(magnet)?.groupValues?.get(1)?.lowercase()
}
