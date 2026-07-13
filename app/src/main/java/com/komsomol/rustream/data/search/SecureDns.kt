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

}
