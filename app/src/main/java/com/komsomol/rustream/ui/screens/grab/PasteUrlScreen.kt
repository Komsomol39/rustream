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

@Composable
fun PasteUrlScreen(
    onBack: () -> Unit,
    viewModel: GrabViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsState()
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

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Button(
                onClick = { viewModel.downloadUrlVideo(url) },
                enabled = url.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text("⬇ Видео") }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(
                onClick = { viewModel.downloadUrlAudio(url) },
                enabled = url.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text("⬇ Аудио (mp3)") }
        }

        HorizontalDivider()

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
                                IconButton(onClick = { viewModel.dismiss(d.id) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Убрать")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
