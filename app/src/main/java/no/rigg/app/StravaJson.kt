package no.rigg.app

import java.time.Instant
import java.time.ZoneId

/**
 * Økta som Strava sin JSON-fil for styrketrening (data_type=json). I motsetning til FIT-fila bruker den Stravas
 * egne øvelsesnavn (f.eks. DUMBBELL_ROMANIAN_DEADLIFTS), så øvelser som ikke finnes i Garmins FIT-liste (som
 * rumensk markløft med manualer) kommer riktig inn. Avviser Strava fila, laster Rigg opp FIT-fila i stedet.
 *
 * Format (developers.strava.com/docs/uploads): version "1.0", start_time (ISO 8601 med sone), utc_offset (sekunder),
 * elapsed_time, sets [{exercise_type, repetitions, weight (kg), duration (sek)}], og streams {time, heartrate}.
 */
object StravaJson {
    fun forLog(log: SessionLog, heartRate: List<HrSample> = emptyList(), zone: ZoneId = ZoneId.systemDefault()): String {
        val startMs = StrengthFit.startMsFor(log, zone)
        val elapsed = StrengthFit.elapsedSecFor(log)
        val start = Instant.ofEpochMilli(startMs - startMs % 1000).atZone(zone).toOffsetDateTime()
        val sets = log.results.flatMap { r ->
            val ex = ExerciseLibrary.get(r.exerciseId)
            val type = exerciseType(r.exerciseId, ex)
            r.sets.map { s ->
                buildList {
                    add("\"exercise_type\":${q(type)}")
                    if (ex?.timed == true) add("\"duration\":${s.reps}") else add("\"repetitions\":${s.reps}")
                    s.weightKg?.takeIf { it > 0 }?.let { add("\"weight\":${num(it)}") }
                }.joinToString(",", "{", "}")
            }
        }
        // Puls: sekunder fra start, maks én måling per sekund, bare innenfor økta.
        val hr = heartRate.map { ((it.timeMs - startMs) / 1000).toInt() to it.bpm }
            .filter { (t, bpm) -> t in 0..elapsed && bpm in 1..254 }
            .distinctBy { it.first }
            .sortedBy { it.first }
        return buildString {
            append('{')
            append("\"version\":\"1.0\",")
            append("\"sport_type\":\"WeightTraining\",")
            append("\"start_time\":").append(q(start.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")))).append(',')
            append("\"utc_offset\":").append(start.offset.totalSeconds).append(',')
            append("\"elapsed_time\":").append(elapsed).append(',')
            append("\"sets\":").append(sets.joinToString(",", "[", "]"))
            if (hr.isNotEmpty()) {
                append(",\"streams\":{")
                append("\"time\":").append(hr.joinToString(",", "[", "]") { it.first.toString() }).append(',')
                append("\"heartrate\":").append(hr.joinToString(",", "[", "]") { it.second.toString() })
                append('}')
            }
            append('}')
        }
    }

    /** Stravas navn på øvelsen, eller en generell øvelse i samme kategori. */
    fun exerciseType(id: String, ex: Exercise?): String = TYPES[id] ?: when (ex?.pattern) {
        Pattern.PUSH -> "PUSH_UP_GENERIC"
        Pattern.PULL -> "ROW_GENERIC"
        Pattern.SQUAT -> "SQUAT_GENERIC"
        Pattern.LUNGE -> "LUNGE_GENERIC"
        Pattern.HINGE -> "DEADLIFT_GENERIC"
        Pattern.CORE -> "CORE_GENERIC"
        Pattern.CARDIO, null -> "CARDIO_GENERIC"
    }

    val ids: Set<String> get() = TYPES.keys

    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun num(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    /**
     * Rigg-øvelse -> exercise_type fra Stravas liste (developers.strava.com/docs/uploads). Der Strava ikke har
     * akkurat varianten, er nærmeste med samme utstyr og muskler valgt, ellers kategoriens _GENERIC.
     */
    private val TYPES: Map<String, String> = mapOf(
        // Press
        "pushup" to "PUSH_UP_GENERIC",
        "pushup_incline" to "INCLINE_PUSH_UP",
        "pushup_decline" to "DECLINE_PUSH_UP",
        "pushup_diamond" to "DIAMOND_PUSH_UP",
        "pushup_archer" to "ARCHER_PUSH_UP",
        "pushup_band" to "PUSH_UP_GENERIC",
        "db_bench" to "DUMBBELL_BENCH_PRESS",
        "db_floor_press" to "DUMBBELL_BENCH_PRESS",
        "db_floor_press_1arm" to "SINGLE_ARM_DUMBBELL_FLOOR_PRESS",
        "bb_bench" to "BARBELL_BENCH_PRESS",
        "db_shoulder_press" to "OVERHEAD_DUMBBELL_PRESS",
        "kb_press" to "STANDING_SINGLE_ARM_SHOULDER_PRESS",
        "pike_pushup" to "PIKE_PUSH_UP",
        "band_press" to "BENCH_PRESS_GENERIC",
        "dips_chair" to "BENCH_DIP",
        "db_lateral_raise" to "LATERAL_RAISE_GENERIC",
        "band_lateral_raise" to "CABLE_LATERAL_RAISE",
        "db_triceps_ext" to "OVERHEAD_DUMBBELL_TRICEPS_EXTENSION",
        "band_triceps_pushdown" to "TRICEPS_PRESSDOWN",
        // Trekk
        "pullup" to "PULL_UP_GENERIC",
        "chinup" to "CLOSE_GRIP_CHIN_UP",
        "pullup_band_assist" to "PULL_UP_GENERIC",
        "negative_pullup" to "NEGATIVE_PULL_UP",
        "dead_hang" to "DEAD_HANG",
        "db_row" to "DUMBBELL_ROW",
        "db_row_bent" to "BENT_OVER_DUMBBELL_ROW",
        "kb_row" to "KETTLEBELL_ROW",
        "bb_row" to "BENT_OVER_BARBELL_ROW",
        "band_row" to "SEATED_CABLE_ROW",
        "band_pulldown" to "LAT_PULLDOWN",
        "band_face_pull" to "FACE_PULL",
        "trx_row" to "INVERTED_ROW",
        "table_row" to "INVERTED_ROW",
        "db_curl" to "STANDING_DUMBBELL_BICEPS_CURL",
        "band_curl" to "CABLE_BICEPS_CURL",
        "superman" to "SUPERMAN_FROM_FLOOR",
        "db_rear_fly" to "INCLINE_REVERSE_FLY",
        // Bein
        "air_squat" to "SQUAT_GENERIC",
        "goblet_squat" to "GOBLET_SQUAT",
        "db_front_squat" to "SQUAT_GENERIC",
        "bb_squat" to "BARBELL_BACK_SQUAT",
        "tempo_squat" to "SQUAT_GENERIC",
        "jump_squat" to "BODY_WEIGHT_JUMP_SQUAT",
        "box_jump" to "BOX_JUMP",
        "pistol_box" to "ASSISTED_PISTOL_SQUATS",
        "band_squat" to "SQUAT_GENERIC",
        "wall_sit" to "WALL_SIT",
        "reverse_lunge" to "REVERSE_LUNGE",
        "db_lunge" to "LUNGE_GENERIC",
        "bulgarian" to "REAR_LEG_RAISED_LUNGE",
        "db_bulgarian" to "DUMBBELL_BULGARIAN_SPLIT_SQUATS",
        "step_up" to "STEP_UP",
        "walking_lunge" to "WALKING_LUNGE",
        "lateral_lunge" to "SIDE_LUNGE",
        // Hofte
        "glute_bridge" to "GLUTE_BRIDGE",
        "glute_bridge_1leg" to "SINGLE_LEG_GLUTE_BRIDGE",
        "hip_thrust_db" to "HIP_RAISE_GENERIC",
        "rdl_db" to "DUMBBELL_ROMANIAN_DEADLIFTS",
        "rdl_1leg_db" to "SINGLE_LEG_DUMBBELL_ROMANIAN_DEADLIFTS",
        "kb_swing" to "KETTLEBELL_SWING",
        "kb_deadlift" to "KETTLEBELL_DEADLIFT",
        "bb_deadlift" to "BARBELL_DEADLIFT",
        "band_pullthrough" to "CABLE_RDL",
        "good_morning_band" to "GOOD_MORNING",
        "hamstring_slide" to "SLIDING_LEG_CURL",
        "calf_raise" to "STANDING_CALF_RAISE",
        "calf_raise_db" to "STANDING_CALF_RAISE",
        // Kjerne
        "plank" to "PLANK_GENERIC",
        "side_plank" to "SIDE_PLANK",
        "dead_bug" to "DEADBUG",
        "bird_dog" to "BIRD_DOG",
        "hollow_hold" to "HOLLOW_ROCK",
        "leg_raise" to "LYING_STRAIGHT_LEG_RAISE",
        "hanging_knee_raise" to "HANGING_KNEE_RAISE",
        "hanging_leg_raise" to "HANGING_LEG_RAISE",
        "pallof_press" to "PALLOF_PRESS",
        "russian_twist_db" to "RUSSIAN_TWIST",
        "suitcase_carry" to "SUITCASE_CARRY",
        "ab_wheel_alt" to "AB_WHEEL_ROLLOUT",
        // Kondisjon
        "jump_rope" to "JUMP_ROPE",
        "burpee" to "BURPEE",
        "mountain_climber" to "MOUNTAIN_CLIMBER",
        "high_knees" to "HIGH_KNEES",
        "kb_swing_cardio" to "KETTLEBELL_SWING",
        "thruster_db" to "THRUSTERS",
    )
}
