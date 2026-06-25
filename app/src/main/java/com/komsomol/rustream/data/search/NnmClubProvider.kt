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
    private val TAG  = "NnmClubProvider"

    fun isLoggedIn(): Boolean = cookieStore.isLoggedIn()

    suspend fun search(query: String, category: ContentCategory): List<SearchResult> =
        withContext(Dispatchers.IO) {
            if (!cookieStore.isLoggedIn()) {
                Log.d(TAG, "Not logged in, skipping search")
                return@withContext emptyList()
            }
            try { doSearch(query, category) } catch (e: Exception) {
                Log.e(TAG, "Search error", e)
                emptyList()
            }
        }

    private fun doSearch(query: String, category: ContentCategory): List<SearchResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder()
            .url("$BASE/tracker.php?nm=$enc")
            .header("User-Agent", UA)
            .header("Referer", "$BASE/index.php")
            .build()

        val bytes = client.newCall(req).execute().use { it.body?.bytes() ?: ByteArray(0) }
        val html = String(bytes, Charset.forName("windows-1251"))

        Log.d(TAG, "Search HTML length: ${html.length}, logged=${html.contains("Выход")}")

        val doc = Jsoup.parse(html)
        val results = mutableListOf<SearchResult>()

        // NNM-Club: строки с классами prow1 и prow2
        val rows = doc.select("tr.prow1, tr.prow2")
        Log.d(TAG, "Found rows: ${rows.size}")

        rows.take(50).forEach { row ->
            try {
                // Ссылка на тему
                val titleEl = row.selectFirst("a.topictitle") ?: return@forEach
                val href    = titleEl.attr("href")
                val topicId = href.substringAfter("t=").substringBefore("&").substringBefore("#")

                // Категория форума
                val catEl   = row.selectFirst("a.forumtitle")

                // Размер — ищем ячейку с размером (обычно 6-я или 7-я)
                val tds     = row.select("td")
                val sizeEl  = tds.firstOrNull { td ->
                    val t = td.text().trim().lowercase()
                    (t.contains("гб") || t.contains("мб") || t.contains("кб") ||
                     t.contains("gb") || t.contains("mb") || t.contains("kb") ||
                     t.contains("тб") || t.contains("tb")) &&
                    t.any { it.isDigit() }
                }

                // Сиды и личи
                val seedsEl = row.selectFirst("span.seedmed, b.seedmed")
                val leechEl = row.selectFirst("span.leechmed, b.leechmed")

                // Ссылка на скачивание
                val dlEl    = row.selectFirst("a[href*='download.php']")
                    ?: row.selectFirst("a[href*='dl.php']")

                Log.d(TAG, "Row: title=${titleEl.text().take(40)} size=${sizeEl?.text()} seeds=${seedsEl?.text()} dl=${dlEl?.attr("href")}")

                results.add(SearchResult(
                    title      = titleEl.text().trim(),
                    source     = SearchSource.NNM,
                    category   = CategoryDetector.detect(titleEl.text(), catEl?.text() ?: "", category),
                    sizeBytes  = parseSize(sizeEl?.text()?.trim() ?: ""),
                    seeders    = seedsEl?.text()?.trim()?.toIntOrNull() ?: 0,
                    leechers   = leechEl?.text()?.trim()?.toIntOrNull() ?: 0,
                    magnetUri  = null,
                    torrentUrl = dlEl?.let {
                        val dlHref = it.attr("href")
                        if (dlHref.startsWith("http")) dlHref else "$BASE/$dlHref"
                    },
                    detailUrl  = if (topicId.isNotEmpty()) "$BASE/viewtopic.php?t=$topicId" else "$BASE/$href",
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Row parse error", e)
            }
        }

        Log.d(TAG, "Total results: ${results.size}")
        return results
    }

    private fun parseSize(text: String): Long {
        val lower = text.lowercase().replace(",", ".").trim()
        val num = Regex("([0-9.]+)").find(lower)?.value?.toDoubleOrNull() ?: return 0L
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
