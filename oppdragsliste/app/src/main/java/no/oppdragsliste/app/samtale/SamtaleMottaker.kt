package no.oppdragsliste.app.samtale

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import no.oppdragsliste.app.app
import java.util.concurrent.TimeUnit

/**
 * Får beskjed om endringer i telefonstatus. Når en samtale er over (status IDLE), starter vi en jobb
 * som leser anropsloggen litt etterpå. Loggen skrives først rett etter at samtalen er lagt på.
 */
class SamtaleMottaker : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        if (intent.getStringExtra(TelephonyManager.EXTRA_STATE) != TelephonyManager.EXTRA_STATE_IDLE) return
        val oppsett = context.app.innstillinger.na
        if (!oppsett.samtaleVarsel || !oppsett.samtaleModus.varsel) return

        val jobb = OneTimeWorkRequestBuilder<SamtaleJobb>()
            .setInitialDelay(3, TimeUnit.SECONDS)
            .build()
        // Android sender ofte samme statusendring to ganger; REPLACE gjør at bare én jobb kjører.
        WorkManager.getInstance(context).enqueueUniqueWork("etter-samtale", ExistingWorkPolicy.REPLACE, jobb)
    }
}
