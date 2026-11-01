package app.muro

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.muro.data.BlockedDomain
import app.muro.data.ListId
import app.muro.vpn.DnsCodec
import app.muro.vpn.Upstream
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xbill.DNS.DClass
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.Type

/** Caminos que solo se pueden comprobar contra la red real, desde el teléfono. */
@RunWith(AndroidJUnit4::class)
class NetworkPathsTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.muro

    private fun query(domain: String): ByteArray =
        Message.newQuery(Record.newRecord(Name.fromString("$domain."), Type.A, DClass.IN)).toWire()

    /** Espera a que termine la carga inicial que lanza MuroApp al arrancar. */
    private suspend fun awaitSeed() {
        app.lists.seedIfEmpty()
        delay(5_000)
    }

    @Test
    fun listasVencidasSeDescarganYReemplazan() = runBlocking {
        awaitSeed()
        val marker = BlockedDomain(ListId.ADS.name, "marca-de-prueba.invalid")
        app.db.domains().insertAll(listOf(marker))
        app.settings.setListsUpdatedAt(1)

        app.lists.refreshIfStale()

        assertTrue(app.settings.flow.first().listsUpdatedAt > 1)
        assertTrue(app.db.domains().count(ListId.ADS.name) >= 1_000)
        assertTrue(app.db.domains().count(ListId.TRACKERS.name) >= 1_000)
        assertFalse(marker.domain in app.db.domains().observeList(ListId.ADS.name).first())
    }

    @Test
    fun listasRecientesNoSeDescargan() = runBlocking {
        awaitSeed()
        val marker = BlockedDomain(ListId.ADS.name, "marca-reciente.invalid")
        app.db.domains().insertAll(listOf(marker))
        app.settings.setListsUpdatedAt(System.currentTimeMillis())

        app.lists.refreshIfStale()

        assertTrue(marker.domain in app.db.domains().observeList(ListId.ADS.name).first())
    }

    @Test
    fun adguardRealBloqueaYSinFiltroNo() {
        val upstream = Upstream()

        val filtered = assertNotNullReply(upstream.resolve(query("doubleclick.net"), filtered = true))
        val unfiltered = assertNotNullReply(upstream.resolve(query("doubleclick.net"), filtered = false))

        assertTrue(DnsCodec.isSinkholed(filtered))
        assertFalse(DnsCodec.isSinkholed(unfiltered))
    }

    @Test
    fun siAdguardCaeUsaElDnsDeRespaldo() {
        val upstream = Upstream(filteredUrl = DEAD_URL, unfilteredUrl = DEAD_URL)
        upstream.backupDns = "1.1.1.1"

        val reply = assertNotNullReply(upstream.resolve(query("example.com"), filtered = true))

        assertTrue(DnsCodec.hasAnswers(reply))
    }

    @Test
    fun siTodoFallaDevuelveNull() {
        val upstream = Upstream(filteredUrl = DEAD_URL, unfilteredUrl = DEAD_URL)
        upstream.backupDns = "127.0.0.1"

        assertNull(upstream.resolve(query("example.com"), filtered = true))
    }

    private fun assertNotNullReply(reply: ByteArray?): ByteArray {
        assertNotNull(reply)
        return reply!!
    }

    private companion object {
        const val DEAD_URL = "https://127.0.0.1:9/dns-query"
    }
}
