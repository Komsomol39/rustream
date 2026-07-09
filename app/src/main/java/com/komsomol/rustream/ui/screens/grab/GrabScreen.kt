package com.komsomol.rustream.ui.screens.grab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komsomol.rustream.domain.model.GrabDownload
import com.komsomol.rustream.domain.model.GrabResult
import com.komsomol.rustream.domain.model.GrabState

@Composable
fun GrabScreen(
    onBack: () -> Unit,
    onOpenPaste: () -> Unit = {},
    viewModel: GrabViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val engineMsg by viewModel.engineMsg.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Онлайн-поиск", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenPaste) { Text("🔗 Ссылка") }
            TextButton(onClick = { viewModel.updateEngine() }) { Text("⟳") }
        }
        if (engineMsg != null) {
            Text(engineMsg ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp))
        }

        OutlinedTextField(
            value = query, onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("YouTube, SoundCloud, PeerTube...") },
            trailingIcon = { IconButton(onClick = viewModel::search) {
                Icon(Icons.Default.Search, "Поиск") } },
            singleLine = true
        )

        // Активные скачивания
        if (downloads.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                downloads.values.sortedBy { it.title }.forEach { d ->
                    GrabDownloadRow(d, onDismiss = { viewModel.dismiss(d.id) })
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        when (val state = uiState) {
            is GrabUiState.Idle -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Найди видео или трек и скачай в библиотеку",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is GrabUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            is GrabUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
            is GrabUiState.Success -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.results, key = { it.url + it.serviceId }) { r ->
                    GrabResultRow(r,
                        onVideo = { viewModel.downloadVideo(r) },
                        onAudio = { viewModel.downloadAudio(r) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun GrabResultRow(r: GrabResult, onVideo: () -> Unit, onAudio: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (r.thumbnailUrl != null) {
            coil.compose.AsyncImage(
                model = r.thumbnailUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(width = 96.dp, height = 54.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.bodyLarge,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(
                r.serviceName,
                r.uploader?.takeIf { it.isNotBlank() },
                fmtDur(r.durationSec).takeIf { r.durationSec > 0 }
            ).joinToString(" • ")
            Text(sub, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row {
                TextButton(onClick = onVideo) { Text("⬇ Видео") }
                TextButton(onClick = onAudio) { Text("⬇ Аудио") }
            }
        }
    }
}

@Composable
private fun GrabDownloadRow(d: GrabDownload, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text((if (d.video) "🎬 " else "🎵 ") + d.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                when (d.state) {
                    GrabState.RESOLVING -> Text("Получаем ссылку...",
                        style = MaterialTheme.typography.labelSmall)
                    GrabState.DOWNLOADING -> LinearProgressIndicator(
                        progress = { d.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp))
                    GrabState.DONE -> Text("✓ Готово — смотри в библиотеке",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary)
                    GrabState.ERROR -> Text("Ошибка: " + (d.message ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            if (d.state == GrabState.DONE || d.state == GrabState.ERROR) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Убрать")
                }
            }
        }
    }
}

private fun fmtDur(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return m.toString() + ":" + s.toString().padStart(2, '0')
}
