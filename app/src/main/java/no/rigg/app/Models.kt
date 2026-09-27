package no.rigg.app

import kotlinx.serialization.Serializable

enum class Equipment(private val en: String, private val no: String) {
    BODYWEIGHT("Bodyweight", "Kroppsvekt"), // alltid tilgjengelig, vises ikke som valg
    DUMBBELLS("Dumbbells", "Manualer"),
    KETTLEBELL("Kettlebell", "Kettlebell"),
    RESISTANCE_BANDS("Resistance bands", "Strikk"),
    PULL_UP_BAR("Pull-up bar", "Tverrligger"),
    BENCH("Bench", "Benk"),
    PLYO_BOX("Plyo box", "Hoppekasse"),
    BARBELL("Barbell and plates", "Stang og skiver"),
    CHAIR("Sturdy chair/sofa", "Solid stol/sofa"),
    TABLE("Sturdy table", "Solid bord"),
    TRX("Suspension trainer (TRX)", "Slynger (TRX)"),
    JUMP_ROPE("Jump rope", "Hoppetau");

    val label: String get() = tr(en, no)

    companion object {
        val selectable: List<Equipment> get() = values().filter { it != BODYWEIGHT }

        /** Utstyr som gir en faktisk belastning å øke (i motsetning til f.eks. en benk eller stol). */
        val loadBearing: Set<Equipment> = setOf(DUMBBELLS, KETTLEBELL, BARBELL, RESISTANCE_BANDS, TRX)
    }
}

enum class Muscle { CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, QUADS, GLUTES_HAMS, CALVES, ABS, FULL_BODY }

enum class Pattern { PUSH, PULL, SQUAT, HINGE, LUNGE, CORE, CARDIO }

enum class Goal(private val en: String, private val no: String) {
    STRENGTH("Strength", "Styrke"),
    HYPERTROPHY("Muscle growth", "Muskelvekst"),
    FITNESS("General fitness", "Generell form");

    val label: String get() = tr(en, no)
}

enum class Level(private val en: String, private val no: String) {
    BEGINNER("Beginner", "Nybegynner"),
    INTERMEDIATE("Intermediate", "Erfaren"),
    ADVANCED("Advanced", "Avansert");

    val label: String get() = tr(en, no)
}

/** En øvelse i biblioteket. requiresAll: alt må finnes. requiresAny: minst ett må finnes (tom = ingen krav). */
data class Exercise(
    val id: String,
    val names: Txt,
    val pattern: Pattern,
    val muscles: List<Muscle>,
    val requiresAll: Set<Equipment>,
    val requiresAny: Set<Equipment>,
    val level: Int, // 1 = enkel, 2 = middels, 3 = krevende
    val timed: Boolean,
    val notes: Txt,
) {
    /** Navnet på telefonens språk. */
    val name: String get() = names.get()

    /** Engelsk navn: brukes i videosøk (flest videoer) og i AI-prompten. */
    val nameEn: String get() = names.en

    val note: String get() = notes.get()

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
