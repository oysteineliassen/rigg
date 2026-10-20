package no.rigg.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Kortet på Program-fanen: en HIIT-økt man kan ta når som helst, ved siden av programmet. Den logges som en egen
 * økt (teller i uka og historikken), men endrer ikke programmet.
 */
@Composable
fun HiitCard(vm: AppViewModel, onStart: () -> Unit) {
    var setup by remember { mutableStateOf(false) }
    val active = vm.guided?.dayTitle == Hiit.TITLE
    val colors = MaterialTheme.colorScheme
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel(tr("Interval workout", "Intervalløkt"), color = colors.tertiary)
            Text("HIIT", style = MaterialTheme.typography.headlineSmall)
            Text(
                tr(
                    "High pulse, everything on time: six exercises in a circuit with short breaks. Comes in addition to the plan.",
                    "Høy puls, alt på tid: seks øvelser i runde med korte pauser. Kommer i tillegg til programmet.",
                ),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.size(4.dp))
            OutlinedButton(onClick = { if (active) vm.showGuided() else setup = true }, shape = RoundedCornerShape(14.dp)) {
                Icon(RiggIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (active) tr("Continue the HIIT workout", "Fortsett HIIT-økta") else tr("Set up HIIT", "Sett opp HIIT"))
            }
        }
    }
    if (setup) {
        HiitDialog(
            vm = vm,
            onStart = { format, exercises ->
                setup = false
                onStart()
                vm.startHiit(format, exercises)
            },
            onDismiss = { setup = false },
        )
    }
}

/** Velg lengde, se øvelsene (trykk for figur), bytt dem ut om du vil, og start. */
@Composable
private fun HiitDialog(vm: AppViewModel, onStart: (HiitFormat, List<Exercise>) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var format by remember { mutableStateOf(HiitFormat.STANDARD) }
    // Starter et nytt sted for hver loggførte økt, så forslaget varierer; "Bytt øvelser" går videre.
    var variant by remember { mutableIntStateOf(vm.logs.size) }
    val exercises = remember(variant, vm.profile) { Hiit.exercises(vm.profile, variant) }
    var infoFor by remember { mutableStateOf<Exercise?>(null) }
    infoFor?.let { ExerciseInfoDialog(it, onDismiss = { infoFor = null }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("HIIT workout", "HIIT-økt")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HiitFormat.values().forEach { f ->
                        val selected = f == format
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                .then(
                                    if (selected) Modifier.background(colors.primaryContainer)
                                    else Modifier.border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp)),
                                )
                                .clickable { format = f }.padding(horizontal = 6.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                f.label, style = MaterialTheme.typography.titleSmall,
                                color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                            )
                            Text(
                                tr("~${Hiit.minutes(f.spec, exercises.size)} min", "ca. ${Hiit.minutes(f.spec, exercises.size)} min"),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    format.detail + tr(
                        " · ${format.spec.roundRestSec} s break between rounds. Starts with a short warm-up.",
                        " · ${format.spec.roundRestSec} sek pause mellom rundene. Starter med en kort oppvarming.",
                    ),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
                exercises.forEachIndexed { i, ex ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerHigh)
                            .clickable { infoFor = ex }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.width(16.dp), textAlign = TextAlign.Center)
                        ExerciseTile(ex, size = 40.dp)
                        Text(ex.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
                TextButton(onClick = { variant++ }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(tr("Swap exercises", "Bytt øvelser"))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onStart(format, exercises) }, enabled = exercises.isNotEmpty()) {
                Text(tr("Start", "Start"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "Avbryt")) } },
    )
}
