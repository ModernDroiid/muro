package app.muro.vpn

import android.util.Log
import app.muro.vpn.DnsCodec.isResponseTo
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Reenvía consultas a AdGuard cifradas (DNS sobre HTTPS). Si no responde usa el DNS de
 * respaldo, también cifrado: nunca se cae a DNS en texto plano, porque cualquiera en la
 * misma red podría forzar ese modo bloqueando AdGuard y leer qué dominios se consultan.
 *
 * Muro no está en la lista de apps de la VPN, así que este tráfico sale directo a la red
 * real y no vuelve a entrar al túnel.
 */
class Upstream(
    private val filteredUrl: String = ADGUARD_FILTERED,
    private val unfilteredUrl: String = ADGUARD_UNFILTERED,
    private val backupUrl: (String) -> String = ::backupDohUrl,
) : Resolver {
    @Volatile
    var backupDns: String = "1.1.1.1"

    // El resolvedor del sistema se rinde en unos 5 s: los dos intentos deben caber ahí.
    private val client = OkHttpClient.Builder()
        .dns(BootstrapDns)
        .connectTimeout(2_500, TimeUnit.MILLISECONDS)
        .callTimeout(3_500, TimeUnit.MILLISECONDS)
        .build()

    override fun resolve(query: ByteArray, filtered: Boolean): ByteArray? {
        val url = if (filtered) filteredUrl else unfilteredUrl
        return try {
            overHttps(url, query)
        } catch (e: IOException) {
            Log.w(TAG, "AdGuard falló, usando el DNS de respaldo: ${e.message}")
            try {
                overHttps(backupUrl(backupDns), query)
            } catch (e: IOException) {
                Log.w(TAG, "El DNS de respaldo también falló: ${e.message}")
                null
            }
        }
    }

    private fun overHttps(url: String, query: ByteArray): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("Accept", DNS_MESSAGE.toString())
            .post(query.toRequestBody(DNS_MESSAGE))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.bytes() ?: throw IOException("Respuesta vacía")
            if (!isResponseTo(query, body)) throw IOException("La respuesta no corresponde a la consulta")
            return body
        }
    }

    /** Si el DNS del sistema no resuelve el nombre de AdGuard, usa sus IP publicadas. */
    private object BootstrapDns : Dns {
        private val known = mapOf(
            "dns.adguard-dns.com" to listOf("94.140.14.14", "94.140.15.15"),
            "unfiltered.adguard-dns.com" to listOf("94.140.14.140", "94.140.14.141"),
        )

        override fun lookup(hostname: String): List<InetAddress> = try {
            Dns.SYSTEM.lookup(hostname)
        } catch (e: UnknownHostException) {
            known[hostname]?.map(InetAddress::getByName) ?: throw e
        }
    }

    private companion object {
        const val TAG = "MuroUpstream"
        const val ADGUARD_FILTERED = "https://dns.adguard-dns.com/dns-query"
        const val ADGUARD_UNFILTERED = "https://unfiltered.adguard-dns.com/dns-query"
        val DNS_MESSAGE = "application/dns-message".toMediaType()
    }
}

/** Dirección DNS sobre HTTPS de un servidor guardado como IP. */
internal fun backupDohUrl(server: String): String {
    val host = if (':' in server) "[$server]" else server
    return "https://$host/dns-query"
}
