package app.muro.vpn

/** Una consulta UDP sacada de la interfaz virtual. */
class UdpDatagram(
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val payload: ByteArray,
)

/**
 * Lee y arma paquetes IPv4/UDP a mano. Por la interfaz solo entra lo enrutado a la
 * IP falsa del DNS, así que basta con este caso y no hace falta una pila TCP/IP.
 */
object Ipv4Udp {
    private const val IPV4_HEADER = 20
    private const val UDP_HEADER = 8
    private const val PROTOCOL_UDP = 17
    private const val DNS_PORT = 53

    const val REPLY_OVERHEAD = IPV4_HEADER + UDP_HEADER

    /** Devuelve la consulta si [packet] es UDP hacia el puerto 53 de [dnsIp]; si no, null. */
    fun parseDnsQuery(packet: ByteArray, length: Int, dnsIp: ByteArray): UdpDatagram? {
        if (length < REPLY_OVERHEAD) return null
        if (packet[0].toInt() ushr 4 and 0xF != 4) return null

        val headerLength = (packet[0].toInt() and 0xF) * 4
        if (headerLength < IPV4_HEADER) return null
        val totalLength = u16(packet, 2)
        if (totalLength > length || totalLength < headerLength + UDP_HEADER) return null

        // Las consultas fragmentadas no se reensamblan: el resolvedor las reintenta.
        val moreFragmentsOrOffset = u16(packet, 6) and 0x3FFF
        if (moreFragmentsOrOffset != 0) return null
        if (packet[9].toInt() and 0xFF != PROTOCOL_UDP) return null

        val dstIp = packet.copyOfRange(16, 20)
        if (!dstIp.contentEquals(dnsIp)) return null
        if (u16(packet, headerLength + 2) != DNS_PORT) return null

        val udpLength = u16(packet, headerLength + 4)
        if (udpLength < UDP_HEADER || headerLength + udpLength > totalLength) return null

        return UdpDatagram(
            srcIp = packet.copyOfRange(12, 16),
            dstIp = dstIp,
            srcPort = u16(packet, headerLength),
            dstPort = DNS_PORT,
            payload = packet.copyOfRange(headerLength + UDP_HEADER, headerLength + udpLength),
        )
    }

    /** Arma la respuesta a [query] con los puertos e IP invertidos. */
    fun buildReply(query: UdpDatagram, dns: ByteArray): ByteArray {
        val udpLength = UDP_HEADER + dns.size
        val totalLength = IPV4_HEADER + udpLength
        val out = ByteArray(totalLength)

        out[0] = 0x45
        putU16(out, 2, totalLength)
        putU16(out, 6, 0x4000) // No fragmentar
        out[8] = 64 // TTL
        out[9] = PROTOCOL_UDP.toByte()
        query.dstIp.copyInto(out, 12)
        query.srcIp.copyInto(out, 16)
        putU16(out, 10, checksum(out, 0, IPV4_HEADER))

        putU16(out, IPV4_HEADER, query.dstPort)
        putU16(out, IPV4_HEADER + 2, query.srcPort)
        putU16(out, IPV4_HEADER + 4, udpLength)
        dns.copyInto(out, REPLY_OVERHEAD)
        putU16(out, IPV4_HEADER + 6, udpChecksum(out, udpLength))
        return out
    }

    private fun udpChecksum(packet: ByteArray, udpLength: Int): Int {
        // El checksum UDP cubre también una "pseudo cabecera" con las IP y el protocolo.
        val pseudo = ByteArray(12 + udpLength)
        packet.copyInto(pseudo, 0, 12, 20)
        pseudo[9] = PROTOCOL_UDP.toByte()
        putU16(pseudo, 10, udpLength)
        packet.copyInto(pseudo, 12, IPV4_HEADER, IPV4_HEADER + udpLength)
        val sum = checksum(pseudo, 0, pseudo.size)
        // En UDP, 0 significa "sin checksum": un resultado 0 se transmite como 0xFFFF.
        return if (sum == 0) 0xFFFF else sum
    }

    internal fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += u16(data, i)
            i += 2
        }
        if (i < end) sum += (data[i].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.toInt().inv() and 0xFFFF
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun putU16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 8).toByte()
        data[offset + 1] = value.toByte()
    }
}
