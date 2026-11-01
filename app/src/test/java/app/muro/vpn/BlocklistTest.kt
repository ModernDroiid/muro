package app.muro.vpn

import app.muro.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocklistTest {
    private val list = Blocklist(
        blocked = setOf("ads.ejemplo.com", "red.net"),
        allowed = setOf("bueno.red.net"),
    )

    @Test
    fun `bloquea el dominio y sus subdominios`() {
        assertEquals(Verdict.BLOCK, list.verdict("ads.ejemplo.com"))
        assertEquals(Verdict.BLOCK, list.verdict("a.b.ads.ejemplo.com"))
    }

    @Test
    fun `no bloquea dominios parecidos ni el padre`() {
        assertEquals(Verdict.FORWARD, list.verdict("xads.ejemplo.com"))
        assertEquals(Verdict.FORWARD, list.verdict("ejemplo.com"))
        assertEquals(Verdict.FORWARD, list.verdict("com"))
    }

    @Test
    fun `permitir gana aunque el padre este bloqueado`() {
        assertEquals(Verdict.BLOCK, list.verdict("malo.red.net"))
        assertEquals(Verdict.ALLOW, list.verdict("bueno.red.net"))
        assertEquals(Verdict.ALLOW, list.verdict("cdn.bueno.red.net"))
    }

    @Test
    fun `A y AAAA del mismo dominio cuentan una sola vez`() {
        val dedupe = QueryLogger.Deduplicator(windowMs = 2_000)
        fun entry(time: Long, domain: String = "a.com") = LogEntry(0, time, domain, "app", "App", true)

        assertTrue(dedupe.isFresh(entry(1_000)))
        assertFalse(dedupe.isFresh(entry(1_500)))
        assertTrue(dedupe.isFresh(entry(1_500, "b.com")))
        assertTrue(dedupe.isFresh(entry(4_000)))
    }
}
