package no.rigg.app

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.ceil

/**
 * Øktmodus: økta gjennomføres ett sett om gangen. Oppvarming først, så hvert sett med forslag fra forrige gang,
 * hvilen vises som en stor nedtelling mellom settene, og tidsøvelser får en egen nedtelling.
 * Skjermen holdes på så lenge den vises.
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
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        // Topplinje: avslutt, dagen og klokka, skjul.
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { confirmExit = true }) {
                Icon(RiggIcons.Close, contentDescription = tr("End", "Avslutt"), tint = colors.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                vm.sessionTimer?.takeIf { it.dayTitle == day.title }?.let {
                    SessionClock(it, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Condensed, letterSpacing = 0.6.sp), color = colors.onSurfaceVariant)
                }
            }
            IconButton(onClick = { vm.hideGuided() }) {
                Icon(RiggIcons.ChevronDown, contentDescription = tr("Hide", "Skjul"), tint = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        StepProgress(steps.size, state.step)
        Spacer(Modifier.height(14.dp))

        val resting = vm.restTimer
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !state.warmupDone && warmup.isNotEmpty() ->
                    WarmupView(warmup, onDone = { vm.updateGuided(state.copy(warmupDone = true)) })
                resting != null && state.step in 1 until steps.size ->
                    RestView(vm, resting, steps, state)
                state.step < steps.size -> key(state.step) {
                    SetView(vm, steps, state)
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

/** Én bit per sett: gjort (rust), nå (lys), igjen (grå). */
@Composable
private fun StepProgress(count: Int, current: Int) {
    if (count == 0) return
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (count > 24) 2.dp else 3.dp)) {
        repeat(count) { i ->
            Box(
                Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(
                    when {
                        i < current -> colors.secondary
                        i == current -> colors.onSurface
                        else -> colors.surfaceContainerHighest
                    },
                ),
            )
        }
    }
}

@Composable
private fun RoundButton(text: String, contentDescription: String, onClick: () -> Unit, size: Int = 60) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(size.dp).semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) { Text(text, fontSize = 26.sp, fontFamily = BodyFont) }
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

    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel(tr("Warm-up · about ${(items.sumOf { it.seconds } + 30) / 60} min", "Oppvarming · ca. ${(items.sumOf { it.seconds } + 30) / 60} min"))
        Text(items[index].name, style = MaterialTheme.typography.headlineMedium)
        Text(
            formatDuration(if (endsAt != null) left else items[index].seconds),
            style = MaterialTheme.typography.displayLarge,
            color = if (endsAt != null) colors.onSurface else colors.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        items.forEachIndexed { i, item ->
            val current = i == index
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (current) colors.primaryContainer else colors.surfaceContainer)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (i < index) CheckDot(18.dp) else Spacer(Modifier.width(18.dp))
                Text(
                    item.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        current -> colors.onPrimaryContainer
                        i < index -> colors.onSurfaceVariant
                        else -> colors.onSurface
                    },
                )
                Text(formatDuration(item.seconds), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (endsAt == null) {
            BigButton(tr("Start warm-up", "Start oppvarming"), onClick = {
                left = items[index].seconds
                endsAt = SystemClock.elapsedRealtime() + items[index].seconds * 1000L
            }, icon = RiggIcons.Play)
        } else {
            BigButton(tr("Next", "Neste"), onClick = {
                // Neste øvelse med en gang.
                if (index < items.lastIndex) {
                    index++
                    left = items[index].seconds
                    endsAt = SystemClock.elapsedRealtime() + items[index].seconds * 1000L
                } else {
                    endsAt = null
                    onDone()
                }
            })
        }
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(tr("Skip warm-up", "Hopp over oppvarming"))
        }
    }
}

// ---------------------------------------------------------------- ETT SETT

