package com.komsomol.rustream.ui.screens.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val context           = LocalContext.current
    val darkTheme         by viewModel.darkTheme.collectAsState()
    val ruTorEnabled      by viewModel.ruTorEnabled.collectAsState()
    val ruTrackerEnabled  by viewModel.ruTrackerEnabled.collectAsState()
    val rtLoggedIn        by viewModel.ruTrackerLoggedIn.collectAsState()
    val kinozalEnabled    by viewModel.kinozalEnabled.collectAsState()
    val nnmEnabled        by viewModel.nnmEnabled.collectAsState()
    val nnmLoggedIn       by viewModel.nnmLoggedIn.collectAsState()
    val rutorDebug        by viewModel.rutorDebug.collectAsState()
    val nnmDebugHtml      by viewModel.nnmDebugHtml.collectAsState()

    val rtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.onRuTrackerLoginSuccess()
    }
    val nnmLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.onNnmLoginSuccess()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Тёмная тема", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = darkTheme, onCheckedChange = viewModel::setDarkTheme)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Источники поиска", style = MaterialTheme.typography.titleMedium)

                SourceRow("Kinozal", "Без авторизации", kinozalEnabled, viewModel::setKinozalEnabled)
                HorizontalDivider()
                SourceRow("RuTor", "Магнет-ссылки, может не работать в РФ", ruTorEnabled, viewModel::setRuTorEnabled)
                HorizontalDivider()
                AuthSourceRow("RuTracker", rtLoggedIn, ruTrackerEnabled, viewModel::setRuTrackerEnabled,
                    { rtLauncher.launch(Intent(context, RuTrackerLoginActivity::class.java)) },
                    viewModel::logoutRuTracker)
                HorizontalDivider()
                AuthSourceRow("NNM-Club", nnmLoggedIn, nnmEnabled, viewModel::setNnmEnabled,
                    { nnmLauncher.launch(Intent(context, NnmLoginActivity::class.java)) },
                    viewModel::logoutNnm)

                // RuTor debug — показывается после поиска
                if (rutorDebug.isNotBlank()) {
                    HorizontalDivider()
                    Text("RuTor debug:", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(rutorDebug.take(600),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // NNM debug
                if (nnmDebugHtml.isNotBlank() && !nnmDebugHtml.startsWith("OK")) {
                    HorizontalDivider()
                    Text("NNM debug:", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(nnmDebugHtml.take(300),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("О приложении", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("RuStream v1.0", style = MaterialTheme.typography.bodyMedium)
                Text("Поиск торрентов", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SourceRow(name: String, subtitle: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun AuthSourceRow(
    name: String, loggedIn: Boolean, enabled: Boolean,
    onToggle: (Boolean) -> Unit, onLogin: () -> Unit, onLogout: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (loggedIn) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp),
                            MaterialTheme.colorScheme.tertiary)
                        Text("Авторизован", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary)
                    } else {
                        Text("Требуется вход", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Switch(checked = enabled, onCheckedChange = onToggle, enabled = loggedIn)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loggedIn) {
                OutlinedButton(onClick = onLogin, Modifier.weight(1f)) {
                    Icon(Icons.Default.Login, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp)); Text("Обновить")
                }
                OutlinedButton(onClick = onLogout, Modifier.weight(1f)) {
                    Icon(Icons.Default.Logout, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp)); Text("Выйти")
                }
            } else {
                Button(onClick = onLogin, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Login, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Войти в $name")
                }
            }
        }
    }
}
