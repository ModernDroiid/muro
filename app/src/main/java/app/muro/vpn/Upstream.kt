package app.muro.vpn

import android.util.Log
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Reenvía consultas a AdGuard cifradas (DNS sobre HTTPS) y usa el DNS de respaldo si
 * falla. Muro no está en la lista de apps de la VPN, así que este tráfico sale directo
 * a la red real y no vuelve a entrar al túnel.
 */
class Upstream(
    private val filteredUrl: String = ADGUARD_FILTERED,
    private val unfilteredUrl: String = ADGUARD_UNFILTERED,
) : Resolver {
    @Volatile
    var backupDns: String = "1.1.1.1"

    private val client = OkHttpClient.Builder()
        .dns(BootstrapDns)
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    override fun resolve(query: ByteArray, filtered: Boolean): ByteArray? {
        val url = if (filtered) filteredUrl else unfilteredUrl
        return try {
            overHttps(url, query)
        } catch (e: IOException) {
            Log.w(TAG, "DoH falló, usando el DNS de respaldo: ${e.message}")
            try {
                overUdp(backupDns, query)
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
            return response.body?.bytes()?.takeIf { it.size >= DNS_HEADER }
                ?: throw IOException("Respuesta vacía")
        }
    }

    private fun overUdp(server: String, query: ByteArray): ByteArray {
        DatagramSocket().use { socket ->
            socket.soTimeout = 2_500
            socket.send(DatagramPacket(query, query.size, InetAddress.getByName(server), 53))
            val buffer = ByteArray(Tunnel.MAX_DNS_PAYLOAD)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            if (packet.length < DNS_HEADER || buffer[0] != query[0] || buffer[1] != query[1]) {
                throw IOException("Respuesta inválida")
            }
            return buffer.copyOf(packet.length)
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
        const val DNS_HEADER = 12
        val DNS_MESSAGE = "application/dns-message".toMediaType()
    }
}
