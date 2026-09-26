package no.rigg.app

import no.rigg.app.Equipment.*
import no.rigg.app.Muscle.*
import no.rigg.app.Pattern.*

data class Generated(val plan: Plan, val warnings: List<String>)

/**
 * Regelbasert programgenerator. Fungerer offline og brukes også til å validere AI-generert output.
 */
object ProgramGenerator {

    private data class Slot(val pattern: Pattern, val muscle: Muscle? = null)

    private val FULL_A = listOf(Slot(SQUAT), Slot(PUSH, CHEST), Slot(PULL, BACK), Slot(HINGE, GLUTES_HAMS), Slot(PUSH, SHOULDERS), Slot(CORE))
    private val FULL_B = listOf(Slot(LUNGE), Slot(PULL, BACK), Slot(PUSH, CHEST), Slot(HINGE, GLUTES_HAMS), Slot(PULL, BICEPS), Slot(CORE))
    private val FULL_C = listOf(Slot(SQUAT), Slot(PUSH, SHOULDERS), Slot(PULL, BACK), Slot(LUNGE), Slot(PUSH, TRICEPS), Slot(CORE))
    private val UPPER = listOf(Slot(PUSH, CHEST), Slot(PULL, BACK), Slot(PUSH, SHOULDERS), Slot(PULL, BACK), Slot(PUSH, TRICEPS), Slot(PULL, BICEPS), Slot(CORE))
    private val LOWER = listOf(Slot(SQUAT), Slot(HINGE, GLUTES_HAMS), Slot(LUNGE), Slot(HINGE, CALVES), Slot(CORE))
    private val PUSH_DAY = listOf(Slot(PUSH, CHEST), Slot(PUSH, SHOULDERS), Slot(PUSH, CHEST), Slot(PUSH, TRICEPS), Slot(CORE))
    private val PULL_DAY = listOf(Slot(PULL, BACK), Slot(PULL, BACK), Slot(PULL, BICEPS), Slot(PULL, SHOULDERS), Slot(CORE))

    private fun template(days: Int): List<Pair<String, List<Slot>>> = when (days.coerceIn(1, 6)) {
        1 -> listOf("Helkropp" to FULL_A)
        2 -> listOf("Helkropp A" to FULL_A, "Helkropp B" to FULL_B)
        3 -> listOf("Helkropp A" to FULL_A, "Helkropp B" to FULL_B, "Helkropp C" to FULL_C)
        4 -> listOf("Overkropp A" to UPPER, "Underkropp A" to LOWER, "Overkropp B" to UPPER, "Underkropp B" to LOWER)
        5 -> listOf("Push" to PUSH_DAY, "Pull" to PULL_DAY, "Bein" to LOWER, "Overkropp" to UPPER, "Underkropp" to LOWER)
        else -> listOf("Push A" to PUSH_DAY, "Pull A" to PULL_DAY, "Bein A" to LOWER,
            "Push B" to PUSH_DAY, "Pull B" to PULL_DAY, "Bein B" to LOWER)
    }

    private fun maxLevel(l: Level) = when (l) { Level.BEGINNER -> 1; Level.INTERMEDIATE -> 2; Level.ADVANCED -> 3 }

    private fun repsFor(ex: Exercise, goal: Goal, level: Level): String {
        val baseReps = when (goal) {
            Goal.STRENGTH -> "5-8"
            Goal.HYPERTROPHY -> "8-12"
            Goal.FITNESS -> "12-15"
        }
        val timedText = if (level == Level.ADVANCED) "45-60 sek" else "30-45 sek"
        return when {
            ex.timed -> timedText
            ex.pattern == CORE -> "10-15"
            ex.pattern == CARDIO -> "12-20"
            !ex.isWeighted() && goal != Goal.FITNESS -> "10-20" // kroppsvekt: gå nær utmattelse
            else -> baseReps
        }
    }

    /** Foreslått reps-tekst for en øvelse gitt profilen, brukt når man bytter ut en øvelse i et eksisterende program. */
    fun suggestedReps(ex: Exercise, profile: Profile): String = repsFor(ex, profile.goal, profile.level)

    /**
     * Alternative øvelser med samme bevegelsesmønster som brukeren faktisk kan gjøre,
     * ekskludert øvelsen selv og andre øvelser allerede i samme dag. Nærmeste vanskelighetsgrad først.
     */
    fun alternativesFor(current: Exercise, day: WorkoutDay, profile: Profile): List<Exercise> {
        val available = ExerciseLibrary.available(profile.equipment)
        val usedIds = day.exercises.map { it.exerciseId }.toSet()
        return available
            .filter { it.pattern == current.pattern && it.id != current.id && it.id !in usedIds }
            .sortedBy { kotlin.math.abs(it.level - current.level) }
    }

