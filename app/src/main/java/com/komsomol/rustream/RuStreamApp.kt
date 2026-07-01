package com.komsomol.rustream

import android.app.Application
import android.os.Environment
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

@HiltAndroidApp
class RuStreamApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val dir = File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "RuStream")
                dir.mkdirs()
                File(dir, "crash.txt").writeText(
                    "Thread: " + thread.name + "\n" + sw.toString())
            } catch (_: Exception) {}
            prev?.uncaughtException(thread, e)
        }
    }
}
