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
    val downloads by viewModel.downloads.collectAsState(initial = emptyList())
    val dhtNodes by viewModel.dhtNodes.collectAsState(initial = 0L)

    if (downloads.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Нет загрузок", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Нажмите на раздачу в поиске чтобы скачать",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("DHT: " + dhtNodes + " узлов",
                    style = MaterialTheme.typography.labelSmall,
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
        item {
            Text("DHT: " + dhtNodes + " узлов",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(downloads, key = { it.id }) { item ->
            DownloadCard(
                item     = item,
                onPause  = { viewModel.pause(item) },
                onResume = { viewModel.resume(item) },
                onRemove = { viewModel.remove(item) }
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            // Заголовок + кнопки
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
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
                item.state == DownloadState.FETCHING_META) {
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
