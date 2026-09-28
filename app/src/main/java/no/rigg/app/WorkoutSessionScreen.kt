package no.rigg.app

import android.os.SystemClock
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.ceil

/**
 * Øktmodus: økta gjennomføres ett sett om gangen. Oppvarming først, så hvert sett med forslag fra forrige gang,
 * hviletimeren starter av seg selv, og tidsøvelser får en nedtelling. Skjermen holdes på så lenge den vises.
 */
@Composable
fun WorkoutSessionScreen(vm: AppViewModel, day: WorkoutDay, state: GuidedWorkout) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val steps = remember(day) { sessionSteps(day) }
    val warmup = remember(day, vm.profile.equipment) { warmupFor(day, vm.profile.equipment) }
    var confirmExit by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // Topplinje: dagen, klokka, skjul og avslutt.
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(day.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                vm.sessionTimer?.takeIf { it.dayTitle == day.title }?.let { SessionClock(it) }
            }
            TextButton(onClick = { vm.hideGuided() }) { Text(tr("Hide", "Skjul")) }
            TextButton(onClick = { confirmExit = true }) { Text(tr("End", "Avslutt")) }
        }
        val progress = if (steps.isEmpty()) 1f else state.step.coerceAtMost(steps.size) / steps.size.toFloat()
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !state.warmupDone && warmup.isNotEmpty() ->
                    WarmupView(warmup, onDone = { vm.updateGuided(state.copy(warmupDone = true)) })
                state.step < steps.size -> key(state.step) {
                    SetView(vm, day, steps, state)
                }
                else -> SummaryView(vm, day, state)
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(tr("End the workout?", "Avslutte økta?")) },
            text = {
                Text(
                    tr(
                        "You can save the sets you've done so far, or discard the workout.",
                        "Du kan lagre settene du har gjort så langt, eller forkaste økta.",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; vm.finishGuided(day) }) { Text(tr("Save", "Lagre")) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmExit = false; vm.discardGuided() }) { Text(tr("Discard", "Forkast")) }
                    TextButton(onClick = { confirmExit = false }) { Text(tr("Continue", "Fortsett")) }
                }
            },
        )
    }
}

// ---------------------------------------------------------------- OPPVARMING

@Composable
private fun WarmupView(items: List<WarmupItem>, onDone: () -> Unit) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(0) }
    var endsAt by remember { mutableStateOf<Long?>(null) }
    var left by remember { mutableIntStateOf(items.first().seconds) }

    // Nedtelling gjennom øvelsene: pip de siste tre sekundene, så videre til neste.
    LaunchedEffect(endsAt, index) {
        val end = endsAt ?: return@LaunchedEffect
        var lastBeep = Int.MAX_VALUE
        while (true) {
            val ms = end - SystemClock.elapsedRealtime()
            val s = ceil(ms.coerceAtLeast(0) / 1000.0).toInt()
            left = s
            if (s in 1..3 && s < lastBeep) { Beeper.countdown(); lastBeep = s }
            if (ms <= 0) {
                Beeper.go()
                Haptics.vibrate(context, 300)
                if (index < items.lastIndex) {
                    index++
                    left = items[index].seconds
                    endsAt = SystemClock.elapsedRealtime() + items[index].seconds * 1000L
                } else {
                    endsAt = null
                    onDone()
                }
                break
            }
            delay(100)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            tr("Warm-up · about ${(items.sumOf { it.seconds } + 30) / 60} min", "Oppvarming · ca. ${(items.sumOf { it.seconds } + 30) / 60} min"),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
        )
        items.forEachIndexed { i, item ->
            val current = i == index
            Card(
                Modifier.fillMaxWidth(),
                colors = if (current) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        Modifier.weight(1f),
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        color = if (i < index) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (current && endsAt != null) formatDuration(left) else formatDuration(item.seconds),
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDone) { Text(tr("Skip warm-up", "Hopp over oppvarming")) }
            Spacer(Modifier.weight(1f))
            if (endsAt == null) {
                Button(onClick = {
                    left = items[index].seconds
                    endsAt = SystemClock.elapsedRealtime() + items[index].seconds * 1000L
                }) { Text(tr("Start", "Start")) }
            } else {
                FilledTonalButton(onClick = {
                    // Neste øvelse med en gang.
                    if (index < items.lastIndex) {
                        index++
                        left = items[index].seconds
                        endsAt = SystemClock.elapsedRealtime() + items[index].seconds * 1000L
                    } else {
                        endsAt = null
                        onDone()
                    }
                }) { Text(tr("Next", "Neste")) }
            }
        }
    }
}

