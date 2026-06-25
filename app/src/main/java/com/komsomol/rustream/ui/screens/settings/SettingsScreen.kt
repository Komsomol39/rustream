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
    val context          = LocalContext.current
    val darkTheme        by viewModel.darkTheme.collectAsState()
    val ruTorEnabled     by viewModel.ruTorEnabled.collectAsState()
    val ruTrackerEnabled by viewModel.ruTrackerEnabled.collectAsState()
    val ruTrackerLoggedIn by viewModel.ruTrackerLoggedIn.collectAsState()
    val kinozalEnabled   by viewModel.kinozalEnabled.collectAsState()
    val nnmEnabled       by viewModel.nnmEnabled.collectAsState()

    val loginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onRuTrackerLoginSuccess()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)

        // Тема
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Тёмная тема", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = darkTheme, onCheckedChange = viewModel::setDarkTheme)
            }
        }

        // Источники поиска
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Источники поиска", style = MaterialTheme.typography.titleMedium)

                SourceRow("Kinozal", "Без авторизации", kinozalEnabled, viewModel::setKinozalEnabled)
                HorizontalDivider()
                SourceRow("NNM-Club", "Без авторизации (RSS)", nnmEnabled, viewModel::setNnmEnabled)
                HorizontalDivider()
                SourceRow("RuTor", "Может не работать в РФ", ruTorEnabled, viewModel::setRuTorEnabled)
                HorizontalDivider()

                // RuTracker со статусом авторизации
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("RuTracker", style = MaterialTheme.typography.bodyLarge)
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (ruTrackerLoggedIn) {
                                Icon(Icons.Default.CheckCircle, null,
                                    Modifier.size(14.dp), MaterialTheme.colorScheme.tertiary)
                                Text("Авторизован",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary)
                            } else {
                                Text("Требуется вход",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Switch(checked = ruTrackerEnabled, onCheckedChange = viewModel::setRuTrackerEnabled,
                        enabled = ruTrackerLoggedIn)
                }
            }
        }

        // RuTracker авторизация
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Авторизация RuTracker", style = MaterialTheme.typography.titleMedium)
                Text("Открывает браузер — войдите в аккаунт, куки сохранятся автоматически.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (ruTrackerLoggedIn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            loginLauncher.launch(Intent(context, RuTrackerLoginActivity::class.java))
                        }, Modifier.weight(1f)) {
                            Icon(Icons.Default.Login, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("Обновить сессию")
                        }
                        OutlinedButton(onClick = viewModel::logoutRuTracker, Modifier.weight(1f)) {
                            Icon(Icons.Default.Logout, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("Выйти")
                        }
                    }
                } else {
                    Button(onClick = {
                        loginLauncher.launch(Intent(context, RuTrackerLoginActivity::class.java))
                    }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Login, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text("Войти в RuTracker")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("О приложении", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("RuStream v1.0", style = MaterialTheme.typography.bodyMedium)
                Text("Поиск и загрузка торрентов",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SourceRow(
    name: String, subtitle: String,
    enabled: Boolean, onToggle: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}
