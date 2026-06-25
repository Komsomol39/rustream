package com.komsomol.rustream.data.search

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.komsomol.rustream.data.settings.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuTrackerCookieStore @Inject constructor(
    @ApplicationContext private val context: Context
) : CookieJar {

    companion object {
        private val KEY_COOKIES = stringPreferencesKey("rutracker_webview_cookies")
        private const val HOST = "rutracker.net"
    }

    /** Сохраняем сырую строку куков из WebView CookieManager */
    fun saveCookies(rawCookies: String) {
        runBlocking {
            context.dataStore.edit { it[KEY_COOKIES] = rawCookies }
        }
    }

    fun isLoggedIn(): Boolean {
        val raw = runBlocking {
            context.dataStore.data.map { it[KEY_COOKIES] ?: "" }.first()
        }
        val bbSession = parseCookieString(raw)["bb_session"] ?: return false
        val userId = bbSession.split("-").getOrNull(1)?.toIntOrNull() ?: 0
        return userId > 0
    }

    fun clearCookies() {
        runBlocking { context.dataStore.edit { it[KEY_COOKIES] = "" } }
    }

    private fun parseCookieString(raw: String): Map<String, String> =
        raw.split(";").mapNotNull { part ->
            val eq = part.indexOf("=")
            if (eq > 0) part.substring(0, eq).trim() to part.substring(eq + 1).trim()
            else null
        }.toMap()

    // CookieJar interface — OkHttp использует нас для запросов
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // OkHttp сам не сохраняет — куки только из WebView
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.host.contains("rutracker")) return emptyList()
        val raw = runBlocking {
            context.dataStore.data.map { it[KEY_COOKIES] ?: "" }.first()
        }
        if (raw.isBlank()) return emptyList()

        return parseCookieString(raw).mapNotNull { (name, value) ->
            try {
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(HOST)
                    .build()
            } catch (_: Exception) { null }
        }
    }
}
