package com.komsomol.rustream.ui.screens.downloads

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komsomol.rustream.domain.model.DownloadItem
import com.komsomol.rustream.domain.model.DownloadState

/** Псевдоэлемент списка: черта, отделяющая неактивные раздачи. */
private const val DIVIDER_ID = "__divider__"

@Composable
fun DownloadsScreen(
    onOpen: (String) -> Unit = {},
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val list by viewModel.list.collectAsState()
    val dhtNodes by viewModel.dhtNodes.collectAsState(initial = 0L)
    val selected by viewModel.selected.collectAsState()
    val selectMode by viewModel.selectMode.collectAsState()
    var confirmRemove by remember { mutableStateOf<DownloadItem?>(null) }
    var confirmRemoveSelected by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    // Пока идёт перетаскивание, список берётся отсюда: сортировка из движка
    // придёт только после отпускания, иначе карточка прыгала бы под пальцем
    var dragIds by remember { mutableStateOf<List<String>?>(null) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // Черта — полноценный элемент списка, а не рисунок между карточками.
    // Иначе за последнюю карточку было не перетащить: индекс не мог стать
    // больше числа активных, и раздача никогда не уходила на паузу.
    val baseIds = list.active.map { it.id } + DIVIDER_ID + list.inactive.map { it.id }
    val shownIds = dragIds ?: baseIds
    val byId = list.all.associateBy { it.id }

    BackHandler(enabled = selectMode) { viewModel.clearSelection() }

    confirmRemove?.let { target ->
        var deleteFiles by remember(target.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Удалить раздачу?") },
            text = {
                Column {
                    Text(target.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { deleteFiles = !deleteFiles }) {
                        Checkbox(checked = deleteFiles, onCheckedChange = { deleteFiles = it })
                        Text("Удалить скачанные файлы с устройства")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(target, deleteFiles)
                    confirmRemove = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text("Отмена") }
            }
        )
    }

    if (showAdd) {
        AddTorrentDialog(
            viewModel = viewModel,
            onDismiss = { showAdd = false }
        )
    }

    if (confirmRemoveSelected) {
        var deleteFiles by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirmRemoveSelected = false },
            title = { Text("Удалить " + selected.size + " раздач?") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { deleteFiles = !deleteFiles }) {
                    Checkbox(checked = deleteFiles, onCheckedChange = { deleteFiles = it })
                    Text("Удалить скачанные файлы с устройства")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeSelected(deleteFiles); confirmRemoveSelected = false
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveSelected = false }) { Text("Отмена") }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (list.isEmpty) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Нет загрузок", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Найдите раздачу в поиске или добавьте её кнопкой «+»",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("DHT: " + dhtNodes + " узлов",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                if (selectMode) {
                    SelectionBar(
                        count       = selected.size,
                        onPause     = { viewModel.pauseSelected() },
                        onResume    = { viewModel.resumeSelected() },
                        onRemove    = { confirmRemoveSelected = true },
                        onSelectAll = { viewModel.selectAll() },
                        onCancel    = { viewModel.clearSelection() }
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
                ) {
                    item {
                        Text("DHT: " + dhtNodes + " узлов",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    itemsIndexed(shownIds, key = { _, id -> id }) { _, id ->
                        val item = byId[id]
                        if (id == DIVIDER_ID) {
                            InactiveDivider()
                        } else if (item != null) {
                            DownloadCard(
                                item      = item,
                                dragging  = dragId == id,
                                dragDelta = if (dragId == id) dragOffset else 0f,
                                selected  = selected.contains(id),
                                selectMode = selectMode,
                                onClick   = {
                                    if (selectMode) viewModel.toggleSelect(id) else onOpen(id)
                                },
                                onLongClick = { viewModel.toggleSelect(id) },
                                onPause   = { viewModel.pause(item) },
                                onResume  = { viewModel.resume(item) },
                                onRemove  = { confirmRemove = item },
                                onDragStart = {
                                    dragIds = shownIds
                                    dragId = id
                                    dragOffset = 0f
                                },
                                onDrag = { delta, rowHeight ->
                                    // Сдвиг накопился больше высоты строки —
                                    // меняем соседей местами и оставляем остаток.
                                    // Так не нужен разбор попаданий по координатам.
                                    dragOffset += delta
                                    var cur = dragIds
                                    if (cur != null && rowHeight > 0f) {
                                        var at = cur.indexOf(id)
                                        while (at >= 0 && dragOffset > rowHeight && at < cur!!.size - 1) {
                                            val m = cur!!.toMutableList()
                                            m.add(at + 1, m.removeAt(at))
                                            cur = m
                                            at += 1
                                            dragOffset -= rowHeight
                                        }
                                        while (at > 0 && dragOffset < -rowHeight) {
                                            val m = cur!!.toMutableList()
                                            m.add(at - 1, m.removeAt(at))
                                            cur = m
                                            at -= 1
                                            dragOffset += rowHeight
                                        }
                                        dragIds = cur
                                    }
                                },
                                onDragEnd = {
                                    dragIds?.let { viewModel.applyOrder(it, DIVIDER_ID) }
                                    dragIds = null
                                    dragId = null
                                    dragOffset = 0f
                                }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Добавить торрент")
        }
    }
}

/**
 * Ручное добавление: magnet-ссылка, прямая ссылка на .torrent
 * или .torrent-файл с устройства.
 */
@Composable
private fun AddTorrentDialog(
    viewModel: DownloadsViewModel,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val adding by viewModel.adding.collectAsState()
    val error by viewModel.addError.collectAsState()
    var link by remember { mutableStateOf("") }

    // Магнет в буфере обмена — самый частый сценарий, подставляем сразу
    LaunchedEffect(Unit) {
        viewModel.clearAddError()
        val clip = clipboard.getText()?.text?.trim().orEmpty()
        if (clip.startsWith("magnet:", true) ||
            (clip.startsWith("http", true) && clip.contains(".torrent"))) {
            link = clip
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.addFile(uri) { onDismiss() }
    }

    AlertDialog(
        onDismissRequest = { if (!adding) onDismiss() },
        title = { Text("Добавить торрент") },
        text = {
            Column {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it; viewModel.clearAddError() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("magnet: или ссылка на .torrent") },
                    placeholder = { Text("magnet:?xt=urn:btih:...") },
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.let { link = it.trim() }
                        }) { Icon(Icons.Default.ContentPaste, contentDescription = "Вставить") }
                    },
                    isError = error != null,
                    maxLines = 3,
                    enabled = !adding
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.clearAddError()
                        // Многие файловые менеджеры не проставляют MIME-тип
                        // торрента, поэтому пускаем и octet-stream, и всё подряд
                        filePicker.launch(arrayOf(
                            "application/x-bittorrent",
                            "application/octet-stream",
                            "*/*"
                        ))
                    },
                    enabled = !adding,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Выбрать .torrent файл")
                }

                if (adding) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Добавляем...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.addLink(link) { onDismiss() } },
                enabled = link.isNotBlank() && !adding
            ) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !adding) { Text("Отмена") }
        }
    )
}

