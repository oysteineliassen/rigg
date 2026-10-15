package no.oppdragsliste.app.logikk

import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Prioritet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AvstandTest {
    // Kristiansand sentrum og Søgne sentrum, ca. 15 km i luftlinje.
    private val krs = 58.1467 to 7.9956
    private val sogne = 58.0942 to 7.7940
    private val idag = LocalDate.of(2026, 10, 7)

    private fun o(id: Long, p: Prioritet = Prioritet.OMRADE, pos: Pair<Double, Double>? = sogne, dag: Int = 1, sted: String = "Søgne") =
        Oppdrag(
            id = id, prioritet = p, lat = pos?.first, lng = pos?.second, sted = sted,
            opprettet = Instant.parse("2026-09-%02dT08:00:00Z".format(dag)),
        )

    @Test
    fun haversine() {
        val m = avstandMeter(krs.first, krs.second, sogne.first, sogne.second)
        assertTrue("var $m", m in 12_000f..15_000f)
        assertEquals(0f, avstandMeter(1.0, 1.0, 1.0, 1.0))
    }

    @Test
    fun `geofencer begrenses til 100 og prioriterer akutt, haster og eldste`() {
        val mange = (1L..120L).map { o(it, dag = 1 + (it % 28).toInt()) } +
            o(500, Prioritet.AKUTT, dag = 28) + o(501, Prioritet.HASTER, dag = 28) + o(502, pos = null)
        val valgt = velgGeofencer(mange)
        assertEquals(100, valgt.size)
        assertEquals(listOf(500L, 501L), valgt.take(2).map { it.id })
        assertTrue(valgt.none { it.id == 502L })
        // Etter akutt og haster: de eldste først.
        val resten = valgt.drop(2).map { it.opprettet }
        assertEquals(resten.sorted(), resten)
    }

    @Test
    fun `sorter pa avstand, uten koordinater sist`() {
        val liste = listOf(o(1, pos = sogne), o(2, pos = null), o(3, pos = krs), o(4, pos = krs).copy(ferdig = true))
        assertEquals(listOf(3L, 1L, 2L), sorterPaAvstand(liste, krs.first, krs.second).map { it.first.id })
    }

    @Test
    fun `naer varsles bare innenfor radius og en gang per dag`() {
        val liste = listOf(o(1), o(2).copy(naerVarslet = idag), o(3, pos = krs), o(4).copy(naerVarslet = idag.minusDays(1)))
        assertEquals(listOf(1L, 4L), naerSomSkalVarsles(liste, sogne.first, sogne.second, 2000, idag).map { it.id })
    }

    @Test
    fun `varseltekst grupperer pa sted`() {
        val (tittel, tekst) = naerTekst(listOf(o(1, sted = "Tangvall"), o(2), o(3)))
        assertEquals("3 oppdrag i nærheten", tittel)
        assertEquals("Søgne (2), Tangvall (1)", tekst)
    }
}
