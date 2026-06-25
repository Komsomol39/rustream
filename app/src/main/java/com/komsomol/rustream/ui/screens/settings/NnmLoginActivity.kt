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
import com.komsomol.rustream.data.search.NnmCookieStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            textSize = 12f
            setPadding(16, 4, 16, 4)
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
        ).apply { addRule(RelativeLayout.BELOW, progressBar.id); topMargin = 24 }

        root.addView(progressBar)
        root.addView(statusText)
        root.addView(webView, webParams)
        setContentView(root)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        CookieManager.getInstance().removeAllCookies(null)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = View.GONE
                statusText.text = "Войдите в аккаунт NNM-Club"
                if (loginDetected) return

                val cookies = CookieManager.getInstance().getCookie(url) ?: return
                // NNM использует phpbb2mysql_4_sid или phpbb2mysql_sid
                if (cookies.contains("phpbb2mysql") && url.contains("nnmclub") && !url.contains("login")) {
                    loginDetected = true
                    cookieStore.saveCookies(cookies)
                    Toast.makeText(this@NnmLoginActivity, "✓ NNM-Club: авторизация успешна!", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) statusText.text = "Ошибка соединения, повторная попытка..."
            }
        }

        webView.loadUrl("https://nnmclub.to/forum/login.php")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
