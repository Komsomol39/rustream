package com.komsomol.rustream.data.search

import com.komsomol.rustream.domain.model.ContentCategory
import com.komsomol.rustream.domain.model.SearchResult
import com.komsomol.rustream.domain.model.SearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuTrackerProvider @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(SimpleCookieJar())
        .build()

    private val BASE = "https://rutracker.net/forum"
    private var loggedIn = false
    private var login = ""
    private var password = ""

    fun setCredentials(login: String, password: String) {
        if (this.login != login || this.password != password) {
            this.login = login
            this.password = password
            loggedIn = false
        }
    }

    suspend fun search(query: String, category: ContentCategory): List<SearchResult> =
        withContext(Dispatchers.IO) {
            if (login.isBlank() || password.isBlank()) return@withContext emptyList()
            if (!loggedIn) {
                loggedIn = doLogin()
                if (!loggedIn) return@withContext emptyList()
            }
            doSearch(query, category)
        }

    private fun doLogin(): Boolean {
        return try {
            val body = FormBody.Builder()
                .add("login_username", login)
                .add("login_password", password)
                .add("login", "Вход")
                .build()
            val req = Request.Builder()
                .url("$BASE/login.php")
                .post(body)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                .header("Referer", "$BASE/index.php")
                .build()
            val html = client.newCall(req).execute().use { it.body?.string() ?: "" }
            login.lowercase() in html.lowercase()
        } catch (e: Exception) {
            false
        }
    }

    private fun doSearch(query: String, category: ContentCategory): List<SearchResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder()
            .url("$BASE/tracker.php?nm=$encoded")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
            .header("Referer", "$BASE/index.php")
            .build()

        val html = client.newCall(req).execute().use { it.body?.string() ?: "" }
        val doc = Jsoup.parse(html)
        val results = mutableListOf<SearchResult>()

        // Проверенные селекторы из теста
        doc.select("table#tor-tbl tbody tr.tCenter").take(50).forEach { row ->
            try {
                val topicId = row.attr("data-topic_id").takeIf { it.isNotEmpty() } ?: return@forEach
                val titleEl = row.selectFirst("a.tLink") ?: return@forEach
                val title = titleEl.text().trim()
                val catEl = row.selectFirst("td.f-name-col a")
                val sizeEl = row.selectFirst("td.tor-size")
                val seedsEl = row.selectFirst("td.seedmed b") ?: row.selectFirst("b.seedmed")
                val leechEl = row.selectFirst("td.leechmed b")
                val dlEl = row.selectFirst("a.tr-dl")

                val sizeText = sizeEl?.text()?.trim() ?: ""
                val seeders = seedsEl?.text()?.trim()?.toIntOrNull() ?: 0
                val leechers = leechEl?.text()?.trim()?.toIntOrNull() ?: 0
                val torrentUrl = dlEl?.let { "$BASE/${it.attr("href")}" }

                val detectedCat = when {
                    category != ContentCategory.ALL -> category
                    else -> detectCategory(title, catEl?.text() ?: "")
                }

                results.add(SearchResult(
                    title = title,
                    source = SearchSource.RUTRACKER,
                    category = detectedCat,
                    sizeBytes = parseSize(sizeText),
                    seeders = seeders,
                    leechers = leechers,
                    magnetUri = null,
                    torrentUrl = torrentUrl,
                    detailUrl = "$BASE/viewtopic.php?t=$topicId",
                    uploadDate = ""
                ))
            } catch (_: Exception) {}
        }
        return results
    }

    private fun detectCategory(title: String, cat: String): ContentCategory {
        val text = (title + " " + cat).lowercase()
        return when {
            text.contains(Regex("кино|фильм|сериал|movie|film|series|hdtv|bdrip|mkv|avi|mp4|blu-ray|bluray")) -> ContentCategory.VIDEO
            text.contains(Regex("музык|music|mp3|flac|альбом|album|lossless|hi-res|soundtrack")) -> ContentCategory.MUSIC
            else -> ContentCategory.ALL
        }
    }

    private fun parseSize(text: String): Long {
        val lower = text.lowercase().replace("↓", "").trim()
        val num = lower.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: return 0L
        return when {
            lower.contains("gb") || lower.contains("гб") || lower.contains("tib") -> (num * 1024 * 1024 * 1024).toLong()
            lower.contains("mb") || lower.contains("мб") || lower.contains("mib") -> (num * 1024 * 1024).toLong()
            lower.contains("kb") || lower.contains("кб") -> (num * 1024).toLong()
            else -> 0L
        }
    }
}