// ---------------------------------------------------------------- ETT SETT

@Composable
private fun SetView(vm: AppViewModel, day: WorkoutDay, steps: List<WorkStep>, state: GuidedWorkout) {
    val step = steps[state.step]
    val pe = step.pe
    val slot = step.exerciseNo - 1
    val ex = ExerciseLibrary.get(pe.exerciseId)
    if (ex == null) {
        // Ukjent øvelse (f.eks. fra et gammelt AI-program): hopp over.
        LaunchedEffect(Unit) { vm.updateGuided(state.copy(step = state.step + 1)) }
        return
    }
    val weights = remember(ex, vm.profile) { weightOptions(ex, vm.profile) }
    val previous = state.done.find { it.slot == slot && it.setIndex == step.setIndex }
    val target = remember(step) { setTarget(ex, pe, step.setIndex, vm.logs, weights) }
    // Samme sett gjort tidligere i økta (tilbake-knappen) vinner; ellers forrige sett i dag; ellers forslaget.
    val earlierToday = state.done.filter { it.slot == slot && it.setIndex < step.setIndex }.maxByOrNull { it.setIndex }
    var reps by remember { mutableIntStateOf(previous?.reps ?: target.reps) }
    var weight by remember { mutableStateOf(previous?.weightKg ?: earlierToday?.weightKg ?: target.weightKg) }

    fun complete(value: Int) {
        val done = state.done.filterNot { it.slot == slot && it.setIndex == step.setIndex } +
            DoneSet(pe.exerciseId, slot, step.setIndex, value, if (ex.isWeighted()) weight else null)
        vm.updateGuided(state.copy(step = state.step + 1, done = done))
        if (step.restAfter > 0) vm.startRestTimer(step.restAfter)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val superset = if (pe.group.isNotEmpty()) tr(" · superset ${pe.group}", " · supersett ${pe.group}") else ""
        Text(
            tr(
                "Exercise ${step.exerciseNo} of ${step.exerciseCount} · set ${step.setIndex + 1} of ${pe.sets}$superset",
                "Øvelse ${step.exerciseNo} av ${step.exerciseCount} · sett ${step.setIndex + 1} av ${pe.sets}$superset",
            ),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ExerciseIllustration(ex, modifier = Modifier.size(180.dp), animate = true)
        Text(ex.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            tr("Target: ${pe.reps}", "Mål: ${pe.reps}") +
                (target.weightKg?.let { tr(" · suggested ${formatWeight(it)} kg", " · forslag ${formatWeight(it)} kg") } ?: ""),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (pe.tip.isNotBlank()) {
            Text(pe.tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }

        if (ex.timed) {
            WorkTimer(
                seconds = reps,
                onSecondsChange = { reps = it },
                onFinished = { complete(it) },
            )
        } else {
            Stepper(
                label = tr("Reps", "Reps"),
                value = reps.toString(),
                onMinus = { if (reps > 0) reps-- },
                onPlus = { reps++ },
            )
        }
        if (ex.isWeighted()) {
            Stepper(
                label = tr("Weight (kg)", "Vekt (kg)"),
                value = weight?.let { formatWeight(it) } ?: "–",
                onMinus = { weight = stepWeight(weight, weights, up = false) },
                onPlus = { weight = stepWeight(weight, weights, up = true) },
            )
            if (weights.isEmpty()) {
                Text(
                    tr(
                        "Tip: enter the weights you own on the Setup tab, and the buttons will step between them.",
                        "Tips: legg inn vektene du har på Oppsett-fanen, så hopper knappene mellom dem.",
                    ),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        if (!ex.timed) {
            Button(onClick = { complete(reps) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(tr("Set done", "Ferdig sett"), fontSize = 18.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                onClick = { vm.updateGuided(state.copy(step = state.step - 1)) },
                enabled = state.step > 0,
            ) { Text(tr("Back", "Tilbake")) }
            TextButton(onClick = { vm.updateGuided(state.copy(step = state.step + 1)) }) { Text(tr("Skip set", "Hopp over sett")) }
        }

        steps.getOrNull(state.step + 1)?.let { next ->
            val nextEx = ExerciseLibrary.get(next.pe.exerciseId)
            HorizontalDivider()
            Text(
                tr("Next: ${nextEx?.name ?: next.pe.exerciseId}, set ${next.setIndex + 1}", "Neste: ${nextEx?.name ?: next.pe.exerciseId}, sett ${next.setIndex + 1}") +
                    if (step.restAfter > 0) tr(" (after ${step.restAfter} s rest)", " (etter ${step.restAfter} sek pause)") else tr(" (straight away)", " (rett etterpå)"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
        // Plass til hviletimerbaren nederst.
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Uten innvendig luft: standardpaddingen (24 dp på hver side) ville klemt tegnet i en 56 dp knapp.
            OutlinedButton(onClick = onMinus, modifier = Modifier.size(56.dp), contentPadding = PaddingValues(0.dp)) { Text("−", fontSize = 22.sp) }
            Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.Center)
            OutlinedButton(onClick = onPlus, modifier = Modifier.size(56.dp), contentPadding = PaddingValues(0.dp)) { Text("+", fontSize = 22.sp) }
        }
    }
}

/**
 * Nedtelling for tidsøvelser (planke, veggsitt ...): juster sekundene, trykk Start. Pip de siste tre sekundene
 * og en høyere tone ved null, som hviletimeren. Stopper man tidlig, logges tiden man holdt.
 */
@Composable
private fun WorkTimer(seconds: Int, onSecondsChange: (Int) -> Unit, onFinished: (Int) -> Unit) {
    val context = LocalContext.current
    var endsAt by remember { mutableStateOf<Long?>(null) }
    var left by remember { mutableIntStateOf(seconds) }

    LaunchedEffect(endsAt) {
        val end = endsAt ?: return@LaunchedEffect
        var lastBeep = Int.MAX_VALUE
        while (true) {
            val ms = end - SystemClock.elapsedRealtime()
            val s = ceil(ms.coerceAtLeast(0) / 1000.0).toInt()
            left = s
            if (s in 1..3 && s < lastBeep) { Beeper.countdown(); lastBeep = s }
            if (ms <= 0) {
                Beeper.go()
                Haptics.vibrate(context, 400)
                onFinished(seconds)
                break
            }
            delay(100)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val running = endsAt != null
        Text(
            formatDuration(if (running) left else seconds),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
        )
        if (!running) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onSecondsChange((seconds - 5).coerceAtLeast(5)) }) { Text(tr("−5 s", "−5 sek")) }
                OutlinedButton(onClick = { onSecondsChange(seconds + 5) }) { Text(tr("+5 s", "+5 sek")) }
            }
            Button(
                onClick = {
                    left = seconds
                    endsAt = SystemClock.elapsedRealtime() + seconds * 1000L
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(tr("Start", "Start"), fontSize = 18.sp) }
            TextButton(onClick = { onFinished(seconds) }) { Text(tr("Log without timer", "Logg uten tidtaker")) }
        } else {
            FilledTonalButton(
                onClick = {
                    val held = (seconds - left).coerceAtLeast(0)
                    endsAt = null
                    onFinished(held)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(tr("Stop and log the time held", "Stopp og logg tiden"), fontSize = 16.sp) }
        }
    }
}

// ---------------------------------------------------------------- OPPSUMMERING

@Composable
private fun SummaryView(vm: AppViewModel, day: WorkoutDay, state: GuidedWorkout) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(tr("Workout done!", "Økta er ferdig!"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        day.exercises.forEachIndexed { slot, pe ->
            val ex = ExerciseLibrary.get(pe.exerciseId)
            val sets = state.done.filter { it.slot == slot }.sortedBy { it.setIndex }
            val unit = if (ex?.timed == true) tr(" s", " sek") else ""
            val text = if (sets.isEmpty()) tr("skipped", "hoppet over") else sets.joinToString(", ") { s ->
                (s.weightKg?.let { "${formatWeight(it)} kg × " } ?: "") + "${s.reps}$unit"
            }
            Column {
                Text(ex?.name ?: pe.exerciseId, fontWeight = FontWeight.Medium)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.finishGuided(day) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(tr("Save workout", "Lagre økta"), fontSize = 18.sp)
        }
        TextButton(onClick = { vm.updateGuided(state.copy(step = (state.step - 1).coerceAtLeast(0))) }) {
            Text(tr("Back to the last set", "Tilbake til siste sett"))
        }
    }
}
