package com.komsomol.rustream.data.search

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.komsomol.rustream.data.settings.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NnmCookieStore @Inject constructor(
    @ApplicationContext private val context: Context
) : CookieJar {

    companion object {
        private val KEY_COOKIES    = stringPreferencesKey("nnm_webview_cookies")
        private val KEY_DEBUG_HTML = stringPreferencesKey("nnm_debug_html")
        private const val HOST = "nnmclub.to"
        private const val TAG  = "NnmCookieStore"
    }

    fun saveCookies(rawCookies: String) = runBlocking {
        Log.d(TAG, "Saving: $rawCookies")
        context.dataStore.edit { it[KEY_COOKIES] = rawCookies }
    }

    fun saveDebugHtml(html: String) = runBlocking {
        // Сохраняем: залогинен?, TR классы, первые 500 символов body
        context.dataStore.edit { it[KEY_DEBUG_HTML] = html }
    }

    val debugHtml: Flow<String> = context.dataStore.data.map { it[KEY_DEBUG_HTML] ?: "" }

    fun getRawCookies(): String = runBlocking {
        context.dataStore.data.map { it[KEY_COOKIES] ?: "" }.first()
    }

    fun isLoggedIn(): Boolean {
        val raw = getRawCookies()
        if (raw.isBlank()) return false
        val cookies = parseCookies(raw)
        return cookies.any { (name, value) ->
            name.startsWith("phpbb2mysql") && value.isNotBlank()
        }
    }

    fun clearCookies() = runBlocking {
        context.dataStore.edit { it[KEY_COOKIES] = ""; it[KEY_DEBUG_HTML] = "" }
    }

    fun parseCookies(raw: String): Map<String, String> =
        raw.split(";").mapNotNull { part ->
            val eq = part.indexOf("=")
            if (eq > 0) part.substring(0, eq).trim() to part.substring(eq + 1).trim()
            else null
        }.toMap()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {}

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.host.contains("nnmclub")) return emptyList()
        val raw = getRawCookies()
        if (raw.isBlank()) return emptyList()
        return parseCookies(raw).mapNotNull { (name, value) ->
            try { Cookie.Builder().name(name).value(value).domain(HOST).build() }
            catch (_: Exception) { null }
        }
    }
}
