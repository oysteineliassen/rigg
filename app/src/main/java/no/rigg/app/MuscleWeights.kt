package no.rigg.app

import no.rigg.app.Muscle.ABS
import no.rigg.app.Muscle.BACK
import no.rigg.app.Muscle.BICEPS
import no.rigg.app.Muscle.CALVES
import no.rigg.app.Muscle.CHEST
import no.rigg.app.Muscle.GLUTES_HAMS
import no.rigg.app.Muscle.QUADS
import no.rigg.app.Muscle.SHOULDERS
import no.rigg.app.Muscle.TRICEPS

/**
 * Hvor mye hver muskelgruppe jobber i øvelsen, til muskelkartet. 1 = hovedmuskelen, ca. 0,5 = tydelig hjelpemuskel,
 * 0,2–0,4 = stabiliserer eller hjelper litt. Mer detaljert enn [Exercise.muscles], som brukes til å velge øvelser.
 */
object MuscleWeights {
    fun forExercise(ex: Exercise): Map<Muscle, Float> = MAP[ex.id] ?: fallback(ex)

    val ids: Set<String> get() = MAP.keys

    /** Egne øvelser uten oppføring: første muskel fullt, resten halvt, helkropp litt på alt. */
    private fun fallback(ex: Exercise): Map<Muscle, Float> {
        val w = mutableMapOf<Muscle, Float>()
        ex.muscles.forEachIndexed { i, m ->
            if (m == Muscle.FULL_BODY) {
                Muscle.values().filter { it != Muscle.FULL_BODY }.forEach { w[it] = maxOf(w[it] ?: 0f, 0.3f) }
            } else {
                w[m] = if (i == 0) 1f else 0.5f
            }
        }
        return w
    }

    private fun w(vararg p: Pair<Muscle, Number>) = p.associate { it.first to it.second.toFloat() }

