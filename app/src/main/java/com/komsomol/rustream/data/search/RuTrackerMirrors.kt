package com.komsomol.rustream.data.search

/**
 * Зеркала RuTracker в порядке приоритета.
 * При SSL/сетевой ошибке автоматически пробуем следующее.
 */
object RuTrackerMirrors {

    val ALL = listOf(
        "https://rutracker.org",
        "https://rutracker.net",
        "https://rutracker.nl",
        "https://rutracker.cc",
        "https://rutracker.ru",
    )

    /** Быстро проверяем доступность зеркала (HEAD запрос, таймаут 5с) */
    fun findWorking(): String? {
        for (mirror in ALL) {
            try {
                val url = java.net.URL("$mirror/forum/index.php")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..399) return mirror
            } catch (_: Exception) {}
        }
        return null
    }
}
