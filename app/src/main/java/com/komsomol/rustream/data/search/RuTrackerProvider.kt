package com.komsomol.rustream.data.search

import com.komsomol.rustream.domain.model.ContentCategory
import com.komsomol.rustream.domain.model.SearchResult
import com.komsomol.rustream.domain.model.SearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuTrackerProvider @Inject constructor() {

    private val cookieJar = SimpleCookieJar()
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(cookieJar)
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
            // Сначала получаем страницу чтобы установить начальные куки
            val getReq = Request.Builder()
                .url("$BASE/index.php")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36")
                .build()
            client.newCall(getReq).execute().use { }

            // Логин POST
            val body = FormBody.Builder()
                .add("login_username", login)
                .add("login_password", password)
                .add("login", "вход")
                .build()
            val req = Request.Builder()
                .url("$BASE/login.php")
                .post(body)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36")
                .header("Referer", "$BASE/index.php")
                .header("Accept-Language", "ru-RU,ru;q=0.9")
                .build()
            client.newCall(req).execute().use { response ->
                // RuTracker возвращает 500 при успешном логине — проверяем по кукам
                val cookies = cookieJar.loadForRequest(
                    okhttp3.HttpUrl.Builder()
                        .scheme("https").host("rutracker.net").build()
                )
                val hasSession = cookies.any { it.name == "bb_session" && !it.value.startsWith("0-0-") }
                hasSession
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun doSearch(query: String, category: ContentCategory): List<SearchResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder()
            .url("$BASE/tracker.php?nm=$encoded")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36")
            .header("Referer", "$BASE/index.php")
            .header("Accept-Language", "ru-RU,ru;q=0.9")
            .build()

        val html = client.newCall(req).execute().use { it.body?.string() ?: "" }
        val doc = Jsoup.parse(html)
        val results = mutableListOf<SearchResult>()

        doc.select("table#tor-tbl tbody tr.tCenter").take(50).forEach { row ->
            try {
                val topicId = row.attr("data-topic_id").takeIf { it.isNotEmpty() } ?: return@forEach
                val titleEl = row.selectFirst("a.tLink") ?: return@forEach
                val catEl   = row.selectFirst("td.f-name-col a")
                val sizeEl  = row.selectFirst("td.tor-size")
                val seedsEl = row.selectFirst("b.seedmed")
                val leechEl = row.selectFirst("td.leechmed b")
                val dlEl    = row.selectFirst("a.tr-dl")

                results.add(SearchResult(
                    title      = titleEl.text().trim(),
                    source     = SearchSource.RUTRACKER,
                    category   = detectCategory(titleEl.text(), catEl?.text() ?: "", category),
                    sizeBytes  = parseSize(sizeEl?.text()?.trim() ?: ""),
                    seeders    = seedsEl?.text()?.trim()?.toIntOrNull() ?: 0,
                    leechers   = leechEl?.text()?.trim()?.toIntOrNull() ?: 0,
                    magnetUri  = null,
                    torrentUrl = dlEl?.let { "$BASE/${it.attr("href")}" },
                    detailUrl  = "$BASE/viewtopic.php?t=$topicId",
                ))
            } catch (_: Exception) {}
        }
        return results
    }

    private fun detectCategory(title: String, cat: String, requested: ContentCategory): ContentCategory {
        if (requested != ContentCategory.ALL) return requested
        val text = (title + " " + cat).lowercase()
        return when {
            text.contains(Regex("кино|фильм|сериал|movie|film|series|hdtv|bdrip|mkv|avi|mp4|blu-ray|bluray")) -> ContentCategory.VIDEO
            text.contains(Regex("музык|music|mp3|flac|альбом|album|lossless|hi-res|soundtrack|rock|pop|jazz")) -> ContentCategory.MUSIC
            else -> ContentCategory.ALL
        }
    }

    private fun parseSize(text: String): Long {
        val lower = text.lowercase().replace("↓", "").trim()
        val num = lower.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: return 0L
        return when {
            lower.contains("gb") || lower.contains("гб") -> (num * 1_073_741_824).toLong()
            lower.contains("mb") || lower.contains("мб") -> (num * 1_048_576).toLong()
            lower.contains("kb") || lower.contains("кб") -> (num * 1024).toLong()
            else -> 0L
        }
    }
}
