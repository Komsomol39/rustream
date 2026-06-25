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
                Log.e(TAG, "Failed: ${e.message}")
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
        val doc   = Jsoup.parse(html)

        // Строки результатов — ищем по download.php ссылкам
        // Структура TR: td[0]=checkbox, td[1]=cat, td[2]=title(a.topictitle), 
        //               td[3]=author, td[4]=DL, td[5]=size, td[6]=seeds, td[7]=leech
        val results = mutableListOf<SearchResult>()

        doc.select("a[href*='download.php']").forEach { dlEl ->
            try {
                val row = dlEl.findParentRow() ?: return@forEach
                val tds = row.select("td")
                if (tds.size < 7) return@forEach

                val titleEl = tds[2].selectFirst("a.topictitle, a.genmed") ?: return@forEach
                val title   = titleEl.text().trim()
                if (title.isBlank()) return@forEach

                val href    = titleEl.attr("href")
                val topicId = href.substringAfter("t=").substringBefore("&").substringBefore("#")
                val cat     = tds[1].text().trim()

                // Размер: td[5] содержит "BYTES READABLE" например "1042668893 994 MB"
                // Берём число после пробела с единицей
                val sizeText = tds[5].text().trim()
                val sizeBytes = parseSizeFromRaw(sizeText)

                val seeds  = tds[6].text().trim().toIntOrNull() ?: 0
                val leeches = if (tds.size > 7) tds[7].text().trim().toIntOrNull() ?: 0 else 0

                val dlHref = dlEl.attr("href")
                val torrentUrl = if (dlHref.startsWith("http")) dlHref else "$BASE/$dlHref"

                results.add(SearchResult(
                    title      = title,
                    source     = SearchSource.NNM,
                    category   = CategoryDetector.detect(title, cat, category),
                    sizeBytes  = sizeBytes,
                    seeders    = seeds,
                    leechers   = leeches,
                    magnetUri  = null,
                    torrentUrl = torrentUrl,
                    detailUrl  = if (topicId.isNotEmpty()) "$BASE/viewtopic.php?t=$topicId" else href,
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Row parse error: ${e.message}")
            }
        }

        Log.d(TAG, "NNM results: ${results.size}")
        cookieStore.saveDebugHtml("OK: ${results.size} results for last query")
        return results.take(50)
    }

    // Находим ближайший TR-родитель
    private fun org.jsoup.nodes.Element.findParentRow(): org.jsoup.nodes.Element? =
        parents().firstOrNull { it.tagName() == "tr" }

    // Размер в td[5]: "1042668893 994 MB" или "1866283991 1.74 GB"
    // Первое число — байты напрямую!
    private fun parseSizeFromRaw(text: String): Long {
        val parts = text.trim().split(" ")
        // Первый токен — байты
        val bytes = parts.firstOrNull()?.toLongOrNull()
        if (bytes != null && bytes > 0) return bytes
        // Fallback — парсим читаемый формат
        return parseSize(text)
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
