package no.oppdragsliste.app.sikkerhetskopi

import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Prioritet
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class SikkerhetskopiTest {
    private val a = Oppdrag(
        id = 5, beskrivelse = "Bytte sikringsskap", kunde = "Kari", telefon = "900 00 000", sted = "Søgne",
        adresse = "Linnegrøvan 1", lat = 58.09, lng = 7.79, prioritet = Prioritet.AVTALE,
        dato = LocalDate.of(2026, 10, 9), tid = LocalTime.of(14, 30), opprettet = Instant.parse("2026-10-01T08:00:00Z"),
    )
    private val b = Oppdrag(
        id = 6, kunde = "Ola", prioritet = Prioritet.OMRADE, opprettet = Instant.parse("2026-09-01T08:00:00Z"),
        ferdig = true, ferdigDato = Instant.parse("2026-09-03T12:00:00Z"),
    )

    @Test
    fun `eksport og import gir samme oppdrag`() {
        val tilbake = les(eksporter(listOf(a, b)))
        assertEquals(listOf(a.copy(id = 0), b.copy(id = 0)), tilbake)
    }

    @Test
    fun `ukjente felt og ukjent prioritet tales`() {
        val tekst = """{"versjon":2,"ekstra":true,"eksportert":"2026-10-07T10:00:00Z",
            "oppdrag":[{"kunde":"X","prioritet":"UKJENT","opprettet":"2026-10-01T08:00:00Z","nytt":1}]}"""
        val o = les(tekst).single()
        assertEquals("X", o.kunde)
        assertEquals(Prioritet.OMRADE, o.prioritet)
    }

    @Test(expected = Exception::class)
    fun `ugyldig fil kaster`() {
        les("ikke json")
    }

    @Test
    fun `import hopper over duplikater`() {
        val importerte = les(eksporter(listOf(a, b)))
        assertEquals(listOf(b.copy(id = 0)), nyeOppdrag(listOf(a), importerte))
        assertEquals(emptyList<Oppdrag>(), nyeOppdrag(listOf(a, b), importerte))
        // Samme fil to ganger i én import gir ikke dobbelt opp.
        assertEquals(2, nyeOppdrag(emptyList(), importerte + importerte).size)
    }
}
