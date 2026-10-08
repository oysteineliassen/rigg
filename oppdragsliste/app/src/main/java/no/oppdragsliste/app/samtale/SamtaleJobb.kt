package no.oppdragsliste.app.samtale

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import no.oppdragsliste.app.MainActivity
import no.oppdragsliste.app.app
import no.oppdragsliste.app.logikk.formaterTelefon
import no.oppdragsliste.app.logikk.kandidaterForSamtale
import no.oppdragsliste.app.varsler.Varsler

/** Viser varselet «Registrere oppdrag fra samtalen med …?» rett etter en samtale. */
class SamtaleJobb(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val innstillinger = ctx.app.innstillinger
        val oppsett = innstillinger.na
        if (!oppsett.samtaleVarsel || !oppsett.samtaleModus.varsel) return Result.success()
        val samtale = sisteSamtale(ctx) ?: return Result.success()
        if (!erAktuell(samtale, oppsett.minSamtaleSekunder, innstillinger.sisteVarsledeSamtale, System.currentTimeMillis(), simKort = oppsett.samtaleSim)) {
            return Result.success()
        }
        innstillinger.sisteVarsledeSamtale = samtale.id

        val navn = navnForNummer(ctx, samtale.nummer) ?: samtale.lagretNavn
        val pent = formaterTelefon(samtale.nummer)
        val kjent = kandidaterForSamtale(ctx.app.repository.kjente.first(), samtale.nummer, navn).firstOrNull()
        val hvem = navn ?: pent
        val tittel = "Registrere oppdrag fra samtalen med $hvem?"
        val tekst = listOfNotNull(
            if (navn != null) pent else null,
            kjent?.let { listOf(it.kunde, it.anlegg).filter { s -> s.isNotBlank() }.joinToString(" · ") },
            "Trykk for å registrere",
        ).joinToString(" · ")
        val varselId = Varsler.samtaleId(samtale.id)
        val apne = Varsler.apneApp(ctx, varselId) {
            action = MainActivity.HANDLING_NYTT
            putExtra(MainActivity.EKSTRA_TELEFON, pent)
            putExtra(MainActivity.EKSTRA_KONTAKT, navn.orEmpty())
            putExtra(MainActivity.EKSTRA_VARSEL, varselId)
            putExtra(MainActivity.EKSTRA_SAMTALE, samtale.id)
        }
        Varsler.vis(ctx, varselId, Varsler.bygg(ctx, Varsler.KANAL_SAMTALE, tittel, tekst).setContentIntent(apne))
        return Result.success()
    }
}
