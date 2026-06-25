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
class RuTorProvider @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val mirrors = listOf(
        "https://rutor.info",
        "https://rutor.is"
    )

    suspend fun search(query: String, category: ContentCategory): List<SearchResult> =
        withContext(Dispatchers.IO) {
            for (mirror in mirrors) {
                try {
                    val results = searchOn(mirror, query, category)
                    if (results.isNotEmpty()) return@withContext results
                } catch (_: Exception) {}
            }
            emptyList()
        }

    private fun searchOn(base: String, query: String, category: ContentCategory): List<SearchResult> {
        val catCode = when (category) {
            ContentCategory.VIDEO -> "1"
            ContentCategory.MUSIC -> "4"
            ContentCategory.ALL   -> "0"
        }
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$base/search/0/$catCode/100/$encoded" // сортировка по сидам

        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
            .build()

        val html = client.newCall(req).execute().use { it.body?.string() ?: "" }

        // Проверяем что это не страница-заглушка
        if (html.contains("Вечная блокировка") || html.contains("Новый Адрес")) return emptyList()

        val doc = Jsoup.parse(html)
        val tbl = doc.selectFirst("table#index") ?: return emptyList()
        val results = mutableListOf<SearchResult>()

        tbl.select("tr").drop(1).take(50).forEach { row ->
            try {
                val cells = row.select("td")
                if (cells.size < 5) return@forEach

                val titleCell  = cells[1]
                val titleEl    = titleCell.select("a").lastOrNull() ?: return@forEach
                val title      = titleEl.text().trim()
                val detailHref = titleEl.attr("href")
                val magnet     = titleCell.selectFirst("a[href^=magnet:]")?.attr("href")

                val sizeText = cells.getOrNull(3)?.text() ?: ""
                val seeders  = cells.getOrNull(4)?.selectFirst("span.green")?.text()?.trim()?.toIntOrNull()
                    ?: cells.getOrNull(4)?.selectFirst("span")?.text()?.trim()?.toIntOrNull() ?: 0
                val leechers = cells.getOrNull(4)?.select("span")?.lastOrNull()?.text()?.trim()?.toIntOrNull() ?: 0
                val date     = cells.getOrNull(0)?.text() ?: ""

                results.add(SearchResult(
                    title      = title,
                    source     = SearchSource.RUTOR,
                    category   = CategoryDetector.detect(title, "", category),
                    sizeBytes  = parseSize(sizeText),
                    seeders    = seeders,
                    leechers   = leechers,
                    magnetUri  = magnet,
                    torrentUrl = null,
                    detailUrl  = "$base$detailHref",
                    uploadDate = date
                ))
            } catch (_: Exception) {}
        }
        return results
    }

    private fun parseSize(text: String): Long {
        val lower = text.lowercase().trim()
        val num = lower.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: return 0L
        return when {
            lower.contains("gb") || lower.contains("гб") -> (num * 1_073_741_824).toLong()
            lower.contains("mb") || lower.contains("мб") -> (num * 1_048_576).toLong()
            lower.contains("kb") || lower.contains("кб") -> (num * 1024).toLong()
            else -> 0L
        }
    }
}
