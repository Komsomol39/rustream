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
}
