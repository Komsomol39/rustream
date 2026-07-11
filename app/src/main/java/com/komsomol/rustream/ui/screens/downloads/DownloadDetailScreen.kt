package com.komsomol.rustream.ui.screens.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komsomol.rustream.domain.model.DownloadState

@Composable
fun DownloadDetailScreen(
    onBack: () -> Unit,
    viewModel: DownloadDetailViewModel = hiltViewModel()
) {
    val item by viewModel.item.collectAsState(initial = null)
    val files by viewModel.files.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(
                item?.title ?: "Загрузка",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
        }

        val enabledCount = files.count { it.enabled }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                files.size.toString() + " файлов, выбрано " + enabledCount,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { viewModel.setAll(true) }) { Text("Все") }
            TextButton(onClick = { viewModel.setAll(false) }) { Text("Ничего") }
        }

        if (files.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    when (item?.state) {
                        DownloadState.FETCHING_META -> "Список файлов появится после получения метаданных..."
                        DownloadState.CHECKING      -> "Идёт проверка уже скачанных файлов..."
                        else                        -> "Список файлов недоступен"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(files, key = { it.index }) { f ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = f.enabled,
                            onCheckedChange = { viewModel.toggle(f.index, it) }
                        )
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(f.name, style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val pct = if (f.sizeBytes > 0)
                                (f.downloadedBytes * 100 / f.sizeBytes).toInt() else 0
                            Text(
                                formatBytes(f.sizeBytes) +
                                    (if (f.enabled && pct in 1..99) "  •  " + pct + "%" else "") +
                                    (if (pct >= 100) "  •  ✓" else ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
