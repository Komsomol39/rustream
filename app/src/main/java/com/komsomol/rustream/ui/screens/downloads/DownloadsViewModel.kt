package com.komsomol.rustream.ui.screens.downloads

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komsomol.rustream.data.torrent.DownloadRepository
import com.komsomol.rustream.domain.model.DownloadItem
import com.komsomol.rustream.domain.model.DownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Список загрузок, разделённый чертой "Неактивные".
 * Выше черты — то, что качается или ждёт очереди, ниже — поставленное на паузу.
 */
data class DownloadsList(
    val active: List<DownloadItem> = emptyList(),
    val inactive: List<DownloadItem> = emptyList()
) {
    val all: List<DownloadItem> get() = active + inactive
    val isEmpty: Boolean get() = active.isEmpty() && inactive.isEmpty()
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: DownloadRepository
) : ViewModel() {

    val dhtNodes = repo.dhtNodes

    /**
     * Порядок берётся из очереди движка. Раздачи, которых в нём почему-то нет
     * (например, добавленные до появления очереди), уходят в конец — теряться
     * они не должны.
     */
    val list: StateFlow<DownloadsList> =
        combine(repo.downloads, repo.order) { map, order ->
            val ranked = map.values.sortedWith(
                compareBy({ order.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                          { -it.addedAt })
            )
            DownloadsList(
                active   = ranked.filter { it.state != DownloadState.PAUSED },
                inactive = ranked.filter { it.state == DownloadState.PAUSED }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadsList())

    fun pause(item: DownloadItem)  = repo.pause(item.id)
    fun resume(item: DownloadItem) = repo.resume(item.id)
    fun remove(item: DownloadItem, deleteFiles: Boolean = false) = repo.remove(item.id, deleteFiles)

    // ---- Перетаскивание ----

    /**
     * Зафиксировать новый порядок. Пришедший список — плоский, в нём уже учтено
     * положение относительно черты: всё, что оказалось на позиции activeCount
     * и ниже, ставится на паузу, остальное снимается с неё.
     */
    fun applyOrder(ids: List<String>, dividerId: String) {
        val cut = ids.indexOf(dividerId)
        val real = ids.filter { it != dividerId }
        repo.reorder(real)
        if (cut < 0) return
        val map = repo.downloads.value
        ids.forEachIndexed { i, id ->
            if (id == dividerId) return@forEachIndexed
            val item = map[id] ?: return@forEachIndexed
            val shouldPause = i > cut
            if (shouldPause && item.state != DownloadState.PAUSED) repo.pause(id)
            if (!shouldPause && item.state == DownloadState.PAUSED) repo.resume(id)
        }
    }

    // ---- Множественный выбор ----

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    val selectMode: StateFlow<Boolean> =
        _selected.map { it.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleSelect(id: String) {
        _selected.value = _selected.value.let { if (it.contains(id)) it - id else it + id }
    }

    fun clearSelection() { _selected.value = emptySet() }

    fun selectAll() { _selected.value = repo.downloads.value.keys.toSet() }

    fun pauseSelected() {
        _selected.value.forEach { repo.pause(it) }
        clearSelection()
    }

    fun resumeSelected() {
        _selected.value.forEach { repo.resume(it) }
        clearSelection()
    }

    fun removeSelected(deleteFiles: Boolean) {
        _selected.value.forEach { repo.remove(it, deleteFiles) }
        clearSelection()
    }

    // ---- Ручное добавление ----

    private val _addError = MutableStateFlow<String?>(null)
    val addError: StateFlow<String?> = _addError.asStateFlow()

    private val _adding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = _adding.asStateFlow()

    fun clearAddError() { _addError.value = null }

    fun addLink(input: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _adding.value = true
            val err = repo.addManualLink(input)
            _adding.value = false
            _addError.value = err
            if (err == null) onDone()
        }
    }

    fun addFile(uri: Uri, onDone: () -> Unit) {
        viewModelScope.launch {
            _adding.value = true
            val err = try {
                val name = queryName(context, uri) ?: "Загрузка"
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes == null) "Не удалось прочитать файл"
                else repo.addManualTorrentBytes(name, bytes)
            } catch (e: Exception) {
                "Ошибка чтения файла: " + (e.message ?: "?")
            }
            _adding.value = false
            _addError.value = err
            if (err == null) onDone()
        }
    }

    private fun queryName(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (_: Exception) { null }
}
