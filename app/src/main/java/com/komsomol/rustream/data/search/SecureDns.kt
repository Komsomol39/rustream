package com.komsomol.rustream.data.search

import android.util.Log
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/**
 * DNS-over-HTTPS.
 *
 * Некоторые провайдеры перехватывают DNS: на запрос адреса торрент-сайта
 * возвращают IP клона, который отдаёт правильные метаданные, но подменённый
 * infohash (за ним — раздача с одной рекламой). Браузеры это обходят
 * собственным шифрованным DNS, а обычный HTTP-клиент — нет.
 *
 * Резолвим имена через Cloudflare по HTTPS, в обход системного DNS.
 * Если DoH недоступен — тихо откатываемся на системный резолвер,
 * чтобы не остаться совсем без сети.
 */
object SecureDns {

    private const val TAG = "SecureDns"

    @Volatile var lastStatus: String = "не инициализирован"
        private set

    /** Диагностика: что резолвится для host через систему и через DoH */
    fun diagnose(host: String): String = buildString {
        val sys = try {
            java.net.InetAddress.getAllByName(host).joinToString(",") { it.hostAddress ?: "?" }
        } catch (e: Exception) { "ошибка: ${e.message}" }
        append("Системный DNS для $host:\n$sys\n\n")
        val doh = try {
            resolver.lookup(host).joinToString(",") { it.hostAddress ?: "?" }
        } catch (e: Exception) { "ошибка: ${e.message}" }
        append("DoH (Cloudflare) для $host:\n$doh\n\nСтатус DoH: $lastStatus")
    }

    val resolver: Dns by lazy {
        try {
            val bootstrap = OkHttpClient.Builder().build()
            val doh = DnsOverHttps.Builder()
                .client(bootstrap)
                .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                // адреса резолвера прописаны явно: иначе пришлось бы
                // резолвить сам cloudflare-dns.com через системный DNS
                .bootstrapDnsHosts(
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("1.0.0.1"),
                    InetAddress.getByName("2606:4700:4700::1111")
                )
                .includeIPv6(false)
                .build()

            object : Dns {
                override fun lookup(hostname: String): List<InetAddress> = try {
                    val r = doh.lookup(hostname)
                    lastStatus = "OK через Cloudflare"
                    r
                } catch (e: Exception) {
                    lastStatus = "ОТКАТ на системный DNS: ${e.message}"
                    Log.w(TAG, "DoH failed for $hostname, falling back: ${e.message}")
                    Dns.SYSTEM.lookup(hostname)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "DoH init failed, using system DNS: ${e.message}")
            Dns.SYSTEM
        }
    }
    /** Диагностика: реальный запрос к обоим API-доменам, показывает хэш 2160p The Sting */
    suspend fun diagnoseApi(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val hosts = listOf("movies-api.accel.li", "yts.bz")
        val sb = StringBuilder()
        val cli = OkHttpClient.Builder()
            .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        for (h in hosts) {
            sb.append(h).append(":\n")
            try {
                val url = "https://$h/api/v2/list_movies.json?query_term=the%20sting&limit=5"
                val req = okhttp3.Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .build()
                val body = cli.newCall(req).execute().use { it.body?.string() ?: "" }
                val movies = org.json.JSONObject(body).optJSONObject("data")?.optJSONArray("movies")
                var found = "?"
                if (movies != null) for (i in 0 until movies.length()) {
                    val m = movies.getJSONObject(i)
                    if (m.optInt("year") == 1973 && m.optString("title").contains("Sting")) {
                        val ts = m.optJSONArray("torrents")
                        if (ts != null) for (j in 0 until ts.length()) {
                            val t = ts.getJSONObject(j)
                            if (t.optString("quality") == "2160p") found = t.optString("hash").uppercase()
                        }
                    }
                }
                val ok = if (found == "092830915ADEA71C92FA58DF2E8EB39EA3CF3449") "✓ НАСТОЯЩИЙ"
                         else "✗ ФЕЙК/перехват"
                sb.append("  hash=").append(found.take(16)).append("... ").append(ok).append("\n")
            } catch (e: Exception) {
                sb.append("  ошибка: ").append(e.message).append("\n")
            }
        }
        sb.toString()
    }

}
