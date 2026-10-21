package no.oppdragsliste.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import no.oppdragsliste.app.data.Oppsett
import no.oppdragsliste.app.logikk.formaterTid
import no.oppdragsliste.app.logikk.varighetTekst
import no.oppdragsliste.app.varsler.Planlegger
import no.oppdragsliste.app.varsler.Varsler
import java.time.LocalTime

private val minuttValg = listOf(15, 30, 60, 90, 120, 180)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VarselInnstillinger(oppsett: Oppsett, onEndre: ((Oppsett) -> Oppsett) -> Unit) {
    val context = LocalContext.current
    val teller = gjenopptattTeller()
    val kanVarsle = remember(teller) { Varsler.kanVarsle(context) }
    val eksakt = remember(teller) { Planlegger.kanPlanleggeEksakt(context) }
    var velgTid by remember { mutableStateOf(false) }

    Gruppe("Påminnelser") {
        if (!kanVarsle) {
            Forklaring("Appen har ikke lov til å vise varsler.")
            OutlinedButton(onClick = { apneAppInnstillinger(context) }) { Text("Slå på varsler") }
        }
        BryterRad("Varsle før avtaler", oppsett.avtaleVarsel) { pa -> onEndre { it.copy(avtaleVarsel = pa) } }
        if (oppsett.avtaleVarsel) {
            Forklaring("Hvor lenge før avtalt tid:")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                minuttValg.forEach { m ->
                    FilterChip(
                        selected = oppsett.avtaleMinutterFor == m,
                        onClick = { onEndre { it.copy(avtaleMinutterFor = m) } },
                        label = { Text(varighetTekst(m)) },
                    )
                }
            }
            Forklaring("Avtaler uten klokkeslett får ikke påminnelse, men er med i morgenoversikten.")
        }
        if (!eksakt && Build.VERSION.SDK_INT >= 31) {
            Forklaring("Påminnelsene kan komme litt for sent fordi appen ikke har lov til å sette nøyaktige alarmer.")
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)),
                )
            }) { Text("Tillat nøyaktige alarmer") }
        }

        BryterRad("Morgenoversikt", oppsett.morgenOversikt) { pa -> onEndre { it.copy(morgenOversikt = pa) } }
        if (oppsett.morgenOversikt) {
            Forklaring("F.eks. «2 akutte, 1 avtale i dag, 8 venter i området».")
            OutlinedButton(onClick = { velgTid = true }) { Text("Klokkeslett: ${formaterTid(oppsett.morgenTid)}") }
        }
    }

    if (velgTid) {
        val tilstand = rememberTimePickerState(oppsett.morgenTid.hour, oppsett.morgenTid.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { velgTid = false },
            title = { Text("Morgenoversikt") },
            text = { TimePicker(state = tilstand) },
            confirmButton = {
                TextButton(onClick = {
                    onEndre { it.copy(morgenTid = LocalTime.of(tilstand.hour, tilstand.minute)) }
                    velgTid = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { velgTid = false }) { Text("Avbryt") } },
        )
    }
}
