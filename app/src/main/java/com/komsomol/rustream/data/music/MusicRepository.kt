package com.komsomol.rustream.data.music

import android.media.MediaMetadataRetriever
import com.komsomol.rustream.data.torrent.TorrentEngine
import com.komsomol.rustream.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val engine: TorrentEngine,
    private val mergeStore: ArtistMergeStore,
    private val settings: com.komsomol.rustream.data.settings.SettingsRepository
) {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    // Список исполнителей с учётом правил объединения
    val artists: kotlinx.coroutines.flow.Flow<List<com.komsomol.rustream.domain.model.ArtistGroup>> =
        kotlinx.coroutines.flow.combine(_tracks, mergeStore.mergeMap) { tracks, merge ->
            val byCanonical = LinkedHashMap<String, MutableList<Track>>()
            val members = LinkedHashMap<String, LinkedHashSet<String>>()
            for (t in tracks.sortedBy { it.title.lowercase() }) {
                val raw = t.artist?.takeIf { it.isNotBlank() } ?: "Неизвестный исполнитель"
                val canonical = merge[raw] ?: raw
                byCanonical.getOrPut(canonical) { mutableListOf() }.add(t)
                members.getOrPut(canonical) { LinkedHashSet() }.add(raw)
            }
            byCanonical.map { (name, ts) ->
                com.komsomol.rustream.domain.model.ArtistGroup(
                    displayName = name,
                    memberNames = members[name]?.toList() ?: listOf(name),
                    tracks = ts
                )
            }.sortedBy { it.displayName.lowercase() }
        }

    suspend fun mergeArtists(names: List<String>) = mergeStore.merge(names)
    suspend fun unmergeArtist(name: String) = mergeStore.unmerge(name)

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val audioExt = setOf("mp3", "flac", "ogg", "m4a", "wav", "opus", "aac")

    suspend fun scan() = withContext(Dispatchers.IO) {
        if (_scanning.value) return@withContext
        _scanning.value = true
        try {
            val files = mutableListOf<File>()
            val roots = mutableListOf(engine.savePath)
            roots.addAll(settings.mediaFolders.first())
            roots.distinct().forEach { path ->
                val root = File(path)
                if (root.exists()) collectAudio(root, files)
            }
            // убрать дубли по пути (папки могут пересекаться)
            val seen = HashSet<String>()
            files.retainAll { seen.add(it.absolutePath) }
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

    fun deleteFile(path: String) {
        try { File(path).delete() } catch (_: Exception) {}
        _tracks.value = _tracks.value.filterNot { it.path == path }
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
