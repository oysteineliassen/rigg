package no.oppdragsliste.app.logikk

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class KalendersynkTest {
    private val k = Kobling(hendelse = 7, kalender = 3, signatur = "abc", start = 1000, heldag = false)
    private val uendret = Kalenderstand(1000, false)

    @Test
    fun `ny avtale opprettes`() = assertEquals(Synkhandling.OPPRETT, velgSynkhandling(null, 3, "abc", null))

    @Test
    fun `ingenting endret`() = assertEquals(Synkhandling.INGEN, velgSynkhandling(k, 3, "abc", uendret))

    @Test
    fun `endret i appen skrives til kalenderen`() =
        assertEquals(Synkhandling.OPPDATER_KALENDER, velgSynkhandling(k, 3, "xyz", uendret))

    @Test
    fun `flyttet i kalenderen tas inn i appen`() {
        assertEquals(Synkhandling.OPPDATER_APP, velgSynkhandling(k, 3, "abc", Kalenderstand(5000, false)))
        assertEquals(Synkhandling.OPPDATER_APP, velgSynkhandling(k, 3, "abc", Kalenderstand(1000, true)))
    }

    @Test
    fun `endret begge steder, appen vinner`() =
        assertEquals(Synkhandling.OPPDATER_KALENDER, velgSynkhandling(k, 3, "xyz", Kalenderstand(5000, false)))

    @Test
    fun `slettet i kalenderen, la vaere til appen endrer avtalen`() {
        assertEquals(Synkhandling.INGEN, velgSynkhandling(k, 3, "abc", null))
        assertEquals(Synkhandling.OPPRETT, velgSynkhandling(k, 3, "xyz", null))
    }

    @Test
    fun `byttet kalender`() = assertEquals(Synkhandling.FLYTT_KALENDER, velgSynkhandling(k, 4, "abc", uendret))

    @Test
    fun `gammel kobling uten stand husker standen forst`() {
        val gammel = Kobling.fraTekst("7:3:abc")!!
        assertEquals(Synkhandling.HUSK_STAND, velgSynkhandling(gammel, 3, "abc", Kalenderstand(5000, false)))
    }

    @Test
    fun `kobling til og fra tekst`() {
        assertEquals(k, Kobling.fraTekst(k.tilTekst()))
        assertEquals(Kobling(7, 3, "abc"), Kobling.fraTekst("7:3:abc"))
        assertEquals(null, Kobling.fraTekst("tull"))
    }

    @Test
    fun `dato og klokkeslett fra kalenderen`() {
        val oslo = ZoneId.of("Europe/Oslo")
        val tid = Instant.parse("2026-10-12T07:30:00Z").toEpochMilli()
        assertEquals(LocalDate.of(2026, 10, 12) to LocalTime.of(9, 30), fraKalender(Kalenderstand(tid, false), oslo))
        val heldag = Instant.parse("2026-10-13T00:00:00Z").toEpochMilli()
        assertEquals(LocalDate.of(2026, 10, 13) to null, fraKalender(Kalenderstand(heldag, true), oslo))
    }
}
