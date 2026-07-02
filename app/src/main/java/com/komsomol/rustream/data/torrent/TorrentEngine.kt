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
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorrentEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "TorrentEngine"
    // SupervisorJob + handler: падение одной корутины не роняет приложение
    private val crashHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
        Log.e("TorrentEngine", "Coroutine crash: " + e)
    }
    private val scope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob() + crashHandler)
    val session = SessionManager()
    private var started = false

    private val _downloads = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadItem>> = _downloads.asStateFlow()

    // Число узлов DHT — индикатор здоровья сети
    private val _dhtNodes = MutableStateFlow(0L)
    val dhtNodes: StateFlow<Long> = _dhtNodes.asStateFlow()

    // id -> TorrentHandle
    private val handles = mutableMapOf<String, TorrentHandle>()
    // infoHash -> id (для связи alert -> item)
    private val hashToId = mutableMapOf<String, String>()

    // Публичный Download если есть "доступ ко всем файлам", иначе папка приложения
    val savePath: String
        get() {
            val allFiles = android.os.Build.VERSION.SDK_INT < 30 ||
                Environment.isExternalStorageManager()
            return if (allFiles) {
                File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "RuStream").absolutePath
            } else {
                File(context.getExternalFilesDir(null), "RuStream").absolutePath
            }
        }

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
                        Log.d(TAG, "ADD_TORRENT hash=" + hash + " id=" + id)
                    }
                    AlertType.METADATA_RECEIVED -> {
                        val h = (alert as MetadataReceivedAlert).handle()
                        val hash = h.infoHash().toString()
                        val id = synchronized(hashToId) { hashToId[hash] }
                        if (id != null) {
                            synchronized(handles) { handles[id] = h }
                            // даже если успел сработать таймаут — оживляем загрузку
                            updateState(id, DownloadState.DOWNLOADING)
                        }
                        Log.d(TAG, "METADATA hash=" + hash + " id=" + id)
                    }
                    AlertType.TORRENT_FINISHED -> {
                        val h = (alert as TorrentFinishedAlert).handle()
                        val hash = h.infoHash().toString()
                        synchronized(hashToId) { hashToId[hash] }?.let {
                            updateState(it, DownloadState.FINISHED, 1f)
                        }
                    }
                    AlertType.TORRENT_ERROR -> {
                        val a = alert as TorrentErrorAlert
                        val hash = a.handle().infoHash().toString()
                        val msg: String = a.message()
                        val id2 = synchronized(hashToId) { hashToId[hash] }
                        if (id2 != null) updateState(id2, DownloadState.ERROR, error = msg)
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
        sp.activeDownloads(8)

        session.start(org.libtorrent4j.SessionParams(sp))
        session.startDht()
        File(savePath).mkdirs()

        scope.launch {
            while (true) {
                delay(1000)
                if (started) {
                    try { _dhtNodes.value = session.stats().dhtNodes() } catch (_: Exception) {}
                    pollProgress()
                }
            }
        }
        Log.d(TAG, "Started, savePath=" + savePath)
    }

    fun stop() {
        started = false
        try { session.stop() } catch (_: Exception) {}
    }

    fun addMagnet(item: DownloadItem) {
        val magnet = item.magnetUri ?: return
        _downloads.update { it + (item.id to item.copy(state = DownloadState.FETCHING_META, errorMessage = null)) }
        val hash = extractHash(magnet)
        if (hash != null) synchronized(hashToId) { hashToId[hash] = item.id }
        scope.launch {
            try {
                session.download(magnet, File(savePath), org.libtorrent4j.swig.torrent_flags_t())
            } catch (e: Exception) {
                Log.e(TAG, "addMagnet: " + e.message)
                updateState(item.id, DownloadState.ERROR, error = "Не удалось добавить: " + (e.message ?: "?"))
                return@launch
            }
            // Таймаут метаданных: если за 2 минуты пиров не нашлось — сообщаем
            delay(METADATA_TIMEOUT_MS)
            val cur = _downloads.value[item.id]
            if (cur != null && cur.state == DownloadState.FETCHING_META) {
                updateState(item.id, DownloadState.ERROR,
                    error = "Пиры не найдены за 2 мин (DHT: " + _dhtNodes.value +
                            " узлов). Раздача продолжится сама, если пиры появятся")
            }
        }
    }

    fun addTorrentFile(item: DownloadItem, bytes: ByteArray) {
        _downloads.update { it + (item.id to item.copy(state = DownloadState.DOWNLOADING, errorMessage = null)) }
        scope.launch {
            try {
                val ti = TorrentInfo.bdecode(bytes)
                val hash = ti.infoHash().toString()
                synchronized(hashToId) { hashToId[hash] = item.id }
                session.download(ti, File(savePath))
            } catch (e: Exception) {
                Log.e(TAG, "addTorrentFile: " + e.message)
                updateState(item.id, DownloadState.ERROR, error = "Битый .torrent: " + (e.message ?: "?"))
            }
        }
    }

    // Регистрация ошибки без добавления в сессию (например .torrent не скачался)
    fun addFailed(item: DownloadItem, message: String) {
        _downloads.update { it + (item.id to item.copy(state = DownloadState.ERROR, errorMessage = message)) }
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

    private fun updateState(id: String, state: DownloadState, progress: Float? = null, error: String? = null) {
        _downloads.update { map ->
            val item = map[id] ?: return@update map
            map + (id to item.copy(
                state = state,
                progress = progress ?: item.progress,
                errorMessage = if (state == DownloadState.ERROR) (error ?: item.errorMessage) else null
            ))
        }
    }

    companion object {
        private const val METADATA_TIMEOUT_MS = 120_000L

        // Поддерживает hex (40) и base32 (32) инфохэши
        fun extractHash(magnet: String): String? {
            val m = Regex("urn:btih:([a-fA-F0-9]{40}|[A-Za-z2-7]{32})", RegexOption.IGNORE_CASE)
                .find(magnet) ?: return null
            val raw = m.groupValues[1]
            return if (raw.length == 40) raw.lowercase() else base32ToHex(raw)
        }

        private fun base32ToHex(input: String): String? {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            var bits = 0
            var value = 0
            val out = StringBuilder()
            for (c in input.uppercase()) {
                val idx = alphabet.indexOf(c)
                if (idx < 0) return null
                value = (value shl 5) or idx
                bits += 5
                if (bits >= 8) {
                    bits -= 8
                    out.append(String.format("%02x", (value shr bits) and 0xFF))
                }
            }
            return if (out.length == 40) out.toString() else null
        }
    }
}
