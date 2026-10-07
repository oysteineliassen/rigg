package no.oppdragsliste.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import no.oppdragsliste.app.app
import no.oppdragsliste.app.data.Oppsett
import no.oppdragsliste.app.kalender.Kalender
import no.oppdragsliste.app.kalender.KalenderValg
import no.oppdragsliste.app.logikk.varighetTekst

private val varighetValg = listOf(30, 60, 90, 120, 180)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KalenderInnstillinger(oppsett: Oppsett, onEndre: ((Oppsett) -> Oppsett) -> Unit) {
    val context = LocalContext.current
    val teller = gjenopptattTeller()
    var svar by remember { mutableIntStateOf(0) }
    val tilgang = remember(teller, svar) { Kalender.harTilgang(context) }
    var kalendere by remember { mutableStateOf<List<KalenderValg>>(emptyList()) }
    LaunchedEffect(tilgang, oppsett.kalenderSynk) {
        if (tilgang && oppsett.kalenderSynk) kalendere = Kalender.kalendere(context)
    }
    val be = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        svar++
        if (r.values.all { it }) onEndre { it.copy(kalenderSynk = true) }
    }

    Gruppe("Google Kalender") {
        Forklaring(
            "Avtaler med dato legges i kalenderen du velger her. Er det en Google-kalender, synker telefonen " +
                "den til Google Kalender. Endrer du dato, klokkeslett eller annet på avtalen, oppdateres " +
                "hendelsen; slettes avtalen eller endres den til en annen prioritet, fjernes den.",
        )
        Forklaring("Synken går én vei: endringer gjort i kalenderen kommer ikke tilbake til appen.")
        BryterRad("Legg avtaler i kalenderen", oppsett.kalenderSynk && tilgang) { pa ->
            if (pa && !tilgang) be.launch(Kalender.tillatelser()) else onEndre { it.copy(kalenderSynk = pa) }
        }
        if (oppsett.kalenderSynk && !tilgang) {
            Forklaring("Appen har ikke tilgang til kalenderen.")
            OutlinedButton(onClick = { apneAppInnstillinger(context) }) { Text("Åpne app-innstillinger") }
        }
        if (oppsett.kalenderSynk && tilgang) {
            if (kalendere.isEmpty()) {
                Forklaring("Fant ingen kalender å skrive til. Er Google-kontoen lagt til på telefonen?")
            } else {
                Text("Kalender:", style = MaterialTheme.typography.bodyMedium)
                Column {
                    kalendere.forEach { k ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.selectable(
                                selected = oppsett.kalenderId == k.id,
                                onClick = { onEndre { it.copy(kalenderId = k.id) } },
                            ),
                        ) {
                            RadioButton(selected = oppsett.kalenderId == k.id, onClick = null)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(k.navn, style = MaterialTheme.typography.bodyLarge)
                                if (k.konto.isNotBlank() && k.konto != k.navn) Forklaring(k.konto)
                            }
                        }
                    }
                }
                if (oppsett.kalenderId < 0) Forklaring("Velg en kalender for å starte synken.")
            }
            Forklaring("Varighet for avtaler med klokkeslett (uten klokkeslett blir det heldagshendelse):")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                varighetValg.forEach { m ->
                    FilterChip(
                        selected = oppsett.kalenderVarighetMin == m,
                        onClick = { onEndre { it.copy(kalenderVarighetMin = m) } },
                        label = { Text(varighetTekst(m)) },
                    )
                }
            }
            if (oppsett.kalenderId >= 0) {
                Button(onClick = { context.app.planleggAltSenere() }) { Text("Synk nå") }
            }
        }
    }
}
