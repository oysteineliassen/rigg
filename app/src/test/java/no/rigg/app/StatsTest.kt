package no.rigg.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StatsTest {
    // Mandag 28. september 2026 er uke 40.
    private val today = LocalDate.of(2026, 9, 30) // onsdag
    private fun log(date: String, title: String = "A", min: Int = 30) = SessionLog(date, title, emptyList(), min)

    @Test
    fun countsThisWeekAndWeekdays() {
        val logs = listOf(log("2026-09-27"), log("2026-09-28"), log("2026-09-30"))
        assertEquals(2, logsThisWeek(logs, today).size)
        assertEquals(setOf(1, 3), trainedWeekdays(logs, today))
    }

    @Test
    fun nextDaySkipsDaysDoneThisWeek() {
        val plan = Plan(
            name = "P",
            days = listOf(WorkoutDay("A", emptyList()), WorkoutDay("B", emptyList()), WorkoutDay("C", emptyList())),
        )
        assertEquals("A", nextDay(plan, emptyList(), today)?.title)
        assertEquals("B", nextDay(plan, listOf(log("2026-09-28", "A")), today)?.title)
        assertEquals("C", nextDay(plan, listOf(log("2026-09-28", "B"), log("2026-09-29", "A")), today)?.title)
        val all = listOf(log("2026-09-28", "A"), log("2026-09-29", "B"), log("2026-09-30", "C"))
        assertNull(nextDay(plan, all, today))
        // Forrige uke teller ikke.
        assertEquals("A", nextDay(plan, listOf(log("2026-09-25", "A")), today)?.title)
    }

    @Test
    fun streakCountsWholeWeeks() {
        assertEquals(0, weekStreak(emptyList(), today))
        // Ingen økt denne uka ennå: forrige ukes rekke står.
        assertEquals(2, weekStreak(listOf(log("2026-09-15"), log("2026-09-22")), today))
        assertEquals(3, weekStreak(listOf(log("2026-09-15"), log("2026-09-22"), log("2026-09-29")), today))
        assertEquals(1, weekStreak(listOf(log("2026-09-08"), log("2026-09-29")), today))
    }

    @Test
    fun sessionsPerWeekOldestFirst() {
        val weeks = sessionsPerWeek(listOf(log("2026-09-22"), log("2026-09-23"), log("2026-09-29")), today, weeks = 3)
        assertEquals(listOf(38 to 0, 39 to 2, 40 to 1), weeks)
    }

    @Test
    fun totalsAndDeltas() {
        val logs = listOf(log("2026-08-01", min = 40), log("2026-09-10", min = 35), log("2026-09-29", min = 0))
        assertEquals(2, sessionsLastDays(logs, today))
        assertEquals(75, totalMinutes(logs))
        val d = LocalDate.of(2026, 9, 1)
        assertEquals("+6 kg", progressDelta(listOf(ProgressPoint(d, 8.0, "kg"), ProgressPoint(d, 14.0, "kg"))))
        assertEquals("+5", progressDelta(listOf(ProgressPoint(d, 13.0, "reps"), ProgressPoint(d, 18.0, "reps"))))
        assertEquals("", progressDelta(listOf(ProgressPoint(d, 13.0, "reps"))))
    }
}
