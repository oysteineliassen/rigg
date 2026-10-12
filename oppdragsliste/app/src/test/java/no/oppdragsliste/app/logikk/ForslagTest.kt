package no.oppdragsliste.app.logikk

import no.oppdragsliste.app.data.KjentAnlegg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ForslagTest {
    private fun k(kunde: String, anlegg: String, dag: Int, telefon: String = "", sted: String = "") =
        KjentAnlegg(kunde, anlegg, telefon = telefon, sted = sted, sistBrukt = Instant.parse("2026-10-%02dT08:00:00Z".format(dag)))

    // Sortert sist brukt først, slik databasen leverer dem.
    private val kjente = listOf(
        k("Søgne kommune", "Søgne skole", 9, "38 05 55 00", "Søgne"),
        k("Ola Hansen", "", 8, "900 00 001", "Tangvall"),
        k("Søgne kommune", "Rådhuset", 7, "38 05 55 01", "Søgne"),
        k("Kristiansand kommune", "Søgne bo- og servicesenter", 6),
        k("Bakeriet Søgne AS", "Butikk", 5),
        k("søgne kommune", "Tangvall skole", 4), // annen skrivemåte, samme kunde
    )

    @Test
    fun `kundeforslag rangerer starter-med foran ord og inneholder`() {
        assertEquals(
            listOf("Søgne kommune", "Bakeriet Søgne AS"),
            kundeForslag(kjente, "søg"),
        )
        // Begge har et ord som starter med «kommune»; sist brukt først, og «søgne kommune» er samme kunde.
        assertEquals(listOf("Søgne kommune", "Kristiansand kommune"), kundeForslag(kjente, "kommune"))
        assertEquals(listOf("Ola Hansen"), kundeForslag(kjente, "ha"))
    }

    @Test
    fun `ingen kundeforslag for tomt felt eller ferdig skrevet navn`() {
        assertEquals(emptyList<String>(), kundeForslag(kjente, ""))
        assertEquals(emptyList<String>(), kundeForslag(kjente, "Ola Hansen"))
    }

    @Test
    fun `anleggsforslag for kunden, sist brukt forst, uansett store og sma bokstaver`() {
        assertEquals(
            listOf("Søgne skole", "Rådhuset", "Tangvall skole").map { AnleggForslag(it) },
            anleggForslag(kjente, "SØGNE KOMMUNE", ""),
        )
        assertEquals(listOf("Søgne skole", "Tangvall skole"), anleggForslag(kjente, "Søgne kommune", "sko").map { it.anlegg })
        assertEquals(emptyList<AnleggForslag>(), anleggForslag(kjente, "Ola Hansen", ""))
    }

    @Test
    fun `sok bare pa anlegg viser kunden, sa den kan fylles inn`() {
        assertEquals(emptyList<AnleggForslag>(), anleggForslag(kjente, "", ""))
        assertEquals(listOf(AnleggForslag("Butikk", "Bakeriet Søgne AS")), anleggForslag(kjente, "", "but"))
        assertEquals(
            listOf(AnleggForslag("Søgne skole", "Søgne kommune"), AnleggForslag("Tangvall skole", "søgne kommune")),
            anleggForslag(kjente, "", "skole"),
        )
        assertEquals("Butikk · Bakeriet Søgne AS", AnleggForslag("Butikk", "Bakeriet Søgne AS").tekst)
    }

    @Test
    fun `med kunde kommer egne anlegg forst, sa anlegg hos andre kunder`() {
        assertEquals(listOf(AnleggForslag("Rådhuset", "Søgne kommune")), anleggForslag(kjente, "Ola Hansen", "rådh"))
        // Søgne kommune har selv Rådhuset, så det vises uten kunde.
        assertEquals(listOf(AnleggForslag("Rådhuset")), anleggForslag(kjente, "Søgne kommune", "rådh"))
    }

    @Test
    fun utfylling() {
        assertEquals("38 05 55 01", finnUtfylling(kjente, "søgne kommune", "rådhuset")?.telefon)
        // Uten anlegg: det som sist ble brukt for kunden.
        assertEquals("38 05 55 00", finnUtfylling(kjente, "Søgne kommune", "")?.telefon)
        assertEquals("Tangvall", finnUtfylling(kjente, "Ola Hansen", "")?.sted)
        assertNull(finnUtfylling(kjente, "", ""))
        assertNull(finnUtfylling(kjente, "Ny kunde", "Ukjent"))
    }

    @Test
    fun `kunde og anlegg som ikke er kjent sammen`() {
        // Nytt anlegg for kjent kunde: telefon fra kunden, men ikke sted fra et annet anlegg.
        val nyttAnlegg = finnUtfylling(kjente, "Søgne kommune", "Ukjent")!!
        assertEquals("38 05 55 00", nyttAnlegg.telefon)
        assertEquals("", nyttAnlegg.sted)
        // Ny kunde på kjent anlegg: sted fra anlegget, men ikke telefonen til den andre kunden.
        val nyKunde = finnUtfylling(kjente, "Ny kunde AS", "Rådhuset")!!
        assertEquals("Søgne", nyKunde.sted)
        assertEquals("", nyKunde.telefon)
    }

    @Test
    fun `kjent kunde fra telefonnummer`() {
        assertEquals("Ola Hansen", finnPaTelefon(kjente, "+47 90000001")?.kunde)
        assertNull(finnPaTelefon(kjente, "90000009"))
        assertNull(finnPaTelefon(kjente, "113"))
    }

    @Test
    fun `autoutfylling overskriver bare tomt eller eget forslag`() {
        assertEquals("Søgne", nyVerdi("", null, "Søgne"))
        assertEquals("Søgne", nyVerdi("Tangvall", "Tangvall", "Søgne"))
        assertEquals("Mandal", nyVerdi("Mandal", "Tangvall", "Søgne"))
        assertEquals("900 00 000", nyVerdi("900 00 000", null, "38 05 55 00"))
    }

    @Test
    fun `kandidater for en samtale - nummer og navn, sist brukt forst, uten duplikater`() {
        val liste = listOf(
            KjentAnlegg("Søgne kommune", "Rådhuset", kontaktperson = "Kari", telefon = "900 00 001", sistBrukt = Instant.parse("2026-10-05T08:00:00Z")),
            KjentAnlegg("Søgne kommune", "Søgne skole", kontaktperson = "Kari", telefon = "90000001", sistBrukt = Instant.parse("2026-10-07T08:00:00Z")),
            KjentAnlegg("Bakeriet", "", kontaktperson = "kari", telefon = "411 11 111", sistBrukt = Instant.parse("2026-10-06T08:00:00Z")),
            KjentAnlegg("Annen", "X", kontaktperson = "Per", telefon = "922 22 222", sistBrukt = Instant.parse("2026-10-08T08:00:00Z")),
            KjentAnlegg("Søgne kommune", "Rådhuset", kontaktperson = "Kari", telefon = "90000001", sistBrukt = Instant.parse("2026-10-01T08:00:00Z")),
        )
        assertEquals(
            listOf("Søgne skole", "", "Rådhuset"),
            kandidaterForSamtale(liste, "+47 900 00 001", "Kari").map { it.anlegg },
        )
        // Bare nummer (ukjent navn).
        assertEquals(listOf("Søgne skole", "Rådhuset"), kandidaterForSamtale(liste, "90000001", null).map { it.anlegg })
        assertEquals(emptyList<KjentAnlegg>(), kandidaterForSamtale(liste, "95555555", "Ola"))
    }

    @Test
    fun `notat folger anlegget`() {
        val liste = listOf(
            KjentAnlegg("Søgne kommune", "Rådhuset", notat = "Kode 1234 til porten"),
            KjentAnlegg("Søgne kommune", "Søgne skole", notat = "Nøkkel i resepsjonen"),
        )
        assertEquals("Kode 1234 til porten", finnUtfylling(liste, "søgne kommune", "RÅDHUSET")?.notat)
        // Ny kunde på kjent anlegg: notatet fra anlegget.
        assertEquals("Kode 1234 til porten", finnUtfylling(liste, "Ny kunde", "Rådhuset")?.notat)
        // Nytt anlegg for kjent kunde: ikke notatet fra et annet anlegg.
        assertEquals("", finnUtfylling(liste, "Søgne kommune", "Ukjent")?.notat)
        assertEquals("Nøkkel i resepsjonen", notatFor(liste, " Søgne kommune ", "søgne skole"))
        assertEquals("", notatFor(liste, "", ""))
    }
}
