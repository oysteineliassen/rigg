package no.rigg.app

import no.rigg.app.Equipment.*
import no.rigg.app.Pattern.*
import kotlin.math.roundToLong

// Ren treningslogikk uten Android-avhengigheter, så den kan enhetstestes: vekter, oppvarming, supersett,
// rekkefølgen i øktmodus, mål for hvert sett og neste 4-ukersblokk.

// ---------------------------------------------------------------- VEKTENE MAN HAR

private fun round2(x: Double): Double = (x * 100).roundToLong() / 100.0

/**
 * Tolker vektene man har, skrevet fritt: "2-20/2", "2–20 kg i steg på 2", "4 6 8 12,5", "8; 12; 16".
 * Et intervall uten steg regnes i steg på 2 kg. Komma brukes som desimaltegn bare i ,5 ,25 og ,75
 * (12,5), ellers skiller det vekter (4,6,8).
 */
fun parseWeights(text: String): List<Double> {
    if (text.isBlank()) return emptyList()
    var t = text.lowercase().replace('–', '-').replace('—', '-')
    t = Regex("(\\d),(5|25|75)(?!\\d)").replace(t, "\$1.\$2")
    val out = mutableSetOf<Double>()
    val num = "(\\d+(?:\\.\\d+)?)"
    val stepWords = "(?:/|x|step|steps of|by|i steg på|i steg av|steg)"
    val range = Regex("$num\\s*(?:kg)?\\s*-\\s*$num\\s*(?:kg)?(?:\\s*$stepWords\\s*$num)?")
    t = range.replace(t) { m ->
        val a = m.groupValues[1].toDouble()
        val b = m.groupValues[2].toDouble()
        val step = m.groupValues[3].toDoubleOrNull()?.takeIf { it > 0 } ?: 2.0
        if (b >= a && (b - a) / step <= 200) {
            var w = a
            while (w <= b + 1e-9) {
                out += round2(w)
                w += step
            }
        }
        " "
    }
    Regex(num).findAll(t).forEach { out += it.value.toDouble() }
    return out.filter { it > 0 && it <= 300 }.sorted()
}

/** Vektene som passer til en øvelse, ut fra hva slags vekt den bruker og hva man har. */
fun weightOptions(ex: Exercise, profile: Profile): List<Double> {
    val uses = ex.requiresAll + ex.requiresAny
    val list = mutableListOf<Double>()
    if (DUMBBELLS in uses && DUMBBELLS in profile.equipment) list += parseWeights(profile.dumbbellWeights)
    if (KETTLEBELL in uses && KETTLEBELL in profile.equipment) list += parseWeights(profile.kettlebellWeights)
    return list.distinct().sorted()
}

/** Nærmeste vekt over [current] blant [options], eller null hvis det ikke finnes noen tyngre. */
fun nextWeightUp(current: Double?, options: List<Double>): Double? =
    current?.let { c -> options.firstOrNull { it > c + 1e-9 } }

/** Ett steg opp eller ned: til neste vekt man har, eller 1 kg hvis vektene ikke er oppgitt. */
fun stepWeight(current: Double?, options: List<Double>, up: Boolean): Double? {
    if (options.isEmpty()) {
        val c = current ?: return if (up) 1.0 else null
        val next = c + if (up) 1.0 else -1.0
        return if (next <= 0) null else round2(next)
    }
    if (current == null) return if (up) options.first() else null
    return if (up) options.firstOrNull { it > current + 1e-9 } ?: current
    else options.lastOrNull { it < current - 1e-9 }
}

// ---------------------------------------------------------------- OPPVARMING

/** Én oppvarmingsøvelse. [figure] er strekfiguren som vises mens den pågår, [video] et engelsk videosøk. */
data class WarmupItem(val text: Txt, val seconds: Int, val figure: IllustrationType, val video: String = text.en) {
    val name: String get() = text.get()
}

private fun w(en: String, no: String, seconds: Int, figure: IllustrationType, video: String) =
    WarmupItem(Txt(en, no), seconds, figure, video)

