package no.rigg.app

import java.io.ByteArrayOutputStream

/**
 * Lager en FIT-fil (samme filformat som Garmin-klokker bruker) for en styrkeøkt: ett "set"-meldinger per sett,
 * med øvelse (kategori + navn fra Garmins FIT-profil), reps og vekt. Strava leser settene fra filen og tegner
 * muskelkartet ut fra øvelsene, så det blir riktig i stedet for det klokka gjetter.
 *
 * Bare det som trengs er med: file_id, event (start/stopp), set, lap, session og activity.
 * Kodene for øvelsene står i [FitExercises] og er slått opp i Garmins FIT SDK (profil 21.x).
 */
object StrengthFit {
    /** Sekunder mellom 1970-01-01 og FIT-epoken 1989-12-31T00:00:00Z. */
    private const val FIT_EPOCH_OFFSET = 631_065_600L

    // FIT-basetyper: (kode, antall byte).
    private const val ENUM = 0x00
    private const val UINT8 = 0x02
    private const val UINT16 = 0x84
    private const val UINT32 = 0x86
    private const val UINT32Z = 0x8C

    private const val NO_U16 = 0xFFFFL

    /** Ett sett i fila. Tidene er sekunder fra øktstart. */
    data class Set(
        val exercise: FitExercise,
        val reps: Int?,
        val weightKg: Double?,
        val startSec: Int,
        val durationSec: Int,
    )

    fun fitTime(unixMs: Long): Long = unixMs / 1000 - FIT_EPOCH_OFFSET

    /** [startMs] er øktstart (veggklokke), [elapsedSec] hele øktas lengde. */
    fun encode(startMs: Long, elapsedSec: Int, sets: List<Set>): ByteArray {
        val start = fitTime(startMs)
        val end = start + elapsedSec
        val totalMs = elapsedSec * 1000L
        val w = MessageWriter()

        w.message(
            local = 0, global = 0, // file_id
            Field(0, ENUM, 4), // type = activity
            Field(1, UINT16, 255), // manufacturer = development
            Field(2, UINT16, 1), // product
            Field(3, UINT32Z, 1), // serial_number
            Field(4, UINT32, start), // time_created
        )
        w.message(
            local = 1, global = 21, // event: timer start
            Field(253, UINT32, start),
            Field(0, ENUM, 0), // event = timer
            Field(1, ENUM, 0), // event_type = start
        )
        sets.forEachIndexed { i, s ->
            val reps = s.reps?.takeIf { it in 0..65534 }?.toLong() ?: NO_U16
            val weight = s.weightKg?.takeIf { it > 0 }?.let { Math.round(it * 16).coerceAtMost(65534) } ?: NO_U16
            w.message(
                local = 2, global = 225, // set
                Field(254, UINT32, start + s.startSec + s.durationSec), // timestamp = slutten av settet
                Field(0, UINT32, s.durationSec * 1000L), // duration (ms)
                Field(3, UINT16, reps), // repetitions
                Field(4, UINT16, weight), // weight (kg × 16)
                Field(5, UINT8, 1), // set_type = active
                Field(6, UINT32, start + s.startSec), // start_time
                Field(7, UINT16, s.exercise.category.toLong()), // category
                Field(8, UINT16, s.exercise.name.toLong()), // category_subtype (øvelsesnavnet)
                Field(9, UINT16, 1), // weight_display_unit = kilogram
                Field(10, UINT16, i.toLong()), // message_index
            )
        }
        w.message(
            local = 3, global = 21, // event: timer stop
            Field(253, UINT32, end),
            Field(0, ENUM, 0),
            Field(1, ENUM, 4), // stop_all
        )
        w.message(
            local = 4, global = 19, // lap
            Field(254, UINT16, 0), // message_index
            Field(253, UINT32, end),
            Field(0, ENUM, 9), // event = lap
            Field(1, ENUM, 1), // event_type = stop
            Field(2, UINT32, start), // start_time
            Field(7, UINT32, totalMs), // total_elapsed_time
            Field(8, UINT32, totalMs), // total_timer_time
            Field(25, ENUM, 10), // sport = training
            Field(39, ENUM, 20), // sub_sport = strength_training
        )
        w.message(
            local = 5, global = 18, // session
            Field(254, UINT16, 0),
            Field(253, UINT32, end),
            Field(0, ENUM, 8), // event = session
            Field(1, ENUM, 1), // event_type = stop
            Field(2, UINT32, start),
            Field(5, ENUM, 10), // sport = training
            Field(6, ENUM, 20), // sub_sport = strength_training
            Field(7, UINT32, totalMs),
            Field(8, UINT32, totalMs),
            Field(25, UINT16, 0), // first_lap_index
            Field(26, UINT16, 1), // num_laps
        )
        val offset = java.util.TimeZone.getDefault().getOffset(startMs) / 1000
        w.message(
            local = 6, global = 34, // activity
            Field(253, UINT32, end),
            Field(0, UINT32, totalMs), // total_timer_time
            Field(1, UINT16, 1), // num_sessions
            Field(2, ENUM, 0), // type = manual
            Field(3, ENUM, 26), // event = activity
            Field(4, ENUM, 1), // event_type = stop
            Field(5, UINT32, end + offset), // local_timestamp
        )
        return w.toFile()
    }

