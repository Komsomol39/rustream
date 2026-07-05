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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path == null) { finish(); return }

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
        setContentView(view)

        // Полноэкранный режим
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insets = WindowInsetsControllerCompat(window, view)
        insets.hide(WindowInsetsCompat.Type.systemBars())
        insets.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        p.prepare()
        p.play()
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_PATH = "path"
    }
}
