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

    // RuTor зеркала — работают только с российских IP
    private val mirrors = listOf(
        "https://rutor.info",
        "https://rutor.is"
    )

    private val TAG = "RuTor"

    suspend fun search(query: String, category: ContentCategory): List<SearchResult> =
        withContext(Dispatchers.IO) {
            for (mirror in mirrors) {
                try {
                    val results = searchOn(mirror, query, category)
                    if (results.isNotEmpty()) return@withContext results
                } catch (e: Exception) {
                    Log.d(TAG, "$mirror failed: ${e.message}")
                }
            }
            emptyList()
        }

    private fun searchOn(base: String, query: String, category: ContentCategory): List<SearchResult> {
        // Категория: 0=все, 1=фильмы, 2=сериалы, 3=игры, 4=музыка, 5=аниме, 6=ТВ, 7=документальные
        val catCode = when (category) {
            ContentCategory.VIDEO -> "1"
            ContentCategory.MUSIC -> "4"
            ContentCategory.ALL   -> "0"
        }
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        // Сортировка по сидам: /100/ в URL
        val url = "$base/search/0/$catCode/100/$encoded"

        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
            .build()

        val html = client.newCall(req).execute().use { it.body?.string() ?: "" }

        // Проверяем — это заглушка блокировки или реальные результаты
        if (html.contains("Вечная блокировка") || html.contains("Новый Адрес") || html.length < 15000) {
            Log.d(TAG, "$base: blocked page (len=${html.length})")
            return emptyList()
        }

        val doc = Jsoup.parse(html)
        val results = mutableListOf<SearchResult>()

        // Строки результатов: tr.gai
        doc.select("tr.gai").take(50).forEach { row ->
            try {
                val tds = row.select("td")
                if (tds.size < 4) return@forEach

                // td[1] содержит: ссылку DL, magnet, и title
                val td1 = tds[1]
                val titleEl = td1.selectFirst("a[href*='rutor.info/torrent'], a[href*='rutor.is/torrent']")
                    ?: td1.select("a").lastOrNull { it.text().length > 5 }
                    ?: return@forEach

                val title   = titleEl.text().trim()
                val detailHref = titleEl.attr("href")

                val magnet  = td1.selectFirst("a[href^='magnet:']")?.attr("href")
                val dlEl    = td1.selectFirst("a.downgif, a[href*='d.rutor']")
                val size    = if (tds.size > 3) tds[3].text().trim() else ""
                val date    = tds[0].text().trim()

                // td[4]: "6  0" — первое число сиды, второе личи
                val seedsLeech = if (tds.size > 4) tds[4].text().trim() else ""
                val parts = seedsLeech.split(Regex("\s+")).filter { it.isNotBlank() }
                val seeds  = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val leech  = parts.getOrNull(1)?.toIntOrNull() ?: 0

                results.add(SearchResult(
                    title      = title,
                    source     = SearchSource.RUTOR,
                    category   = CategoryDetector.detect(title, "", category),
                    sizeBytes  = parseSize(size),
                    seeders    = seeds,
                    leechers   = leech,
                    magnetUri  = magnet,
                    torrentUrl = dlEl?.attr("href"),
                    detailUrl  = detailHref,
                    uploadDate = date
                ))
            } catch (_: Exception) {}
        }

        Log.d(TAG, "$base: ${results.size} results")
        return results
    }

    private fun parseSize(text: String): Long {
        val lower = text.lowercase().replace(",", ".").trim()
        val num = Regex("([0-9.]+)").find(lower)?.value?.toDoubleOrNull() ?: return 0L
        return when {
            lower.contains("gb") || lower.contains("гб") -> (num * 1_073_741_824).toLong()
            lower.contains("mb") || lower.contains("мб") -> (num * 1_048_576).toLong()
            lower.contains("kb") || lower.contains("кб") -> (num * 1024).toLong()
            else -> 0L
        }
    }
}
