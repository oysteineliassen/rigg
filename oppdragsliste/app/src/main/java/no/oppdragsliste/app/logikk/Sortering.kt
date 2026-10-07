package no.oppdragsliste.app.logikk

import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Prioritet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class Seksjon(val prioritet: Prioritet, val oppdrag: List<Oppdrag>)

/** Sammenligner avtaler: dato, så klokkeslett (uten klokkeslett sist den dagen), så når de ble lagt inn. */
val avtaleRekkefolge: Comparator<Oppdrag> =
    compareBy<Oppdrag, LocalDate?>(nullsLast()) { it.dato }
        .thenBy(nullsLast()) { it.tid }
        .thenBy { it.opprettet }

/** Eldste først: det som har ventet lengst, øverst. */
val eldsteForst: Comparator<Oppdrag> = compareBy<Oppdrag> { it.opprettet }.thenBy { it.id }

fun sorter(prioritet: Prioritet, oppdrag: List<Oppdrag>): List<Oppdrag> = when (prioritet) {
    Prioritet.AVTALE -> oppdrag.sortedWith(avtaleRekkefolge)
    Prioritet.AKUTT, Prioritet.HASTER, Prioritet.OMRADE -> oppdrag.sortedWith(eldsteForst)
}

/**
 * Åpne oppdrag gruppert i seksjoner i fast rekkefølge: Akutt → Avtalt tid → Haster → Når i området.
 * Tomme seksjoner tas ikke med.
 */
fun grupper(oppdrag: List<Oppdrag>): List<Seksjon> {
    val apne = oppdrag.filter { !it.ferdig }.groupBy { it.prioritet }
    return Prioritet.entries.mapNotNull { p ->
        apne[p]?.takeIf { it.isNotEmpty() }?.let { Seksjon(p, sorter(p, it)) }
    }
}

/** Utførte oppdrag, sist utført først. */
fun utforte(oppdrag: List<Oppdrag>): List<Oppdrag> =
    oppdrag.filter { it.ferdig }.sortedWith(compareByDescending<Oppdrag> { it.ferdigDato ?: it.opprettet })

/** Søk i sted, adresse, kunde, anlegg, beskrivelse og telefon. Telefon matcher også uten mellomrom. */
fun filtrer(oppdrag: List<Oppdrag>, sok: String): List<Oppdrag> {
    val ord = sok.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (ord.isEmpty()) return oppdrag
    val sifferSok = sok.filter { it.isDigit() }
    return oppdrag.filter { o ->
        val tekst = listOf(o.sted, o.adresse, o.kunde, o.anlegg, o.beskrivelse, o.telefon).joinToString(" ").lowercase()
        ord.all { tekst.contains(it) } ||
            (sifferSok.length >= 3 && o.telefon.filter { it.isDigit() }.contains(sifferSok))
    }
}

fun dagerSiden(opprettet: Instant, idag: LocalDate, sone: ZoneId = ZoneId.systemDefault()): Long =
    ChronoUnit.DAYS.between(opprettet.atZone(sone).toLocalDate(), idag).coerceAtLeast(0)

fun lagtInnTekst(dager: Long): String = when (dager) {
    0L -> "Lagt inn i dag"
    1L -> "Lagt inn i går"
    else -> "Lagt inn for $dager dager siden"
}

/**
 * Forfalt: åpent oppdrag med dato før i dag, eller i dag med klokkeslett som har passert.
 */
fun erForfalt(o: Oppdrag, na: LocalDateTime): Boolean {
    if (o.ferdig) return false
    val dato = o.dato ?: return false
    val idag = na.toLocalDate()
    return dato.isBefore(idag) || (dato == idag && o.tid != null && o.tid.isBefore(na.toLocalTime()))
}
