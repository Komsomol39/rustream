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
import com.komsomol.rustream.data.search.NnmCookieStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NnmLoginActivity : ComponentActivity() {

    @Inject lateinit var cookieStore: NnmCookieStore

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
            text = "Загрузка..."
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
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
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
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()

        webView.webViewClient = object : WebViewClient() {
            // Реклама пытается увести на сторонние сайты — не пускаем.
            // Разрешена навигация только по доменам самого трекера.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host ?: return false
                return !(host.contains("nnmclub") || host.contains("nnm-club"))
            }

            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = View.GONE
                if (loginDetected) return

                val rawCookies = CookieManager.getInstance().getCookie(url) ?: ""
                Log.d("NnmLogin", "URL=$url cookies=$rawCookies")

                // Показываем статус
                if (url.contains("login")) {
                    statusText.text = "Войдите в аккаунт NNM-Club"
                } else {
                    statusText.text = "Проверка авторизации..."
                }

                // Успех проверяем по содержимому страницы: у залогиненного
                // пользователя phpBB показывает ссылку выхода. Просто "не login.php
                // и есть куки" мало — phpBB раздаёт куки даже гостям.
                if (url.contains("nnmclub") && rawCookies.isNotBlank()) {
                    view.evaluateJavascript(
                        "(function(){var h=document.body?document.body.innerHTML:'';" +
                        "return h.indexOf('login.php?logout')!==-1||h.indexOf('Выход')!==-1;})()"
                    ) { res ->
                        if (res == "true" && !loginDetected) {
                            Log.d("NnmLogin", "Login detected! Cookies: $rawCookies")
                            loginDetected = true
                            cookieStore.saveCookies(rawCookies)
                            runOnUiThread {
                                Toast.makeText(this@NnmLoginActivity,
                                    "✓ NNM-Club: авторизация успешна!", Toast.LENGTH_SHORT).show()
                                setResult(Activity.RESULT_OK)
                                finish()
                            }
                        }
                    }
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    statusText.text = "Ошибка: ${error.description}"
                }
            }
        }

        CookieManager.getInstance().removeAllCookies {
            webView.loadUrl("https://nnmclub.to/forum/login.php")
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
