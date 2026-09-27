package no.rigg.app

import java.time.LocalDate

/** Ett punkt i fremgangshistorikken til en øvelse: dato, en verdi og enheten den måles i. */
data class ProgressPoint(val date: LocalDate, val value: Double, val unit: String)

/** Alle øvelse-id-er som er logget med minst ett sett, i den rekkefølgen de sist ble logget. */
fun loggedExerciseIds(logs: List<SessionLog>): List<String> =
    logs.asReversed()
        .flatMap { log -> log.results.filter { it.sets.isNotEmpty() }.map { it.exerciseId } }
        .distinct()

/**
 * Fremgangshistorikk for én øvelse: for vektøvelser brukes tyngste vekt logget den økta,
 * ellers høyeste antall reps (eller sekunder for tidsøvelser).
 */
fun historyFor(logs: List<SessionLog>, exercise: Exercise): List<ProgressPoint> =
    logs.mapNotNull { log ->
        val result = log.results.find { it.exerciseId == exercise.id } ?: return@mapNotNull null
        if (result.sets.isEmpty()) return@mapNotNull null
        val date = runCatching { LocalDate.parse(log.date) }.getOrNull() ?: return@mapNotNull null
        val maxWeight = result.sets.mapNotNull { it.weightKg }.maxOrNull()
        val (value, unit) = when {
            exercise.isWeighted() && maxWeight != null -> maxWeight to "kg"
            exercise.timed -> result.sets.maxOf { it.reps }.toDouble() to tr("sec", "sek")
            else -> result.sets.maxOf { it.reps }.toDouble() to "reps"
        }
        ProgressPoint(date, value, unit)
    }.sortedBy { it.date }

/** Tolker en tekst som "8-12" eller "30-45 sek" til et tallintervall. */
fun parseRepRange(text: String): IntRange? {
    val nums = Regex("\\d+").findAll(text).map { it.value.toInt() }.toList()
    return when (nums.size) {
        0 -> null
        1 -> nums[0]..nums[0]
        else -> nums[0]..nums.last()
    }
}

/** Formaterer en vekt uten unødvendige desimaler, f.eks. 10.0 -> "10", 12.5 -> "12.5". */
fun formatWeight(kg: Double): String =
    if (kg == kg.toLong().toDouble()) kg.toLong().toString() else kg.toString()

/**
 * Gir konkrete forslag til neste økt basert på hva som faktisk ble logget sist,
 * ikke bare en generell plan. Dette er "dobbel progresjon" tilpasset lite utstyr:
 * øk reps til du når toppen av intervallet, deretter vekt/vanskelighetsgrad/tempo.
 */
object ProgressionAdvisor {

    fun lastResult(logs: List<SessionLog>, exerciseId: String): ExerciseResult? =
        logs.asReversed().firstNotNullOfOrNull { log -> log.results.find { it.exerciseId == exerciseId } }

    fun suggestion(exercise: Exercise, planned: PlannedExercise, logs: List<SessionLog>): String? {
        val last = lastResult(logs, exercise.id) ?: return null
        if (last.sets.isEmpty()) return null

        val unit = if (exercise.timed) tr("sec", "sek") else "reps"
        val range = parseRepRange(planned.reps)
        val minAchieved = last.sets.minOf { it.reps }
        val lastWeight = last.sets.mapNotNull { it.weightKg }.maxOrNull()
        val weightText = lastWeight?.let { tr(" with ${formatWeight(it)} kg", " med ${formatWeight(it)} kg") } ?: ""

        val allHitTop = range != null && last.sets.all { it.reps >= range.last }

        return when {
            allHitTop && exercise.isWeighted() && lastWeight != null ->
                tr(
                    "Last time: every set reached ${range!!.last}+ $unit$weightText. Add a little weight next time, or try a harder variation.",
                    "Sist: alle sett nådde ${range!!.last}+ $unit$weightText. Øk vekten litt neste gang, eller prøv en tyngre variant.",
                )
            allHitTop ->
                tr(
                    "Last time: every set reached ${range!!.last}+ $unit$weightText. Try a harder variation, slower tempo or shorter rest.",
                    "Sist: alle sett nådde ${range!!.last}+ $unit$weightText. Prøv en vanskeligere variant, tregere tempo eller kortere pause.",
                )
            else ->
                tr(
                    "Last time: your lowest set was $minAchieved $unit$weightText. Try to match or beat it this time.",
                    "Sist: laveste sett var $minAchieved $unit$weightText. Prøv å matche eller slå det denne gangen.",
                )
        }
    }
}
