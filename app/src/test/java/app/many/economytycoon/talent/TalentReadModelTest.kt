package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TalentReadModelTest {
    private val c1 = CompanyId(1)
    private val c2 = CompanyId(2)

    private fun view(sc: Scenario, who: CompanyId = c1, names: TalentNames = TalentNames.Plain) =
        TalentReader(sc.system, names).view(who, Tick(sc.day), sc.inputs(), sc.world)

    private fun busy(): Scenario = Scenario(9L).also { it.run(20); it.populate(); it.run(40) }

    @Test fun cohortsAreSummarisedWithTheirSkills() {
        val sc = Scenario(); sc.run(40)
        val v = view(sc)
        assertEquals(sc.workforce.rows.filterKeys { it.company == c1 }.size, v.cohorts.size)
        val m = v.cohorts.first { it.key == sc.machinists1 }
        assertEquals("Machinist", m.role.replaceFirstChar { it.uppercase() }.let { if (it.equals(m.role, true)) m.role else it })
        assertTrue(m.multiplierPpm > 0L && m.meanLevelPpm > 0L)
        assertEquals(sc.workforce.rows.getValue(sc.machinists1).heads, m.headcount)
    }

    @Test fun otherCompaniesAreNotMixedIn() {
        val sc = busy()
        val v1 = view(sc, c1); val v2 = view(sc, c2)
        assertTrue(v1.cohorts.all { it.key.company == c1 })
        assertTrue(v2.cohorts.all { it.key.company == c2 })
        assertTrue(v1.programmes.all { sc.system.training.get(it.id)?.company == c1 })
        assertTrue(v1.huntingOffers.all { it.hunter == c1 } && v1.offersAgainstUs.all { it.victim == c1 })
        assertTrue(v2.offersAgainstUs.isNotEmpty())
    }

    @Test fun ownershipIsMarkedForInstitutionsAndProviders() {
        val sc = busy()
        val v = view(sc)
        assertTrue(v.institutions.any { it.mine && it.name == "Tech College" })
        assertTrue(v.institutions.any { it.publicOwned && !it.mine })
        assertTrue(v.providers.any { it.mine && it.name == "Night School" })
        assertTrue(view(sc, c2).providers.none { it.mine })
    }

    @Test fun positionsShowSuccessors() {
        val sc = busy()
        val v = view(sc)
        val ceo = v.positions.single()
        assertEquals("CEO", ceo.title)
        assertFalse(ceo.vacant)
        assertEquals("Ada", ceo.holder)
        assertEquals(listOf("Bo"), ceo.successors.map { it.name })
        assertEquals(2, v.people.size)
        assertTrue(v.coveragePpm in 0L..Ppm.ONE)
    }

    @Test fun policiesCampaignsAndLaddersAreListed() {
        val sc = busy()
        val v2 = view(sc, c2)
        assertTrue(v2.counterEnabled && v2.counterMaxMatchPpm == 200_000L)
        assertEquals(1, v2.covenants.size)
        assertTrue(v2.retention.isNotEmpty())
        val v = view(sc)
        assertEquals(1, v.campaigns.size)
        assertEquals(1, v.ladders.size)
        assertEquals(2, v.ladders.single().rungs.size)
        assertEquals(1, v.ladders.single().steps.size)
    }

    @Test fun namesAreResolvedThroughTheSuppliedNames() {
        val sc = busy()
        val names = object : TalentNames {
            override fun region(id: RegionId) = "R${id.value}"
            override fun company(id: CompanyId) = "Co${id.value}"
            override fun country(id: CountryId) = "Land${id.value}"
        }
        val v = view(sc, names = names)
        assertTrue(v.institutions.all { it.regionName.startsWith("R") })
        assertTrue(v.huntingOffers.all { it.hunterName == "Co1" && it.victimName == "Co2" })
        assertTrue(v.migration.all { it.countryName.startsWith("Land") })
        assertTrue(v.brands.all { it.regionName.startsWith("R") })
    }

    @Test fun shortagesAreLimitedToTheCompanysRegions() {
        val sc = Scenario(); sc.run(60)
        val v = view(sc)
        assertTrue(v.shortages.isNotEmpty())
        assertTrue(v.shortages.all { it.region == RegionId(1) })
    }

    @Test fun readingNeverChangesTheState() {
        val sc = busy()
        val before = sc.system.stateHash()
        repeat(3) { view(sc); view(sc, c2) }
        assertEquals(before, sc.system.stateHash())
    }

    @Test fun migrationShowsPolicyAndLedger() {
        val sc = busy()
        val m = view(sc).migration.first { it.country == CountryId(1) }
        assertEquals(sc.system.migration.policy(CountryId(1)).opennessPpm, m.opennessPpm)
        assertEquals(sc.system.migration.ledger(CountryId(1)).out, m.out)
    }

    @Test fun adviceIsIncluded() {
        val sc = Scenario(); sc.run(90)
        val v = view(sc)
        assertEquals(TalentAdvisor(sc.system).advise(c1, Tick(sc.day), sc.inputs(), sc.world), v.advice)
    }

    @Test fun anEmptyCompanyGetsAnEmptyView() {
        val sc = Scenario(); sc.run(5)
        val v = view(sc, CompanyId(77))
        assertTrue(v.cohorts.isEmpty() && v.positions.isEmpty() && v.ladders.isEmpty() && v.programmes.isEmpty() && v.providers.none { it.mine })
    }
}
