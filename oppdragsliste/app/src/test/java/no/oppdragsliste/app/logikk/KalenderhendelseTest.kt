package no.oppdragsliste.app.logikk

import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Prioritet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class KalenderhendelseTest {
    private val oslo = ZoneId.of("Europe/Oslo")
    private val avtale = Oppdrag(
        id = 1, prioritet = Prioritet.AVTALE, beskrivelse = "Måling", kunde = "Søgne kommune", anlegg = "Rådhuset",
        telefon = "38055501", sted = "Tangvall", adresse = "Rådhusvegen 1",
        dato = LocalDate.of(2026, 10, 9), tid = LocalTime.of(14, 0), opprettet = Instant.EPOCH,
    )

    @Test
    fun `avtale med klokkeslett`() {
        val h = kalenderhendelse(avtale, 90, oslo)!!
        assertEquals("Måling – Søgne kommune · Rådhuset", h.tittel)
        assertEquals("Rådhusvegen 1, Tangvall", h.sted)
        assertTrue(h.beskrivelse.contains("Telefon: 38 05 55 01"))
        assertFalse(h.heldag)
        assertEquals(Instant.parse("2026-10-09T12:00:00Z").toEpochMilli(), h.start) // sommertid: UTC+2
        assertEquals(90 * 60_000L, h.slutt - h.start)
    }

    @Test
    fun `uten klokkeslett blir heldag ved midnatt UTC`() {
        val h = kalenderhendelse(avtale.copy(tid = null), 60, oslo)!!
        assertTrue(h.heldag)
        assertEquals("UTC", h.tidssone)
        assertEquals(Instant.parse("2026-10-09T00:00:00Z").toEpochMilli(), h.start)
        assertEquals(Instant.parse("2026-10-10T00:00:00Z").toEpochMilli(), h.slutt)
    }

    @Test
    fun `bare avtaler med dato, utforte merkes`() {
        assertNull(kalenderhendelse(avtale.copy(prioritet = Prioritet.HASTER), 60, oslo))
        assertNull(kalenderhendelse(avtale.copy(dato = null, tid = null), 60, oslo))
        assertTrue(kalenderhendelse(avtale.copy(ferdig = true), 60, oslo)!!.tittel.startsWith("✓ "))
    }

    @Test
    fun `ny dato gir ny signatur, sa hendelsen flyttes`() {
        val for_ = kalenderhendelse(avtale, 60, oslo)!!
        assertEquals(for_.signatur, kalenderhendelse(avtale, 60, oslo)!!.signatur)
        assertNotEquals(for_.signatur, kalenderhendelse(avtale.copy(dato = LocalDate.of(2026, 10, 12)), 60, oslo)!!.signatur)
    }
}
