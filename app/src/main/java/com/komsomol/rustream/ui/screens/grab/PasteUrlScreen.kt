package com.komsomol.rustream.ui.screens.grab

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komsomol.rustream.domain.model.GrabState
import com.komsomol.rustream.domain.model.GrabFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.MusicNote

@Composable
fun PasteUrlScreen(
    onBack: () -> Unit,
    viewModel: GrabViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsState()
    val formatQuery by viewModel.formatQuery.collectAsState()
    var url by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Скачать по ссылке", style = MaterialTheme.typography.titleLarge)
        }

        OutlinedTextField(
            value = url, onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("https://...") },
            label = { Text("Ссылка на видео или трек") },
            trailingIcon = {
                IconButton(onClick = {
                    clipboard.getText()?.text?.let { url = it }
                }) { Icon(Icons.Default.ContentPaste, contentDescription = "Вставить") }
            },
            singleLine = true
        )

        Text("Работает с YouTube, RuTube, TikTok, Instagram и сотнями других сайтов",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

        Button(
            onClick = { viewModel.queryFormats(url) },
            enabled = url.isNotBlank() && formatQuery == null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) { Text("Показать варианты") }

        HorizontalDivider()

        // Панель выбора качества
        formatQuery?.let { q ->
            FormatPicker(
                title    = q.title,
                loading  = q.loading,
                error    = q.error,
                formats  = q.formats,
                onPick   = { fmt ->
                    viewModel.downloadFormat(q.url, q.title, fmt)
                    viewModel.dismissFormats()
                    url = ""
                },
                onClose  = { viewModel.dismissFormats() }
            )
            HorizontalDivider()
        }

        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Здесь появятся загрузки",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                downloads.values.sortedBy { it.title }.forEach { d ->
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
                                    GrabState.DOWNLOADING -> Column {
                                        LinearProgressIndicator(
                                            progress = { d.progress },
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp))
                                        Text(d.detail ?: "Начинаем...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 2.dp))
                                    }
                                    GrabState.PROCESSING -> Row(verticalAlignment = Alignment.CenterVertically) {
                                        LinearProgressIndicator(
                                            modifier = Modifier.weight(1f).padding(top = 4.dp, end = 8.dp))
                                        Text("Обработка...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    GrabState.DONE -> Text("✓ Готово — смотри в библиотеке",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary)
                                    GrabState.ERROR -> Text("Ошибка: " + (d.message ?: ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                            }
                            IconButton(onClick = {
                                if (d.state == GrabState.DONE || d.state == GrabState.ERROR)
                                    viewModel.dismiss(d.id) else viewModel.cancel(d.id)
                            }) {
                                Icon(Icons.Default.Close,
                                    contentDescription = if (d.state == GrabState.DONE ||
                                        d.state == GrabState.ERROR) "Убрать" else "Отменить")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatPicker(
    title: String,
    loading: Boolean,
    error: String?,
    formats: List<GrabFormat>,
    onPick: (GrabFormat) -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Закрыть")
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Ищем доступные варианты...",
                    style = MaterialTheme.typography.bodySmall)
            }
            error != null -> Text(error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
            else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                formats.forEach { fmt -> FormatTile(fmt, onClick = { onPick(fmt) }) }
            }
        }
    }
}

@Composable
private fun FormatTile(fmt: GrabFormat, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (fmt.video) Icons.Default.Videocam else Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(fmt.label, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f))
            if (fmt.detail != null) {
                Text(fmt.detail, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
