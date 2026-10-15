package no.oppdragsliste.app.sted

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.logikk.norsk

/**
 * Finner koordinater for adresse og sted. Alle feil (ingen nett, ingen treff, ingen geokoder på
 * telefonen) gir null: oppdraget lagres uansett, bare uten koordinater.
 */
suspend fun geokod(context: Context, o: Oppdrag): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext null
    val geocoder = Geocoder(context, norsk)
    val forsok = listOf(
        listOf(o.adresse, o.sted).filter { it.isNotBlank() }.joinToString(", "),
        o.sted,
    ).map { it.trim() }.filter { it.isNotBlank() }.distinct()
    for (sporring in forsok) {
        val treff = try {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName("$sporring, Norge", 1)?.firstOrNull()
        } catch (e: Exception) {
            null
        }
        if (treff != null && treff.hasLatitude() && treff.hasLongitude()) return@withContext treff.latitude to treff.longitude
    }
    null
}