/** [count] øvelser fra [pool], startet på [variant] og videre rundt, så det varierer fra økt til økt. */
private fun <T> pick(pool: List<T>, variant: Int, count: Int = 1): List<T> {
    if (pool.isEmpty()) return emptyList()
    val start = Math.floorMod(variant, pool.size)
    return (0 until minOf(count, pool.size)).map { pool[(start + it) % pool.size] }
}

/**
 * Kort oppvarming (rundt 4-5 minutter) tilpasset bevegelsene i dagens økt. Hver del har flere øvelser å velge
 * mellom; [variant] (f.eks. antall loggførte økter) bestemmer hvilke, så oppvarmingen skifter fra økt til økt,
 * men er den samme gjennom én økt.
 */
fun warmupFor(day: WorkoutDay, equipment: Set<Equipment>, variant: Int = 0): List<WarmupItem> {
    val patterns = day.exercises.mapNotNull { ExerciseLibrary.get(it.exerciseId)?.pattern }.toSet()

    // Puls opp.
    val pulse = listOfNotNull(
        w("Jumping jacks or marching on the spot", "Jumping jacks eller marsj på stedet", 60, IllustrationType.JUMPING_JACK, "jumping jacks"),
        w("High knees, easy pace", "Høye knær i rolig tempo", 45, IllustrationType.CARDIO_RUN, "high knees warm up"),
        if (JUMP_ROPE in equipment) {
            w("Easy skipping with the jump rope", "Rolig hoppetau", 60, IllustrationType.JUMP_ROPE, "jump rope basic bounce")
        } else null,
        w("Slow mountain climbers", "Rolige mountain climbers", 40, IllustrationType.MOUNTAIN_CLIMBER, "slow mountain climbers"),
    )
    val items = pick(pulse, variant).toMutableList()

    if (patterns.any { it == SQUAT || it == LUNGE || it == HINGE }) {
        val legs = listOf(
            w("Slow bodyweight squats", "Rolige knebøy uten vekt", 45, IllustrationType.SQUAT, "bodyweight squat form"),
            w("Hip hinges: hands on hips, push the hips back", "Hoftebøy: hendene på hofta, skyv rumpa bakover", 30,
                IllustrationType.HINGE_WARMUP, "hip hinge drill warm up"),
            w("Glute bridges on the floor", "Rumpeløft på gulvet", 40, IllustrationType.GLUTE_BRIDGE, "glute bridge"),
            w("Side lunges without weight", "Sideutfall uten vekt", 40, IllustrationType.LATERAL_LUNGE, "lateral lunge bodyweight"),
            w("Reverse lunges without weight", "Utfall bakover uten vekt", 40, IllustrationType.LUNGE, "reverse lunge bodyweight"),
        )
        items += pick(legs, variant, 2)
    }
    if (LUNGE in patterns) {
        val lunge = listOf(
            w("Leg swings, both sides", "Beinsving, begge sider", 40, IllustrationType.LEG_SWING, "leg swings warm up"),
            w("Slow walking lunges", "Rolige gående utfall", 40, IllustrationType.WALKING_LUNGE, "walking lunges"),
        )
        items += pick(lunge, variant)
    }
    if (PUSH in patterns) {
        val push = listOf(
            w("Arm circles, forward and back", "Armsirkler forover og bakover", 30, IllustrationType.ARM_CIRCLES, "arm circles warm up"),
            w("Easy push-ups (on knees or against a bench)", "Lette armhevinger (på knærne eller mot benk)", 30,
                IllustrationType.PUSHUP_INCLINE, "incline push up"),
            w("Plank, easy hold", "Planke, rolig hold", 30, IllustrationType.PLANK, "plank form"),
        )
        items += pick(push, variant, 2)
    }
    if (PULL in patterns) {
        val pull = listOfNotNull(
            if (RESISTANCE_BANDS in equipment) {
                w("Band pull-aparts in front of the chest", "Strikk trukket fra hverandre foran brystet", 30,
                    IllustrationType.SCAP_SQUEEZE, "band pull apart")
            } else {
                w("Squeeze the shoulder blades together", "Klem skulderbladene sammen", 30, IllustrationType.SCAP_SQUEEZE,
                    "scapular squeeze exercise")
            },
            w("Slow supermans", "Rolige supermans", 30, IllustrationType.SUPERMAN, "superman exercise"),
            if (PULL_UP_BAR in equipment) {
                w("Hang loosely from the bar", "Heng løst i stanga", 20, IllustrationType.DEAD_HANG, "dead hang")
            } else null,
        )
        items += pick(pull, variant)
    }
    if (HINGE in patterns || CORE in patterns) {
        val core = listOf(
            w("Cat-cow on all fours", "Katt og ku på alle fire", 30, IllustrationType.CAT_COW, "cat cow stretch"),
            w("Bird dog, slow", "Bird dog, rolig", 30, IllustrationType.BIRD_DOG, "bird dog exercise"),
            w("Dead bug, slow", "Dead bug, rolig", 30, IllustrationType.DEAD_BUG, "dead bug exercise"),
        )
        items += pick(core, variant)
    }

    // Hold den kort: kutt fra slutten til den er under ca. 5 minutter.
    while (items.size > 2 && items.sumOf { it.seconds } > 300) items.removeAt(items.lastIndex)
    return items
}

