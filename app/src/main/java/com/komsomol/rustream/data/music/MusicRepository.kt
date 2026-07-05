package com.komsomol.rustream.data.music

import android.media.MediaMetadataRetriever
import com.komsomol.rustream.data.torrent.TorrentEngine
import com.komsomol.rustream.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val engine: TorrentEngine
) {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val audioExt = setOf("mp3", "flac", "ogg", "m4a", "wav", "opus", "aac")

    suspend fun scan() = withContext(Dispatchers.IO) {
        if (_scanning.value) return@withContext
        _scanning.value = true
        try {
            val files = mutableListOf<File>()
            val root = File(engine.savePath)
            if (root.exists()) collectAudio(root, files)
            files.sortBy { it.name.lowercase() }

            // Мгновенно показываем список по именам файлов
            val quick = files.map { f ->
                Track(path = f.absolutePath, fileName = f.name, title = f.nameWithoutExtension)
            }.toMutableList()
            _tracks.value = quick.toList()

            // Фоново обогащаем метаданными, обновляя список порциями
            for (i in files.indices) {
                quick[i] = readMeta(files[i])
                if (i % 10 == 9 || i == files.size - 1) {
                    _tracks.value = quick.toList()
                }
            }
        } finally {
            _scanning.value = false
        }
    }

    private fun collectAudio(dir: File, out: MutableList<File>) {
        dir.listFiles()?.forEach { f ->
            if (f.isDirectory) collectAudio(f, out)
            else if (f.extension.lowercase() in audioExt) out.add(f)
        }
    }

    private fun readMeta(f: File): Track {
        var title = f.nameWithoutExtension
        var artist: String? = null
        var dur = 0L
        try {
            val m = MediaMetadataRetriever()
            m.setDataSource(f.absolutePath)
            m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }?.let { title = it }
            artist = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            dur = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            m.release()
        } catch (_: Exception) {}
        return Track(f.absolutePath, f.name, title, artist, dur)
    }
}
