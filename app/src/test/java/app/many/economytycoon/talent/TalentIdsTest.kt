package app.many.economytycoon.talent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TalentIdsTest {
    @Test fun eachKindCountsOnItsOwn() {
        val c = TalentCounters()
        assertEquals(InstitutionId(1), c.institution())
        assertEquals(InstitutionId(2), c.institution())
        assertEquals(ProgrammeId(1), c.programme())
        assertEquals(PersonId(1), c.person())
        assertEquals(InstitutionId(3), c.institution())
    }

    @Test fun snapshotRestoreContinuesTheSequence() {
        val c = TalentCounters()
        repeat(4) { c.offer() }
        c.course()
        val d = TalentCounters()
        d.restore(c.snapshot())
        assertEquals(OfferId(5), d.offer())
        assertEquals(CourseId(2), d.course())
        assertEquals(1L, d.peek("ladder"))
    }

    @Test(expected = IllegalArgumentException::class) fun restoreRejectsNonPositiveCounters() {
        TalentCounters().restore(mapOf("offer" to 0L))
    }

    @Test fun idsOrderByValue() {
        assertTrue(PersonId(2) > PersonId(1))
        assertTrue(SkillId("a") < SkillId("b"))
        assertEquals(listOf(ProviderId(1), ProviderId(3)), listOf(ProviderId(3), ProviderId(1)).sorted())
    }

    @Test fun everyKindHasACounterMethod() {
        val c = TalentCounters()
        c.institution(); c.programme(); c.person(); c.offer(); c.covenant(); c.campaign(); c.ladder(); c.position(); c.provider(); c.course()
        assertEquals(TalentCounters.KINDS.sorted(), c.snapshot().keys.toList())
    }
}
