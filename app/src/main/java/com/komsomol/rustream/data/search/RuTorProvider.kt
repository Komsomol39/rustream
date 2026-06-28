package com.komsomol.rustream.data.search

import android.util.Log
import com.komsomol.rustream.data.settings.SettingsRepository
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
class RuTorProvider @Inject constructor(
    private val settings: SettingsRepository
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)

        .build()

    private val mirrors = listOf("https://rutor.info", "https://rutor.is")
    private val TAG = "RuTor"

    suspend fun search(query: String, category: ContentCategory): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val debugLines = mutableListOf<String>()

            for (mirror in mirrors) {
                try {
                    val catCode = when (category) {
                        ContentCategory.VIDEO -> "1"
                        ContentCategory.MUSIC -> "4"
                        ContentCategory.ALL   -> "0"
                    }
                    val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                    val url = "$mirror/search/0/$catCode/100/$encoded"

                    debugLines += "URL: $url"

                    val resp = client.newCall(
                        Request.Builder().url(url)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                            .build()
                    ).execute()

                    val html = resp.use { it.body?.string() ?: "" }
                    val finalUrl = resp.request.url.toString()

                    debugLines += "status=${resp.code} len=${html.length}"
                    debugLines += "finalUrl=$finalUrl"
                    debugLines += "hasBlocked=${html.contains("Вечная блокировка")}"
                    debugLines += "hasNewAddr=${html.contains("Новый Адрес")}"

                    val doc = Jsoup.parse(html)
                    val trGai = doc.select("tr.gai").size
                    val magnets = doc.select("a[href^='magnet:']").size
                    val trClasses = doc.select("tr").map { it.className() }
                        .filter { it.isNotBlank() }.distinct().take(8)
                    val tables = doc.select("table").map { "id=${it.id()} cls=${it.className()}" }.take(5)

                    debugLines += "tr.gai=$trGai magnets=$magnets"
                    debugLines += "TR classes: $trClasses"
                    debugLines += "Tables: $tables"
                    debugLines += "HTML300: " + html.take(300).replace("\n", " ")

                    settings.setRutorDebug(debugLines.joinToString("\n"))

                    // Проверяем по длине — заглушка блокировки ~9895 байт
                    // НЕ проверяем по тексту — "Вечная блокировка" есть и на реальных страницах
                    if (html.length < 15000 && !html.contains("tr class=\"gai\"")) {
                        debugLines += "BLOCKED (short page, no results) - skipping"
                        continue
                    }

                    val results = mutableListOf<SearchResult>()
                    doc.select("tr.gai").take(50).forEach { row ->
                        try {
                            val tds = row.select("td")
                            if (tds.size < 4) return@forEach

                            val td1     = tds[1]
                            val titleEl = td1.selectFirst("a[href*='rutor.info/torrent'], a[href*='rutor.is/torrent']")
                                ?: td1.select("a").lastOrNull { it.text().length > 5 }
                                ?: return@forEach

                            val title      = titleEl.text().trim()
                            val detailHref = titleEl.attr("href")
                            val magnet     = td1.selectFirst("a[href^='magnet:']")?.attr("href")
                            val dlEl       = td1.selectFirst("a.downgif, a[href*='d.rutor']")
                            val size       = if (tds.size > 3) tds[3].text().trim() else ""
                            val date       = tds[0].text().trim()
                            val seedsLeech = if (tds.size > 4) tds[4].text().trim() else ""
                            val parts  = seedsLeech.trim().split(" ").filter { it.isNotBlank() }
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

                    debugLines += "RESULTS: ${results.size}"
                    settings.setRutorDebug(debugLines.joinToString("\n"))

                    if (results.isNotEmpty()) return@withContext results

                } catch (e: Exception) {
                    debugLines += "$mirror ERROR: ${e.message}"
                    settings.setRutorDebug(debugLines.joinToString("\n"))
                    Log.e(TAG, "$mirror: ${e.message}")
                }
            }
            emptyList()
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
