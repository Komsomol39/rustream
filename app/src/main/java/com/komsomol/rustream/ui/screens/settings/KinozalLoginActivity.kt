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
import com.komsomol.rustream.data.search.KinozalCookieStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class KinozalLoginActivity : ComponentActivity() {

    @Inject lateinit var cookieStore: KinozalCookieStore

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
            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = View.GONE
                if (loginDetected) return

                val rawCookies = CookieManager.getInstance().getCookie(url) ?: ""
                Log.d("KinozalLogin", "URL=" + url + " cookies=" + rawCookies)

                if (url.contains("login")) {
                    statusText.text = "Войдите в аккаунт Kinozal"
                } else {
                    statusText.text = "Проверка авторизации..."
                }

                // Успех: ушли со страницы логина и появились куки uid/pass
                if (!url.contains("login.php") && url.contains("kinozal") &&
                    rawCookies.contains("uid=") && rawCookies.contains("pass=")) {
                    Log.d("KinozalLogin", "Login detected!")
                    loginDetected = true
                    cookieStore.saveCookies(rawCookies)
                    runOnUiThread {
                        Toast.makeText(this@KinozalLoginActivity,
                            "✓ Kinozal: авторизация успешна!", Toast.LENGTH_SHORT).show()
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    statusText.text = "Ошибка: " + error.description
                }
            }
        }

        webView.loadUrl("https://kinozal.tv/login.php")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
