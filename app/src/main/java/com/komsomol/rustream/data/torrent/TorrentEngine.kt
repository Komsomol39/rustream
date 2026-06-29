package com.komsomol.rustream.data.torrent

import android.content.Context
import android.util.Log
import com.frostwire.jlibtorrent.AlertListener
import com.frostwire.jlibtorrent.Priority
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.SessionParams
import com.frostwire.jlibtorrent.SettingsPack
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentInfo
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.alerts.AlertType
import com.frostwire.jlibtorrent.alerts.MetadataReceivedAlert
import com.frostwire.jlibtorrent.alerts.TorrentFinishedAlert
import com.komsomol.rustream.domain.model.DownloadItem
import com.komsomol.rustream.domain.model.DownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorrentEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "TorrentEngine"
    private val session = SessionManager()
    private val scope = CoroutineScope(Dispatchers.IO)

    // Хранилище загрузок: id -> DownloadItem
    private val _downloads = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadItem>> = _downloads.asStateFlow()

    // Хранилище хэндлов: id -> TorrentHandle
    private val handles = mutableMapOf<String, TorrentHandle>()

    private var progressJob: Job? = null

    init {
        startSession()
    }

    private fun startSession() {
        try {
            val sp = SettingsPack().apply {
                connectionsLimit(200)
                downloadRateLimit(0)
                uploadRateLimit(0)
                activeDhtLimit(300)
            }
            session.start(SessionParams(sp))
            session.addListener(object : AlertListener {
                override fun types(): IntArray? = null
                override fun alert(alert: Alert<*>) {
                    when (alert.type()) {
                        AlertType.METADATA_RECEIVED -> {
                            val a = alert as MetadataReceivedAlert
                            val handle = a.handle()
                            val id = findIdByHandle(handle) ?: return
                            updateState(id, DownloadState.DOWNLOADING)
                            Log.d(TAG, "Metadata received for $id")
                        }
                        AlertType.TORRENT_FINISHED -> {
                            val a = alert as TorrentFinishedAlert
                            val handle = a.handle()
                            val id = findIdByHandle(handle) ?: return
                            updateState(id, DownloadState.FINISHED, progress = 1f)
                            Log.d(TAG, "Finished: $id")
                        }
                        else -> {}
                    }
                }
            })
            startProgressUpdater()
            Log.d(TAG, "Session started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start session", e)
        }
    }

    fun addMagnet(item: DownloadItem) {
        scope.launch {
            try {
                _downloads.update { it + (item.id to item.copy(state = DownloadState.FETCHING_META)) }
                val saveDir = File(item.savePath).also { it.mkdirs() }
                session.download(item.magnetUri!!, saveDir)
                // Находим handle по последнему добавленному
                delay(1000)
                val handle = session.find(com.frostwire.jlibtorrent.Sha1Hash.parseHex(
                    item.magnetUri.substringAfter("btih:").substringBefore("&").uppercase()
                ))
                if (handle != null && handle.isValid) {
                    handles[item.id] = handle
                }
            } catch (e: Exception) {
                Log.e(TAG, "addMagnet error", e)
                updateState(item.id, DownloadState.ERROR)
            }
        }
    }

    fun addTorrentFile(item: DownloadItem, torrentBytes: ByteArray) {
        scope.launch {
            try {
                _downloads.update { it + (item.id to item.copy(state = DownloadState.DOWNLOADING)) }
                val saveDir = File(item.savePath).also { it.mkdirs() }
                val ti = TorrentInfo.bdecode(torrentBytes)
                val priorities = Array(ti.numFiles()) { Priority.DEFAULT }
                session.download(ti, saveDir, null, priorities, null)
                delay(500)
                val handle = session.find(ti.infoHash())
                if (handle != null && handle.isValid) {
                    handles[item.id] = handle
                }
            } catch (e: Exception) {
                Log.e(TAG, "addTorrentFile error", e)
                updateState(item.id, DownloadState.ERROR)
            }
        }
    }

    fun pause(id: String) {
        handles[id]?.pause()
        updateState(id, DownloadState.PAUSED)
    }

    fun resume(id: String) {
        handles[id]?.resume()
        updateState(id, DownloadState.DOWNLOADING)
    }

    fun remove(id: String, deleteFiles: Boolean = false) {
        val handle = handles[id]
        if (handle != null && handle.isValid) {
            session.remove(handle)
            handles.remove(id)
        }
        _downloads.update { it - id }
    }

    private fun startProgressUpdater() {
        progressJob = scope.launch {
            while (true) {
                delay(1000)
                updateAllProgress()
            }
        }
    }

    private fun updateAllProgress() {
        val current = _downloads.value.toMutableMap()
        var changed = false
        for ((id, item) in current) {
            if (item.state != DownloadState.DOWNLOADING && item.state != DownloadState.FETCHING_META) continue
            val handle = handles[id] ?: continue
            if (!handle.isValid) continue
            try {
                val status = handle.status()
                val progress = status.progress()
                val dlSpeed = status.downloadPayloadRate().toLong()
                val ulSpeed = status.uploadPayloadRate().toLong()
                val dlBytes = status.totalDone()
                val totalBytes = status.totalWanted()
                val seeds = status.numSeeds()
                val peers = status.numPeers()
                val state = when {
                    progress >= 1f -> DownloadState.FINISHED
                    status.isMovingStorage -> DownloadState.DOWNLOADING
                    else -> item.state
                }
                current[id] = item.copy(
                    progress = progress,
                    downloadedBytes = dlBytes,
                    totalBytes = totalBytes,
                    downloadSpeedBps = dlSpeed,
                    uploadSpeedBps = ulSpeed,
                    seeds = seeds,
                    peers = peers,
                    state = state
                )
                changed = true
            } catch (_: Exception) {}
        }
        if (changed) _downloads.value = current
    }

    private fun findIdByHandle(handle: TorrentHandle): String? =
        handles.entries.firstOrNull { it.value == handle }?.key

    private fun updateState(id: String, state: DownloadState, progress: Float? = null) {
        _downloads.update { map ->
            val item = map[id] ?: return@update map
            map + (id to item.copy(
                state = state,
                progress = progress ?: item.progress
            ))
        }
    }

    fun stopSession() {
        progressJob?.cancel()
        session.stop()
    }
}
