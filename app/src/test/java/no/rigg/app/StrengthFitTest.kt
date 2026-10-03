package no.rigg.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class StrengthFitTest {
    private val log = SessionLog(
        "2026-10-02", "Dag 1",
        listOf(
            ExerciseResult("goblet_squat", listOf(SetResult(8, 13.5), SetResult(12, 13.5))),
            ExerciseResult("plank", listOf(SetResult(30))),
        ),
        durationMin = 26,
        startedAtMs = 1_791_051_000_000,
    )

    @Test
    fun everyLibraryExerciseHasAFitExercise() {
        val missing = ExerciseLibrary.all.map { it.id }.filter { FitExercises.forId(it) == null }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun fileHasValidHeaderAndCrc() {
        val fit = StrengthFit.forLog(log)
        assertEquals(14, fit[0].toInt())
        assertEquals(".FIT", String(fit, 8, 4, Charsets.US_ASCII))
        val dataSize = (0 until 4).sumOf { (fit[4 + it].toInt() and 0xFF) shl (8 * it) }
        assertEquals(fit.size, 14 + dataSize + 2)
        // FIT-CRC over hele fila inkludert CRC-en skal bli 0 (både header og fil).
        assertEquals(0, StrengthFit.crc(fit, 0, 14))
        assertEquals(0, StrengthFit.crc(fit, 0, fit.size))
    }

    @Test
    fun knownCrc() {
        // "123456789" med FIT sin CRC-16 (samme som CRC-16/ARC).
        assertEquals(0xBB3D, StrengthFit.crc("123456789".toByteArray(), 0, 9))
    }

    @Test
    fun oldLogsWithoutClockGetAnEstimatedTime() {
        val old = log.copy(durationMin = 0, startedAtMs = 0)
        assertEquals(600, StrengthFit.elapsedSecFor(old)) // 3 sett er under minstetiden på 10 min
        val start = StrengthFit.startMsFor(old, ZoneOffset.UTC)
        // Ferdig kl. 18:00 UTC den dagen.
        assertEquals(java.time.Instant.parse("2026-10-02T18:00:00Z").toEpochMilli(), start + 600_000)
        assertTrue(StrengthFit.forLog(old, ZoneOffset.UTC).size > 100)
    }
}
