package com.komsomol.rustream.data.torrent

import android.util.Log
import com.komsomol.rustream.data.search.KinozalCookieStore
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
    private val nnmCookies: NnmCookieStore,
    private val kinozalCookies: KinozalCookieStore
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
    private val kinozalClient = plainClient.newBuilder().cookieJar(kinozalCookies).build()

    private fun clientFor(source: SearchSource): OkHttpClient = when (source) {
        SearchSource.RUTRACKER -> rtClient
        SearchSource.NNM       -> nnmClient
        SearchSource.KINOZAL   -> kinozalClient
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
            expectedBytes = result.sizeBytes,
            state      = DownloadState.FETCHING_META
        )
        engine.addMagnet(item)
        Log.d(TAG, "Started magnet: " + id)
        id
    }

    // Запуск через .torrent файл
    suspend fun startTorrentUrl(result: SearchResult): String? = withContext(Dispatchers.IO) {
        var torrentUrl = result.torrentUrl ?: return@withContext null
        if (torrentUrl.startsWith("//")) torrentUrl = "https:" + torrentUrl
        val id = java.util.UUID.randomUUID().toString()
        val item = DownloadItem(
            id         = id,
            title      = result.title,
            magnetUri  = null,
            torrentUrl = torrentUrl,
            savePath   = engine.savePath,
            expectedBytes = result.sizeBytes,
            state      = DownloadState.DOWNLOADING
        )
        try {
            val req = Request.Builder().url(torrentUrl)
                .header("User-Agent", UA)
                .header("Referer", result.detailUrl)
                .build()
            val bytes = clientFor(result.source).newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    engine.addFailed(item, if (resp.code == 404) "Торрент удалён с трекера (404). Выберите другую версию." else "Ошибка " + resp.code + " при скачивании .torrent")
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

    // ---- Ручное добавление (кнопка "+" на вкладке Загрузки) ----

    /**
     * Принимает magnet-ссылку или прямую ссылку на .torrent.
     * Возвращает null при успехе, иначе текст ошибки для показа пользователю.
     */
    suspend fun addManualLink(rawInput: String): String? = withContext(Dispatchers.IO) {
        val input = rawInput.trim()
        if (input.isEmpty()) return@withContext "Пустая ссылка"

        if (input.startsWith("magnet:", ignoreCase = true)) {
            val hash = TorrentEngine.extractHash(input)
                ?: return@withContext "В магнет-ссылке нет инфохэша — проверьте, что она скопирована целиком"
            if (downloads.value.containsKey(hash)) return@withContext "Эта раздача уже в списке"
            val item = DownloadItem(
                id         = hash,
                title      = magnetTitle(input) ?: ("Раздача " + hash.take(8)),
                magnetUri  = enrichMagnet(input),
                torrentUrl = null,
                savePath   = engine.savePath,
                state      = DownloadState.FETCHING_META
            )
            engine.addMagnet(item)
            return@withContext null
        }

        if (!input.startsWith("http://", true) && !input.startsWith("https://", true)) {
            return@withContext "Нужна magnet-ссылка или ссылка http(s) на .torrent"
        }

        // Ссылка на .torrent: качаем без кук — это публичный адрес.
        // Для приватных трекеров пользователь скачивает файл браузером
        // и добавляет его кнопкой "Файл".
        val id = java.util.UUID.randomUUID().toString()
        val item = DownloadItem(
            id         = id,
            title      = input.substringAfterLast('/').substringBefore('?')
                            .removeSuffix(".torrent").ifBlank { "Загрузка" },
            magnetUri  = null,
            torrentUrl = input,
            savePath   = engine.savePath,
            state      = DownloadState.DOWNLOADING
        )
        return@withContext try {
            val req = Request.Builder().url(input).header("User-Agent", UA).build()
            val bytes = plainClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext "Сервер ответил " + resp.code
                resp.body?.bytes()
            }
            if (bytes == null || bytes.isEmpty()) {
                "Пустой ответ по этой ссылке"
            } else if (bytes[0] != 'd'.code.toByte()) {
                "По ссылке лежит не .torrent (похоже на HTML-страницу). " +
                "Если трекер требует вход — скачайте файл браузером и добавьте " +
                "кнопкой «Выбрать .torrent файл»"
            } else {
                engine.addTorrentFile(item, bytes)
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "addManualLink: " + e.message)
            "Не удалось скачать: " + (e.message ?: "?")
        }
    }

    /** Добавление .torrent, выбранного в файловом менеджере. */
    suspend fun addManualTorrentBytes(name: String, bytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            if (bytes.isEmpty()) return@withContext "Файл пустой"
            if (bytes[0] != 'd'.code.toByte())
                return@withContext "Это не .torrent — файл должен начинаться с bencode-словаря"
            val item = DownloadItem(
                id         = java.util.UUID.randomUUID().toString(),
                title      = name.removeSuffix(".torrent").ifBlank { "Загрузка" },
                magnetUri  = null,
                torrentUrl = null,
                savePath   = engine.savePath,
                state      = DownloadState.DOWNLOADING
            )
            engine.addTorrentFile(item, bytes)
            null
        }

    // Имя раздачи из параметра dn= магнет-ссылки
    private fun magnetTitle(magnet: String): String? = try {
        Regex("[?&]dn=([^&]+)").find(magnet)?.groupValues?.get(1)
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            ?.replace('+', ' ')?.trim()?.ifBlank { null }
    } catch (_: Exception) { null }

    suspend fun getFiles(id: String) = engine.getFiles(id)
    fun setFileEnabled(id: String, index: Int, enabled: Boolean) =
        engine.setFileEnabled(id, index, enabled)
    fun setAllFilesEnabled(id: String, enabled: Boolean) =
        engine.setAllFilesEnabled(id, enabled)

    fun pause(id: String)  = engine.pause(id)
    fun resume(id: String) = engine.resume(id)
    fun remove(id: String, deleteFiles: Boolean = false) = engine.remove(id, deleteFiles)

    companion object {
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
    }
}
