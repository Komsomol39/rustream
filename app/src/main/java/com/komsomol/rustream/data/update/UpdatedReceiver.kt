package com.komsomol.rustream.data.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import com.komsomol.rustream.MainActivity
import java.io.File

/**
 * Срабатывает после того, как система заменила приложение новой версией:
 * убирает скачанный APK из кэша и предлагает открыть приложение снова
 * (Android не разрешает перезапуститься самим из фона).
 */
class UpdatedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        File(context.cacheDir, "updates").deleteRecursively()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL, "Обновления", NotificationManager.IMPORTANCE_DEFAULT))

        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) { null }

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE)

        nm.notify(1001, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("RuStream обновлён" + if (version != null) " до $version" else "")
            .setContentText("Нажмите, чтобы открыть")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }

    companion object {
        private const val CHANNEL = "updates"
    }
}
