package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TalentStateTest {
    private fun hex(s: TalentState) = s.stateHash().toString()

    @Test fun theEmptyStateHashIsPinned() {
        assertEquals("ab0b49669997741ccc1e37c9edbf559445f40c57608a975e725995f5af53d457", hex(TalentState()))
    }

    @Test fun aSeededScenarioHashIsPinned() {
        val sc = Scenario(21L)
        sc.run(20); sc.populate(); sc.run(180)
        assertEquals("f10729e62c22b80f6d687192ad21821b5b2d0198ffdf477bde9740649fd42e4f", hex(sc.system.state))
    }

    @Test fun hashingIsRepeatable() {
        val sc = Scenario(5L); sc.run(60)
        assertEquals(sc.system.stateHash(), sc.system.stateHash())
    }

    @Test fun everyPartitionChangesTheHash() {
        val base = Scenario(21L).also { it.run(10); it.populate(); it.run(35) }
        val h0 = base.system.stateHash()
        val s = base.system.state
        val changes = listOf<Pair<String, () -> Unit>>(
            "institution" to { s.institutions.values.first().tuition = Money.units(7) },
            "pool" to { s.pools.values.first().addAt(0, 3L) },
            "market" to { s.markets.values.first().premiumPpm += 1L },
            "cohort skills" to { s.cohortSkills.values.first().certified[DefaultTaxonomy.MACHINING] = 5L },
            "programme" to { s.programmes.values.first().restore(ProgrammeStatus.CANCELLED, Money.ZERO, 0L, Tick(1), 0L, 0L, Money.ZERO) },
            "policy" to { s.policies.values.first().restore(1L, 2L, false) },
            "person" to { s.people.values.first().loyaltyPpm += 1L },
            "position" to { s.positions.values.first().searchSpent = Money.units(1) },
            "successor" to { s.successors.values.first().values.first().readinessPpm += 1L },
            "offer" to { s.offers.values.first().oddsPpm += 1L },
            "covenant" to { s.covenants.values.first().restore(3, false) },
            "counter policy" to { s.counterPolicies.values.first().restore(false, 5L) },
            "brand" to { s.brands.values.first().restore(1L, 2L) },
            "campaign" to { s.campaigns.values.first().restore(CampaignStatus.CANCELLED, 0L, 0L) },
            "ladder" to { s.ladders.values.first().restore(false, 9L, Tick(3), emptyList()) },
            "provider" to { s.providers.values.first().restore(1L, 1_000_000L, Accreditation.NONE, Tick(0), 1L, false, Money.ZERO, Money.ZERO, 0L, 0L, 0L) },
            "counter" to { s.counters.person() },
        )
        for ((name, change) in changes) {
            val before = base.system.stateHash()
            change()
            assertNotEquals("changing $name must change the hash", before, base.system.stateHash())
        }
        assertNotEquals(h0, base.system.stateHash())
    }

    @Test fun idsAreNeverReused() {
        val c = TalentCounters()
        val a = c.person(); val b = c.person()
        assertTrue(b.value > a.value)
        val snap = c.snapshot()
        val d = TalentCounters(); d.restore(snap)
        assertEquals(c.person().value, d.person().value)
    }

    @Test fun countersRejectNonsense() {
        val c = TalentCounters()
        try { c.restore(mapOf("person" to 0L)); org.junit.Assert.fail("zero counter accepted") } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("person")) }
    }

    @Test fun clearingEmptiesEverything() {
        val sc = Scenario(21L).also { it.run(10); it.populate(); it.run(5) }
        sc.system.state.clear()
        assertEquals(TalentState().stateHash(), sc.system.state.stateHash())
        assertEquals(1L, sc.system.state.counters.peek("person"))
        assertEquals(CompanyId(1), CompanyId(1))
        assertEquals(RegionId(1), RegionId(1))
    }
}
