package no.oppdragsliste.app.samtale

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat

data class Samtale(
    val id: Long,
    val nummer: String,
    val varighetSek: Long,
    val tidspunkt: Long,
    val type: Int,
    val lagretNavn: String?,
    /** Hvilket SIM-kort/telefonkonto samtalen gikk via (PhoneAccountHandle-id), null hvis ukjent. */
    val simId: String? = null,
) {
    /** Når samtalen ble avsluttet (ms). */
    val slutt: Long get() = tidspunkt + varighetSek * 1000
}

/**
 * Er dette en samtale det er verdt å spørre om? Besvart eller utgående, med nummer, lang nok, avsluttet
 * for mindre enn [vinduMin] minutter siden, og ikke spurt om før ([sistSpurt] er id-en til forrige).
 * [simKort]: bare samtaler via disse SIM-kortene (tomt = alle). Ukjent SIM regnes med.
 */
fun erAktuell(s: Samtale, minSekunder: Int, sistSpurt: Long, naMs: Long, vinduMin: Int = 10, simKort: Set<String> = emptySet()): Boolean =
    s.id != sistSpurt &&
        (simKort.isEmpty() || s.simId.isNullOrBlank() || s.simId in simKort) &&
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
        CallLog.Calls.DATE, CallLog.Calls.TYPE, CallLog.Calls.CACHED_NAME, CallLog.Calls.PHONE_ACCOUNT_ID,
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
                simId = c.getString(6)?.takeIf { it.isNotBlank() },
            )
        }
    } catch (e: SecurityException) {
        null
    }
}
