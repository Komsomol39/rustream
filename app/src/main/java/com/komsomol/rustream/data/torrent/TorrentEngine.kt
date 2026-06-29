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
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.swig.add_torrent_params
import org.libtorrent4j.swig.error_code
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

    val savePath: String = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS
    ).absolutePath + "/RuStream"

    fun start() {
        if (started) return
        started = true

        val sp = SettingsPack().apply {
            setInteger(SettingsPack.integer_types.connections_limit.swigValue(), 200)
            setInteger(SettingsPack.integer_types.active_downloads.swigValue(), 5)
            setInteger(SettingsPack.integer_types.active_seeds.swigValue(), 5)
        }

        session.addListener(object : AlertListener {
            override fun types(): IntArray? = null
            override fun alert(alert: Alert<*>) {
                when (alert.type()) {
                    AlertType.METADATA_RECEIVED -> {
                        val a = alert as MetadataReceivedAlert
                        val hash = a.handle().infoHashes().best().toHex()
                        Log.d(TAG, "Metadata received: $hash")
                        updateState(hash, DownloadState.DOWNLOADING)
                    }
                    AlertType.TORRENT_FINISHED -> {
                        val a = alert as TorrentFinishedAlert
                        val hash = a.handle().infoHashes().best().toHex()
                        Log.d(TAG, "Finished: $hash")
                        updateState(hash, DownloadState.FINISHED, progress = 1f)
                    }
                    AlertType.TORRENT_ERROR -> {
                        val a = alert as TorrentErrorAlert
                        val hash = a.handle().infoHashes().best().toHex()
                        Log.e(TAG, "Error: $hash ${a.error()}")
                        updateState(hash, DownloadState.ERROR)
                    }
                    else -> {}
                }
            }
        })

        session.start(sp)
        File(savePath).mkdirs()

        // Polling прогресса каждую секунду
        scope.launch {
            while (true) {
                delay(1000)
                pollProgress()
            }
        }
        Log.d(TAG, "Session started, savePath=$savePath")
    }

    fun stop() {
        if (started) session.stop()
        started = false
    }

    fun addMagnet(item: DownloadItem) {
        val magnet = item.magnetUri ?: return
        _downloads.update { it + (item.id to item.copy(state = DownloadState.FETCHING_META)) }
        scope.launch {
            try {
                session.download(magnet, File(savePath))
            } catch (e: Exception) {
                Log.e(TAG, "addMagnet error: ${e.message}")
                updateState(item.id, DownloadState.ERROR)
            }
        }
    }

    fun addTorrentFile(item: DownloadItem, torrentBytes: ByteArray) {
        _downloads.update { it + (item.id to item.copy(state = DownloadState.DOWNLOADING)) }
        scope.launch {
            try {
                val ti = TorrentInfo.bdecode(torrentBytes)
                session.download(ti, File(savePath))
            } catch (e: Exception) {
                Log.e(TAG, "addTorrentFile error: ${e.message}")
                updateState(item.id, DownloadState.ERROR)
            }
        }
    }

    fun pause(id: String) {
        findHandle(id)?.pause()
        updateState(id, DownloadState.PAUSED)
    }

    fun resume(id: String) {
        findHandle(id)?.resume()
        updateState(id, DownloadState.DOWNLOADING)
    }

    fun remove(id: String, deleteFiles: Boolean = false) {
        findHandle(id)?.let { h ->
            if (deleteFiles) session.remove(h, SessionManager.REMOVE_AND_DELETE)
            else session.remove(h)
        }
        _downloads.update { it - id }
    }

    private fun pollProgress() {
        val handles = try { session.handles() } catch (e: Exception) { return }
        handles.forEach { h ->
            if (!h.isValid) return@forEach
            try {
                val status = h.status()
                val hash   = h.infoHashes().best().toHex()
                val item   = _downloads.value[hash] ?: return@forEach
                if (item.state == DownloadState.FINISHED || item.state == DownloadState.ERROR) return@forEach

                val state = when {
                    status.isFinished                     -> DownloadState.FINISHED
                    status.isPaused                       -> DownloadState.PAUSED
                    status.progress() < 1f && !status.isPaused -> DownloadState.DOWNLOADING
                    else -> item.state
                }

                _downloads.update { map ->
                    map + (hash to item.copy(
                        state           = state,
                        progress        = status.progress(),
                        downloadedBytes = status.totalDone(),
                        totalBytes      = status.total(),
                        downloadSpeedBps = status.downloadPayloadRate().toLong(),
                        uploadSpeedBps   = status.uploadPayloadRate().toLong(),
                        seeds            = status.numSeeds(),
                        peers            = status.numPeers()
                    ))
                }
            } catch (_: Exception) {}
        }
    }

    private fun findHandle(id: String): TorrentHandle? =
        try { session.handles().firstOrNull { it.isValid && it.infoHashes().best().toHex() == id } }
        catch (_: Exception) { null }

    private fun updateState(id: String, state: DownloadState, progress: Float? = null) {
        _downloads.update { map ->
            val item = map[id] ?: return@update map
            map + (id to item.copy(
                state    = state,
                progress = progress ?: item.progress
            ))
        }
    }
}
