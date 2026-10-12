package no.oppdragsliste.app.logikk

import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Paminnelse
import no.oppdragsliste.app.data.Prioritet
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class WidgetinnholdTest {
    private val idag = LocalDate.of(2026, 10, 12)
    private val na = LocalDateTime.of(idag, LocalTime.of(10, 0))

    private fun o(t: String, p: Prioritet, dato: LocalDate? = null, tid: LocalTime? = null, slutt: LocalTime? = null, dag: Int = 1) =
        Oppdrag(beskrivelse = t, prioritet = p, dato = dato, tid = tid, sluttid = slutt, opprettet = Instant.parse("2026-10-%02dT08:00:00Z".format(dag)))

    @Test
    fun `rekkefolge og tekst`() {
        val oppdrag = listOf(
            o("Haster", Prioritet.HASTER),
            o("Område", Prioritet.OMRADE),
            o("I morgen", Prioritet.AVTALE, idag.plusDays(1), LocalTime.of(7, 30)),
            o("Kl 14", Prioritet.AVTALE, idag, LocalTime.of(14, 0), LocalTime.of(15, 30)),
            o("Kl 9", Prioritet.AVTALE, idag, LocalTime.of(9, 0)),
            o("Akutt", Prioritet.AKUTT),
            o("Forfalt", Prioritet.AVTALE, idag.minusDays(2)),
            o("Ferdig", Prioritet.AKUTT).copy(ferdig = true),
        )
        val p = listOf(Paminnelse(tekst = "Ring Kari\nom tilbud", dato = idag, tid = LocalTime.of(12, 0)))
        val w = widgetinnhold(oppdrag, p, na)
        assertEquals(listOf("Forfalt", "Akutt", "Kl 9", "Ring Kari", "Kl 14", "I morgen", "Haster"), w.rader.map { it.tittel })
        assertEquals(listOf("lør. 10. okt.", "Akutt", "09:00", "12:00", "14:00–15:30", "i morgen 07:30", "Haster"), w.rader.map { it.nar })
        assertEquals(true, w.rader[0].forfalt)
        assertEquals(true, w.rader[2].forfalt) // kl. 9 har passert
        assertEquals(Radtype.PAMINNELSE, w.rader[3].type)
        assertEquals("1 forfalt · 1 akutt · 3 i dag · 1 i området", w.oppsummering)
    }

    @Test
    fun `tom liste`() {
        assertEquals("Ingen åpne oppdrag", widgetinnhold(emptyList(), emptyList(), na).oppsummering)
        assertEquals("1 i området", widgetinnhold(listOf(o("x", Prioritet.OMRADE)), emptyList(), na).oppsummering)
    }

    @Test
    fun `anlegg pa hver rad`() {
        val akutt = Oppdrag(id = 1, beskrivelse = "Strømbrudd", kunde = "Søgne kommune", anlegg = "Rådhuset", prioritet = Prioritet.AKUTT)
        val utenBeskrivelse = Oppdrag(id = 2, kunde = "Ola Hansen", anlegg = "Hytta", prioritet = Prioritet.HASTER)
        val p = Paminnelse(tekst = "Bestille kabel", dato = idag, tid = LocalTime.of(12, 0), oppdragId = 1)
        val w = widgetinnhold(listOf(akutt, utenBeskrivelse), listOf(p), na)
        assertEquals("Rådhuset · Søgne kommune", w.rader.first { it.tittel == "Strømbrudd" }.under)
        // Kunden er tittelen, så bare anlegget under.
        assertEquals("Hytta", w.rader.first { it.tittel == "Ola Hansen" }.under)
        assertEquals("Rådhuset · Søgne kommune", w.rader.first { it.tittel == "Bestille kabel" }.under)
    }
}
