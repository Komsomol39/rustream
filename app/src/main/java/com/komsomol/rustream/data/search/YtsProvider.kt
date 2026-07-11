package com.komsomol.rustream.data.search

import android.util.Log
import com.komsomol.rustream.domain.model.ContentCategory
import com.komsomol.rustream.domain.model.SearchResult
import com.komsomol.rustream.domain.model.SearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YtsProvider @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Только оригинальный YTS. Клоны (yts.bz, yts.lt, yts.do, yts.rs и др.)
    // отдают через API фейковые хэши: за ними раздача с одной рекламой
    // (картинка + txt про прокси/TOR), без самого фильма.
    private val mirrors = listOf("https://yts.mx")
    private val TAG = "YTS"

    // YTS — только фильмы
    suspend fun search(query: String, category: ContentCategory): List<SearchResult> =
        withContext(Dispatchers.IO) {
            // YTS только видео
            if (category == ContentCategory.MUSIC) return@withContext emptyList()
            for (mirror in mirrors) {
                try {
                    val results = searchOn(mirror, query)
                    if (results.isNotEmpty()) return@withContext results
                } catch (e: Exception) {
                    Log.d(TAG, "$mirror: ${e.message}")
                }
            }
            emptyList()
        }

    private fun searchOn(base: String, query: String): List<SearchResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$base/api/v2/list_movies.json?query_term=$enc&limit=50&sort_by=seeds"

        val req = Request.Builder().url(url)
            .header("User-Agent", UA)
            .build()

        val body = client.newCall(req).execute().use { it.body?.string() ?: "" }
        val json  = JSONObject(body)

        if (json.optString("status") != "ok") return emptyList()

        val movies = json.optJSONObject("data")?.optJSONArray("movies") ?: return emptyList()
        val results = mutableListOf<SearchResult>()

        for (i in 0 until movies.length()) {
            val movie    = movies.getJSONObject(i)
            val title    = movie.optString("title_long", movie.optString("title"))
            val year     = movie.optInt("year")
            val rating   = movie.optDouble("rating", 0.0)
            val summary  = movie.optString("summary", "")
            val movieUrl = movie.optString("url", "")
            val torrents = movie.optJSONArray("torrents") ?: continue

            for (j in 0 until torrents.length()) {
                val torrent = torrents.getJSONObject(j)
                val quality = torrent.optString("quality") // 720p, 1080p, 2160p
                val type    = torrent.optString("type")    // bluray, web
                val hash    = torrent.optString("hash")
                val seeds   = torrent.optInt("seeds")
                val peers   = torrent.optInt("peers")
                val size    = torrent.optString("size")    // "1.62 GB"
                val sizeB   = torrent.optLong("size_bytes")

                if (hash.isBlank()) continue

                // Формируем magnet
                val magnet = buildMagnet(hash, "$title $quality")

                // Название с качеством
                val fullTitle = "$title ($year) [$quality ${type.uppercase()}]"

                results.add(SearchResult(
                    title      = fullTitle,
                    source     = SearchSource.YTS,
                    category   = ContentCategory.VIDEO,
                    sizeBytes  = if (sizeB > 0) sizeB else parseSize(size),
                    seeders    = seeds,
                    leechers   = peers,
                    magnetUri  = magnet,
                    // .torrent строим по хэшу на оригинальном сайте —
                    // url из ответа API у зеркал часто отдаёт 404
                    torrentUrl = "$base/torrent/download/$hash",
                    detailUrl  = movieUrl,
                    uploadDate = year.toString()
                ))
            }
        }

        Log.d(TAG, "$base: ${results.size} results")
        return results.sortedByDescending { it.seeders }
    }

    private fun buildMagnet(hash: String, name: String): String {
        val enc = java.net.URLEncoder.encode(name, "UTF-8")
        // Живые публичные трекеры: у YTS-хэшей пиры часто находятся только
        // с явными трекерами (dead-трекеры YTS давно не отвечают)
        val trackers = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.tracker.cl:1337/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker.openbittorrent.com:6969/announce"
        )
        val tr = trackers.joinToString("") { "&tr=" + java.net.URLEncoder.encode(it, "UTF-8") }
        return "magnet:?xt=urn:btih:$hash&dn=$enc$tr"
    }

    private fun parseSize(text: String): Long {
        val lower = text.lowercase().trim()
        val num = Regex("([0-9.]+)").find(lower)?.value?.toDoubleOrNull() ?: return 0L
        return when {
            lower.contains("gb") -> (num * 1_073_741_824).toLong()
            lower.contains("mb") -> (num * 1_048_576).toLong()
            lower.contains("kb") -> (num * 1024).toLong()
            else -> 0L
        }
    }

    companion object {
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }
}
