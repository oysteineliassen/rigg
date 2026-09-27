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

    private fun template(days: Int): List<Pair<String, List<Slot>>> {
        val full = tr("Full body", "Helkropp")
        val upper = tr("Upper body", "Overkropp")
        val lower = tr("Lower body", "Underkropp")
        val legs = tr("Legs", "Bein")
        return when (days.coerceIn(1, 6)) {
            1 -> listOf(full to FULL_A)
            2 -> listOf("$full A" to FULL_A, "$full B" to FULL_B)
            3 -> listOf("$full A" to FULL_A, "$full B" to FULL_B, "$full C" to FULL_C)
            4 -> listOf("$upper A" to UPPER, "$lower A" to LOWER, "$upper B" to UPPER, "$lower B" to LOWER)
            5 -> listOf("Push" to PUSH_DAY, "Pull" to PULL_DAY, legs to LOWER, upper to UPPER, lower to LOWER)
            else -> listOf("Push A" to PUSH_DAY, "Pull A" to PULL_DAY, "$legs A" to LOWER,
                "Push B" to PUSH_DAY, "Pull B" to PULL_DAY, "$legs B" to LOWER)
        }
    }

    private fun maxLevel(l: Level) = when (l) { Level.BEGINNER -> 1; Level.INTERMEDIATE -> 2; Level.ADVANCED -> 3 }

    private fun repsFor(ex: Exercise, goal: Goal, level: Level): String {
        val baseReps = when (goal) {
            Goal.STRENGTH -> "5-8"
            Goal.HYPERTROPHY -> "8-12"
            Goal.FITNESS -> "12-15"
        }
        val timedText = if (level == Level.ADVANCED) tr("45-60 sec", "45-60 sek") else tr("30-45 sec", "30-45 sek")
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
        if (PULL !in covered) warnings += tr(
            "No pulling exercises (back) with your equipment. Consider a pull-up bar, resistance band or dumbbell.",
            "Ingen trekkøvelser (rygg) med utstyret ditt. Vurder tverrligger, strikk eller manual.",
        )
        if (PUSH !in covered) warnings += tr("No pushing exercises with your equipment.", "Ingen pressøvelser med utstyret ditt.")
        if (SQUAT !in covered && LUNGE !in covered) warnings += tr("No leg exercises found.", "Ingen beinøvelser funnet.")
        if (owned.none { it in Equipment.loadBearing } && profile.goal != Goal.FITNESS) {
            warnings += tr(
                "Without external weight, progression comes from reps, tempo and harder variations. Dumbbells or a kettlebell give much more variety.",
                "Uten ekstern vekt blir progresjon via reps, tempo og vanskeligere varianter. Manualer eller kettlebell gir mye mer variasjon.",
            )
        }
        if (PULL_UP_BAR !in owned && RESISTANCE_BANDS !in owned && DUMBBELLS !in owned && KETTLEBELL !in owned && BARBELL !in owned && TRX !in owned && TABLE !in owned) {
            warnings += tr(
                "Back is hard to train without pulling equipment. A pull-up bar or resistance bands are a cheap upgrade.",
                "Rygg er vanskelig å trene uten trekk-utstyr. En tverrligger eller strikker er et billig løft.",
            )
        }
        if (profile.customEquipment.isNotBlank()) {
            warnings += tr(
                "You've listed your own equipment (\"${profile.customEquipment}\"), which the rule-based program doesn't know " +
                    "and therefore won't use automatically. Try AI generation if you want it taken into account.",
                "Du har notert eget utstyr (\"${profile.customEquipment}\") som det regelbaserte programmet ikke kjenner til " +
                    "og derfor ikke bruker automatisk. Prøv AI-generering hvis du vil at det skal tas hensyn til.",
            )
        }

        val name = tr("${profile.goal.label} - ${days.size} days a week", "${profile.goal.label} - ${days.size} dager i uka")
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
        sb.appendLine(tr("Run for 4 weeks, then switch to a new program or take a lighter week.", "Kjør 4 uker, deretter nytt program eller en lettere uke."))
        sb.appendLine(tr("Week 1: Break-in. Stop 3 reps short of failure.", "Uke 1: Innkjøring. Stopp 3 reps før utmattelse."))
        sb.appendLine(tr("Week 2: Same exercises, aim for 1-2 more reps per set.", "Uke 2: Samme øvelser, prøv 1-2 flere reps per sett."))
        sb.appendLine(tr("Week 3: Slower tempo (3 seconds down) or shorter rests (-15 sec).", "Uke 3: Tregere tempo (3 sekunder ned) eller kortere pauser (-15 sek)."))
        sb.appendLine(tr("Week 4: Go close to failure (1-2 reps left) on the last set of each exercise.", "Uke 4: Gå nær utmattelse (1-2 reps igjen) på siste sett i hver øvelse."))
        sb.appendLine()
        sb.appendLine(tr("Double progression: once you hit the top of the rep range on all sets, move to the next step:", "Dobbel progresjon: Klarer du øvre repetisjonstall på alle sett, gå til neste steg:"))
        if (hasWeight) sb.appendLine(tr("- Add a little weight, or use a harder variation.", "- Øk vekten litt, eller bruk en tyngre variant."))
        sb.appendLine(tr("- Switch to a unilateral variation (one arm or one leg).", "- Bytt til en unilateral variant (én arm eller ett ben)."))
        sb.appendLine(tr("- Add a pause at the bottom, or use a 3-1-1 tempo.", "- Legg inn pause i bunnen, eller bruk 3-1-1 tempo."))
        if (RESISTANCE_BANDS in owned) sb.appendLine(tr("- Use a heavier band or double up the band.", "- Bruk en tyngre strikk eller doble strikken."))
        if (!hasWeight) sb.appendLine(tr("- Without weights, harder variations are what make an exercise tougher, not endlessly adding reps.", "- Uten vekter er det varianter som gjør øvelsen tyngre, ikke flere reps i det uendelige."))
        return sb.toString().trim()
    }
}
