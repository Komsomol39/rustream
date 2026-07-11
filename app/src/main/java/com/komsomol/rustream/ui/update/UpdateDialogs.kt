package com.komsomol.rustream.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Диалоги состояния обновления. Рендерит Available/Downloading/Error, остальное — ничего. */
@Composable
fun UpdateDialogs(state: UpdateUiState, viewModel: UpdateViewModel) {
    when (state) {
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = { viewModel.dismiss() },
            title = { Text("Доступно обновление") },
            text = {
                Text("Версия " + state.info.versionName +
                     " (установлена " + viewModel.currentVersion + ")")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.downloadAndInstall(state.info) }) {
                    Text("Скачать и установить")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismiss() }) { Text("Позже") }
            }
        )
        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Подготовка обновления") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("%.0f%%".format(state.progress * 100),
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {}
        )
        is UpdateUiState.Error -> AlertDialog(
            onDismissRequest = { viewModel.dismiss() },
            title = { Text("Не удалось обновить") },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismiss() }) { Text("ОК") }
            }
        )
        else -> Unit
    }
}
