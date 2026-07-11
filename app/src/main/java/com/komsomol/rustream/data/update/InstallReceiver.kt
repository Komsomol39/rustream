package com.komsomol.rustream.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

/**
 * Принимает статусы сессии PackageInstaller.
 * PENDING_USER_ACTION приходит сразу после commit — показываем системное
 * подтверждение установки (приложение в этот момент на переднем плане,
 * т.к. установка запущена пользователем).
 */
class InstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    if (confirm != null) context.startActivity(confirm)
                } catch (e: Exception) {
                    Log.e(TAG, "confirm failed: ${e.message}")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Log.d(TAG, "install ok")
            else -> Log.e(TAG, "install failed: " +
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.komsomol.rustream.INSTALL_STATUS"
        private const val TAG = "InstallReceiver"
    }
}
