package no.oppdragsliste.app.kalender

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import no.oppdragsliste.app.app
import no.oppdragsliste.app.logikk.Kalenderhendelse
import no.oppdragsliste.app.logikk.Kalenderstand
import no.oppdragsliste.app.logikk.Kobling
import no.oppdragsliste.app.logikk.Synkhandling
import no.oppdragsliste.app.logikk.fraKalender
import no.oppdragsliste.app.logikk.kalenderhendelse
import no.oppdragsliste.app.logikk.velgSynkhandling
import no.oppdragsliste.app.ui.harTillatelse
import no.oppdragsliste.app.varsler.Planlegger
import java.util.concurrent.TimeUnit

data class KalenderValg(val id: Long, val navn: String, val konto: String, val google: Boolean)

/**
 * Toveis synk av avtaler med en kalender på telefonen via Androids kalenderleverandør. Er kalenderen
 * en Google-kalender, sørger telefonen selv for synken mot Google Kalender.
 *
 * Appen → kalender: nye, endrede og slettede avtaler. Kalender → appen: flyttet dato/klokkeslett.
 * Koblingene lagres i egne innstillinger («kalender»): oppdragsId -> [Kobling].
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

    private fun erPa(ctx: Context): Boolean {
        val o = ctx.app.innstillinger.na
        return o.kalenderSynk && o.kalenderId >= 0 && harTilgang(ctx)
    }

    // Synk kan startes fra appen og fra bakgrunnsjobben samtidig; én om gangen, så ingenting opprettes dobbelt.
    private val las = Mutex()

    suspend fun synk(context: Context) = las.withLock { synkEnGang(context.applicationContext) }

    private suspend fun synkEnGang(ctx: Context) = withContext(Dispatchers.IO) {
        overvak(ctx)
        if (!erPa(ctx)) return@withContext
        val oppsett = ctx.app.innstillinger.na
        val repo = ctx.app.repository
        val prefs = ctx.getSharedPreferences("kalender", Context.MODE_PRIVATE)
        val koblinger = prefs.all.mapNotNull { (nokkel, verdi) ->
            val id = nokkel.toLongOrNull() ?: return@mapNotNull null
            val k = (verdi as? String)?.let(Kobling::fraTekst) ?: return@mapNotNull null
            id to k
        }.toMap()

        val alle = repo.alleNa()
        val onsket = alle.mapNotNull { o -> kalenderhendelse(o, oppsett.kalenderVarighetMin)?.let { o to it } }
        val onsketIder = onsket.map { it.first.id }.toSet()
        val endringer = prefs.edit()
        var appEndret = false
        // Koblingene slik de blir etter denne synken (for å rydde bort hendelser ingen oppdrag eier).
        val endelig = koblinger.toMutableMap()
        fun lagre(id: Long, hendelse: Long, h: Kalenderhendelse, stand: Kalenderstand) {
            val ny = Kobling(hendelse, oppsett.kalenderId, h.signatur, stand.start, stand.heldag, stand.slutt)
            endringer.putString(id.toString(), ny.tilTekst())
            endelig[id] = ny
        }
        val valgt = oppsett.kalenderId
        try {
            // Hendelsene appen selv har laget (merket med pakkenavnet), uansett kalender.
            val egne = egneHendelser(ctx)
            // Fjern hendelser for oppdrag som er slettet, eller ikke lenger er avtaler med dato.
            koblinger.filterKeys { it !in onsketIder }.forEach { (id, k) ->
                slett(ctx, k.hendelse)
                endringer.remove(id.toString())
                endelig.remove(id)
            }
            onsket.forEach { (o, h) ->
                var k = koblinger[o.id]
                var stand = k?.takeIf { it.kalender == valgt }?.let { les(ctx, it.hendelse) }
                // Finnes ikke hendelsen under id-en vi husket (kalendersynken kan gi den ny id), eller mangler
                // koblingen: finn vår hendelse igjen i stedet for å lage en ny (det ga duplikater før).
                if (stand == null && (k == null || k.kalender == valgt)) {
                    // Andre oppdrags hendelser skal ikke tas.
                    val andres = koblinger.filterKeys { it != o.id }.values.map { it.hendelse }.toSet()
                    val ledige = egne.filter { it.kalender == valgt && it.id !in andres && (it.oppdragId == null || it.oppdragId == o.id) }
                    val funnet = ledige.firstOrNull { it.oppdragId == o.id }
                        ?: k?.start?.let { start ->
                            // Uten merke: samme starttid som sist, helst samme tittel.
                            val sammeStart = ledige.filter { it.start == start }
                            sammeStart.firstOrNull { it.tittel == h.tittel } ?: sammeStart.singleOrNull()
                        }
                    if (funnet != null) {
                        stand = les(ctx, funnet.id)
                        // Uten tidligere kobling: tom signatur, så hendelsen oppdateres med appens versjon.
                        k = (k ?: Kobling(funnet.id, valgt, "")).copy(hendelse = funnet.id)
                    }
                }
                when (velgSynkhandling(k, valgt, h.signatur, stand)) {
                    Synkhandling.OPPRETT -> settInn(ctx, valgt, h, o.id)?.let { lagre(o.id, it, h, Kalenderstand(h.start, h.heldag, h.slutt)) }
                    Synkhandling.FLYTT_KALENDER -> {
                        slett(ctx, k!!.hendelse)
                        settInn(ctx, valgt, h, o.id)?.let { lagre(o.id, it, h, Kalenderstand(h.start, h.heldag, h.slutt)) }
                    }
                    Synkhandling.OPPDATER_KALENDER -> {
                        oppdater(ctx, k!!.hendelse, h, o.id)
                        lagre(o.id, k.hendelse, h, Kalenderstand(h.start, h.heldag, h.slutt))
                    }
                    Synkhandling.OPPDATER_APP -> {
                        val fra = fraKalender(stand!!, k, haddeSluttid = o.sluttid != null)
                        val ny = o.copy(dato = fra.dato, tid = fra.tid, sluttid = fra.sluttid)
                        repo.oppdaterStille(ny)
                        appEndret = true
                        // Det appen nå ville skrevet, så neste synk ikke tror appen er endret.
                        val nyH = kalenderhendelse(ny, oppsett.kalenderVarighetMin) ?: return@forEach
                        lagre(o.id, k!!.hendelse, nyH, stand)
                    }
                    Synkhandling.HUSK_STAND -> lagre(o.id, k!!.hendelse, h, stand!!)
                    Synkhandling.INGEN -> if (k != null && k != koblinger[o.id]) {
                        // Funnet igjen under ny id: husk den nye id-en.
                        endringer.putString(o.id.toString(), k.tilTekst())
                        endelig[o.id] = k
                    }
                }
            }
            // Rydd: våre egne hendelser som ingen oppdrag eier (f.eks. duplikater fra før), slettes.
            val eid = endelig.values.map { it.hendelse }.toSet()
            egne.filter { it.id !in eid }.forEach { slett(ctx, it.id) }
        } catch (e: SecurityException) {
            // Tilgangen trukket tilbake underveis.
        } finally {
            endringer.apply()
        }
        // Avtaler flyttet i kalenderen: påminnelsene må flyttes også.
        if (appEndret) Planlegger.planleggAlle(ctx)
    }

    /**
     * Sørger for at appen får vite om endringer i kalenderen også når den er lukket: en jobb som
     * starter når kalenderdataene endres, pluss en sikkerhetsjobb hver time.
     */
    private fun overvak(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (!erPa(ctx)) {
            wm.cancelUniqueWork(ENDRING)
            wm.cancelUniqueWork(PERIODISK)
            return
        }
        wm.enqueueUniqueWork(ENDRING, ExistingWorkPolicy.KEEP, endringsjobb())
        wm.enqueueUniquePeriodicWork(
            PERIODISK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<KalenderJobb>(1, TimeUnit.HOURS).build(),
        )
    }

    internal const val ENDRING = "kalender-endring"
    private const val PERIODISK = "kalender-periodisk"

    internal const val ENDRING_MERKE = "kalender-endring"

    internal fun endringsjobb() = OneTimeWorkRequestBuilder<KalenderJobb>()
        .addTag(ENDRING_MERKE)
        .setConstraints(
            Constraints.Builder()
                .addContentUriTrigger(Events.CONTENT_URI, true)
                .setTriggerContentUpdateDelay(5, TimeUnit.SECONDS)
                .setTriggerContentMaxDelay(1, TimeUnit.MINUTES)
                .build(),
        )
        .build()

    private fun les(ctx: Context, id: Long): Kalenderstand? =
        ctx.contentResolver.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, id),
            arrayOf(Events.DTSTART, Events.ALL_DAY, Events.DELETED, Events.DTEND), null, null, null,
        )?.use { c ->
            if (c.moveToFirst() && c.getInt(2) == 0) {
                val start = c.getLong(0)
                Kalenderstand(start, c.getInt(1) == 1, if (c.isNull(3)) start else c.getLong(3))
            } else {
                null
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

    /** Merket på hendelsene appen lager, så de kan finnes igjen og knyttes til riktig oppdrag. */
    private const val MERKE = "oppdragsliste://oppdrag/"

    /** Siste linje i beskrivelsen på våre hendelser (se [kalenderhendelse]). */
    private const val SIGNATUR = "Fra Oppdragsliste"

    private fun merk(ctx: Context, oppdragId: Long) = ContentValues().apply {
        put(Events.CUSTOM_APP_PACKAGE, ctx.packageName)
        put(Events.CUSTOM_APP_URI, MERKE + oppdragId)
    }

    private fun settInn(ctx: Context, kalender: Long, h: Kalenderhendelse, oppdragId: Long): Long? {
        val v = verdier(h).apply {
            put(Events.CALENDAR_ID, kalender)
            putAll(merk(ctx, oppdragId))
        }
        return ctx.contentResolver.insert(Events.CONTENT_URI, v)?.let { ContentUris.parseId(it) }
    }

    private fun oppdater(ctx: Context, id: Long, h: Kalenderhendelse, oppdragId: Long) {
        // Merket settes også her, så hendelser laget av eldre versjoner får det.
        val v = verdier(h).apply { putAll(merk(ctx, oppdragId)) }
        ctx.contentResolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), v, null, null)
    }

    /** En hendelse appen har laget. [oppdragId] er null for hendelser fra eldre versjoner uten oppdragsmerke. */
    private data class EgenHendelse(val id: Long, val kalender: Long, val oppdragId: Long?, val tittel: String, val start: Long)

    private fun egneHendelser(ctx: Context): List<EgenHendelse> {
        val liste = mutableListOf<EgenHendelse>()
        ctx.contentResolver.query(
            Events.CONTENT_URI,
            arrayOf(Events._ID, Events.CALENDAR_ID, Events.CUSTOM_APP_URI, Events.TITLE, Events.DTSTART),
            // Merket med pakkenavnet, eller med «Fra Oppdragsliste» i beskrivelsen (beskrivelsen følger med når
            // Google synker hendelsen, det gjør ikke alltid pakkemerket).
            "(${Events.CUSTOM_APP_PACKAGE} = ? OR ${Events.DESCRIPTION} LIKE ?) AND ${Events.DELETED} = 0",
            arrayOf(ctx.packageName, "%$SIGNATUR"),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                liste += EgenHendelse(
                    id = c.getLong(0),
                    kalender = c.getLong(1),
                    oppdragId = c.getString(2)?.takeIf { it.startsWith(MERKE) }?.removePrefix(MERKE)?.toLongOrNull(),
                    tittel = c.getString(3).orEmpty(),
                    start = c.getLong(4),
                )
            }
        }
        return liste
    }

    private fun slett(ctx: Context, id: Long) {
        ctx.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null)
    }
}

/** Synker med kalenderen i bakgrunnen, og venter deretter på neste endring i kalenderen. */
class KalenderJobb(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Kalender.synk(applicationContext)
        // Endringsjobben er engangs; still opp en ny som venter på neste endring (etter denne).
        if (Kalender.ENDRING_MERKE in tags) {
            WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork(Kalender.ENDRING, ExistingWorkPolicy.APPEND_OR_REPLACE, Kalender.endringsjobb())
        }
        return Result.success()
    }
}
