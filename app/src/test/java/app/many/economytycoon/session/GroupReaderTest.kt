package app.many.economytycoon.session

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.Holder
import app.many.economytycoon.ui.present.PresentSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupReaderTest {
    private val s = PresentSupport.session("Holding Works", 77L)
    private val e = s.economy
    private val me = s.selectedCompany!!

    private fun foundSub(name: String, stake: Long): CompanyId {
        val country = e.companies.company(me)!!.country
        val c = e.companies.found(name, CompanyKind.AI, country, null, Holder("founder", 900L + name.length), Money.units(200_000), e.governmentOf(country), e.tick).getOrThrow()
        e.finance.mint(e.tick, c.owner, Money.units(50_000), "working cash")
        e.companies.groups.setStake(me, c.id, Rate.percent(stake))
        return c.id
    }

    @Test fun aCompanyThatOwnsNothingHasNoGroup() {
        assertNull(s.group())
        assertFalse(GroupReader(e, s.reports).hasGroup(me))
    }

    @Test fun controllingAnotherCompanyFormsAGroup() {
        val sub = foundSub("Branch One", 80)
        val reader = GroupReader(e, s.reports)
        assertEquals(listOf(sub), reader.subsidiariesOf(me))
        val g = s.group()!!
        assertEquals(me, g.members.first().company)
        assertEquals(2, g.size)
        assertEquals(800_000L, g.members.first { it.company == sub }.interestPpm)
    }

    @Test fun anInterestBelowControlIsNotAGroup() {
        foundSub("Minor Stake", 30)
        assertNull(s.group())
    }

    @Test fun groupAssetsAreAtLeastTheRootsOwnAssets() {
        foundSub("Branch One", 100)
        val g = s.group()!!
        val alone = s.reports.company.finance(me)!!.balance.totalAssets
        assertTrue(g.balance.totalAssets > alone)
    }

    @Test fun chainsOfOwnershipMultiplyDown() {
        val a = foundSub("Branch One", 80)
        val country = e.companies.company(me)!!.country
        val b = e.companies.found("Branch Two", CompanyKind.AI, country, null, Holder("founder", 950L), Money.units(200_000), e.governmentOf(country), e.tick).getOrThrow()
        e.companies.groups.setStake(a, b.id, Rate.percent(50))
        val g = s.group()!!
        assertEquals(400_000L, g.members.first { it.company == b.id }.interestPpm)
        assertEquals(3, g.size)
    }

    @Test fun presenterShowsEveryMemberAndBothStatements() {
        foundSub("Branch One", 100)
        val m = app.many.economytycoon.ui.present.GroupPresenter.build(s)!!
        assertTrue(m.title.endsWith("group"))
        assertEquals(2, m.members.size)
        assertTrue(m.members.first().isRoot)
        assertEquals("100%", m.members.last().interest)
        assertEquals(3, m.income.size)
        assertEquals(3, m.balance.size)
        assertEquals(6, m.tiles.size)
        assertNotNull(m.notes.firstOrNull { it == app.many.economytycoon.ui.present.GroupPresenter.METHOD })
    }

    @Test fun presenterIsNullWithoutAGroup() {
        assertNull(app.many.economytycoon.ui.present.GroupPresenter.build(s))
    }
}
