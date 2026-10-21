package no.oppdragsliste.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * «Samtale med Kari Nordmann – vil du registrere et oppdrag fra samtalen?» Er kontakten registrert på
 * kunde/anlegg før, kan riktig valg velges (det sist brukte er forhåndsvalgt).
 */
@Composable
fun SamtaleSporsmalDialog(sp: SamtaleSporsmal, onSvar: (registrer: Boolean, kandidat: no.oppdragsliste.app.data.KjentAnlegg?) -> Unit) {
    // Indeks i kandidatlisten; -1 = ny kunde / fyll inn selv.
    var valgt by remember(sp.samtaleId) { mutableIntStateOf(if (sp.kandidater.isEmpty()) -1 else 0) }
    val hvem = sp.navn ?: sp.telefon
    val nar = when (sp.minutterSiden) {
        0L -> "nå nettopp"
        1L -> "for 1 minutt siden"
        else -> "for ${sp.minutterSiden} minutter siden"
    }
    AlertDialog(
        onDismissRequest = { onSvar(false, null) },
        icon = { Icon(Icons.Outlined.Call, null) },
        title = { Text("Samtale med $hvem") },
        text = {
            Column {
                Text(
                    (if (sp.navn != null) "${sp.telefon} · " else "") + "avsluttet $nar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text("Vil du registrere et oppdrag fra samtalen?", style = MaterialTheme.typography.bodyLarge)
                if (sp.kandidater.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (sp.kandidater.size == 1) "Registrert på før:" else "Registrert på før – hvilken gjelder det?",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    sp.kandidater.forEachIndexed { i, k ->
                        Valg(valgt == i, { valgt = i }) {
                            Text(k.kunde.ifBlank { "(uten kunde)" }, fontWeight = FontWeight.SemiBold)
                            val under = listOf(k.anlegg, k.stedTekst).filter { it.isNotBlank() }.joinToString(" · ")
                            if (under.isNotBlank()) {
                                Text(under, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Valg(valgt == -1, { valgt = -1 }) { Text("Ny kunde eller annet anlegg") }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSvar(true, sp.kandidater.getOrNull(valgt)) }) { Text("Registrer oppdrag") }
        },
        dismissButton = { TextButton(onClick = { onSvar(false, null) }) { Text("Ikke nå") } },
    )
}

@Composable
private fun Valg(valgt: Boolean, onVelg: () -> Unit, innhold: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = valgt, onClick = onVelg)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = valgt, onClick = null, modifier = Modifier.size(40.dp))
        Column(Modifier.padding(start = 4.dp)) { innhold() }
    }
}
