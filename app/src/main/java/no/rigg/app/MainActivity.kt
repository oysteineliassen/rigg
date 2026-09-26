package no.rigg.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }
    private var askedForNotifications = false
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportTo(it) }
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importFrom(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                App(
                    vm,
                    requestNotificationPermission = {
                        // Spør maks én gang per gang appen åpnes, så du ikke masetes på hver timer-trykk hvis du har sagt nei.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedForNotifications &&
                            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            askedForNotifications = true
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    exportData = { exportLauncher.launch("rigg-backup.json") },
                    importData = { importLauncher.launch(arrayOf("application/json")) },
                )
            }
        }
    }
}

private enum class Tab(val label: String) { SETUP("Oppsett"), PLAN("Program"), PROGRESS("Fremgang"), SETTINGS("Innstillinger") }

private fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

/**
 * Åpner et YouTube-søk for øvelsen i nettleser/YouTube-appen (den som er registrert for slike lenker).
 * Bevisst ikke en pekt til én bestemt video: med ~90 øvelser i biblioteket kan vi ikke kvalitetssikre
 * og vedlikeholde en spesifikk lenke for hver, og et søk slutter aldri å virke selv om enkeltvideoer forsvinner.
 */
private fun openExerciseVideo(context: android.content.Context, exercise: Exercise) {
    val query = Uri.encode("${exercise.name} øvelse riktig teknikk")
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query"))
    runCatching { context.startActivity(intent) }
}

/** Enkel ja/nei-bekreftelse, brukt før man avslutter eller avbryter en økt. */
@Composable
private fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Nei") } },
    )
}

@Composable
private fun App(
    vm: AppViewModel,
    requestNotificationPermission: () -> Unit,
    exportData: () -> Unit,
    importData: () -> Unit,
) {
    var tab by remember { mutableStateOf(if (vm.plan != null) Tab.PLAN else Tab.SETUP) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.error) {
        vm.error?.let { snackbar.showSnackbar(it, duration = SnackbarDuration.Long); vm.dismissError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.values().forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.SETUP -> Icons.Filled.Tune
                                    Tab.PLAN -> Icons.Filled.FitnessCenter
                                    Tab.PROGRESS -> Icons.AutoMirrored.Filled.ShowChart
                                    Tab.SETTINGS -> Icons.Filled.Settings
                                },
                                contentDescription = t.label,
                            )
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.SETUP -> SetupScreen(vm, goToPlan = { tab = Tab.PLAN }, goToSettings = { tab = Tab.SETTINGS })
                Tab.PLAN -> PlanScreen(vm, goToSetup = { tab = Tab.SETUP }, requestNotificationPermission = requestNotificationPermission)
                Tab.PROGRESS -> ProgressScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm, requestNotificationPermission, exportData, importData)
            }
            vm.restTimer?.let { timer ->
                RestTimerBar(
                    timer = timer,
                    onAddSeconds = { vm.addRestSeconds(it) },
                    onStop = { vm.stopRestTimer() },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                )
            }
        }
    }
}

/**
 * Tvinger ny tegning jevnlig mens [active] er true. Selve tiden regnes fra klokka der den vises,
 * så visningen er alltid riktig selv om appen har vært i bakgrunnen.
 */
@Composable
private fun rememberTick(active: Boolean, periodMs: Long = 250L): Long {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(active) {
        while (active) {
            tick++
            delay(periodMs)
        }
    }
    return tick
}

/** Egen liten composable så bare klokketeksten tegnes på nytt hvert sekund, ikke hele dagskortet. */
@Composable
private fun SessionClock(session: ActiveSession) {
    @Suppress("UNUSED_VARIABLE")
    val tick = rememberTick(active = true, periodMs = 500L)
    Text(formatDuration(session.elapsedSec()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun RestTimerBar(timer: RestTimer, onAddSeconds: (Int) -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE")
    val tick = rememberTick(active = true) // leses her så baren tegnes på nytt hvert kvarte sekund
    val left = timer.secondsLeft() // regnes i samme scope som tick leses, ikke inne i Card-lambdaen
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Hviler: $left sek", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onAddSeconds(15) }) { Text("+15s") }
                TextButton(onClick = onStop) { Text("Stopp") }
            }
        }
    }
}

