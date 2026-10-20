package no.rigg.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class StravaJsonTest {
    private val start = java.time.ZonedDateTime.of(2026, 10, 2, 20, 10, 0, 0, ZoneId.of("Europe/Oslo")).toInstant().toEpochMilli()
    private val log = SessionLog(
        "2026-10-02", "Dag 1",
        listOf(
            ExerciseResult("rdl_db", listOf(SetResult(12, 13.5), SetResult(12, 13.5))),
            ExerciseResult("plank", listOf(SetResult(30))),
            ExerciseResult("pushup", listOf(SetResult(15))),
        ),
        durationMin = 26, startedAtMs = start, endedAtMs = start + 26 * 60_000L,
    )

    @Test
    fun everyLibraryExerciseHasAStravaName() {
        assertEquals(emptyList<String>(), ExerciseLibrary.all.map { it.id }.filter { it !in StravaJson.ids })
    }

    @Test
    fun jsonHasStravaNamesTimeAndHeartRate() {
        val hr = listOf(HrSample(start, 90), HrSample(start + 5_000, 95), HrSample(start - 60_000, 70))
        val json = StravaJson.forLog(log, hr, ZoneId.of("Europe/Oslo"))
        assertTrue(json, json.startsWith("{\"version\":\"1.0\""))
        assertTrue(json, "\"start_time\":\"2026-10-02T20:10:00+02:00\"" in json)
        assertTrue(json, "\"utc_offset\":7200" in json)
        assertTrue(json, "\"elapsed_time\":1560" in json)
        assertTrue(json, "{\"exercise_type\":\"DUMBBELL_ROMANIAN_DEADLIFTS\",\"repetitions\":12,\"weight\":13.5}" in json)
        assertTrue(json, "{\"exercise_type\":\"PLANK_GENERIC\",\"duration\":30}" in json)
        assertTrue(json, "{\"exercise_type\":\"PUSH_UP_GENERIC\",\"repetitions\":15}" in json)
        // Målingen før start er utenfor økta.
        assertTrue(json, "\"streams\":{\"time\":[0,5],\"heartrate\":[90,95]}" in json)
    }
}
