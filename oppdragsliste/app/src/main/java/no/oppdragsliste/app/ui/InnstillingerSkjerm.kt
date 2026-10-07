package no.oppdragsliste.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.oppdragsliste.app.data.Oppsett
import no.oppdragsliste.app.data.Tema

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InnstillingerSkjerm(
    oppsett: Oppsett,
    onEndre: ((Oppsett) -> Oppsett) -> Unit,
    onTilbake: () -> Unit,
    ekstra: @Composable ColumnScope.() -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Innstillinger") },
                navigationIcon = {
                    IconButton(onClick = onTilbake) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Tilbake") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Gruppe("Utseende") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tema.entries.forEach { t ->
                        FilterChip(
                            selected = oppsett.tema == t,
                            onClick = { onEndre { it.copy(tema = t) } },
                            label = { Text(t.tittel) },
                        )
                    }
                }
            }
            ekstra()
        }
    }
}

/** Et kort med overskrift og innhold, brukt for hver del av innstillingene. */
@Composable
fun Gruppe(tittel: String, innhold: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tittel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            innhold()
        }
    }
}

/** Tekst + bryter på én linje. */
@Composable
fun BryterRad(tekst: String, pa: Boolean, aktiv: Boolean = true, onEndre: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tekst, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = pa, onCheckedChange = onEndre, enabled = aktiv)
    }
}

@Composable
fun Forklaring(tekst: String) {
    Text(tekst, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
