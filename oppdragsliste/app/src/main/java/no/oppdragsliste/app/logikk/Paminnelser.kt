package no.oppdragsliste.app.logikk

import no.oppdragsliste.app.data.Paminnelse
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle

/** Åpne påminnelser: de med tidspunkt først (tidligst øverst), så de uten, eldste først. */
fun sorterPaminnelser(liste: List<Paminnelse>): List<Paminnelse> =
    liste.filter { !it.ferdig }.sortedWith(
        compareBy<Paminnelse, LocalDateTime?>(nullsLast()) { it.tidspunkt }.thenBy { it.opprettet },
    )

/** Søk i påminnelsesteksten (og tittelen på oppdraget den er koblet til). */
fun filtrerPaminnelser(liste: List<Paminnelse>, sok: String, oppdragTittel: (Long) -> String?): List<Paminnelse> {
    val ord = sok.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (ord.isEmpty()) return liste
    return liste.filter { p ->
        val tekst = (p.tekst + " " + (p.oppdragId?.let(oppdragTittel) ?: "")).lowercase()
        ord.all { tekst.contains(it) }
    }
}

fun erForfalt(p: Paminnelse, na: LocalDateTime): Boolean =
    !p.ferdig && p.tidspunkt?.let { !it.isAfter(na) } == true

/** Hurtigvalg for tidspunkt: «Om 1 time», «I kveld 18:00», «I morgen 07:00», «Mandag 07:00». */
fun hurtigvalg(na: LocalDateTime): List<Pair<String, LocalDateTime>> = buildList {
    // Om en time, rundet opp til nærmeste kvarter.
    val omTime = na.plusHours(1).withSecond(0).withNano(0).let { t ->
        val rest = t.minute % 15
        if (rest == 0) t else t.plusMinutes((15 - rest).toLong())
    }
    add("Om 1 time" to omTime)
    val kveld = LocalDateTime.of(na.toLocalDate(), LocalTime.of(18, 0))
    if (na.isBefore(kveld.minusHours(1))) add("I kveld 18:00" to kveld)
    val imorgen = na.toLocalDate().plusDays(1)
    add("I morgen 07:00" to LocalDateTime.of(imorgen, LocalTime.of(7, 0)))
    // Til helgen: neste mandag.
    if (na.dayOfWeek in setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
        var d: LocalDate = imorgen
        while (d.dayOfWeek != DayOfWeek.MONDAY) d = d.plusDays(1)
        if (d != imorgen) add("Mandag 07:00" to LocalDateTime.of(d, LocalTime.of(7, 0)))
    }
}

/** «Om 1 time» etter et utsett: nå + 1 time, til hele minutter. */
fun utsatt(na: LocalDateTime, minutter: Long = 60): LocalDateTime = na.plusMinutes(minutter).withSecond(0).withNano(0)

/** Ukedag med stor forbokstav, f.eks. «Mandag». */
fun ukedag(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.FULL, norsk).replaceFirstChar { it.uppercase() }
