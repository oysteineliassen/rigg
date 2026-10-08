package no.oppdragsliste.app.samtale

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat

data class Samtale(val id: Long, val nummer: String, val varighetSek: Long, val tidspunkt: Long, val type: Int, val lagretNavn: String?) {
    /** Når samtalen ble avsluttet (ms). */
    val slutt: Long get() = tidspunkt + varighetSek * 1000
}

/**
 * Er dette en samtale det er verdt å spørre om? Besvart eller utgående, med nummer, lang nok, avsluttet
 * for mindre enn [vinduMin] minutter siden, og ikke spurt om før ([sistSpurt] er id-en til forrige).
 */
fun erAktuell(s: Samtale, minSekunder: Int, sistSpurt: Long, naMs: Long, vinduMin: Int = 10): Boolean =
    s.id != sistSpurt &&
        s.nummer.isNotBlank() &&
        (s.type == CallLog.Calls.INCOMING_TYPE || s.type == CallLog.Calls.OUTGOING_TYPE) &&
        s.varighetSek >= minSekunder &&
        naMs - s.slutt < vinduMin * 60_000L

/** Siste samtale i anropsloggen, eller null uten tilgang. */
fun sisteSamtale(ctx: Context): Samtale? {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
        return null
    }
    val kolonner = arrayOf(
        CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.DURATION,
        CallLog.Calls.DATE, CallLog.Calls.TYPE, CallLog.Calls.CACHED_NAME,
    )
    return try {
        ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, kolonner, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
            if (!c.moveToFirst()) return null
            Samtale(
                id = c.getLong(0),
                nummer = c.getString(1).orEmpty(),
                varighetSek = c.getLong(2),
                tidspunkt = c.getLong(3),
                type = c.getInt(4),
                lagretNavn = c.getString(5)?.takeIf { it.isNotBlank() },
            )
        }
    } catch (e: SecurityException) {
        null
    }
}
