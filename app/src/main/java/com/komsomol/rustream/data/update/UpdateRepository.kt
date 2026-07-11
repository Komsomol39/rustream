package com.komsomol.rustream.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String
)

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun currentVersionCode(): Long = try {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode
        else @Suppress("DEPRECATION") pi.versionCode.toLong()
    } catch (_: Exception) { 0L }

    fun currentVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }

    /** null — обновлений нет или проверка не удалась */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val resp = client.newCall(Request.Builder().url(VERSION_URL).build()).execute()
            val body = resp.use { if (it.isSuccessful) it.body?.string() else null }
                ?: return@withContext null
            val j = JSONObject(body)
            val info = UpdateInfo(
                versionCode = j.getLong("versionCode"),
                versionName = j.getString("versionName"),
                apkUrl      = j.getString("apkUrl")
            )
            if (info.versionCode > currentVersionCode()) info else null
        } catch (e: Exception) {
            Log.e(TAG, "check failed: ${e.message}")
            null
        }
    }

    /**
     * Скачивает APK в кэш, отдаёт прогресс 0..1. Файл именуется по версии:
     * если эта версия уже скачана целиком — загрузка пропускается.
     * Качаем во временный файл и переименовываем, так что существование
     * итогового файла гарантирует целостность.
     */
    suspend fun downloadApk(info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "update-v${info.versionCode}.apk")
            if (file.exists() && file.length() > 0) {
                onProgress(1f)
                return@withContext file
            }
            // прибираем APK других версий
            dir.listFiles()?.forEach { if (it.name != file.name) it.delete() }

            val tmp = File(dir, file.name + ".part")
            val resp = client.newCall(Request.Builder().url(info.apkUrl).build()).execute()
            if (!resp.isSuccessful) { resp.close(); error("HTTP ${resp.code}") }
            val total = resp.body!!.contentLength()
            resp.body!!.byteStream().use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress(read.toFloat() / total)
                    }
                }
            }
            if (!tmp.renameTo(file)) error("Не удалось сохранить файл")
            file
        }

    /**
     * Установка через PackageInstaller: без вопроса «чем открыть»,
     * система сама закрывает приложение и заменяет его. Единственное
     * взаимодействие — системное подтверждение «Обновить».
     */
    fun installApk(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply { setAppPackageName(context.packageName) }

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("app.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out, 64 * 1024) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallReceiver::class.java)
                .setAction(InstallReceiver.ACTION_INSTALL_STATUS)
            val pi = PendingIntent.getBroadcast(
                context, sessionId, intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            session.commit(pi.intentSender)
        }
    }

    companion object {
        private const val TAG = "UpdateRepo"
        private const val VERSION_URL =
            "https://github.com/Komsomol39/rustream/releases/latest/download/version.json"
    }
}
