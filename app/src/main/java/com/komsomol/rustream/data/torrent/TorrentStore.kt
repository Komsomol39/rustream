package com.komsomol.rustream.data.torrent

import android.content.Context
import android.util.Log
import com.komsomol.rustream.domain.model.DownloadItem
import com.komsomol.rustream.domain.model.DownloadState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Персистентный список раздач.
 *
 * Зачем: сессия libtorrent создаётся заново при каждом старте процесса и ничего
 * не помнит о прошлом запуске. До этого список загрузок жил только в памяти
 * TorrentEngine, поэтому после закрытия приложения (или его выгрузки системой)
 * вкладка "Загрузки" оказывалась пустой, хотя файлы лежали на диске.
 *
 * Хранит:
 *   filesDir/torrents/sessions.json — список раздач
 *   filesDir/torrents/<id>.torrent  — метаданные раздачи, если они у нас есть
 *
 * .torrent сохраняется, когда файл уже был у нас в руках (скачан с трекера или
 * выбран пользователем). При перезапуске раздача поднимается прямо из него:
 * не нужно ни ждать метаданные из DHT, ни второй раз идти на приватный трекер.
 */
class TorrentStore(context: Context) {

    private val TAG = "TorrentStore"
    private val dir = File(context.filesDir, "torrents").apply { mkdirs() }
    private val indexFile = File(dir, "sessions.json")
    private val lock = Any()

    data class Saved(
        val id: String,
        val title: String,
        val magnetUri: String?,
        val torrentUrl: String?,
        val savePath: String,
        val expectedBytes: Long,
        val addedAt: Long,
        val paused: Boolean,
        val finished: Boolean
    )

    fun torrentFile(id: String): File = File(dir, id + ".torrent")

    /**
     * Fast-resume: снимок состояния раздачи от самого libtorrent.
     * Позволяет при следующем запуске продолжить без перепроверки хэшей всех
     * файлов, а если снимок сделан с флагом SAVE_INFO_DICT — ещё и без
     * повторного получения метаданных из сети.
     */
    fun resumeFile(id: String): File = File(dir, id + ".resume")

    fun saveResumeData(id: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        try {
            val tmp = File(dir, id + ".resume.tmp")
            tmp.writeBytes(bytes)
            val target = resumeFile(id)
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        } catch (e: Exception) {
            Log.e(TAG, "saveResumeData: " + e)
        }
    }

    fun saveTorrentFile(id: String, bytes: ByteArray) {
        try {
            torrentFile(id).writeBytes(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "saveTorrentFile: " + e)
        }
    }

    fun load(): List<Saved> {
        synchronized(lock) {
            if (!indexFile.exists()) return emptyList()
            return try {
                val arr = JSONArray(indexFile.readText())
                (0 until arr.length()).mapNotNull { i -> parseEntry(arr, i) }
            } catch (e: Exception) {
                Log.e(TAG, "load: " + e)
                emptyList()
            }
        }
    }

    private fun parseEntry(arr: JSONArray, i: Int): Saved? {
        return try {
            val o = arr.getJSONObject(i)
            val magnet = o.optString("magnetUri")
            val url = o.optString("torrentUrl")
            Saved(
                id            = o.getString("id"),
                title         = o.optString("title", "Раздача"),
                magnetUri     = if (magnet.isEmpty()) null else magnet,
                torrentUrl    = if (url.isEmpty()) null else url,
                savePath      = o.optString("savePath"),
                expectedBytes = o.optLong("expectedBytes", 0L),
                addedAt       = o.optLong("addedAt", System.currentTimeMillis()),
                paused        = o.optBoolean("paused", false),
                finished      = o.optBoolean("finished", false)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun writeAll(list: List<Saved>) {
        try {
            val arr = JSONArray()
            for (s in list) {
                val o = JSONObject()
                o.put("id", s.id)
                o.put("title", s.title)
                o.put("magnetUri", s.magnetUri ?: "")
                o.put("torrentUrl", s.torrentUrl ?: "")
                o.put("savePath", s.savePath)
                o.put("expectedBytes", s.expectedBytes)
                o.put("addedAt", s.addedAt)
                o.put("paused", s.paused)
                o.put("finished", s.finished)
                arr.put(o)
            }
            // Пишем во временный файл и переименовываем: если процесс умрёт
            // посреди записи, прошлый индекс останется целым.
            val tmp = File(dir, "sessions.tmp")
            tmp.writeText(arr.toString())
            if (indexFile.exists()) indexFile.delete()
            tmp.renameTo(indexFile)
        } catch (e: Exception) {
            Log.e(TAG, "writeAll: " + e)
        }
    }

    /** Добавить или обновить запись по id. */
    fun put(item: DownloadItem) {
        synchronized(lock) {
            // Раздачи с ошибкой не переживают перезапуск: восстанавливать
            // битую ссылку незачем.
            if (item.state == DownloadState.ERROR) {
                removeInternal(item.id)
                return
            }
            val entry = Saved(
                id            = item.id,
                title         = item.title,
                magnetUri     = item.magnetUri,
                torrentUrl    = item.torrentUrl,
                savePath      = item.savePath,
                expectedBytes = item.expectedBytes,
                addedAt       = item.addedAt,
                paused        = item.state == DownloadState.PAUSED,
                finished      = item.state == DownloadState.FINISHED
            )
            writeAll(loadUnlocked().filter { it.id != item.id } + entry)
        }
    }

    /** Обновить только флаг паузы, не трогая остальное. */
    fun setPaused(id: String, paused: Boolean) {
        synchronized(lock) {
            val list = loadUnlocked()
            if (list.none { it.id == id }) return
            writeAll(list.map { if (it.id == id) it.copy(paused = paused) else it })
        }
    }

    fun setFinished(id: String) {
        synchronized(lock) {
            val list = loadUnlocked()
            if (list.none { it.id == id }) return
            writeAll(list.map { if (it.id == id) it.copy(finished = true, paused = false) else it })
        }
    }

    fun remove(id: String) {
        synchronized(lock) { removeInternal(id) }
    }

    private fun removeInternal(id: String) {
        val list = loadUnlocked()
        if (list.any { it.id == id }) writeAll(list.filter { it.id != id })
        try { torrentFile(id).delete() } catch (_: Exception) {}
        try { resumeFile(id).delete() } catch (_: Exception) {}
    }

    // Чтение без захвата lock: вызывается только изнутри synchronized-блоков
    // (перевход в synchronized на том же объекте безопасен, но так честнее)
    private fun loadUnlocked(): List<Saved> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).mapNotNull { i -> parseEntry(arr, i) }
        } catch (e: Exception) {
            Log.e(TAG, "loadUnlocked: " + e)
            emptyList()
        }
    }
}
