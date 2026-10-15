package app.many.economytycoon.simulation

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.grants.GrantEvent
import app.many.economytycoon.grants.GrantKind
import app.many.economytycoon.grants.GrantProgram
import app.many.economytycoon.grants.GrantStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrantRunnerTest {
    private class Rig {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        val gov = LedgerOwner.government(country.id)
        init { e.finance.mint(e.tick, gov, Money.units(5_000_000), "test:treasury") }
        fun company(name: String, p: Long): CompanyId =
            CompanyId((sim.execute(Actor.Player(p), Command.FoundCompany(name, country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        fun farm(owner: CompanyId) = e.production.grantFacility(owner, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
        fun jobsGrant(rate: Long = 100, cap: Long = 50_000, budget: Long = 100_000): GrantProgram =
            (e.grants.announce(country.id, GrantKind.JOBS, null, Money.units(rate), Money.units(cap), Money.units(budget), e.tick) as Outcome.Ok).value
        fun buildGrant(family: IndustryFamily = IndustryFamily.AGRICULTURE, rate: Long = 5_000): GrantProgram =
            (e.grants.announce(country.id, GrantKind.FACILITIES, family, Money.units(rate), Money.units(50_000), Money.units(100_000), e.tick) as Outcome.Ok).value
        fun cash(c: CompanyId) = e.finance.ledger.cash(LedgerOwner.company(c))
        fun at(t: Long) { e.tick = Tick(t) }
        fun apply(c: CompanyId, g: GrantProgram) = e.grantRunner.apply(c, g.id, e.tick)
    }

    @Test fun facilitiesInTheFavouredIndustryAreCounted() {
        val r = Rig(); val a = r.company("A Works", 1)
        assertEquals(0L, r.e.grantRunner.facilitiesIn(a, r.country.id, IndustryFamily.AGRICULTURE))
        r.farm(a); r.farm(a)
        assertEquals(2L, r.e.grantRunner.facilitiesIn(a, r.country.id, IndustryFamily.AGRICULTURE))
        assertEquals(0L, r.e.grantRunner.facilitiesIn(a, r.country.id, IndustryFamily.MINING))
    }

    @Test fun demolishedFacilitiesDoNotCount() {
        val r = Rig(); val a = r.company("A Works", 1)
        val f = r.farm(a); r.farm(a)
        r.e.production.demolish(a, f.id)
        assertEquals(1L, r.e.grantRunner.facilitiesIn(a, r.country.id, IndustryFamily.AGRICULTURE))
    }

    @Test fun applyingTakesTheCurrentCountAsTheBaseline() {
        val r = Rig(); val a = r.company("A Works", 1)
        r.farm(a)
        val g = r.buildGrant()
        val app = (r.apply(a, g) as Outcome.Ok).value
        assertEquals(1L, app.baseline)
    }

    @Test fun onlyTradingCompaniesCanApply() {
        val r = Rig(); val g = r.jobsGrant()
        assertEquals(Rules.WRONG_STATE, (r.apply(CompanyId(999), g) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, (r.e.grantRunner.apply(r.company("A Works", 1), app.many.economytycoon.core.GrantId(77), r.e.tick) as Outcome.Rejected).rejection.rule)
    }

    @Test fun anInvestmentGrantPaysForNewFacilities() {
        val r = Rig(); val a = r.company("A Works", 1)
        r.farm(a)
        val g = r.buildGrant(rate = 5_000)
        r.apply(a, g)
        r.farm(a); r.farm(a)
        val before = r.cash(a)
        r.at(g.endsAt.value)
        val ev = r.e.grantRunner.run(r.e.tick)
        assertEquals(Money.units(10_000), g.applicationOf(a)!!.paid)
        assertEquals(before + Money.units(10_000), r.cash(a))
        assertEquals(GrantStatus.PAID, g.status)
        assertTrue(ev.any { it is GrantEvent.Settled && it.company == a && it.gain == 2L })
        assertTrue(ev.any { it is GrantEvent.Closed })
        assertTrue(r.e.finance.invariants().toString(), r.e.finance.invariants().isEmpty())
    }

    @Test fun standingStillEarnsNothing() {
        val r = Rig(); val a = r.company("A Works", 1)
        r.farm(a)
        val g = r.buildGrant()
        r.apply(a, g)
        val before = r.cash(a)
        r.at(g.endsAt.value)
        r.e.grantRunner.run(r.e.tick)
        assertEquals(Money.ZERO, g.applicationOf(a)!!.paid)
        assertEquals(before, r.cash(a))
    }

    @Test fun theTreasuryPaysOutOfItsOwnCash() {
        val r = Rig(); val a = r.company("A Works", 1)
        val g = r.buildGrant(rate = 5_000)
        r.apply(a, g)
        repeat(3) { r.farm(a) }
        val treasury = r.e.finance.ledger.cash(r.gov)
        r.at(g.endsAt.value)
        r.e.grantRunner.run(r.e.tick)
        assertEquals(treasury - Money.units(15_000), r.e.finance.ledger.cash(r.gov))
    }

    @Test fun anEmptyTreasuryPaysNothingButTheProgrammeStillCloses() {
        val r = Rig(); val a = r.company("A Works", 1)
        val g = r.buildGrant(rate = 5_000)
        r.apply(a, g)
        r.farm(a)
        val cash = r.e.finance.ledger.cash(r.gov)
        r.e.finance.ledger.post(app.many.economytycoon.finance.TransactionDraft(
            "test:drain", r.e.tick, app.many.economytycoon.finance.ReasonCode.TRANSFER,
            listOf(
                app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(r.gov, app.many.economytycoon.finance.Chart.CASH), -cash),
                app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(r.e.finance.households, app.many.economytycoon.finance.Chart.CASH), cash),
            ), "drain",
        ))
        r.at(g.endsAt.value)
        r.e.grantRunner.run(r.e.tick)
        assertEquals(GrantStatus.PAID, g.status)
        assertEquals(Money.ZERO, g.applicationOf(a)!!.paid)
        assertEquals(1L, g.applicationOf(a)!!.gain)
    }

    @Test fun aClosedCompanyEarnsNothing() {
        val r = Rig(); val a = r.company("A Works", 1); val b = r.company("B Works", 2)
        val g = r.buildGrant(rate = 5_000)
        r.apply(a, g); r.apply(b, g)
        r.farm(a); r.farm(b)
        r.e.retire(a)
        r.at(g.endsAt.value)
        r.e.grantRunner.run(r.e.tick)
        assertEquals(null, g.applicationOf(a))
        assertEquals(Money.units(5_000), g.applicationOf(b)!!.paid)
    }

    @Test fun employmentIsCountedAcrossTheCountry() {
        val r = Rig(); val a = r.company("A Works", 1)
        val f = r.farm(a)
        assertEquals(0L, r.e.grantRunner.jobsIn(a, r.country.id))
        r.sim.execute(Actor.Player(1), Command.SetStaffing(a, f.id, RoleId("farmhand"), 6))
        r.sim.advance(30)
        val jobs = r.e.grantRunner.jobsIn(a, r.country.id)
        assertTrue("jobs=$jobs", jobs > 0L)
        assertEquals(r.e.workforce.employedBy(a), jobs)
    }

    @Test fun aJobsGrantPaysForHiring() {
        val r = Rig(); val a = r.company("A Works", 1)
        val f = r.farm(a)
        val g = r.jobsGrant(rate = 100)
        r.apply(a, g)
        r.sim.execute(Actor.Player(1), Command.SetStaffing(a, f.id, RoleId("farmhand"), 8))
        r.sim.advance(40)
        val jobs = r.e.grantRunner.jobsIn(a, r.country.id)
        r.at(g.endsAt.value)
        r.e.finance.mint(r.e.tick, r.gov, Money.units(1_000_000), "test:topup")
        r.e.grantRunner.run(r.e.tick)
        assertEquals(jobs, g.applicationOf(a)!!.gain)
        assertEquals(Money.units(100 * jobs), g.applicationOf(a)!!.paid)
    }

    @Test fun governmentsAnnounceProgrammesOnSchedule() {
        val r = Rig()
        r.e.finance.mint(r.e.tick, r.gov, Money.units(50_000_000), "test:more")
        var announced = 0
        for (t in 1L..200L) {
            r.at(t)
            announced += r.e.grantRunner.run(r.e.tick).count { it is GrantEvent.Announced }
        }
        assertTrue("announced=$announced", announced > 0)
        assertTrue(r.e.grants.all().all { it.country == r.country.id || r.e.world.countries().any { c -> c.id == it.country } })
    }

    @Test fun committedBudgetsStayWithinWhatTheStateCanAfford() {
        val r = Rig()
        for (t in 1L..300L) {
            r.at(t)
            r.e.grantRunner.run(r.e.tick)
            for (c in r.e.world.countries()) {
                val committed = r.e.grants.committed(c.id)
                val limit = r.e.grantRunner.budgetFor(c.id)
                assertTrue("tick $t: $committed > $limit", committed <= limit)
            }
        }
    }

    @Test fun favourableIndustriesAreBuildableFromTheStart() {
        val r = Rig()
        val fam = r.e.grantRunner.favourable()
        assertTrue(fam.isNotEmpty())
        assertEquals(fam.sortedBy { it.ordinal }, fam)
        assertEquals(fam.size, fam.toSet().size)
    }

    @Test fun commandsReachTheRunner() {
        val r = Rig(); val a = r.company("A Works", 1)
        val g = r.jobsGrant()
        assertTrue(r.sim.execute(Actor.Player(1), Command.ApplyGrant(a, g.id)) is Outcome.Ok)
        assertTrue(g.applicationOf(a) != null)
        assertTrue(r.sim.execute(Actor.Player(1), Command.WithdrawGrant(a, g.id)) is Outcome.Ok)
        assertFalse(g.applicationOf(a) != null)
    }

    @Test fun anotherPlayerCannotApplyForYou() {
        val r = Rig(); val a = r.company("A Works", 1)
        val g = r.jobsGrant()
        assertTrue(r.sim.execute(Actor.Player(2), Command.ApplyGrant(a, g.id)) is Outcome.Rejected)
    }
}
