package com.komsomol.rustream.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val darkTheme by viewModel.darkTheme.collectAsState()
    val downloadPath by viewModel.downloadPath.collectAsState()
    val ruTrackerLogin by viewModel.ruTrackerLogin.collectAsState()
    val ruTrackerPassword by viewModel.ruTrackerPassword.collectAsState()

    var loginField by remember(ruTrackerLogin) { mutableStateOf(ruTrackerLogin) }
    var passwordField by remember(ruTrackerPassword) { mutableStateOf(ruTrackerPassword) }
    var pathField by remember(downloadPath) { mutableStateOf(downloadPath) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)

        // Theme
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Тёмная тема", style = MaterialTheme.typography.bodyLarge)
                    Text("Текущая: ${if (darkTheme) "Тёмная" else "Светлая"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = darkTheme, onCheckedChange = viewModel::setDarkTheme)
            }
        }

        // Download path
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Папка загрузок", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = pathField,
                    onValueChange = { pathField = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Путь") },
                    singleLine = true
                )
                Button(
                    onClick = { viewModel.setDownloadPath(pathField) },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Сохранить") }
            }
        }

        // RuTracker (для будущего использования)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("RuTracker (скоро)", style = MaterialTheme.typography.titleMedium)
                Text("Для поиска на RuTracker потребуется аккаунт",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = loginField,
                    onValueChange = { loginField = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Логин") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = passwordField,
                    onValueChange = { passwordField = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Button(
                    onClick = {
                        viewModel.setRuTrackerLogin(loginField)
                        viewModel.setRuTrackerPassword(passwordField)
                    },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Сохранить") }
            }
        }

        // About
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
