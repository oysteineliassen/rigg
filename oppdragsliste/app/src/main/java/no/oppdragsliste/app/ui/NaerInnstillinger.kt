package no.oppdragsliste.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.oppdragsliste.app.app
import no.oppdragsliste.app.data.Oppsett
import no.oppdragsliste.app.sted.harBakgrunnsposisjon

private val radiusValg = listOf(500, 1000, 2000, 3000, 5000, 10_000)

private fun radiusTekst(m: Int) = if (m < 1000) "$m m" else "${m / 1000} km"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NaerInnstillinger(
    oppsett: Oppsett,
    onEndre: ((Oppsett) -> Oppsett) -> Unit,
    geokodStatus: String?,
    onGeokod: () -> Unit,
) {
    val context = LocalContext.current
    val teller = gjenopptattTeller()
    val forgrunn = remember(teller) { harTillatelse(context, Manifest.permission.ACCESS_FINE_LOCATION) }
    val bakgrunn = remember(teller) { harBakgrunnsposisjon(context) }

    val beForgrunn = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    // På Android 11+ åpner denne systemsiden der man velger «Tillat hele tiden».
    val beBakgrunn = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Gruppe("Du er i området") {
        Forklaring(
            "Appen kan si fra når du kommer i nærheten av åpne oppdrag, f.eks. «3 oppdrag i nærheten: " +
                "Søgne (2), Tangvall (1)». Samme oppdrag varsles høyst én gang per dag.",
        )
        Forklaring(
            "Koordinatene finnes fra sted og adresse når du lagrer. Android tillater 100 områder; har du flere " +
                "oppdrag, prioriteres akutte og haster, deretter de eldste.",
        )
        // Når tilgangen er gitt (eller innstillingen endres), registreres geofencene på nytt.
        LaunchedEffect(bakgrunn, oppsett.naerVarsel) { context.app.planleggAltSenere() }
        BryterRad("Varsle når jeg er i nærheten", oppsett.naerVarsel) { pa ->
            onEndre { it.copy(naerVarsel = pa) }
        }

        if (oppsett.naerVarsel && !bakgrunn) {
            Text("To trinn trengs:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            TillatelseLinje("1. Posisjon mens appen er i bruk", forgrunn)
            if (!forgrunn) {
                Forklaring("Velg «Nøyaktig» og «Mens appen er i bruk».")
                Button(onClick = {
                    beForgrunn.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { Text("Gi tilgang til posisjon") }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                TillatelseLinje("2. Posisjon hele tiden", bakgrunn)
                if (forgrunn) {
                    Forklaring(
                        "For å varsle mens appen er lukket, må posisjon være tillatt «hele tiden». " +
                            "Velg «Tillat hele tiden» på neste side.",
                    )
                    Button(onClick = { beBakgrunn.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }) {
                        Text("Tillat hele tiden")
                    }
                    OutlinedButton(onClick = { apneAppInnstillinger(context) }) { Text("Åpne app-innstillinger") }
                }
            }
        }

        Forklaring("Avstand:")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            radiusValg.forEach { r ->
                FilterChip(
                    selected = oppsett.naerRadiusMeter == r,
                    onClick = { onEndre { it.copy(naerRadiusMeter = r) } },
                    label = { Text(radiusTekst(r)) },
                )
            }
        }
        OutlinedButton(onClick = onGeokod) { Text("Finn koordinater for oppdrag som mangler") }
        geokodStatus?.let { Forklaring(it) }
    }
}
