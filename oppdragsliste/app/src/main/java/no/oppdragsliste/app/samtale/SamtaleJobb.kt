package no.oppdragsliste.app.samtale

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import no.oppdragsliste.app.MainActivity
import no.oppdragsliste.app.app
import no.oppdragsliste.app.logikk.formaterTelefon
import no.oppdragsliste.app.varsler.Varsler

data class Samtale(val id: Long, val nummer: String, val varighetSek: Long, val tidspunkt: Long, val type: Int, val lagretNavn: String?)

/** Skal det spørres om nytt oppdrag etter denne samtalen? Ren funksjon, så den kan testes. */
fun skalVarsle(s: Samtale, minSekunder: Int, sisteVarslet: Long, naMs: Long): Boolean =
    s.id != sisteVarslet &&
        s.nummer.isNotBlank() &&
        (s.type == CallLog.Calls.INCOMING_TYPE || s.type == CallLog.Calls.OUTGOING_TYPE) &&
        s.varighetSek >= minSekunder &&
        // Bare samtaler som nettopp er avsluttet (starttid + varighet de siste 10 minuttene).
        naMs - (s.tidspunkt + s.varighetSek * 1000) < 10 * 60 * 1000

class SamtaleJobb(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val innstillinger = ctx.app.innstillinger
        val oppsett = innstillinger.na
        if (!oppsett.samtaleVarsel) return Result.success()
        val samtale = sisteSamtale(ctx) ?: return Result.success()
        if (!skalVarsle(samtale, oppsett.minSamtaleSekunder, innstillinger.sisteVarsledeSamtale, System.currentTimeMillis())) {
            return Result.success()
        }
        innstillinger.sisteVarsledeSamtale = samtale.id

        val navn = navnForNummer(ctx, samtale.nummer) ?: samtale.lagretNavn
        val pent = formaterTelefon(samtale.nummer)
        val tittel = if (navn != null) "Nytt oppdrag fra $navn?" else "Nytt oppdrag fra $pent?"
        val tekst = if (navn != null) "$pent · Trykk for å registrere" else "Trykk for å registrere"
        val varselId = Varsler.samtaleId(samtale.id)
        val apne = Varsler.apneApp(ctx, varselId) {
            action = MainActivity.HANDLING_NYTT
            putExtra(MainActivity.EKSTRA_TELEFON, pent)
            putExtra(MainActivity.EKSTRA_KONTAKT, navn.orEmpty())
            putExtra(MainActivity.EKSTRA_VARSEL, varselId)
        }
        Varsler.vis(ctx, varselId, Varsler.bygg(ctx, Varsler.KANAL_SAMTALE, tittel, tekst).setContentIntent(apne))
        return Result.success()
    }

    private fun sisteSamtale(ctx: Context): Samtale? {
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
}
