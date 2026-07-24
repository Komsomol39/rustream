package com.komsomol.rustream.data.search

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.komsomol.rustream.data.settings.SecretCipher
import com.komsomol.rustream.data.settings.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    @ApplicationContext private val context: Context,
    private val cipher: SecretCipher
) : CookieJar {

    // Кэш в памяти: куки читаются на каждый HTTP-запрос,
    // блокирующее чтение DataStore каждый раз — лишнее
    @Volatile private var cachedCookies: String? = null

    init { warmCache() }

    /**
     * Прогрев кэша в фоне. Куки читаются на каждый запрос, а чтение DataStore
     * блокирующее — после прогрева обращения берут значение из памяти и
     * не задерживают поток UI.
     */
    private fun warmCache() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val v = context.dataStore.data
                    .map { cipher.decrypt(it[KEY_COOKIES] ?: "") }.first()
                // не перетираем значение, уже записанное свежим логином
                if (cachedCookies == null) cachedCookies = v
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private val KEY_COOKIES    = stringPreferencesKey("nnm_webview_cookies")
        private const val HOST = "nnmclub.to"
    }

    fun saveCookies(rawCookies: String) {
        runBlocking { context.dataStore.edit { it[KEY_COOKIES] = cipher.encrypt(rawCookies) } }
        cachedCookies = rawCookies
    }

    fun getRawCookies(): String =
        cachedCookies ?: runBlocking {
            context.dataStore.data.map { cipher.decrypt(it[KEY_COOKIES] ?: "") }.first()
        }.also { cachedCookies = it }

    fun isLoggedIn(): Boolean {
        val raw = getRawCookies()
        if (raw.isBlank()) return false
        val cookies = parseCookies(raw)
        return cookies.any { (name, value) ->
            name.startsWith("phpbb2mysql") && value.isNotBlank()
        }
    }

    fun clearCookies() {
        runBlocking { context.dataStore.edit { it[KEY_COOKIES] = "" } }
        cachedCookies = ""
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
