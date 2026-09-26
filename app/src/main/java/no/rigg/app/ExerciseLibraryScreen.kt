package no.rigg.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

// ---------------------------------------------------------------- Norske etiketter

val Pattern.label: String
    get() = when (this) {
        Pattern.PUSH -> "Press"
        Pattern.PULL -> "Trekk"
        Pattern.SQUAT -> "Knebøy"
        Pattern.LUNGE -> "Utfall og ettbeins"
        Pattern.HINGE -> "Hofte og bakside"
        Pattern.CORE -> "Kjerne"
        Pattern.CARDIO -> "Kondisjon"
    }

val Muscle.label: String
    get() = when (this) {
        Muscle.CHEST -> "Bryst"
        Muscle.BACK -> "Rygg"
        Muscle.SHOULDERS -> "Skuldre"
        Muscle.BICEPS -> "Biceps"
        Muscle.TRICEPS -> "Triceps"
        Muscle.QUADS -> "Forside lår"
        Muscle.GLUTES_HAMS -> "Sete og bakside lår"
        Muscle.CALVES -> "Legger"
        Muscle.ABS -> "Mage"
        Muscle.FULL_BODY -> "Helkropp"
    }

private fun levelLabel(level: Int): String = when (level) {
    1 -> "Lett"
    2 -> "Middels"
    else -> "Krevende"
}

/** F.eks. "Manualer + Benk", "Stol eller Benk", eller "Kroppsvekt". */
fun Exercise.equipmentText(): String {
    val allNames = requiresAll.filter { it != Equipment.BODYWEIGHT }.map { it.label }
    val anyNames = requiresAny.filter { it != Equipment.BODYWEIGHT }.map { it.label }
    val anyText = when {
        anyNames.isEmpty() -> null
        anyNames.size == 1 -> anyNames.first()
        else -> anyNames.joinToString(" eller ")
    }
    val parts = allNames + listOfNotNull(if (anyText != null && allNames.isNotEmpty() && anyNames.size > 1) "($anyText)" else anyText)
    return if (parts.isEmpty()) "Kroppsvekt" else parts.joinToString(" + ")
}

/** Det som mangler for å kunne gjøre øvelsen med utstyret man har, eller null hvis alt er der. */
private fun Exercise.missingText(owned: Set<Equipment>): String? {
    val have = owned + Equipment.BODYWEIGHT
    val missing = requiresAll.filter { it !in have }.map { it.label }.toMutableList()
    if (requiresAny.isNotEmpty() && requiresAny.none { it in have }) {
        missing += requiresAny.joinToString(" eller ") { it.label }
    }
    return if (missing.isEmpty()) null else missing.joinToString(", ")
}

// ---------------------------------------------------------------- Skjermen

/** Oversikt over hele øvelsesbiblioteket, gruppert etter bevegelse, med søk og filter på eget utstyr. */
@Composable
fun ExerciseLibraryScreen(vm: AppViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var onlyMine by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Exercise?>(null) }

    val owned = vm.profile.equipment
    val availableIds = remember(owned) { ExerciseLibrary.available(owned).map { it.id }.toSet() }
    val q = query.trim()
    val filtered = ExerciseLibrary.all.filter { ex ->
        (!onlyMine || ex.id in availableIds) &&
            (q.isEmpty() || ex.name.contains(q, ignoreCase = true) || ex.equipmentText().contains(q, ignoreCase = true) ||
                ex.muscles.any { it.label.contains(q, ignoreCase = true) })
    }
    val grouped = Pattern.values().mapNotNull { p -> filtered.filter { it.pattern == p }.takeIf { it.isNotEmpty() }?.let { p to it } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column {
                Text("Øvelser", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${ExerciseLibrary.all.size} øvelser · ${availableIds.size} kan gjøres med utstyret ditt",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Søk etter navn, utstyr eller muskel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                FilterChip(selected = onlyMine, onClick = { onlyMine = !onlyMine }, label = { Text("Bare med mitt utstyr") })
            }
        }

        if (grouped.isEmpty()) {
            item {
                Text(
                    "Ingen øvelser passer søket.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }

        grouped.forEach { (pattern, exercises) ->
            item(key = "header_${pattern.name}") {
                Text(
                    "${pattern.label} (${exercises.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
            }
            items(exercises, key = { it.id }) { ex ->
                ExerciseRow(ex, missing = ex.missingText(owned), onClick = { selected = ex })
            }
        }
    }

    selected?.let { ex -> ExerciseInfoDialog(ex, missing = ex.missingText(owned), onDismiss = { selected = null }) }
}

@Composable
private fun ExerciseRow(ex: Exercise, missing: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .alpha(if (missing == null) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseThumbnail(ex, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ex.name, fontWeight = FontWeight.Medium)
            Text(
                ex.equipmentText() + " · " + levelLabel(ex.level),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (missing != null) {
                Text("Mangler: $missing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ExerciseInfoDialog(ex: Exercise, missing: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ExerciseIllustration(ex, modifier = Modifier.size(160.dp), animate = true)
                Spacer(Modifier.height(12.dp))
                Text(ex.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${ex.pattern.label} · ${levelLabel(ex.level)}" + if (ex.timed) " · på tid" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                InfoLine("Utstyr", ex.equipmentText())
                InfoLine("Muskler", ex.muscles.joinToString(", ") { it.label })
                if (missing != null) {
                    Text(
                        "Du mangler: $missing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
                if (ex.note.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(ex.note, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { openExerciseVideo(context, ex) }) { Text("Se video") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss) { Text("Lukk") }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
