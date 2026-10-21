package no.oppdragsliste.app.logikk

import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Prioritet
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AvtalegrupperTest {
    // Onsdag 7. oktober 2026, uke 41.
    private val idag = LocalDate.of(2026, 10, 7)
    private fun a(id: Long, dato: LocalDate?) = Oppdrag(id = id, prioritet = Prioritet.AVTALE, dato = dato)

    @Test
    fun `grupper i riktig rekkefolge`() {
        val liste = listOf(
            a(1, idag.minusDays(3)),
            a(2, idag),
            a(3, idag.plusDays(1)),
            a(4, LocalDate.of(2026, 10, 10)), // lørdag, samme uke
            a(5, LocalDate.of(2026, 10, 13)), // neste uke
            a(6, LocalDate.of(2026, 10, 20)), // uke 43
            a(7, LocalDate.of(2026, 10, 22)), // uke 43
        )
        val g = grupperAvtaler(liste, idag)
        assertEquals(listOf("Forfalt", "I dag", "I morgen", "Resten av uka", "Neste uke", "Uke 43"), g.map { it.tittel })
        assertEquals(listOf(listOf(1L), listOf(2L), listOf(3L), listOf(4L), listOf(5L), listOf(6L, 7L)), g.map { g2 -> g2.oppdrag.map { it.id } })
        assertEquals(listOf(true, true, true, true, false, false), g.map { it.apenSomStandard })
        assertEquals("uke 42 · 12.–18. okt.", g[4].undertittel)
        assertEquals("19.–25. okt.", g[5].undertittel)
    }

    @Test
    fun `tomme grupper tas ikke med`() {
        val g = grupperAvtaler(listOf(a(1, LocalDate.of(2026, 11, 2))), idag)
        assertEquals(listOf("Uke 45"), g.map { it.tittel })
    }

    @Test
    fun `uke over manedsskifte og arsskifte`() {
        assertEquals("28. sep.–4. okt.", ukeSpenn(LocalDate.of(2026, 10, 1)))
        val nyttar = grupperAvtaler(listOf(a(1, LocalDate.of(2027, 1, 5)), a(2, LocalDate.of(2026, 12, 15))), idag)
        assertEquals(listOf("Uke 51", "Uke 1"), nyttar.map { it.tittel })
    }
}
