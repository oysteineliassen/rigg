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
        assertEquals("Søgne skole", finnUtfylling(kjente, "Søgne kommune", "")?.anlegg)
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
}
