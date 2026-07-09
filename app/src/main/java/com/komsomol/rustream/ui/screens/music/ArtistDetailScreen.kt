package com.komsomol.rustream.ui.screens.music

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komsomol.rustream.domain.model.Track

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    onBack: () -> Unit,
    viewModel: MusicViewModel = hiltViewModel()
) {
    val artists by viewModel.artists.collectAsState()
    val group = artists.find { it.displayName == artistName }
    var toDelete by remember { mutableStateOf<Track?>(null) }

    if (toDelete != null) {
        val t = toDelete!!
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить трек?") },
            text = { Text(t.title + "\n\nФайл будет удалён с устройства безвозвратно.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteTrack(t.path); toDelete = null }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(artistName, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        if (group == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Нет треков") }
            return@Column
        }

        // Блок объединённых имён (если их больше одного)
        if (group.memberNames.size > 1) {
            Text("Объединённые исполнители:",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp))
            group.memberNames.forEach { name ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.unmerge(name) }) {
                        Icon(Icons.Default.Close, contentDescription = "Разгруппировать",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(group.tracks, key = { it.path }) { t ->
                Column(
                    Modifier.fillMaxWidth()
                        .combinedClickable(
                            onClick = { viewModel.playFrom(t, group.tracks) },
                            onLongClick = { toDelete = t }
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(t.title, style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (t.durationMs > 0) {
                        Text(fmtDur(t.durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun fmtDur(ms: Long): String {
    val s = ms / 1000
    return (s / 60).toString() + ":" + (s % 60).toString().padStart(2, '0')
}
