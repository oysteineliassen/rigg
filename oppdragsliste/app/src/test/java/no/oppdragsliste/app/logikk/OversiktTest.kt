package no.oppdragsliste.app.logikk

import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Prioritet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class OversiktTest {
    private val idag = LocalDate.of(2026, 10, 7)

    @Test
    fun morgenoversikt() {
        val liste = listOf(
            Oppdrag(prioritet = Prioritet.AKUTT),
            Oppdrag(prioritet = Prioritet.AKUTT),
            Oppdrag(prioritet = Prioritet.AVTALE, dato = idag),
            Oppdrag(prioritet = Prioritet.AVTALE, dato = idag.plusDays(1)),
        ) + List(8) { Oppdrag(prioritet = Prioritet.OMRADE) } + Oppdrag(prioritet = Prioritet.OMRADE, ferdig = true)
        assertEquals("2 akutte, 1 avtale i dag, 8 venter i området", morgenTekst(liste, idag))
    }

    @Test
    fun `entall, haster og forfalte`() {
        val liste = listOf(
            Oppdrag(prioritet = Prioritet.AKUTT),
            Oppdrag(prioritet = Prioritet.AVTALE, dato = idag.minusDays(2)),
            Oppdrag(prioritet = Prioritet.HASTER),
        )
        assertEquals("1 akutt, 1 forfalt avtale, 1 haster", morgenTekst(liste, idag))
    }

    @Test
    fun `ingenting a melde`() = assertNull(morgenTekst(emptyList(), idag))

    @Test
    fun varighet() {
        assertEquals("45 min", varighetTekst(45))
        assertEquals("1 time", varighetTekst(60))
        assertEquals("1 t 30 min", varighetTekst(90))
        assertEquals("3 timer", varighetTekst(180))
    }

    @Test
    fun paminnelse() {
        val o = Oppdrag(prioritet = Prioritet.AVTALE, dato = idag, tid = LocalTime.of(14, 0))
        assertEquals(LocalDateTime.of(idag, LocalTime.of(13, 0)), paminnelsesTidspunkt(o, 60))
        assertNull(paminnelsesTidspunkt(o.copy(tid = null), 60))
        assertNull(paminnelsesTidspunkt(o.copy(ferdig = true), 60))
        assertNull(paminnelsesTidspunkt(o.copy(prioritet = Prioritet.HASTER), 60))
    }

    @Test
    fun `neste morgen`() {
        val sju = LocalTime.of(7, 0)
        assertEquals(LocalDateTime.of(idag, sju), nesteTidspunkt(sju, LocalDateTime.of(idag, LocalTime.of(6, 0))))
        assertEquals(LocalDateTime.of(idag.plusDays(1), sju), nesteTidspunkt(sju, LocalDateTime.of(idag, LocalTime.of(7, 0))))
    }
}
