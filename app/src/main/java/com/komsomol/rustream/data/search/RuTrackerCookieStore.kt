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
        private val KEY_MIRROR  = stringPreferencesKey("rutracker_active_mirror")
    }

    fun saveCookies(rawCookies: String, mirror: String = "https://rutracker.net") {
        runBlocking {
            context.dataStore.edit {
                it[KEY_COOKIES] = rawCookies
                it[KEY_MIRROR]  = mirror
            }
        }
    }

    fun getActiveMirror(): String = runBlocking {
        context.dataStore.data.map { it[KEY_MIRROR] ?: "https://rutracker.net" }.first()
    }

    fun isLoggedIn(): Boolean {
        val raw = runBlocking { context.dataStore.data.map { it[KEY_COOKIES] ?: "" }.first() }
        return isValidSession(raw)
    }

    fun clearCookies() = runBlocking {
        context.dataStore.edit { it[KEY_COOKIES] = ""; it[KEY_MIRROR] = "" }
    }

    private fun isValidSession(raw: String): Boolean {
        val bbSession = parseCookies(raw)["bb_session"] ?: return false
        val userId = bbSession.split("-").getOrNull(1)?.toIntOrNull() ?: 0
        return userId > 0
    }

    private fun parseCookies(raw: String): Map<String, String> =
        raw.split(";").mapNotNull { part ->
            val eq = part.indexOf("=")
            if (eq > 0) part.substring(0, eq).trim() to part.substring(eq + 1).trim()
            else null
        }.toMap()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { /* только WebView */ }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.host.contains("rutracker")) return emptyList()
        val raw = runBlocking { context.dataStore.data.map { it[KEY_COOKIES] ?: "" }.first() }
        if (raw.isBlank()) return emptyList()
        return parseCookies(raw).mapNotNull { (name, value) ->
            try { Cookie.Builder().name(name).value(value).domain("rutracker.net").build() }
            catch (_: Exception) { null }
        }
    }
}
