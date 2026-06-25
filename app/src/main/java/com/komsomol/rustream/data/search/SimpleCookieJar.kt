package com.komsomol.rustream.data.search

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class SimpleCookieJar : CookieJar {
    private val store = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store.getOrPut(url.host) { mutableListOf() }.apply {
            removeAll { c -> cookies.any { it.name == c.name } }
            addAll(cookies)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store[url.host] ?: emptyList()

    /** Прямой доступ к куки по хосту и имени */
    fun getCookie(host: String, name: String): String? =
        store[host]?.firstOrNull { it.name == name }?.value

    fun hasSessionCookie(host: String): Boolean {
        val session = getCookie(host, "bb_session") ?: return false
        // Валидная сессия: "0-<userId>-<token>" где userId != 0
        val parts = session.split("-")
        return parts.size >= 2 && (parts.getOrNull(1)?.toIntOrNull() ?: 0) > 0
    }
}
