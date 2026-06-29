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
import org.libtorrent4j.TorrentHandle
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

    private val _downloads = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadItem>> = _downloads.asStateFlow()

    // Храним handles напрямую — id -> TorrentHandle
    private val handles = mutableMapOf<String, TorrentHandle>()

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
                        val h = (alert as MetadataReceivedAlert).handle()
                        val id = findIdByHandle(h)
                        Log.d(TAG, "Metadata received id=$id")
                        id?.let { updateState(it, DownloadState.DOWNLOADING) }
                            ?: synchronized(handles) { handles["meta_${h.hashCode()}"] = h }
                    }
                    AlertType.TORRENT_FINISHED -> {
                        val h = (alert as TorrentFinishedAlert).handle()
                        findIdByHandle(h)?.let { updateState(it, DownloadState.FINISHED, 1f) }
                    }
                    AlertType.TORRENT_ERROR -> {
                        val h = (alert as TorrentErrorAlert).handle()
                        findIdByHandle(h)?.let { updateState(it, DownloadState.ERROR) }
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
        scope.launch {
            try {
                val h = session.download(magnet, File(savePath))
                synchronized(handles) { handles[item.id] = h }
            } catch (e: Exception) {
                Log.e(TAG, "addMagnet: ${e.message}")
                updateState(item.id, DownloadState.ERROR)
            }
        }
    }

    fun addTorrentFile(item: DownloadItem, bytes: ByteArray) {
        _downloads.update { it + (item.id to item.copy(state = DownloadState.DOWNLOADING)) }
        scope.launch {
            try {
                val ti = TorrentInfo.bdecode(bytes)
                val h  = session.download(ti, File(savePath))
                synchronized(handles) { handles[item.id] = h }
            } catch (e: Exception) {
                Log.e(TAG, "addTorrentFile: ${e.message}")
                updateState(item.id, DownloadState.ERROR)
            }
        }
    }

    fun pause(id: String) {
        synchronized(handles) { handles[id] }?.pause()
        updateState(id, DownloadState.PAUSED)
    }

    fun resume(id: String) {
        synchronized(handles) { handles[id] }?.resume()
        updateState(id, DownloadState.DOWNLOADING)
    }

    fun remove(id: String, deleteFiles: Boolean = false) {
        val h = synchronized(handles) { handles.remove(id) }
        if (h != null) {
            try { session.remove(h) } catch (_: Exception) {}
        }
        _downloads.update { it - id }
    }

    private fun pollProgress() {
        val current = _downloads.value
        current.forEach { (id, item) ->
            if (item.state == DownloadState.FINISHED || item.state == DownloadState.ERROR) return@forEach
            val h = synchronized(handles) { handles[id] } ?: return@forEach
            try {
                if (!h.isValid) return@forEach
                val s = h.status()
                val flags = s.flags()
                val isPaused = flags.and_(org.libtorrent4j.swig.torrent_flags_t.paused).nonZero()
                val newState = when {
                    s.isFinished -> DownloadState.FINISHED
                    isPaused     -> DownloadState.PAUSED
                    else         -> DownloadState.DOWNLOADING
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

    private fun findIdByHandle(h: TorrentHandle): String? =
        synchronized(handles) { handles.entries.firstOrNull { it.value == h }?.key }

    private fun updateState(id: String, state: DownloadState, progress: Float? = null) {
        _downloads.update { map ->
            val item = map[id] ?: return@update map
            map + (id to item.copy(state = state, progress = progress ?: item.progress))
        }
    }
}
