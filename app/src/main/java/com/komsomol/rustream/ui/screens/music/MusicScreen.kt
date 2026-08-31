package com.komsomol.rustream.ui.screens.music

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
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

/** Трек в результатах поиска: показываем исполнителя, чтобы было ясно, чей он. */
@Composable
private fun SearchTrackRow(
    track: com.komsomol.rustream.domain.model.Track,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isCurrent) Icons.Default.VolumeUp else Icons.Default.MusicNote,
            contentDescription = null,
            tint = if (isCurrent) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface)
            Text(track.artist ?: "Неизвестный исполнитель",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicScreen(
    onOpenArtist: (String) -> Unit = {},
    openPath: String? = null,
    viewModel: MusicViewModel = hiltViewModel()
) {
    val artists by viewModel.artists.collectAsState()

    // Пришли сюда сразу после скачивания трека: библиотеку надо перечитать
    // (файл новый), затем открыть папку исполнителя, в которую он попал
    LaunchedEffect(openPath) {
        if (!openPath.isNullOrBlank()) {
            viewModel.artistForFile(openPath)?.let { onOpenArtist(it) }
        }
    }
    val scanning by viewModel.scanning.collectAsState()
    val current by viewModel.current.collectAsState()
    val playing by viewModel.playing.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val selectMode by viewModel.selectMode.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val query by viewModel.query.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val searching = query.isNotBlank()

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
                if (artists.isNotEmpty()) {
                    TextButton(onClick = { viewModel.playAll() }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("Все")
                    }
                }
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

        if (!selectMode) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                placeholder = { Text("Поиск по трекам и исполнителям") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить")
                        }
                    }
                }
            )
            Spacer(Modifier.height(6.dp))
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
        } else if (searching) {
            if (results.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Ничего не найдено",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(results, key = { it.path }) { t ->
                        // Плейлистом становится сам список результатов,
                        // в том порядке, в каком он на экране
                        SearchTrackRow(
                            track = t,
                            isCurrent = current?.path == t.path,
                            onClick = { viewModel.playFrom(t, results) }
                        )
                    }
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

        val shuffle by viewModel.shuffle.collectAsState()
        val repeatMode by viewModel.repeatMode.collectAsState()
        if (current != null) {
            SharedMiniPlayer(current!!, playing, positionMs, durationMs,
                shuffle, repeatMode,
                { viewModel.toggle() }, { viewModel.next() },
                { viewModel.prev() }, { viewModel.seekTo(it) },
                { viewModel.toggleShuffle() }, { viewModel.cycleRepeat() },
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
    shuffle: Boolean, repeatMode: Int,
    onToggle: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit, onSeek: (Long) -> Unit,
    onShuffle: () -> Unit, onRepeat: () -> Unit,
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
                IconButton(onClick = onShuffle) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Перемешать",
                        tint = if (shuffle) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onPrev) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Назад") }
                FilledIconButton(onClick = onToggle) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Играть/пауза") }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Дальше") }
                IconButton(onClick = onRepeat) {
                    Icon(
                        if (repeatMode == 1) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Повтор",
                        tint = if (repeatMode != 0) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
