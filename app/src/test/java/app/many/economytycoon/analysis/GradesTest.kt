package app.many.economytycoon.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradesTest {
    @Test fun higherIsBetterBandsGradeAtTheirCutOffs() {
        val b = Measure.CURRENT.band
        assertEquals(Grade.STRONG, b.grade(2_000_000))
        assertEquals(Grade.SOUND, b.grade(1_999_999))
        assertEquals(Grade.SOUND, b.grade(1_400_000))
        assertEquals(Grade.WATCH, b.grade(1_000_000))
        assertEquals(Grade.WEAK, b.grade(999_999))
    }

    @Test fun lowerIsBetterBandsMirrorThem() {
        val b = Measure.DEBT_TO_EQUITY.band
        assertEquals(Grade.STRONG, b.grade(0))
        assertEquals(Grade.STRONG, b.grade(500_000))
        assertEquals(Grade.SOUND, b.grade(1_200_000))
        assertEquals(Grade.WATCH, b.grade(2_500_000))
        assertEquals(Grade.WEAK, b.grade(2_500_001))
    }

    @Test fun everyMeasureHasConsistentBands() {
        for (m in Measure.values()) {
            val b = m.band
            if (b.direction == Direction.HIGHER_IS_BETTER) assertTrue(m.name, b.strong > b.sound && b.sound > b.watch)
            else assertTrue(m.name, b.strong < b.sound && b.sound < b.watch)
            assertTrue(m.name, m.hint.isNotBlank() && m.label.isNotBlank())
        }
    }

    @Test fun readingsSkipMissingValues() {
        assertNull(Grader.read(Measure.CASH, null))
        assertEquals(Grade.STRONG, Grader.read(Measure.CASH, 900_000)!!.grade)
    }

    @Test fun overallOfNothingIsNull() {
        assertNull(Grader.overall(emptyList()))
    }

    @Test fun overallFollowsTheAverage() {
        fun r(g: Grade) = Reading(Measure.CURRENT, 0, g)
        assertEquals(Grade.STRONG, Grader.overall(listOf(r(Grade.STRONG), r(Grade.STRONG))))
        assertEquals(Grade.SOUND, Grader.overall(listOf(r(Grade.STRONG), r(Grade.SOUND), r(Grade.SOUND))))
        assertEquals(Grade.WATCH, Grader.overall(listOf(r(Grade.SOUND), r(Grade.WATCH), r(Grade.WATCH))))
        assertEquals(Grade.WEAK, Grader.overall(listOf(r(Grade.WEAK), r(Grade.WEAK), r(Grade.WATCH))))
    }

    @Test fun aSingleWeakReadingAmongStrongOnesDoesNotSinkTheCompany() {
        fun r(g: Grade) = Reading(Measure.CURRENT, 0, g)
        assertEquals(Grade.SOUND, Grader.overall(listOf(r(Grade.STRONG), r(Grade.STRONG), r(Grade.STRONG), r(Grade.STRONG), r(Grade.WEAK))))
    }

    @Test fun weakestListsOnlyProblemsWorstFirst() {
        val rs = listOf(
            Reading(Measure.CASH, 0, Grade.STRONG), Reading(Measure.NET_MARGIN, 0, Grade.WATCH),
            Reading(Measure.CURRENT, 0, Grade.WEAK), Reading(Measure.GROSS_MARGIN, 0, Grade.SOUND),
        )
        val w = Grader.weakest(rs)
        assertEquals(listOf(Measure.CURRENT, Measure.NET_MARGIN), w.map { it.measure })
    }

    @Test fun weakestIsCappedByCount() {
        val rs = Measure.values().map { Reading(it, 0, Grade.WEAK) }
        assertEquals(2, Grader.weakest(rs, 2).size)
    }
}
