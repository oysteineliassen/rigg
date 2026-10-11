package no.rigg.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

// Tall og utvalg til forsiden og Fremgang-fanen. Ren logikk uten Android, så den kan testes.

fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun SessionLog.localDate(): LocalDate? = runCatching { LocalDate.parse(date) }.getOrNull()

/** Loggene fra og med mandag denne uka. */
fun logsThisWeek(logs: List<SessionLog>, today: LocalDate): List<SessionLog> {
    val start = weekStart(today)
    return logs.filter { l -> l.localDate()?.let { !it.isBefore(start) && !it.isAfter(today) } == true }
}

/** Ukedagene (1 = mandag ... 7 = søndag) det er trent denne uka. */
fun trainedWeekdays(logs: List<SessionLog>, today: LocalDate): Set<Int> =
    logsThisWeek(logs, today).mapNotNull { it.localDate()?.dayOfWeek?.value }.toSet()

/** Neste økt: første dag i programmet som ikke er gjort denne uka. Null når hele uka er gjort. */
fun nextDay(plan: Plan, logs: List<SessionLog>, today: LocalDate): WorkoutDay? {
    val done = logsThisWeek(logs, today).map { it.dayTitle }.toMutableList()
    return plan.days.firstOrNull { day ->
        // Samme dag kan stå to ganger i loggen; hver logg "bruker opp" én forekomst.
        val i = done.indexOf(day.title)
        if (i >= 0) { done.removeAt(i); false } else true
    }
}

/**
 * Forslag til en ekstra økt når ukas økter er gjort: dagen i programmet det er lengst siden du trente
 * (aldri trent først), så musklene som har hvilt mest får jobbe.
 */
fun extraDay(plan: Plan, logs: List<SessionLog>): WorkoutDay? = plan.days.minByOrNull { day ->
    logs.withIndex().lastOrNull { it.value.dayTitle == day.title }?.index ?: -1
}

/** Omtrentlig varighet på en økt i minutter (arbeid + pauser, uten oppvarming). */
fun estimateMinutes(day: WorkoutDay): Int {
    val seconds = sessionSteps(day).sumOf { step ->
        val ex = ExerciseLibrary.get(step.pe.exerciseId)
        val work = when {
            day.hiit != null -> day.hiit.workSec
            ex?.timed == true -> parseRepRange(step.pe.reps)?.last ?: 40
            else -> 40
        }
        work + step.restAfter
    }
    return ((seconds + 59) / 60).coerceAtLeast(1)
}

/** Antall uker på rad med minst én økt. Denne uka teller bare hvis det er trent i den. */
fun weekStreak(logs: List<SessionLog>, today: LocalDate): Int {
    val weeks = logs.mapNotNull { it.localDate()?.let(::weekStart) }.toSet()
    var week = weekStart(today)
    if (week !in weeks) week = week.minusWeeks(1)
    var n = 0
    while (week in weeks) {
        n++
        week = week.minusWeeks(1)
    }
    return n
}

/** Økter per uke de siste [weeks] ukene, eldste først: (ukenummer, antall). */
fun sessionsPerWeek(logs: List<SessionLog>, today: LocalDate, weeks: Int = 8): List<Pair<Int, Int>> {
    val thisWeek = weekStart(today)
    val counts = logs.mapNotNull { it.localDate()?.let(::weekStart) }.groupingBy { it }.eachCount()
    return (weeks - 1 downTo 0).map { back ->
        val w = thisWeek.minusWeeks(back.toLong())
        w.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) to (counts[w] ?: 0)
    }
}

fun sessionsLastDays(logs: List<SessionLog>, today: LocalDate, days: Long = 30): Int =
    logs.count { l -> l.localDate()?.let { ChronoUnit.DAYS.between(it, today) in 0 until days } == true }

fun totalMinutes(logs: List<SessionLog>): Int = logs.sumOf { it.durationMin }

/** Kort beskjed til forsiden om hva som skal økes neste gang, for første øvelse i dagen der det gjelder. */
data class Nudge(val exercise: Exercise, val title: String, val detail: String)

fun nudgeFor(day: WorkoutDay, logs: List<SessionLog>, profile: Profile): Nudge? {
    for (pe in day.exercises) {
        val ex = ExerciseLibrary.get(pe.exerciseId) ?: continue
        val last = ProgressionAdvisor.lastResult(logs, ex.id)?.takeIf { it.sets.isNotEmpty() } ?: continue
        val range = parseRepRange(pe.reps) ?: continue
        if (!last.sets.all { it.reps >= range.last }) continue
        val unit = if (ex.timed) tr("s", "sek") else "reps"
        val lastWeight = last.sets.mapNotNull { it.weightKg }.maxOrNull()
        if (ex.isWeighted() && lastWeight != null) {
            val next = nextWeightUp(lastWeight, weightOptions(ex, profile))
            return Nudge(
                ex,
                if (next != null) tr("${ex.name}: ${formatWeight(next)} kg next time", "${ex.name}: ${formatWeight(next)} kg neste gang")
                else tr("${ex.name}: time for more weight", "${ex.name}: tid for mer vekt"),
                tr("Every set reached ${range.last} $unit with ${formatWeight(lastWeight)} kg last time",
                    "Alle sett nådde ${range.last} $unit med ${formatWeight(lastWeight)} kg sist"),
            )
        }
        return Nudge(
            ex,
            tr("${ex.name}: ready for a harder variation", "${ex.name}: klar for en tyngre variant"),
            tr("Every set reached ${range.last} $unit last time", "Alle sett nådde ${range.last} $unit sist"),
        )
    }
    return null
}

/** Endringen fra første til siste logg, som "+6 kg", "+5" eller "±0". */
fun progressDelta(history: List<ProgressPoint>): String {
    if (history.size < 2) return ""
    val d = history.last().value - history.first().value
    val unit = if (history.last().unit == "kg") " kg" else ""
    return when {
        d > 0.001 -> "+${formatWeight(d)}$unit"
        d < -0.001 -> "−${formatWeight(-d)}$unit"
        else -> "±0"
    }
}

/** Beste sett fra sist øvelsen ble logget, som "12 × 14 kg", "18 reps" eller "45 sek". */
fun lastSetSummary(logs: List<SessionLog>, exercise: Exercise): String? {
    val sets = ProgressionAdvisor.lastResult(logs, exercise.id)?.sets?.takeIf { it.isNotEmpty() } ?: return null
    val best = sets.maxWith(compareBy<SetResult>({ it.weightKg ?: 0.0 }, { it.reps }))
    return when {
        best.weightKg != null && best.weightKg > 0 -> "${best.reps} × ${formatWeight(best.weightKg)} kg"
        exercise.timed -> "${best.reps} ${tr("sec", "sek")}"
        else -> "${best.reps} reps"
    }
}

