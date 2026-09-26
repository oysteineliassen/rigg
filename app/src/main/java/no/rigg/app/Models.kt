package no.rigg.app

import kotlinx.serialization.Serializable

enum class Equipment(val label: String) {
    BODYWEIGHT("Kroppsvekt"), // alltid tilgjengelig, vises ikke som valg
    DUMBBELLS("Manualer"),
    KETTLEBELL("Kettlebell"),
    RESISTANCE_BANDS("Strikk"),
    PULL_UP_BAR("Tverrligger"),
    BENCH("Benk"),
    PLYO_BOX("Hoppekasse"),
    BARBELL("Stang og skiver"),
    CHAIR("Solid stol/sofa"),
    TABLE("Solid bord"),
    TRX("Slynger (TRX)"),
    JUMP_ROPE("Hoppetau");

    companion object {
        val selectable: List<Equipment> get() = values().filter { it != BODYWEIGHT }

        /** Utstyr som gir en faktisk belastning å øke (i motsetning til f.eks. en benk eller stol). */
        val loadBearing: Set<Equipment> = setOf(DUMBBELLS, KETTLEBELL, BARBELL, RESISTANCE_BANDS, TRX)
    }
}

enum class Muscle { CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, QUADS, GLUTES_HAMS, CALVES, ABS, FULL_BODY }

enum class Pattern { PUSH, PULL, SQUAT, HINGE, LUNGE, CORE, CARDIO }

enum class Goal(val label: String) {
    STRENGTH("Styrke"),
    HYPERTROPHY("Muskelvekst"),
    FITNESS("Generell form")
}

enum class Level(val label: String) {
    BEGINNER("Nybegynner"),
    INTERMEDIATE("Erfaren"),
    ADVANCED("Avansert")
}

/** En øvelse i biblioteket. requiresAll: alt må finnes. requiresAny: minst ett må finnes (tom = ingen krav). */
data class Exercise(
    val id: String,
    val name: String,
    val pattern: Pattern,
    val muscles: List<Muscle>,
    val requiresAll: Set<Equipment>,
    val requiresAny: Set<Equipment>,
    val level: Int, // 1 = enkel, 2 = middels, 3 = krevende
    val timed: Boolean,
    val note: String,
) {
    /** True hvis øvelsen bruker utstyr man kan legge på mer belastning på (manualer, kettlebell, stang, strikk, TRX). */
    fun isWeighted(): Boolean = (requiresAll + requiresAny).any { it in Equipment.loadBearing }
}

@Serializable
data class Profile(
    val equipment: Set<Equipment> = setOf(Equipment.DUMBBELLS),
    val equipmentDetails: String = "", // f.eks. "2 justerbare manualer 2-20 kg, gul strikk"
    val customEquipment: String = "", // fritekst for utstyr utover de forhåndsdefinerte valgene, f.eks. "sandsekk, boksesekk"
    val goal: Goal = Goal.HYPERTROPHY,
    val level: Level = Level.BEGINNER,
    val daysPerWeek: Int = 3,
    val minutesPerSession: Int = 45,
    val notes: String = "", // skader, ønsker osv.
)

@Serializable
data class PlannedExercise(
    val exerciseId: String,
    val sets: Int,
    val reps: String,
    val restSec: Int,
    val tip: String = "",
)

@Serializable
data class WorkoutDay(
    val title: String,
    val exercises: List<PlannedExercise>,
)

@Serializable
data class Plan(
    val name: String,
    val weeks: Int = 4,
    val days: List<WorkoutDay>,
    val progression: String = "",
    val createdAt: String = "", // ISO-dato, satt når planen tas i bruk (se AppViewModel.setPlan)
)

/** dagen 1 (mandag) til 7 (søndag), samme som java.time.DayOfWeek.value. */
@Serializable
data class ReminderSettings(
    val enabled: Boolean = false,
    val days: Set<Int> = emptySet(),
    val hour: Int = 18,
    val minute: Int = 0,
)

@Serializable
data class SetResult(
    val reps: Int,
    val weightKg: Double? = null,
)

@Serializable
data class ExerciseResult(
    val exerciseId: String,
    val sets: List<SetResult>,
)

@Serializable
data class SessionLog(
    val date: String,
    val dayTitle: String,
    val results: List<ExerciseResult> = emptyList(),
    val durationMin: Int = 0, // 0 = ukjent/ikke tidtatt (eldre logger, eller logget uten å bruke øktklokka)
)

/** Alt appen lagrer, samlet til én fil for backup/eksport. Ekskluderer AI-nøkkelen med vilje. */
@Serializable
data class ExportBundle(
    val profile: Profile,
    val plan: Plan? = null,
    val logs: List<SessionLog> = emptyList(),
    val reminders: ReminderSettings = ReminderSettings(),
    val formatVersion: Int = 1,
)
