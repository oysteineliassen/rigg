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
    /** Én arm eller ett bein om gangen: reps/tid gjelder per side. */
    val perSide: Boolean = false,
) {
    /** Navnet på telefonens språk. */
    val name: String get() = names.get()

    /** Engelsk navn: brukes i videosøk (flest videoer) og i AI-prompten. */
    val nameEn: String get() = names.en

    val note: String get() = notes.get()

    /** Forklaring for øvelser som tas én side om gangen, ellers null. */
    val perSideHint: String?
        get() = when {
            !perSide -> null
            timed -> tr("Per side: hold the time on one side, switch, then the same on the other.",
                "Per side: hold tiden på den ene siden, bytt, og like lenge på den andre.")
            else -> tr("Per side: 8 reps = 8 with one side first, then 8 with the other.",
                "Per side: 8 reps = 8 med den ene siden først, så 8 med den andre.")
        }

    /** " per side" / " per side" etter reps og tid, eller tom. */
    val perSideSuffix: String get() = if (perSide) tr(" per side", " per side") else ""

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
    /** Vektene man faktisk har, som fritekst: "2-20/2", "4 6 8 12,5" osv. Tolkes av [parseWeights]. */
    val dumbbellWeights: String = "",
    val kettlebellWeights: String = "",
    /** Skriv fullførte økter til Health Connect (krever tillatelse). */
    val healthConnect: Boolean = false,
    val heartRate: Boolean = false, // hent puls fra Health Connect (f.eks. fra Garmin-klokka) inn i loggede økter
)

@Serializable
data class PlannedExercise(
    val exerciseId: String,
    val sets: Int,
    val reps: String,
    val restSec: Int,
    val tip: String = "",
    /** Supersett: øvelser i samme dag med samme bokstav kjøres vekselvis, med pause etter siste i gruppa. Tom = alene. */
    val group: String = "",
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
    /** Hvilken 4-ukersblokk dette er (1 = første program, 2 = laget med "Neste blokk" osv.). */
    val block: Int = 1,
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
    val startedAtMs: Long = 0, // når økta startet (0 = ukjent: eldre logger, eller logget uten øktklokka)
    val stravaActivityId: Long? = null, // satt når økta er lastet opp til Strava
    val endedAtMs: Long = 0, // når økta ble lagret (0 = ukjent)
    val avgHr: Int? = null, // snittpuls fra Health Connect, hvis hentet
    val maxHr: Int? = null,
)

/** Ett gjennomført sett i øktmodus. */
@Serializable
data class DoneSet(
    val exerciseId: String,
    /** Plassen i dagens øvelsesliste (0, 1, 2 ...), i tilfelle samme øvelse står to ganger. */
    val slot: Int,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double? = null,
)

/** Pågående økt i øktmodus: hvor man er, og settene som er gjort. Lagres så den overlever at appen lukkes. */
@Serializable
data class GuidedWorkout(
    val dayTitle: String,
    val step: Int = 0,
    val done: List<DoneSet> = emptyList(),
    val warmupDone: Boolean = false,
    val warmupIndex: Int = 0, // oppvarmingsøvelsen man er på, så den ikke starter på nytt når øktmodus åpnes igjen
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
