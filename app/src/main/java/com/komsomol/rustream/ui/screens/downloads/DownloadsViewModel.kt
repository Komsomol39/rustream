package com.komsomol.rustream.ui.screens.downloads

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komsomol.rustream.data.torrent.DownloadRepository
import com.komsomol.rustream.domain.model.DownloadItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: DownloadRepository
) : ViewModel() {

    val downloads = repo.downloads.map { it.values.sortedByDescending { d -> d.addedAt } }
    val dhtNodes  = repo.dhtNodes

    fun pause(item: DownloadItem)  = repo.pause(item.id)
    fun resume(item: DownloadItem) = repo.resume(item.id)
    fun remove(item: DownloadItem, deleteFiles: Boolean = false) = repo.remove(item.id, deleteFiles)

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
