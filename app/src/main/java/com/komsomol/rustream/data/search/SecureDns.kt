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


    /** Диагностика: сохраняет сырой ответ yts.bz для Sting в файл на устройстве */
    suspend fun dumpYtsRaw(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val out = StringBuilder()
        val cli = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .cache(null)
            .build()
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                 "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        for (host in listOf("yts.bz", "movies-api.accel.li")) {
            out.append("========== ").append(host).append(" ==========\n")
            try {
                val url = "https://$host/api/v2/list_movies.json?query_term=sting&limit=50&sort_by=seeds"
                val req = okhttp3.Request.Builder().url(url)
                    .header("User-Agent", ua)
                    .cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
                    .build()
                cli.newCall(req).execute().use { resp ->
                    out.append("HTTP ").append(resp.code).append("\n")
                    out.append("server=").append(resp.header("server") ?: "?").append("\n")
                    out.append("cf-ray=").append(resp.header("cf-ray") ?: "нет").append("\n")
                    out.append("via=").append(resp.header("via") ?: "нет").append("\n")
                    out.append("resolved IP=").append(
                        try { java.net.InetAddress.getByName(host).hostAddress } catch (e: Exception) { e.message }
                    ).append("\n")
                    val body = resp.body?.string() ?: ""
                    out.append("--- тело (").append(body.length).append(" симв) ---\n")
                    out.append(body).append("\n\n")
                }
            } catch (e: Exception) {
                out.append("ОШИБКА: ").append(e.message).append("\n\n")
            }
        }
        // пишем в файл
        try {
            val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS), "RuStream")
            dir.mkdirs()
            val f = java.io.File(dir, "yts-raw.txt")
            f.writeText(out.toString())
            "Сохранено: " + f.absolutePath + "\n(" + out.length + " символов)"
        } catch (e: Exception) {
            "Не удалось сохранить: " + e.message
        }
    }

}
