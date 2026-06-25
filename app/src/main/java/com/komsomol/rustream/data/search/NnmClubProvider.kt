package com.komsomol.rustream.data.search

import com.komsomol.rustream.domain.model.ContentCategory
import com.komsomol.rustream.domain.model.SearchResult
import com.komsomol.rustream.domain.model.SearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NnmClubProvider @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun search(query: String, category: ContentCategory): List<SearchResult> =
        withContext(Dispatchers.IO) {
            try { doSearch(query, category) } catch (_: Exception) { emptyList() }
        }

    private fun doSearch(query: String, category: ContentCategory): List<SearchResult> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder()
            .url("https://nnmclub.to/forum/rss.php?nm=$enc")
            .header("User-Agent", UA)
            .build()

        val bytes = client.newCall(req).execute().use { it.body?.bytes() ?: ByteArray(0) }
        if (bytes.isEmpty()) return emptyList()

        // RSS в windows-1251
        val xml = bytes.toString(Charsets.ISO_8859_1)
            .replace("encoding="windows-1251"", "encoding="ISO-8859-1"")

        return parseRss(xml, category)
    }

    private fun parseRss(xml: String, category: ContentCategory): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var inItem = false
            var title = ""; var link = ""; var sizeBytesVal = 0L; var pubDate = ""

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "item"      -> { inItem = true; title = ""; link = ""; sizeBytesVal = 0L; pubDate = "" }
                        "title"     -> if (inItem) title = parser.nextText()
                        "link"      -> if (inItem) link = parser.nextText()
                        "pubDate"   -> if (inItem) pubDate = parser.nextText()
                        "enclosure" -> if (inItem) sizeBytesVal = parser.getAttributeValue(null, "length")?.toLongOrNull() ?: 0L
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "item" && inItem && title.isNotBlank()) {
                        inItem = false
                        // Извлекаем topic id из ссылки: viewtopic.php?t=XXXXX
                        val topicId = link.substringAfter("t=").substringBefore("&")
                        results.add(SearchResult(
                            title      = title.trim(),
                            source     = SearchSource.NNM,
                            category   = CategoryDetector.detect(title, "", category),
                            sizeBytes  = sizeBytesVal,
                            seeders    = 0, // RSS не даёт сиды
                            leechers   = 0,
                            magnetUri  = null,
                            torrentUrl = if (topicId.isNotEmpty())
                                "https://nnmclub.to/forum/download.php?id=$topicId" else null,
                            detailUrl  = link,
                            uploadDate = pubDate.take(16)
                        ))
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {}
        return results.take(50)
    }

    companion object {
        private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
    }
}
