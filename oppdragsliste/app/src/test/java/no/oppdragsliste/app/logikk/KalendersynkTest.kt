package no.oppdragsliste.app.logikk

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class KalendersynkTest {
    private val k = Kobling(hendelse = 7, kalender = 3, signatur = "abc", start = 1000, heldag = false, slutt = 2000)
    private val uendret = Kalenderstand(1000, false, 2000)

    @Test
    fun `ny avtale opprettes`() = assertEquals(Synkhandling.OPPRETT, velgSynkhandling(null, 3, "abc", null))

    @Test
    fun `ingenting endret`() = assertEquals(Synkhandling.INGEN, velgSynkhandling(k, 3, "abc", uendret))

    @Test
    fun `endret i appen skrives til kalenderen`() =
        assertEquals(Synkhandling.OPPDATER_KALENDER, velgSynkhandling(k, 3, "xyz", uendret))

    @Test
    fun `flyttet i kalenderen tas inn i appen`() {
        assertEquals(Synkhandling.OPPDATER_APP, velgSynkhandling(k, 3, "abc", Kalenderstand(5000, false, 6000)))
        assertEquals(Synkhandling.OPPDATER_APP, velgSynkhandling(k, 3, "abc", Kalenderstand(1000, true, 2000)))
    }

    @Test
    fun `endret begge steder, appen vinner`() =
        assertEquals(Synkhandling.OPPDATER_KALENDER, velgSynkhandling(k, 3, "xyz", Kalenderstand(5000, false, 6000)))

    @Test
    fun `slettet i kalenderen, la vaere til appen endrer avtalen`() {
        assertEquals(Synkhandling.INGEN, velgSynkhandling(k, 3, "abc", null))
        assertEquals(Synkhandling.OPPRETT, velgSynkhandling(k, 3, "xyz", null))
    }

    @Test
    fun `endret sluttid i kalenderen tas inn i appen`() =
        assertEquals(Synkhandling.OPPDATER_APP, velgSynkhandling(k, 3, "abc", Kalenderstand(1000, false, 9000)))

    @Test
    fun `kobling uten sluttid fra for husker standen forst`() {
        val uten = k.copy(slutt = null)
        assertEquals(Synkhandling.HUSK_STAND, velgSynkhandling(uten, 3, "abc", Kalenderstand(1000, false, 2000)))
    }

    @Test
    fun `byttet kalender`() = assertEquals(Synkhandling.FLYTT_KALENDER, velgSynkhandling(k, 4, "abc", uendret))

    @Test
    fun `gammel kobling uten stand husker standen forst`() {
        val gammel = Kobling.fraTekst("7:3:abc")!!
        assertEquals(Synkhandling.HUSK_STAND, velgSynkhandling(gammel, 3, "abc", Kalenderstand(5000, false, 6000)))
    }

    @Test
    fun `kobling til og fra tekst`() {
        assertEquals(k, Kobling.fraTekst(k.tilTekst()))
        assertEquals(Kobling(7, 3, "abc"), Kobling.fraTekst("7:3:abc"))
        assertEquals(Kobling(7, 3, "abc", 1000, false), Kobling.fraTekst("7:3:abc:1000:false"))
        assertEquals(null, Kobling.fraTekst("tull"))
    }

    @Test
    fun `dato og klokkeslett fra kalenderen`() {
        val oslo = ZoneId.of("Europe/Oslo")
        val start = Instant.parse("2026-10-12T07:30:00Z").toEpochMilli()
        val slutt = Instant.parse("2026-10-12T10:00:00Z").toEpochMilli()
        assertEquals(
            FraKalender(LocalDate.of(2026, 10, 12), LocalTime.of(9, 30), LocalTime.of(12, 0)),
            fraKalender(Kalenderstand(start, false, slutt), sone = oslo),
        )
        val heldag = Instant.parse("2026-10-13T00:00:00Z").toEpochMilli()
        assertEquals(
            FraKalender(LocalDate.of(2026, 10, 13), null, null),
            fraKalender(Kalenderstand(heldag, true, heldag + 86_400_000), sone = oslo),
        )
    }

    @Test
    fun `flyttet uten a endre lengden beholder tom sluttid`() {
        val oslo = ZoneId.of("Europe/Oslo")
        val time = 3_600_000L
        val forrige = Kobling(7, 3, "abc", start = 0, heldag = false, slutt = time)
        val start = Instant.parse("2026-10-12T07:30:00Z").toEpochMilli()
        val flyttet = Kalenderstand(start, false, start + time)
        assertEquals(null, fraKalender(flyttet, forrige, haddeSluttid = false, sone = oslo).sluttid)
        // Lengden endret: da blir sluttiden satt.
        val lengre = Kalenderstand(start, false, start + 2 * time)
        assertEquals(LocalTime.of(11, 30), fraKalender(lengre, forrige, haddeSluttid = false, sone = oslo).sluttid)
        // Hadde sluttid fra før: den følger med.
        assertEquals(LocalTime.of(10, 30), fraKalender(flyttet, forrige, haddeSluttid = true, sone = oslo).sluttid)
    }

    @Test
    fun `hendelse over midnatt gir ingen sluttid`() {
        val oslo = ZoneId.of("Europe/Oslo")
        val start = Instant.parse("2026-10-12T20:00:00Z").toEpochMilli()
        val slutt = Instant.parse("2026-10-12T23:00:00Z").toEpochMilli()
        assertEquals(null, fraKalender(Kalenderstand(start, false, slutt), sone = oslo).sluttid)
    }
}
