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
    fun `det brukeren har skrevet, og nummer fra samtale, beholdes`() {
        val s = Skjema(telefon = "900 00 000").fyllFra(skole).copy(sted = "Mandal").fyllFra(radhus)
        assertEquals("900 00 000", s.telefon)
        assertEquals("Mandal", s.sted)
        assertEquals("", s.adresse)
    }
}
