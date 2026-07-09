package com.komsomol.rustream.player

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private var videoPath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path == null) { finish(); return }
        videoPath = path

        // FFmpeg-декодер (Jellyfin) подхватится как расширение:
        // аппаратные кодеки в приоритете, DTS/AC3 и прочее декодирует FFmpeg
        val renderers = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val p = ExoPlayer.Builder(this, renderers).build()
        player = p

        val view = PlayerView(this)
        view.player = p
        view.keepScreenOn = true
        view.setShowSubtitleButton(true)

        // Полупрозрачная кнопка "назад" поверх видео, видна вместе с контролами
        val backBtn = android.widget.ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = null
            alpha = 0.6f
            setColorFilter(android.graphics.Color.WHITE)
            setOnClickListener { finish() }
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
        setContentView(root)

        // Кнопка появляется/прячется вместе с панелью управления
        view.setControllerVisibilityListener(
            androidx.media3.ui.PlayerView.ControllerVisibilityListener { visibility ->
                backBtn.visibility = visibility
            })

        // Полноэкранный режим
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insets = WindowInsetsControllerCompat(window, view)
        insets.hide(WindowInsetsCompat.Type.systemBars())
        insets.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Продолжаем с места, где остановились в прошлый раз
        val savedPos = positionPrefs().getLong(videoPath, 0L)
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))), savedPos)
        p.prepare()
        p.play()
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
        savePosition()
        player?.release()
        player = null
    }

    private fun positionPrefs() =
        getSharedPreferences("playback_positions", MODE_PRIVATE)

    // Сохраняем позицию; если досмотрели почти до конца — сбрасываем,
    // чтобы следующий запуск был с начала
    private fun savePosition() {
        val p = player ?: return
        val dur = p.duration
        val pos = p.currentPosition
        if (dur <= 0) return
        val prefs = positionPrefs()
        if (pos > 10_000 && pos < dur - 30_000) {
            prefs.edit().putLong(videoPath, pos).apply()
        } else if (pos >= dur - 30_000) {
            prefs.edit().remove(videoPath).apply()
        }
    }

    companion object {
        const val EXTRA_PATH = "path"
    }
}
