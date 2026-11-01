package app.muro.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Ipv4UdpTest {
    private val client = byteArrayOf(10, 111, 222.toByte(), 1)
    private val dns = Tunnel.DNS_BYTES

    private fun packet(
        payload: ByteArray = byteArrayOf(1, 2, 3, 4),
        dstPort: Int = 53,
        dst: ByteArray = dns,
        protocol: Int = 17,
        fragment: Int = 0,
    ): ByteArray {
        val udp = 8 + payload.size
        val total = 20 + udp
        val out = ByteArray(total)
        out[0] = 0x45
        out[2] = (total ushr 8).toByte()
        out[3] = total.toByte()
        out[6] = (fragment ushr 8).toByte()
        out[7] = fragment.toByte()
        out[9] = protocol.toByte()
        client.copyInto(out, 12)
        dst.copyInto(out, 16)
        out[20] = 0xC3.toByte() // Puerto de origen 50000
        out[21] = 0x50
        out[22] = (dstPort ushr 8).toByte()
        out[23] = dstPort.toByte()
        out[25] = udp.toByte()
        payload.copyInto(out, 28)
        return out
    }

    @Test
    fun `lee una consulta DNS valida`() {
        val raw = packet()
        val query = assertNotNull2(Ipv4Udp.parseDnsQuery(raw, raw.size, dns))
        assertEquals(50000, query.srcPort)
        assertEquals(53, query.dstPort)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), query.payload)
    }

    @Test
    fun `ignora lo que no es DNS hacia la IP falsa`() {
        listOf(
            packet(dstPort = 443),
            packet(protocol = 6),
            packet(dst = byteArrayOf(8, 8, 8, 8)),
            packet(fragment = 0x2000),
            packet(fragment = 0x0010),
        ).forEach { assertNull(Ipv4Udp.parseDnsQuery(it, it.size, dns)) }
    }

    @Test
    fun `ignora paquetes cortados`() {
        val raw = packet()
        assertNull(Ipv4Udp.parseDnsQuery(raw, raw.size - 1, dns))
        assertNull(Ipv4Udp.parseDnsQuery(raw, 10, dns))
        assertNull(Ipv4Udp.parseDnsQuery(ByteArray(40) { 0x60 }, 40, dns))
    }

    @Test
    fun `la respuesta invierte direcciones y puertos`() {
        val raw = packet()
        val query = Ipv4Udp.parseDnsQuery(raw, raw.size, dns)!!
        val reply = Ipv4Udp.buildReply(query, byteArrayOf(9, 8, 7))

        assertArrayEquals(dns, reply.copyOfRange(12, 16))
        assertArrayEquals(client, reply.copyOfRange(16, 20))
        assertEquals(53, u16(reply, 20))
        assertEquals(50000, u16(reply, 22))
        assertEquals(reply.size, u16(reply, 2))
        assertArrayEquals(byteArrayOf(9, 8, 7), reply.copyOfRange(28, reply.size))
    }

    @Test
    fun `los checksums de la respuesta son validos`() {
        val raw = packet()
        val query = Ipv4Udp.parseDnsQuery(raw, raw.size, dns)!!

        // Tamaño impar a propósito: es el caso con relleno en el checksum.
        val reply = Ipv4Udp.buildReply(query, ByteArray(33) { it.toByte() })

        assertEquals(0, Ipv4Udp.checksum(reply, 0, 20))

        val udpLength = reply.size - 20
        val pseudo = ByteArray(12 + udpLength)
        reply.copyInto(pseudo, 0, 12, 20)
        pseudo[9] = 17
        pseudo[10] = (udpLength ushr 8).toByte()
        pseudo[11] = udpLength.toByte()
        reply.copyInto(pseudo, 12, 20, reply.size)
        assertEquals(0, Ipv4Udp.checksum(pseudo, 0, pseudo.size))
    }

    private fun u16(data: ByteArray, offset: Int) =
        (data[offset].toInt() and 0xFF shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun <T : Any> assertNotNull2(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
