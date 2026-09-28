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

data class WarmupItem(val text: Txt, val seconds: Int) {
    val name: String get() = text.get()
}

/** Kort oppvarming (rundt 4-5 minutter) tilpasset bevegelsene i dagens økt. */
fun warmupFor(day: WorkoutDay, equipment: Set<Equipment>): List<WarmupItem> {
    val patterns = day.exercises.mapNotNull { ExerciseLibrary.get(it.exerciseId)?.pattern }.toSet()
    val items = mutableListOf(WarmupItem(Txt("Jumping jacks or marching on the spot", "Jumping jacks eller marsj på stedet"), 60))
    if (patterns.any { it == SQUAT || it == LUNGE || it == HINGE }) {
        items += WarmupItem(Txt("Slow bodyweight squats", "Rolige knebøy uten vekt"), 45)
        items += WarmupItem(Txt("Hip hinges: hands on hips, push the hips back", "Hoftebøy: hendene på hofta, skyv rumpa bakover"), 30)
    }
    if (LUNGE in patterns) items += WarmupItem(Txt("Leg swings, both sides", "Beinsving, begge sider"), 40)
    if (PUSH in patterns) {
        items += WarmupItem(Txt("Arm circles, forward and back", "Armsirkler forover og bakover"), 30)
        items += WarmupItem(Txt("Easy push-ups (on knees or against a bench)", "Lette armhevinger (på knærne eller mot benk)"), 30)
    }
    if (PULL in patterns) {
        items += if (RESISTANCE_BANDS in equipment) {
            WarmupItem(Txt("Band pull-aparts in front of the chest", "Strikk trukket fra hverandre foran brystet"), 30)
        } else {
            WarmupItem(Txt("Squeeze the shoulder blades together", "Klem skulderbladene sammen"), 30)
        }
    }
    if (HINGE in patterns || CORE in patterns) items += WarmupItem(Txt("Cat-cow on all fours", "Katt og ku på alle fire"), 30)

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
