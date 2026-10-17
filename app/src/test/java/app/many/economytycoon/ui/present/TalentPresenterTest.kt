package app.many.economytycoon.ui.present

import app.many.economytycoon.talent.ShortageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TalentPresenterTest {
    @Test fun tilesSummariseThePeopleSide() {
        val (_, v) = TalentViews.quiet()
        val m = TalentPresenter.build(v)
        assertEquals(listOf("People", "Open posts", "Skill match", "Shortages", "Key posts covered", "Employer appeal"), m.tiles.map { it.label })
        assertEquals(v.cohorts.sumOf { it.headcount }.toString().let { it.reversed().chunked(3).joinToString(",").reversed() }, m.tiles[0].value)
    }

    @Test fun everyOwnCohortGetsARow() {
        val (_, v) = TalentViews.quiet()
        val m = TalentPresenter.build(v)
        assertEquals(v.cohorts.size, m.cohorts.size)
        assertTrue(m.cohorts.all { it.output.endsWith("of the role's need") && it.pay.endsWith("of market pay") })
    }

    @Test fun shortagesAreLabelledAndColoured() {
        assertEquals("Too few people", TalentPresenter.kindLabel(ShortageKind.QUANTITY))
        assertEquals(Tone.BAD, TalentPresenter.kindTone(ShortageKind.BOTH))
        assertEquals(Tone.GOOD, TalentPresenter.kindTone(ShortageKind.NONE))
        val (_, v) = TalentViews.quiet(90)
        val m = TalentPresenter.build(v)
        assertEquals(v.shortages.size, m.shortages.size)
        assertTrue(m.shortages.all { it.where.startsWith("Region ") && it.detail.contains("over normal") })
    }

    @Test fun tonesFollowThresholds() {
        assertEquals(Tone.BAD, TalentPresenter.outputTone(700_000L))
        assertEquals(Tone.WARN, TalentPresenter.outputTone(900_000L))
        assertEquals(Tone.GOOD, TalentPresenter.outputTone(1_050_000L))
        assertEquals(Tone.BAD, TalentPresenter.payTone(800_000L))
        assertEquals(Tone.WARN, TalentPresenter.payTone(950_000L))
        assertEquals(Tone.GOOD, TalentPresenter.payTone(1_000_000L))
    }

    @Test fun adviceCarriesTones() {
        val (_, v) = TalentViews.quiet(120)
        val m = TalentPresenter.build(v)
        assertEquals(v.advice.size, m.advice.size)
        assertTrue(m.advice.all { it.code.startsWith("talent-") })
    }

    @Test fun migrationRowsDescribeDoorsAndFlows() {
        val (_, v) = TalentViews.busy()
        val m = TalentPresenter.build(v)
        assertTrue(m.migration.isNotEmpty())
        assertTrue(m.migration.all { it.doors.contains("quota") && it.flows.contains("left") })
    }

    @Test fun anEmptyCompanyGetsAGentleNote() {
        val (_, v) = TalentViews.busy(who = app.many.economytycoon.core.CompanyId(99))
        val m = TalentPresenter.build(v)
        assertEquals("You have no staff yet.", m.note)
        assertEquals("0", m.tiles[0].value)
        assertEquals("100.0%", m.tiles[2].value)
    }

    @Test fun wordsHandleSingularAndPlural() {
        assertEquals("1 person", TalentWords.people(1L))
        assertEquals("2 people", TalentWords.people(2L))
        assertEquals("1,200 people", TalentWords.people(1_200L))
        assertEquals("0 people", TalentWords.people(0L))
    }

    @Test fun cohortRowsMentionWeaknessWhenThereIsOne() {
        val (_, v) = TalentViews.quiet()
        val weak = v.cohorts.filter { it.weakestSkill != null }
        for (c in weak) assertNotNull(TalentPresenter.cohort(c).weakness)
        for (c in v.cohorts.filter { it.weakestSkill == null }) assertEquals(null, TalentPresenter.cohort(c).weakness)
        assertFalse(TalentPresenter.build(v).note.isBlank())
    }
}
