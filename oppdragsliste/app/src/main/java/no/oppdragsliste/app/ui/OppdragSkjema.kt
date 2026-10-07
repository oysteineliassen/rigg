package no.oppdragsliste.app.ui

import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import no.oppdragsliste.app.logikk.AnleggForslag
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import no.oppdragsliste.app.data.KjentAnlegg
import no.oppdragsliste.app.data.Prioritet
import no.oppdragsliste.app.logikk.Feil
import no.oppdragsliste.app.logikk.anleggForslag
import no.oppdragsliste.app.logikk.kundeForslag
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
/**
 * Skjemaet som et ark nedenfra. Tilbaketasten (og trykk utenfor) lukker først ved andre trykk, og det som
 * er skrevet, er tatt vare på som utkast uansett. «Lukk» lukker med en gang, «Tøm» starter på nytt.
 */
@Composable
fun SkjemaArk(
    skjema: Skjema?,
    kjente: List<KjentAnlegg>,
    onEndre: ((Skjema) -> Skjema) -> Unit,
    onVelgKunde: (String) -> Unit,
    onVelgAnlegg: (AnleggForslag) -> Unit,
    onFeltForlatt: () -> Unit,
    onGlem: (kunde: String, anlegg: String?) -> Unit,
    onLagre: () -> Unit,
    onTom: () -> Unit,
    fjernet: String?,
    onAngre: () -> Unit,
    onLukk: () -> Unit,
) {
    val context = LocalContext.current
    // Siste skjema vises mens arket glir ut.
    var sist by remember { mutableStateOf(skjema) }
    if (skjema != null) sist = skjema
    var sisteTilbake by remember { mutableLongStateOf(0L) }
    val forsokLukke: () -> Unit = {
        val na = SystemClock.uptimeMillis()
        if (skjema == null || !skjema.harInnhold || na - sisteTilbake < 2500) {
            sisteTilbake = 0L
            onLukk()
        } else {
            sisteTilbake = na
            Toast.makeText(context, "Trykk tilbake én gang til for å lukke. Det du har skrevet, blir husket.", Toast.LENGTH_SHORT).show()
        }
    }
    BackHandler(enabled = skjema != null) { forsokLukke() }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = skjema != null, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { forsokLukke() },
            )
        }
        AnimatedVisibility(
            visible = skjema != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding(),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            sist?.let { s ->
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 16.dp),
                ) {
                    SkjemaInnhold(s, kjente, onEndre, onVelgKunde, onVelgAnlegg, onFeltForlatt, onGlem, onLagre, onTom, fjernet, onAngre, onLukk)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkjemaInnhold(
    skjema: Skjema,
    kjente: List<KjentAnlegg>,
    onEndre: ((Skjema) -> Skjema) -> Unit,
    onVelgKunde: (String) -> Unit,
    onVelgAnlegg: (AnleggForslag) -> Unit,
    onFeltForlatt: () -> Unit,
    onGlem: (kunde: String, anlegg: String?) -> Unit,
    onLagre: () -> Unit,
    onTom: () -> Unit,
    fjernet: String?,
    onAngre: () -> Unit,
    onLukk: () -> Unit,
) {
    val fokus = LocalFocusManager.current
    val anleggFokus = remember { FocusRequester() }
    var kundeAktiv by remember { mutableStateOf(false) }
    var anleggAktiv by remember { mutableStateOf(false) }
    val kundeTreff = remember(kjente, skjema.kunde) { kundeForslag(kjente, skjema.kunde) }
    val anleggTreff = remember(kjente, skjema.kunde, skjema.anlegg) { anleggForslag(kjente, skjema.kunde, skjema.anlegg) }

    var visDato by remember { mutableStateOf(false) }
    var visTid by remember { mutableStateOf(false) }
    val idag = LocalDate.now()
    fun feil(f: Feil) = skjema.visFeil && f in skjema.feil

    Column(Modifier.fillMaxWidth()) {
        Topplinje(skjema, onTom, onLukk)
        if (fjernet != null) AngreLinje(fjernet, onAngre)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (skjema.fraUtkast) {
                Text(
                    "Fortsetter der du slapp. Trykk «${if (skjema.original == null) "Tøm" else "Tilbakestill"}» for å starte på nytt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

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
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { f ->
                        if (kundeAktiv && !f.isFocused) onFeltForlatt()
                        kundeAktiv = f.isFocused
                    },
            )
            if (kundeAktiv && kundeTreff.isNotEmpty()) {
                Forslagsliste(
                    forslag = kundeTreff,
                    tekst = { it },
                    bekreftTekst = { navn ->
                        val n = kjente.count { it.kunde.trim().equals(navn, ignoreCase = true) && it.anlegg.isNotBlank() }
                        if (n == 0) "Fjerne «$navn» fra forslagene?"
                        else "Fjerne «$navn» og ${if (n == 1) "1 anlegg" else "$n anlegg"} fra forslagene?"
                    },
                    onVelg = { navn ->
                        onVelgKunde(navn)
                        anleggFokus.requestFocus()
                    },
                    onGlem = { navn -> onGlem(navn, null) },
                )
            }
            OutlinedTextField(
                value = skjema.anlegg,
                onValueChange = { v -> onEndre { it.copy(anlegg = v) } },
                label = { Text("Anlegg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(anleggFokus)
                    .onFocusChanged { f ->
                        if (anleggAktiv && !f.isFocused) onFeltForlatt()
                        anleggAktiv = f.isFocused
                    },
            )
            if (anleggAktiv && anleggTreff.isNotEmpty()) {
                Forslagsliste(
                    forslag = anleggTreff,
                    tekst = { it.tekst },
                    bekreftTekst = { f ->
                        val kunde = f.kunde ?: skjema.kunde
                        "Fjerne «${f.anlegg}»" + (if (kunde.isNotBlank()) " ($kunde)" else "") + " fra forslagene?"
                    },
                    onVelg = { f ->
                        onVelgAnlegg(f)
                        fokus.clearFocus()
                    },
                    onGlem = { f -> onGlem(f.kunde ?: skjema.kunde, f.anlegg) },
                )
            }
            OutlinedTextField(
                value = skjema.kontaktperson,
                onValueChange = { v -> onEndre { it.copy(kontaktperson = v) } },
                label = { Text("Kontaktperson") },
                singleLine = true,
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
                value = skjema.adresse,
                onValueChange = { v -> onEndre { it.copy(adresse = v) } },
                label = { Text("Adresse") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
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
private fun Topplinje(skjema: Skjema, onTom: () -> Unit, onLukk: () -> Unit) {
    // «Tøm» må trykkes to ganger (innen tre sekunder), så det ikke skjer ved et uhell.
    var bekreft by remember { mutableStateOf(false) }
    LaunchedEffect(bekreft) {
        if (bekreft) {
            delay(3000)
            bekreft = false
        }
    }
    val nytt = skjema.original == null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Text(
            if (nytt) "Nytt oppdrag" else "Endre oppdrag",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = {
                if (bekreft) {
                    bekreft = false
                    onTom()
                } else {
                    bekreft = true
                }
            },
            enabled = skjema.harInnhold,
        ) {
            val tekst = if (nytt) "Tøm" else "Tilbakestill"
            Text(
                if (bekreft) "$tekst – sikker?" else tekst,
                color = if (bekreft) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        }
        IconButton(onClick = onLukk) { Icon(Icons.Outlined.Close, "Lukk (det du har skrevet, blir husket)") }
    }
}

/**
 * Forslag rett under feltet. Trykk velger. Hold inne for å fjerne et forslag fra minnet; det må bekreftes
 * i raden, og kan angres etterpå. Oppdragene påvirkes ikke.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> Forslagsliste(
    forslag: List<T>,
    tekst: (T) -> String,
    bekreftTekst: (T) -> String,
    onVelg: (T) -> Unit,
    onGlem: (T) -> Unit,
) {
    var bekreft by remember { mutableStateOf<T?>(null) }
    val haptikk = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            forslag.forEach { f ->
                if (bekreft == f) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(bekreftTekst(f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Oppdragene påvirkes ikke.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            OutlinedButton(onClick = { bekreft = null }) { Text("Avbryt") }
                            TextButton(onClick = {
                                bekreft = null
                                onGlem(f)
                            }) { Text("Fjern", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onVelg(f) },
                                onLongClick = {
                                    haptikk.performHapticFeedback(HapticFeedbackType.LongPress)
                                    bekreft = f
                                },
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Icon(Icons.Outlined.History, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(tekst(f), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** «Fjernet … fra forslagene  [Angre]», vises noen sekunder etter at et forslag er fjernet. */
@Composable
private fun AngreLinje(melding: String, onAngre: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp)) {
            Text(melding, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAngre) { Text("Angre", color = MaterialTheme.colorScheme.inversePrimary) }
        }
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
