package no.rigg.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingTest {

    private fun ex(id: String) = ExerciseLibrary.get(id)!!

    @Test
    fun parsesWeightRangesAndLists() {
        assertEquals((1..10).map { it * 2.0 }, parseWeights("2-20/2"))
        assertEquals((1..10).map { it * 2.0 }, parseWeights("2–20 kg i steg på 2"))
        assertEquals(listOf(4.0, 6.0, 8.0, 12.5), parseWeights("4 6 8 12,5"))
        assertEquals(listOf(4.0, 6.0, 8.0), parseWeights("4,6,8"))
        assertEquals(listOf(8.0, 12.0, 16.0), parseWeights("8; 12; 16 kg"))
        assertEquals(listOf(2.5, 5.0, 7.5, 10.0), parseWeights("2.5-10 / 2.5"))
        assertTrue(parseWeights("").isEmpty())
        assertTrue(parseWeights("ingen").isEmpty())
    }

    @Test
    fun stepsBetweenAvailableWeights() {
        val w = listOf(10.0, 12.0, 14.0)
        assertEquals(14.0, nextWeightUp(12.0, w))
        assertNull(nextWeightUp(14.0, w))
        assertEquals(12.0, stepWeight(10.0, w, up = true))
        assertEquals(10.0, stepWeight(12.0, w, up = false))
        assertNull(stepWeight(10.0, w, up = false))
        assertEquals(10.0, stepWeight(null, w, up = true))
        assertEquals(13.0, stepWeight(12.0, emptyList(), up = true))
    }

    @Test
    fun weightOptionsOnlyForOwnedEquipment() {
        val p = Profile(equipment = setOf(Equipment.DUMBBELLS), dumbbellWeights = "2-6/2", kettlebellWeights = "16")
        assertEquals(listOf(2.0, 4.0, 6.0), weightOptions(ex("goblet_squat"), p))
        assertTrue(weightOptions(ex("air_squat"), p).isEmpty())
    }

    @Test
    fun supersetsAreInterleavedWithRestAfterTheSecond() {
        val day = WorkoutDay(
            "Test",
            listOf(
                PlannedExercise("pushup", 2, "8-12", 60, group = "A"),
                PlannedExercise("db_row", 2, "8-12", 60, group = "A"),
                PlannedExercise("plank", 1, "30 sec", 30),
            ),
        )
        val steps = sessionSteps(day)
        assertEquals(listOf("pushup", "db_row", "pushup", "db_row", "plank"), steps.map { it.pe.exerciseId })
        assertEquals(listOf(0, 0, 1, 1, 0), steps.map { it.setIndex })
        assertEquals(listOf(0, 60, 0, 60, 0), steps.map { it.restAfter })
    }

    @Test
    fun normalExercisesRestBetweenSetsButNotAfterTheLast() {
        val day = WorkoutDay("Test", listOf(PlannedExercise("pushup", 3, "8-12", 90), PlannedExercise("air_squat", 2, "10-20", 60)))
        val steps = sessionSteps(day)
        assertEquals(5, steps.size)
        assertEquals(listOf(90, 90, 90, 60, 0), steps.map { it.restAfter })
        assertEquals(listOf(1, 1, 1, 2, 2), steps.map { it.exerciseNo })
    }

    private fun log(id: String, vararg sets: SetResult) =
        SessionLog("2026-09-01", "Test", listOf(ExerciseResult(id, sets.toList())))

    @Test
    fun targetGoesUpInWeightWhenAllSetsHitTheTop() {
        val goblet = ex("goblet_squat")
        val pe = PlannedExercise("goblet_squat", 3, "8-12", 75)
        val logs = listOf(log("goblet_squat", SetResult(12, 12.0), SetResult(12, 12.0), SetResult(12, 12.0)))
        assertEquals(SetTarget(8, 14.0), setTarget(goblet, pe, 0, logs, listOf(10.0, 12.0, 14.0)))
        // Uten kjente vekter: samme vekt, toppen av intervallet.
        assertEquals(SetTarget(12, 12.0), setTarget(goblet, pe, 0, logs, emptyList()))
    }

    @Test
    fun targetMatchesLastTimeOtherwise() {
        val goblet = ex("goblet_squat")
        val pe = PlannedExercise("goblet_squat", 3, "8-12", 75)
        val logs = listOf(log("goblet_squat", SetResult(10, 12.0), SetResult(9, 12.0)))
        assertEquals(SetTarget(10, 12.0), setTarget(goblet, pe, 0, logs, emptyList()))
        assertEquals(SetTarget(9, 12.0), setTarget(goblet, pe, 1, logs, emptyList()))
        // Tredje sett fantes ikke sist: bruker siste sett.
        assertEquals(SetTarget(9, 12.0), setTarget(goblet, pe, 2, logs, emptyList()))
        // Aldri logget: bunnen av intervallet.
        assertEquals(SetTarget(8, null), setTarget(goblet, pe, 0, emptyList(), emptyList()))
    }

    @Test
    fun warmupIsShortAndStartsGeneral() {
        val day = WorkoutDay("Test", listOf(PlannedExercise("goblet_squat", 3, "8-12", 75), PlannedExercise("pushup", 3, "8-12", 75),
            PlannedExercise("db_row", 3, "8-12", 75), PlannedExercise("bulgarian", 3, "8-12", 75), PlannedExercise("plank", 3, "30 sec", 45)))
        val w = warmupFor(day, setOf(Equipment.DUMBBELLS))
        assertTrue(w.sumOf { it.seconds } <= 300)
        assertEquals("Jumping jacks or marching on the spot", w.first().text.en)
        assertTrue(w.size >= 3)
    }

    @Test
    fun generatedProgramsOnlyUseAvailableExercises() {
        val equipmentSets = listOf(
            emptySet(),
            setOf(Equipment.DUMBBELLS),
            setOf(Equipment.DUMBBELLS, Equipment.PULL_UP_BAR, Equipment.BENCH, Equipment.RESISTANCE_BANDS),
            Equipment.selectable.toSet(),
        )
        for (eq in equipmentSets) for (days in 1..6) for (minutes in listOf(20, 30, 45, 90)) for (goal in Goal.values()) {
            val p = Profile(equipment = eq, daysPerWeek = days, minutesPerSession = minutes, goal = goal)
            val plan = ProgramGenerator.generate(p).plan
            val allowed = ExerciseLibrary.available(eq).map { it.id }.toSet()
            assertEquals(days, plan.days.size)
            plan.days.forEach { d ->
                assertTrue("tom dag for $eq/$days/$minutes", d.exercises.isNotEmpty())
                d.exercises.forEach { assertTrue(it.exerciseId in allowed) }
                assertEquals(d.exercises.size, d.exercises.map { it.exerciseId }.toSet().size)
            }
        }
    }

    @Test
    fun shortSessionsArePairedInSupersets() {
        val p = Profile(equipment = setOf(Equipment.DUMBBELLS, Equipment.PULL_UP_BAR), daysPerWeek = 3, minutesPerSession = 25)
        val plan = ProgramGenerator.generate(p).plan
        plan.days.forEach { d ->
            val groups = d.exercises.filter { it.group.isNotEmpty() }.groupBy { it.group }
            assertTrue(groups.isNotEmpty())
            groups.values.forEach { g -> assertEquals(2, g.size) }
            // Parene ligger etter hverandre.
            d.exercises.forEachIndexed { i, pe ->
                if (pe.group.isNotEmpty() && (i == 0 || d.exercises[i - 1].group != pe.group)) {
                    assertEquals(pe.group, d.exercises[i + 1].group)
                }
            }
        }
        val long = ProgramGenerator.generate(p.copy(minutesPerSession = 60)).plan
        assertTrue(long.days.all { d -> d.exercises.all { it.group.isEmpty() } })
    }

    @Test
    fun nextBlockProgressesExercisesThatHitTheTop() {
        val p = Profile(equipment = setOf(Equipment.DUMBBELLS), dumbbellWeights = "2-20/2")
        val plan = Plan(
            name = "Test",
            days = listOf(WorkoutDay("Dag 1", listOf(
                PlannedExercise("goblet_squat", 3, "8-12", 75),
                PlannedExercise("pushup", 3, "10-20", 75),
                PlannedExercise("db_row", 3, "8-12", 75),
            ))),
        )
        val logs = listOf(SessionLog("2026-09-01", "Dag 1", listOf(
            ExerciseResult("goblet_squat", listOf(SetResult(12, 12.0), SetResult(12, 12.0), SetResult(12, 12.0))),
            ExerciseResult("pushup", listOf(SetResult(20), SetResult(20), SetResult(21))),
            ExerciseResult("db_row", listOf(SetResult(10, 14.0), SetResult(9, 14.0), SetResult(8, 14.0))),
        )))
        val g = nextBlock(plan, logs, p)
        val ids = g.plan.days[0].exercises.map { it.exerciseId }
        assertEquals("goblet_squat", ids[0]) // har tyngre manual: beholdes, med ny startvekt
        assertTrue(g.plan.days[0].exercises[0].tip.contains("14"))
        assertTrue(ids[1] != "pushup") // kroppsvekt på toppen: tyngre variant
        assertTrue(ExerciseLibrary.get(ids[1])!!.level > ex("pushup").level)
        assertEquals("db_row", ids[2]) // ikke på toppen ennå: uendret
        assertEquals(2, g.plan.block)
        assertTrue(g.plan.name.endsWith("2"))
        val third = nextBlock(g.plan, logs, p).plan
        assertEquals(3, third.block)
        assertTrue(third.name.endsWith("3"))
        assertTrue(!third.name.contains("2 ·"))
    }

    @Test
    fun weightWindowCentersOnCurrent() {
        val w = listOf(4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0)
        assertEquals(listOf(8.0, 10.0, 12.0, 14.0, 16.0), weightWindow(w, 12.0))
        assertEquals(listOf(4.0, 6.0, 8.0, 10.0, 12.0), weightWindow(w, 4.0))
        assertEquals(listOf(10.0, 12.0, 14.0, 16.0, 18.0), weightWindow(w, 30.0))
        assertEquals(listOf(4.0, 6.0), weightWindow(listOf(6.0, 4.0), null))
    }

    @Test
    fun restShowsEverythingUntilTheNextRest() {
        val day = WorkoutDay(
            "Test",
            listOf(
                PlannedExercise("pushup", 2, "8-12", 60, group = "A"),
                PlannedExercise("db_row", 2, "8-12", 60, group = "A"),
                PlannedExercise("plank", 1, "30 sec", 30),
            ),
        )
        val steps = sessionSteps(day)
        // Etter første sett med pushup er det db_row rett etter, så pause: begge vises.
        assertEquals(listOf("pushup", "db_row"), stepsUntilRest(steps, 2).map { it.pe.exerciseId })
        assertEquals(listOf("plank"), stepsUntilRest(steps, 4).map { it.pe.exerciseId })
    }

    @Test
    fun oneSidedExercisesArePerSide() {
        assertTrue(ex("side_plank").perSide && ex("side_plank").timed)
        assertTrue(ex("db_row_bent").perSide)
        assertTrue(ex("bulgarian").perSide)
        assertTrue(!ex("pushup").perSide)
        assertEquals(null, ex("pushup").perSideHint)
    }

    @Test
    fun warmupVariesBetweenSessions() {
        val day = WorkoutDay("Test", listOf(PlannedExercise("goblet_squat", 3, "8-12", 75), PlannedExercise("pushup", 3, "8-12", 75),
            PlannedExercise("db_row", 3, "8-12", 75), PlannedExercise("plank", 3, "30 sec", 45)))
        val eq = setOf(Equipment.DUMBBELLS)
        val all = (0 until 6).map { v -> warmupFor(day, eq, v) }
        // Samme økt (samme variant) gir samme oppvarming, neste økt en annen.
        assertEquals(all[0].map { it.figure }, warmupFor(day, eq, 0).map { it.figure })
        assertTrue(all[0].map { it.figure } != all[1].map { it.figure })
        assertTrue(all.flatten().map { it.figure }.toSet().size >= 10)
        all.forEach { assertTrue(it.sumOf { w -> w.seconds } <= 300) }
    }
}
