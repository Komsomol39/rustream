package com.komsomol.rustream.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

@dagger.hilt.android.AndroidEntryPoint
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    @javax.inject.Inject
    lateinit var musicPlayer: com.komsomol.rustream.data.music.PlayerController

    private var player: ExoPlayer? = null
    private var videoPath: String = ""

    // Последняя валидная позиция и длительность, обновляются во время игры.
    // Нужны потому, что при finish() ExoPlayer может успеть сброситься в 0.
    private var lastPos: Long = 0L
    private var lastDur: Long = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            player?.let {
                val pos = it.currentPosition
                val dur = it.duration
                if (pos > 0) lastPos = pos
                if (dur > 0) lastDur = dur
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path == null) { finish(); return }
        videoPath = path

        // Видео и музыка не должны играть одновременно — глушим музыку
        try { musicPlayer.pauseForExternal() } catch (_: Exception) {}

        val renderers = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val p = ExoPlayer.Builder(this, renderers).build()
        player = p

        val view = PlayerView(this)
        view.player = p
        view.keepScreenOn = true
        view.setShowSubtitleButton(true)

        val backBtn = android.widget.ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = null
            alpha = 0.6f
            setColorFilter(android.graphics.Color.WHITE)
            // Сохраняем ДО finish(), пока плеер ещё жив
            setOnClickListener { savePosition(); finish() }
        }
        val shareBtn = android.widget.ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_share)
            background = null
            alpha = 0.6f
            setColorFilter(android.graphics.Color.WHITE)
            setOnClickListener { shareVideo() }
        }
        val root = android.widget.FrameLayout(this)
        root.addView(view, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        val d = (resources.displayMetrics.density * 12).toInt()
        val sz = (resources.displayMetrics.density * 40).toInt()
        root.addView(backBtn, android.widget.FrameLayout.LayoutParams(sz, sz).apply {
            leftMargin = d; topMargin = d
        })
        root.addView(shareBtn, android.widget.FrameLayout.LayoutParams(sz, sz).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            rightMargin = d; topMargin = d
        })
        setContentView(root)

        view.setControllerVisibilityListener(
            androidx.media3.ui.PlayerView.ControllerVisibilityListener { visibility ->
                backBtn.visibility = visibility
                shareBtn.visibility = visibility
            })

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insets = WindowInsetsControllerCompat(window, view)
        insets.hide(WindowInsetsCompat.Type.systemBars())
        insets.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val savedPos = positionPrefs().getLong(videoPath, 0L)
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))), savedPos)
        p.prepare()
        p.play()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        savePosition()
    }

    override fun onStop() {
        super.onStop()
        savePosition()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(ticker)
        savePosition()
        player?.release()
        player = null
    }

    /**
     * Поделиться текущим видеофайлом: отдаём его другим приложениям через
     * FileProvider (напрямую file:// нельзя — FileUriExposedException).
     * Плеер ставим на паузу, чтобы звук не играл поверх чужого приложения.
     */
    private fun shareVideo() {
        try {
            val file = File(videoPath)
            if (!file.exists()) {
                android.widget.Toast.makeText(this, "Файл не найден",
                    android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            player?.pause()
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file)
            val mime = when (file.extension.lowercase()) {
                "mp4", "m4v" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "avi" -> "video/x-msvideo"
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                else -> "video/*"
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Отправить видео"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(this,
                "Не удалось поделиться: ${e.message}",
                android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun positionPrefs() =
        getSharedPreferences("playback_positions", MODE_PRIVATE)

    // Используем последнюю валидную позицию из тикера, а не мгновенное чтение
    // (которое при закрытии может уже вернуть 0)
    private fun savePosition() {
        // подхватить самое свежее значение, если плеер ещё жив
        player?.let {
            val pos = it.currentPosition
            val dur = it.duration
            if (pos > 0) lastPos = pos
            if (dur > 0) lastDur = dur
        }
        if (lastDur <= 0 || lastPos <= 0) return
        val prefs = positionPrefs()
        if (lastPos > 10_000 && lastPos < lastDur - 30_000) {
            prefs.edit().putLong(videoPath, lastPos).apply()
        } else if (lastPos >= lastDur - 30_000) {
            prefs.edit().remove(videoPath).apply()
        }
    }

    companion object {
        const val EXTRA_PATH = "path"
    }
}