// ---------------------------------------------------------------- SUPERSETT

private fun family(p: Pattern?): String = when (p) {
    PUSH -> "push"
    PULL -> "pull"
    SQUAT, LUNGE, HINGE -> "legs"
    CORE -> "core"
    CARDIO -> "cardio"
    null -> "other"
}

/**
 * Parer øvelser to og to i supersett når tida er knapp: øvelser som ikke sliter på de samme musklene
 * (press + trekk, bein + overkropp, kjerne + hva som helst). Parene legges etter hverandre, og pausen
 * etter paret blir den lengste av de to.
 */
fun pairSupersets(list: List<PlannedExercise>): List<PlannedExercise> {
    val fam = list.map { family(ExerciseLibrary.get(it.exerciseId)?.pattern) }
    val partner = IntArray(list.size) { -1 }
    for (i in list.indices) {
        if (partner[i] != -1) continue
        val j = (i + 1 until list.size).firstOrNull { j -> partner[j] == -1 && fam[j] != fam[i] && fam[i] != "cardio" && fam[j] != "cardio" }
            ?: continue
        partner[i] = j
        partner[j] = i
    }
    val out = mutableListOf<PlannedExercise>()
    var letter = 'A'
    val placed = BooleanArray(list.size)
    for (i in list.indices) {
        if (placed[i]) continue
        val j = partner[i]
        if (j == -1) {
            out += list[i].copy(group = "")
            placed[i] = true
        } else {
            val rest = maxOf(list[i].restSec, list[j].restSec)
            out += list[i].copy(group = letter.toString(), restSec = rest)
            out += list[j].copy(group = letter.toString(), restSec = rest)
            placed[i] = true
            placed[j] = true
            letter++
        }
    }
    return out
}

// ---------------------------------------------------------------- ØKTMODUS: REKKEFØLGE OG MÅL

/** Ett steg i øktmodus: ett sett av én øvelse. [restAfter] = 0 betyr rett videre (første øvelse i et supersett). */
data class WorkStep(
    val pe: PlannedExercise,
    val setIndex: Int,
    val restAfter: Int,
    val exerciseNo: Int,
    val exerciseCount: Int,
)

/**
 * Settene i den rekkefølgen de gjøres. Øvelser i samme supersett (samme [PlannedExercise.group], etter
 * hverandre i lista) tas vekselvis: A1 sett 1, A2 sett 1, pause, A1 sett 2 ...
 */