@Composable
private fun SetView(vm: AppViewModel, steps: List<WorkStep>, state: GuidedWorkout) {
    val step = steps[state.step]
    val pe = step.pe
    val slot = step.exerciseNo - 1
    val ex = ExerciseLibrary.get(pe.exerciseId)
    if (ex == null) {
        // Ukjent øvelse (f.eks. fra et gammelt AI-program): hopp over.
        LaunchedEffect(Unit) { vm.updateGuided(state.copy(step = state.step + 1)) }
        return
    }
    val colors = MaterialTheme.colorScheme
    val weights = remember(ex, vm.profile) { weightOptions(ex, vm.profile) }
    val previous = state.done.find { it.slot == slot && it.setIndex == step.setIndex }
    val target = remember(step) { setTarget(ex, pe, step.setIndex, vm.logs, weights) }
    val lastTime = remember(ex) { lastSetSummary(vm.logs, ex) }
    val lastWeight = remember(ex) { ProgressionAdvisor.lastResult(vm.logs, ex.id)?.sets?.mapNotNull { it.weightKg }?.maxOrNull() }
    // Samme sett gjort tidligere i økta (tilbake-knappen) vinner; ellers forrige sett i dag; ellers forslaget.
    val earlierToday = state.done.filter { it.slot == slot && it.setIndex < step.setIndex }.maxByOrNull { it.setIndex }
    var reps by remember { mutableIntStateOf(previous?.reps ?: target.reps) }
    var weight by remember { mutableStateOf(previous?.weightKg ?: earlierToday?.weightKg ?: target.weightKg) }

    fun complete(value: Int) {
        val done = state.done.filterNot { it.slot == slot && it.setIndex == step.setIndex } +
            DoneSet(pe.exerciseId, slot, step.setIndex, value, if (ex.isWeighted()) weight else null)
        vm.updateGuided(state.copy(step = state.step + 1, done = done))
        if (step.restAfter > 0 && state.step + 1 < steps.size) vm.startRestTimer(step.restAfter)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(
                tr(
                    "Exercise ${step.exerciseNo} of ${step.exerciseCount} · set ${step.setIndex + 1} of ${pe.sets}",
                    "Øvelse ${step.exerciseNo} av ${step.exerciseCount} · sett ${step.setIndex + 1} av ${pe.sets}",
                ),
                Modifier.weight(1f),
            )
            if (pe.group.isNotEmpty()) {
                val pos = steps.filter { it.pe.group == pe.group }.map { it.pe.exerciseId }.distinct().indexOf(pe.exerciseId) + 1
                Text(
                    tr("SUPERSET ${pe.group}$pos", "SUPERSETT ${pe.group}$pos"),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.tertiary,
                    modifier = Modifier.border(1.dp, colors.tertiary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        // Scenen med figuren.
        Box(
            Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(24.dp)).background(colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            ExerciseIllustration(ex, modifier = Modifier.size(200.dp), animate = true)
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(ex.name, style = MaterialTheme.typography.headlineMedium)
            Text(
                tr("Target ${pe.reps}", "Mål ${pe.reps}") + (lastTime?.let { tr(" · last $it", " · sist $it") } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            if (pe.tip.isNotBlank()) {
                Text(pe.tip, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }

        if (ex.timed) {
            WorkTimer(
                seconds = reps,
                onSecondsChange = { reps = it },
                onFinished = { complete(it) },
            )
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundButton("−", tr("Fewer reps", "Færre reps"), onClick = { if (reps > 0) reps-- })
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(reps.toString(), style = MaterialTheme.typography.displayLarge)
                    SectionLabel("Reps")
                }
                RoundButton("+", tr("More reps", "Flere reps"), onClick = { reps++ })
            }
        }

        if (ex.isWeighted()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(tr("Weight", "Vekt"), Modifier.weight(1f))
                    val w = weight
                    if (w != null && lastWeight != null && w > lastWeight + 0.001) {
                        Icon(RiggIcons.ArrowUp, contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            tr("up from ${formatWeight(lastWeight)} kg", "opp fra ${formatWeight(lastWeight)} kg"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.tertiary,
                        )
                    }
                }
                if (weights.isNotEmpty()) {
                    val window = weightWindow(weights, weight)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        window.forEach { w ->
                            val selected = weight?.let { kotlin.math.abs(it - w) < 0.001 } == true
                            Box(
                                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                                    .then(
                                        if (selected) Modifier.background(colors.inverseSurface)
                                        else Modifier.border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp)),
                                    )
                                    .clickable { weight = w },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (selected) "${formatWeight(w)} kg" else formatWeight(w),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = Condensed),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (selected) colors.inverseOnSurface else colors.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    // Flere vekter enn det er plass til: pilene flytter vinduet.
                    if (weights.size > window.size) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { weight = stepWeight(weight, weights, up = false) }) { Text(tr("− lighter", "− lettere")) }
                            TextButton(onClick = { weight = stepWeight(weight, weights, up = true) }) { Text(tr("heavier +", "tyngre +")) }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RoundButton("−", tr("Lighter", "Lettere"), onClick = { weight = stepWeight(weight, weights, up = false) }, size = 48)
                        Text(
                            weight?.let { "${formatWeight(it)} kg" } ?: "–",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                        RoundButton("+", tr("Heavier", "Tyngre"), onClick = { weight = stepWeight(weight, weights, up = true) }, size = 48)
                    }
                    Text(
                        tr(
                            "Tip: enter the weights you own on the Setup tab, and you can tap them here.",
                            "Tips: legg inn vektene du har på Oppsett-fanen, så kan du trykke på dem her.",
                        ),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    )
                }
            }
        }

        if (!ex.timed) {
            Spacer(Modifier.height(4.dp))
            BigButton(tr("Set done", "Ferdig sett"), onClick = { complete(reps) }, icon = RiggIcons.Check)
        }

        steps.getOrNull(state.step + 1)?.let { next ->
            val nextEx = ExerciseLibrary.get(next.pe.exerciseId)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (nextEx != null) ExerciseTile(nextEx, size = 36.dp)
                Text(
                    tr("Next: ", "Neste: ") + (nextEx?.name ?: next.pe.exerciseId) +
                        tr(", set ${next.setIndex + 1}", ", sett ${next.setIndex + 1}") + " · " +
                        if (step.restAfter > 0) tr("after ${step.restAfter} s rest", "etter ${step.restAfter} sek pause") else tr("straight away", "rett etterpå"),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                onClick = { vm.updateGuided(state.copy(step = state.step - 1)) },
                enabled = state.step > 0,
            ) { Text(tr("Back", "Tilbake")) }
            TextButton(onClick = { vm.updateGuided(state.copy(step = state.step + 1)) }) { Text(tr("Skip set", "Hopp over sett")) }
        }
    }
}

// ---------------------------------------------------------------- HVILE

@Composable
private fun RestView(vm: AppViewModel, timer: RestTimer, steps: List<WorkStep>, state: GuidedWorkout) {
    @Suppress("UNUSED_VARIABLE")
    val tick = rememberTick(active = true, periodMs = 100L)
    val msLeft = timer.remainingMs().coerceAtLeast(0)
    val left = timer.secondsLeft()
    val fraction = if (timer.totalSeconds <= 0) 0f else (msLeft / (timer.totalSeconds * 1000f)).coerceIn(0f, 1f)
    val colors = MaterialTheme.colorScheme

    val lastStep = steps[state.step - 1]
    val lastEx = ExerciseLibrary.get(lastStep.pe.exerciseId)
    val lastDone = state.done.find { it.slot == lastStep.exerciseNo - 1 && it.setIndex == lastStep.setIndex }
    val next = steps[state.step]
    val nextEx = ExerciseLibrary.get(next.pe.exerciseId)

    Column(Modifier.fillMaxSize().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Siste sett, med mulighet til å rette det.
        if (lastEx != null && lastDone != null) {
            val unit = if (lastEx.timed) tr(" s", " sek") else ""
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainer)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(RiggIcons.Check, contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(16.dp))
                Text(
                    "${lastEx.name} · ${tr("set", "sett")} ${lastStep.setIndex + 1}: " +
                        "${lastDone.reps}$unit" + (lastDone.weightKg?.let { " × ${formatWeight(it)} kg" } ?: ""),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = {
                    vm.stopRestTimer()
                    vm.updateGuided(state.copy(step = state.step - 1))
                }) { Text(tr("Edit", "Endre")) }
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            SectionLabel(tr("Rest", "Hvile"), color = colors.onSurfaceVariant)
            Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
                val track = colors.surfaceContainerHighest
                val ring = colors.secondary
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 12.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                    if (fraction > 0f) {
                        drawArc(ring, -90f, 360f * fraction, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
                Text(formatDuration(left), style = MaterialTheme.typography.displayLarge.copy(fontSize = 84.sp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { vm.addRestSeconds(15) },
                    modifier = Modifier.height(48.dp),
                    border = BorderStroke(1.5.dp, colors.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
                ) { Text(tr("+15 s", "+15 sek"), style = MaterialTheme.typography.labelLarge) }
                Button(
                    onClick = { vm.stopRestTimer() },
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.inverseSurface, contentColor = colors.inverseOnSurface),
                ) { Text(tr("Skip rest", "Hopp over hvilen"), style = MaterialTheme.typography.labelLarge) }
            }
        }

        // Neste sett.
        if (nextEx != null) {
            val weights = remember(nextEx, vm.profile) { weightOptions(nextEx, vm.profile) }
            val target = remember(next) { setTarget(nextEx, next.pe, next.setIndex, vm.logs, weights) }
            val sameEarlier = state.done.filter { it.slot == next.exerciseNo - 1 && it.setIndex < next.setIndex }.maxByOrNull { it.setIndex }
            val w = sameEarlier?.weightKg ?: target.weightKg
            val unit = if (nextEx.timed) tr(" s", " sek") else ""
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainer).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ExerciseTile(nextEx, size = 64.dp, animate = true)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionLabel(tr("Next", "Neste"))
                    Text(
                        "${nextEx.name} · ${tr("set", "sett")} ${next.setIndex + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        tr("Target ", "Mål ") + "${target.reps}$unit" + (if (nextEx.isWeighted() && w != null) " × ${formatWeight(w)} kg" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
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

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val running = endsAt != null
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!running) RoundButton("−", tr("5 seconds less", "5 sekunder mindre"), onClick = { onSecondsChange((seconds - 5).coerceAtLeast(5)) })
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatDuration(if (running) left else seconds), style = MaterialTheme.typography.displayLarge)
                SectionLabel(if (running) tr("Hold", "Hold") else tr("Seconds", "Sekunder"))
            }
            if (!running) RoundButton("+", tr("5 seconds more", "5 sekunder mer"), onClick = { onSecondsChange(seconds + 5) })
        }
        if (!running) {
            BigButton(tr("Start", "Start"), onClick = {
                left = seconds
                endsAt = SystemClock.elapsedRealtime() + seconds * 1000L
            }, icon = RiggIcons.Play)
            TextButton(onClick = { onFinished(seconds) }) { Text(tr("Log without timer", "Logg uten tidtaker")) }
        } else {
            Button(
                onClick = {
                    val held = (seconds - left).coerceAtLeast(0)
                    endsAt = null
                    onFinished(held)
                },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                ),
            ) { Text(tr("Stop and log the time held", "Stopp og logg tiden"), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

// ---------------------------------------------------------------- OPPSUMMERING

@Composable
private fun SummaryView(vm: AppViewModel, day: WorkoutDay, state: GuidedWorkout) {
    val colors = MaterialTheme.colorScheme
    val setsDone = state.done.size
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CheckDot(34.dp)
            Column {
                Text(tr("Workout done!", "Økta er ferdig!"), style = MaterialTheme.typography.headlineMedium)
                Text(
                    tr("$setsDone sets", "$setsDone sett") +
                        (vm.sessionTimer?.takeIf { it.dayTitle == day.title }?.let { " · " + formatDuration(it.elapsedSec()) } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        day.exercises.forEachIndexed { slot, pe ->
            val ex = ExerciseLibrary.get(pe.exerciseId)
            val sets = state.done.filter { it.slot == slot }.sortedBy { it.setIndex }
            val unit = if (ex?.timed == true) tr(" s", " sek") else ""
            val text = if (sets.isEmpty()) tr("skipped", "hoppet over") else sets.joinToString("  ·  ") { s ->
                "${s.reps}$unit" + (s.weightKg?.let { " × ${formatWeight(it)}" } ?: "")
            } + if (sets.any { it.weightKg != null }) " kg" else ""
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainer).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (ex != null) ExerciseTile(ex, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(ex?.name ?: pe.exerciseId, style = MaterialTheme.typography.titleSmall)
                    Text(text, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BigButton(tr("Save workout", "Lagre økta"), onClick = { vm.finishGuided(day) }, icon = RiggIcons.Check)
        TextButton(
            onClick = { vm.updateGuided(state.copy(step = (state.step - 1).coerceAtLeast(0))) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text(tr("Back to the last set", "Tilbake til siste sett")) }
    }
}
