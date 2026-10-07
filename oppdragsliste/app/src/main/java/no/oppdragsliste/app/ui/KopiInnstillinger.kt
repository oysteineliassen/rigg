package no.oppdragsliste.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun KopiInnstillinger(status: String?, onEksporter: (Uri) -> Unit, onImporter: (Uri) -> Unit) {
    val lagre = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(onEksporter)
    }
    val apne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImporter)
    }
    Gruppe("Sikkerhetskopi") {
        Forklaring(
            "Lagre alle oppdrag (også utførte) som en JSON-fil, f.eks. i Google Disk eller Nedlastinger. " +
                "Import legger til oppdragene fra filen; de som allerede finnes, hoppes over.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { lagre.launch("oppdragsliste-${LocalDate.now()}.json") }) { Text("Eksporter") }
            OutlinedButton(onClick = { apne.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                Text("Importer")
            }
        }
        status?.let { Forklaring(it) }
    }
}
