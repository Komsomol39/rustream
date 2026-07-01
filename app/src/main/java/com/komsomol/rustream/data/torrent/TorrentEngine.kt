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
import org.libtorrent4j.TorrentStatus
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorrentEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "TorrentEngine"
    private val scope = CoroutineScope(Dispatchers.IO)
    val session = SessionManager()
    private var started = false

    private val _downloads = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadItem>> = _downloads.asStateFlow()

    // id -> TorrentHandle
    private val handles = mutableMapOf<String, TorrentHandle>()
    // infoHash -> id (для связи alert -> item)
    private val hashToId = mutableMapOf<String, String>()

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
                    AlertType.ADD_TORRENT -> {
                        val h = (alert as AddTorrentAlert).handle()
                        val hash = h.infoHash().toString()
                        val id = synchronized(hashToId) { hashToId[hash] }
                        if (id != null) synchronized(handles) { handles[id] = h }
                        Log.d(TAG, "ADD_TORRENT hash=$hash id=$id")
                    }
                    AlertType.METADATA_RECEIVED -> {
                        val h = (alert as MetadataReceivedAlert).handle()
                        val hash = h.infoHash().toString()
                        val id = synchronized(hashToId) { hashToId[hash] }
                        if (id != null) {
                            synchronized(handles) { handles[id] = h }
                            updateState(id, DownloadState.DOWNLOADING)
                        }
                        Log.d(TAG, "METADATA hash=$hash id=$id")
                    }
                    AlertType.TORRENT_FINISHED -> {
                        val h = (alert as TorrentFinishedAlert).handle()
                        val hash = h.infoHash().toString()
                        synchronized(hashToId) { hashToId[hash] }?.let {
                            updateState(it, DownloadState.FINISHED, 1f)
                        }
                    }
                    AlertType.TORRENT_ERROR -> {
                        val h = (alert as TorrentErrorAlert).handle()
                        val hash = h.infoHash().toString()
                        synchronized(hashToId) { hashToId[hash] }?.let {
                            updateState(it, DownloadState.ERROR)
                        }
                    }
                    else -> {}
                }
            }
        })

        // Настройки с DHT + bootstrap-нодами
        val sp = SettingsPack()
        sp.setEnableDht(true)
        sp.setDhtBootstrapNodes(
            "router.bittorrent.com:6881," +
            "dht.transmissionbt.com:6881," +
            "router.utorrent.com:6881," +
            "dht.libtorrent.org:25401"
        )
        sp.listenInterfaces("0.0.0.0:6881,[::]:6881")
        sp.broadcastLsd(true)
        // Активные загрузки
        sp.activeDownloads(8)

        session.start(org.libtorrent4j.SessionParams(sp))
        session.startDht()
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
        val hash = extractHash(magnet)
        if (hash != null) synchronized(hashToId) { hashToId[hash] = item.id }
        scope.launch {
            try {
                session.download(magnet, File(savePath), torrent_flags_t())
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
                val hash = ti.infoHash().toString()
                synchronized(hashToId) { hashToId[hash] = item.id }
                session.download(ti, File(savePath))
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
        if (h != null) try { session.remove(h) } catch (_: Exception) {}
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
                val state = when (s.state()) {
                    TorrentStatus.State.DOWNLOADING          -> DownloadState.DOWNLOADING
                    TorrentStatus.State.DOWNLOADING_METADATA -> DownloadState.FETCHING_META
                    TorrentStatus.State.FINISHED,
                    TorrentStatus.State.SEEDING              -> DownloadState.FINISHED
                    else                                     -> DownloadState.DOWNLOADING
                }
                _downloads.update { map ->
                    map + (id to item.copy(
                        state            = state,
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
