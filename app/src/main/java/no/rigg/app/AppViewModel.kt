package no.rigg.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val exportFormat = Json { encodeDefaults = true; ignoreUnknownKeys = true; coerceInputValues = true }

    var profile by mutableStateOf(store.loadProfile()); private set
    var plan by mutableStateOf(store.loadPlan()); private set
    var logs by mutableStateOf(store.loadLogs()); private set
    var ai by mutableStateOf(store.loadAi()); private set
    var reminders by mutableStateOf(store.loadReminders()); private set

    var warnings by mutableStateOf<List<String>>(emptyList()); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    /** Øktmodus: økta som gjennomføres sett for sett, og om skjermen for den vises akkurat nå. */
    var guided by mutableStateOf(store.loadGuided()); private set
    var guidedVisible by mutableStateOf(true); private set

    /** Økes hver gang et nytt program er lagt inn, slik at UI kan navigere til programmet. */
    var planVersion by mutableStateOf(0); private set

    // Timerne bor i WorkoutTimers (prosess-nivå) slik at WorkoutTimerService kan kjøre dem videre med skjermen av.
    val restTimer: RestTimer? get() = WorkoutTimers.rest
    val sessionTimer: ActiveSession? get() = WorkoutTimers.session
    val timersPaused: Boolean get() = WorkoutTimers.paused

    /** Strava-oppsett, og status for opplastinger som er startet (nøkkel: [logKey]). */
    var strava by mutableStateOf(store.loadStrava()); private set
    var stravaUploads by mutableStateOf<Map<String, StravaUpload>>(emptyMap()); private set

    /** Økta som vises som delbart bilde (rett etter at den er logget, eller valgt fra historikken). */
    var shareLog by mutableStateOf<SessionLog?>(null)

    init {
        WorkoutTimers.init(app)
        // Oppvarming eller tidssett som teller ned til null (også mens øktmodus er skjult): gå videre.
        viewModelScope.launch {
            snapshotFlow { WorkoutTimers.work?.finished }.collect { if (it == true) onWorkFinished() }
        }
        dropOrphanSession()
        // Øktmodus uten øktklokke er en gammel, glemt økt (klokka forkastes etter 12 timer).
        if (guided != null && WorkoutTimers.session == null) saveGuided(null)
        // Hvis Android lukket appen midt i en økt: få tjenesten og varselet tilbake.
        if (WorkoutTimers.isActive) WorkoutTimers.ensureService(app)
    }

    fun updateProfile(p: Profile) { profile = p; store.saveProfile(p) }
    fun updateAi(s: AiSettings) { ai = s; store.saveAi(s) }

    fun updateReminders(r: ReminderSettings) {
        reminders = r
        store.saveReminders(r)
        ReminderScheduler.scheduleAll(getApplication(), r)
    }

    fun dismissError() { error = null }

    private fun refreshWidget() = WorkoutWidgetProvider.updateAll(getApplication())

    /** Hvis programmet byttes midt i en økt, finnes ikke dagen (og Avbryt-knappen) lenger: stopp klokka. */
    private fun dropOrphanSession() {
        val g = guided
        if (g != null && plan?.days?.none { it.title == g.dayTitle } != false) saveGuided(null)
        val s = WorkoutTimers.session ?: return
        if (plan?.days?.none { it.title == s.dayTitle } != false) stopSession()
    }

    private fun setPlan(g: Generated) {
        plan = g.plan.copy(createdAt = LocalDate.now().toString())
        warnings = g.warnings
        planVersion++
        store.savePlan(plan)
        dropOrphanSession()
        refreshWidget()
    }

    /** Regelbasert. Instant og offline. */
    fun generateWithRules() {
        error = null
        setPlan(ProgramGenerator.generate(profile))
    }

    /** AI via API. Faller ikke tilbake stille: brukeren ser feilen. */
    fun generateWithAi() {
        error = null
        busy = true
        viewModelScope.launch {
            AiClient.generate(ai, profile)
                .onSuccess { setPlan(it) }
                .onFailure { error = it.message ?: tr("Unknown error", "Ukjent feil") }
            busy = false
        }
    }

    fun promptForClipboard(): String = AiClient.buildPrompt(profile)

    /** For "kopier prompt til chat, lim inn svaret"-flyten. */
    fun importPlan(raw: String): Boolean {
        error = null
        val r = AiClient.parsePlan(raw, profile)
        r.onSuccess { setPlan(it) }.onFailure { error = it.message ?: tr("Couldn't read the response", "Kunne ikke lese svaret") }
        return r.isSuccess
    }

    fun clearPlan() { plan = null; warnings = emptyList(); store.savePlan(null); dropOrphanSession(); refreshWidget() }

    fun logSession(dayTitle: String, results: List<ExerciseResult> = emptyList()) {
        // Bruker øktklokka hvis den kjører for denne dagen, ellers 0 (ukjent varighet).
        val running = sessionTimer?.takeIf { it.dayTitle == dayTitle }
        val durationMin = running?.let { (it.elapsedSec() + 30) / 60 } ?: 0
        val log = SessionLog(
            LocalDate.now().toString(), dayTitle, results, durationMin,
            startedAtMs = running?.startedAtMs ?: 0,
            endedAtMs = if (running != null) System.currentTimeMillis() else 0,
        )
        val updated = logs + log
        logs = updated
        if (results.any { it.sets.isNotEmpty() }) {
            shareLog = log
            when {
                // Venter litt på pulsen fra klokka før opplastingen, så den kommer med.
                strava.ready && strava.autoUpload -> uploadToStrava(log, waitForHeartRate = profile.heartRate)
                profile.heartRate -> fetchHeartRate(log)
            }
        }
        store.saveLogs(updated)
        if (running != null) stopSession()
        if (guided?.dayTitle == dayTitle) saveGuided(null)
        refreshWidget()
        if (running != null && durationMin > 0 && profile.healthConnect) writeToHealthConnect(dayTitle, running.startedAtMs)
    }

    private fun writeToHealthConnect(title: String, startedAtMs: Long) {
        val start = java.time.Instant.ofEpochMilli(startedAtMs)
        val end = java.time.Instant.now()
        viewModelScope.launch {
            HealthConnectSync.writeWorkout(getApplication(), title, start, end).onFailure {
                error = tr("Couldn't save the workout to Health Connect: ${it.message}", "Kunne ikke lagre økta i Health Connect: ${it.message}")
            }
        }
    }

    // ---------------------------------------------------------------- ØKTMODUS

    private fun saveGuided(g: GuidedWorkout?) {
        guided = g
        store.saveGuided(g)
    }

    /** Starter (eller åpner igjen) øktmodus for en dag. Øktklokka startes samtidig. */
    fun openGuided(day: WorkoutDay) {
        // En annen dag var i gang: lagre settene som ble gjort der før den nye starter.
        val old = guided
        if (old != null && old.dayTitle != day.title && old.done.isNotEmpty()) {
            plan?.days?.find { it.title == old.dayTitle }?.let { finishGuided(it) }
        }
        if (sessionTimer?.dayTitle != day.title) startSession(day.title)
        if (guided?.dayTitle != day.title) saveGuided(GuidedWorkout(day.title))
        guidedVisible = true
    }

    fun updateGuided(g: GuidedWorkout) {
        // Går man til et annet sett mens planken teller, hører ikke nedtellingen til lenger.
        val w = workTimer
        if (w != null && w.kind == WorkKind.SET && w.index != g.step) stopWork()
        saveGuided(g)
    }

    // ---------------------------------------------------------------- OPPVARMING OG TIDSSETT
    // Nedtellingen ligger i WorkoutTimers, så den fortsetter når øktmodus skjules.

    val workTimer: WorkTimer? get() = WorkoutTimers.work

    private fun guidedDay(): WorkoutDay? = guided?.let { g -> plan?.days?.find { it.title == g.dayTitle } }

    fun warmupItems(day: WorkoutDay): List<WarmupItem> = warmupFor(day, profile.equipment)

    /** Starter nedtellingen for oppvarmingsøvelsen man står på. */
    fun startWarmup() {
        val g = guided ?: return
        val day = guidedDay() ?: return
        val item = warmupItems(day).getOrNull(g.warmupIndex) ?: return
        WorkoutTimers.startWork(getApplication(), WorkKind.WARMUP, item.name, g.warmupIndex, item.seconds)
    }

    /** Neste oppvarmingsøvelse med en gang (eller ferdig med oppvarmingen etter den siste). */
    fun nextWarmup() {
        val g = guided ?: return
        advanceWarmup(g, g.warmupIndex)
    }

    fun skipWarmup() {
        val g = guided ?: return
        if (workTimer?.kind == WorkKind.WARMUP) stopWork()
        saveGuided(g.copy(warmupDone = true))
    }

    private fun advanceWarmup(g: GuidedWorkout, from: Int) {
        val day = guidedDay() ?: return
        val items = warmupItems(day)
        val next = from + 1
        if (next < items.size) {
            saveGuided(g.copy(warmupIndex = next))
            WorkoutTimers.startWork(getApplication(), WorkKind.WARMUP, items[next].name, next, items[next].seconds)
        } else {
            if (workTimer?.kind == WorkKind.WARMUP) stopWork()
            saveGuided(g.copy(warmupDone = true, warmupIndex = items.size))
        }
    }

    /** Starter nedtellingen for et tidssett (planke, veggsitt ...). Vekten lagres med, så settet kan logges uten skjermen. */
    fun startTimedSet(step: Int, label: String, seconds: Int, weightKg: Double?, perSide: Boolean = false) =
        WorkoutTimers.startWork(getApplication(), WorkKind.SET, label, step, seconds, weightKg, perSide)

    /** Stopper tidssettet før tiden og logger tiden som ble holdt (per side: på siden man var på). */
    fun stopTimedSetEarly() {
        val w = workTimer?.takeIf { it.kind == WorkKind.SET } ?: return
        val held = when (w.side) {
            WorkSide.SWITCH -> w.holdSeconds
            else -> (w.totalSeconds - w.secondsLeft()).coerceAtLeast(0)
        }
        stopWork()
        guidedDay()?.let { completeSet(it, w.index, held, w.weightKg) }
    }

    fun stopWork() = WorkoutTimers.stopWork(getApplication())

    /** Logger et sett i øktmodus, går til neste og starter hvilen etter det. */
    fun completeSet(day: WorkoutDay, stepIndex: Int, value: Int, weightKg: Double?) {
        val g = guided ?: return
        val steps = sessionSteps(day)
        val step = steps.getOrNull(stepIndex) ?: return
        val slot = step.exerciseNo - 1
        val done = g.done.filterNot { it.slot == slot && it.setIndex == step.setIndex } +
            DoneSet(step.pe.exerciseId, slot, step.setIndex, value, weightKg)
        saveGuided(g.copy(step = stepIndex + 1, done = done))
        if (step.restAfter > 0 && stepIndex + 1 < steps.size) startRestTimer(step.restAfter)
    }

    private fun onWorkFinished() {
        val w = WorkoutTimers.takeFinishedWork(getApplication()) ?: return
        val g = guided ?: return
        when (w.kind) {
            WorkKind.WARMUP -> if (!g.warmupDone && w.index == g.warmupIndex) advanceWarmup(g, w.index)
            WorkKind.SET -> if (w.index == g.step) guidedDay()?.let { completeSet(it, w.index, w.holdSeconds, w.weightKg) }
        }
    }

    /** Skjuler øktmodus uten å avslutte økta (klokka og settene beholdes). */
    fun hideGuided() { guidedVisible = false }

    /** Historikken som egen skjerm. [historyStart]: åpne rett på denne økta (null = lista). */
    var historyOpen by mutableStateOf(false); private set
    var historyStart by mutableStateOf<SessionLog?>(null); private set
    /** Vis bare uka som starter denne mandagen (fra søylene på Fremgang), null = alt. */
    var historyWeek by mutableStateOf<java.time.LocalDate?>(null); private set

    fun openHistory(start: SessionLog? = null, week: java.time.LocalDate? = null) {
        historyStart = start
        historyWeek = week
        historyOpen = true
    }

    fun closeHistory() {
        historyOpen = false
        historyStart = null
        historyWeek = null
    }

    /** Viser øktmodus igjen etter [hideGuided], f.eks. fra hvilebaren. Gjør ingenting uten en påbegynt økt. */
    fun showGuided() {
        val g = guided ?: return
        if (plan?.days?.any { it.title == g.dayTitle } == true) guidedVisible = true
    }

    /** Lagrer settene som er gjort som en vanlig logg, og avslutter økta. */
    fun finishGuided(day: WorkoutDay) {
        val g = guided ?: return
        val results = day.exercises.mapIndexedNotNull { slot, pe ->
            val sets = g.done.filter { it.slot == slot }.sortedBy { it.setIndex }.map { SetResult(it.reps, it.weightKg) }
            if (sets.isEmpty()) null else ExerciseResult(pe.exerciseId, sets)
        }
        stopRestTimer()
        stopWork()
        logSession(day.title, results)
    }

    /** Avbryter økta uten å logge noe. */
    fun discardGuided() {
        saveGuided(null)
        stopRestTimer()
        stopWork()
        stopSession()
    }

    // ---------------------------------------------------------------- NESTE BLOKK

    fun startNextBlock() {
        val current = plan ?: return
        // Øvelsene byttes ut, så en påbegynt økt i øktmodus passer ikke lenger.
        if (guided != null) saveGuided(null)
        setPlan(nextBlock(current, logs, profile))
    }

    fun undoLastLog() {
        if (logs.isEmpty()) return
        logs = logs.dropLast(1)
        store.saveLogs(logs)
        refreshWidget()
    }

    fun deleteLog(log: SessionLog) {
        val idx = logs.indexOf(log)
        if (idx == -1) return
        logs = logs.toMutableList().also { it.removeAt(idx) }
        store.saveLogs(logs)
        refreshWidget()
    }

    /** Bytter ut en øvelse i en gitt dag med en annen, uten å røre resten av programmet. */
    fun swapExercise(dayTitle: String, oldExerciseId: String, newExercise: Exercise) {
        val current = plan ?: return
        val newReps = ProgramGenerator.suggestedReps(newExercise, profile)
        val updatedDays = current.days.map { day ->
            if (day.title != dayTitle) return@map day
            day.copy(
                exercises = day.exercises.map { pe ->
                    if (pe.exerciseId == oldExerciseId) {
                        pe.copy(exerciseId = newExercise.id, reps = newReps, tip = newExercise.note)
                    } else pe
                },
            )
        }
        plan = current.copy(days = updatedDays)
        store.savePlan(plan)
    }

    /** Manuell justering av sett/reps/pause for én øvelse i en gitt dag. */
    fun updateExercise(dayTitle: String, exerciseId: String, sets: Int, reps: String, restSec: Int) {
        val current = plan ?: return
        val updatedDays = current.days.map { day ->
            if (day.title != dayTitle) return@map day
            day.copy(
                exercises = day.exercises.map { pe ->
                    if (pe.exerciseId == exerciseId) pe.copy(sets = sets, reps = reps, restSec = restSec) else pe
                },
            )
        }
        plan = current.copy(days = updatedDays)
        store.savePlan(plan)
    }

    // ---------------------------------------------------------------- ØKTKLOKKE OG HVILETIMER
    // Selve nedtellingen, lydene og varselet håndteres av WorkoutTimerService.

    /** Starter øktklokka, så varigheten logges automatisk når man trykker "Logg økt". */
    fun startSession(dayTitle: String) = WorkoutTimers.startSession(getApplication(), dayTitle)

    /** Avbryter øktklokka uten å logge noe. */
    fun stopSession() = WorkoutTimers.stopSession(getApplication())

    fun startRestTimer(seconds: Int) = WorkoutTimers.startRest(getApplication(), seconds)

    fun addRestSeconds(delta: Int) = WorkoutTimers.addRest(getApplication(), delta)

    fun stopRestTimer() = WorkoutTimers.stopRest(getApplication())

    // ---------------------------------------------------------------- STRAVA

    /** Identifiserer en logg uavhengig av om den er lastet opp (stravaActivityId endres underveis). */
    fun logKey(log: SessionLog): String = "${log.date}|${log.dayTitle}|${log.startedAtMs}|${log.results.hashCode()}"

    fun updateStrava(s: StravaSettings) {
        strava = s
        store.saveStrava(s)
    }

    /** Kalles når Strava sender oss tilbake etter innloggingen (rigg://localhost/strava?code=...). */
    fun finishStravaLogin(code: String?, errorParam: String?, scope: String?) {
        if (code == null) {
            if (errorParam != null) error = tr("Strava login was cancelled.", "Innloggingen til Strava ble avbrutt.")
            return
        }
        if (scope != null && "activity:write" !in scope) {
            error = tr(
                "Rigg needs permission to upload activities. Connect again and keep that box ticked.",
                "Rigg trenger tillatelse til å laste opp aktiviteter. Koble til på nytt og la den boksen være krysset av.",
            )
            return
        }
        viewModelScope.launch {
            runCatching { StravaClient.exchangeCode(strava, code) }
                .onSuccess { updateStrava(it) }
                .onFailure { error = tr("Couldn't connect to Strava: ${it.message}", "Kunne ikke koble til Strava: ${it.message}") }
        }
    }

    fun disconnectStrava() = updateStrava(strava.copy(accessToken = "", refreshToken = "", expiresAt = 0, athleteName = ""))

    // ---------------------------------------------------------------- PULS

    /** Henting av puls som pågår (nøkkel: [logKey]), så knappen kan vise det. */
    var heartRateLoading by mutableStateOf<Set<String>>(emptySet()); private set

    /** Pulsmålingene fra Health Connect for tidsrommet økta varte. Tom hvis puls er slått av eller ingen finnes. */
    private suspend fun heartRateFor(log: SessionLog): List<HrSample> {
        if (!profile.heartRate) return emptyList()
        val start = StrengthFit.startMsFor(log)
        val end = start + StrengthFit.elapsedSecFor(log) * 1000L
        return HealthConnectSync.readHeartRate(getApplication(), java.time.Instant.ofEpochMilli(start), java.time.Instant.ofEpochMilli(end))
    }

    /** Henter puls til en logget økt og lagrer snitt og maks i loggen. Returnerer målingene. */
    fun fetchHeartRate(log: SessionLog) {
        val key = logKey(log)
        if (key in heartRateLoading) return
        heartRateLoading = heartRateLoading + key
        viewModelScope.launch {
            val samples = heartRateFor(log)
            heartRateLoading = heartRateLoading - key
            if (samples.isEmpty()) {
                error = tr(
                    "No heart rate found for this workout in Health Connect yet. Sync the watch and try again.",
                    "Fant ingen puls for denne økta i Health Connect ennå. Synk klokka og prøv igjen.",
                )
            } else {
                saveHeartRate(key, samples)
            }
        }
    }

    private fun saveHeartRate(key: String, samples: List<HrSample>) {
        val (avg, max) = hrStats(samples) ?: return
        updateLog(key) { it.copy(avgHr = avg, maxHr = max) }
    }

    /** Endrer en logg (finnes via [logKey]) og lagrer, også i økta som vises som bilde. */
    private fun updateLog(key: String, change: (SessionLog) -> SessionLog): SessionLog? {
        val idx = logs.indexOfFirst { logKey(it) == key }
        if (idx == -1) return null
        val updated = change(logs[idx])
        logs = logs.toMutableList().also { it[idx] = updated }
        store.saveLogs(logs)
        if (shareLog?.let { logKey(it) } == key) shareLog = updated
        return updated
    }

    // ---------------------------------------------------------------- KLOKKESLETT FOR ELDRE LOGGER
    // Logger fra før Rigg lagret klokkeslett har bare dato. Da brukes klokkas økt i Health Connect, eller tiden du velger.

    /** Setter start (og slutt) for økta. Pulsen hentes på nytt for det nye tidsrommet. */
    fun setStartTime(log: SessionLog, startMs: Long, endMs: Long = startMs + StrengthFit.elapsedSecFor(log) * 1000L) {
        val updated = updateLog(logKey(log)) { it.copy(startedAtMs = startMs, endedAtMs = endMs, avgHr = null, maxHr = null) } ?: return
        if (profile.heartRate) fetchHeartRate(updated)
    }

    /** Leter etter klokkas styrkeøkt samme dag i Health Connect. [onResult] får true hvis tiden ble satt. */
    fun findStartTimeInHealthConnect(log: SessionLog, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val date = runCatching { LocalDate.parse(log.date) }.getOrNull()
            val found = date?.let { HealthConnectSync.findStrengthWorkout(getApplication(), it, log.durationMin) }
            if (found != null) setStartTime(log, found.first.toEpochMilli(), found.second.toEpochMilli())
            onResult(found != null)
        }
    }

    /**
     * Laster opp økta til Strava som FIT-fil med riktige øvelser (og puls, hvis den finnes). Status havner i
     * [stravaUploads]. Med [waitForHeartRate] ventes det opptil fem minutter på at klokka har synket pulsen til
     * Health Connect; finnes den fortsatt ikke, lastes økta opp uten.
     */
    /** Sjekker om økta (lastet opp tidligere) fortsatt ligger på Strava, før den eventuelt lastes opp igjen. */
    fun checkStravaActivity(activityId: Long, onResult: (StravaClient.ActivityStatus) -> Unit) {
        viewModelScope.launch {
            val status = runCatching {
                val s = StravaClient.freshToken(strava)
                if (s != strava) updateStrava(s)
                StravaClient.activityStatus(s.accessToken, activityId)
            }.getOrDefault(StravaClient.ActivityStatus.UNKNOWN)
            onResult(status)
        }
    }

    /**
     * [reupload]: lastes opp på nytt (f.eks. etter at aktiviteten ble slettet i Strava). Får en ny external_id,
     * ellers kan Strava avvise den som en kopi av den gamle.
     */
    fun uploadToStrava(log: SessionLog, waitForHeartRate: Boolean = false, reupload: Boolean = false) {
        if (!strava.ready) return
        val key = logKey(log)
        if (stravaUploads[key] is StravaUpload.Working || stravaUploads[key] is StravaUpload.WaitingForHeartRate) return
        viewModelScope.launch {
            var samples = heartRateFor(log)
            if (waitForHeartRate && samples.isEmpty()) {
                stravaUploads = stravaUploads + (key to StravaUpload.WaitingForHeartRate)
                repeat(10) {
                    if (samples.isNotEmpty() || stravaUploads[key] !is StravaUpload.WaitingForHeartRate) return@repeat
                    kotlinx.coroutines.delay(30_000)
                    samples = heartRateFor(log)
                }
            }
            if (samples.isNotEmpty()) saveHeartRate(key, samples)
            stravaUploads = stravaUploads + (key to StravaUpload.Working)
            runCatching {
                val s = StravaClient.freshToken(strava)
                if (s != strava) updateStrava(s)
                val externalId = "rigg-${StrengthFit.startMsFor(log)}" + if (reupload) "-${System.currentTimeMillis() / 1000}" else ""
                val description = StravaClient.describe(log)
                // Først Stravas eget JSON-format (Stravas øvelsesnavn, f.eks. rumensk markløft med manualer).
                // Avviser Strava fila, prøves FIT-fila (Garmins øvelsesnavn) i stedet.
                // Først full JSON, så en enklere JSON (uten sport_type og puls), til slutt FIT. Feilene vises etterpå.
                val errors = mutableListOf<String>()
                var done: StravaUpload.Done? = null
                // JSON er slått av: Strava avviste fila (oktober 2026), og hvert forsøk ga bare ventetid og en feilmelding.
                // Sett STRAVA_JSON = true for å prøve igjen.
                for ((minimal, suffix) in if (STRAVA_JSON) listOf(false to "", true to "-j2") else emptyList()) {
                    val json = withContext(Dispatchers.Default) {
                        StravaJson.forLog(log, samples, minimal = minimal).toByteArray(Charsets.UTF_8)
                    }
                    try {
                        done = StravaUpload.Done(StravaClient.upload(s.accessToken, json, "json", log.dayTitle, description, externalId + suffix))
                        break
                    } catch (e: StravaException) {
                        errors += e.message ?: "?"
                    }
                }
                done ?: run {
                    val fit = withContext(Dispatchers.Default) { StrengthFit.forLog(log, samples) }
                    val id = StravaClient.upload(s.accessToken, fit, "fit", log.dayTitle, description, "$externalId-fit")
                    StravaUpload.Done(id, jsonError = errors.distinct().joinToString(" / ").ifBlank { null })
                }
            }.onSuccess { done ->
                markUploaded(key, done.activityId)
                stravaUploads = stravaUploads + (key to done)
            }.onFailure { e ->
                val result = when {
                    // Første opplasting som Strava allerede har fra Rigg (samme fil): den er der, så det er greit.
                    e is StravaDuplicateException && !reupload && e.activityId == log.stravaActivityId -> StravaUpload.Done(e.activityId)
                    e is StravaDuplicateException -> StravaUpload.Failed(
                        tr(
                            "Strava rejected it as a copy of an activity with the same start time (probably from the watch). Delete that one in Strava and try again.",
                            "Strava avviste den som en kopi av en aktivitet med samme starttid (trolig fra klokka). Slett den i Strava og prøv igjen.",
                        ),
                        duplicateOf = e.activityId,
                    )
                    else -> StravaUpload.Failed(e.message ?: tr("Upload failed", "Opplastingen feilet"))
                }
                if (result is StravaUpload.Done) markUploaded(key, result.activityId)
                stravaUploads = stravaUploads + (key to result)
            }
        }
    }

    private companion object {
        const val STRAVA_JSON = false
    }

    /** Hopper over ventingen på puls og laster opp med en gang. */
    fun uploadNowWithoutWaiting(log: SessionLog) {
        val key = logKey(log)
        if (stravaUploads[key] is StravaUpload.WaitingForHeartRate) stravaUploads = stravaUploads - key
    }

    private fun markUploaded(key: String, activityId: Long) = updateLog(key) { it.copy(stravaActivityId = activityId) }

    fun pauseTimers() = WorkoutTimers.pause(getApplication())

    fun resumeTimers() = WorkoutTimers.resume(getApplication())

    // ---------------------------------------------------------------- BACKUP / EKSPORT

    /** Ekskluderer AI-nøkkelen med vilje: en backup-fil skal kunne deles uten å lekke den. */
    fun exportJson(): String = exportFormat.encodeToString(ExportBundle(profile, plan, logs, reminders))

    fun importJson(text: String): Boolean = runCatching {
        val bundle = exportFormat.decodeFromString<ExportBundle>(text)
        profile = bundle.profile
        store.saveProfile(profile)
        plan = bundle.plan
        store.savePlan(plan)
        logs = bundle.logs
        store.saveLogs(logs)
        reminders = bundle.reminders
        store.saveReminders(reminders)
        ReminderScheduler.scheduleAll(getApplication(), reminders)
        planVersion++
        dropOrphanSession()
        refreshWidget()
    }.isSuccess

    fun exportTo(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(exportJson().toByteArray()) }
                ?: throw IllegalStateException(tr("Couldn't get write access to the file", "Fikk ikke skrivetilgang til filen"))
        }.onFailure { error = tr("Export failed: ${it.message}", "Kunne ikke eksportere: ${it.message}") }
    }

    fun importFrom(uri: Uri) {
        runCatching {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException(tr("No data found in the file", "Fant ingen data i filen"))
            if (!importJson(text)) throw IllegalStateException(tr("The file doesn't look like a valid backup", "Filen så ikke ut som en gyldig backup"))
        }.onFailure { error = it.message ?: tr("Import failed", "Kunne ikke importere") }
    }
}