    /**
     * FIT-fil for en loggført økt. Appen vet ikke nøyaktig når hvert sett ble gjort, så settene fordeles jevnt
     * over økta i den rekkefølgen de står i programmet.
     */
    fun forLog(log: SessionLog, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): ByteArray {
        val all = log.results.flatMap { r ->
            val ex = ExerciseLibrary.get(r.exerciseId)
            val fit = ex?.let { FitExercises.forExercise(it) } ?: FitExercises.forId(r.exerciseId) ?: FitExercise(29, 0)
            r.sets.map { s -> Triple(fit, ex?.timed == true, s) }
        }
        val elapsed = elapsedSecFor(log)
        val slot = (elapsed / all.size.coerceAtLeast(1)).coerceAtLeast(2)
        val sets = all.mapIndexed { i, (fit, timed, s) ->
            val work = if (timed) s.reps else s.reps * 4
            Set(
                exercise = fit,
                reps = if (timed) null else s.reps,
                weightKg = s.weightKg,
                startSec = i * slot,
                durationSec = work.coerceIn(10, 120).coerceAtMost(slot - 1),
            )
        }
        return encode(startMsFor(log, zone), elapsed, sets)
    }

    /** Øktas lengde: målt tid hvis den finnes, ellers et anslag på 2,5 minutter per sett (minst 10 minutter). */
    fun elapsedSecFor(log: SessionLog): Int =
        if (log.durationMin > 0) log.durationMin * 60
        else (log.results.sumOf { it.sets.size } * 150).coerceAtLeast(600)

