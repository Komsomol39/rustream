package com.komsomol.rustream.data.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.komsomol.rustream.data.torrent.TorrentEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import com.komsomol.rustream.domain.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: TorrentEngine
) {
    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val videoExt = setOf("mkv", "mp4", "avi", "webm", "mov", "ts", "m4v")

    suspend fun scan() = withContext(Dispatchers.IO) {
        if (_scanning.value) return@withContext
        _scanning.value = true
        try {
            val files = mutableListOf<File>()
            val root = File(engine.savePath)
            if (root.exists()) collectVideo(root, files)
            files.sortBy { it.name.lowercase() }

            val quick = files.map { f ->
                VideoItem(path = f.absolutePath, fileName = f.name,
                    title = f.nameWithoutExtension, sizeBytes = f.length())
            }.toMutableList()
            _videos.value = quick.toList()

            for (i in files.indices) {
                quick[i] = quick[i].copy(
                    durationMs = readDuration(files[i]),
                    thumbPath  = thumbFor(files[i])
                )
                if (i % 3 == 2 || i == files.size - 1) _videos.value = quick.toList()
            }
        } finally {
            _scanning.value = false
        }
    }

    fun deleteFile(path: String) {
        try { File(path).delete() } catch (_: Exception) {}
        _videos.value = _videos.value.filterNot { it.path == path }
    }

    private fun collectVideo(dir: File, out: MutableList<File>) {
        dir.listFiles()?.forEach { f ->
            if (f.isDirectory) collectVideo(f, out)
            else if (f.extension.lowercase() in videoExt) out.add(f)
        }
    }

    // Кадр из видео -> jpg в кеше приложения. Ключ включает размер файла,
    // чтобы превью пересоздалось, если файл докачался/заменился.
    private fun thumbFor(f: File): String? = try {
        val dir = File(context.cacheDir, "video_thumbs").apply { mkdirs() }
        val out = File(dir, f.absolutePath.hashCode().toString() + "_" + f.length() + ".jpg")
        if (out.exists()) {
            out.absolutePath
        } else {
            val m = MediaMetadataRetriever()
            m.setDataSource(f.absolutePath)
            // кадр с 10-й секунды (титры/чёрный экран в начале пропускаем)
            val bmp = m.getFrameAtTime(10_000_000L)
                ?: m.getFrameAtTime(0L)
            m.release()
            if (bmp == null) null
            else {
                val w = 320
                val h = (w.toFloat() * bmp.height / bmp.width).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
                out.outputStream().use {
                    scaled.compress(Bitmap.CompressFormat.JPEG, 80, it)
                }
                out.absolutePath
            }
        }
    } catch (_: Exception) { null }

    private fun readDuration(f: File): Long = try {
        val m = MediaMetadataRetriever()
        m.setDataSource(f.absolutePath)
        val d = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        m.release()
        d
    } catch (_: Exception) { 0L }
}
