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

    private val mirrors = listOf("https://rutor.info", "https://rutor.is")

    suspend fun search(query: String, category: ContentCategory): List<SearchResult> =
        withContext(Dispatchers.IO) {
            for (mirror in mirrors) {
                try { return@withContext searchOn(mirror, query, category) } catch (_: Exception) {}
            }
            emptyList()
        }

    private fun searchOn(base: String, query: String, category: ContentCategory): List<SearchResult> {
        // Категория RuTor: 0=все, 1=фильмы, 2=сериалы, 3=игры, 4=музыка, ...
        val catCode = when (category) {
            ContentCategory.VIDEO -> "1"
            ContentCategory.MUSIC -> "4"
            ContentCategory.ALL   -> "0"
        }
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$base/search/0/$catCode/0/$encoded"

        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
            .build()

        val html = client.newCall(req).execute().use { it.body?.string() ?: "" }
        val doc = Jsoup.parse(html)
        val results = mutableListOf<SearchResult>()

        doc.select("table#index tr").drop(1).take(50).forEach { row ->
            val cells = row.select("td")
            if (cells.size < 5) return@forEach

            val titleCell = cells[1]
            val title = titleCell.select("a").lastOrNull()?.text() ?: return@forEach
            val detailHref = titleCell.select("a").lastOrNull()?.attr("href") ?: return@forEach
            val magnetLink = titleCell.select("a[href^=magnet:]").attr("href").takeIf { it.isNotEmpty() }

            val sizeText = cells.getOrNull(3)?.text() ?: ""
            val seeders  = cells.getOrNull(4)?.select("span")?.firstOrNull()?.text()?.trim()?.toIntOrNull() ?: 0
            val leechers = cells.getOrNull(4)?.select("span")?.lastOrNull()?.text()?.trim()?.toIntOrNull() ?: 0
            val date     = cells.getOrNull(0)?.text() ?: ""

            val detectedCat = CategoryDetector.detect(title, "", category)

            results.add(SearchResult(
                title      = title,
                source     = SearchSource.RUTOR,
                category   = detectedCat,
                sizeBytes  = parseSize(sizeText),
                seeders    = seeders,
                leechers   = leechers,
                magnetUri  = magnetLink,
                torrentUrl = null,
                detailUrl  = "$base$detailHref",
                uploadDate = date
            ))
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
