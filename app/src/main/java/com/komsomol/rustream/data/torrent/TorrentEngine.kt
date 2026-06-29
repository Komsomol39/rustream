package com.komsomol.rustream.data.torrent

import android.content.Context
import android.os.Environment
import android.util.Log
import com.komsomol.rustream.domain.model.DownloadItem
import com.komsomol.rustream.domain.model.DownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.libtorrent4j.AlertListener
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorrentEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "TorrentEngine"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val session = SessionManager()
    private var started = false

    // id -> DownloadItem
    private val _downloads = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadItem>> = _downloads.asStateFlow()

    // id -> magnet/hash для поиска handle
    private val idToHash = mutableMapOf<String, String>()

    val savePath: String = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS
    ).absolutePath + "/RuStream"

    fun start() {
        if (started) return
        started = true

        session.addListener(object : AlertListener {
            override fun types(): IntArray? = null
            override fun alert(alert: Alert<*>) {
                when (alert.type()) {
                    AlertType.METADATA_RECEIVED -> {
                        val a = alert as MetadataReceivedAlert
                        val hash = a.handle().infoHash().toString()
                        Log.d(TAG, "Metadata: $hash")
                        findIdByHash(hash)?.let { updateState(it, DownloadState.DOWNLOADING) }
                    }
                    AlertType.TORRENT_FINISHED -> {
                        val a = alert as TorrentFinishedAlert
                        val hash = a.handle().infoHash().toString()
                        Log.d(TAG, "Finished: $hash")
                        findIdByHash(hash)?.let { updateState(it, DownloadState.FINISHED, 1f) }
                    }
                    AlertType.TORRENT_ERROR -> {
                        val a = alert as TorrentErrorAlert
                        val hash = a.handle().infoHash().toString()
                        Log.e(TAG, "Error: $hash")
                        findIdByHash(hash)?.let { updateState(it, DownloadState.ERROR) }
                    }
                    else -> {}
                }
            }
        })

        session.start()
        File(savePath).mkdirs()

        scope.launch {
            while (true) {
                delay(1000)
                if (started) pollProgress()
            }
        }
        Log.d(TAG, "Started, savePath=$savePath")
    }

    fun stop() {
        started = false
        try { session.stop() } catch (_: Exception) {}
    }

    fun addMagnet(item: DownloadItem) {
        val magnet = item.magnetUri ?: return
        _downloads.update { it + (item.id to item.copy(state = DownloadState.FETCHING_META)) }
        // Извлекаем hash из magnet для привязки handle
        val hash = extractHash(magnet) ?: item.id
        synchronized(idToHash) { idToHash[item.id] = hash }
        scope.launch {
            try {
                session.download(magnet, File(savePath))
            } catch (e: Exception) {
                Log.e(TAG, "addMagnet error: ${e.message}")
                updateState(item.id, DownloadState.ERROR)
            }
        }
    }

    fun addTorrentFile(item: DownloadItem, bytes: ByteArray) {
        _downloads.update { it + (item.id to item.copy(state = DownloadState.DOWNLOADING)) }
        scope.launch {
            try {
                val ti = TorrentInfo.bdecode(bytes)
                val hash = ti.infoHash().toString()
                synchronized(idToHash) { idToHash[item.id] = hash }
                session.download(ti, File(savePath))
            } catch (e: Exception) {
                Log.e(TAG, "addTorrentFile error: ${e.message}")
                updateState(item.id, DownloadState.ERROR)
            }
        }
    }

    fun pause(id: String) {
        val hash = synchronized(idToHash) { idToHash[id] } ?: return
        try { session.find(org.libtorrent4j.Sha1Hash(hash))?.pause() } catch (_: Exception) {}
        updateState(id, DownloadState.PAUSED)
    }

    fun resume(id: String) {
        val hash = synchronized(idToHash) { idToHash[id] } ?: return
        try { session.find(org.libtorrent4j.Sha1Hash(hash))?.resume() } catch (_: Exception) {}
        updateState(id, DownloadState.DOWNLOADING)
    }

    fun remove(id: String, deleteFiles: Boolean = false) {
        val hash = synchronized(idToHash) { idToHash.remove(id) } ?: return
        try {
            val h = session.find(org.libtorrent4j.Sha1Hash(hash))
            if (h != null) {
                if (deleteFiles) session.remove(h, SessionManager.DELETE_FILES)
                else session.remove(h)
            }
        } catch (_: Exception) {}
        _downloads.update { it - id }
    }

    private fun pollProgress() {
        val current = _downloads.value
        current.forEach { (id, item) ->
            if (item.state == DownloadState.FINISHED || item.state == DownloadState.ERROR ||
                item.state == DownloadState.QUEUED) return@forEach
            val hash = synchronized(idToHash) { idToHash[id] } ?: return@forEach
            try {
                val h = session.find(org.libtorrent4j.Sha1Hash(hash)) ?: return@forEach
                val s = h.status()
                val newState = when {
                    s.isFinished -> DownloadState.FINISHED
                    s.isPaused  -> DownloadState.PAUSED
                    else        -> DownloadState.DOWNLOADING
                }
                _downloads.update { map ->
                    map + (id to item.copy(
                        state            = newState,
                        progress         = s.progress(),
                        downloadedBytes  = s.totalDone(),
                        totalBytes       = s.total(),
                        downloadSpeedBps = s.downloadPayloadRate().toLong(),
                        uploadSpeedBps   = s.uploadPayloadRate().toLong(),
                        seeds            = s.numSeeds(),
                        peers            = s.numPeers()
                    ))
                }
            } catch (_: Exception) {}
        }
    }

    private fun findIdByHash(hash: String): String? =
        synchronized(idToHash) { idToHash.entries.firstOrNull { it.value == hash }?.key }

    private fun updateState(id: String, state: DownloadState, progress: Float? = null) {
        _downloads.update { map ->
            val item = map[id] ?: return@update map
            map + (id to item.copy(state = state, progress = progress ?: item.progress))
        }
    }

    private fun extractHash(magnet: String): String? =
        Regex("urn:btih:([a-fA-F0-9]{40})", RegexOption.IGNORE_CASE)
            .find(magnet)?.groupValues?.get(1)?.lowercase()
}
