package com.komsomol.rustream.ui.screens.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.komsomol.rustream.data.search.RuTrackerCookieStore
import com.komsomol.rustream.data.search.RuTrackerMirrors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class RuTrackerLoginActivity : ComponentActivity() {

    @Inject lateinit var cookieStore: RuTrackerCookieStore

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private var mirrorIndex = 0
    private var loginDetected = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Простой layout без XML
        val root = RelativeLayout(this)

        webView = WebView(this).apply {
            id = View.generateViewId()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, 8
            )
        }

        statusText = TextView(this).apply {
            text = "Поиск рабочего зеркала..."
            textSize = 12f
            setPadding(16, 4, 16, 4)
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply { addRule(RelativeLayout.BELOW, progressBar.id) }
        }

        root.addView(progressBar)
        root.addView(statusText)

        val webParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        ).apply {
            addRule(RelativeLayout.BELOW, progressBar.id)
            topMargin = 24
        }
        root.addView(webView, webParams)
        setContentView(root)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = View.GONE
                statusText.text = "Войдите в аккаунт RuTracker"

                if (loginDetected) return
                // Проверяем — залогинились ли
                val cookies = CookieManager.getInstance().getCookie(url) ?: return
                if (!url.contains("login") && cookies.contains("bb_session")) {
                    val bbSession = cookies.split(";")
                        .firstOrNull { it.trim().startsWith("bb_session") }
                        ?.substringAfter("=")?.trim() ?: return
                    val userId = bbSession.split("-").getOrNull(1)?.toIntOrNull() ?: 0
                    if (userId > 0) {
                        loginDetected = true
                        cookieStore.saveCookies(cookies)
                        Toast.makeText(
                            this@RuTrackerLoginActivity,
                            "✓ Авторизация успешна!",
                            Toast.LENGTH_SHORT
                        ).show()
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (!request.isForMainFrame) return
                // Ошибка на главном фрейме — пробуем следующее зеркало
                mirrorIndex++
                tryNextMirror()
            }
        }

        // Начинаем с поиска рабочего зеркала
        findMirrorAndLoad()
    }

    private fun findMirrorAndLoad() {
        progressBar.visibility = View.VISIBLE
        statusText.text = "Поиск рабочего зеркала..."

        CoroutineScope(Dispatchers.IO).launch {
            val mirror = RuTrackerMirrors.findWorking()
            withContext(Dispatchers.Main) {
                if (mirror != null) {
                    statusText.text = "Зеркало: $mirror"
                    webView.loadUrl("$mirror/forum/login.php")
                } else {
                    // Все зеркала недоступны — загружаем первое напрямую, пусть пользователь попробует
                    statusText.text = "Нет доступных зеркал, пробуем ${RuTrackerMirrors.ALL[0]}..."
                    webView.loadUrl("${RuTrackerMirrors.ALL[0]}/forum/login.php")
                }
            }
        }
    }

    private fun tryNextMirror() {
        if (mirrorIndex >= RuTrackerMirrors.ALL.size) {
            runOnUiThread {
                statusText.text = "Все зеркала недоступны. Попробуйте позже."
                progressBar.visibility = View.GONE
            }
            return
        }
        val mirror = RuTrackerMirrors.ALL[mirrorIndex]
        runOnUiThread {
            statusText.text = "Ошибка, пробуем $mirror..."
            progressBar.visibility = View.VISIBLE
            webView.loadUrl("$mirror/forum/login.php")
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
