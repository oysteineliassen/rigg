package no.oppdragsliste.app.sted

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import no.oppdragsliste.app.ui.harTillatelse

fun harPosisjon(context: Context): Boolean =
    harTillatelse(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
        harTillatelse(context, Manifest.permission.ACCESS_COARSE_LOCATION)

fun harBakgrunnsposisjon(context: Context): Boolean =
    harTillatelse(context, Manifest.permission.ACCESS_FINE_LOCATION) &&
        (Build.VERSION.SDK_INT < 29 || harTillatelse(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION))

/** Nåværende posisjon, eller siste kjente hvis den ikke kan bestemmes i løpet av 15 sekunder. */
@SuppressLint("MissingPermission")
suspend fun naverendePosisjon(context: Context): Pair<Double, Double>? {
    if (!harPosisjon(context)) return null
    val klient = LocationServices.getFusedLocationProviderClient(context)
    return try {
        val kilde = CancellationTokenSource()
        val pos = withTimeoutOrNull(15_000) {
            klient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, kilde.token).await()
        } ?: klient.lastLocation.await().also { kilde.cancel() }
        pos?.let { it.latitude to it.longitude }
    } catch (e: Exception) {
        null
    }
}
