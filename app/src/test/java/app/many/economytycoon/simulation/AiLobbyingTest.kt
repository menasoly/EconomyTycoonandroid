package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.regulation.Law
import app.many.economytycoon.regulation.Lever
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiLobbyingTest {
    private class Rig(val withFarms: Boolean = true) {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        val lobbying = AiLobbying(e, CommandExecutor(e))
        val rivals: List<CompanyId>
        init {
            e.regulationRunner.ensureGovernments(e.tick)
            rivals = (1..10).map { i ->
                val id = CompanyId((sim.execute(Actor.System, Command.FoundCompany("Rival $i", country.id, null, Money.units(300_000), CompanyKind.AI)) as Outcome.Ok).value.created!!)
                if (withFarms) e.production.grantFacility(id, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
                id
            }
        }
        fun law(l: Lever, raise: Boolean): Law {
            val c = e.world.country(country.id); val g = e.regulation.government(c.id)!!
            return (e.regulation.propose(c, l, e.regulation.target(l, l.valueIn(c), raise, g.baselineWage)!!, Tick(0)) as Outcome.Ok).value
        }
        fun keen(law: Law) = rivals.filter { (law.id.value + it.value) % 5L < 2L }
    }

    @Test fun effectsFollowSelfInterest() {
        val r = Rig()
        assertEquals(-1, r.lobbying.effect(r.law(Lever.CORPORATE_TAX, true), false))
        assertEquals(1, r.lobbying.effect(Rig().law(Lever.CORPORATE_TAX, false), false))
        assertEquals(-1, r.lobbying.effect(Rig().law(Lever.MINIMUM_WAGE, true), false))
        assertEquals(1, r.lobbying.effect(Rig().law(Lever.PAYROLL_TAX, false), false))
        assertEquals(0, r.lobbying.effect(Rig().law(Lever.TARIFF, true), true))
    }

    @Test fun onlyPollutersCareAboutEnvironmentalRules() {
        val r = Rig(); val law = r.law(Lever.ENVIRONMENT, true)
        assertEquals(0, r.lobbying.effect(law, false))
        assertEquals(-1, r.lobbying.effect(law, true))
    }

    @Test fun rivalsOpposeATaxRiseAndTheKeenOnesAllChipIn() {
        val r = Rig(); val law = r.law(Lever.CORPORATE_TAX, true)
        val report = r.lobbying.run(r.e.tick)
        val keen = r.keen(law)
        assertTrue(keen.isNotEmpty())
        assertEquals(keen.size, report.donations)
        for (id in r.rivals) {
            if (id in keen) { assertTrue(law.opposedBy(id) >= r.e.regulation.params.minLobby); assertEquals(Money.ZERO, law.backedBy(id)) }
            else assertEquals(Money.ZERO, law.opposedBy(id))
        }
    }

    @Test fun rivalsBackATaxCut() {
        val r = Rig(); val law = r.law(Lever.PAYROLL_TAX, false)
        r.lobbying.run(r.e.tick)
        assertTrue(r.keen(law).all { law.backedBy(it).raw > 0L && law.opposedBy(it).raw == 0L })
        assertTrue(law.supportPool.raw > 0L && law.opposePool.raw == 0L)
    }

    @Test fun eachCompanyWeighsInOnlyOncePerLaw() {
        val r = Rig(); val law = r.law(Lever.SALES_TAX, true)
        r.lobbying.run(r.e.tick)
        val pool = law.opposePool
        assertEquals(0, r.lobbying.run(r.e.tick).donations)
        assertEquals(pool, law.opposePool)
    }

    @Test fun donationsAreSmallAndComeOutOfCash() {
        val r = Rig(); val law = r.law(Lever.SALES_TAX, true)
        val who = r.keen(law).first()
        val before = r.e.finance.ledger.cash(app.many.economytycoon.finance.LedgerOwner.company(who))
        r.lobbying.run(r.e.tick)
        val gave = law.opposedBy(who)
        assertEquals(before - gave, r.e.finance.ledger.cash(app.many.economytycoon.finance.LedgerOwner.company(who)))
        assertTrue(gave <= Money.units(25_000) && gave.raw * 100 <= before.raw)
    }

    @Test fun companiesWithoutFactoriesInTheCountryStayOut() {
        val r = Rig(withFarms = false); r.law(Lever.CORPORATE_TAX, true)
        assertEquals(0, r.lobbying.run(r.e.tick).donations)
    }

    @Test fun humanCompaniesAreLeftToTheirOwnDevices() {
        val r = Rig(false); val me = CompanyId((r.sim.execute(Actor.Player(1), Command.FoundCompany("Human Works", r.country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        r.e.production.grantFacility(me, FacilityTypeId("grain_farm"), r.region, null, null, null, r.e.tick)
        val law = r.law(Lever.CORPORATE_TAX, true)
        r.lobbying.run(r.e.tick)
        assertFalse(law.opposedBy(me).raw > 0L)
    }

    @Test fun anEarlyVoteGoesWithTheMoneyForTheOpposition() {
        val r = Rig(); val law = r.law(Lever.CORPORATE_TAX, true)
        val before = r.e.regulation.leaning(law)
        r.lobbying.run(r.e.tick)
        assertTrue(r.e.regulation.leaning(law) < before)
    }
}
