package no.oppdragsliste.app.sted

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await
import no.oppdragsliste.app.MainActivity
import no.oppdragsliste.app.app
import no.oppdragsliste.app.logikk.naerSomSkalVarsles
import no.oppdragsliste.app.logikk.naerTekst
import no.oppdragsliste.app.logikk.velgGeofencer
import no.oppdragsliste.app.varsler.Varsler
import no.oppdragsliste.app.varsler.asynkront
import java.time.LocalDate

/** Registrerer geofencer rundt åpne oppdrag med koordinater (maks 100, viktigste og eldste først). */
object Geofencer {
    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, GeofenceMottaker::class.java),
        // Geofence-tjenesten fyller inn data i intenten, så den må være muterbar.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    /**
     * Synkroniserer geofencene med oppdragene. Uten [tving] gjøres ingenting hvis settet er uendret
     * siden sist (geofencer forsvinner ved omstart, så da tvinges det).
     */
    @SuppressLint("MissingPermission")
    suspend fun synk(context: Context, tving: Boolean = false) {
        val ctx = context.applicationContext
        val oppsett = ctx.app.innstillinger.na
        val prefs = ctx.getSharedPreferences("geofencer", Context.MODE_PRIVATE)
        val klient = LocationServices.getGeofencingClient(ctx)

        val valgte = if (oppsett.naerVarsel && harBakgrunnsposisjon(ctx)) velgGeofencer(ctx.app.repository.apne()) else emptyList()
        val signatur = valgte.joinToString(";") { "${it.id}:${it.lat}:${it.lng}" } + "|" + oppsett.naerRadiusMeter
        if (!tving && prefs.getString("signatur", null) == signatur) return

        try {
            klient.removeGeofences(pendingIntent(ctx)).await()
            if (valgte.isNotEmpty()) {
                val geofencer = valgte.map { o ->
                    Geofence.Builder()
                        .setRequestId(o.id.toString())
                        .setCircularRegion(o.lat!!, o.lng!!, oppsett.naerRadiusMeter.toFloat())
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                        .setNotificationResponsiveness(60_000)
                        .build()
                }
                val foresporsel = GeofencingRequest.Builder()
                    // Ikke varsle med en gang for steder vi allerede er på (f.eks. når et oppdrag legges inn hos kunden).
                    .setInitialTrigger(0)
                    .addGeofences(geofencer)
                    .build()
                klient.addGeofences(foresporsel, pendingIntent(ctx)).await()
            }
            prefs.edit().putString("signatur", signatur).apply()
        } catch (e: Exception) {
            // F.eks. posisjon slått av på telefonen. Prøves igjen ved neste endring eller oppstart.
            prefs.edit().remove("signatur").apply()
        }
    }
}

/** Mottar «inn i området» fra geofencene og viser ett samlet varsel for oppdrag i nærheten. */
class GeofenceMottaker : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hendelse = GeofencingEvent.fromIntent(intent) ?: return
        if (hendelse.hasError() || hendelse.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val posisjon = hendelse.triggeringLocation
        val utlosteIder = hendelse.triggeringGeofences.orEmpty().mapNotNull { it.requestId.toLongOrNull() }.toSet()
        val ctx = context.applicationContext
        asynkront {
            val oppsett = ctx.app.innstillinger.na
            if (!oppsett.naerVarsel) return@asynkront
            val repo = ctx.app.repository
            val apne = repo.apne()
            val idag = LocalDate.now()
            val naer = if (posisjon != null) {
                naerSomSkalVarsles(apne, posisjon.latitude, posisjon.longitude, oppsett.naerRadiusMeter, idag)
            } else {
                apne.filter { it.id in utlosteIder && it.naerVarslet != idag }
            }
            if (naer.isEmpty()) return@asynkront
            naer.forEach { repo.oppdaterStille(it.copy(naerVarslet = idag)) }

            val (tittel, tekst) = naerTekst(naer)
            val trykk = Varsler.apneApp(ctx, Varsler.ID_NAER) { action = MainActivity.HANDLING_NAER }
            Varsler.vis(ctx, Varsler.ID_NAER, Varsler.bygg(ctx, Varsler.KANAL_NAER, tittel, tekst).setContentIntent(trykk))
        }
    }
}
