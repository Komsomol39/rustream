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
class KinozalProvider @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val BASE = "https://kinozal.tv"

    suspend fun search(query: String, category: ContentCategory): List<SearchResult> =
        withContext(Dispatchers.IO) {
            try { doSearch(query, category) } catch (_: Exception) { emptyList() }
        }

    private fun doSearch(query: String, category: ContentCategory): List<SearchResult> {
        // Kinozal категории: c=0=все, c=1=фильмы, c=8=музыка
        val cat = when (category) {
            ContentCategory.VIDEO -> "1"
            ContentCategory.MUSIC -> "8"
            ContentCategory.ALL   -> "0"
        }
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder()
            .url("$BASE/browse.php?s=$enc&g=0&c=$cat&v=0&d=0&w=0&t=0&f=0")
            .header("User-Agent", UA)
            .build()

        val html = client.newCall(req).execute().use { it.body?.string() ?: "" }
        val doc = Jsoup.parse(html)
        val results = mutableListOf<SearchResult>()

        // Строки результатов: tr.first.bg и tr.bg в таблице t_peer
        // Пробуем разные селекторы — Kinozal иногда меняет классы
        val rows = doc.select("table.t_peer tr.first.bg, table.t_peer tr.bg")
            .ifEmpty { doc.select("tr.bg") }
            .ifEmpty { doc.select("table.t_peer tr").filter { it.select("td").size > 4 } }
        rows.take(50).forEach { row ->
            try {
                val titleA  = row.selectFirst("td.nam a, a[href*=details]") ?: return@forEach
                val sizeEl  = row.select("td.s, td[class*=size]").firstOrNull()
                val seedsEl = row.selectFirst("td.sl_s, td.seed, [class*=seed]")
                val leechEl = row.selectFirst("td.sl_p, td.leech, [class*=leech]")
                val href    = titleA.attr("href") // /details.php?id=XXXXX
                val id      = href.substringAfter("id=").substringBefore("&")

                results.add(SearchResult(
                    title      = titleA.text().trim(),
                    source     = SearchSource.KINOZAL,
                    category   = CategoryDetector.detect(titleA.text(), "", category),
                    sizeBytes  = parseSize(sizeEl?.text()?.trim() ?: ""),
                    seeders    = seedsEl?.text()?.trim()?.toIntOrNull() ?: 0,
                    leechers   = leechEl?.text()?.trim()?.toIntOrNull() ?: 0,
                    magnetUri  = null,
                    torrentUrl = if (id.isNotEmpty()) "$BASE/download.php?id=$id" else null,
                    detailUrl  = "$BASE$href",
                ))
            } catch (_: Exception) {}
        }
        return results
    }

    private fun parseSize(text: String): Long {
        val lower = text.lowercase().trim()
        val num = lower.replace(Regex("[^0-9.,]"), "").replace(",", ".").toDoubleOrNull() ?: return 0L
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
