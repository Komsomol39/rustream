package com.komsomol.rustream.ui.screens.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.komsomol.rustream.data.search.RuTrackerCookieStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RuTrackerLoginActivity : ComponentActivity() {

    @Inject lateinit var cookieStore: RuTrackerCookieStore

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
        }

        // Очищаем старые куки
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                // После логина RuTracker редиректит на index.php
                if (url.contains("rutracker") && url.contains("index.php") && !url.contains("login")) {
                    // Даём время на установку кук
                    view.postDelayed({
                        extractAndSaveCookies(url)
                    }, 1000)
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url.contains("index.php") && !url.contains("login")) {
                    val cookies = CookieManager.getInstance().getCookie(url)
                    if (cookies != null && cookies.contains("bb_session")) {
                        extractAndSaveCookies(url)
                    }
                }
            }
        }

        webView.loadUrl("https://rutracker.net/forum/login.php")
    }

    private fun extractAndSaveCookies(url: String) {
        val cookies = CookieManager.getInstance().getCookie(url) ?: return
        // Парсим строку куков "name=value; name2=value2"
        val cookieMap = cookies.split(";").mapNotNull { part ->
            val eq = part.indexOf("=")
            if (eq > 0) part.substring(0, eq).trim() to part.substring(eq + 1).trim()
            else null
        }.toMap()

        val bbSession = cookieMap["bb_session"] ?: return
        val parts = bbSession.split("-")
        val userId = parts.getOrNull(1)?.toIntOrNull() ?: 0

        if (userId > 0) {
            cookieStore.saveCookies(cookies)
            runOnUiThread {
                Toast.makeText(this, "RuTracker: авторизация успешна ✓", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
        }
    }
}
