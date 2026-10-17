package app.many.economytycoon.ui.present

import app.many.economytycoon.talent.DevelopmentKind
import app.many.economytycoon.talent.TalentCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CareersPresenterTest {
    @Test fun laddersShowThePathAndNextPromotions() {
        val (_, v) = TalentViews.busy()
        val m = CareersPresenter.build(v)
        val l = m.ladders.single()
        assertEquals("Shop to design", l.title)
        assertEquals(2, l.path.split(" > ").size)
        assertEquals(1, l.steps.size)
        assertTrue(l.state.startsWith("Active"))
        assertTrue(l.toggle is TalentCommand.SetLadderEnabled && (l.toggle as TalentCommand.SetLadderEnabled).enabled == false)
        assertTrue(l.remove is TalentCommand.RemoveLadder)
    }

    @Test fun blockedStepsExplainWhy() {
        val (_, v) = TalentViews.busy()
        val steps = CareersPresenter.build(v).ladders.single().steps
        assertTrue(steps.all { it.text.contains(" to ") })
        assertTrue(steps.all { it.tone == Tone.MUTED || it.tone == Tone.GOOD })
    }

    @Test fun positionsShowHolderEffectAndSuccessors() {
        val (_, v) = TalentViews.busy()
        val p = CareersPresenter.build(v).positions.single()
        assertTrue(p.title.startsWith("CEO"))
        assertEquals("Ada, 66", p.holder)
        assertTrue(p.effect.startsWith("Running at"))
        assertEquals(1, p.successors.size)
        assertEquals("Bo", p.successors.single().name)
        assertEquals(DevelopmentKind.MENTORING.label, p.successors.single().development)
    }

    @Test fun developmentCanBeStepped() {
        assertEquals(DevelopmentKind.MENTORING, CareersPresenter.nextDevelopment(DevelopmentKind.NONE))
        assertEquals(DevelopmentKind.EXECUTIVE_COURSE, CareersPresenter.nextDevelopment(DevelopmentKind.STRETCH))
        assertNull(CareersPresenter.nextDevelopment(DevelopmentKind.EXECUTIVE_COURSE))
        val (_, v) = TalentViews.busy()
        val up = CareersPresenter.build(v).positions.single().successors.single().upgrade as TalentCommand.SetDevelopment
        assertEquals(DevelopmentKind.STRETCH, up.development)
    }

    @Test fun missingOrUnreadySuccessorsRaiseAWarning() {
        val (sc, _) = TalentViews.busy()
        val pos = sc.system.succession.positionsOf(TalentViews.company).single()
        val bo = sc.system.succession.successorsOf(pos.id).single()
        sc.system.succession.removeSuccessor(pos.id, bo.person)
        val v = app.many.economytycoon.talent.TalentReader(sc.system).view(TalentViews.company, app.many.economytycoon.core.Tick(sc.day), sc.inputs(), sc.world)
        val m = CareersPresenter.build(v)
        assertEquals("No successor named", m.positions.single().warning)
        assertEquals("Name successors so a departure does not leave a hole.", m.note)
    }

    @Test fun vacantPostsAreFlagged() {
        val (sc, _) = TalentViews.busy()
        val pos = sc.system.succession.positionsOf(TalentViews.company).single()
        sc.system.succession.successorsOf(pos.id).forEach { sc.system.succession.removeSuccessor(pos.id, it.person) }
        sc.system.succession.vacate(pos.id, app.many.economytycoon.core.Tick(sc.day), departed = true)
        val v = app.many.economytycoon.talent.TalentReader(sc.system).view(TalentViews.company, app.many.economytycoon.core.Tick(sc.day), sc.inputs(), sc.world)
        val m = CareersPresenter.build(v)
        assertTrue(m.note.startsWith("A key post is empty"))
        assertEquals(Tone.BAD, m.positions.single().tone)
        assertNull(m.positions.single().warning)
    }

    @Test fun peopleRowsCarryPayAndLoyalty() {
        val (_, v) = TalentViews.busy()
        val m = CareersPresenter.build(v)
        assertEquals(2, m.people.size)
        assertTrue(m.people.all { it.loyalty.endsWith("loyal") && it.detail.contains("a day") })
        assertNotNull(m.people.firstOrNull { it.detail.contains("holds CEO") })
    }

    @Test fun tilesReportCoverage() {
        val (_, v) = TalentViews.busy()
        val m = CareersPresenter.build(v)
        assertEquals(listOf("Ladders", "Key posts", "Succession cover", "Key post effect"), m.tiles.map { it.label })
        assertEquals("1", m.tiles[0].value)
    }

    @Test fun anEmptyCompanyIsToldToPromoteFromWithin() {
        val (_, v) = TalentViews.quiet()
        val m = CareersPresenter.build(v)
        assertTrue(m.ladders.isEmpty() && m.positions.isEmpty())
        assertEquals("Promoting from within keeps people and costs less than hiring in.", m.note)
    }
}
