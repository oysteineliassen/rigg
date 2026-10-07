package no.oppdragsliste.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import no.oppdragsliste.app.data.Prioritet
import no.oppdragsliste.app.logikk.Feil
import no.oppdragsliste.app.logikk.formaterDato
import no.oppdragsliste.app.logikk.formaterTid
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private val Prioritet.forklaring: String
    get() = when (this) {
        Prioritet.AKUTT -> "Må tas nå"
        Prioritet.AVTALE -> "Fast dato og tid"
        Prioritet.HASTER -> "Snarest mulig"
        Prioritet.OMRADE -> "Når jeg er i nærheten"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkjemaArk(
    skjema: Skjema,
    onEndre: ((Skjema) -> Skjema) -> Unit,
    onLagre: () -> Unit,
    onLukk: () -> Unit,
) {
    val arkTilstand = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var visDato by remember { mutableStateOf(false) }
    var visTid by remember { mutableStateOf(false) }
    val idag = LocalDate.now()
    fun feil(f: Feil) = skjema.visFeil && f in skjema.feil

    ModalBottomSheet(onDismissRequest = onLukk, sheetState = arkTilstand) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (skjema.original == null) "Nytt oppdrag" else "Endre oppdrag",
                style = MaterialTheme.typography.headlineSmall,
            )

            PrioritetVelger(skjema.prioritet) { p -> onEndre { it.copy(prioritet = p) } }

            OutlinedTextField(
                value = skjema.beskrivelse,
                onValueChange = { v -> onEndre { it.copy(beskrivelse = v) } },
                label = { Text("Beskrivelse") },
                minLines = 2,
                isError = feil(Feil.MANGLER_BESKRIVELSE_ELLER_KUNDE),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = skjema.kunde,
                onValueChange = { v -> onEndre { it.copy(kunde = v) } },
                label = { Text("Kunde") },
                singleLine = true,
                isError = feil(Feil.MANGLER_BESKRIVELSE_ELLER_KUNDE),
                supportingText = if (feil(Feil.MANGLER_BESKRIVELSE_ELLER_KUNDE)) {
                    { Text(Feil.MANGLER_BESKRIVELSE_ELLER_KUNDE.melding) }
                } else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = skjema.telefon,
                onValueChange = { v -> onEndre { it.copy(telefon = v) } },
                label = { Text("Telefon") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = skjema.sted,
                onValueChange = { v -> onEndre { it.copy(sted = v) } },
                label = { Text("Sted") },
                placeholder = { Text("F.eks. Søgne") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = skjema.adresse,
                onValueChange = { v -> onEndre { it.copy(adresse = v) } },
                label = { Text("Adresse") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            // Dato og klokkeslett
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { visDato = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Event, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(skjema.dato?.let { formaterDato(it, idag) } ?: "Dato")
                }
                if (skjema.dato != null) {
                    IconButton(onClick = { onEndre { it.copy(dato = null, tid = null) } }) {
                        Icon(Icons.Outlined.Close, "Fjern dato")
                    }
                }
                OutlinedButton(onClick = { visTid = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(skjema.tid?.let { formaterTid(it) } ?: "Klokkeslett")
                }
                if (skjema.tid != null) {
                    IconButton(onClick = { onEndre { it.copy(tid = null) } }) {
                        Icon(Icons.Outlined.Close, "Fjern klokkeslett")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { onEndre { it.copy(dato = idag) } }, label = { Text("I dag") })
                AssistChip(onClick = { onEndre { it.copy(dato = idag.plusDays(1)) } }, label = { Text("I morgen") })
            }
            listOf(Feil.MANGLER_DATO, Feil.TID_UTEN_DATO).filter { feil(it) }.forEach {
                Text(it.melding, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = onLagre,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) { Text("Lagre", style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (visDato) {
        val start = (skjema.dato ?: idag).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val tilstand = rememberDatePickerState(initialSelectedDateMillis = start)
        DatePickerDialog(
            onDismissRequest = { visDato = false },
            confirmButton = {
                TextButton(onClick = {
                    tilstand.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onEndre { it.copy(dato = d) }
                    }
                    visDato = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { visDato = false }) { Text("Avbryt") } },
        ) { DatePicker(state = tilstand) }
    }

    if (visTid) {
        val start = skjema.tid ?: LocalTime.of(8, 0)
        val tilstand = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { visTid = false },
            title = { Text("Klokkeslett") },
            text = { TimePicker(state = tilstand) },
            confirmButton = {
                TextButton(onClick = {
                    val t = LocalTime.of(tilstand.hour, tilstand.minute)
                    // Klokkeslett uten dato gir mening bare for i dag.
                    onEndre { it.copy(tid = t, dato = it.dato ?: idag) }
                    visTid = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { visTid = false }) { Text("Avbryt") } },
        )
    }
}

@Composable
private fun PrioritetVelger(valgt: Prioritet, onValg: (Prioritet) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Prioritet.entries.chunked(2).forEach { rad ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rad.forEach { p ->
                    val farge = p.farge()
                    val erValgt = p == valgt
                    Surface(
                        onClick = { onValg(p) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (erValgt) farge else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (erValgt) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(2.dp, farge),
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp),
                    ) {
                        Column(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(p.tittel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(p.forklaring, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
