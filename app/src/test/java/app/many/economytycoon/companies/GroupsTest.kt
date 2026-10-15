package app.many.economytycoon.companies

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.finance.Holder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupsTest {
    private fun id(n: Long) = CompanyId(n)

    @Test fun majorityStakeMakesTheHolderTheController() {
        val g = CorporateGroups()
        g.setStake(id(1), id(2), Rate.percent(60)).getOrThrow()
        assertEquals(id(1), g.controllerOf(id(2)))
        assertEquals(Rate.percent(60), g.stake(id(1), id(2)))
    }

    @Test fun minorityStakeDoesNotControl() {
        val g = CorporateGroups()
        g.setStake(id(1), id(2), Rate.percent(30)).getOrThrow()
        assertNull(g.controllerOf(id(2)))
        assertEquals(listOf(id(2) to Rate.percent(30)), g.stakesHeldBy(id(1)))
    }

    @Test fun largestHolderAboveThresholdWins() {
        val g = CorporateGroups()
        g.setStake(id(1), id(3), Rate.percent(55)).getOrThrow()
        g.setStake(id(2), id(3), Rate.percent(45)).getOrThrow()
        assertEquals(id(1), g.controllerOf(id(3)))
        g.setStake(id(1), id(3), Rate.percent(0)).getOrThrow()
        assertNull(g.controllerOf(id(3)))
    }

    @Test fun selfOwnershipAndCyclesAreRejected() {
        val g = CorporateGroups()
        assertEquals(Rules.OWNERSHIP_CYCLE, g.setStake(id(1), id(1), Rate.percent(60)).rejectionOrNull()?.rule)
        g.setStake(id(1), id(2), Rate.percent(70)).getOrThrow()
        g.setStake(id(2), id(3), Rate.percent(70)).getOrThrow()
        assertEquals(Rules.OWNERSHIP_CYCLE, g.setStake(id(3), id(1), Rate.percent(60)).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, g.setStake(id(3), id(4), Rate(1_000_001)).rejectionOrNull()?.rule)
    }

    @Test fun descendantsAndUltimateParentWalkTheChain() {
        val g = CorporateGroups()
        g.setStake(id(1), id(2), Rate.percent(80)).getOrThrow()
        g.setStake(id(2), id(3), Rate.percent(80)).getOrThrow()
        g.setStake(id(1), id(4), Rate.percent(51)).getOrThrow()
        assertEquals(listOf(id(2), id(3), id(4)), g.descendants(id(1)))
        assertEquals(id(1), g.ultimateParent(id(3)))
        assertEquals(listOf(id(1), id(2), id(3), id(4)), g.group(id(1)))
        assertEquals(listOf(id(2), id(4)), g.subsidiaries(id(1)))
    }

    @Test fun interestsMultiplyDownTheChain() {
        val g = CorporateGroups()
        g.setStake(id(1), id(2), Rate.percent(80)).getOrThrow()
        g.setStake(id(2), id(3), Rate.percent(50)).getOrThrow()
        val interests = Consolidation.interests(g, id(1))
        assertEquals(1_000_000L, interests[id(1)])
        assertEquals(800_000L, interests[id(2)])
        assertEquals(400_000L, interests[id(3)])
    }

    @Test fun removingACompanyDropsItsLinks() {
        val g = CorporateGroups()
        g.setStake(id(1), id(2), Rate.percent(80)).getOrThrow()
        g.setStake(id(2), id(3), Rate.percent(80)).getOrThrow()
        g.remove(id(2))
        assertTrue(g.descendants(id(1)).isEmpty())
        assertNull(g.controllerOf(id(3)))
    }

    @Test fun restoreRecomputesControllers() {
        val g = CorporateGroups()
        g.setStake(id(1), id(2), Rate.percent(80)).getOrThrow()
        g.setStake(id(5), id(6), Rate.percent(20)).getOrThrow()
        val copy = CorporateGroups()
        copy.restore(g.entries())
        assertEquals(id(1), copy.controllerOf(id(2)))
        assertNull(copy.controllerOf(id(6)))
        val a = app.many.economytycoon.core.CanonicalHasher().also { g.hashInto(it) }.finish().toHex()
        val b = app.many.economytycoon.core.CanonicalHasher().also { copy.hashInto(it) }.finish().toHex()
        assertEquals(a, b)
    }

    @Test fun rebuildReadsShareRegisters() {
        val r = CompanyRig()
        val parent = r.found("Parent Holdings")
        val child = r.found("Child Works")
        val parentHolder = Holder("owner", parent.owner.id)
        r.finance.equity.transfer(child.owner, "common", child.founder, parentHolder, r.companies.params.initialShares * 6 / 10).getOrThrow()
        val groups = CorporateGroups()
        groups.rebuild(r.finance.equity, r.companies.all())
        assertEquals(parent.id, groups.controllerOf(child.id))
        assertTrue(groups.stake(parent.id, child.id).ppm in 590_000L..610_000L)
    }
}
