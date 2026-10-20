package no.rigg.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ShareAndPauseTest {
    private fun english(block: () -> Unit) {
        val old = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try { block() } finally { Locale.setDefault(old) }
    }

    @Test
    fun pausedSessionClockStandsStill() {
        val start = 1_000_000L
        val running = ActiveSession("A", start)
        assertEquals(60, running.elapsedSec(start + 60_000))

        // På pause etter 60 s: står på 60 s uansett hvor lenge pausen varer.
        val paused = running.copy(pausedAtMs = start + 60_000)
        assertTrue(paused.paused)
        assertEquals(60, paused.elapsedSec(start + 600_000))

        // Fortsatt etter 5 min pause: pausen er trukket fra.
        val resumed = paused.copy(pausedAtMs = null, pausedTotalMs = 300_000)
        assertFalse(resumed.paused)
        assertEquals(90, resumed.elapsedSec(start + 60_000 + 300_000 + 30_000))
    }

    @Test
    fun pausedRestKeepsTimeLeft() {
        val rest = RestTimer(90, endsAtElapsed = 100_000, pausedLeftMs = 42_500)
        assertEquals(42_500, rest.remainingMs(now = 500_000))
        assertEquals(43, rest.secondsLeft(now = 500_000))
        assertEquals(10_000, RestTimer(90, endsAtElapsed = 100_000).remainingMs(now = 90_000))
    }

    @Test
    fun setsSummaryIsCompact() = english {
        val same = listOf(SetResult(8, 13.5), SetResult(12, 13.5), SetResult(12, 13.5))
        assertEquals("8 / 12 / 12 reps · 13.5 kg", setsSummary(same, timed = false))
        val mixed = listOf(SetResult(8, 12.5), SetResult(10, 15.0))
        assertEquals("8×12.5 · 10×15 kg", setsSummary(mixed, timed = false))
        assertEquals("10 / 10 reps", setsSummary(listOf(SetResult(10), SetResult(10)), timed = false))
        assertEquals("30 / 45 s", setsSummary(listOf(SetResult(30), SetResult(45)), timed = true))
        assertEquals("", setsSummary(emptyList(), timed = false))
    }

    @Test
    fun volumeCountsWeightedSetsOnly() {
        val log = SessionLog(
            "2026-10-02", "Dag 1",
            listOf(
                ExerciseResult("goblet_squat", listOf(SetResult(10, 20.0), SetResult(8, 20.0))),
                ExerciseResult("pushup", listOf(SetResult(15))),
            ),
        )
        assertEquals(360.0, sessionVolumeKg(log), 1e-9)
        assertEquals("1 240", formatThousands(1240))
        assertEquals("980", formatThousands(980))
    }

    @Test
    fun warmupHasFigures() = english {
        val day = WorkoutDay("Test", listOf(PlannedExercise("goblet_squat", 3, "8-12", 75), PlannedExercise("pushup", 3, "8-12", 75)))
        val w = warmupFor(day, setOf(Equipment.DUMBBELLS))
        assertEquals(IllustrationType.JUMPING_JACK, w.first().figure)
        assertTrue(w.any { it.figure == IllustrationType.SQUAT })
    }
}
