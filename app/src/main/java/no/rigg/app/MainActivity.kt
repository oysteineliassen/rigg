package no.rigg.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        // Bare første gang: etter en rotasjon er koden fra Strava allerede brukt.
        if (savedInstanceState == null) handleStravaRedirect(intent)
        setContent {
            RiggTheme {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleStravaRedirect(intent)
    }

    private fun handleStravaRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "rigg" || uri.host != "localhost" || uri.path != "/strava") return
        vm.finishStravaLogin(uri.getQueryParameter("code"), uri.getQueryParameter("error"), uri.getQueryParameter("scope"))
    }
}

private enum class Tab(val en: String, val no: String) {
    SETUP("Setup", "Oppsett"), PLAN("Plan", "Program"), EXERCISES("Exercises", "Øvelser"), PROGRESS("Progress", "Fremgang"), SETTINGS("More", "Mer");

    val label: String get() = tr(en, no)
}

internal fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

/**
 * Åpner et YouTube-søk for øvelsen i nettleser/YouTube-appen (den som er registrert for slike lenker).
 * Bevisst ikke en pekt til én bestemt video: med ~90 øvelser i biblioteket kan vi ikke kvalitetssikre
 * og vedlikeholde en spesifikk lenke for hver, og et søk slutter aldri å virke selv om enkeltvideoer forsvinner.
 */
fun openExerciseVideo(context: android.content.Context, exercise: Exercise) {
    // Alltid engelsk søk, uansett språk i appen: det gir langt flere og bedre treff.
    val query = Uri.encode("${exercise.nameEn} exercise proper form")
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query"))
    runCatching { context.startActivity(intent) }
}

