package no.oppdragsliste.app.logikk

import no.oppdragsliste.app.data.Paminnelse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PaminnelserTest {
    private val idag = LocalDate.of(2026, 10, 8) // torsdag

    private fun p(id: Long, dato: LocalDate? = null, tid: LocalTime? = null, ferdig: Boolean = false, tekst: String = "P$id", dag: Int = 1) =
        Paminnelse(id = id, tekst = tekst, dato = dato, tid = tid, ferdig = ferdig, opprettet = Instant.parse("2026-10-%02dT08:00:00Z".format(dag)))

    @Test
    fun `sortering - tidligst forst, uten tid sist, ferdige ikke med`() {
        val liste = listOf(
            p(1),
            p(2, idag.plusDays(1), LocalTime.of(7, 0)),
            p(3, idag, LocalTime.of(15, 0)),
            p(4, idag, null), // varsel kl. 08:00
            p(5, idag, LocalTime.of(9, 0), ferdig = true),
        )
        assertEquals(listOf(4L, 3L, 2L, 1L), sorterPaminnelser(liste).map { it.id })
    }

    @Test
    fun `uten klokkeslett varsles kl 8`() {
        assertEquals(LocalDateTime.of(idag, LocalTime.of(8, 0)), p(1, idag).tidspunkt)
        assertEquals(null, p(1).tidspunkt)
    }

    @Test
    fun forfalt() {
        val na = LocalDateTime.of(idag, LocalTime.of(12, 0))
        assertTrue(erForfalt(p(1, idag, LocalTime.of(11, 0)), na))
        assertFalse(erForfalt(p(2, idag, LocalTime.of(13, 0)), na))
        assertFalse(erForfalt(p(3), na))
        assertFalse(erForfalt(p(4, idag, LocalTime.of(11, 0), ferdig = true), na))
    }

    @Test
    fun `sok i tekst og oppdrag`() {
        val liste = listOf(p(1, tekst = "Bestille kabel"), p(2, tekst = "Ring tilbake").copy(oppdragId = 7))
        assertEquals(listOf(1L), filtrerPaminnelser(liste, "kabel") { null }.map { it.id })
        assertEquals(listOf(2L), filtrerPaminnelser(liste, "søgne") { if (it == 7L) "Sikringsskap Søgne" else null }.map { it.id })
    }

    @Test
    fun hurtigvalg() {
        val formiddag = LocalDateTime.of(idag, LocalTime.of(10, 7))
        val valg = hurtigvalg(formiddag).toMap()
        assertEquals(LocalDateTime.of(idag, LocalTime.of(11, 15)), valg["Om 1 time"])
        assertEquals(LocalDateTime.of(idag, LocalTime.of(18, 0)), valg["I kveld 18:00"])
        assertEquals(LocalDateTime.of(idag.plusDays(1), LocalTime.of(7, 0)), valg["I morgen 07:00"])
        assertFalse("Mandag 07:00" in valg) // torsdag
        // Sent på kvelden: ikke «I kveld».
        assertFalse("I kveld 18:00" in hurtigvalg(LocalDateTime.of(idag, LocalTime.of(17, 30))).toMap())
        // Fredag: mandag tilbys.
        val fredag = LocalDateTime.of(LocalDate.of(2026, 10, 9), LocalTime.of(9, 0))
        assertEquals(LocalDateTime.of(LocalDate.of(2026, 10, 12), LocalTime.of(7, 0)), hurtigvalg(fredag).toMap()["Mandag 07:00"])
        // Søndag: «I morgen» er mandag, så mandag tilbys ikke to ganger.
        val sondag = LocalDateTime.of(LocalDate.of(2026, 10, 11), LocalTime.of(9, 0))
        assertFalse("Mandag 07:00" in hurtigvalg(sondag).toMap())
    }

    @Test
    fun `utsett en time`() =
        assertEquals(LocalDateTime.of(idag, LocalTime.of(13, 30)), utsatt(LocalDateTime.of(idag, LocalTime.of(12, 30, 45))))
}
