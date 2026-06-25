package com.komsomol.rustream.data.search

import android.util.Log
import com.komsomol.rustream.domain.model.ContentCategory
import com.komsomol.rustream.domain.model.SearchResult
import com.komsomol.rustream.domain.model.SearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.nio.charset.Charset
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
    private val TAG  = "NnmClub"

    fun isLoggedIn(): Boolean = cookieStore.isLoggedIn()

    suspend fun search(query: String, category: ContentCategory): List<SearchResult> =
        withContext(Dispatchers.IO) {
            if (!cookieStore.isLoggedIn()) return@withContext emptyList()
            try { doSearch(query, category) } catch (e: Exception) {
                Log.e(TAG, "Search failed: ${e.message}")
                cookieStore.saveDebugHtml("ERROR: ${e.message}")
                emptyList()
            }
        }

    private fun doSearch(query: String, category: ContentCategory): List<SearchResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder()
            .url("$BASE/tracker.php?nm=$enc")
            .header("User-Agent", UA)
            .header("Referer", "$BASE/index.php")
            .header("Accept-Language", "ru-RU,ru;q=0.9")
            .build()

        val bytes = client.newCall(req).execute().use { it.body?.bytes() ?: ByteArray(0) }
        val html  = String(bytes, Charset.forName("windows-1251"))

        val isLoggedIn = html.contains("Выход") || html.contains("logout")
        val doc = Jsoup.parse(html)

        // Собираем диагностику
        val trClasses = doc.select("tr").map { it.className() }.filter { it.isNotBlank() }.distinct().take(10)
        val tables    = doc.select("table").map { "id=${it.id()} cls=${it.className()}" }.take(6)
        val prows     = doc.select("tr.prow1, tr.prow2").size
        val topicLinks = doc.select("a.topictitle").size

        val debug = buildString {
            appendLine("loggedIn=$isLoggedIn htmlLen=${html.length}")
            appendLine("TR classes: $trClasses")
            appendLine("Tables: $tables")
            appendLine("prow1/2 rows: $prows")
            appendLine("topictitle links: $topicLinks")
            appendLine("---HTML start---")
            append(html.take(800))
        }
        Log.d(TAG, debug)
        cookieStore.saveDebugHtml(debug)

        val results = mutableListOf<SearchResult>()
        doc.select("tr.prow1, tr.prow2").take(50).forEach { row ->
            try {
                val titleEl = row.selectFirst("a.topictitle") ?: return@forEach
                val href    = titleEl.attr("href")
                val topicId = href.substringAfter("t=").substringBefore("&").substringBefore("#")
                val catEl   = row.selectFirst("a.forumtitle")
                val tds     = row.select("td")
                val sizeEl  = tds.firstOrNull { td ->
                    val t = td.text().trim().lowercase()
                    (t.contains("гб")||t.contains("мб")||t.contains("кб")||
                     t.contains("gb")||t.contains("mb")||t.contains("kb")||
                     t.contains("тб")||t.contains("tb")) && t.any { it.isDigit() }
                }
                val seedsEl = row.selectFirst("span.seedmed, b.seedmed")
                val leechEl = row.selectFirst("span.leechmed, b.leechmed")
                val dlEl    = row.selectFirst("a[href*='download.php']")

                results.add(SearchResult(
                    title      = titleEl.text().trim(),
                    source     = SearchSource.NNM,
                    category   = CategoryDetector.detect(titleEl.text(), catEl?.text() ?: "", category),
                    sizeBytes  = parseSize(sizeEl?.text()?.trim() ?: ""),
                    seeders    = seedsEl?.text()?.trim()?.toIntOrNull() ?: 0,
                    leechers   = leechEl?.text()?.trim()?.toIntOrNull() ?: 0,
                    magnetUri  = null,
                    torrentUrl = dlEl?.let { h ->
                        val href2 = h.attr("href")
                        if (href2.startsWith("http")) href2 else "$BASE/$href2"
                    },
                    detailUrl  = if (topicId.isNotEmpty()) "$BASE/viewtopic.php?t=$topicId" else "$BASE/$href",
                ))
            } catch (_: Exception) {}
        }
        Log.d(TAG, "Results: ${results.size}")
        return results
    }

    private fun parseSize(text: String): Long {
        val lower = text.lowercase().replace(",", ".").trim()
        val num = Regex("([0-9.]+)").find(lower)?.value?.toDoubleOrNull() ?: return 0L
        return when {
            lower.contains("тб")||lower.contains("tb") -> (num * 1_099_511_627_776).toLong()
            lower.contains("гб")||lower.contains("gb") -> (num * 1_073_741_824).toLong()
            lower.contains("мб")||lower.contains("mb") -> (num * 1_048_576).toLong()
            lower.contains("кб")||lower.contains("kb") -> (num * 1024).toLong()
            else -> 0L
        }
    }

    companion object {
        private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
    }
}
