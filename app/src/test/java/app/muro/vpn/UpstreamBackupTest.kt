package app.muro.vpn

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import java.net.InetAddress

class UpstreamBackupTest {
    private val adguard = MockWebServer()
    private val backup = MockWebServer()
    private val query = queryFor("example.com")
    private val good = answer(query)

    @After
    fun stop() {
        adguard.shutdown()
        backup.shutdown()
    }

    private fun queryFor(domain: String): ByteArray =
        Message.newQuery(Record.newRecord(Name.fromString("$domain."), Type.A, DClass.IN)).toWire()

    private fun answer(query: ByteArray, id: Int = Message(query).header.id, isResponse: Boolean = true): ByteArray {
        val request = Message(query)
        return Message(id).apply {
            if (isResponse) header.setFlag(Flags.QR.toInt())
            addRecord(request.question, Section.QUESTION)
            addRecord(ARecord(request.question.name, DClass.IN, 60, InetAddress.getByName("192.0.2.1")), Section.ANSWER)
        }.toWire()
    }

    private fun MockWebServer.replies(body: ByteArray, code: Int = 200) {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                MockResponse().setResponseCode(code).setBody(Buffer().write(body))
        }
    }

    private fun upstream() = Upstream(
        filteredUrl = adguard.url("/dns-query").toString(),
        unfilteredUrl = adguard.url("/dns-query").toString(),
        backupUrl = { backup.url("/dns-query").toString() },
    )

    @Test
    fun `si AdGuard funciona no toca el respaldo`() {
        adguard.replies(good)
        backup.replies(good)

        assertArrayEquals(good, upstream().resolve(query, filtered = true))
        assertEquals(0, backup.requestCount)
    }

    @Test
    fun `si AdGuard falla pregunta al respaldo por HTTPS`() {
        adguard.replies(ByteArray(0), code = 500)
        backup.replies(good)

        assertArrayEquals(good, upstream().resolve(query, filtered = true))

        val sent = backup.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("application/dns-message", sent.getHeader("Content-Type"))
        assertArrayEquals(query, sent.body.readByteArray())
    }

    @Test
    fun `una respuesta que no corresponde a la consulta cuenta como fallo`() {
        val wrong = listOf(
            answer(query, id = (Message(query).header.id + 1) and 0xFFFF),
            answer(query, isResponse = false),
            answer(queryFor("otro.com"), id = Message(query).header.id),
            byteArrayOf(1, 2, 3),
        )
        wrong.forEach { bad ->
            adguard.replies(bad)
            backup.replies(good)

            assertArrayEquals(good, upstream().resolve(query, filtered = true))
        }
    }

    @Test
    fun `si ambos fallan devuelve null y no hay otro camino`() {
        adguard.replies(ByteArray(0), code = 500)
        backup.replies(ByteArray(0), code = 500)

        assertNull(upstream().resolve(query, filtered = true))
    }

    @Test
    fun `arma la direccion HTTPS del respaldo`() {
        assertEquals("https://1.1.1.1/dns-query", backupDohUrl("1.1.1.1"))
        assertEquals("https://[2606:4700:4700::1111]/dns-query", backupDohUrl("2606:4700:4700::1111"))
    }
}
