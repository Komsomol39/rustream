package com.komsomol.rustream.ui.screens.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komsomol.rustream.domain.model.DownloadItem
import com.komsomol.rustream.domain.model.DownloadState

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel = hiltViewModel()) {
    val downloads by viewModel.downloads.collectAsState()

    if (downloads.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Загрузок нет", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Нажмите на торрент в поиске чтобы скачать",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(downloads, key = { it.id }) { item ->
            DownloadCard(
                item = item,
                onPause   = { viewModel.pause(item.id) },
                onResume  = { viewModel.resume(item.id) },
                onRemove  = { viewModel.remove(item.id) }
            )
        }
    }
}

@Composable
fun DownloadCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            // Заголовок
            Text(item.title, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)

            Spacer(Modifier.height(8.dp))

            // Прогресс бар
            if (item.state == DownloadState.DOWNLOADING || item.state == DownloadState.PAUSED) {
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (item.state == DownloadState.PAUSED)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
            }

            // Статус строка
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text(
                        text = stateText(item),
                        style = MaterialTheme.typography.labelSmall,
                        color = stateColor(item)
                    )
                    if (item.state == DownloadState.DOWNLOADING && item.downloadSpeedBps > 0) {
                        Text(
                            text = "↓ ${formatSpeed(item.downloadSpeedBps)}  ${formatSize(item.downloadedBytes)}/${formatSize(item.totalBytes)}  S:${item.seeds}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Кнопки управления
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (item.state) {
                        DownloadState.DOWNLOADING -> {
                            IconButton(onClick = onPause, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Pause, "Пауза", modifier = Modifier.size(18.dp))
                            }
                        }
                        DownloadState.PAUSED -> {
                            IconButton(onClick = onResume, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.PlayArrow, "Продолжить", modifier = Modifier.size(18.dp))
                            }
                        }
                        else -> {}
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, "Удалить",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun stateColor(item: DownloadItem) = when (item.state) {
    DownloadState.DOWNLOADING    -> MaterialTheme.colorScheme.primary
    DownloadState.FINISHED       -> MaterialTheme.colorScheme.tertiary
    DownloadState.ERROR          -> MaterialTheme.colorScheme.error
    DownloadState.PAUSED         -> MaterialTheme.colorScheme.onSurfaceVariant
    DownloadState.FETCHING_META  -> MaterialTheme.colorScheme.secondary
    DownloadState.QUEUED         -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun stateText(item: DownloadItem) = when (item.state) {
    DownloadState.QUEUED         -> "В очереди"
    DownloadState.FETCHING_META  -> "Получение метаданных..."
    DownloadState.DOWNLOADING    -> "%.1f%%".format(item.progress * 100)
    DownloadState.PAUSED         -> "Пауза  %.1f%%".format(item.progress * 100)
    DownloadState.FINISHED       -> "✓ Завершено"
    DownloadState.ERROR          -> "Ошибка"
}

private fun formatSpeed(bps: Long): String = when {
    bps >= 1_048_576 -> "%.1f MB/s".format(bps / 1_048_576.0)
    bps >= 1024      -> "%.0f KB/s".format(bps / 1024.0)
    else             -> "$bps B/s"
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0          -> "—"
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576  -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024       -> "%.1f KB".format(bytes / 1024.0)
    else                -> "$bytes B"
}
