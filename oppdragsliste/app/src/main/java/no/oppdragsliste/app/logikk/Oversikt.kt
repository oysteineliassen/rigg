package no.oppdragsliste.app.logikk

import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Prioritet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** «2 akutte, 1 avtale i dag, 3 haster, 8 venter i området». Null hvis det ikke er noe å melde. */
fun morgenTekst(oppdrag: List<Oppdrag>, idag: LocalDate): String? {
    val apne = oppdrag.filter { !it.ferdig }
    val akutte = apne.count { it.prioritet == Prioritet.AKUTT }
    val avtalerIdag = apne.count { it.prioritet == Prioritet.AVTALE && it.dato == idag }
    val forfalte = apne.count { it.prioritet == Prioritet.AVTALE && it.dato != null && it.dato.isBefore(idag) }
    val haster = apne.count { it.prioritet == Prioritet.HASTER }
    val omrade = apne.count { it.prioritet == Prioritet.OMRADE }
    val deler = buildList {
        if (akutte > 0) add(if (akutte == 1) "1 akutt" else "$akutte akutte")
        if (avtalerIdag > 0) add(if (avtalerIdag == 1) "1 avtale i dag" else "$avtalerIdag avtaler i dag")
        if (forfalte > 0) add(if (forfalte == 1) "1 forfalt avtale" else "$forfalte forfalte avtaler")
        if (haster > 0) add("$haster haster")
        if (omrade > 0) add("$omrade venter i området")
    }
    return deler.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

/** «1 time», «1 t 30 min», «45 min», «2 timer». */
fun varighetTekst(minutter: Int): String {
    val t = minutter / 60
    val m = minutter % 60
    return when {
        t == 0 -> "$m min"
        m == 0 -> if (t == 1) "1 time" else "$t timer"
        else -> "$t t $m min"
    }
}

/** Når påminnelsen for en avtale skal komme, eller null hvis den ikke skal ha påminnelse. */
fun paminnelsesTidspunkt(o: Oppdrag, minutterFor: Int): LocalDateTime? {
    if (o.ferdig || o.prioritet != Prioritet.AVTALE) return null
    val dato = o.dato ?: return null
    val tid = o.tid ?: return null
    return LocalDateTime.of(dato, tid).minusMinutes(minutterFor.toLong())
}

/** Neste gang klokka er [tid]: i dag hvis det ikke har passert, ellers i morgen. */
fun nesteTidspunkt(tid: LocalTime, na: LocalDateTime): LocalDateTime {
    val idag = LocalDateTime.of(na.toLocalDate(), tid)
    return if (idag.isAfter(na)) idag else idag.plusDays(1)
}
