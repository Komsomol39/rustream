package com.komsomol.rustream.data.search

import com.komsomol.rustream.domain.model.ContentCategory
import com.komsomol.rustream.domain.model.SearchResult
import com.komsomol.rustream.domain.model.SearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuTrackerProvider @Inject constructor(
    private val cookieStore: RuTrackerCookieStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(cookieStore)
        .build()

    private val BASE = "https://rutracker.net/forum"

    fun isLoggedIn(): Boolean = cookieStore.isLoggedIn()

    suspend fun search(query: String, category: ContentCategory): List<SearchResult> =
        withContext(Dispatchers.IO) {
            if (!cookieStore.isLoggedIn()) return@withContext emptyList()
            doSearch(query, category)
        }

    private fun doSearch(query: String, category: ContentCategory): List<SearchResult> {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("$BASE/tracker.php?nm=$encoded")
                .header("User-Agent", UA)
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
            results
        } catch (_: Exception) {
            emptyList()
        }
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

    companion object {
        private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
    }
}
