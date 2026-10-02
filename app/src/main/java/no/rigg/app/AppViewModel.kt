package no.rigg.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
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

    /** Økta som vises som delbart bilde (rett etter at den er logget, eller valgt fra historikken). */
    var shareLog by mutableStateOf<SessionLog?>(null)

    init {
        WorkoutTimers.init(app)
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
        val log = SessionLog(LocalDate.now().toString(), dayTitle, results, durationMin)
        val updated = logs + log
        logs = updated
        if (results.any { it.sets.isNotEmpty() }) shareLog = log
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

    fun updateGuided(g: GuidedWorkout) = saveGuided(g)

    /** Skjuler øktmodus uten å avslutte økta (klokka og settene beholdes). */
    fun hideGuided() { guidedVisible = false }

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
        logSession(day.title, results)
    }

    /** Avbryter økta uten å logge noe. */
    fun discardGuided() {
        saveGuided(null)
        stopRestTimer()
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
