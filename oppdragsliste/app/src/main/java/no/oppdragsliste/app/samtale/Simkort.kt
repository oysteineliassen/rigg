package no.oppdragsliste.app.samtale

import android.annotation.SuppressLint
import android.content.Context
import android.telecom.TelecomManager

/** Et SIM-kort (telefonkonto) slik telefonen viser det, f.eks. «Jobb» eller «Telenor». */
data class Simkort(val id: String, val navn: String, val nummer: String?)

/**
 * SIM-kortene telefonen kan ringe med. Id-en er den samme som anropsloggen lagrer for hver samtale
 * (PHONE_ACCOUNT_ID). Krever READ_PHONE_STATE; tom liste uten tilgang.
 */
@SuppressLint("MissingPermission")
fun simkort(context: Context): List<Simkort> = try {
    val telecom = context.getSystemService(TelecomManager::class.java)
    telecom.callCapablePhoneAccounts.mapIndexed { i, handle ->
        val konto = telecom.getPhoneAccount(handle)
        Simkort(
            id = handle.id,
            navn = konto?.label?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${i + 1}",
            nummer = konto?.address?.schemeSpecificPart?.takeIf { it.isNotBlank() },
        )
    }
} catch (e: Exception) {
    emptyList()
}
