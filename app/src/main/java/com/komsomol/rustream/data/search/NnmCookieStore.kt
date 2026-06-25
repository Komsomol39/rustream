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
class NnmCookieStore @Inject constructor(
    @ApplicationContext private val context: Context
) : CookieJar {

    companion object {
        private val KEY_COOKIES = stringPreferencesKey("nnm_webview_cookies")
        private const val HOST  = "nnmclub.to"
    }

    fun saveCookies(rawCookies: String) = runBlocking {
        context.dataStore.edit { it[KEY_COOKIES] = rawCookies }
    }

    fun isLoggedIn(): Boolean {
        val raw = runBlocking { context.dataStore.data.map { it[KEY_COOKIES] ?: "" }.first() }
        return parseCookies(raw).containsKey("phpbb2mysql_4_sid") ||
               parseCookies(raw).containsKey("phpbb2mysql_sid")
    }

    fun clearCookies() = runBlocking { context.dataStore.edit { it[KEY_COOKIES] = "" } }

    private fun parseCookies(raw: String): Map<String, String> =
        raw.split(";").mapNotNull { part ->
            val eq = part.indexOf("=")
            if (eq > 0) part.substring(0, eq).trim() to part.substring(eq + 1).trim()
            else null
        }.toMap()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {}

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.host.contains("nnmclub")) return emptyList()
        val raw = runBlocking { context.dataStore.data.map { it[KEY_COOKIES] ?: "" }.first() }
        if (raw.isBlank()) return emptyList()
        return parseCookies(raw).mapNotNull { (name, value) ->
            try { Cookie.Builder().name(name).value(value).domain(HOST).build() }
            catch (_: Exception) { null }
        }
    }
}
