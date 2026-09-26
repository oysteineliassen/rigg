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

    /** Økes hver gang et nytt program er lagt inn, slik at UI kan navigere til programmet. */
    var planVersion by mutableStateOf(0); private set

    // Timerne bor i WorkoutTimers (prosess-nivå) slik at WorkoutTimerService kan kjøre dem videre med skjermen av.
    val restTimer: RestTimer? get() = WorkoutTimers.rest
    val sessionTimer: ActiveSession? get() = WorkoutTimers.session

    init {
        WorkoutTimers.init(app)
        dropOrphanSession()
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
                .onFailure { error = it.message ?: "Ukjent feil" }
            busy = false
        }
    }

    fun promptForClipboard(): String = AiClient.buildPrompt(profile)

    /** For "kopier prompt til chat, lim inn svaret"-flyten. */
    fun importPlan(raw: String): Boolean {
        error = null
        val r = AiClient.parsePlan(raw, profile)
        r.onSuccess { setPlan(it) }.onFailure { error = it.message ?: "Kunne ikke lese svaret" }
        return r.isSuccess
    }

    fun clearPlan() { plan = null; warnings = emptyList(); store.savePlan(null); dropOrphanSession(); refreshWidget() }

    fun logSession(dayTitle: String, results: List<ExerciseResult> = emptyList()) {
        // Bruker øktklokka hvis den kjører for denne dagen, ellers 0 (ukjent varighet).
        val durationMin = sessionTimer?.takeIf { it.dayTitle == dayTitle }?.let { (it.elapsedSec() + 30) / 60 } ?: 0
        val updated = logs + SessionLog(LocalDate.now().toString(), dayTitle, results, durationMin)
        logs = updated
        store.saveLogs(updated)
        if (sessionTimer?.dayTitle == dayTitle) stopSession()
        refreshWidget()
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
                ?: throw IllegalStateException("Fikk ikke skrivetilgang til filen")
        }.onFailure { error = "Kunne ikke eksportere: ${it.message}" }
    }

    fun importFrom(uri: Uri) {
        runCatching {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("Fant ingen data i filen")
            if (!importJson(text)) throw IllegalStateException("Filen så ikke ut som en gyldig backup")
        }.onFailure { error = it.message ?: "Kunne ikke importere" }
    }
}
