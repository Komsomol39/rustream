package com.komsomol.rustream.ui.screens.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.util.Log
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
import com.komsomol.rustream.data.search.YoutubeCookieStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Вход в аккаунт Google/YouTube через WebView. После успешного логина
 * забираем куки сессии (SAPISID/__Secure-3PSID/LOGIN_INFO) и сохраняем —
 * yt-dlp будет использовать их как cookies.txt, чтобы обходить бот-проверку.
 */
@AndroidEntryPoint
class YoutubeLoginActivity : ComponentActivity() {

    @Inject lateinit var cookieStore: YoutubeCookieStore

    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private var loginDetected = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = RelativeLayout(this)
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            id = View.generateViewId()
            isIndeterminate = true
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, 8)
        }
        statusText = TextView(this).apply {
            text = "Войдите в аккаунт Google"
            textSize = 13f
            setPadding(16, 6, 16, 6)
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply { addRule(RelativeLayout.BELOW, progressBar.id) }
        }
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Мобильный UA — Google реже показывает «небезопасный браузер» на WebView
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        }
        val webParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        ).apply { addRule(RelativeLayout.BELOW, progressBar.id); topMargin = 32 }

        root.addView(progressBar)
        root.addView(statusText)
        root.addView(webView, webParams)
        setContentView(root)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = View.GONE
                if (loginDetected) return

                val cookies = CookieManager.getInstance().getCookie(url) ?: ""
                Log.d("YtLogin", "URL=$url")

                when {
                    url.contains("accounts.google.com") ->
                        statusText.text = "Войдите в аккаунт Google"
                    url.contains("youtube.com") ->
                        statusText.text = "Проверка авторизации…"
                }

                // Ключевые куки сессии появляются на youtube.com после логина
                val hasSession = cookies.contains("SAPISID") ||
                    cookies.contains("__Secure-3PSID") || cookies.contains("LOGIN_INFO")
                if (url.contains("youtube.com") && hasSession) {
                    loginDetected = true
                    // Собираем куки со всех доменов Google
                    val cm = CookieManager.getInstance()
                    val merged = buildString {
                        append(cm.getCookie("https://www.youtube.com") ?: "")
                        append("; ")
                        append(cm.getCookie("https://.google.com") ?: "")
                    }.ifBlank { cookies }
                    cookieStore.saveCookies(merged)
                    runOnUiThread {
                        Toast.makeText(this@YoutubeLoginActivity,
                            "✓ YouTube: вход выполнен", Toast.LENGTH_SHORT).show()
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) statusText.text = "Ошибка: ${error.description}"
            }
        }

        // Стартуем со страницы логина Google с возвратом на YouTube
        webView.loadUrl("https://accounts.google.com/ServiceLogin?service=youtube" +
            "&continue=https://www.youtube.com/")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