    private val MAP: Map<String, Map<Muscle, Float>> = mapOf(
        // Press
        "pushup" to w(CHEST to 1, TRICEPS to 0.5, SHOULDERS to 0.4, ABS to 0.25),
        "pushup_incline" to w(CHEST to 1, TRICEPS to 0.5, SHOULDERS to 0.3),
        "pushup_decline" to w(CHEST to 1, SHOULDERS to 0.6, TRICEPS to 0.5, ABS to 0.25),
        "pushup_diamond" to w(TRICEPS to 1, CHEST to 0.6, SHOULDERS to 0.3),
        "pushup_archer" to w(CHEST to 1, TRICEPS to 0.5, SHOULDERS to 0.4, ABS to 0.25),
        "pushup_band" to w(CHEST to 1, TRICEPS to 0.5, SHOULDERS to 0.4, ABS to 0.25),
        "db_bench" to w(CHEST to 1, TRICEPS to 0.5, SHOULDERS to 0.4),
        "db_floor_press" to w(CHEST to 1, TRICEPS to 0.6, SHOULDERS to 0.3),
        "db_floor_press_1arm" to w(CHEST to 1, TRICEPS to 0.6, SHOULDERS to 0.3, ABS to 0.3),
        "bb_bench" to w(CHEST to 1, TRICEPS to 0.5, SHOULDERS to 0.4),
        "db_shoulder_press" to w(SHOULDERS to 1, TRICEPS to 0.5, BACK to 0.2, ABS to 0.2),
        "kb_press" to w(SHOULDERS to 1, TRICEPS to 0.5, ABS to 0.3, BACK to 0.2),
        "pike_pushup" to w(SHOULDERS to 1, TRICEPS to 0.5, CHEST to 0.3),
        "band_press" to w(CHEST to 1, SHOULDERS to 0.5, TRICEPS to 0.4),
        "dips_chair" to w(TRICEPS to 1, CHEST to 0.5, SHOULDERS to 0.3),
        "db_lateral_raise" to w(SHOULDERS to 1, BACK to 0.2),
        "band_lateral_raise" to w(SHOULDERS to 1, BACK to 0.2),
        "db_triceps_ext" to w(TRICEPS to 1),
        "band_triceps_pushdown" to w(TRICEPS to 1),
        // Trekk
        "pullup" to w(BACK to 1, BICEPS to 0.6, SHOULDERS to 0.3, ABS to 0.2),
        "chinup" to w(BACK to 1, BICEPS to 0.7, SHOULDERS to 0.2, ABS to 0.2),
        "pullup_band_assist" to w(BACK to 1, BICEPS to 0.6, SHOULDERS to 0.3),
        "negative_pullup" to w(BACK to 1, BICEPS to 0.6, SHOULDERS to 0.3),
        "dead_hang" to w(BACK to 1, SHOULDERS to 0.4, BICEPS to 0.3, ABS to 0.2),
        "db_row" to w(BACK to 1, BICEPS to 0.5, SHOULDERS to 0.4),
        "db_row_bent" to w(BACK to 1, BICEPS to 0.5, SHOULDERS to 0.4, GLUTES_HAMS to 0.2),
        "kb_row" to w(BACK to 1, BICEPS to 0.5, SHOULDERS to 0.4),
        "bb_row" to w(BACK to 1, BICEPS to 0.5, SHOULDERS to 0.4, GLUTES_HAMS to 0.3),
        "band_row" to w(BACK to 1, BICEPS to 0.5, SHOULDERS to 0.4),
        "band_pulldown" to w(BACK to 1, BICEPS to 0.5, SHOULDERS to 0.2),
        "band_face_pull" to w(SHOULDERS to 1, BACK to 0.6, BICEPS to 0.2),
        "trx_row" to w(BACK to 1, BICEPS to 0.5, SHOULDERS to 0.4, ABS to 0.2),
        "table_row" to w(BACK to 1, BICEPS to 0.5, SHOULDERS to 0.4, ABS to 0.2),
        "db_curl" to w(BICEPS to 1),
        "band_curl" to w(BICEPS to 1),
        "superman" to w(BACK to 1, GLUTES_HAMS to 0.6, SHOULDERS to 0.2),
        "db_rear_fly" to w(SHOULDERS to 1, BACK to 0.6),
        // Knebøy
        "air_squat" to w(QUADS to 1, GLUTES_HAMS to 0.6),
        "goblet_squat" to w(QUADS to 1, GLUTES_HAMS to 0.6, ABS to 0.3, BACK to 0.2),
        "db_front_squat" to w(QUADS to 1, GLUTES_HAMS to 0.6, ABS to 0.3, BACK to 0.2),
        "bb_squat" to w(QUADS to 1, GLUTES_HAMS to 0.7, BACK to 0.3, ABS to 0.3),
        "tempo_squat" to w(QUADS to 1, GLUTES_HAMS to 0.6),
        "jump_squat" to w(QUADS to 1, GLUTES_HAMS to 0.6, CALVES to 0.5),
        "box_jump" to w(QUADS to 1, GLUTES_HAMS to 0.6, CALVES to 0.5),
        "pistol_box" to w(QUADS to 1, GLUTES_HAMS to 0.6, ABS to 0.2),
        "band_squat" to w(QUADS to 1, GLUTES_HAMS to 0.6),
        "wall_sit" to w(QUADS to 1, GLUTES_HAMS to 0.3),
        // Utfall
        "reverse_lunge" to w(QUADS to 1, GLUTES_HAMS to 0.7),
        "db_lunge" to w(QUADS to 1, GLUTES_HAMS to 0.7),
        "bulgarian" to w(QUADS to 1, GLUTES_HAMS to 0.7),
        "db_bulgarian" to w(QUADS to 1, GLUTES_HAMS to 0.7),
        "step_up" to w(QUADS to 1, GLUTES_HAMS to 0.7, CALVES to 0.2),
        "walking_lunge" to w(QUADS to 1, GLUTES_HAMS to 0.7),
        "lateral_lunge" to w(QUADS to 1, GLUTES_HAMS to 0.7),
        // Hofte
        "glute_bridge" to w(GLUTES_HAMS to 1),
        "glute_bridge_1leg" to w(GLUTES_HAMS to 1, ABS to 0.2),
        "hip_thrust_db" to w(GLUTES_HAMS to 1, QUADS to 0.2),
        "rdl_db" to w(GLUTES_HAMS to 1, BACK to 0.5),
        "rdl_1leg_db" to w(GLUTES_HAMS to 1, BACK to 0.4, ABS to 0.2),
        "kb_swing" to w(GLUTES_HAMS to 1, BACK to 0.5, ABS to 0.3, SHOULDERS to 0.2),
        "kb_deadlift" to w(GLUTES_HAMS to 1, BACK to 0.5, QUADS to 0.4),
        "bb_deadlift" to w(GLUTES_HAMS to 1, BACK to 0.7, QUADS to 0.4),
        "band_pullthrough" to w(GLUTES_HAMS to 1, BACK to 0.2),
        "good_morning_band" to w(GLUTES_HAMS to 1, BACK to 0.6),
        "hamstring_slide" to w(GLUTES_HAMS to 1),
        "calf_raise" to w(CALVES to 1),
        "calf_raise_db" to w(CALVES to 1),
        // Kjerne
        "plank" to w(ABS to 1, SHOULDERS to 0.2),
        "side_plank" to w(ABS to 1, SHOULDERS to 0.2),
        "dead_bug" to w(ABS to 1),
        "bird_dog" to w(ABS to 1, BACK to 0.5, GLUTES_HAMS to 0.3),
        "hollow_hold" to w(ABS to 1),
        "leg_raise" to w(ABS to 1),
        "hanging_knee_raise" to w(ABS to 1, BACK to 0.2),
        "hanging_leg_raise" to w(ABS to 1, BACK to 0.2),
        "pallof_press" to w(ABS to 1, SHOULDERS to 0.2),
        "russian_twist_db" to w(ABS to 1),
        "suitcase_carry" to w(ABS to 1, BACK to 0.5, SHOULDERS to 0.3),
        "ab_wheel_alt" to w(ABS to 1, SHOULDERS to 0.3),
        // Kondisjon
        "jump_rope" to w(CALVES to 1, QUADS to 0.3, SHOULDERS to 0.2),
        "burpee" to w(QUADS to 0.6, CHEST to 0.5, GLUTES_HAMS to 0.4, TRICEPS to 0.3, SHOULDERS to 0.3, ABS to 0.3),
        "mountain_climber" to w(ABS to 1, SHOULDERS to 0.4, QUADS to 0.3),
        "high_knees" to w(QUADS to 0.6, CALVES to 0.5, ABS to 0.3, GLUTES_HAMS to 0.3),
        "kb_swing_cardio" to w(GLUTES_HAMS to 1, BACK to 0.5, ABS to 0.3, SHOULDERS to 0.2),
        "thruster_db" to w(QUADS to 1, SHOULDERS to 0.8, GLUTES_HAMS to 0.5, TRICEPS to 0.4, ABS to 0.2),
    )
}
