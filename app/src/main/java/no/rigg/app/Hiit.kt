package no.rigg.app

// Ren logikk for HIIT-økter (testes i HiitTest): formatene og hvilke øvelser som settes sammen til en runde.

/** De tre HIIT-variantene man velger mellom. */
enum class HiitFormat(val spec: HiitSpec, private val en: String, private val no: String) {
    SHORT(HiitSpec(workSec = 30, restSec = 30, rounds = 2, roundRestSec = 60), "Short", "Kort"),
    STANDARD(HiitSpec(workSec = 40, restSec = 20, rounds = 3, roundRestSec = 60), "Standard", "Standard"),
    HARD(HiitSpec(workSec = 45, restSec = 15, rounds = 4, roundRestSec = 60), "Hard", "Hard"),
    ;

    val label: String get() = tr(en, no)

    /** F.eks. "40/20 sek · 3 runder". */
    val detail: String get() = tr(
        "${spec.workSec}/${spec.restSec} s · ${spec.rounds} rounds",
        "${spec.workSec}/${spec.restSec} sek · ${spec.rounds} runder",
    )
}

object Hiit {
    /** Tittelen HIIT-økter logges med. */
    const val TITLE = "HIIT"

    // Stasjonene i en runde, i rekkefølge: puls, bein, press, kjerne, helkropp, trekk/bein. Øvelser som tas én side
    // om gangen er utelatt (det blir rotete på 40 sekunder), og vanskelighetsgraden følger nivået i profilen.
    private val CARDIO = listOf("burpee", "mountain_climber", "high_knees", "jump_rope", "kb_swing_cardio")
    private val LEGS = listOf("jump_squat", "air_squat", "goblet_squat", "kb_swing", "glute_bridge", "wall_sit")
    private val PUSH = listOf("pushup", "pushup_incline", "pike_pushup", "dips_chair", "pushup_diamond")
    private val CORE = listOf("plank", "hollow_hold", "russian_twist_db", "leg_raise", "mountain_climber", "ab_wheel_alt")
    private val FULL = listOf("thruster_db", "burpee", "kb_swing_cardio", "jump_squat", "box_jump", "high_knees", "jump_rope")
    private val PULL = listOf("table_row", "trx_row", "band_row", "superman", "bb_row", "air_squat")
    private val STATIONS = listOf(CARDIO, LEGS, PUSH, CORE, FULL, PULL)

    /**
     * Seks øvelser til en runde, tilpasset utstyret og nivået. [variant] (f.eks. antall loggførte økter, eller
     * "bytt øvelser") velger blant alternativene, så økta varierer. Samme øvelse brukes ikke to ganger.
     */
    fun exercises(profile: Profile, variant: Int): List<Exercise> {
        val maxLevel = when (profile.level) {
            Level.BEGINNER -> 1
            Level.INTERMEDIATE -> 2
            Level.ADVANCED -> 3
        }
        val available = ExerciseLibrary.available(profile.equipment).associateBy { it.id }
        val used = mutableSetOf<String>()
        return STATIONS.mapIndexedNotNull { i, pool ->
            val options = pool.mapNotNull { available[it] }.filter { it.level <= maxLevel && !it.perSide && it.id !in used }
                .ifEmpty { pool.mapNotNull { available[it] }.filter { !it.perSide && it.id !in used } }
            if (options.isEmpty()) return@mapIndexedNotNull null
            // Hver stasjon går ett hakk videre per økt. Stasjonene har ulikt antall valg, så kombinasjonene varierer.
            options[Math.floorMod(variant + i, options.size)].also { used += it.id }
        }
    }

    /** HIIT-økta som en [WorkoutDay]: alle øvelsene på tid, [HiitSpec.rounds] ganger. */
    fun day(format: HiitFormat, exercises: List<Exercise>): WorkoutDay {
        val s = format.spec
        val reps = tr("${s.workSec} sec", "${s.workSec} sek")
        return WorkoutDay(
            title = TITLE,
            exercises = exercises.map { PlannedExercise(it.id, s.rounds, reps, s.restSec) },
            hiit = s,
        )
    }

    /** Omtrentlig lengde i minutter (uten oppvarming). */
    fun minutes(spec: HiitSpec, stations: Int): Int {
        val perRound = stations * (spec.workSec + spec.restSec) - spec.restSec
        val total = spec.rounds * perRound + (spec.rounds - 1) * spec.roundRestSec
        return (total + 59) / 60
    }
}