    /** Når økta startet. Eldre logger har bare dato: da regnes den som avsluttet kl. 18. */
    fun startMsFor(log: SessionLog, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long {
        if (log.startedAtMs > 0) return log.startedAtMs
        val date = runCatching { java.time.LocalDate.parse(log.date) }.getOrElse { java.time.LocalDate.now(zone) }
        return date.atTime(18, 0).atZone(zone).toInstant().toEpochMilli() - elapsedSecFor(log) * 1000L
    }

    private class Field(val num: Int, val type: Int, val value: Long) {
        val size: Int get() = when (type) {
            ENUM, UINT8 -> 1
            UINT16 -> 2
            else -> 4
        }
    }

    private class MessageWriter {
        private val data = ByteArrayOutputStream()
        private val defined = HashMap<Int, List<Pair<Int, Int>>>()

        fun message(local: Int, global: Int, vararg fields: Field) {
            val layout = fields.map { it.num to it.type }
            if (defined[local] != layout) {
                // Definisjonsmelding: lokal type -> global melding og feltene (nummer, størrelse, basetype).
                data.write(0x40 or local)
                data.write(0) // reservert
                data.write(0) // little endian
                le(global.toLong(), 2)
                data.write(fields.size)
                fields.forEach { f ->
                    data.write(f.num)
                    data.write(f.size)
                    data.write(f.type)
                }
                defined[local] = layout
            }
            data.write(local)
            fields.forEach { f -> le(f.value, f.size) }
        }

        private fun le(value: Long, bytes: Int) {
            for (i in 0 until bytes) data.write(((value shr (8 * i)) and 0xFF).toInt())
        }

        fun toFile(): ByteArray {
            val body = data.toByteArray()
            val header = ByteArray(14)
            header[0] = 14
            header[1] = 0x20 // protokoll 2.0
            header[2] = (2132 and 0xFF).toByte() // profil 21.32
            header[3] = (2132 shr 8).toByte()
            for (i in 0 until 4) header[4 + i] = ((body.size shr (8 * i)) and 0xFF).toByte()
            ".FIT".toByteArray(Charsets.US_ASCII).copyInto(header, 8)
            val hc = crc(header, 0, 12)
            header[12] = (hc and 0xFF).toByte()
            header[13] = (hc shr 8).toByte()
            val all = header + body
            val fc = crc(all, 0, all.size)
            return all + byteArrayOf((fc and 0xFF).toByte(), (fc shr 8).toByte())
        }
    }

    private val CRC_TABLE = intArrayOf(
        0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
    )

    /** FIT sin CRC-16 (fra FIT SDK-en). */
    fun crc(bytes: ByteArray, from: Int, to: Int): Int {
        var crc = 0
        for (i in from until to) {
            val b = bytes[i].toInt() and 0xFF
            var tmp = CRC_TABLE[crc and 0xF]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor CRC_TABLE[b and 0xF]
            tmp = CRC_TABLE[crc and 0xF]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor CRC_TABLE[(b shr 4) and 0xF]
        }
        return crc
    }
}

/** Øvelse i FIT-format: kategori (exercise_category) og navn innenfor kategorien (f.eks. squat_exercise_name). */
data class FitExercise(val category: Int, val name: Int)

/**
 * Hver øvelse i biblioteket koblet til nærmeste øvelse i Garmins FIT-profil. Strava bruker denne for muskelkartet.
 * Mangler en øvelse her, brukes en generell øvelse etter bevegelsesmønsteret ([forExercise]).
 */
object FitExercises {
    private val MAP: Map<String, FitExercise> = mapOf(
    "pushup" to FitExercise(22, 77), // pushUp / pushUp
    "pushup_incline" to FitExercise(22, 27), // pushUp / inclinePushUp
    "pushup_decline" to FitExercise(22, 13), // pushUp / declinePushUp
    "pushup_diamond" to FitExercise(22, 15), // pushUp / diamondPushUp
    "pushup_archer" to FitExercise(22, 69), // pushUp / wideHandsPushUp
    "pushup_band" to FitExercise(37, 33), // bandedExercises / pushUps
    "db_bench" to FitExercise(0, 6), // benchPress / dumbbellBenchPress
    "db_floor_press" to FitExercise(0, 7), // benchPress / dumbbellFloorPress
    "db_floor_press_1arm" to FitExercise(0, 15), // benchPress / oneArmFloorPress
    "bb_bench" to FitExercise(0, 1), // benchPress / barbellBenchPress
    "db_shoulder_press" to FitExercise(24, 24), // shoulderPress / dumbbellShoulderPress
    "kb_press" to FitExercise(24, 18), // shoulderPress / singleArmDumbbellShoulderPress
    "pike_pushup" to FitExercise(22, 84), // pushUp / pikePushUp
    "band_press" to FitExercise(37, 5), // bandedExercises / chestPress
    "dips_chair" to FitExercise(30, 0), // tricepsExtension / benchDip
    "db_lateral_raise" to FitExercise(14, 34), // lateralRaise / dumbbellLateralRaise
    "band_lateral_raise" to FitExercise(37, 25), // bandedExercises / lateralRaise
    "db_triceps_ext" to FitExercise(30, 15), // tricepsExtension / overheadDumbbellTricepsExtension
    "band_triceps_pushdown" to FitExercise(30, 39), // tricepsExtension / tricepsPressdown
    "pullup" to FitExercise(21, 38), // pullUp / pullUp
    "chinup" to FitExercise(21, 39), // pullUp / chinUp
    "pullup_band_assist" to FitExercise(21, 42), // pullUp / bandAssistedPullUp
    "negative_pullup" to FitExercise(21, 38), // pullUp / pullUp
    "dead_hang" to FitExercise(21, 38), // pullUp / pullUp
    "db_row" to FitExercise(23, 2), // row / dumbbellRow
    "db_row_bent" to FitExercise(23, 47), // row / bentOverRowWithDumbell
    "kb_row" to FitExercise(23, 9), // row / kettlebellRow
    "bb_row" to FitExercise(23, 45), // row / barbellRow
    "band_row" to FitExercise(37, 35), // bandedExercises / row
    "band_pulldown" to FitExercise(37, 26), // bandedExercises / latpull
    "band_face_pull" to FitExercise(37, 13), // bandedExercises / facePull
    "trx_row" to FitExercise(23, 49), // row / trxInvertedRow
    "table_row" to FitExercise(23, 35), // row / invertedRow
    "db_curl" to FitExercise(7, 46), // curl / dumbbellBicepsCurl
    "band_curl" to FitExercise(37, 7), // bandedExercises / curl
    "superman" to FitExercise(13, 29), // hyperextension / supermanFromFloor
    "db_rear_fly" to FitExercise(9, 11), // flye / inclineReverseFlye
    "air_squat" to FitExercise(28, 100), // squat / airSquat
    "goblet_squat" to FitExercise(28, 37), // squat / gobletSquat
    "db_front_squat" to FitExercise(28, 27), // squat / dumbbellFrontSquat
    "bb_squat" to FitExercise(28, 6), // squat / barbellBackSquat
    "tempo_squat" to FitExercise(28, 61), // squat / squat
    "jump_squat" to FitExercise(20, 37), // plyo / jumpSquat
    "box_jump" to FitExercise(20, 33), // plyo / boxJump
    "pistol_box" to FitExercise(28, 57), // squat / singleLegBenchSquat
    "band_squat" to FitExercise(37, 42), // bandedExercises / squat
    "wall_sit" to FitExercise(28, 20), // squat / bodyWeightWallSquat
    "reverse_lunge" to FitExercise(17, 44), // lunge / reverseBoxLunge
    "db_lunge" to FitExercise(17, 21), // lunge / dumbbellLunge
    "bulgarian" to FitExercise(17, 18), // lunge / dumbbellBulgarianSplitSquat
    "db_bulgarian" to FitExercise(17, 18), // lunge / dumbbellBulgarianSplitSquat
    "step_up" to FitExercise(28, 66), // squat / stepUp
    "walking_lunge" to FitExercise(17, 78), // lunge / walkingLunge
    "lateral_lunge" to FitExercise(17, 61), // lunge / sideLunge
    "glute_bridge" to FitExercise(10, 11), // hipRaise / hipRaise
    "glute_bridge_1leg" to FitExercise(10, 30), // hipRaise / singleLegHipRaise
    "hip_thrust_db" to FitExercise(10, 1), // hipRaise / barbellHipThrustWithBench
    "rdl_db" to FitExercise(8, 23), // deadlift / romanianDeadlift
    "rdl_1leg_db" to FitExercise(8, 14), // deadlift / singleLegRomanianDeadliftWithDumbbell
    "kb_swing" to FitExercise(10, 23), // hipRaise / kettlebellSwing
    "kb_deadlift" to FitExercise(8, 20), // deadlift / kettlebellDeadlift
    "bb_deadlift" to FitExercise(8, 0), // deadlift / barbellDeadlift
    "band_pullthrough" to FitExercise(4, 0), // chop / cablePullThrough
    "good_morning_band" to FitExercise(15, 12), // legCurl / bandGoodMorning
    "hamstring_slide" to FitExercise(15, 6), // legCurl / slidingLegCurl
    "calf_raise" to FitExercise(1, 18), // calfRaise / standingCalfRaise
    "calf_raise_db" to FitExercise(1, 20), // calfRaise / standingDumbbellCalfRaise
    "plank" to FitExercise(19, 43), // plank / plank
    "side_plank" to FitExercise(19, 66), // plank / sidePlank
    "dead_bug" to FitExercise(11, 1), // hipStability / deadBug
    "bird_dog" to FitExercise(11, 19), // hipStability / quadrupedWithLegLift
    "hollow_hold" to FitExercise(6, 24), // crunch / hollowRock
    "leg_raise" to FitExercise(16, 8), // legRaise / lyingStraightLegRaise
    "hanging_knee_raise" to FitExercise(16, 0), // legRaise / hangingKneeRaise
    "hanging_leg_raise" to FitExercise(16, 1), // legRaise / hangingLegRaise
    "pallof_press" to FitExercise(5, 6), // core / cableCorePress
    "russian_twist_db" to FitExercise(5, 46), // core / russianTwist
    "suitcase_carry" to FitExercise(3, 1), // carry / farmersWalk
    "ab_wheel_alt" to FitExercise(5, 18), // core / kneelingAbWheel
    "jump_rope" to FitExercise(2, 6), // cardio / jumpRope
    "burpee" to FitExercise(29, 0), // totalBody / burpee
    "mountain_climber" to FitExercise(19, 34), // plank / mountainClimber
    "high_knees" to FitExercise(31, 26), // warmUp / walkingHighKnees
    "kb_swing_cardio" to FitExercise(10, 23), // hipRaise / kettlebellSwing
    "thruster_db" to FitExercise(28, 101), // squat / dumbbellThrusters
    )

    private val FALLBACK = mapOf(
        Pattern.PUSH to FitExercise(22, 77), // pushUp / pushUp
        Pattern.PULL to FitExercise(23, 36), // row / row
        Pattern.SQUAT to FitExercise(28, 61), // squat / squat
        Pattern.LUNGE to FitExercise(17, 32), // lunge / lunge
        Pattern.HINGE to FitExercise(8, 23), // deadlift / romanianDeadlift
        Pattern.CORE to FitExercise(19, 43), // plank / plank
        Pattern.CARDIO to FitExercise(29, 0), // totalBody / burpee
    )

    fun forId(id: String): FitExercise? = MAP[id]

    fun forExercise(ex: Exercise): FitExercise = MAP[ex.id] ?: FALLBACK.getValue(ex.pattern)

    val ids: Set<String> get() = MAP.keys
}
