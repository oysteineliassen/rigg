package no.oppdragsliste.app.logikk

import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Prioritet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class SorteringTest {
    private val sone = ZoneOffset.UTC
    private fun dag(d: Int): Instant = LocalDate.of(2026, 10, d).atStartOfDay(sone).toInstant()

    private fun o(
        id: Long,
        p: Prioritet,
        opprettet: Instant = dag(1),
        dato: LocalDate? = null,
        tid: LocalTime? = null,
        ferdig: Boolean = false,
        ferdigDato: Instant? = null,
        beskrivelse: String = "Oppdrag $id",
    ) = Oppdrag(
        id = id, prioritet = p, opprettet = opprettet, dato = dato, tid = tid,
        ferdig = ferdig, ferdigDato = ferdigDato, beskrivelse = beskrivelse,
    )

    @Test
    fun `seksjonene kommer i rekkefolgen akutt, avtale, haster, omrade`() {
        val liste = listOf(
            o(1, Prioritet.OMRADE),
            o(2, Prioritet.HASTER),
            o(3, Prioritet.AVTALE, dato = LocalDate.of(2026, 10, 9)),
            o(4, Prioritet.AKUTT),
        )
        assertEquals(
            listOf(Prioritet.AKUTT, Prioritet.AVTALE, Prioritet.HASTER, Prioritet.OMRADE),
            grupper(liste).map { it.prioritet },
        )
    }

    @Test
    fun `tomme seksjoner og ferdige oppdrag tas ikke med`() {
        val liste = listOf(o(1, Prioritet.OMRADE), o(2, Prioritet.AKUTT, ferdig = true))
        val seksjoner = grupper(liste)
        assertEquals(listOf(Prioritet.OMRADE), seksjoner.map { it.prioritet })
        assertEquals(listOf(1L), seksjoner.single().oppdrag.map { it.id })
    }

    @Test
    fun `avtaler sorteres pa dato og sa klokkeslett, uten klokkeslett sist samme dag`() {
        val d8 = LocalDate.of(2026, 10, 8)
        val d9 = LocalDate.of(2026, 10, 9)
        val liste = listOf(
            o(1, Prioritet.AVTALE, dato = d9, tid = LocalTime.of(8, 0)),
            o(2, Prioritet.AVTALE, dato = d8, tid = null),
            o(3, Prioritet.AVTALE, dato = d8, tid = LocalTime.of(14, 0)),
            o(4, Prioritet.AVTALE, dato = d8, tid = LocalTime.of(9, 30)),
        )
        val sortert = grupper(liste).single().oppdrag.map { it.id }
        assertEquals(listOf(4L, 3L, 2L, 1L), sortert)
    }

    @Test
    fun `nar i omradet sorteres eldste forst`() {
        val liste = listOf(
            o(1, Prioritet.OMRADE, opprettet = dag(5)),
            o(2, Prioritet.OMRADE, opprettet = dag(1)),
            o(3, Prioritet.OMRADE, opprettet = dag(3)),
        )
        assertEquals(listOf(2L, 3L, 1L), grupper(liste).single().oppdrag.map { it.id })
    }

    @Test
    fun `utforte sorteres sist utfort forst`() {
        val liste = listOf(
            o(1, Prioritet.OMRADE, ferdig = true, ferdigDato = dag(2)),
            o(2, Prioritet.OMRADE, ferdig = true, ferdigDato = dag(6)),
            o(3, Prioritet.OMRADE),
        )
        assertEquals(listOf(2L, 1L), utforte(liste).map { it.id })
    }

    @Test
    fun `sok treffer sted, kunde, beskrivelse og telefon`() {
        val liste = listOf(
            Oppdrag(id = 1, sted = "Søgne", beskrivelse = "Bytte sikringsskap"),
            Oppdrag(id = 2, kunde = "Ola Nordmann", sted = "Tangvall"),
            Oppdrag(id = 3, telefon = "900 00 123", beskrivelse = "Lampe"),
            Oppdrag(id = 4, adresse = "Storgata 1", sted = "Kristiansand"),
        )
        assertEquals(listOf(1L), filtrer(liste, "søgne").map { it.id })
        assertEquals(listOf(2L), filtrer(liste, "NORDMANN").map { it.id })
        assertEquals(listOf(1L), filtrer(liste, "sikring").map { it.id })
        assertEquals(listOf(3L), filtrer(liste, "90000123").map { it.id })
        assertEquals(listOf(3L), filtrer(liste, "900 00").map { it.id })
        assertEquals(listOf(4L), filtrer(liste, "storgata kristiansand").map { it.id })
        assertEquals(4, filtrer(liste, "  ").size)
    }

    @Test
    fun `dager siden og tekst`() {
        val idag = LocalDate.of(2026, 10, 7)
        assertEquals(0, dagerSiden(dag(7), idag, sone))
        assertEquals(6, dagerSiden(dag(1), idag, sone))
        assertEquals("Lagt inn i dag", lagtInnTekst(0))
        assertEquals("Lagt inn i går", lagtInnTekst(1))
        assertEquals("Lagt inn for 6 dager siden", lagtInnTekst(6))
    }

    @Test
    fun `forfalt nar dato er passert eller klokkeslett i dag er passert`() {
        val na = LocalDateTime.of(2026, 10, 7, 12, 0)
        val igar = LocalDate.of(2026, 10, 6)
        val idag = LocalDate.of(2026, 10, 7)
        assertTrue(erForfalt(o(1, Prioritet.AVTALE, dato = igar), na))
        assertTrue(erForfalt(o(2, Prioritet.AVTALE, dato = idag, tid = LocalTime.of(10, 0)), na))
        assertFalse(erForfalt(o(3, Prioritet.AVTALE, dato = idag, tid = LocalTime.of(14, 0)), na))
        assertFalse(erForfalt(o(4, Prioritet.AVTALE, dato = idag), na))
        assertFalse(erForfalt(o(5, Prioritet.AVTALE, dato = igar, ferdig = true), na))
        assertFalse(erForfalt(o(6, Prioritet.OMRADE), na))
    }
}
