package no.oppdragsliste.app.logikk

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val norsk: Locale = Locale.forLanguageTag("nb-NO")
private val datoFormat = DateTimeFormatter.ofPattern("EEE d. MMM", norsk)
private val datoMedArFormat = DateTimeFormatter.ofPattern("EEE d. MMM yyyy", norsk)
private val tidFormat = DateTimeFormatter.ofPattern("HH:mm", norsk)

fun formaterTid(t: LocalTime): String = t.format(tidFormat)

/** «I dag», «I morgen», «I går» eller «tir. 8. okt.». */
fun formaterDato(d: LocalDate, idag: LocalDate): String = when (d) {
    idag -> "I dag"
    idag.plusDays(1) -> "I morgen"
    idag.minusDays(1) -> "I går"
    else -> d.format(if (d.year == idag.year) datoFormat else datoMedArFormat)
}

fun formaterDatoTid(d: LocalDate?, t: LocalTime?, idag: LocalDate): String? {
    if (d == null) return null
    return formaterDato(d, idag) + (t?.let { " kl. " + formaterTid(it) } ?: "")
}

/**
 * Norske nummer: mobil (4xx/9xx) som «900 00 000», fasttelefon som «38 12 34 56».
 * Landskode +47/0047 fjernes. Andre nummer returneres som de er.
 */
fun formaterTelefon(nummer: String): String {
    val renset = nummer.filter { it.isDigit() || it == '+' }
    val lokal = when {
        renset.startsWith("+47") -> renset.removePrefix("+47")
        renset.startsWith("0047") -> renset.removePrefix("0047")
        else -> renset
    }
    if (lokal.length != 8 || !lokal.all { it.isDigit() }) return nummer.trim()
    return if (lokal[0] == '4' || lokal[0] == '9') {
        "${lokal.substring(0, 3)} ${lokal.substring(3, 5)} ${lokal.substring(5)}"
    } else {
        lokal.chunked(2).joinToString(" ")
    }
}
