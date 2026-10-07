package no.oppdragsliste.app.kalender

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.oppdragsliste.app.app
import no.oppdragsliste.app.logikk.Kalenderhendelse
import no.oppdragsliste.app.logikk.kalenderhendelse
import no.oppdragsliste.app.ui.harTillatelse

data class KalenderValg(val id: Long, val navn: String, val konto: String, val google: Boolean)

/**
 * Enveis synk av avtaler til en kalender på telefonen via Androids kalenderleverandør. Er kalenderen
 * en Google-kalender, sørger telefonen selv for at den synkes til Google Kalender.
 *
 * Hvilken hendelse som hører til hvilket oppdrag, lagres i egne innstillinger («kalender»):
 * oppdragsId -> «hendelsesId:kalenderId:signatur».
 */
object Kalender {
    private val tillatelser = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun harTilgang(context: Context) = tillatelser.all { harTillatelse(context, it) }
    fun tillatelser() = tillatelser

    /** Kalendere vi kan skrive til, Google-kalendere først. */
    suspend fun kalendere(context: Context): List<KalenderValg> = withContext(Dispatchers.IO) {
        if (!harTilgang(context)) return@withContext emptyList()
        val kolonner = arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE)
        val utvalg = "${Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${Calendars.VISIBLE} = 1"
        val liste = mutableListOf<KalenderValg>()
        try {
            context.contentResolver.query(
                Calendars.CONTENT_URI, kolonner, utvalg, arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    liste += KalenderValg(
                        id = c.getLong(0),
                        navn = c.getString(1).orEmpty(),
                        konto = c.getString(2).orEmpty(),
                        google = c.getString(3) == "com.google",
                    )
                }
            }
        } catch (e: SecurityException) {
            return@withContext emptyList()
        }
        liste.sortedWith(compareBy<KalenderValg> { !it.google }.thenBy { it.konto }.thenBy { it.navn })
    }

    private data class Kobling(val hendelse: Long, val kalender: Long, val signatur: String)

    suspend fun synk(context: Context) = withContext(Dispatchers.IO) {
        val ctx = context.applicationContext
        val oppsett = ctx.app.innstillinger.na
        if (!oppsett.kalenderSynk || oppsett.kalenderId < 0 || !harTilgang(ctx)) return@withContext
        val prefs = ctx.getSharedPreferences("kalender", Context.MODE_PRIVATE)
        val koblinger = prefs.all.mapNotNull { (nokkel, verdi) ->
            val deler = (verdi as? String)?.split(":") ?: return@mapNotNull null
            val id = nokkel.toLongOrNull() ?: return@mapNotNull null
            if (deler.size != 3) return@mapNotNull null
            id to Kobling(deler[0].toLong(), deler[1].toLong(), deler[2])
        }.toMap()

        val onsket = ctx.app.repository.alleNa()
            .mapNotNull { o -> kalenderhendelse(o, oppsett.kalenderVarighetMin)?.let { o.id to it } }
            .toMap()
        val endringer = prefs.edit()
        try {
            // Fjern hendelser for oppdrag som er slettet, eller ikke lenger er avtaler med dato.
            koblinger.filterKeys { it !in onsket }.forEach { (id, k) ->
                slett(ctx, k.hendelse)
                endringer.remove(id.toString())
            }
            onsket.forEach { (id, h) ->
                val k = koblinger[id]
                val ny = when {
                    k == null -> settInn(ctx, oppsett.kalenderId, h)
                    k.kalender != oppsett.kalenderId -> { // byttet kalender: flytt
                        slett(ctx, k.hendelse)
                        settInn(ctx, oppsett.kalenderId, h)
                    }
                    k.signatur == h.signatur -> return@forEach // uendret (eller slettet i kalenderen: la være)
                    finnes(ctx, k.hendelse) -> k.hendelse.also { oppdater(ctx, it, h) }
                    else -> settInn(ctx, oppsett.kalenderId, h)
                } ?: return@forEach
                endringer.putString(id.toString(), "$ny:${oppsett.kalenderId}:${h.signatur}")
            }
        } catch (e: SecurityException) {
            // Tilgangen trukket tilbake underveis.
        } finally {
            endringer.apply()
        }
    }

    private fun verdier(h: Kalenderhendelse) = ContentValues().apply {
        put(Events.TITLE, h.tittel)
        put(Events.EVENT_LOCATION, h.sted)
        put(Events.DESCRIPTION, h.beskrivelse)
        put(Events.DTSTART, h.start)
        put(Events.DTEND, h.slutt)
        put(Events.ALL_DAY, if (h.heldag) 1 else 0)
        put(Events.EVENT_TIMEZONE, h.tidssone)
    }

    private fun settInn(ctx: Context, kalender: Long, h: Kalenderhendelse): Long? {
        val v = verdier(h).apply {
            put(Events.CALENDAR_ID, kalender)
            put(Events.CUSTOM_APP_PACKAGE, ctx.packageName)
            put(Events.CUSTOM_APP_URI, "oppdragsliste://avtale")
        }
        return ctx.contentResolver.insert(Events.CONTENT_URI, v)?.let { ContentUris.parseId(it) }
    }

    private fun oppdater(ctx: Context, id: Long, h: Kalenderhendelse) {
        ctx.contentResolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), verdier(h), null, null)
    }

    private fun slett(ctx: Context, id: Long) {
        ctx.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null)
    }

    private fun finnes(ctx: Context, id: Long): Boolean =
        ctx.contentResolver.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, id), arrayOf(Events._ID, Events.DELETED), null, null, null,
        )?.use { c -> c.moveToFirst() && c.getInt(1) == 0 } ?: false
}
