package com.komsomol.rustream.ui.screens.video

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komsomol.rustream.domain.model.VideoItem
import com.komsomol.rustream.player.PlayerActivity

@Composable
fun VideoScreen(viewModel: VideoViewModel = hiltViewModel()) {
    val browse by viewModel.browse.collectAsState()
    val query by viewModel.query.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val context = LocalContext.current
    var toDelete by remember { mutableStateOf<VideoItem?>(null) }

    if (toDelete != null) {
        val item = toDelete!!
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить файл?") },
            text = { Text(item.title + "\n\nФайл будет удалён с устройства безвозвратно.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(item.path); toDelete = null }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } }
        )
    }

    // Системная "назад" поднимает на уровень вверх, а не выкидывает из вкладки
    BackHandler(enabled = browse.canGoUp) { viewModel.goUp() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (browse.canGoUp) {
                IconButton(onClick = { viewModel.goUp() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(browse.title, style = MaterialTheme.typography.headlineSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            Text(
                if (scanning) "сканирую..." else browse.totalCount.toString() + " файлов",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Обновить")
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            placeholder = { Text("Поиск по всем видео") },
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

        if (browse.folders.isEmpty() && browse.files.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Пока пусто", style = MaterialTheme.typography.bodyLarge)
                    Text("Скачанное видео появится здесь",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                // Папки идут первыми, как в любом файловом менеджере
                items(browse.folders, key = { "d:" + it.path }) { f ->
                    FolderRow(folder = f, onClick = { viewModel.open(f) })
                }
                items(browse.files, key = { it.path }) { v ->
                    VideoRow(
                        video = v,
                        onClick = {
                            val i = Intent(context, PlayerActivity::class.java)
                            i.putExtra(PlayerActivity.EXTRA_PATH, v.path)
                            context.startActivity(i)
                        },
                        onLongClick = { toDelete = v }
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderRow(folder: VideoFolder, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Folder, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(folder.videoCount.toString() + " видео",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoRow(video: VideoItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (video.thumbPath != null) {
            coil.compose.AsyncImage(
                model = java.io.File(video.thumbPath),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(width = 112.dp, height = 63.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            )
        } else {
            Icon(Icons.Default.PlayCircle, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, style = MaterialTheme.typography.bodyLarge,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(
                formatSize(video.sizeBytes).takeIf { video.sizeBytes > 0 },
                formatDuration(video.durationMs).takeIf { video.durationMs > 0 }
            ).joinToString(" • ")
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / 1048576.0
    return if (mb >= 1024.0) {
        String.format(java.util.Locale.US, "%.1f ГБ", mb / 1024.0)
    } else {
        String.format(java.util.Locale.US, "%.0f МБ", mb)
    }
}

private fun formatDuration(ms: Long): String {
    val totalMin = ms / 60000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) h.toString() + " ч " + m.toString() + " мин"
           else m.toString() + " мин"
}
