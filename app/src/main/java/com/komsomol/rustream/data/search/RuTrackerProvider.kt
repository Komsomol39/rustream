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
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(cookieStore)
        .build()

    fun isLoggedIn(): Boolean = cookieStore.isLoggedIn()

    suspend fun search(query: String, category: ContentCategory): List<SearchResult> =
        withContext(Dispatchers.IO) {
            if (!cookieStore.isLoggedIn()) return@withContext emptyList()
            val mirrors = listOf(cookieStore.getActiveMirror()) +
                RuTrackerMirrors.ALL.filter { it != cookieStore.getActiveMirror() }
            for (mirror in mirrors) {
                try {
                    val results = doSearch(query, category, "$mirror/forum")
                    if (results.isNotEmpty()) return@withContext results
                } catch (_: Exception) {}
            }
            emptyList()
        }

    private fun doSearch(query: String, category: ContentCategory, base: String): List<SearchResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")

        // RuTracker категории: f=музыка=468,463,464 / видео=2,7,4,33,209
        // Используем поиск по разделам через параметр f[]
        val catParam = when (category) {
            ContentCategory.MUSIC -> "&f[]=468&f[]=463&f[]=464&f[]=466&f[]=467"
            ContentCategory.VIDEO -> "&f[]=2&f[]=7&f[]=4&f[]=33&f[]=209&f[]=312"
            ContentCategory.ALL   -> ""
        }

        val req = Request.Builder()
            .url("$base/tracker.php?nm=$encoded$catParam")
            .header("User-Agent", UA)
            .header("Referer", "$base/index.php")
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

                val detectedCat = CategoryDetector.detect(titleEl.text(), catEl?.text() ?: "", category)
                // При фильтрации по категории пропускаем несовпадающее
                if (category != ContentCategory.ALL && detectedCat != category) return@forEach

                results.add(SearchResult(
                    title      = titleEl.text().trim(),
                    source     = SearchSource.RUTRACKER,
                    category   = detectedCat,
                    sizeBytes  = parseSize(sizeEl?.text()?.trim() ?: ""),
                    seeders    = seedsEl?.text()?.trim()?.toIntOrNull() ?: 0,
                    leechers   = leechEl?.text()?.trim()?.toIntOrNull() ?: 0,
                    magnetUri  = null,
                    torrentUrl = dlEl?.let { "$base/${it.attr("href")}" },
                    detailUrl  = "$base/viewtopic.php?t=$topicId",
                ))
            } catch (_: Exception) {}
        }
        return results
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
