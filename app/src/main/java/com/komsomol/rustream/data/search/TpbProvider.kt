package com.komsomol.rustream.data.search

import android.util.Log
import com.komsomol.rustream.domain.model.ContentCategory
import com.komsomol.rustream.domain.model.SearchResult
import com.komsomol.rustream.domain.model.SearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Pirate Bay через официальный JSON API apibay.org.
 *
 * Ответ — массив объектов вида:
 *   {"id":"...","name":"...","info_hash":"ABCDEF...","leechers":"3",
 *    "seeders":"12","num_files":"1","size":"1610612736","username":"...",
 *    "added":"1699999999","category":"207","imdb":"tt..."}
 * Категории: 100-199 аудио, 200-299 видео, 300-399 приложения, и т.д.
 * Пустой результат = массив с единственным элементом id="0", name="No results returned".
 *
 * Строим magnet из info_hash — .torrent-файлов у TPB нет, только magnet.
 */
@Singleton
class TpbProvider @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .cache(null)
        .build()

    private val mirrors = listOf("https://apibay.org")
    private val TAG = "TPB"

    suspend fun search(query: String, category: ContentCategory): List<SearchResult> =
        withContext(Dispatchers.IO) {
            for (base in mirrors) {
                try {
                    val enc = java.net.URLEncoder.encode(query, "UTF-8")
                    // cat=0 — искать во всех категориях, фильтруем сами
                    val url = "$base/q.php?q=$enc&cat=0"
                    val req = Request.Builder().url(url)
                        .header("User-Agent", UA)
                        .build()
                    val body = client.newCall(req).execute().use { it.body?.string() ?: "" }
                    if (body.isBlank()) continue

                    val arr = JSONArray(body)
                    if (arr.length() == 0) return@withContext emptyList()
                    // маркер "нет результатов"
                    if (arr.length() == 1 &&
                        arr.getJSONObject(0).optString("id") == "0") return@withContext emptyList()

                    val results = mutableListOf<SearchResult>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val hash = o.optString("info_hash").trim()
                        val name = o.optString("name").trim()
                        if (hash.isBlank() || hash.length != 40 || name.isBlank()) continue

                        val seeders  = o.optString("seeders").toIntOrNull() ?: 0
                        val leechers = o.optString("leechers").toIntOrNull() ?: 0
                        val sizeB    = o.optString("size").toLongOrNull() ?: 0L
                        val catCode  = o.optString("category").toIntOrNull() ?: 0
                        val added    = o.optString("added").toLongOrNull() ?: 0L

                        val cat = when (catCode) {
                            in 100..199 -> ContentCategory.MUSIC
                            in 200..299 -> ContentCategory.VIDEO
                            else        -> CategoryDetector.detect(name, "", category)
                        }

                        results.add(SearchResult(
                            title      = name,
                            source     = SearchSource.TPB,
                            category   = cat,
                            sizeBytes  = sizeB,
                            seeders    = seeders,
                            leechers   = leechers,
                            magnetUri  = buildMagnet(hash, name),
                            torrentUrl = null,           // у TPB только magnet
                            detailUrl  = "https://thepiratebay.org/description.php?id=" +
                                          o.optString("id"),
                            uploadDate = if (added > 0)
                                java.text.SimpleDateFormat("yyyy", java.util.Locale.US)
                                    .format(java.util.Date(added * 1000)) else ""
                        ))
                    }
                    Log.d(TAG, "$base: ${results.size} results")
                    return@withContext results.sortedByDescending { it.seeders }
                } catch (e: Exception) {
                    Log.e(TAG, "$base: ${e.message}")
                }
            }
            emptyList()
        }

    private fun buildMagnet(hash: String, name: String): String {
        val enc = java.net.URLEncoder.encode(name, "UTF-8")
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

    companion object {
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
