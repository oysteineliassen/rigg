package no.oppdragsliste.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import no.oppdragsliste.app.data.Ordre
import no.oppdragsliste.app.logikk.formaterDato
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Endre en sak (eller legge inn en ny, når [ordre] har id 0). Saksnummer er påkrevd. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdreRedigerDialog(ordre: Ordre, onLagre: (Ordre) -> Unit, onLukk: () -> Unit) {
    var saksnr by rememberSaveable { mutableStateOf(ordre.saksnr) }
    var tittel by rememberSaveable { mutableStateOf(ordre.tittel) }
    var kunde by rememberSaveable { mutableStateOf(ordre.kunde) }
    var kundenr by rememberSaveable { mutableStateOf(ordre.kundenr) }
    var adresse by rememberSaveable { mutableStateOf(ordre.adresse) }
    var visFeil by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onLukk,
        // Trykk utenfor skal ikke forkaste det som er skrevet.
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(if (ordre.id == 0L) "Ny sak" else "Endre SAK ${ordre.saksnr}") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = saksnr,
                    onValueChange = { saksnr = it.filter { c -> c.isDigit() }; visFeil = false },
                    label = { Text("Saksnummer") },
                    singleLine = true,
                    isError = visFeil && saksnr.isBlank(),
                    supportingText = if (visFeil && saksnr.isBlank()) {
                        { Text("Saksnummer må fylles inn") }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(value = tittel, onValueChange = { tittel = it }, label = { Text("Tittel") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = kunde, onValueChange = { kunde = it }, label = { Text("Kunde") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = kundenr, onValueChange = { kundenr = it }, label = { Text("Kundenummer") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = adresse, onValueChange = { adresse = it }, label = { Text("Adresse") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                if (saksnr.isBlank()) {
                    visFeil = true
                } else {
                    onLagre(
                        ordre.copy(
                            saksnr = saksnr.trim(), tittel = tittel.trim(), kunde = kunde.trim(), kundenr = kundenr.trim(),
                            adresse = adresse.trim(), dato = null, tid = null,
                        ),
                    )
                }
            }) { Text("Lagre") }
        },
        dismissButton = { TextButton(onClick = onLukk) { Text("Avbryt") } },
    )
}
