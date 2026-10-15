package no.oppdragsliste.app.samtale

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** Slår opp navnet på et nummer i kontaktene. Null hvis det ikke finnes eller tillatelse mangler. */
fun navnForNummer(context: Context, nummer: String): String? {
    if (nummer.isBlank()) return null
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return null
    }
    val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(nummer))
    return try {
        context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Leser navn og nummer fra et telefonnummer valgt i telefonens kontaktvelger (ACTION_PICK på
 * Phone.CONTENT_URI). Velgeren gir lesetilgang til akkurat den raden, så READ_CONTACTS trengs ikke.
 */
fun lesValgtKontakt(context: Context, uri: Uri): Pair<String, String>? = try {
    context.contentResolver.query(
        uri,
        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
        null, null, null,
    )?.use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() to c.getString(1).orEmpty() else null }
} catch (e: Exception) {
    null
}