// ---------------------------------------------------------------- OPPSETT

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupScreen(vm: AppViewModel, goToPlan: () -> Unit, goToSettings: () -> Unit) {
    val p = vm.profile
    val clipboard = LocalClipboardManager.current
    var showImport by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Hva har du hjemme?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Kroppsvekt er alltid med. Velg det du faktisk har tilgjengelig.", style = MaterialTheme.typography.bodyMedium)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Equipment.selectable.forEach { e ->
                FilterChip(
                    selected = e in p.equipment,
                    onClick = {
                        val next = if (e in p.equipment) p.equipment - e else p.equipment + e
                        vm.updateProfile(p.copy(equipment = next))
                    },
                    label = { Text(e.label) },
                )
            }
        }

        OutlinedTextField(
            value = p.equipmentDetails,
            onValueChange = { vm.updateProfile(p.copy(equipmentDetails = it)) },
            label = { Text("Detaljer (valgfritt)") },
            placeholder = { Text("F.eks. 2 manualer à 12 kg, gul og rød strikk") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = p.customEquipment,
            onValueChange = { vm.updateProfile(p.copy(customEquipment = it)) },
            label = { Text("Annet utstyr (valgfritt)") },
            placeholder = { Text("Har du noe som ikke står over? F.eks. sandsekk, boksesekk, dørbøyle-slynger") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (p.customEquipment.isNotBlank()) {
            Text(
                "Brukes i AI-genererte program. Det regelbaserte (offline) programmet kjenner foreløpig bare " +
                    "øvelsesbiblioteket sitt og kan ikke automatisk lage øvelser for utstyr det ikke vet om.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()

        Text("Mål", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Goal.values().forEach { g ->
                FilterChip(selected = p.goal == g, onClick = { vm.updateProfile(p.copy(goal = g)) }, label = { Text(g.label) })
            }
        }

        Text("Nivå", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Level.values().forEach { l ->
                FilterChip(selected = p.level == l, onClick = { vm.updateProfile(p.copy(level = l)) }, label = { Text(l.label) })
            }
        }

        Text("Dager i uka: ${p.daysPerWeek}", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = p.daysPerWeek.toFloat(),
            onValueChange = { vm.updateProfile(p.copy(daysPerWeek = it.toInt())) },
            valueRange = 1f..6f, steps = 4,
        )

        Text("Tid per økt: ${p.minutesPerSession} min", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = p.minutesPerSession.toFloat(),
            onValueChange = { vm.updateProfile(p.copy(minutesPerSession = (it / 5).toInt() * 5)) },
            valueRange = 20f..90f, steps = 13,
        )

        OutlinedTextField(
            value = p.notes,
            onValueChange = { vm.updateProfile(p.copy(notes = it)) },
            label = { Text("Skader og ønsker (valgfritt)") },
            placeholder = { Text("F.eks. vondt i venstre kne, vil ha sterkere rygg") },
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        Button(
            onClick = { vm.generateWithRules(); goToPlan() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Lag program (regelbasert, offline)") }

        Button(
            onClick = {
                if (vm.ai.apiKey.isBlank()) goToSettings() else { vm.generateWithAi() }
            },
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.filledTonalButtonColors(),
        ) {
            if (vm.busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("AI lager programmet ...")
            } else {
                Text(if (vm.ai.apiKey.isBlank()) "Lag program med AI (legg inn API-nøkkel)" else "Lag program med AI")
            }
        }
        // Naviger til programmet når et nytt program er lagt inn (f.eks. etter AI-kall).
        var firstComposition by remember { mutableStateOf(true) }
        LaunchedEffect(vm.planVersion) {
            if (firstComposition) firstComposition = false else goToPlan()
        }

        Text("Bruker du heller en chat du allerede er logget inn på?", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            onClick = { clipboard.setText(AnnotatedString(vm.promptForClipboard())); copied = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (copied) "Prompt kopiert. Lim den inn i chatten" else "1. Kopier prompt til chat") }
        OutlinedButton(onClick = { showImport = true }, modifier = Modifier.fillMaxWidth()) { Text("2. Lim inn svaret fra chatten") }
    }

    if (showImport) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Lim inn svar") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
                    placeholder = { Text("Lim inn JSON-svaret fra chatten her") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (vm.importPlan(text)) { showImport = false; goToPlan() }
                }) { Text("Importer") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Avbryt") } },
        )
    }
}

// ---------------------------------------------------------------- PROGRAM

@Composable
private fun PlanScreen(vm: AppViewModel, goToSetup: () -> Unit, requestNotificationPermission: () -> Unit) {
    val plan = vm.plan
    if (plan == null) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Ingen program ennå", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Velg utstyr og mål, og lag et program.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = goToSetup) { Text("Gå til oppsett") }
        }
        return
    }

    val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val doneThisWeek = vm.logs.count { runCatching { !LocalDate.parse(it.date).isBefore(weekStart) }.getOrDefault(false) }
    var showHistory by remember { mutableStateOf(false) }

    val weeksElapsed = remember(plan.createdAt) {
        runCatching { java.time.temporal.ChronoUnit.WEEKS.between(LocalDate.parse(plan.createdAt), LocalDate.now()) }.getOrDefault(0L)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(plan.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "${plan.weeks} uker · ${plan.days.size} økter i uka · fullført denne uka: $doneThisWeek/${plan.days.size}",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (weeksElapsed >= plan.weeks) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tid for en lettere uke?", fontWeight = FontWeight.Bold)
                    Text(
                        "Du har kjørt dette programmet i $weeksElapsed uker. Vurder en lettere uke (50-60 % av vanlig vekt/reps), " +
                            "eller lag et nytt program på Oppsett-fanen for fortsatt fremgang.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = goToSetup, modifier = Modifier.align(Alignment.End)) { Text("Gå til oppsett") }
                }
            }
        }

        if (vm.warnings.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Merk", fontWeight = FontWeight.Bold)
                    vm.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }

        plan.days.forEach { day -> DayCard(vm, day, requestNotificationPermission) }

        if (plan.progression.isNotBlank()) {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Progresjon", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(plan.progression, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (vm.logs.isNotEmpty()) {
            Text("Siste økter", style = MaterialTheme.typography.titleMedium)
            vm.logs.takeLast(5).reversed().forEach {
                val suffix = if (it.durationMin > 0) " · ${it.durationMin} min" else ""
                Text("${it.date} · ${it.dayTitle}$suffix", style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showHistory = true }) { Text("Se all historikk") }
                TextButton(onClick = { vm.undoLastLog() }) { Text("Angre siste logging") }
            }
        }

        Text(
            "Dette er generelle treningsforslag, ikke medisinsk rådgivning. Stopp hvis noe gjør vondt, og snakk med lege ved skader eller sykdom.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showHistory) {
        HistoryDialog(logs = vm.logs, onDismiss = { showHistory = false }, onDelete = { vm.deleteLog(it) })
    }
}

@Composable
private fun HistoryDialog(logs: List<SessionLog>, onDismiss: () -> Unit, onDelete: (SessionLog) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Alle økter", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (logs.isEmpty()) {
                    Text("Ingen loggførte økter ennå.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                        logs.asReversed().forEach { log ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(Modifier.padding(10.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        val suffix = if (log.durationMin > 0) " · ${log.durationMin} min" else ""
                                        Text("${log.date} · ${log.dayTitle}$suffix", fontWeight = FontWeight.Medium)
                                        TextButton(onClick = { onDelete(log) }) { Text("Slett") }
                                    }
                                    log.results.forEach { res ->
                                        val name = ExerciseLibrary.get(res.exerciseId)?.name ?: res.exerciseId
                                        val setsText = res.sets.joinToString(", ") { s ->
                                            if (s.weightKg != null) "${formatWeight(s.weightKg)}kg×${s.reps}" else "${s.reps}"
                                        }
                                        Text(
                                            "$name: $setsText",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Lukk") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayCard(vm: AppViewModel, day: WorkoutDay, requestNotificationPermission: () -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var detailFor by remember { mutableStateOf<PlannedExercise?>(null) }
    var showLogDialog by remember { mutableStateOf(false) }
    var swapFor by remember { mutableStateOf<PlannedExercise?>(null) }
    var editFor by remember { mutableStateOf<PlannedExercise?>(null) }
    var confirmCancelSession by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(day.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            day.exercises.forEach { pe ->
                val ex = ExerciseLibrary.get(pe.exerciseId)
                Column(Modifier.clickable { detailFor = pe }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (ex != null) {
                            ExerciseThumbnail(ex, size = 40.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(ex?.name ?: pe.exerciseId, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text("${pe.sets} × ${pe.reps}")
                        IconButton(
                            onClick = { requestNotificationPermission(); vm.startRestTimer(pe.restSec) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Filled.Timer, contentDescription = "Start hviletimer (${pe.restSec} sek)")
                        }
                    }
                    if (expanded) {
                        Text(
                            "Pause ${pe.restSec} sek" + if (pe.tip.isNotBlank()) " · ${pe.tip}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = if (ex != null) 50.dp else 0.dp),
                        )
                    }
                }
            }
            val activeSession = vm.sessionTimer?.takeIf { it.dayTitle == day.title }
            if (activeSession != null) {
                // Egen linje for øktklokka, så knapperaden under ikke blir for bred på smale skjermer.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Økt pågår", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    SessionClock(activeSession)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { confirmCancelSession = true }) { Text("Avbryt økt") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Kort tekst: tre knapper skal få plass på én linje også på smale telefoner.
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Skjul" else "Detaljer") }
                Spacer(Modifier.weight(1f))
                if (activeSession == null) {
                    OutlinedButton(onClick = { requestNotificationPermission(); vm.startSession(day.title) }) { Text("Start økt") }
                }
                FilledTonalButton(onClick = { showLogDialog = true }) { Text("Logg økt") }
            }
        }
    }

    val pe = detailFor
    if (pe != null) {
        val ex = ExerciseLibrary.get(pe.exerciseId)
        val suggestion = ex?.let { ProgressionAdvisor.suggestion(it, pe, vm.logs) }
        Dialog(onDismissRequest = { detailFor = null }) {
            Card {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (ex != null) {
                        ExerciseIllustration(ex, modifier = Modifier.size(160.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(ex.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("${pe.sets} sett × ${pe.reps} · pause ${pe.restSec} sek", style = MaterialTheme.typography.bodyMedium)
                        if (pe.tip.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(pe.tip, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        }
                        if (suggestion != null) {
                            Spacer(Modifier.height(10.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                Text(
                                    suggestion,
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { openExerciseVideo(context, ex) }) { Text("Se video") }
                            OutlinedButton(onClick = { editFor = pe; detailFor = null }) { Text("Rediger") }
                            OutlinedButton(onClick = { swapFor = pe; detailFor = null }) { Text("Bytt øvelse") }
                        }
                    } else {
                        Text(pe.exerciseId)
                    }
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { detailFor = null }) { Text("Lukk") }
                }
            }
        }
    }

    if (confirmCancelSession) {
        ConfirmDialog(
            title = "Avbryte økta?",
            text = "Tiden som er brukt så langt blir ikke lagret.",
            confirmLabel = "Avbryt økta",
            onConfirm = { vm.stopSession() },
            onDismiss = { confirmCancelSession = false },
        )
    }

    if (showLogDialog) {
        LogSessionDialog(
            day = day,
            logs = vm.logs,
            onDismiss = { showLogDialog = false },
            onSave = { results -> vm.logSession(day.title, results); showLogDialog = false },
        )
    }

    val swapPe = swapFor
    if (swapPe != null) {
        val current = ExerciseLibrary.get(swapPe.exerciseId)
        if (current != null) {
            SwapExerciseDialog(
                current = current,
                alternatives = ProgramGenerator.alternativesFor(current, day, vm.profile),
                onDismiss = { swapFor = null },
                onPick = { newEx -> vm.swapExercise(day.title, swapPe.exerciseId, newEx); swapFor = null },
            )
        } else {
            swapFor = null
        }
    }

    val editPe = editFor
    if (editPe != null) {
        EditExerciseDialog(
            pe = editPe,
            onDismiss = { editFor = null },
            onSave = { sets, reps, restSec -> vm.updateExercise(day.title, editPe.exerciseId, sets, reps, restSec); editFor = null },
        )
    }
}

@Composable
private fun SwapExerciseDialog(current: Exercise, alternatives: List<Exercise>, onDismiss: () -> Unit, onPick: (Exercise) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Bytt ut «${current.name}»", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (alternatives.isEmpty()) {
                    Text(
                        "Fant ingen andre øvelser med samme bevegelsesmønster og utstyret ditt.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                        alternatives.forEach { alt ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(alt) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ExerciseThumbnail(alt, size = 40.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(alt.name, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Avbryt") }
            }
        }
    }
}

@Composable
private fun EditExerciseDialog(pe: PlannedExercise, onDismiss: () -> Unit, onSave: (Int, String, Int) -> Unit) {
    var sets by remember { mutableStateOf(pe.sets.toString()) }
    var reps by remember { mutableStateOf(pe.reps) }
    var restSec by remember { mutableStateOf(pe.restSec.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Rediger øvelse", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = sets, onValueChange = { sets = it }, label = { Text("Sett") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = reps, onValueChange = { reps = it }, label = { Text("Reps (f.eks. 8-12 eller 30-45 sek)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = restSec, onValueChange = { restSec = it }, label = { Text("Pause (sekunder)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("Avbryt") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        onSave(
                            sets.toIntOrNull()?.coerceIn(1, 10) ?: pe.sets,
                            reps.ifBlank { pe.reps },
                            restSec.toIntOrNull()?.coerceIn(0, 600) ?: pe.restSec,
                        )
                    }) { Text("Lagre") }
                }
            }
        }
    }
}

@Composable
private fun LogSessionDialog(
    day: WorkoutDay,
    logs: List<SessionLog>,
    onDismiss: () -> Unit,
    onSave: (List<ExerciseResult>) -> Unit,
) {
    // For hver øvelse og hvert sett: en tekstfelt-tilstand for reps og ett for vekt,
    // forhåndsutfylt med forrige logg (eller toppen av måltallet) så man slipper å taste alt på nytt.
    val state = remember {
        day.exercises.associate { pe ->
            val ex = ExerciseLibrary.get(pe.exerciseId)
            val last = ProgressionAdvisor.lastResult(logs, pe.exerciseId)
            val defaultReps = parseRepRange(pe.reps)?.last ?: 10
            val setsState = List(pe.sets) { i ->
                val lastSet = last?.sets?.getOrNull(i)
                mutableStateOf((lastSet?.reps ?: defaultReps).toString()) to
                    mutableStateOf(lastSet?.weightKg?.let { formatWeight(it) } ?: "")
            }
            pe.exerciseId to ((ex?.isWeighted() == true) to setsState)
        }
    }
    var showConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Logg ${day.title}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                day.exercises.forEach { pe ->
                    val ex = ExerciseLibrary.get(pe.exerciseId)
                    val (weighted, setsState) = state.getValue(pe.exerciseId)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(ex?.name ?: pe.exerciseId, fontWeight = FontWeight.Medium)
                        setsState.forEachIndexed { i, (repsState, weightState) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Sett ${i + 1}", modifier = Modifier.width(56.dp), style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(
                                    value = repsState.value,
                                    onValueChange = { repsState.value = it },
                                    label = { Text(if (ex?.timed == true) "sek" else "reps") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(90.dp),
                                )
                                if (weighted) {
                                    OutlinedTextField(
                                        value = weightState.value,
                                        onValueChange = { weightState.value = it },
                                        label = { Text("kg") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.width(90.dp),
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // "Lukk", ikke "Avbryt": økta og klokka fortsetter, bare skjemaet lukkes.
                    TextButton(onClick = onDismiss) { Text("Lukk") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { showConfirm = true }) { Text("Lagre") }
                }
            }
        }
    }

    if (showConfirm) {
        ConfirmDialog(
            title = "Logge økta?",
            text = "Dette lagrer resultatene for «${day.title}» og avslutter øktklokka.",
            confirmLabel = "Loggfør",
            onConfirm = {
                val results = day.exercises.map { pe ->
                    val (_, setsState) = state.getValue(pe.exerciseId)
                    val sets = setsState.map { (repsState, weightState) ->
                        SetResult(
                            reps = repsState.value.toIntOrNull() ?: 0,
                            weightKg = weightState.value.replace(',', '.').toDoubleOrNull(),
                        )
                    }
                    ExerciseResult(pe.exerciseId, sets)
                }
                onSave(results)
            },
            onDismiss = { showConfirm = false },
        )
    }
}

// ---------------------------------------------------------------- INNSTILLINGER

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    vm: AppViewModel,
    requestNotificationPermission: () -> Unit,
    exportData: () -> Unit,
    importData: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ReminderSection(vm, requestNotificationPermission)

        HorizontalDivider()

        DataSection(exportData, importData)

        HorizontalDivider()

        AiSection(vm)
    }
}

