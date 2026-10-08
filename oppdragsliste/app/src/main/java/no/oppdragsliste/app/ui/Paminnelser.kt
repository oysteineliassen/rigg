package no.oppdragsliste.app.ui

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Paminnelse
import no.oppdragsliste.app.logikk.erForfalt
import no.oppdragsliste.app.logikk.filtrer
import no.oppdragsliste.app.logikk.formaterDato
import no.oppdragsliste.app.logikk.formaterDatoTid
import no.oppdragsliste.app.logikk.formaterTid
import no.oppdragsliste.app.logikk.hurtigvalg
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/** En påminnelse i listen: avkrysning, tekst, tidspunkt og eventuelt oppdraget den hører til. */
@Composable
fun PaminnelseRad(
    p: Paminnelse,
    oppdrag: Oppdrag?,
    na: LocalDateTime,
    onFerdig: (Boolean) -> Unit,
    onKlikk: () -> Unit,
    onOppdrag: (Oppdrag) -> Unit,
    modifier: Modifier = Modifier,
) {
    val forfalt = erForfalt(p, na)
    Card(
        onClick = onKlikk,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
            Checkbox(checked = p.ferdig, onCheckedChange = onFerdig)
            Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    p.tekst,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (p.ferdig) TextDecoration.LineThrough else null,
                    color = if (p.ferdig) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                val nar = formaterDatoTid(p.dato, p.tid, na.toLocalDate())
                if (nar != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val farge = if (forfalt) forfaltFarge else MaterialTheme.colorScheme.onSurfaceVariant
                        Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(15.dp), tint = farge)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            nar + if (p.tid == null) " (kl. 08:00)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = farge,
                            fontWeight = if (forfalt) FontWeight.SemiBold else null,
                        )
                    }
                }
                if (oppdrag != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onOppdrag(oppdrag) },
                    ) {
                        Icon(Icons.Outlined.Link, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            oppdrag.tittel.ifBlank { "Oppdrag" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Skjema for en påminnelse. Tilbake/trykk utenfor lukker først ved andre trykk når noe er skrevet.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PaminnelseDialog(
    skjema: PaminnelseSkjema,
    oppdrag: List<Oppdrag>,
    oppdragEtterId: Map<Long, Oppdrag>,
    onEndre: ((PaminnelseSkjema) -> PaminnelseSkjema) -> Unit,
    onLagre: () -> Unit,
    onSlett: (Paminnelse) -> Unit,
    onLukk: () -> Unit,
) {
    val context = LocalContext.current
    var sisteTilbake by remember { mutableLongStateOf(0L) }
    val forsokLukke: () -> Unit = {
        val na = SystemClock.uptimeMillis()
        if (!skjema.harInnhold || na - sisteTilbake < 2500) {
            onLukk()
        } else {
            sisteTilbake = na
            Toast.makeText(context, "Trykk tilbake én gang til for å forkaste påminnelsen", Toast.LENGTH_SHORT).show()
        }
    }
    var visDato by remember { mutableStateOf(false) }
    var visTid by remember { mutableStateOf(false) }
    var velgOppdrag by remember { mutableStateOf(false) }
    var bekreftSlett by remember { mutableStateOf(false) }
    val tekstFokus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (skjema.original == null) tekstFokus.requestFocus() }
    val idag = LocalDate.now()

    Dialog(onDismissRequest = forsokLukke, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .imePadding(),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (skjema.original == null) "Ny påminnelse" else "Endre påminnelse",
                    style = MaterialTheme.typography.headlineSmall,
                )
                OutlinedTextField(
                    value = skjema.tekst,
                    onValueChange = { v -> onEndre { it.copy(tekst = v, visFeil = false) } },
                    label = { Text("Hva må du huske?") },
                    minLines = 2,
                    isError = skjema.visFeil,
                    supportingText = if (skjema.visFeil) {
                        { Text("Skriv hva påminnelsen gjelder") }
                    } else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(tekstFokus),
                )

                Text("Varsel", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    hurtigvalg(LocalDateTime.now()).forEach { (navn, t) ->
                        AssistChip(
                            onClick = { onEndre { it.copy(dato = t.toLocalDate(), tid = t.toLocalTime()) } },
                            label = { Text(navn) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { visDato = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Event, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(skjema.dato?.let { formaterDato(it, idag) } ?: "Dato")
                    }
                    OutlinedButton(onClick = { visTid = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(skjema.tid?.let { formaterTid(it) } ?: "Klokkeslett")
                    }
                    if (skjema.dato != null) {
                        IconButton(onClick = { onEndre { it.copy(dato = null, tid = null) } }) {
                            Icon(Icons.Outlined.Close, "Fjern varsel")
                        }
                    }
                }
                if (skjema.dato == null) {
                    Forklaring("Uten dato kommer det ikke noe varsel; påminnelsen ligger bare i listen.")
                } else if (skjema.tid == null) {
                    Forklaring("Uten klokkeslett kommer varselet kl. 08:00.")
                }

                Text("Oppdrag", style = MaterialTheme.typography.titleSmall)
                val koblet = skjema.oppdragId?.let { oppdragEtterId[it] }
                if (koblet != null) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                            Icon(Icons.Outlined.Link, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(koblet.tittel, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val under = listOf(koblet.kunde.takeIf { koblet.beskrivelse.isNotBlank() }.orEmpty(), koblet.stedTekst)
                                    .filter { it.isNotBlank() }.joinToString(" · ")
                                if (under.isNotBlank()) Forklaring(under)
                            }
                            IconButton(onClick = { onEndre { it.copy(oppdragId = null) } }) {
                                Icon(Icons.Outlined.Close, "Fjern koblingen")
                            }
                        }
                    }
                } else {
                    OutlinedButton(onClick = { velgOppdrag = true }) {
                        Icon(Icons.Outlined.Link, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Koble til oppdrag")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    val original = skjema.original
                    if (original != null) {
                        TextButton(onClick = { if (bekreftSlett) onSlett(original) else bekreftSlett = true }) {
                            Text(if (bekreftSlett) "Slett – sikker?" else "Slett", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onLukk) { Text("Avbryt") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onLagre() }) { Text("Lagre") }
                }
            }
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
                    // Uten dato: i dag hvis tiden ikke har passert, ellers i morgen.
                    onEndre {
                        val dato = it.dato ?: if (t.isAfter(LocalTime.now())) idag else idag.plusDays(1)
                        it.copy(tid = t, dato = dato)
                    }
                    visTid = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { visTid = false }) { Text("Avbryt") } },
        )
    }
    if (velgOppdrag) {
        VelgOppdragDialog(
            oppdrag = oppdrag,
            onVelg = { o ->
                onEndre { it.copy(oppdragId = o.id) }
                velgOppdrag = false
            },
            onLukk = { velgOppdrag = false },
        )
    }
}

/** Liste over åpne oppdrag med søk, for å koble en påminnelse til et av dem. */
@Composable
private fun VelgOppdragDialog(oppdrag: List<Oppdrag>, onVelg: (Oppdrag) -> Unit, onLukk: () -> Unit) {
    var sok by remember { mutableStateOf("") }
    val treff = remember(oppdrag, sok) { filtrer(oppdrag, sok) }
    val idag = LocalDate.now()
    Dialog(onDismissRequest = onLukk, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 560.dp)
                .imePadding(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Koble til oppdrag", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = sok,
                    onValueChange = { sok = it },
                    placeholder = { Text("Søk i kunde, sted, beskrivelse …") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
                if (treff.isEmpty()) Forklaring("Ingen åpne oppdrag passer.")
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(treff, key = { it.id }) { o ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onVelg(o) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            Text(o.tittel, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val under = listOfNotNull(
                                o.kunde.takeIf { it.isNotBlank() && it != o.tittel },
                                o.prioritet.tittel,
                                formaterDatoTid(o.dato, o.tid, idag, o.sluttid),
                                o.stedTekst.ifBlank { null },
                            ).joinToString(" · ")
                            Text(under, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onLukk) { Text("Avbryt") }
                }
            }
        }
    }
}
