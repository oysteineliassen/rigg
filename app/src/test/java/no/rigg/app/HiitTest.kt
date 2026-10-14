package no.rigg.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HiitTest {
    private val full = Profile(equipment = Equipment.selectable.toSet(), level = Level.ADVANCED)
    private val bodyweight = Profile(equipment = emptySet(), level = Level.BEGINNER)

    @Test
    fun sixDifferentExercisesWithoutPerSide() {
        for (v in 0 until 10) {
            val ex = Hiit.exercises(full, v)
            assertEquals(6, ex.size)
            assertEquals(6, ex.map { it.id }.toSet().size)
            assertTrue(ex.none { it.perSide })
        }
        // Bare kroppsvekt og nybegynner: fortsatt en hel runde, bare med det man kan gjøre.
        val bw = Hiit.exercises(bodyweight, 0)
        assertTrue(bw.size >= 5)
        assertTrue(bw.all { it.requiresAll.isEmpty() && it.requiresAny.isEmpty() })
    }

    @Test
    fun variesBetweenSessions() {
        val combos = (0 until 6).map { v -> Hiit.exercises(full, v).map { it.id } }.toSet()
        assertTrue(combos.size >= 4)
    }

    @Test
    fun stepsAreACircuitWithBreaks() {
        val ex = Hiit.exercises(full, 0)
        val day = Hiit.day(HiitFormat.STANDARD, ex)
        val steps = sessionSteps(day)
        assertEquals(6 * 3, steps.size)
        // Første runde: øvelse 1-6, så runde 2.
        assertEquals(ex.map { it.id }, steps.take(6).map { it.pe.exerciseId })
        assertEquals(listOf(20, 20, 20, 20, 20, 60), steps.take(6).map { it.restAfter })
        assertEquals(1, steps[6].setIndex)
        assertEquals(0, steps.last().restAfter)
        // 3 runder à (6 × 60 - 20) sek + 2 rundepauser à 60 sek = 1140 sek = 19 min.
        assertEquals(19, Hiit.minutes(HiitFormat.STANDARD.spec, 6))
    }
}
