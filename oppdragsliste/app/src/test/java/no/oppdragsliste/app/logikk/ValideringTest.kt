package no.oppdragsliste.app.logikk

import no.oppdragsliste.app.data.Prioritet
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ValideringTest {
    private val dato = LocalDate.of(2026, 10, 8)

    @Test
    fun `beskrivelse eller kunde er nok`() {
        assertEquals(emptySet<Feil>(), valider(Prioritet.OMRADE, "Bytte stikk", "", null, null))
        assertEquals(emptySet<Feil>(), valider(Prioritet.OMRADE, "", "Kari", null, null))
    }

    @Test
    fun `mangler bade beskrivelse og kunde`() {
        assertEquals(setOf(Feil.MANGLER_BESKRIVELSE_ELLER_KUNDE), valider(Prioritet.HASTER, " ", "", null, null))
    }

    @Test
    fun `avtale krever dato`() {
        assertEquals(setOf(Feil.MANGLER_DATO), valider(Prioritet.AVTALE, "Måling", "", null, null))
        assertEquals(setOf(Feil.MANGLER_DATO), valider(Prioritet.AVTALE, "Måling", "", null, LocalTime.NOON))
        assertEquals(emptySet<Feil>(), valider(Prioritet.AVTALE, "Måling", "", dato, null))
        assertEquals(emptySet<Feil>(), valider(Prioritet.AVTALE, "Måling", "", dato, LocalTime.NOON))
    }

    @Test
    fun `andre prioriteter trenger ikke dato, men klokkeslett krever dato`() {
        assertEquals(emptySet<Feil>(), valider(Prioritet.AKUTT, "Strømbrudd", "", null, null))
        assertEquals(setOf(Feil.TID_UTEN_DATO), valider(Prioritet.HASTER, "x", "", null, LocalTime.NOON))
    }

    @Test
    fun `flere feil samtidig`() {
        assertEquals(
            setOf(Feil.MANGLER_BESKRIVELSE_ELLER_KUNDE, Feil.MANGLER_DATO),
            valider(Prioritet.AVTALE, "", "", null, null),
        )
    }
}