/** Enkel ja/nei-bekreftelse, brukt før man avslutter eller avbryter en økt. */
@Composable
internal fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("No", "Nei")) } },
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

    // Øktmodus tar hele skjermen (uten fanene) mens den er åpen.
    val guided = vm.guided
    val guidedDay = guided?.let { g -> vm.plan?.days?.find { it.title == g.dayTitle } }
    if (guided != null && guidedDay != null && vm.guidedVisible) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                // Hvilen vises som egen skjerm inne i øktmodus, så her trengs ikke baren nederst.
                WorkoutSessionScreen(vm, guidedDay, guided)
            }
        }
        return
    }

    // Historikken tar også hele skjermen (uten fanene) mens den er åpen.
    if (vm.historyOpen) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                // key: ny skjerm hver gang den åpnes, så den starter på riktig økt.
                key(vm.historyStart) {
                    HistoryScreen(vm, startWith = vm.historyStart, onDismiss = { vm.closeHistory() })
                }
                vm.shareLog?.let { log -> WorkoutShareDialog(log, vm, onDismiss = { vm.shareLog = null }) }
            }
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Tab.values().forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.SETUP -> RiggIcons.Sliders
                                    Tab.PLAN -> RiggIcons.Dumbbell
                                    Tab.EXERCISES -> RiggIcons.Lines
                                    Tab.PROGRESS -> RiggIcons.Trend
                                    Tab.SETTINGS -> RiggIcons.More
                                },
                                contentDescription = t.label,
                            )
                        },
                        label = { Text(t.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.SETUP -> SetupScreen(vm, goToPlan = { tab = Tab.PLAN }, goToSettings = { tab = Tab.SETTINGS })
                Tab.PLAN -> PlanScreen(vm, goToSetup = { tab = Tab.SETUP }, requestNotificationPermission = requestNotificationPermission)
                Tab.EXERCISES -> ExerciseLibraryScreen(vm)
                Tab.PROGRESS -> ProgressScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm, requestNotificationPermission, exportData, importData)
            }
            vm.shareLog?.let { log -> WorkoutShareDialog(log, vm, onDismiss = { vm.shareLog = null }) }
            vm.restTimer?.let { timer ->
                RestTimerBar(
                    timer = timer,
                    onAddSeconds = { vm.addRestSeconds(it) },
                    onStop = { vm.stopRestTimer() },
                    // Trykk på baren: tilbake til økta (bare når det finnes en påbegynt økt i øktmodus).
                    onOpen = vm.guided?.takeIf { g -> vm.plan?.days?.any { it.title == g.dayTitle } == true }?.let { { vm.showGuided() } },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                )
            }
            // Oppvarming eller tidsøvelse (planke ...) som teller mens øktmodus er skjult. Hvilen vises heller hvis begge går.
            if (vm.restTimer == null) vm.workTimer?.takeIf { !it.finished }?.let { work ->
                WorkTimerBar(
                    timer = work,
                    onOpen = { vm.showGuided() },
                    onStop = if (work.kind == WorkKind.SET) ({ vm.stopTimedSetEarly() }) else null,
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
internal fun rememberTick(active: Boolean, periodMs: Long = 250L): Long {
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
internal fun SessionClock(
    session: ActiveSession,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
) {
    @Suppress("UNUSED_VARIABLE")
    val tick = rememberTick(active = true, periodMs = 500L)
    Text(formatDuration(session.elapsedSec()), style = style, fontWeight = FontWeight.Bold, color = color)
}

@Composable
internal fun RestTimerBar(
    timer: RestTimer,
    onAddSeconds: (Int) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
) {
    @Suppress("UNUSED_VARIABLE")
    val tick = rememberTick(active = true) // leses her så baren tegnes på nytt hvert kvarte sekund
    val left = timer.secondsLeft() // regnes i samme scope som tick leses, ikke inne i Card-lambdaen
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    val content: @Composable ColumnScope.() -> Unit = {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (timer.paused) tr("Rest paused: $left s", "Hvile på pause: $left sek") else tr("Resting: $left s", "Hviler: $left sek"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (onOpen != null) {
                    Text(
                        tr("Tap to return to the workout", "Trykk for å gå tilbake til økta"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onAddSeconds(15) }) { Text("+15s") }
                TextButton(onClick = onStop) { Text(tr("Stop", "Stopp")) }
            }
        }
    }
    if (onOpen != null) Card(onClick = onOpen, modifier = modifier, colors = colors, content = content)
    else Card(modifier, colors = colors, content = content)
}

/** Nedtellingen for oppvarming eller en tidsøvelse, nederst på skjermen mens øktmodus er skjult. Trykk for å gå tilbake. */
@Composable
internal fun WorkTimerBar(timer: WorkTimer, onOpen: () -> Unit, onStop: (() -> Unit)?, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE")
    val tick = rememberTick(active = true)
    val left = timer.secondsLeft()
    Card(
        onClick = onOpen,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (timer.kind == WorkKind.WARMUP) tr("Warm-up: ", "Oppvarming: ") else "") + timer.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    if (timer.paused) tr("Paused · tap to return", "På pause · trykk for å gå tilbake")
                    else tr("Tap to return to the workout", "Trykk for å gå tilbake til økta"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
            Text(formatDuration(left), style = MaterialTheme.typography.headlineSmall)
            if (onStop != null) TextButton(onClick = onStop) { Text(tr("Stop", "Stopp")) }
        }
    }
}

// ---------------------------------------------------------------- OPPSETT

@Composable
private fun SetupScreen(vm: AppViewModel, goToPlan: () -> Unit, goToSettings: () -> Unit) {
    val p = vm.profile
    val clipboard = LocalClipboardManager.current
    var showImport by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var moreOptions by remember { mutableStateOf(false) }
    var editWeights by remember { mutableStateOf<Equipment?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("Your equipment", "Utstyret ditt"), style = MaterialTheme.typography.headlineMedium)
            Text(
                tr("Bodyweight is always included. Tap what you have.", "Kroppsvekt er alltid med. Trykk på det du har."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Utstyret som fliser med ikon, fire i bredden.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Equipment.selectable.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { e ->
                        EquipmentTile(
                            e, selected = e in p.equipment, modifier = Modifier.weight(1f),
                            onClick = { vm.updateProfile(p.copy(equipment = if (e in p.equipment) p.equipment - e else p.equipment + e)) },
                        )
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        if (Equipment.DUMBBELLS in p.equipment) {
            WeightsRow(Equipment.DUMBBELLS, tr("Dumbbell weights you have", "Manualvekter du har"), p.dumbbellWeights) { editWeights = Equipment.DUMBBELLS }
        }
        if (Equipment.KETTLEBELL in p.equipment) {
            WeightsRow(Equipment.KETTLEBELL, tr("Kettlebells you have", "Kettlebeller du har"), p.kettlebellWeights) { editWeights = Equipment.KETTLEBELL }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(tr("Goal", "Mål"))
            Segmented(Goal.values().toList(), p.goal, { it.shortLabel }) { vm.updateProfile(p.copy(goal = it)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(tr("Level", "Nivå"))
            Segmented(Level.values().toList(), p.level, { it.label }) { vm.updateProfile(p.copy(level = it)) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SectionLabel(tr("Days a week", "Dager i uka"))
                SectionLabel(tr("Time per workout", "Tid per økt"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..6).forEach { d ->
                        Pill(d.toString(), selected = p.daysPerWeek == d, modifier = Modifier.weight(1f)) { vm.updateProfile(p.copy(daysPerWeek = d)) }
                    }
                }
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { vm.updateProfile(p.copy(minutesPerSession = (p.minutesPerSession - 5).coerceAtLeast(20))) },
                        modifier = Modifier.size(40.dp), contentPadding = PaddingValues(0.dp),
                    ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                    Text("${p.minutesPerSession}", style = MaterialTheme.typography.titleLarge)
                    Text(" min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(
                        onClick = { vm.updateProfile(p.copy(minutesPerSession = (p.minutesPerSession + 5).coerceAtMost(90))) },
                        modifier = Modifier.size(40.dp), contentPadding = PaddingValues(0.dp),
                    ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton(tr("Create plan", "Lag program"), onClick = { vm.generateWithRules(); goToPlan() })
            OutlinedButton(
                onClick = { if (vm.ai.apiKey.isBlank()) goToSettings() else vm.generateWithAi() },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (vm.busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("AI is creating the plan ...", "AI lager programmet ..."))
                } else {
                    Icon(RiggIcons.Sparkle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (vm.ai.apiKey.isBlank()) tr("Create with AI (add API key)", "Lag med AI (legg inn API-nøkkel)") else tr("Create with AI instead", "Lag med AI i stedet"))
                }
            }
        }
        // Naviger til programmet når et nytt program er lagt inn (f.eks. etter AI-kall).
        var firstComposition by remember { mutableStateOf(true) }
        LaunchedEffect(vm.planVersion) {
            if (firstComposition) firstComposition = false else goToPlan()
        }

        // Det som sjelden trengs, samlet bak én knapp så skjermen holder seg ryddig.
        TextButton(onClick = { moreOptions = !moreOptions }) {
            Text(if (moreOptions) tr("Fewer options", "Færre valg") else tr("More options", "Flere valg"))
            Spacer(Modifier.width(4.dp))
            Icon(RiggIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        if (moreOptions) {
            OutlinedTextField(
                value = p.equipmentDetails,
                onValueChange = { vm.updateProfile(p.copy(equipmentDetails = it)) },
                label = { Text(tr("Equipment details (optional)", "Detaljer om utstyret (valgfritt)")) },
                placeholder = { Text(tr("E.g. yellow and red bands", "F.eks. gul og rød strikk")) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = p.customEquipment,
                onValueChange = { vm.updateProfile(p.copy(customEquipment = it)) },
                label = { Text(tr("Other equipment (optional)", "Annet utstyr (valgfritt)")) },
                placeholder = { Text(tr("E.g. sandbag, punching bag", "F.eks. sandsekk, boksesekk")) },
                supportingText = if (p.customEquipment.isNotBlank()) {
                    { Text(tr("Only used by AI-generated plans.", "Brukes bare av AI-genererte program.")) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = p.notes,
                onValueChange = { vm.updateProfile(p.copy(notes = it)) },
                label = { Text(tr("Injuries and wishes (optional)", "Skader og ønsker (valgfritt)")) },
                placeholder = { Text(tr("E.g. sore left knee, want a stronger back", "F.eks. vondt i venstre kne, vil ha sterkere rygg")) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                tr("Prefer a chat you're already signed in to?", "Bruker du heller en chat du allerede er logget inn på?"),
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(vm.promptForClipboard())); copied = true },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            ) { Text(if (copied) tr("Prompt copied. Paste it into the chat", "Prompt kopiert. Lim den inn i chatten") else tr("1. Copy prompt for chat", "1. Kopier prompt til chat")) }
            OutlinedButton(onClick = { showImport = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text(tr("2. Paste the reply from the chat", "2. Lim inn svaret fra chatten"))
            }
        }
    }

    editWeights?.let { eq ->
        val dumbbells = eq == Equipment.DUMBBELLS
        var text by remember(eq) { mutableStateOf(if (dumbbells) p.dumbbellWeights else p.kettlebellWeights) }
        AlertDialog(
            onDismissRequest = { editWeights = null },
            title = { Text(if (dumbbells) tr("Dumbbell weights", "Manualvekter") else tr("Kettlebells", "Kettlebeller")) },
            text = {
                WeightsField(
                    label = tr("Weights you have (kg)", "Vektene du har (kg)"),
                    value = text,
                    onChange = { text = it },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateProfile(if (dumbbells) vm.profile.copy(dumbbellWeights = text) else vm.profile.copy(kettlebellWeights = text))
                    editWeights = null
                }) { Text(tr("Save", "Lagre")) }
            },
            dismissButton = { TextButton(onClick = { editWeights = null }) { Text(tr("Cancel", "Avbryt")) } },
        )
    }

    if (showImport) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text(tr("Paste reply", "Lim inn svar")) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
                    placeholder = { Text(tr("Paste the JSON reply from the chat here", "Lim inn JSON-svaret fra chatten her")) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (vm.importPlan(text)) { showImport = false; goToPlan() }
                }) { Text(tr("Import", "Importer")) }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text(tr("Cancel", "Avbryt")) } },
        )
    }
}

/** Kort navn på målene, så de får plass i en knapperad. */
private val Goal.shortLabel: String
    get() = when (this) {
        Goal.STRENGTH -> tr("Strength", "Styrke")
        Goal.HYPERTROPHY -> tr("Muscle", "Muskelvekst")
        Goal.FITNESS -> tr("Fitness", "Form")
    }

@Composable
private fun EquipmentTile(e: Equipment, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .height(78.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.primaryContainer else colors.surface)
            .border(1.5.dp, if (selected) colors.secondary else colors.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(RiggIcons.forEquipment(e), contentDescription = null, tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant, modifier = Modifier.size(26.dp))
        Text(
            e.shortLabel,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** Korte utstyrsnavn til flisene (de lange står i øvelseslista). */
private val Equipment.shortLabel: String
    get() = when (this) {
        Equipment.BODYWEIGHT -> tr("Bodyweight", "Kroppsvekt")
        Equipment.DUMBBELLS -> tr("Dumbbells", "Manualer")
        Equipment.KETTLEBELL -> "Kettlebell"
        Equipment.RESISTANCE_BANDS -> tr("Bands", "Strikk")
        Equipment.PULL_UP_BAR -> tr("Pull-up bar", "Tverrligger")
        Equipment.BENCH -> tr("Bench", "Benk")
        Equipment.PLYO_BOX -> tr("Plyo box", "Hoppekasse")
        Equipment.BARBELL -> tr("Barbell", "Stang")
        Equipment.CHAIR -> tr("Chair/sofa", "Stol/sofa")
        Equipment.TABLE -> tr("Table", "Bord")
        Equipment.TRX -> tr("Straps", "Slynger")
        Equipment.JUMP_ROPE -> tr("Jump rope", "Hoppetau")
    }

@Composable
private fun WeightsRow(e: Equipment, label: String, value: String, onEdit: () -> Unit) {
    val parsed = parseWeights(value)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onEdit)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(RiggIcons.forEquipment(e), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when {
                    parsed.isEmpty() -> tr("Not set: tap to add", "Ikke lagt inn: trykk for å legge til")
                    parsed.size > 4 -> "${formatWeight(parsed.first())}–${formatWeight(parsed.last())} kg · ${parsed.size} " + tr("weights", "vekter")
                    else -> parsed.joinToString(", ") { formatWeight(it) } + " kg"
                },
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Text(tr("Edit", "Endre"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
    }
}

/** Knapperad der ett valg er markert (mål, nivå). */
@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (on) MaterialTheme.colorScheme.inverseSurface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(o) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(o),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (on) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.inverseSurface else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (selected) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
            color = if (selected) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Tekstfelt for vektene man har, med tolkningen vist under så man ser at den ble forstått riktig. */
@Composable
private fun WeightsField(label: String, value: String, onChange: (String) -> Unit) {
    val parsed = parseWeights(value)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(tr("E.g. 2-20/2 or 4 6 8 12.5", "F.eks. 2-20/2 eller 4 6 8 12,5")) },
        supportingText = {
            Text(
                when {
                    value.isBlank() -> tr(
                        "Used to suggest the exact next weight when you progress.",
                        "Brukes til å foreslå nøyaktig neste vekt når du går opp.",
                    )
                    parsed.isEmpty() -> tr("Couldn't read any weights.", "Fant ingen vekter.")
                    else -> tr("Read as: ", "Tolket som: ") + parsed.joinToString(", ") { formatWeight(it) } + " kg"
                },
            )
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
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
            Text("RIGG", style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = 4.sp))
            Spacer(Modifier.height(16.dp))
            Text(tr("No plan yet", "Ingen program ennå"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                tr("Choose your equipment and goal, then create a plan.", "Velg utstyr og mål, og lag et program."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            BigButton(tr("Go to setup", "Gå til oppsett"), onClick = goToSetup)
        }
        return
    }

    val today = LocalDate.now()
    val doneThisWeek = logsThisWeek(vm.logs, today)
    val next = nextDay(plan, vm.logs, today)
    var confirmNextBlock by remember { mutableStateOf(false) }
    var showProgression by remember { mutableStateOf(false) }

    val weeksElapsed = remember(plan.createdAt) {
        runCatching { java.time.temporal.ChronoUnit.WEEKS.between(LocalDate.parse(plan.createdAt), LocalDate.now()) }.getOrDefault(0L)
    }
    val weekNo = (weeksElapsed + 1).coerceAtMost(plan.weeks.toLong())

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("RIGG", style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 4.sp))
            Text(
                tr("Week $weekNo of ${plan.weeks}", "Uke $weekNo av ${plan.weeks}") + if (plan.block > 1) tr(" · block ${plan.block}", " · blokk ${plan.block}") else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(tr("This week", "Denne uka"), style = MaterialTheme.typography.headlineSmall)
                Text(
                    tr("${doneThisWeek.size} of ${plan.days.size} workouts", "${doneThisWeek.size} av ${plan.days.size} økter"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WeekStrip(trainedWeekdays(vm.logs, today), today.dayOfWeek.value)
        }

        if (next != null) {
            NextWorkoutCard(vm, next, onStart = { requestNotificationPermission(); vm.openGuided(next) })
        } else {
            var chooseExtra by remember { mutableStateOf(false) }
            val suggested = remember(plan, vm.logs) { extraDay(plan, vm.logs) }
            // Trent i dag eller i går: minn om hvile (samme muskler trenger gjerne et par døgn).
            val trainedRecently = vm.logs.any { it.date == today.toString() || it.date == today.minusDays(1).toString() }
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel(tr("Week done", "Uka er i boks"), color = MaterialTheme.colorScheme.tertiary)
                    Text(tr("All workouts done this week", "Alle øktene er gjort denne uka"), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        tr(
                            "Rest up, or take an extra workout if you have the time and energy. The plan starts over on Monday.",
                            "Hvil godt, eller ta en ekstra økt hvis du har tid og lyst. Programmet starter på nytt mandag.",
                        ),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (suggested != null) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = { chooseExtra = true }, shape = RoundedCornerShape(14.dp)) {
                            Icon(RiggIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Extra workout", "Ekstra økt"))
                        }
                    }
                }
            }
            if (chooseExtra && suggested != null) {
                ExtraWorkoutDialog(
                    days = plan.days,
                    suggested = suggested,
                    trainedRecently = trainedRecently,
                    onPick = { day -> chooseExtra = false; requestNotificationPermission(); vm.openGuided(day) },
                    onDismiss = { chooseExtra = false },
                )
            }
        }

        nudgeFor(next ?: plan.days.first(), vm.logs, vm.profile)?.let { NudgeCard(it) }

        if (weeksElapsed >= plan.weeks) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Block done: time for the next one", "Blokka er ferdig: tid for neste"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        tr(
                            "You've been on this plan for $weeksElapsed ${if (weeksElapsed == 1L) "week" else "weeks"}. Take a lighter week (50-60% of your usual weight/reps), " +
                                "or create the next block from your logs.",
                            "Du har kjørt dette programmet i $weeksElapsed uker. Ta en lettere uke (50-60 % av vanlig vekt/reps), " +
                                "eller lag neste blokk ut fra loggene dine.",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = goToSetup) { Text(tr("New plan", "Nytt program")) }
                        Button(onClick = { confirmNextBlock = true }) { Text(tr("Next block", "Neste blokk")) }
                    }
                }
            }
        }

        if (vm.warnings.isNotEmpty()) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel(tr("Note", "Merk"), color = MaterialTheme.colorScheme.tertiary)
                    vm.warnings.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }

        if (doneThisWeek.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(tr("Done this week", "Gjort denne uka"))
                doneThisWeek.forEach { log ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .clickable { vm.openHistory(log) }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CheckDot(28.dp)
                        Text(log.dayTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        val day = runCatching { LocalDate.parse(log.date).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) }.getOrDefault("")
                        Text(
                            day + if (log.durationMin > 0) " · ${log.durationMin} min" else "",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(tr("All days", "Alle dagene"))
            plan.days.forEach { day -> DayCard(vm, day, requestNotificationPermission) }
        }

        if (plan.progression.isNotBlank()) {
            Card(
                Modifier.fillMaxWidth().clickable { showProgression = !showProgression },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("How to progress", "Slik blir du sterkere"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Icon(RiggIcons.ChevronDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (showProgression) Text(plan.progression, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (vm.logs.isNotEmpty() && weeksElapsed < plan.weeks) {
            OutlinedButton(onClick = { confirmNextBlock = true }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
                Text(tr("Create the next block from your logs", "Lag neste blokk ut fra loggene"))
            }
        }

        if (vm.logs.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.openHistory() }) { Text(tr("All history", "All historikk")) }
                TextButton(onClick = { vm.undoLastLog() }) { Text(tr("Undo last log", "Angre siste logging")) }
            }
        }

        Text(
            tr(
                "These are general training suggestions, not medical advice. Stop if anything hurts, and see a doctor about injuries or illness.",
                "Dette er generelle treningsforslag, ikke medisinsk rådgivning. Stopp hvis noe gjør vondt, og snakk med lege ved skader eller sykdom.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }


    if (confirmNextBlock) {
        ConfirmDialog(
            title = tr("Create the next block?", "Lage neste blokk?"),
            text = tr(
                "The plan is replaced by a new 4-week block. Exercises where you reached the top of the rep range get a heavier " +
                    "weight or a harder variation; the rest are kept. Your logs are kept.",
                "Programmet erstattes av en ny 4-ukersblokk. Øvelser der du nådde toppen av repetisjonsintervallet får tyngre " +
                    "vekt eller en vanskeligere variant; resten beholdes. Loggene dine beholdes.",
            ),
            confirmLabel = tr("Create", "Lag"),
            onConfirm = { vm.startNextBlock() },
            onDismiss = { confirmNextBlock = false },
        )
    }
}

/** Liten fylt sirkel med hake, for gjennomførte økter og dager. */
@Composable
internal fun CheckDot(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(RiggIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(size * 0.5f))
    }
}

/** Uka som sju sirkler: fylt med hake for dager det er trent, ring rundt i dag. */
@Composable
private fun WeekStrip(trained: Set<Int>, today: Int) {
    val letters = if (Lang.isNorwegian) listOf("M", "T", "O", "T", "F", "L", "S") else listOf("M", "T", "W", "T", "F", "S", "S")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (1..7).forEach { d ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    letters[d - 1],
                    style = MaterialTheme.typography.labelMedium,
                    color = if (d == today) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    d in trained -> CheckDot(38.dp)
                    else -> Box(
                        Modifier.size(38.dp).border(
                            if (d == today) 2.dp else 1.5.dp,
                            if (d == today) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        ),
                    )
                }
            }
        }
    }
}

/** Velg hvilken dag i programmet du vil ta som ekstra økt. Forslaget er dagen det er lengst siden du tok. */
@Composable
private fun ExtraWorkoutDialog(
    days: List<WorkoutDay>,
    suggested: WorkoutDay,
    trainedRecently: Boolean,
    onPick: (WorkoutDay) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Extra workout", "Ekstra økt")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr(
                        "Pick a day from your plan. It's logged like any other workout and counts this week.",
                        "Velg en dag fra programmet. Den logges som en vanlig økt og teller med denne uka.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (trainedRecently) {
                    Text(
                        tr(
                            "You trained today or yesterday. Muscles often need a couple of days, so go a little lighter if you're sore.",
                            "Du trente i dag eller i går. Musklene trenger ofte et par døgn, så ta det litt roligere hvis du er støl.",
                        ),
                        style = MaterialTheme.typography.bodySmall, color = colors.tertiary,
                    )
                }
                days.forEach { day ->
                    val isSuggested = day.title == suggested.title
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (isSuggested) colors.primaryContainer else colors.surfaceContainerHigh)
                            .clickable { onPick(day) }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(day.title, style = MaterialTheme.typography.titleMedium, color = if (isSuggested) colors.onPrimaryContainer else colors.onSurface)
                            Text(
                                (if (isSuggested) tr("Suggested · longest since last time · ", "Forslag · lengst siden sist · ") else "") +
                                    tr("about ${estimateMinutes(day)} min", "ca. ${estimateMinutes(day)} min"),
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                            )
                        }
                        Icon(RiggIcons.Play, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "Avbryt")) } },
    )
}