    fun generate(profile: Profile): Generated {
        val owned = profile.equipment + BODYWEIGHT
        val available = ExerciseLibrary.available(owned)
        val warnings = mutableListOf<String>()
        val usage = mutableMapOf<String, Int>()
        val days = mutableListOf<WorkoutDay>()

        val (baseSets, _, baseRest) = when (profile.goal) {
            Goal.STRENGTH -> Triple(4, "5-8", 120)
            Goal.HYPERTROPHY -> Triple(3, "8-12", 75)
            Goal.FITNESS -> Triple(3, "12-15", 45)
        }
        val setsAdj = when (profile.level) {
            Level.BEGINNER -> if (baseSets > 3) 3 else baseSets
            Level.INTERMEDIATE -> baseSets
            Level.ADVANCED -> if (profile.goal == Goal.FITNESS) baseSets else baseSets + 1
        }
        val budget = (profile.minutesPerSession - 5).coerceAtLeast(10) // 5 min oppvarming

        for ((title, rawSlots) in template(profile.daysPerWeek)) {
            val slots = if (profile.goal == Goal.FITNESS) rawSlots + Slot(CARDIO) else rawSlots
            val usedToday = mutableSetOf<String>()
            val picked = mutableListOf<PlannedExercise>()
            var minutes = 0.0

            for (slot in slots) {
                val ex = pick(slot, available, owned, profile, usedToday, usage) ?: continue
                val sets = when {
                    ex.pattern == CORE || ex.pattern == CARDIO -> 3
                    else -> setsAdj
                }
                val reps = repsFor(ex, profile.goal, profile.level)
                val rest = when {
                    ex.pattern == CORE || ex.pattern == CARDIO -> 45
                    else -> baseRest
                }
                val cost = sets * (if (ex.timed) 45.0 else 40.0) / 60.0 + (sets - 1) * rest / 60.0
                if (picked.size >= 3 && minutes + cost > budget) continue
                minutes += cost
                usedToday += ex.id
                usage[ex.id] = (usage[ex.id] ?: 0) + 1
                picked += PlannedExercise(ex.id, sets, reps, rest, ex.note)
            }
            days += WorkoutDay(title, picked)
        }

        // Mangler vi hele bevegelsesmønstre?
        val covered = days.flatMap { d -> d.exercises.mapNotNull { ExerciseLibrary.get(it.exerciseId)?.pattern } }.toSet()
        if (PULL !in covered) warnings += "Ingen trekkøvelser (rygg) med utstyret ditt. Vurder tverrligger, strikk eller manual."
        if (PUSH !in covered) warnings += "Ingen pressøvelser med utstyret ditt."
        if (SQUAT !in covered && LUNGE !in covered) warnings += "Ingen beinøvelser funnet."
        if (owned.none { it in Equipment.loadBearing } && profile.goal != Goal.FITNESS) {
            warnings += "Uten ekstern vekt blir progresjon via reps, tempo og vanskeligere varianter. Manualer eller kettlebell gir mye mer variasjon."
        }
        if (PULL_UP_BAR !in owned && RESISTANCE_BANDS !in owned && DUMBBELLS !in owned && KETTLEBELL !in owned && BARBELL !in owned && TRX !in owned && TABLE !in owned) {
            warnings += "Rygg er vanskelig å trene uten trekk-utstyr. En tverrligger eller strikker er et billig løft."
        }
        if (profile.customEquipment.isNotBlank()) {
            warnings += "Du har notert eget utstyr (\"${profile.customEquipment}\") som det regelbaserte programmet ikke kjenner til " +
                "og derfor ikke bruker automatisk. Prøv AI-generering hvis du vil at det skal tas hensyn til."
        }

        val name = "${profile.goal.label} - ${days.size} dager i uka"
        return Generated(Plan(name = name, days = days, progression = progressionText(profile, owned)), warnings)
    }

    private fun pick(
        slot: Slot,
        available: List<Exercise>,
        owned: Set<Equipment>,
        profile: Profile,
        usedToday: Set<String>,
        usage: Map<String, Int>,
    ): Exercise? {
        val max = maxLevel(profile.level)
        fun pool(muscle: Muscle?, maxLvl: Int) = available.filter {
            it.pattern == slot.pattern && it.id !in usedToday && it.level <= maxLvl &&
                (muscle == null || muscle in it.muscles)
        }
        var candidates = pool(slot.muscle, max)
        if (candidates.isEmpty()) candidates = pool(slot.muscle, max + 1)
        if (candidates.isEmpty() && slot.muscle != null && slot.pattern != CORE) candidates = pool(null, max)
        if (candidates.isEmpty()) return null
        // Tyngre variant og lastet utstyr foretrekkes, men rotér for variasjon gjennom uka.
        return candidates.maxByOrNull { ex ->
            ex.level * 10 + (if (ex.isWeighted()) 6 else 0) - (usage[ex.id] ?: 0) * 9 - (if (ex.level > max) 15 else 0)
        }
    }

    private fun progressionText(profile: Profile, owned: Set<Equipment>): String {
        val hasWeight = owned.any { it in Equipment.loadBearing }
        val sb = StringBuilder()
        sb.appendLine("Kjør 4 uker, deretter nytt program eller en lettere uke.")
        sb.appendLine("Uke 1: Innkjøring. Stopp 3 reps før utmattelse.")
        sb.appendLine("Uke 2: Samme øvelser, prøv 1-2 flere reps per sett.")
        sb.appendLine("Uke 3: Tregere tempo (3 sekunder ned) eller kortere pauser (-15 sek).")
        sb.appendLine("Uke 4: Gå nær utmattelse (1-2 reps igjen) på siste sett i hver øvelse.")
        sb.appendLine()
        sb.appendLine("Dobbel progresjon: Klarer du øvre repetisjonstall på alle sett, gå til neste steg:")
        if (hasWeight) sb.appendLine("- Øk vekten litt, eller bruk en tyngre variant.")
        sb.appendLine("- Bytt til en unilateral variant (én arm eller ett ben).")
        sb.appendLine("- Legg inn pause i bunnen, eller bruk 3-1-1 tempo.")
        if (RESISTANCE_BANDS in owned) sb.appendLine("- Bruk en tyngre strikk eller doble strikken.")
        if (!hasWeight) sb.appendLine("- Uten vekter er det varianter som gjør øvelsen tyngre, ikke flere reps i det uendelige.")
        return sb.toString().trim()
    }
}
