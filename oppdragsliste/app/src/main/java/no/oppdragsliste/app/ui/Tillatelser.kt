package no.oppdragsliste.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

fun harTillatelse(context: Context, tillatelse: String): Boolean =
    ContextCompat.checkSelfPermission(context, tillatelse) == PackageManager.PERMISSION_GRANTED

/**
 * Teller som øker hver gang skjermen kommer tilbake i forgrunnen, så tillatelser sjekkes på nytt
 * etter at brukeren har vært innom systeminnstillingene.
 */
@Composable
fun gjenopptattTeller(): Int {
    var teller by remember { mutableIntStateOf(0) }
    val eier = LocalLifecycleOwner.current
    DisposableEffect(eier) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) teller++ }
        eier.lifecycle.addObserver(observer)
        onDispose { eier.lifecycle.removeObserver(observer) }
    }
    return teller
}

fun apneAppInnstillinger(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@Composable
fun TillatelseLinje(navn: String, gitt: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (gitt) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (gitt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(navn + if (gitt) "" else " – ikke gitt", style = MaterialTheme.typography.bodyMedium)
    }
}