/** Kortet med neste økt: navn, omfang, figurene og én stor startknapp. */
@Composable
private fun NextWorkoutCard(vm: AppViewModel, day: WorkoutDay, onStart: () -> Unit) {
    val active = vm.sessionTimer?.dayTitle == day.title
    val minutes = remember(day) { estimateMinutes(day) }
    val warmupMin = remember(day, vm.profile.equipment) { (warmupFor(day, vm.profile.equipment).sumOf { it.seconds } + 30) / 60 }
    val exercises = day.exercises.mapNotNull { ExerciseLibrary.get(it.exerciseId) }
    var infoFor by remember { mutableStateOf<Exercise?>(null) }
    infoFor?.let { ExerciseInfoDialog(it, onDismiss = { infoFor = null }) }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel(if (active) tr("In progress", "Pågår") else tr("Next workout", "Neste økt"), color = MaterialTheme.colorScheme.tertiary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(day.title, style = MaterialTheme.typography.headlineLarge)
                Text(
                    tr(
                        "${day.exercises.size} exercises · about $minutes min · $warmupMin min warm-up",
                        "${day.exercises.size} øvelser · ca. $minutes min · $warmupMin min oppvarming",
                    ),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val shown = if (exercises.size > 6) exercises.take(5) else exercises
                shown.forEach { ex -> Box(Modifier.clip(RoundedCornerShape(14.dp)).clickable { infoFor = ex }) { ExerciseTile(ex, size = 48.dp) } }
                if (exercises.size > shown.size) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+${exercises.size - shown.size}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            BigButton(if (active) tr("Continue", "Fortsett økta") else tr("Start workout", "Start økt"), onClick = onStart, icon = RiggIcons.Play)
        }
    }
}

