package com.komsomol.rustream.data.torrent

import android.os.Environment
import com.komsomol.rustream.domain.model.DownloadItem
import com.komsomol.rustream.domain.model.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val engine: TorrentEngine
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val saveDir: String
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "RuStream"
        ).absolutePath

    suspend fun startDownload(
        title: String,
        magnetUri: String?,
        torrentUrl: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        val id = generateId(magnetUri, torrentUrl)

        // Проверяем — не скачивается ли уже
        val existing = engine.downloads.value[id]
        if (existing != null && existing.state != DownloadState.ERROR) {
            return@withContext Result.failure(Exception("Уже в загрузках"))
        }

        val item = DownloadItem(
            id = id,
            title = title,
            magnetUri = magnetUri,
            torrentUrl = torrentUrl,
            savePath = saveDir
        )

        when {
            magnetUri != null -> {
                engine.addMagnet(item)
                Result.success(id)
            }
            torrentUrl != null -> {
                // Скачиваем .torrent файл
                try {
                    val bytes = fetchTorrentFile(torrentUrl)
                    engine.addTorrentFile(item, bytes)
                    Result.success(id)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            else -> Result.failure(Exception("Нет magnet и torrent ссылки"))
        }
    }

    private fun fetchTorrentFile(url: String): ByteArray {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()
        return client.newCall(req).execute().use {
            it.body?.bytes() ?: throw Exception("Пустой ответ")
        }
    }

    private fun generateId(magnetUri: String?, torrentUrl: String?): String {
        // Для magnet — используем btih хэш как ID
        if (magnetUri != null) {
            val hash = magnetUri.substringAfter("btih:", "")
                .substringBefore("&").lowercase()
            if (hash.length == 40) return hash
        }
        return torrentUrl?.hashCode()?.toString() ?: UUID.randomUUID().toString()
    }

    fun pause(id: String) = engine.pause(id)
    fun resume(id: String) = engine.resume(id)
    fun remove(id: String) = engine.remove(id)
}
