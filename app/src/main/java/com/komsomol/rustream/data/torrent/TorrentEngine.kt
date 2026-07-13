package com.komsomol.rustream.data.torrent

import android.content.Context
import android.os.Environment
import android.util.Log
import com.komsomol.rustream.domain.model.DownloadItem
import com.komsomol.rustream.domain.model.DownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
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

    // ВСЕ обращения к libtorrent идут через этот единственный поток.
    // Во время проверки хэшей вызовы вроде fileProgress() блокируются
    // занятым диском: попади они на main — приложение фризит.
    // Один поток заодно убирает конкуренцию за мьютекс сессии.
    private val engineDispatcher: kotlinx.coroutines.CoroutineDispatcher =
        java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "torrent-engine")
        }.asCoroutineDispatcher()
    val session = SessionManager()
    private var started = false

    private val _downloads = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadItem>> = _downloads.asStateFlow()

    // Число узлов DHT — индикатор здоровья сети
    private val _dhtNodes = MutableStateFlow(0L)
    val dhtNodes: StateFlow<Long> = _dhtNodes.asStateFlow()

    // infoHash -> id (для связи alert -> item)
    private val hashToId = mutableMapOf<String, String>()
    // id -> infoHash. ВАЖНО: хэндлы из alert'ов хранить НЕЛЬЗЯ (их память
    // переиспользуется движком -> use-after-free -> SIGSEGV). Берём свежий
    // хэндл у сессии по хэшу каждый раз.
    private val idToHash = mutableMapOf<String, String>()

    private fun findHandle(id: String): TorrentHandle? {
        val hash = synchronized(idToHash) { idToHash[id] } ?: return null
        return try {
            session.find(org.libtorrent4j.Sha1Hash.parseHex(hash))
        } catch (_: Exception) { null }
    }

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
            override fun types(): IntArray = intArrayOf(
                AlertType.ADD_TORRENT.swig(),
                AlertType.METADATA_RECEIVED.swig(),
                AlertType.TORRENT_FINISHED.swig(),
                AlertType.TORRENT_ERROR.swig()
            )
            override fun alert(alert: Alert<*>) {
                try {
                when (alert.type()) {
                    AlertType.ADD_TORRENT -> {
                        val hash = (alert as AddTorrentAlert).handle().infoHash().toString()
                        val id = synchronized(hashToId) { hashToId[hash] }
                        if (id != null) synchronized(idToHash) { idToHash[id] = hash }
                    }
                    AlertType.METADATA_RECEIVED -> {
                        val h = (alert as MetadataReceivedAlert).handle()
                        val hash = h.infoHash().toString()
                        val id = synchronized(hashToId) { hashToId[hash] }
                        if (id != null) {
                            synchronized(idToHash) { idToHash[id] = hash }
                            // Защита от YTS-клонов: реальный размер раздачи против
                            // ожидаемого из поиска. Фейк с одной рекламой весит
                            // сотни КБ вместо гигабайтов — режем, если меньше 20%.
                            val expected = _downloads.value[id]?.expectedBytes ?: 0L
                            val actual = try { h.torrentFile()?.totalSize() ?: 0L } catch (_: Exception) { 0L }
                            if (expected > 50_000_000L && actual in 1 until (expected / 5)) {
                                try { session.remove(h) } catch (_: Exception) {}
                                updateState(id, DownloadState.ERROR,
                                    error = "Раздача недоступна: на трекере лежит " +
                                            fmtSize(actual) + " вместо " + fmtSize(expected) +
                                            ". Скорее всего она удалена или битая — " +
                                            "выберите другую версию.")
                            } else {
                                // даже если успел сработать таймаут — оживляем загрузку
                                updateState(id, DownloadState.DOWNLOADING)
                            }
                        }
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
                } catch (e: Throwable) {
                    Log.e(TAG, "alert handler: " + e)
                }
            }
        })

        // Настройки с DHT + bootstrap-нодами
        val sp = SettingsPack()
        sp.setBoolean(org.libtorrent4j.swig.settings_pack.bool_types.enable_dht.swigValue(), true)
        sp.setString(org.libtorrent4j.swig.settings_pack.string_types.dht_bootstrap_nodes.swigValue(),
            "router.bittorrent.com:6881," +
            "dht.transmissionbt.com:6881," +
            "router.utorrent.com:6881," +
            "dht.libtorrent.org:25401"
        )
        sp.listenInterfaces("0.0.0.0:6881,[::]:6881")
        sp.activeDownloads(8)

        val params = org.libtorrent4j.SessionParams(sp)
        // КРИТИЧНО: mmap-ввод/вывод libtorrent 2.x падает с SIGBUS на Android (FUSE).
        // posix-бэкенд пишет файлы обычным способом — как libtorrent 1.2.
        params.setPosixDiskIO()
        session.start(params)
        session.startDht()
        File(savePath).mkdirs()

        scope.launch(engineDispatcher) {
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
        if (hash != null) {
            synchronized(hashToId) { hashToId[hash] = item.id }
            synchronized(idToHash) { idToHash[item.id] = hash }
        }
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
                synchronized(idToHash) { idToHash[item.id] = hash }
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
        updateState(id, DownloadState.PAUSED)
        scope.launch(engineDispatcher) {
            try { findHandle(id)?.pause() } catch (_: Exception) {}
        }
    }

    fun resume(id: String) {
        updateState(id, DownloadState.DOWNLOADING)
        scope.launch(engineDispatcher) {
            try { findHandle(id)?.resume() } catch (_: Exception) {}
        }
    }

    fun remove(id: String, deleteFiles: Boolean = false) {
        _downloads.update { it - id }
        scope.launch(engineDispatcher) {
            val h = findHandle(id)
            if (h != null) try {
                if (deleteFiles) {
                    org.libtorrent4j.SessionHandle(session.swig())
                        .removeTorrent(h, org.libtorrent4j.SessionHandle.DELETE_FILES)
                } else {
                    session.remove(h)
                }
            } catch (_: Exception) {}
            synchronized(idToHash) { idToHash.remove(id) }
        }
    }

    // Список файлов раздачи (доступен после метаданных)
    suspend fun getFiles(id: String): List<com.komsomol.rustream.domain.model.TorrentFileEntry> =
        kotlinx.coroutines.withContext(engineDispatcher) {
        val h = findHandle(id) ?: return@withContext emptyList()
        try {
            if (!h.isValid) return@withContext emptyList()
            val ti = h.torrentFile() ?: return@withContext emptyList()
            val fs = ti.files()
            // Во время проверки хэшей diskthread занят — прогресс по файлам
            // не запрашиваем, чтобы не тормозить проверку
            val checking = try {
                h.status().state() == TorrentStatus.State.CHECKING_FILES
            } catch (_: Exception) { false }
            val progress = if (checking) LongArray(0)
                else try { h.fileProgress() } catch (_: Exception) { LongArray(0) }
            (0 until fs.numFiles()).map { i ->
                com.komsomol.rustream.domain.model.TorrentFileEntry(
                    index           = i,
                    name            = fs.filePath(i),
                    sizeBytes       = fs.fileSize(i),
                    downloadedBytes = if (i < progress.size) progress[i] else 0L,
                    enabled         = h.filePriority(i) != org.libtorrent4j.Priority.IGNORE
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun setFileEnabled(id: String, index: Int, enabled: Boolean) = scope.launch(engineDispatcher) {
        val h = findHandle(id) ?: return@launch
        try {
            h.filePriority(index,
                if (enabled) org.libtorrent4j.Priority.DEFAULT
                else org.libtorrent4j.Priority.IGNORE)
            if (enabled) h.resume()
        } catch (_: Exception) {}
    }

    fun setAllFilesEnabled(id: String, enabled: Boolean) = scope.launch(engineDispatcher) {
        val h = findHandle(id) ?: return@launch
        try {
            val ti = h.torrentFile() ?: return@launch
            val p = if (enabled) org.libtorrent4j.Priority.DEFAULT
                    else org.libtorrent4j.Priority.IGNORE
            h.prioritizeFiles(Array(ti.numFiles()) { p })
            if (enabled) h.resume()
        } catch (_: Exception) {}
    }

    private fun pollProgress() {
        val current = _downloads.value
        current.forEach { (id, item) ->
            if (item.state == DownloadState.ERROR) return@forEach
            val h = findHandle(id) ?: return@forEach
            try {
                if (!h.isValid) return@forEach
                val s = h.status()
                val state = when (s.state()) {
                    TorrentStatus.State.DOWNLOADING           -> DownloadState.DOWNLOADING
                    TorrentStatus.State.DOWNLOADING_METADATA  -> DownloadState.FETCHING_META
                    TorrentStatus.State.CHECKING_FILES,
                    TorrentStatus.State.CHECKING_RESUME_DATA  -> DownloadState.CHECKING
                    TorrentStatus.State.FINISHED,
                    TorrentStatus.State.SEEDING               -> DownloadState.FINISHED
                    else                                      -> DownloadState.DOWNLOADING
                }
                // Пауза, поставленная пользователем, важнее опроса —
                // иначе тикер перезатрёт её через секунду
                if (item.state == DownloadState.PAUSED &&
                    state == DownloadState.DOWNLOADING) return@forEach
                _downloads.update { map ->
                    map + (id to item.copy(
                        state            = state,
                        progress         = s.progress(),
                        downloadedBytes  = s.totalDone(),
                        totalBytes       = s.totalWanted(),
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

    private fun fmtSize(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.1f ГБ".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576     -> "%.0f МБ".format(bytes / 1_048_576.0)
        bytes >= 1024          -> "%.0f КБ".format(bytes / 1024.0)
        else                   -> "$bytes Б"
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