/** Черта, ниже которой раздачи стоят на паузе. Она же — цель перетаскивания. */
@Composable
private fun InactiveDivider() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text("НЕАКТИВНЫЕ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp))
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Отмена")
            }
            Text("Выбрано: " + count,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onPause) {
                Icon(Icons.Default.Pause, contentDescription = "Остановить выбранные")
            }
            IconButton(onClick = onResume) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Продолжить выбранные")
            }
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = "Выбрать все")
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить выбранные",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun DownloadCard(
    item: DownloadItem,
    dragging: Boolean = false,
    dragDelta: Float = 0f,
    selected: Boolean = false,
    selectMode: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit = {},
    onDrag: (delta: Float, rowHeight: Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {}
) {
    // Высоту строки меряем по факту: карточки разной высоты, а перестановка
    // считается именно в них
    var rowHeight by remember { mutableFloatStateOf(0f) }
    Card(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { rowHeight = it.height.toFloat() }
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragDelta
                if (dragging) { scaleX = 1.02f; scaleY = 1.02f }
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (selected)
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(12.dp)) {
            // Заголовок + кнопки
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                if (selectMode) {
                    Checkbox(checked = selected, onCheckedChange = { onLongClick() })
                    Spacer(Modifier.width(4.dp))
                } else {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Перетащить",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .pointerInput(item.id) {
                                detectDragGestures(
                                    onDragStart = { onDragStart() },
                                    onDragEnd = { onDragEnd() },
                                    onDragCancel = { onDragEnd() }
                                ) { change, amount ->
                                    change.consume()
                                    onDrag(amount.y, rowHeight)
                                }
                            }
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                Row {
                    when (item.state) {
                        DownloadState.DOWNLOADING -> IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Pause, "Пауза", Modifier.size(20.dp))
                        }
                        DownloadState.PAUSED -> IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.PlayArrow, "Продолжить", Modifier.size(20.dp))
                        }
                        else -> Spacer(Modifier.size(36.dp))
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, "Удалить", Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Прогресс-бар (только при активной загрузке)
            if (item.state == DownloadState.DOWNLOADING || item.state == DownloadState.PAUSED ||
                item.state == DownloadState.FETCHING_META || item.state == DownloadState.CHECKING) {
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
                Spacer(Modifier.height(4.dp))
            }

            // Статус строка
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                // Состояние + прогресс %
                val stateText = when (item.state) {
                    DownloadState.QUEUED        -> "В очереди"
                    DownloadState.CHECKING      -> "Проверка файлов  %.0f%%".format(item.progress * 100)
                    DownloadState.FETCHING_META -> "Метаданные... пиры: " + item.peers
                    DownloadState.DOWNLOADING   -> "%.1f%%  ↓ %s/с".format(
                        item.progress * 100, formatSpeed(item.downloadSpeedBps))
                    DownloadState.PAUSED        -> "Пауза  %.1f%%".format(item.progress * 100)
                    DownloadState.FINISHED      -> "✓ Готово"
                    DownloadState.ERROR         -> item.errorMessage ?: "Ошибка"
                }
                val stateColor = when (item.state) {
                    DownloadState.FINISHED  -> MaterialTheme.colorScheme.tertiary
                    DownloadState.ERROR     -> MaterialTheme.colorScheme.error
                    else                    -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(stateText, style = MaterialTheme.typography.labelSmall, color = stateColor)

                // Размер + сиды
                if (item.totalBytes > 0) {
                    Text(
                        "${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}" +
                        if (item.seeds > 0) "  ▲${item.seeds}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

fun formatSpeed(bps: Long): String {
    return when {
        bps >= 1_048_576 -> "%.1f MB".format(bps / 1_048_576.0)
        bps >= 1024      -> "%.0f KB".format(bps / 1024.0)
        else             -> "$bps B"
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024          -> "%.0f KB".format(bytes / 1024.0)
        else                   -> "$bytes B"
    }
}
