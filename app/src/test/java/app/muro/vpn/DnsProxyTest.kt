package app.muro.vpn

import app.muro.data.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import java.net.InetAddress

class DnsProxyTest {
    private val logged = mutableListOf<LogEntry>()
    private val calls = mutableListOf<Boolean>()
    private var upstream: (ByteArray) -> ByteArray? = { answerFor(it, "93.184.216.34") }

    private val proxy = DnsProxy(
        scope = CoroutineScope(Dispatchers.Unconfined),
        resolver = { query, filtered ->
            calls += filtered
            upstream(query)
        },
        appOf = { AppRef("com.test", "Test") },
        log = { logged += it },
        clock = { 1234L },
    )

    private fun query(domain: String, type: Int = Type.A): UdpDatagram {
        val message = Message.newQuery(Record.newRecord(Name.fromString("$domain."), type, DClass.IN))
        return UdpDatagram(ByteArray(4), Tunnel.DNS_BYTES, 40000, 53, message.toWire())
    }

    private fun emptyAnswer(query: ByteArray): ByteArray {
        val request = Message(query)
        return Message(request.header.id).apply {
            header.setFlag(Flags.QR.toInt())
            addRecord(request.question, Section.QUESTION)
        }.toWire()
    }

    private fun answerFor(query: ByteArray, ip: String): ByteArray {
        val request = Message(query)
        return Message(request.header.id).apply {
            header.setFlag(Flags.QR.toInt())
            addRecord(request.question, Section.QUESTION)
            addRecord(ARecord(request.question.name, DClass.IN, 60, InetAddress.getByName(ip)), Section.ANSWER)
        }.toWire()
    }

    @Test
    fun `un dominio de la lista responde no existe sin salir del celular`() {
        proxy.blocklist = Blocklist(setOf("ads.ejemplo.com"), emptySet())
        val q = query("x.ads.ejemplo.com")

        val reply = Message(proxy.answer(q)!!)

        assertEquals(Rcode.NXDOMAIN, reply.header.rcode)
        assertEquals(Message(q.payload).header.id, reply.header.id)
        assertTrue(reply.header.getFlag(Flags.QR.toInt()))
        assertTrue(calls.isEmpty())
        assertTrue(logged.single().blocked)
    }

    @Test
    fun `lo que no esta en la lista pasa por AdGuard filtrado`() {
        val q = query("api.ejemplo.com")

        val reply = proxy.answer(q)!!

        assertArrayEquals(answerFor(q.payload, "93.184.216.34"), reply)
        assertEquals(listOf(true), calls)
        with(logged.single()) {
            assertFalse(blocked)
            assertEquals("api.ejemplo.com", domain)
            assertEquals("Test", appName)
            assertEquals(1234L, timestamp)
        }
    }

    @Test
    fun `lo que bloquea AdGuard se registra como bloqueado`() {
        upstream = { answerFor(it, "0.0.0.0") }

        proxy.answer(query("doubleclick.net"))

        assertTrue(logged.single().blocked)
    }

    @Test
    fun `el bloqueo de AdGuard no entrega su respuesta con TTL a la app`() {
        upstream = { answerFor(it, "0.0.0.0") }

        val reply = Message(proxy.answer(query("doubleclick.net"))!!)

        assertEquals(Rcode.NXDOMAIN, reply.header.rcode)
        assertTrue(reply.getSection(Section.ANSWER).isEmpty())
    }

    @Test
    fun `la respuesta vacia a HTTPS no aparece como permitido junto al bloqueo`() {
        upstream = { emptyAnswer(it) }

        val reply = proxy.answer(query("taboola.com", Type.HTTPS))

        assertTrue(Message(reply!!).getSection(Section.ANSWER).isEmpty())
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `las sondas de Android se responden pero no ensucian el registro`() {
        val reply = proxy.answer(query("_dns.resolver.arpa"))

        assertTrue(reply != null)
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `una consulta A sin respuesta si se registra`() {
        upstream = { emptyAnswer(it) }

        proxy.answer(query("sin-ipv4.ejemplo.com"))

        assertFalse(logged.single().blocked)
    }

    @Test
    fun `un bloqueo local se registra aunque no sea tipo A`() {
        proxy.blocklist = Blocklist(setOf("ads.ejemplo.com"), emptySet())

        proxy.answer(query("ads.ejemplo.com", Type.HTTPS))

        assertTrue(logged.single().blocked)
    }

    @Test
    fun `permitir gana a la lista y se salta el filtro de AdGuard`() {
        proxy.blocklist = Blocklist(setOf("ads.ejemplo.com"), setOf("ads.ejemplo.com"))

        proxy.answer(query("ads.ejemplo.com"))

        assertEquals(listOf(false), calls)
        assertFalse(logged.single().blocked)
    }

    @Test
    fun `sin respuesta de la red devuelve error y no se cuelga`() {
        upstream = { null }

        val reply = Message(proxy.answer(query("ejemplo.com"))!!)

        assertEquals(Rcode.SERVFAIL, reply.header.rcode)
        assertFalse(logged.single().blocked)
    }

    @Test
    fun `una respuesta que no cabe en el tunel devuelve error`() {
        upstream = { ByteArray(Tunnel.MAX_DNS_PAYLOAD + 1) }

        val reply = Message(proxy.answer(query("ejemplo.com"))!!)

        assertEquals(Rcode.SERVFAIL, reply.header.rcode)
    }

    @Test
    fun `un paquete que no es DNS se descarta`() {
        val junk = UdpDatagram(ByteArray(4), Tunnel.DNS_BYTES, 40000, 53, byteArrayOf(1, 2, 3))

        assertNull(proxy.answer(junk))
        assertTrue(logged.isEmpty())
    }
}
