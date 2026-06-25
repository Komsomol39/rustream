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
    val context = LocalContext.current
    val darkTheme        by viewModel.darkTheme.collectAsState()
    val ruTorEnabled     by viewModel.ruTorEnabled.collectAsState()
    val ruTrackerEnabled by viewModel.ruTrackerEnabled.collectAsState()
    val ruTrackerLoggedIn by viewModel.ruTrackerLoggedIn.collectAsState()

    val loginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onRuTrackerLoginSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)

        // Тема
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Тёмная тема", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = darkTheme, onCheckedChange = viewModel::setDarkTheme)
            }
        }

        // Источники поиска
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Источники поиска", style = MaterialTheme.typography.titleMedium)

                // RuTor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("RuTor", style = MaterialTheme.typography.bodyLarge)
                        Text("Без авторизации",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = ruTorEnabled, onCheckedChange = viewModel::setRuTorEnabled)
                }

                HorizontalDivider()

                // RuTracker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("RuTracker", style = MaterialTheme.typography.bodyLarge)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (ruTrackerLoggedIn) {
                                Icon(Icons.Default.CheckCircle, null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.tertiary)
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
                    Switch(
                        checked = ruTrackerEnabled,
                        onCheckedChange = viewModel::setRuTrackerEnabled,
                        enabled = ruTrackerLoggedIn
                    )
                }
            }
        }

        // RuTracker авторизация через WebView
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Авторизация RuTracker", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Открывает настоящий браузер на rutracker.net. Войдите в свой аккаунт — куки сохранятся автоматически.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (ruTrackerLoggedIn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val intent = Intent(context, RuTrackerLoginActivity::class.java)
                                loginLauncher.launch(intent)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Login, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Обновить сессию")
                        }
                        OutlinedButton(
                            onClick = viewModel::logoutRuTracker,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Выйти")
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            val intent = Intent(context, RuTrackerLoginActivity::class.java)
                            loginLauncher.launch(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Login, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Войти в RuTracker")
                    }
                }
            }
        }

        // О приложении
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
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
