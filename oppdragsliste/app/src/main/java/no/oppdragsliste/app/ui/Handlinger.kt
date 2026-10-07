package no.oppdragsliste.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import no.oppdragsliste.app.data.Oppdrag

fun ring(context: Context, telefon: String) {
    val nummer = telefon.filter { it.isDigit() || it == '+' }
    start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(nummer))), "Fant ingen telefonapp")
}

/** Åpner kartappen på koordinatene hvis vi har dem, ellers som søk på adresse og sted. */
fun visIKart(context: Context, o: Oppdrag) {
    val navn = Uri.encode(o.tittel.ifBlank { o.stedTekst })
    val uri = if (o.lat != null && o.lng != null) {
        Uri.parse("geo:${o.lat},${o.lng}?q=${o.lat},${o.lng}($navn)")
    } else {
        Uri.parse("geo:0,0?q=" + Uri.encode(o.stedTekst))
    }
    start(context, Intent(Intent.ACTION_VIEW, uri), "Fant ingen kartapp")
}

private fun start(context: Context, intent: Intent, feilmelding: String) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, feilmelding, Toast.LENGTH_SHORT).show()
    }
}
