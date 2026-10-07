package no.oppdragsliste.app.logikk

import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Prioritet
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Det som skrives til kalenderen for en avtale. Tider i millisekunder (UTC). */
data class Kalenderhendelse(
    val tittel: String,
    val sted: String,
    val beskrivelse: String,
    val start: Long,
    val slutt: Long,
    val heldag: Boolean,
    val tidssone: String,
) {
    /** Endrer seg når noe av innholdet endres, så uendrede hendelser ikke skrives på nytt. */
    val signatur: String get() = hashCode().toUInt().toString(16)
}

/** Skal oppdraget ligge i kalenderen? Avtaler med dato, også utførte (de har jo skjedd). */
fun skalIKalender(o: Oppdrag): Boolean = o.prioritet == Prioritet.AVTALE && o.dato != null

/**
 * Lager kalenderhendelsen. Med klokkeslett: fra klokkeslettet og [varighetMin] minutter. Uten: heldags.
 * Utførte avtaler får «✓» foran tittelen.
 */
fun kalenderhendelse(o: Oppdrag, varighetMin: Int, sone: ZoneId = ZoneId.systemDefault()): Kalenderhendelse? {
    if (!skalIKalender(o)) return null
    val dato = o.dato ?: return null
    val navn = listOf(o.kunde, o.anlegg).filter { it.isNotBlank() }.joinToString(" · ")
    val hoved = when {
        o.beskrivelse.isNotBlank() && navn.isNotBlank() -> "${o.beskrivelse} – $navn"
        o.beskrivelse.isNotBlank() -> o.beskrivelse
        else -> navn
    }
    val tittel = (if (o.ferdig) "✓ " else "") + hoved
    val beskrivelse = buildList {
        if (o.beskrivelse.isNotBlank()) add(o.beskrivelse)
        if (o.kunde.isNotBlank()) add("Kunde: ${o.kunde}")
        if (o.anlegg.isNotBlank()) add("Anlegg: ${o.anlegg}")
        if (o.kontaktperson.isNotBlank()) add("Kontaktperson: ${o.kontaktperson}")
        if (o.telefon.isNotBlank()) add("Telefon: ${formaterTelefon(o.telefon)}")
        add("")
        add("Fra Oppdragsliste")
    }.joinToString("\n")
    val tid = o.tid
    return if (tid != null) {
        val start = LocalDateTime.of(dato, tid).atZone(sone)
        Kalenderhendelse(
            tittel, o.stedTekst, beskrivelse,
            start.toInstant().toEpochMilli(),
            start.plusMinutes(varighetMin.toLong()).toInstant().toEpochMilli(),
            heldag = false, tidssone = sone.id,
        )
    } else {
        // Heldagshendelser skal ifølge Android ha start og slutt ved midnatt UTC.
        Kalenderhendelse(
            tittel, o.stedTekst, beskrivelse,
            dato.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            dato.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            heldag = true, tidssone = "UTC",
        )
    }
}
