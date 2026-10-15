package no.oppdragsliste.app.ui

import no.oppdragsliste.app.data.KjentAnlegg
import org.junit.Assert.assertEquals
import org.junit.Test

class SkjemaTest {
    private val skole = KjentAnlegg("Søgne kommune", "Søgne skole", telefon = "38 05 55 00", sted = "Søgne", adresse = "Skolevegen 1")
    private val radhus = KjentAnlegg("Søgne kommune", "Rådhuset", telefon = "38 05 55 01", sted = "Tangvall", adresse = "")

    @Test
    fun `tomt skjema fylles helt`() {
        val s = Skjema(kunde = "Søgne kommune", anlegg = "Søgne skole").fyllFra(skole)
        assertEquals("38 05 55 00", s.telefon)
        assertEquals("Søgne", s.sted)
        assertEquals("Skolevegen 1", s.adresse)
    }

    @Test
    fun `bytte anlegg erstatter det som ble fylt inn, ogsa med tomt`() {
        val s = Skjema().fyllFra(skole).fyllFra(radhus)
        assertEquals("38 05 55 01", s.telefon)
        assertEquals("Tangvall", s.sted)
        assertEquals("", s.adresse)
    }

    @Test
    fun `utkast tar vare pa alt som er skrevet`() {
        val s = Skjema(beskrivelse = "Lampe", kunde = "Kari", kontaktperson = "Per", dato = java.time.LocalDate.of(2026, 10, 9))
            .fyllFra(skole)
        val tilbake = Utkast.fra(s).tilSkjema(null)
        assertEquals(s.copy(kilde = null, fraUtkast = true), tilbake)
    }

    @Test
    fun `tomt skjema har ikke innhold, endring uten forskjell heller ikke`() {
        assertEquals(false, Skjema().harInnhold)
        assertEquals(true, Skjema(kunde = "K").harInnhold)
        val o = no.oppdragsliste.app.data.Oppdrag(id = 3, kunde = "K")
        assertEquals(false, Skjema.fra(o).harInnhold)
        assertEquals(true, Skjema.fra(o).copy(sted = "Søgne").harInnhold)
    }

    @Test
    fun `det brukeren har skrevet, og nummer fra samtale, beholdes`() {
        val s = Skjema(telefon = "900 00 000").fyllFra(skole).copy(sted = "Mandal").fyllFra(radhus)
        assertEquals("900 00 000", s.telefon)
        assertEquals("Mandal", s.sted)
        assertEquals("", s.adresse)
    }

    @Test
    fun `notat fylles inn og regnes ikke som endring nar det er uendret`() {
        val k = KjentAnlegg("Søgne kommune", "Rådhuset", notat = "Kode 1234")
        assertEquals("Kode 1234", Skjema().fyllFra(k).notat)
        // Eget notat overskrives ikke.
        assertEquals("Mitt notat", Skjema(notat = "Mitt notat").fyllFra(k).notat)
        val o = no.oppdragsliste.app.data.Oppdrag(id = 3, kunde = "Søgne kommune", anlegg = "Rådhuset")
        assertEquals(false, Skjema.fra(o, "Kode 1234").harInnhold)
        assertEquals(true, Skjema.fra(o, "Kode 1234").copy(notat = "Kode 9999").harInnhold)
    }
}
