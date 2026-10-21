package no.oppdragsliste.app.logikk

import no.oppdragsliste.app.data.Prioritet
import java.time.LocalDate
import java.time.LocalTime

enum class Feil(val melding: String) {
    MANGLER_BESKRIVELSE_ELLER_KUNDE("Skriv inn beskrivelse eller kunde"),
    MANGLER_DATO("Avtalt tid krever dato"),
    TID_UTEN_DATO("Klokkeslett krever dato"),
    SLUTT_UTEN_START("Sluttid krever starttid"),
    SLUTT_FOR_START("Sluttid må være etter starttid"),
}

fun valider(
    prioritet: Prioritet,
    beskrivelse: String,
    kunde: String,
    dato: LocalDate?,
    tid: LocalTime?,
    sluttid: LocalTime? = null,
): Set<Feil> = buildSet {
    if (beskrivelse.isBlank() && kunde.isBlank()) add(Feil.MANGLER_BESKRIVELSE_ELLER_KUNDE)
    if (prioritet == Prioritet.AVTALE && dato == null) add(Feil.MANGLER_DATO)
    else if (tid != null && dato == null) add(Feil.TID_UTEN_DATO)
    if (sluttid != null) {
        if (tid == null) add(Feil.SLUTT_UTEN_START)
        else if (!sluttid.isAfter(tid)) add(Feil.SLUTT_FOR_START)
    }
}
