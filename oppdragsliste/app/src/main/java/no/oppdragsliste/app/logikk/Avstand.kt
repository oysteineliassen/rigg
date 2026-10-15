package no.oppdragsliste.app.logikk

import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Prioritet
import java.time.LocalDate
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Android tillater maks 100 geofencer per app. */
const val MAKS_GEOFENCER = 100

/** Avstand i meter mellom to punkter (haversine). */
fun avstandMeter(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return (2 * r * asin(sqrt(a))).toFloat()
}

fun Oppdrag.avstandTil(lat: Double, lng: Double): Float? =
    if (this.lat != null && this.lng != null) avstandMeter(lat, lng, this.lat, this.lng) else null

/** Rekkefølge for hvilke oppdrag som får geofence når det er flere enn plass til. */
private fun Prioritet.geofenceRang(): Int = when (this) {
    Prioritet.AKUTT -> 0
    Prioritet.HASTER -> 1
    Prioritet.OMRADE -> 2
    Prioritet.AVTALE -> 3 // har fast tid uansett
}

/** Åpne oppdrag med koordinater, de viktigste og eldste først, maks [maks]. */
fun velgGeofencer(oppdrag: List<Oppdrag>, maks: Int = MAKS_GEOFENCER): List<Oppdrag> =
    oppdrag.filter { !it.ferdig && it.harKoordinater }
        .sortedWith(compareBy<Oppdrag> { it.prioritet.geofenceRang() }.thenBy { it.opprettet }.thenBy { it.id })
        .take(maks)

/** Åpne oppdrag sortert på avstand fra (lat, lng). Oppdrag uten koordinater kommer sist, eldste først. */
fun sorterPaAvstand(oppdrag: List<Oppdrag>, lat: Double, lng: Double): List<Pair<Oppdrag, Float?>> {
    val apne = oppdrag.filter { !it.ferdig }.map { it to it.avstandTil(lat, lng) }
    val med = apne.filter { it.second != null }.sortedBy { it.second }
    val uten = apne.filter { it.second == null }.sortedWith(compareBy { it.first.opprettet })
    return med + uten
}

/** Oppdrag innenfor [radius] meter som ikke allerede er varslet i dag. */
fun naerSomSkalVarsles(oppdrag: List<Oppdrag>, lat: Double, lng: Double, radius: Int, idag: LocalDate): List<Oppdrag> =
    oppdrag.filter { o ->
        !o.ferdig && o.naerVarslet != idag && (o.avstandTil(lat, lng)?.let { it <= radius } ?: false)
    }

/** Tittel og tekst: «3 oppdrag i nærheten» og «Søgne (2), Tangvall (1)». */
fun naerTekst(oppdrag: List<Oppdrag>): Pair<String, String> {
    val tittel = "${oppdrag.size} oppdrag i nærheten"
    val tekst = oppdrag.groupBy { it.sted.trim().ifBlank { it.adresse.trim().ifBlank { "Uten sted" } } }
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, List<Oppdrag>>> { it.value.size }.thenBy { it.key })
        .joinToString(", ") { "${it.key} (${it.value.size})" }
    return tittel to tekst
}