@Composable
private fun NudgeCard(nudge: Nudge) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(RiggIcons.ArrowUp, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(nudge.title, style = MaterialTheme.typography.titleMedium)
            Text(nudge.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    val doneThisWeek = logsThisWeek(vm.logs, LocalDate.now()).any { it.dayTitle == day.title }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(day.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if (doneThisWeek) CheckDot(24.dp)
            }
            if (expanded) {
                val warmup = warmupFor(day, vm.profile.equipment)
                Text(
                    tr("Warm-up (~${(warmup.sumOf { it.seconds } + 30) / 60} min): ", "Oppvarming (~${(warmup.sumOf { it.seconds } + 30) / 60} min): ") +
                        warmup.joinToString(" · ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            day.exercises.forEachIndexed { index, pe ->
                val ex = ExerciseLibrary.get(pe.exerciseId)
                // Supersett: A1, A2 ... (nummeret innen gruppa).
                val supersetLabel = if (pe.group.isNotEmpty()) {
                    pe.group + (day.exercises.take(index + 1).count { it.group == pe.group })
                } else null
                Column(Modifier.clickable { detailFor = pe }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (ex != null) ExerciseTile(ex, size = 44.dp)
                        Column(Modifier.weight(1f)) {
                            if (supersetLabel != null) {
                                Text(tr("Superset $supersetLabel", "Supersett $supersetLabel"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                            Text(ex?.name ?: pe.exerciseId, style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            "${pe.sets} × ${pe.reps}",
                            Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    if (expanded) {
                        Text(
                            tr("Rest ${pe.restSec} s", "Pause ${pe.restSec} sek") + if (pe.tip.isNotBlank()) " · ${pe.tip}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = if (ex != null) 56.dp else 0.dp, top = 4.dp),
                        )
                    }
                }
            }
            val activeSession = vm.sessionTimer?.takeIf { it.dayTitle == day.title }
            if (activeSession != null) {
                // Egen linje for øktklokka, så knapperaden under ikke blir for bred på smale skjermer.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Workout in progress", "Økt pågår"), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    SessionClock(activeSession)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { confirmCancelSession = true }) { Text(tr("Cancel workout", "Avbryt økt")) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Kort tekst: tre knapper skal få plass på én linje også på smale telefoner.
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) tr("Hide", "Skjul") else tr("Details", "Detaljer")) }
                Spacer(Modifier.weight(1f))
                // Start økt åpner øktmodus (sett for sett). "Logg" er for å føre en økt i ettertid.
                OutlinedButton(onClick = { showLogDialog = true }, shape = RoundedCornerShape(14.dp)) { Text(tr("Log", "Logg")) }
                Button(onClick = { requestNotificationPermission(); vm.openGuided(day) }, shape = RoundedCornerShape(14.dp)) {
                    Text(if (activeSession == null) tr("Start", "Start økt") else tr("Open", "Åpne økta"))
                }
            }
        }
    }

    val pe = detailFor
    if (pe != null) {
        val ex = ExerciseLibrary.get(pe.exerciseId)
        val suggestion = ex?.let { ProgressionAdvisor.suggestion(it, pe, vm.logs, weightOptions(it, vm.profile)) }
        Dialog(onDismissRequest = { detailFor = null }) {
            Card {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (ex != null) {
                        ExerciseIllustration(ex, modifier = Modifier.size(160.dp), animate = true)
                        Spacer(Modifier.height(12.dp))
                        Text(ex.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(tr("${pe.sets} ${if (pe.sets == 1) "set" else "sets"} × ${pe.reps} · rest ${pe.restSec} s", "${pe.sets} sett × ${pe.reps} · pause ${pe.restSec} sek"), style = MaterialTheme.typography.bodyMedium)
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
                            OutlinedButton(onClick = { openExerciseVideo(context, ex) }) { Text(tr("Watch video", "Se video")) }
                            OutlinedButton(onClick = { editFor = pe; detailFor = null }) { Text(tr("Edit", "Rediger")) }
                            OutlinedButton(onClick = { swapFor = pe; detailFor = null }) { Text(tr("Swap exercise", "Bytt øvelse")) }
                        }
                    } else {
                        Text(pe.exerciseId)
                    }
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { detailFor = null }) { Text(tr("Close", "Lukk")) }
                }
            }
        }
    }

    if (confirmCancelSession) {
        ConfirmDialog(
            title = tr("Cancel the workout?", "Avbryte økta?"),
            text = tr("The time spent so far won't be saved.", "Tiden som er brukt så langt blir ikke lagret."),
            confirmLabel = tr("Cancel workout", "Avbryt økta"),
            onConfirm = { vm.discardGuided() },
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
                Text(tr("Replace “${current.name}”", "Bytt ut «${current.name}»"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (alternatives.isEmpty()) {
                    Text(
                        tr(
                            "No other exercises found with the same movement pattern and your equipment.",
                            "Fant ingen andre øvelser med samme bevegelsesmønster og utstyret ditt.",
                        ),
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
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(tr("Cancel", "Avbryt")) }
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
                Text(tr("Edit exercise", "Rediger øvelse"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = sets, onValueChange = { sets = it }, label = { Text(tr("Sets", "Sett")) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = reps, onValueChange = { reps = it }, label = { Text(tr("Reps (e.g. 8-12 or 30-45 s)", "Reps (f.eks. 8-12 eller 30-45 sek)")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = restSec, onValueChange = { restSec = it }, label = { Text(tr("Rest (seconds)", "Pause (sekunder)")) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text(tr("Cancel", "Avbryt")) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        onSave(
                            sets.toIntOrNull()?.coerceIn(1, 10) ?: pe.sets,
                            reps.ifBlank { pe.reps },
                            restSec.toIntOrNull()?.coerceIn(0, 600) ?: pe.restSec,
                        )
                    }) { Text(tr("Save", "Lagre")) }
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

    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = colors.surface)) {
            Column(
                Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column {
                    SectionLabel(tr("Log workout", "Logg økt"))
                    Text(day.title, style = MaterialTheme.typography.headlineSmall)
                }
                day.exercises.forEach { pe ->
                    val ex = ExerciseLibrary.get(pe.exerciseId)
                    val (weighted, setsState) = state.getValue(pe.exerciseId)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainer).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (ex != null) {
                                var showInfo by remember { mutableStateOf(false) }
                                if (showInfo) ExerciseInfoDialog(ex, onDismiss = { showInfo = false })
                                Box(Modifier.clip(RoundedCornerShape(15.dp)).clickable { showInfo = true }) { ExerciseTile(ex, size = 52.dp) }
                            }
                            Column(Modifier.weight(1f)) {
                                if (pe.group.isNotEmpty()) {
                                    Text(tr("Superset ${pe.group}", "Supersett ${pe.group}"), style = MaterialTheme.typography.labelMedium, color = colors.tertiary)
                                }
                                Text(ex?.name ?: pe.exerciseId, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    tr("Target ${pe.sets} × ${pe.reps}", "Mål ${pe.sets} × ${pe.reps}"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                        setsState.forEachIndexed { i, (repsState, weightState) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(30.dp).clip(CircleShape).background(colors.surfaceVariant), contentAlignment = Alignment.Center) {
                                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                                }
                                OutlinedTextField(
                                    value = repsState.value,
                                    onValueChange = { repsState.value = it },
                                    suffix = { Text(if (ex?.timed == true) tr("sec", "sek") else "reps", color = colors.onSurfaceVariant) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f),
                                )
                                if (weighted) {
                                    Text("×", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                                    OutlinedTextField(
                                        value = weightState.value,
                                        onValueChange = { weightState.value = it },
                                        suffix = { Text("kg", color = colors.onSurfaceVariant) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // "Lukk", ikke "Avbryt": økta og klokka fortsetter, bare skjemaet lukkes.
                    TextButton(onClick = onDismiss) { Text(tr("Close", "Lukk")) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { showConfirm = true }) { Text(tr("Save", "Lagre")) }
                }
            }
        }
    }

    if (showConfirm) {
        ConfirmDialog(
            title = tr("Log the workout?", "Logge økta?"),
            text = tr("This saves the results for “${day.title}” and stops the workout clock.", "Dette lagrer resultatene for «${day.title}» og avslutter øktklokka."),
            confirmLabel = tr("Log", "Loggfør"),
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
        Text(tr("Settings", "Innstillinger"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        ReminderSection(vm, requestNotificationPermission)

        HorizontalDivider()

        DataSection(exportData, importData)

        HorizontalDivider()

        // Health Connect finnes fra Android 9.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HealthConnectSection(vm)

            HorizontalDivider()
        }

        StravaSection(vm)

        HorizontalDivider()

        AiSection(vm)
    }
}

@Composable
private fun DataSection(exportData: () -> Unit, importData: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Data", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            tr(
                "Everything is stored only on this phone. Export to a file before switching phones, or as a simple backup.",
                "Alt lagres kun lokalt på telefonen. Eksporter til fil før du bytter telefon, eller som en enkel backup.",
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = exportData, modifier = Modifier.weight(1f)) { Text(tr("Export", "Eksporter")) }
            OutlinedButton(onClick = importData, modifier = Modifier.weight(1f)) { Text(tr("Import", "Importer")) }
        }
        Text(
            tr("Your AI key is not included in the export.", "AI-nøkkelen din tas ikke med i eksporten."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderSection(vm: AppViewModel, requestNotificationPermission: () -> Unit) {
    val r = vm.reminders

    Text(tr("Workout reminders", "Treningspåminnelser"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Turn on reminders", "Skru på påminnelser"), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = r.enabled,
            onCheckedChange = { checked ->
                if (checked) requestNotificationPermission()
                vm.updateReminders(r.copy(enabled = checked))
            },
        )
    }

    if (r.enabled) {
        Text(tr("Which days?", "Hvilke dager?"), style = MaterialTheme.typography.titleMedium)
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

        Text(tr("Time: %02d:%02d", "Tidspunkt: %02d:%02d").format(r.hour, r.minute), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Hour", "Time"), style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = { vm.updateReminders(r.copy(hour = (r.hour + 23) % 24)) }) { Text("−") }
            IconButton(onClick = { vm.updateReminders(r.copy(hour = (r.hour + 1) % 24)) }) { Text("+") }
            Spacer(Modifier.width(8.dp))
            Text("Min", style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = { vm.updateReminders(r.copy(minute = (r.minute + 45) % 60)) }) { Text("−") }
            IconButton(onClick = { vm.updateReminders(r.copy(minute = (r.minute + 15) % 60)) }) { Text("+") }
        }

        Text(
            tr(
                "Minutes change in 15-minute steps. The notification may arrive a few minutes late if the phone is saving battery.",
                "Minuttene hopper i kvarter. Varselet kan komme noen minutter etter valgt tid hvis telefonen sparer strøm.",
            ),
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
        Text(tr("AI model", "AI-modell"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            tr(
                "Your sign-in to the chat app can't be reused in another app. To call a model directly you need an API key " +
                    "(created for free at the provider, paid per use. A plan typically costs less than a cent). " +
                    "To skip the key, use “Copy prompt” and “Paste reply” on the Setup page with the chat you're already signed in to.",
                "Innloggingen din i chat-appen kan ikke gjenbrukes i en annen app. For å kalle en modell direkte trenger du en API-nøkkel " +
                    "(opprettes gratis hos leverandøren, betalt per bruk. Et program koster typisk noen øre). " +
                    "Vil du slippe nøkkel, bruk «Kopier prompt» og «Lim inn svar» på oppsettsiden med chatten du allerede er logget inn på.",
            ),
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(tr("Provider", "Leverandør"), style = MaterialTheme.typography.titleMedium)
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
            label = { Text(tr("API key", "API-nøkkel")) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = s.model,
            onValueChange = { vm.updateAi(s.copy(model = it.trim())) },
            label = { Text(tr("Model (empty = ${s.provider.defaultModel})", "Modell (tom = ${s.provider.defaultModel})")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            tr(
                "The key is stored only on this phone. Only your profile (equipment, goal, level, notes) is sent to the provider.",
                "Nøkkelen lagres kun lokalt på telefonen. Kun profilen din (utstyr, mål, nivå, notater) sendes til leverandøren.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Valgfri opplasting til Strava, med riktige øvelser så muskelkartet stemmer. */
@Composable
private fun StravaSection(vm: AppViewModel) {
    val context = LocalContext.current
    val st = vm.strava
    val colors = MaterialTheme.colorScheme
    var clientId by remember(st.clientId) { mutableStateOf(st.clientId) }
    var clientSecret by remember(st.clientSecret) { mutableStateOf(st.clientSecret) }
    fun openUrl(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Strava", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            tr(
                "Upload workouts to Strava with the right exercises, sets and weights, so the muscle map in Strava matches what you trained.",
                "Last opp økter til Strava med riktige øvelser, sett og vekter, så muskelkartet i Strava stemmer med det du trente.",
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Use Strava upload", "Bruk opplasting til Strava"), Modifier.weight(1f))
            Switch(checked = st.enabled, onCheckedChange = { vm.updateStrava(st.copy(enabled = it)) })
        }
        if (!st.enabled) return@Column

        if (!st.connected) {
            Text(
                tr(
                    "One-time setup:\n1. Create an API application at strava.com/settings/api. Enter localhost as \"Authorization Callback Domain\".\n2. Paste the Client ID and Client Secret here.\n3. Tap Connect and approve in Strava.",
                    "Engangsoppsett:\n1. Lag en API-app på strava.com/settings/api. Skriv localhost under «Authorization Callback Domain».\n2. Lim inn Client ID og Client Secret her.\n3. Trykk Koble til og godkjenn i Strava.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            TextButton(onClick = { openUrl("https://www.strava.com/settings/api") }) {
                Text(tr("Open strava.com/settings/api", "Åpne strava.com/settings/api"))
            }
            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it.trim() },
                label = { Text("Client ID") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = clientSecret,
                onValueChange = { clientSecret = it.trim() },
                label = { Text("Client Secret") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    vm.updateStrava(st.copy(clientId = clientId, clientSecret = clientSecret))
                    openUrl(StravaClient.authorizeUri(clientId).toString())
                },
                enabled = clientId.isNotBlank() && clientSecret.isNotBlank(),
            ) { Text(tr("Connect to Strava", "Koble til Strava")) }
        } else {
            Text(
                tr("Connected", "Koblet til") + (st.athleteName.takeIf { it.isNotBlank() }?.let { tr(" as $it", " som $it") } ?: ""),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Upload automatically", "Last opp automatisk"))
                    Text(
                        tr(
                            "When a workout is saved. Off: use the Strava button on the workout image.",
                            "Når en økt lagres. Av: bruk Strava-knappen på øktbildet.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Switch(checked = st.autoUpload, onCheckedChange = { vm.updateStrava(st.copy(autoUpload = it)) })
            }
            TextButton(onClick = { vm.disconnectStrava() }) { Text(tr("Disconnect", "Koble fra")) }
        }
        Text(
            tr(
                "Your watch probably uploads the same workout too. Delete that copy in Strava (or don't record strength workouts on the watch), so it isn't counted twice.",
                "Klokka laster sannsynligvis opp den samme økta også. Slett den kopien i Strava (eller ikke ta opp styrkeøkter på klokka), så den ikke telles dobbelt.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun HealthConnectSection(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = vm.profile
    val status = HealthConnectSync.status(context)
    var note by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(HealthConnectSync.permissions)) {
            note = null
            vm.updateProfile(vm.profile.copy(healthConnect = true))
        } else {
            note = tr("Permission wasn't granted.", "Tillatelsen ble ikke gitt.")
        }
    }
    val hrLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(HealthConnectSync.heartRatePermissions)) {
            note = null
            vm.updateProfile(vm.profile.copy(heartRate = true))
        } else {
            note = tr("Permission wasn't granted.", "Tillatelsen ble ikke gitt.")
        }
    }
    // Er tillatelsen fjernet i Health Connect, slås bryteren av her også.
    LaunchedEffect(p.healthConnect) {
        if (p.healthConnect && !HealthConnectSync.hasPermission(context)) vm.updateProfile(vm.profile.copy(healthConnect = false))
    }
    LaunchedEffect(p.heartRate) {
        if (p.heartRate && !HealthConnectSync.hasHeartRatePermission(context)) vm.updateProfile(vm.profile.copy(heartRate = false))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Health Connect", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            tr(
                "Save logged workouts (strength training with start and end time) to Health Connect, so they show up in other health and fitness apps.",
                "Lagre loggførte økter (styrketrening med start- og sluttid) i Health Connect, så de vises i andre helse- og treningsapper.",
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Save workouts to Health Connect", "Lagre økter i Health Connect"), Modifier.weight(1f))
            Switch(
                checked = p.healthConnect,
                onCheckedChange = { on ->
                    when {
                        !on -> vm.updateProfile(p.copy(healthConnect = false))
                        status == HealthConnectClient.SDK_AVAILABLE -> scope.launch {
                            if (HealthConnectSync.hasPermission(context)) {
                                vm.updateProfile(vm.profile.copy(healthConnect = true))
                            } else {
                                launcher.launch(HealthConnectSync.permissions)
                            }
                        }
                        else -> HealthConnectSync.openInstall(context)
                    }
                },
            )
        }
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            Text(
                tr(
                    "Health Connect needs to be installed or updated on this phone. The switch opens Google Play.",
                    "Health Connect må installeres eller oppdateres på denne telefonen. Bryteren åpner Google Play.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            tr(
                "Only workouts timed with the workout clock are saved, since Health Connect needs a start and end time.",
                "Bare økter som er tatt tid på med øktklokka lagres, fordi Health Connect trenger start- og sluttid.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Heart rate in workouts", "Puls i øktene"))
                Text(
                    tr(
                        "Reads heart rate from Health Connect (e.g. from a Garmin watch via Garmin Connect) for the time the workout lasted. Shown on the workout image and included in Strava uploads.",
                        "Leser puls fra Health Connect (f.eks. fra Garmin-klokka via Garmin Connect) for tiden økta varte. Vises på øktbildet og blir med i opplastingen til Strava.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = p.heartRate,
                onCheckedChange = { on ->
                    when {
                        !on -> vm.updateProfile(p.copy(heartRate = false))
                        status == HealthConnectClient.SDK_AVAILABLE -> scope.launch {
                            if (HealthConnectSync.hasHeartRatePermission(context)) {
                                vm.updateProfile(vm.profile.copy(heartRate = true))
                            } else {
                                hrLauncher.launch(HealthConnectSync.heartRatePermissions + HealthConnectSync.workoutReadPermissions)
                            }
                        }
                        else -> HealthConnectSync.openInstall(context)
                    }
                },
            )
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}
