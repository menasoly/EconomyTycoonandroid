package app.many.economytycoon.ui.present

import app.many.economytycoon.content.TechCategory
import app.many.economytycoon.session.TechState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeoplePresenterTest {
    private val session by lazy { PresentSupport.session("Staffing Works") }

    @Test fun aNewCompanyHasNoStaffYet() {
        val m = PeoplePresenter.build(session)!!
        assertEquals(6, m.tiles.size)
        assertEquals("0", m.tiles.first().value)
        assertTrue(m.cohorts.isEmpty())
        assertTrue(m.warnings.isEmpty())
    }

    @Test fun researchStartsWithEveryTechnologyListed() {
        val m = ResearchPresenter.build(session)!!
        assertEquals(session.economy.content.allTechnologies.size, m.tree.size)
        assertEquals(TechCategory.values().size + 1, m.categories.size)
        assertTrue(m.active.isEmpty() && m.queued.isEmpty())
        assertEquals(Tone.WARN, m.tiles[2].tone)
    }

    @Test fun categoryAndLockFiltersShrinkTheTree() {
        val all = ResearchPresenter.build(session)!!.tree.size
        val one = ResearchPresenter.build(session, TechCategory.ENERGY)!!.tree
        assertTrue(one.size < all && one.all { it.category == "Energy" })
        val open = ResearchPresenter.build(session, hideLocked = true)!!.tree
        assertTrue(open.all { it.state != TechState.LOCKED })
    }

    @Test fun treeIsOrderedByTierThenName() {
        val tree = ResearchPresenter.build(session)!!.tree
        assertEquals(tree.sortedWith(compareBy({ it.tier }, { it.name })).map { it.id }, tree.map { it.id })
    }

    @Test fun startingAvailableResearchMovesItToActive() {
        val s = PresentSupport.session("Lab Works", 6L)
        val first = ResearchPresenter.build(s)!!.tree.first { it.canStart }
        assertTrue(first.canQueue)
        assertFalse(first.canCancel)
        val r = s.issueNow(app.many.economytycoon.simulation.Command.StartResearch(s.selectedCompany!!, first.id))
        assertTrue(r.toString(), r.isOk)
        val after = ResearchPresenter.build(s)!!
        assertEquals(first.id, after.active.single().id)
        assertTrue(after.active.single().canCancel)
        assertEquals("Researching", after.active.single().stateLabel)
    }

    @Test fun everyStateHasLabelAndTone() {
        for (st in TechState.values()) {
            assertTrue(ResearchPresenter.stateLabel(st).isNotBlank())
            ResearchPresenter.tone(st)
        }
    }
}