fun sessionSteps(day: WorkoutDay): List<WorkStep> {
    val exercises = day.exercises
    val steps = mutableListOf<WorkStep>()
    // HIIT: hele lista som én runde, kort pause mellom øvelsene og lengre pause mellom rundene.
    day.hiit?.let { h ->
        for (r in 0 until h.rounds) {
            exercises.forEachIndexed { idx, pe ->
                val rest = if (idx == exercises.lastIndex) h.roundRestSec else h.restSec
                steps += WorkStep(pe, r, rest, idx + 1, exercises.size)
            }
        }
        if (steps.isNotEmpty()) steps[steps.lastIndex] = steps.last().copy(restAfter = 0)
        return steps
    }
    var i = 0
    while (i < exercises.size) {
        val group = exercises[i].group
        var end = i + 1
        if (group.isNotEmpty()) while (end < exercises.size && exercises[end].group == group) end++
        val block = (i until end).toList()
        val rounds = block.maxOf { exercises[it].sets.coerceAtLeast(1) }
        for (r in 0 until rounds) {
            val inRound = block.filter { r < exercises[it].sets.coerceAtLeast(1) }
            inRound.forEachIndexed { k, idx ->
                val last = k == inRound.lastIndex
                steps += WorkStep(exercises[idx], r, if (last) exercises[idx].restSec else 0, idx + 1, exercises.size)
            }
        }
        i = end
    }
    // Ingen pause etter aller siste sett.
    if (steps.isNotEmpty()) steps[steps.lastIndex] = steps.last().copy(restAfter = 0)
    return steps
}

/** Stegene fra [from] til og med det første som har pause etter seg (maks [max]). */
fun stepsUntilRest(steps: List<WorkStep>, from: Int, max: Int = 4): List<WorkStep> {
    val out = mutableListOf<WorkStep>()
    var i = from
    while (i < steps.size && out.size < max) {
        out += steps[i]
        if (steps[i].restAfter > 0) break
        i++
    }
    return out
}

/** Forslag til ett sett: reps (sekunder for tidsøvelser) og vekt. */
data class SetTarget(val reps: Int, val weightKg: Double?)

/**
 * Målet for et sett i øktmodus, ut fra forrige gang øvelsen ble logget (dobbel progresjon):
 * nådde man toppen av intervallet i alle sett, går vekten opp til neste man har og reps ned til bunnen.
 * Ellers: match eller slå forrige gang.
 */
fun setTarget(ex: Exercise, pe: PlannedExercise, setIndex: Int, logs: List<SessionLog>, weights: List<Double>): SetTarget {
    val range = parseRepRange(pe.reps) ?: 10..10
    val last = ProgressionAdvisor.lastResult(logs, ex.id)?.takeIf { it.sets.isNotEmpty() }
    val lastSet = last?.sets?.getOrNull(setIndex) ?: last?.sets?.lastOrNull()
    val lastWeight = last?.sets?.mapNotNull { it.weightKg }?.maxOrNull()
    val allHitTop = last != null && last.sets.all { it.reps >= range.last }
    val matchLast = maxOf(range.first, lastSet?.reps ?: range.first)
    return when {
        last == null -> SetTarget(range.first, null)
        ex.timed -> SetTarget(if (allHitTop) range.last else matchLast, null)
        ex.isWeighted() && allHitTop -> {
            val next = nextWeightUp(lastWeight, weights)
            if (next != null) SetTarget(range.first, next) else SetTarget(range.last, lastWeight)
        }
        allHitTop -> SetTarget(range.last, lastWeight)
        else -> SetTarget(matchLast, lastSet?.weightKg ?: lastWeight)
    }
}

// ---------------------------------------------------------------- NESTE BLOKK

/** En tyngre variant av samme bevegelse, som bruker noen av de samme musklene og kan gjøres med utstyret. */
fun harderVariant(ex: Exercise, excludeIds: Set<String>, profile: Profile): Exercise? =
    ExerciseLibrary.available(profile.equipment)
        .filter {
            it.pattern == ex.pattern && it.id != ex.id && it.id !in excludeIds && it.level > ex.level &&
                it.timed == ex.timed && it.muscles.any { m -> m in ex.muscles }
        }
        .minByOrNull { it.level }

