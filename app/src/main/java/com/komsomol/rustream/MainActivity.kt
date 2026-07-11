package com.komsomol.rustream

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.komsomol.rustream.service.TorrentService
import com.komsomol.rustream.ui.navigation.AppNavGraph
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.komsomol.rustream.ui.theme.RuStreamTheme
import com.komsomol.rustream.ui.update.UpdateDialogs
import com.komsomol.rustream.ui.update.UpdateViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @javax.inject.Inject lateinit var grabRepo: com.komsomol.rustream.data.grab.GrabRepository
    @javax.inject.Inject lateinit var settingsRepo: com.komsomol.rustream.data.settings.SettingsRepository

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* запускаем сервис в любом случае */ startTorrentService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Доступ ко всем файлам (Android 11+): чтобы libtorrent мог писать в Download.
        // Просим один раз — без разрешения приложение работает в своей папке.
        val prefs = getSharedPreferences("onboarding", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !android.os.Environment.isExternalStorageManager() &&
            !prefs.getBoolean("asked_all_files", false)) {
            prefs.edit().putBoolean("asked_all_files", true).apply()
            try {
                val i = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                i.data = android.net.Uri.parse("package:" + packageName)
                startActivity(i)
            } catch (_: Exception) {
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {}
            }
        }

        // Запрашиваем разрешение на уведомления (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startTorrentService()
        }

        // Тихо обновляем yt-dlp при каждом запуске
        grabRepo.autoUpdateSilently()

        setContent {
            val dark by settingsRepo.darkTheme
                .collectAsState(initial = true)
            RuStreamTheme(darkTheme = dark) {
                AppNavGraph()

                // Проверка обновлений при запуске (если включено в настройках)
                val updateVm: UpdateViewModel = hiltViewModel()
                val updateState by updateVm.state.collectAsState()
                LaunchedEffect(Unit) { updateVm.checkOnLaunch() }
                // Окно установки APK запускаем отсюда (передний план) —
                // из фонового ресивера его блокирует MIUI/HyperOS
                LaunchedEffect(Unit) {
                    com.komsomol.rustream.data.update.InstallPrompt.confirmIntent.collect { intent ->
                        try { startActivity(intent) } catch (_: Exception) {}
                    }
                }
                UpdateDialogs(updateState, updateVm)
            }
        }
    }

    private fun startTorrentService() {
        val intent = Intent(this, TorrentService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
