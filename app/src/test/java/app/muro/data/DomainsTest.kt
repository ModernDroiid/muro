package app.muro.data

import app.muro.ui.formatCount
import app.muro.ui.formatSaved
import app.muro.ui.formatTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DomainsTest {
    @Test
    fun `limpia lo que pega el usuario`() {
        assertEquals("ads.ejemplo.com", Domains.normalize("  ADS.Ejemplo.com "))
        assertEquals("ads.ejemplo.com", Domains.normalize("https://ads.ejemplo.com/banner?x=1"))
        assertEquals("ads.ejemplo.com", Domains.normalize("ads.ejemplo.com:8080"))
        assertEquals("ads.ejemplo.com", Domains.normalize("ads.ejemplo.com."))
        assertEquals("xn--espaa-rta.com", Domains.normalize("españa.com"))
    }

    @Test
    fun `rechaza lo que no es un dominio`() {
        listOf("", "localhost", "com", "1.2.3.4", "con espacio.com", "-", "a..b.com").forEach {
            assertNull("'$it'", Domains.normalize(it))
        }
    }

    @Test
    fun `entiende hosts y listas simples`() {
        val parsed = Domains.parseList(
            sequenceOf(
                "# comentario",
                "",
                "127.0.0.1  localhost",
                "::1  localhost",
                "127.0.0.1 ads.uno.com",
                "0.0.0.0 ads.dos.com # nota",
                "ads.tres.com",
                "ADS.TRES.COM",
                "esto no es valido!",
            ),
        )
        assertEquals(setOf("ads.uno.com", "ads.dos.com", "ads.tres.com"), parsed)
    }

    @Test
    fun `formatea cifras como el mockup`() {
        assertEquals("1.284", formatCount(1284))
        assertEquals("512", formatCount(512))
        assertEquals("38 MB", formatSaved(1284))
        assertEquals("0 KB", formatSaved(0))
        assertEquals("1,5 GB", formatSaved(52_000))
    }

    @Test
    fun `muestra la fecha solo en filas de otro dia`() {
        val noon = LocalDate.of(2026, 11, 1).atTime(15, 42).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals("15:42", formatTime(noon, ZoneOffset.UTC, LocalDate.of(2026, 11, 1)))
        assertEquals("01/11 15:42", formatTime(noon, ZoneOffset.UTC, LocalDate.of(2026, 11, 2)))
    }
}
