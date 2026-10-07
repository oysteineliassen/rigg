package no.oppdragsliste.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import no.oppdragsliste.app.data.Oppdrag

/** Data for «I nærheten»-visningen: oppdrag sortert på avstand, med avstand i meter (null = ukjent). */
data class NaerVisning(val oppdrag: List<Pair<Oppdrag, Float?>>, val laster: Boolean = false, val feil: String? = null)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListeSkjerm(
    tilstand: ListeTilstand,
    sok: String,
    onSok: (String) -> Unit,
    onNytt: () -> Unit,
    onEndre: (Oppdrag) -> Unit,
    onFerdig: (Oppdrag, Boolean) -> Unit,
    onSlett: (Oppdrag) -> Unit,
    onInnstillinger: () -> Unit,
    naer: NaerVisning?,
    onNaer: ((Boolean) -> Unit)?,
) {
    var utvidet by rememberSaveable { mutableStateOf<Long?>(null) }
    var visUtforte by rememberSaveable { mutableStateOf(false) }
    fun klikk(o: Oppdrag) { utvidet = if (utvidet == o.id) null else o.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Oppdragsliste", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onInnstillinger) { Icon(Icons.Outlined.Settings, "Innstillinger") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNytt,
                icon = { Icon(Icons.Outlined.Add, null, Modifier.size(28.dp)) },
                text = { Text("Nytt oppdrag", style = MaterialTheme.typography.titleMedium) },
                modifier = Modifier.height(64.dp),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innPadding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "sok") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = sok,
                        onValueChange = onSok,
                        placeholder = { Text("Søk i sted, kunde, beskrivelse, telefon") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = if (sok.isNotEmpty()) {
                            { IconButton(onClick = { onSok("") }) { Icon(Icons.Outlined.Close, "Tøm søk") } }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (onNaer != null) item(key = "naer-knapp") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = naer != null,
                        onClick = { onNaer(naer == null) },
                        label = { Text("I nærheten") },
                        leadingIcon = { Icon(Icons.Outlined.NearMe, null, Modifier.size(18.dp)) },
                    )
                }
            }

            if (naer != null) {
                naerInnhold(naer, tilstand, sok, utvidet, ::klikk, onFerdig, onEndre, onSlett)
            } else {
                if (tilstand.lastet && tilstand.seksjoner.isEmpty()) {
                    item(key = "tom") {
                        TomTekst(
                            if (sok.isBlank()) "Ingen åpne oppdrag. Trykk «Nytt oppdrag» for å legge inn et."
                            else "Ingen åpne oppdrag passer søket.",
                        )
                    }
                }
                tilstand.seksjoner.forEach { seksjon ->
                    item(key = "s-${seksjon.prioritet}") {
                        SeksjonsTittel(seksjon.prioritet.tittel, seksjon.oppdrag.size, seksjon.prioritet.farge())
                    }
                    items(seksjon.oppdrag, key = { it.id }) { o ->
                        OppdragKort(
                            o = o, na = tilstand.na, utvidet = utvidet == o.id,
                            onKlikk = { klikk(o) },
                            onFerdig = { f -> onFerdig(o, f); utvidet = null },
                            onEndre = { onEndre(o) },
                            onSlett = { onSlett(o); utvidet = null },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            if (tilstand.utforte.isNotEmpty()) {
                item(key = "utfort-tittel") {
                    TextButton(onClick = { visUtforte = !visUtforte }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Utført (${tilstand.utforte.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(if (visUtforte) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                    }
                }
                if (visUtforte) {
                    items(tilstand.utforte, key = { "u-${it.id}" }) { o ->
                        OppdragKort(
                            o = o, na = tilstand.na, utvidet = utvidet == o.id,
                            onKlikk = { klikk(o) },
                            onFerdig = { f -> onFerdig(o, f); utvidet = null },
                            onEndre = { onEndre(o) },
                            onSlett = { onSlett(o); utvidet = null },
                            visPrioritet = true,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.naerInnhold(
    naer: NaerVisning,
    tilstand: ListeTilstand,
    sok: String,
    utvidet: Long?,
    klikk: (Oppdrag) -> Unit,
    onFerdig: (Oppdrag, Boolean) -> Unit,
    onEndre: (Oppdrag) -> Unit,
    onSlett: (Oppdrag) -> Unit,
) {
    when {
        naer.laster -> item(key = "naer-laster") { TomTekst("Finner posisjonen din …") }
        naer.feil != null -> item(key = "naer-feil") { TomTekst(naer.feil) }
        else -> {
            // Søket gjelder også her: vis bare oppdrag som er med i den filtrerte listen.
            val synlige = tilstand.seksjoner.flatMap { it.oppdrag }.map { it.id }.toSet()
            val liste = naer.oppdrag.filter { it.first.id in synlige }
            if (liste.isEmpty()) {
                item(key = "naer-tom") { TomTekst(if (sok.isBlank()) "Ingen åpne oppdrag." else "Ingen åpne oppdrag passer søket.") }
            }
            item(key = "naer-tittel") { SeksjonsTittel("Nærmest først", liste.size, MaterialTheme.colorScheme.primary) }
            items(liste, key = { "n-${it.first.id}" }) { (o, meter) ->
                OppdragKort(
                    o = o, na = tilstand.na, utvidet = utvidet == o.id,
                    onKlikk = { klikk(o) },
                    onFerdig = { f -> onFerdig(o, f) },
                    onEndre = { onEndre(o) },
                    onSlett = { onSlett(o) },
                    avstand = meter?.let { formaterAvstand(it) } ?: "ukjent",
                    visPrioritet = true,
                )
            }
        }
    }
}

@Composable
private fun SeksjonsTittel(tittel: String, antall: Int, farge: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.padding(top = 14.dp, bottom = 2.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(farge),
        )
        Spacer(Modifier.width(8.dp))
        Text(tittel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text("$antall", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TomTekst(tekst: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tekst, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
