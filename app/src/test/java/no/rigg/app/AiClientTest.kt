package no.rigg.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AiClientTest {

    private val profile = Profile(equipment = setOf(Equipment.DUMBBELLS))

    @Test
    fun parsesPlanAndDropsExercisesThatCannotBeDone() {
        Locale.setDefault(Locale.ENGLISH)
        val raw = """
            Here you go:
            {
              "name": "Test", "weeks": 4, "progression": "More reps",
              "days": [
                { "title": "Day 1", "exercises": [
                  { "exerciseId": "goblet_squat", "sets": 3, "reps": "8-12", "restSec": 75, "tip": "", "group": "A" },
                  { "exerciseId": "db_row", "sets": 3, "reps": "8-12", "restSec": 75, "tip": "Pull", "group": "A" },
                  { "exerciseId": "pullup", "sets": 3, "reps": "5-8", "restSec": 90, "tip": "" },
                  { "exerciseId": "made_up", "sets": 3, "reps": "5-8", "restSec": 90, "tip": "" }
                ] }
              ]
            }
        """.trimIndent()
        val g = AiClient.parsePlan(raw, profile).getOrThrow()
        val ex = g.plan.days.single().exercises
        assertEquals(listOf("goblet_squat", "db_row"), ex.map { it.exerciseId })
        assertEquals(listOf("A", "A"), ex.map { it.group })
        // Tomt tips erstattes med øvelsens eget notat.
        assertEquals(ExerciseLibrary.get("goblet_squat")!!.note, ex[0].tip)
        assertEquals(2, g.warnings.size)
    }

    @Test
    fun failsWithoutJson() {
        assertTrue(AiClient.parsePlan("no json here", profile).isFailure)
    }

    @Test
    fun promptListsOnlyAvailableExercisesInEnglish() {
        val prompt = AiClient.buildPrompt(profile)
        assertTrue(prompt.contains("goblet_squat | Goblet squat"))
        assertTrue(!prompt.contains("pullup |"))
        assertTrue(prompt.contains("\"group\""))
    }

    @Test
    fun suggestionNamesTheNextWeight() {
        Locale.setDefault(Locale.ENGLISH)
        val ex = ExerciseLibrary.get("goblet_squat")!!
        val pe = PlannedExercise("goblet_squat", 3, "8-12", 75)
        val logs = listOf(SessionLog("2026-09-01", "D", listOf(ExerciseResult("goblet_squat", List(3) { SetResult(12, 12.0) }))))
        val text = ProgressionAdvisor.suggestion(ex, pe, logs, listOf(12.0, 14.0))!!
        assertTrue(text, text.contains("14 kg"))
    }
}
