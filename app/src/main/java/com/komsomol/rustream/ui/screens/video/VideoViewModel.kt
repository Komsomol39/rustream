package com.komsomol.rustream.ui.screens.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komsomol.rustream.data.video.VideoRepository
import com.komsomol.rustream.domain.model.VideoItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Папка в дереве вкладки Видео. */
data class VideoFolder(
    val path: String,
    val name: String,
    val videoCount: Int
)

/**
 * Что показывать в текущей точке дерева.
 * dir == null означает список корней (папка загрузок + пользовательские папки).
 */
data class VideoBrowse(
    val dir: String? = null,
    val title: String = "Видео",
    val canGoUp: Boolean = false,
    val folders: List<VideoFolder> = emptyList(),
    val files: List<VideoItem> = emptyList(),
    val totalCount: Int = 0
)

@HiltViewModel
class VideoViewModel @Inject constructor(
    private val repo: VideoRepository
) : ViewModel() {

    val scanning = repo.scanning

    // null = ещё не заходили никуда; фактическая папка вычисляется из корней
    private val _dir = MutableStateFlow<String?>(null)

    /**
     * Дерево строится из плоского списка файлов, а не отдельным обходом диска.
     * Скан уже прошёл рекурсивно и знает про все видео, поэтому папки можно
     * получить разбором путей — и в списке гарантированно не появится папка,
     * внутри которой видео нет.
     */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(q: String) { _query.value = q }

    val browse = combine(repo.videos, repo.roots, _dir, _query) { videos, roots, dir, q ->
        val needle = q.trim().lowercase()
        if (needle.isNotEmpty()) {
            // Поиск идёт по всему дереву и по всем корням сразу, включая
            // внешние папки: текущая директория при этом не учитывается
            val found = videos.filter {
                it.title.lowercase().contains(needle) || it.fileName.lowercase().contains(needle)
            }
            return@combine VideoBrowse(
                dir = dir, title = "Найдено", canGoUp = false,
                folders = emptyList(), files = found, totalCount = found.size
            )
        }
        val effective = dir ?: roots.singleOrNull()
        if (effective == null) {
            // Корней несколько (или ни одного) — показываем их как папки
            val folders = roots.map { r ->
                VideoFolder(
                    path = r,
                    name = File(r).name.ifBlank { r },
                    videoCount = videos.count { it.path.startsWith(r.trimEnd('/') + "/") }
                )
            }.sortedBy { it.name.lowercase() }
            VideoBrowse(
                dir = null, title = "Видео", canGoUp = false,
                folders = folders, files = emptyList(), totalCount = videos.size
            )
        } else {
            val prefix = effective.trimEnd('/') + "/"
            val inside = videos.filter { it.path.startsWith(prefix) }

            val files = inside.filter { !it.path.removePrefix(prefix).contains('/') }

            val folders = inside.mapNotNull { v ->
                val rel = v.path.removePrefix(prefix)
                if (rel.contains('/')) rel.substringBefore('/') else null
            }.groupingBy { it }.eachCount()
                .map { (name, count) -> VideoFolder(prefix + name, name, count) }
                .sortedBy { it.name.lowercase() }

            val isRoot = roots.any { it.trimEnd('/') == effective.trimEnd('/') }
            VideoBrowse(
                dir = effective,
                title = if (isRoot && roots.size <= 1) "Видео"
                        else File(effective).name.ifBlank { effective },
                canGoUp = !isRoot || roots.size > 1,
                folders = folders,
                files = files,
                totalCount = inside.size
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VideoBrowse())

    init { refresh() }

    fun open(folder: VideoFolder) { _dir.value = folder.path }

    /**
     * Встать в папку, где лежит файл. Нужно после просмотра только что
     * скачанного видео: закрыв плеер, пользователь оказывается там же,
     * куда файл сохранился, а не в корне.
     */
    fun openFolderOf(path: String) {
        val parent = File(path).parent ?: return
        _query.value = ""
        _dir.value = parent
    }

    fun goUp() {
        val cur = _dir.value ?: return
        val roots = repo.roots.value
        // Из корня поднимаемся к списку корней, но только если их несколько
        if (roots.any { it.trimEnd('/') == cur.trimEnd('/') }) {
            if (roots.size > 1) _dir.value = null
            return
        }
        _dir.value = File(cur).parent
    }

    fun refresh() = viewModelScope.launch { repo.scan() }
    fun delete(path: String) = repo.deleteFile(path)
}
