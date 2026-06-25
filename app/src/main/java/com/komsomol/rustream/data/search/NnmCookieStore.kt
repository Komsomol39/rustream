package com.komsomol.rustream.data.search

import android.content.Context
import android.util.Log
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
        private const val TAG   = "NnmCookieStore"
    }

    fun saveCookies(rawCookies: String) = runBlocking {
        Log.d(TAG, "Saving cookies: $rawCookies")
        context.dataStore.edit { it[KEY_COOKIES] = rawCookies }
    }

    fun getRawCookies(): String = runBlocking {
        context.dataStore.data.map { it[KEY_COOKIES] ?: "" }.first()
    }

    fun isLoggedIn(): Boolean {
        val raw = getRawCookies()
        if (raw.isBlank()) return false
        val cookies = parseCookies(raw)
        Log.d(TAG, "Checking login, cookie keys: ${cookies.keys}")
        // NNM использует разные имена сессионного куки в зависимости от версии phpBB
        // Проверяем любой из возможных вариантов
        val sessionCookies = listOf(
            "phpbb2mysql_4_sid", "phpbb2mysql_sid",
            "phpbb2mysql_3_sid", "phpbb_sid",
            "NNMSid", "sid"
        )
        return sessionCookies.any { name ->
            val value = cookies[name]
            !value.isNullOrBlank() && value != "0" && value.length > 5
        }
    }

    fun clearCookies() = runBlocking { context.dataStore.edit { it[KEY_COOKIES] = "" } }

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