/**
 * Neste 4-ukersblokk ut fra loggene: øvelser der man nådde toppen av repetisjonsintervallet i alle sett
 * sist, får tyngre vekt (hvis man har den) eller en vanskeligere variant. Resten beholdes, så man kan
 * fortsette å bygge på dem. Endringene listes som merknader.
 */
fun nextBlock(plan: Plan, logs: List<SessionLog>, profile: Profile): Generated {
    val notes = mutableListOf<String>()
    val days = plan.days.map { day ->
        val usedIds = day.exercises.map { it.exerciseId }.toMutableSet()
        day.copy(exercises = day.exercises.map { pe ->
            val ex = ExerciseLibrary.get(pe.exerciseId) ?: return@map pe
            val last = ProgressionAdvisor.lastResult(logs, ex.id)?.takeIf { it.sets.isNotEmpty() } ?: return@map pe
            val range = parseRepRange(pe.reps) ?: return@map pe
            if (!last.sets.all { it.reps >= range.last }) return@map pe

            val lastWeight = last.sets.mapNotNull { it.weightKg }.maxOrNull()
            val next = if (ex.isWeighted()) nextWeightUp(lastWeight, weightOptions(ex, profile)) else null
            if (next != null) {
                notes += tr("${ex.name}: up to ${formatWeight(next)} kg", "${ex.name}: opp til ${formatWeight(next)} kg")
                return@map pe.copy(tip = tr("Start at ${formatWeight(next)} kg.", "Start på ${formatWeight(next)} kg.") + " " + ex.note)
            }
            val harder = harderVariant(ex, usedIds, profile)
            if (harder != null) {
                usedIds += harder.id
                notes += tr("${ex.name} → ${harder.name} (harder)", "${ex.name} → ${harder.name} (tyngre)")
                return@map pe.copy(exerciseId = harder.id, reps = ProgramGenerator.suggestedReps(harder, profile), tip = harder.note)
            }
            notes += tr("${ex.name}: slower tempo (3 s down, 1 s pause)", "${ex.name}: tregere tempo (3 sek ned, 1 sek pause)")
            pe.copy(tip = tr("Tempo 3-1-1: 3 s down, 1 s pause, up.", "Tempo 3-1-1: 3 sek ned, 1 sek pause, opp.") + " " + ex.note)
        })
    }
    val block = plan.block + 1
    val baseName = plan.name.replace(Regex("\\s·\\s(Block|Blokk)\\s\\d+$"), "")
    val name = baseName + tr(" · Block $block", " · Blokk $block")
    if (notes.isEmpty()) {
        notes += if (logs.isEmpty()) {
            tr("No logged workouts yet, so the program is unchanged. Log workouts to get progression.",
                "Ingen loggførte økter ennå, så programmet er uendret. Logg øktene for å få progresjon.")
        } else {
            tr("You haven't reached the top of the rep range on any exercise yet, so the exercises are kept. Keep building reps.",
                "Du har ikke nådd toppen av repetisjonsintervallet på noen øvelse ennå, så øvelsene beholdes. Fortsett å bygge reps.")
        }
    } else {
        notes.add(0, tr("Changes from the previous block:", "Endringer fra forrige blokk:"))
    }
    return Generated(plan.copy(name = name, days = days, block = block), notes)
}

/** Opptil [count] vekter rundt [current] til knapperaden i øktmodus, sortert stigende. */
fun weightWindow(options: List<Double>, current: Double?, count: Int = 5): List<Double> {
    val sorted = options.distinct().sorted()
    if (sorted.size <= count) return sorted
    val idx = if (current == null) sorted.size / 2 else sorted.indices.minBy { kotlin.math.abs(sorted[it] - current) }
    val start = (idx - count / 2).coerceIn(0, sorted.size - count)
    return sorted.subList(start, start + count)
}
