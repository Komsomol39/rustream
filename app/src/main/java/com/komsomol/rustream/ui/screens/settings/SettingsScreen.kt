package com.komsomol.rustream.ui.screens.settings

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komsomol.rustream.ui.update.UpdateDialogs
import com.komsomol.rustream.ui.update.UpdateUiState
import com.komsomol.rustream.ui.update.UpdateViewModel

private val SCREEN_PADDING = 20.dp
private val CARD_PADDING = 20.dp
private val SECTION_GAP = 16.dp

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) treeUriToPath(uri)?.let { viewModel.addMediaFolder(it) }
    }
    val darkTheme        by viewModel.darkTheme.collectAsState()
    val ruTorEnabled     by viewModel.ruTorEnabled.collectAsState()
    val ruTrackerEnabled by viewModel.ruTrackerEnabled.collectAsState()
    val rtLoggedIn       by viewModel.ruTrackerLoggedIn.collectAsState()
    val kinozalEnabled   by viewModel.kinozalEnabled.collectAsState()
    val nnmEnabled       by viewModel.nnmEnabled.collectAsState()
    val ytsEnabled       by viewModel.ytsEnabled.collectAsState()
    val newpipeEnabled   by viewModel.newpipeEnabled.collectAsState()
    val mediaFolders     by viewModel.mediaFolders.collectAsState()
    val nnmLoggedIn      by viewModel.nnmLoggedIn.collectAsState()
    val kinozalLoggedIn  by viewModel.kinozalLoggedIn.collectAsState()

    val rtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.onRuTrackerLoginSuccess()
    }
    val nnmLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.onNnmLoginSuccess()
    }
    val kinozalLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.onKinozalLoginSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Настройки",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        // --- Оформление ---
        SettingsCard {
            RowItem(
                icon = Icons.Outlined.DarkMode,
                title = "Тёмная тема",
                subtitle = if (darkTheme) "Включена" else "Выключена"
            ) {
                Switch(checked = darkTheme, onCheckedChange = viewModel::setDarkTheme)
            }
        }

        // --- Онлайн-загрузки (не торренты) ---
        SettingsCard {
            SectionLabel("Онлайн-загрузки")
            SourceRow(Icons.Default.Public, "NewPipe / yt-dlp",
                "YouTube, SoundCloud и ещё сотни сайтов",
                newpipeEnabled, viewModel::setNewpipeEnabled)
        }

        // --- Торрент-трекеры ---
        SettingsCard {
            SectionLabel("Торрент-трекеры")

            SourceRow(Icons.Default.Movie, "YTS", "Фильмы 720p–4K, без входа",
                ytsEnabled, viewModel::setYtsEnabled)
            RowDivider()
            SourceRow(Icons.Default.Language, "RuTor", "Нужен VPN в РФ • магнет-ссылки",
                ruTorEnabled, viewModel::setRuTorEnabled)
            RowDivider()
            AuthSourceRow(Icons.Default.Lock, "Kinozal", kinozalLoggedIn, kinozalEnabled,
                viewModel::setKinozalEnabled,
                { kinozalLauncher.launch(Intent(context, KinozalLoginActivity::class.java)) },
                viewModel::logoutKinozal)
            RowDivider()
            AuthSourceRow(Icons.Default.Lock, "RuTracker", rtLoggedIn, ruTrackerEnabled,
                viewModel::setRuTrackerEnabled,
                { rtLauncher.launch(Intent(context, RuTrackerLoginActivity::class.java)) },
                viewModel::logoutRuTracker)
            RowDivider()
            AuthSourceRow(Icons.Default.Lock, "NNM-Club", nnmLoggedIn, nnmEnabled,
                viewModel::setNnmEnabled,
                { nnmLauncher.launch(Intent(context, NnmLoginActivity::class.java)) },
                viewModel::logoutNnm)
        }

        // --- Папки с медиа ---
        SettingsCard {
            SectionLabel("Папки с медиа")
            Text(
                "Откуда играть видео и музыку. Папка загрузок уже включена.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            mediaFolders.forEach { folder ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        folder.substringAfterLast('/').ifBlank { folder },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { viewModel.removeMediaFolder(folder) }) {
                        Icon(Icons.Default.Delete, "Убрать папку",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CreateNewFolder, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Добавить папку")
            }
        }

        // --- О приложении и обновления ---
        val updateVm: UpdateViewModel = hiltViewModel()
        val updateState by updateVm.state.collectAsState()
        val autoUpdate by viewModel.autoUpdateCheck.collectAsState()
        UpdateDialogs(updateState, updateVm)

        SettingsCard {
            SectionLabel("О приложении")
            Text("RuStream", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text("Версия " + updateVm.currentVersion + " • торренты, онлайн-загрузки, плеер",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            RowDivider()
            RowItem(
                icon = Icons.Default.Autorenew,
                title = "Проверять обновления",
                subtitle = "При каждом запуске приложения"
            ) {
                Switch(checked = autoUpdate, onCheckedChange = viewModel::setAutoUpdateCheck)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { updateVm.checkNow() },
                modifier = Modifier.fillMaxWidth(),
                enabled = updateState !is UpdateUiState.Checking
                       && updateState !is UpdateUiState.Downloading
            ) {
                Text(when (updateState) {
                    is UpdateUiState.Checking -> "Проверяю..."
                    else -> "Проверить сейчас"
                })
            }
            if (updateState is UpdateUiState.UpToDate) {
                Spacer(Modifier.height(8.dp))
                Text("Установлена последняя версия",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary)
            }

            RowDivider()
            var dnsInfo by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()
            OutlinedButton(
                onClick = {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val r = com.komsomol.rustream.data.search.SecureDns.diagnose("yts.bz")
                        withContext(kotlinx.coroutines.Dispatchers.Main) { dnsInfo = r }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Проверить DNS (диагностика)") }
            dnsInfo?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

/* ---------- переиспользуемые блоки ---------- */

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(Modifier.padding(CARD_PADDING), content = content)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        Modifier.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

// Универсальная строка: иконка-плашка + заголовок/подзаголовок + управляющий control справа
@Composable
private fun RowItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    control: @Composable () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(12.dp))
        control()
    }
}

@Composable
private fun SourceRow(
    icon: ImageVector, name: String, subtitle: String,
    enabled: Boolean, onToggle: (Boolean) -> Unit
) {
    RowItem(icon, name, subtitle) {
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun AuthSourceRow(
    icon: ImageVector, name: String, loggedIn: Boolean, enabled: Boolean,
    onToggle: (Boolean) -> Unit, onLogin: () -> Unit, onLogout: () -> Unit
) {
    Column {
        RowItem(
            icon = icon,
            title = name,
            subtitle = null
        ) {
            Switch(checked = enabled, onCheckedChange = onToggle, enabled = loggedIn)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.padding(start = 54.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (loggedIn) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.tertiary)
                Text("Авторизован", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary)
            } else {
                Text("Требуется вход", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.padding(start = 54.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (loggedIn) {
                OutlinedButton(onClick = onLogin, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Login, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp)); Text("Обновить")
                }
                OutlinedButton(onClick = onLogout, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Logout, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp)); Text("Выйти")
                }
            } else {
                Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Login, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Войти")
                }
            }
        }
    }
}

// content://.../tree/primary:Music -> /storage/emulated/0/Music
private fun treeUriToPath(uri: android.net.Uri): String? {
    return try {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":")
        val type = parts[0]
        val rel = if (parts.size > 1) parts[1] else ""
        when (type) {
            "primary" -> "/storage/emulated/0/" + rel
            else -> "/storage/" + type + "/" + rel
        }.trimEnd('/')
    } catch (_: Exception) { null }
}
