package com.komsomol.rustream.data.torrent

import android.content.Context
import android.util.Log
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
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
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

    private val _downloads = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadItem>> = _downloads.asStateFlow()

    private val handles = mutableMapOf<String, TorrentHandle>()
    private var progressJob: Job? = null

    init { startSession() }

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
                            val handle = (alert as MetadataReceivedAlert).handle()
                            findIdByHandle(handle)?.let { updateState(it, DownloadState.DOWNLOADING) }
                        }
                        AlertType.TORRENT_FINISHED -> {
                            val handle = (alert as TorrentFinishedAlert).handle()
                            findIdByHandle(handle)?.let { updateState(it, DownloadState.FINISHED, 1f) }
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
                delay(2000)
                // Извлекаем hash из magnet URI
                val hash = item.magnetUri
                    .substringAfter("btih:", "")
                    .substringBefore("&")
                    .uppercase()
                    .take(40)
                if (hash.length == 40) {
                    val handle = session.find(Sha1Hash.parseHex(hash))
                    if (handle != null && handle.isValid) handles[item.id] = handle
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
                if (handle != null && handle.isValid) handles[item.id] = handle
            } catch (e: Exception) {
                Log.e(TAG, "addTorrentFile error", e)
                updateState(item.id, DownloadState.ERROR)
            }
        }
    }

    fun pause(id: String)  { handles[id]?.pause();  updateState(id, DownloadState.PAUSED) }
    fun resume(id: String) { handles[id]?.resume(); updateState(id, DownloadState.DOWNLOADING) }

    fun remove(id: String) {
        handles[id]?.let { h -> if (h.isValid) session.remove(h) }
        handles.remove(id)
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
            if (item.state != DownloadState.DOWNLOADING &&
                item.state != DownloadState.FETCHING_META) continue
            val handle = handles[id]?.takeIf { it.isValid } ?: continue
            try {
                val status   = handle.status()
                val progress = status.progress()
                val state    = if (progress >= 1f) DownloadState.FINISHED else item.state
                current[id]  = item.copy(
                    progress         = progress,
                    downloadedBytes  = status.totalDone(),
                    totalBytes       = status.totalWanted(),
                    downloadSpeedBps = status.downloadPayloadRate().toLong(),
                    uploadSpeedBps   = status.uploadPayloadRate().toLong(),
                    seeds            = status.numSeeds(),
                    peers            = status.numPeers(),
                    state            = state
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
            map + (id to item.copy(state = state, progress = progress ?: item.progress))
        }
    }
}
