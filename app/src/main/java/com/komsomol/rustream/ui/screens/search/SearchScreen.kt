package com.komsomol.rustream.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komsomol.rustream.domain.model.SearchResult
import com.komsomol.rustream.domain.model.SearchSource

@Composable
fun SearchScreen(
    onOpenGrab: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState        by viewModel.uiState.collectAsState()
    val query          by viewModel.query.collectAsState()
    val activeStatuses by viewModel.activeStatuses.collectAsState()
    val newpipeEnabled by viewModel.newpipeEnabled.collectAsState(initial = false)

    var downloadDialogItem by remember { mutableStateOf<SearchResult?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query, onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск торрентов...") },
            trailingIcon = { IconButton(onClick = viewModel::search) {
                Icon(Icons.Default.Search, "Поиск") } },
            singleLine = true
        )
        if (newpipeEnabled) {
            TextButton(onClick = onOpenGrab) {
                Text("🌐 Онлайн-поиск (YouTube, SoundCloud...)")
            }
        }

        if (activeStatuses.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                activeStatuses.forEach { src ->
                    val color = if (src.ready) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.error
                    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
                        Text(if (src.ready) src.name else "${src.name} ⚠",
                            style = MaterialTheme.typography.labelSmall, color = color,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        when (val state = uiState) {
            is SearchUiState.Idle -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(if (activeStatuses.isEmpty()) "Включите источники в Настройках"
                     else "Введите запрос для поиска",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is SearchUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Поиск...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is SearchUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
            is SearchUiState.Success -> {
                val bySource = state.results.groupBy { it.source }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    bySource.forEach { (src, list) ->
                        SuggestionChip(onClick = {},
                            label = { Text("${src.displayName}: ${list.size}",
                                style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.results) { result ->
                        SearchResultCard(result = result, onClick = { downloadDialogItem = result })
                    }
                }
            }
        }
    }

    downloadDialogItem?.let { item ->
        DownloadDialog(
            result    = item,
            onMagnet  = { viewModel.startMagnet(item); downloadDialogItem = null },
            onTorrent = { viewModel.startTorrentUrl(item); downloadDialogItem = null },
            onDismiss = { downloadDialogItem = null }
        )
    }
}

@Composable
fun DownloadDialog(
    result: SearchResult,
    onMagnet: () -> Unit,
    onTorrent: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Скачать") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(result.title, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (result.sizeBytes > 0)
                    Text(formatSize(result.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (result.magnetUri != null) {
                    Button(onClick = onMagnet, modifier = Modifier.fillMaxWidth()) {
                        Text("Magnet-ссылка")
                    }
                }
                if (result.torrentUrl != null) {
                    OutlinedButton(onClick = onTorrent, modifier = Modifier.fillMaxWidth()) {
                        Text("Скачать .torrent")
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Отмена")
                }
            }
        }
    )
}

@Composable
fun SearchResultCard(result: SearchResult, onClick: () -> Unit) {
    val sourceColor = when (result.source) {
        SearchSource.RUTOR     -> Color(0xFF6750A4)
        SearchSource.RUTRACKER -> Color(0xFF00897B)
        SearchSource.KINOZAL   -> Color(0xFFE65100)
        SearchSource.NNM       -> Color(0xFF1565C0)
        SearchSource.YTS       -> Color(0xFF2E7D32)
        SearchSource.TPB       -> Color(0xFF8E24AA)
    }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Text(result.title, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Surface(color = sourceColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                    Text(result.source.displayName,
                        style = MaterialTheme.typography.labelSmall, color = sourceColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Text(formatSize(result.sizeBytes), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (result.seeders > 0)
                    Text("▲ ${result.seeders}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary)
                if (result.leechers > 0)
                    Text("▼ ${result.leechers}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                if (result.uploadDate.isNotEmpty())
                    Text(result.uploadDate, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024          -> "%.1f KB".format(bytes / 1024.0)
        else                   -> "$bytes B"
    }
}
