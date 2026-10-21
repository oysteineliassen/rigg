package no.oppdragsliste.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import no.oppdragsliste.app.data.Ordre
import no.oppdragsliste.app.logikk.formaterDato
import java.time.LocalDate

/**
 * Fanen «Ordre»: saker importert fra skjermbilder av ordrelisten, med avkrysning for utført og fakturert.
 * Saker som er både utført og fakturert, ligger i et sammenfoldet arkiv.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdreSkjerm(
    ordre: List<Ordre>,
    import: OrdreImport?,
    melding: String?,
    onMeldingVist: () -> Unit,
    onImporter: (List<Uri>) -> Unit,
    onBekreftImport: () -> Unit,
    onAvbrytImport: () -> Unit,
    onUtfort: (Ordre, Boolean) -> Unit,
    onFakturert: (Ordre, Boolean) -> Unit,
    onLagOppdrag: (Ordre) -> Unit,
    onSlett: (Ordre) -> Unit,
    onLagreManuelt: (Ordre) -> Unit,
) {
    // Sak som endres (eller ny, id 0) i dialogen.
    var redigeres by remember { mutableStateOf<Ordre?>(null) }
    val velgBilder = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        onImporter(uris)
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(melding) {
        if (melding != null) {
            snackbar.showSnackbar(melding)
            onMeldingVist()
        }
    }
    var sok by rememberSaveable { mutableStateOf("") }
    var utvidet by rememberSaveable { mutableStateOf<Long?>(null) }
    var visArkiv by rememberSaveable { mutableStateOf(false) }

    val treff = ordre.filter { o ->
        val ord = sok.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val tekst = listOf(o.saksnr, o.tittel, o.adresse, o.kunde, o.kundenr).joinToString(" ").lowercase()
        ord.all { tekst.contains(it) }
    }
    val faktureres = treff.filter { it.utfort && !it.fakturert }.sortedByDescending { it.utfortDato }
    val ikkeUtfort = treff.filter { !it.utfort }.sortedWith(compareBy<Ordre, LocalDate?>(nullsLast()) { it.dato }.thenBy { it.saksnr })
    val arkiv = treff.filter { it.fakturert && it.utfort }.sortedByDescending { it.fakturertDato }
    // Fakturert men ikke utført (uvanlig): vises sammen med de som ikke er utført.
    val fakturertIkkeUtfort = treff.filter { it.fakturert && !it.utfort }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ordre", fontWeight = FontWeight.SemiBold) },
                actions = {
                    TextButton(onClick = { redigeres = Ordre(saksnr = "") }) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Legg til manuelt")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { velgBilder.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                icon = { Icon(Icons.Outlined.Image, null) },
                text = { Text("Importer skjermbilde") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innPadding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "sok") {
                OutlinedTextField(
                    value = sok,
                    onValueChange = { sok = it },
                    placeholder = { Text("Søk i saksnr, kunde, adresse …") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (ordre.isEmpty()) {
                item(key = "tom") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ingen saker ennå.", style = MaterialTheme.typography.titleMedium)
                        Forklaring(
                            "Ta skjermbilde av ordrelisten og trykk «Importer skjermbilde», eller del skjermbildet til " +
                                "Oppdragsliste fra bildevisningen. Sakene leses av på telefonen.",
                        )
                    }
                }
            }
            fun seksjon(nokkel: String, tittel: String, liste: List<Ordre>, farge: Color) {
                if (liste.isEmpty()) return
                item(key = "t-$nokkel") { Overskrift(tittel, liste.size, farge) }
                items(liste, key = { "o-${it.id}" }) { o ->
                    OrdreKort(
                        o, utvidet == o.id,
                        onKlikk = { utvidet = if (utvidet == o.id) null else o.id },
                        onUtfort = { onUtfort(o, it) },
                        onFakturert = { onFakturert(o, it) },
                        onLagOppdrag = { onLagOppdrag(o) },
                        onEndre = { redigeres = o },
                        onSlett = { onSlett(o); utvidet = null },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            seksjon("faktureres", "Må faktureres", faktureres, Color(0xFFEF6C00))
            seksjon("ikke", "Ikke utført", ikkeUtfort + fakturertIkkeUtfort, Color(0xFF1976D2))
            if (arkiv.isNotEmpty()) {
                item(key = "arkiv") {
                    TextButton(onClick = { visArkiv = !visArkiv }, modifier = Modifier.fillMaxWidth()) {
                        Text("Utført og fakturert (${arkiv.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(if (visArkiv) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                    }
                }
                if (visArkiv || sok.isNotBlank()) {
                    items(arkiv, key = { "o-${it.id}" }) { o ->
                        OrdreKort(
                            o, utvidet == o.id,
                            onKlikk = { utvidet = if (utvidet == o.id) null else o.id },
                            onUtfort = { onUtfort(o, it) },
                            onFakturert = { onFakturert(o, it) },
                            onLagOppdrag = { onLagOppdrag(o) },
                            onEndre = { redigeres = o },
                            onSlett = { onSlett(o); utvidet = null },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (import != null) OrdreImportDialog(import, onBekreftImport, onAvbrytImport)
    redigeres?.let { o ->
        OrdreRedigerDialog(
            ordre = o,
            onLagre = { ny -> onLagreManuelt(ny); redigeres = null },
            onLukk = { redigeres = null },
        )
    }
}

@Composable
private fun Overskrift(tittel: String, antall: Int, farge: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, start = 4.dp)) {
        Text(tittel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = farge)
        Spacer(Modifier.width(6.dp))
        Text("$antall", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrdreKort(
    o: Ordre,
    utvidet: Boolean,
    onKlikk: () -> Unit,
    onUtfort: (Boolean) -> Unit,
    onFakturert: (Boolean) -> Unit,
    onLagOppdrag: () -> Unit,
    onEndre: () -> Unit,
    onSlett: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bekreftSlett by remember(o.id) { mutableStateOf(false) }
    LaunchedEffect(utvidet) { if (!utvidet) bekreftSlett = false }
    Card(
        onClick = onKlikk,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SAK ${o.saksnr}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                val nar = listOfNotNull(o.dato?.let { formaterDato(it, LocalDate.now()) }, o.tid).joinToString(" ")
                if (nar.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(nar, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                o.tittel.ifBlank { "(uten tittel)" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = if (utvidet) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            val under = listOf(o.kunde, o.adresse).filter { it.isNotBlank() }.joinToString(" · ")
            if (under.isNotBlank()) {
                Text(under, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Avkrysning("Utført", o.utfort, Color(0xFF388E3C), onUtfort)
                Avkrysning("Fakturert", o.fakturert, Color(0xFF7B1FA2), onFakturert)
            }
            AnimatedVisibility(utvidet) {
                Column(Modifier.padding(top = 8.dp)) {
                    if (bekreftSlett) {
                        Text("Slette SAK ${o.saksnr} fra oversikten?", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedButton(onClick = { bekreftSlett = false }) { Text("Avbryt") }
                            Button(
                                onClick = onSlett,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            ) { Text("Slett") }
                        }
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (o.adresse.isNotBlank()) {
                                FilledTonalButton(onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(o.adresse))))
                                    }
                                }) {
                                    Icon(Icons.Outlined.Map, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Vis i kart")
                                }
                            }
                            FilledTonalButton(onClick = onEndre) {
                                Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Endre")
                            }
                            FilledTonalButton(onClick = onLagOppdrag) {
                                Icon(Icons.Outlined.AddTask, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Lag oppdrag")
                            }
                            FilledTonalButton(onClick = { bekreftSlett = true }) {
                                Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Slett")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Avkrysning(tekst: String, pa: Boolean, farge: Color, onEndre: (Boolean) -> Unit) {
    FilterChip(
        selected = pa,
        onClick = { onEndre(!pa) },
        label = { Text(tekst) },
        leadingIcon = if (pa) {
            { Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = farge.copy(alpha = 0.22f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            selectedLeadingIconColor = farge,
        ),
    )
}

/** Viser hva som ble funnet i skjermbildet(e) før det lagres. */
@Composable
private fun OrdreImportDialog(imp: OrdreImport, onBekreft: () -> Unit, onAvbryt: () -> Unit) {
    AlertDialog(
        onDismissRequest = onAvbryt,
        title = { Text(if (imp.laster) "Leser skjermbilde …" else "Funnet i skjermbildet") },
        text = {
            when {
                imp.laster -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Leser teksten på telefonen …")
                }
                imp.feil != null -> Text(imp.feil)
                else -> Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val nye = imp.funnet.count { !it.second }
                    Forklaring(
                        "${imp.funnet.size} saker" + when {
                            nye == imp.funnet.size -> ", alle nye."
                            nye == 0 -> ", alle finnes fra før (oppdateres hvis noe mangler)."
                            else -> ", $nye nye."
                        },
                    )
                    imp.funnet.forEach { (t, finnes) ->
                        Column {
                            Text(
                                "SAK ${t.saksnr}" + if (finnes) " · finnes" else " · ny",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (finnes) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            )
                            Text(t.tittel, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val under = listOf(t.kunde, t.adresse).filter { it.isNotBlank() }.joinToString(" · ")
                            if (under.isNotBlank()) Forklaring(under)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!imp.laster && imp.feil == null) Button(onClick = onBekreft) { Text("Legg til") }
        },
        dismissButton = { TextButton(onClick = onAvbryt) { Text(if (imp.feil != null) "Lukk" else "Avbryt") } },
    )
}
