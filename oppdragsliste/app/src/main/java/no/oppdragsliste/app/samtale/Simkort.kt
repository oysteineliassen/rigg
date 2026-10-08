package no.oppdragsliste.app.samtale

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CallLog
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * Et SIM-kort slik telefonen viser det. [ider] er alle måtene SIM-kortet kan stå oppført i anropsloggen
 * (telefonkonto-id, abonnements-id, ICCID – produsentene gjør det ulikt).
 */
data class Simkort(val ider: Set<String>, val navn: String, val nummer: String?, val fraLogg: Boolean = false) {
    val nokkel: String get() = ider.minOrNull().orEmpty()
}

/**
 * SIM-kortene på telefonen, fra tre kilder: telefonkontoene (TelecomManager), aktive abonnementer
 * (SubscriptionManager) og hvilke SIM-kort som faktisk står i anropsloggen. Krever READ_PHONE_STATE
 * og READ_CALL_LOG; mangler noe, hoppes den kilden over.
 */
@SuppressLint("MissingPermission")
fun simkort(context: Context): List<Simkort> {
    val liste = mutableListOf<Simkort>()
    fun dekket(id: String) = liste.any { id in it.ider }

    // 1. Telefonkontoer, koblet til abonnements-id der det går (Android 11+).
    try {
        val telecom = context.getSystemService(TelecomManager::class.java)
        val telefoni = context.getSystemService(TelephonyManager::class.java)
        telecom.callCapablePhoneAccounts.forEach { handle ->
            val konto = telecom.getPhoneAccount(handle)
            val ider = mutableSetOf(handle.id)
            if (Build.VERSION.SDK_INT >= 30) {
                runCatching { telefoni.getSubscriptionId(handle) }.getOrNull()
                    ?.takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }?.let { ider += it.toString() }
            }
            liste += Simkort(
                ider = ider,
                navn = konto?.label?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${liste.size + 1}",
                nummer = konto?.address?.schemeSpecificPart?.takeIf { it.isNotBlank() },
            )
        }
    } catch (e: Exception) {
        // Ingen tilgang eller ikke støttet: prøv de andre kildene.
    }

    // 2. Aktive abonnementer (SIM-kort og eSIM).
    try {
        context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList?.forEach { info ->
            @Suppress("DEPRECATION")
            val ider = setOfNotNull(info.subscriptionId.toString(), runCatching { info.iccId }.getOrNull()?.takeIf { it.isNotBlank() })
            val eksisterende = liste.indexOfFirst { k -> ider.any { it in k.ider } }
            if (eksisterende >= 0) {
                liste[eksisterende] = liste[eksisterende].let { it.copy(ider = it.ider + ider) }
            } else {
                @Suppress("DEPRECATION")
                val nr = runCatching { info.number }.getOrNull()?.takeIf { it.isNotBlank() }
                liste += Simkort(ider, info.displayName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${info.simSlotIndex + 1}", nr)
            }
        }
    } catch (e: Exception) {
        // Som over.
    }

    // 3. SIM-kort som står i anropsloggen men ikke ble funnet over.
    idierIAnropsloggen(context).forEach { id ->
        if (!dekket(id)) liste += Simkort(setOf(id), "SIM-kort ${liste.size + 1}", null, fraLogg = true)
    }
    return liste
}

/** Ulike SIM-id-er (PHONE_ACCOUNT_ID) blant de siste samtalene i anropsloggen. */
private fun idierIAnropsloggen(context: Context): Set<String> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
        return emptySet()
    }
    val ider = linkedSetOf<String>()
    try {
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.PHONE_ACCOUNT_ID), null, null, "${CallLog.Calls.DATE} DESC",
        )?.use { c ->
            var n = 0
            while (c.moveToNext() && n++ < 500) c.getString(0)?.takeIf { it.isNotBlank() }?.let { ider += it }
        }
    } catch (e: Exception) {
        // Ingen tilgang.
    }
    return ider
}
