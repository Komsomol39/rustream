package com.komsomol.rustream.ui.screens.music

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komsomol.rustream.domain.model.ArtistGroup
import com.komsomol.rustream.domain.model.Track

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicScreen(
    onOpenArtist: (String) -> Unit = {},
    viewModel: MusicViewModel = hiltViewModel()
) {
    val artists by viewModel.artists.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val current by viewModel.current.collectAsState()
    val playing by viewModel.playing.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val selectMode by viewModel.selectMode.collectAsState()
    val selected by viewModel.selected.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectMode) {
                Text("Выбрано: " + selected.size, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { viewModel.mergeSelected() },
                    enabled = selected.size >= 2
                ) { Text("Объединить") }
                TextButton(onClick = { viewModel.cancelSelect() }) { Text("Отмена") }
            } else {
                Text("Музыка", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (scanning) "сканирую..." else artists.size.toString() + " исполн.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                }
            }
        }

        if (!selectMode) {
            Text("Долгое нажатие — выбрать для объединения",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp))
        }

        if (artists.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MusicNote, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Пока пусто", style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(artists, key = { it.displayName }) { a ->
                    ArtistRow(
                        artist = a,
                        selectMode = selectMode,
                        isSelected = selected.contains(a.displayName),
                        onClick = {
                            if (selectMode) viewModel.toggleSelect(a.displayName)
                            else onOpenArtist(a.displayName)
                        },
                        onLongClick = { if (!selectMode) viewModel.enterSelect(a.displayName) }
                    )
                }
            }
        }

        if (current != null) {
            SharedMiniPlayer(current!!, playing, positionMs, durationMs,
                { viewModel.toggle() }, { viewModel.next() },
                { viewModel.prev() }, { viewModel.seekTo(it) },
                { viewModel.stopPlayback() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistRow(
    artist: ArtistGroup,
    selectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectMode) {
            Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(artist.displayName, style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = artist.tracks.size.toString() + " треков" +
                (if (artist.memberNames.size > 1) " • объединено " + artist.memberNames.size else "")
            Text(sub, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SharedMiniPlayer(
    track: Track, playing: Boolean, positionMs: Long, durationMs: Long,
    onToggle: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit, onSeek: (Long) -> Unit,
    onClose: () -> Unit
) {
    Surface(tonalElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(track.title, style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть плеер")
                }
            }
            Slider(
                value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                onValueChange = { if (durationMs > 0) onSeek((it * durationMs).toLong()) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(fmt(positionMs), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onPrev) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Назад") }
                FilledIconButton(onClick = onToggle) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Играть/пауза") }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Дальше") }
                Spacer(Modifier.weight(1f))
                Text(fmt(durationMs), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return (s / 60).toString() + ":" + (s % 60).toString().padStart(2, '0')
}