@Composable
private fun DataSection(exportData: () -> Unit, importData: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Data", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Alt lagres kun lokalt på telefonen. Eksporter til fil før du bytter telefon, eller som en enkel backup.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = exportData, modifier = Modifier.weight(1f)) { Text("Eksporter") }
            OutlinedButton(onClick = importData, modifier = Modifier.weight(1f)) { Text("Importer") }
        }
        Text(
            "AI-nøkkelen din tas ikke med i eksporten.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderSection(vm: AppViewModel, requestNotificationPermission: () -> Unit) {
    val r = vm.reminders

    Text("Treningspåminnelser", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Skru på påminnelser", style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = r.enabled,
            onCheckedChange = { checked ->
                if (checked) requestNotificationPermission()
                vm.updateReminders(r.copy(enabled = checked))
            },
        )
    }

    if (r.enabled) {
        Text("Hvilke dager?", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..7).forEach { iso ->
                FilterChip(
                    selected = iso in r.days,
                    onClick = {
                        val days = if (iso in r.days) r.days - iso else r.days + iso
                        vm.updateReminders(r.copy(days = days))
                    },
                    label = { Text(dayLabel(iso)) },
                )
            }
        }

        Text("Tidspunkt: %02d:%02d".format(r.hour, r.minute), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Time", style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = { vm.updateReminders(r.copy(hour = (r.hour + 23) % 24)) }) { Text("−") }
            IconButton(onClick = { vm.updateReminders(r.copy(hour = (r.hour + 1) % 24)) }) { Text("+") }
            Spacer(Modifier.width(8.dp))
            Text("Min", style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = { vm.updateReminders(r.copy(minute = (r.minute + 45) % 60)) }) { Text("−") }
            IconButton(onClick = { vm.updateReminders(r.copy(minute = (r.minute + 15) % 60)) }) { Text("+") }
        }

        Text(
            "Minuttene hopper i kvarter. Varselet kan komme noen minutter etter valgt tid hvis telefonen sparer strøm.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiSection(vm: AppViewModel) {
    val s = vm.ai
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("AI-modell", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Innloggingen din i chat-appen kan ikke gjenbrukes i en annen app. For å kalle en modell direkte trenger du en API-nøkkel " +
                "(opprettes gratis hos leverandøren, betalt per bruk. Et program koster typisk noen øre). " +
                "Vil du slippe nøkkel, bruk «Kopier prompt» og «Lim inn svar» på oppsettsiden med chatten du allerede er logget inn på.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Text("Leverandør", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Provider.values().forEach { pr ->
                FilterChip(
                    selected = s.provider == pr,
                    onClick = { vm.updateAi(s.copy(provider = pr)) },
                    label = { Text(pr.label) },
                )
            }
        }

        OutlinedTextField(
            value = s.apiKey,
            onValueChange = { vm.updateAi(s.copy(apiKey = it.trim())) },
            label = { Text("API-nøkkel") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = s.model,
            onValueChange = { vm.updateAi(s.copy(model = it.trim())) },
            label = { Text("Modell (tom = ${s.provider.defaultModel})") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Nøkkelen lagres kun lokalt på telefonen. Kun profilen din (utstyr, mål, nivå, notater) sendes til leverandøren.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
