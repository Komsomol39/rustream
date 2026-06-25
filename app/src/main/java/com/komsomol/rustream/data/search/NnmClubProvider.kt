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
class NnmClubProvider @Inject constructor(
    private val cookieStore: NnmCookieStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(cookieStore)
        .build()

    private val BASE = "https://nnmclub.to/forum"

    fun isLoggedIn(): Boolean = cookieStore.isLoggedIn()

    suspend fun search(query: String, category: ContentCategory): List<SearchResult> =
        withContext(Dispatchers.IO) {
            if (!cookieStore.isLoggedIn()) return@withContext emptyList()
            try { doSearch(query, category) } catch (_: Exception) { emptyList() }
        }

    private fun doSearch(query: String, category: ContentCategory): List<SearchResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder()
            .url("$BASE/tracker.php?nm=$enc")
            .header("User-Agent", UA)
            .header("Referer", "$BASE/index.php")
            .build()

        val bytes = client.newCall(req).execute().use { it.body?.bytes() ?: ByteArray(0) }
        val html = bytes.toString(java.nio.charset.Charset.forName("windows-1251"))
        val doc = Jsoup.parse(html)
        val results = mutableListOf<SearchResult>()

        // NNM таблица похожа на RuTracker — tr с классами prow1/prow2
        doc.select("table.forumline tr.prow1, table.forumline tr.prow2").take(50).forEach { row ->
            try {
                val titleEl = row.selectFirst("a.topictitle, td.torTopic a") ?: return@forEach
                val href    = titleEl.attr("href") // viewtopic.php?t=XXXXX
                val topicId = href.substringAfter("t=").substringBefore("&")
                val catEl   = row.selectFirst("td.forumName a, td.forumname a")
                val sizeEl  = row.selectFirst("td.torSize, td:nth-child(6)")
                val seedsEl = row.selectFirst("span.seedmed, b.seedmed, td.seedmed")
                val leechEl = row.selectFirst("span.leechmed, b.leechmed, td.leechmed")
                val dlEl    = row.selectFirst("a[href*=download]")

                results.add(SearchResult(
                    title      = titleEl.text().trim(),
                    source     = SearchSource.NNM,
                    category   = CategoryDetector.detect(titleEl.text(), catEl?.text() ?: "", category),
                    sizeBytes  = parseSize(sizeEl?.text()?.trim() ?: ""),
                    seeders    = seedsEl?.text()?.trim()?.toIntOrNull() ?: 0,
                    leechers   = leechEl?.text()?.trim()?.toIntOrNull() ?: 0,
                    magnetUri  = null,
                    torrentUrl = dlEl?.let { "$BASE/${it.attr("href").trimStart('/')}" },
                    detailUrl  = if (topicId.isNotEmpty()) "$BASE/viewtopic.php?t=$topicId" else href,
                ))
            } catch (_: Exception) {}
        }
        return results
    }

    private fun parseSize(text: String): Long {
        val lower = text.lowercase().replace(",", ".").trim()
        val num = lower.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: return 0L
        return when {
            lower.contains("тб") || lower.contains("tb") -> (num * 1_099_511_627_776).toLong()
            lower.contains("гб") || lower.contains("gb") -> (num * 1_073_741_824).toLong()
            lower.contains("мб") || lower.contains("mb") -> (num * 1_048_576).toLong()
            lower.contains("кб") || lower.contains("kb") -> (num * 1024).toLong()
            else -> 0L
        }
    }

    companion object {
        private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
    }
}
