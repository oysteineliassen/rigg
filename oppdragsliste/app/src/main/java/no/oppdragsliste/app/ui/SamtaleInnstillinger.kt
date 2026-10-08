package no.oppdragsliste.app.ui

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import no.oppdragsliste.app.R
import no.oppdragsliste.app.data.Oppsett
import no.oppdragsliste.app.data.SamtaleModus
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import no.oppdragsliste.app.samtale.NyttOppdragFlis

private val samtaleTillatelser = buildList {
    add(Manifest.permission.READ_PHONE_STATE)
    add(Manifest.permission.READ_CALL_LOG)
    add(Manifest.permission.READ_CONTACTS)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}

@Composable
fun SamtaleInnstillinger(oppsett: Oppsett, onEndre: ((Oppsett) -> Oppsett) -> Unit) {
    val context = LocalContext.current
    val teller = gjenopptattTeller()
    var avslatt by remember { mutableStateOf(false) }
    // Leses på nytt ved hver gjenopptakelse (teller) og etter svar på forespørselen (avslatt).
    val status = remember(teller, avslatt) { samtaleTillatelser.associateWith { harTillatelse(context, it) } }
    val nodvendigGitt = status[Manifest.permission.READ_PHONE_STATE] == true &&
        status[Manifest.permission.READ_CALL_LOG] == true

    val be = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { svar ->
        val ok = (svar[Manifest.permission.READ_PHONE_STATE] ?: harTillatelse(context, Manifest.permission.READ_PHONE_STATE)) &&
            (svar[Manifest.permission.READ_CALL_LOG] ?: harTillatelse(context, Manifest.permission.READ_CALL_LOG))
        onEndre { it.copy(samtaleVarsel = ok) }
        avslatt = !ok
    }

    Gruppe("Etter telefonsamtaler") {
        Forklaring(
            "Når en samtale er over, kommer varselet «Registrere oppdrag fra samtalen med …?». Åpner du appen " +
                "innen 15 minutter, spør den det samme. Skjemaet åpnes med nummer og kontaktperson utfylt, og kunde " +
                "og anlegg fra det personen er registrert på før.",
        )
        Forklaring(
            "Appen trenger da tilgang til telefonstatus (for å merke at samtalen er over), anropsloggen " +
                "(nummer og varighet) og kontaktene (navn, valgfritt). Ingenting sendes fra telefonen.",
        )
        BryterRad("Spør etter samtaler", pa = oppsett.samtaleVarsel && nodvendigGitt) { pa ->
            if (pa && status.values.any { !it }) {
                be.launch(samtaleTillatelser.toTypedArray())
            } else {
                onEndre { it.copy(samtaleVarsel = pa && nodvendigGitt) }
            }
        }
        if (oppsett.samtaleVarsel && nodvendigGitt) {
            Text("Hvordan vil du bli spurt?", style = MaterialTheme.typography.titleSmall)
            Column {
                SamtaleModus.entries.forEach { m ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = oppsett.samtaleModus == m, onClick = { onEndre { it.copy(samtaleModus = m) } })
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = oppsett.samtaleModus == m, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(m.tittel, style = MaterialTheme.typography.bodyLarge)
                            Text(m.forklaring, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (oppsett.samtaleVarsel || avslatt) {
            Column {
                TillatelseLinje("Telefonstatus", status[Manifest.permission.READ_PHONE_STATE] == true)
                TillatelseLinje("Anropslogg", status[Manifest.permission.READ_CALL_LOG] == true)
                TillatelseLinje("Kontakter (valgfritt)", status[Manifest.permission.READ_CONTACTS] == true)
                if (Build.VERSION.SDK_INT >= 33) {
                    TillatelseLinje("Varsler", status[Manifest.permission.POST_NOTIFICATIONS] == true)
                }
            }
            if (status.values.any { !it }) {
                Forklaring("Har du sagt nei før, må tilgangen gis i systeminnstillingene for appen.")
                OutlinedButton(onClick = { apneAppInnstillinger(context) }) { Text("Åpne app-innstillinger") }
            }
        }
        Text(
            "Ikke spør etter samtaler kortere enn ${oppsett.minSamtaleSekunder} sekunder",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = oppsett.minSamtaleSekunder.toFloat(),
            onValueChange = { v -> onEndre { it.copy(minSamtaleSekunder = (v / 5).toInt() * 5) } },
            valueRange = 0f..120f,
            steps = 23,
        )
    }

    Gruppe("Snarveier") {
        Forklaring(
            "Hold fingeren på app-ikonet for snarveien «Nytt oppdrag». Du kan også legge til en flis " +
                "i hurtiginnstillingene (dra ned fra toppen to ganger, trykk blyanten).",
        )
        if (Build.VERSION.SDK_INT >= 33) {
            Row {
                OutlinedButton(onClick = {
                    val sbm = context.getSystemService(StatusBarManager::class.java)
                    sbm.requestAddTileService(
                        ComponentName(context, NyttOppdragFlis::class.java),
                        context.getString(R.string.nytt_oppdrag),
                        Icon.createWithResource(context, R.drawable.ic_varsel),
                        context.mainExecutor,
                    ) { resultat ->
                        if (resultat == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                            Toast.makeText(context, "Flisen er allerede lagt til", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Legg til flis i hurtiginnstillinger") }
            }
        }
    }
}
