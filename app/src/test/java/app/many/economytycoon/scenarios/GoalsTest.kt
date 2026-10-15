package app.many.economytycoon.scenarios

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalsTest {
    private class Rig {
        val sim = Simulation(SimTestSupport.config(9, 2), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        val me = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Measured Co", country.id, null, Money.units(500_000))) as Outcome.Ok).value.created!!)
        fun value(kind: GoalKind, target: Long = 1L) = GoalMeter.measure(e, 1L, Goal(kind, target))
    }

    @Test fun aGoalNeedsAPositiveTarget() {
        var failed = false
        try { Goal(GoalKind.CASH, 0) } catch (e: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }

    @Test fun everyKindHasAReadableDescription() {
        for (k in GoalKind.values()) {
            val d = Goal(k, 3).describe()
            assertTrue(k.name, d.length > 8 && d.first().isUpperCase())
        }
    }

    @Test fun moneyReadsInRoundUnits() {
        assertEquals("Reach a net worth of 2 million", Goal(GoalKind.NET_WORTH, 2_000_000).describe())
        assertEquals("Hold 250 thousand in cash", Goal(GoalKind.CASH, 250_000).describe())
        assertEquals("Earn 1500 profit in a month", Goal(GoalKind.MONTHLY_PROFIT, 1_500).describe())
        assertEquals("Run 12 facilities", Goal(GoalKind.FACILITIES, 12).describe())
    }

    @Test fun progressIsCappedAndNeverNegative() {
        assertEquals(500_000L, GoalProgress(Goal(GoalKind.EMPLOYEES, 100), 50).ppm)
        assertEquals(1_000_000L, GoalProgress(Goal(GoalKind.EMPLOYEES, 100), 250).ppm)
        assertEquals(0L, GoalProgress(Goal(GoalKind.EMPLOYEES, 100), -5).ppm)
        assertTrue(GoalProgress(Goal(GoalKind.EMPLOYEES, 100), 100).done); assertFalse(GoalProgress(Goal(GoalKind.EMPLOYEES, 100), 99).done)
    }

    @Test fun cashAndNetWorthFollowTheBooks() {
        val r = Rig()
        assertEquals(500_000L, r.value(GoalKind.CASH))
        assertTrue(r.value(GoalKind.NET_WORTH) in 400_000L..600_000L)
        r.e.finance.mint(r.e.tick, r.e.companies.require(r.me).owner, Money.units(100_000), "gift")
        assertEquals(600_000L, r.value(GoalKind.CASH))
    }

    @Test fun facilitiesAreCounted() {
        val r = Rig()
        assertEquals(0L, r.value(GoalKind.FACILITIES))
        r.e.production.grantFacility(r.me, FacilityTypeId("grain_farm"), r.region, null, null, null, r.e.tick)
        r.e.production.grantFacility(r.me, FacilityTypeId("grain_farm"), r.region, null, null, null, r.e.tick)
        assertEquals(2L, r.value(GoalKind.FACILITIES))
    }

    @Test fun parcelsAndRentCountOnlyTheCompanysOwn() {
        val r = Rig()
        assertEquals(0L, r.value(GoalKind.PARCELS))
        val city = r.e.world.citiesOf(r.country.id).first()
        val p = r.e.world.land.available(city.id).filter { r.e.propertyRunner.appraise(it).raw > 0L }.first()
        r.sim.execute(Actor.Player(1), Command.BuyParcel(r.me, p.id))
        assertEquals(1L, r.value(GoalKind.PARCELS))
        r.e.property.startLease(r.e.property.addOffer(p.id, r.me, 90, Money.units(700), r.e.tick), 0, r.e.tick)
        assertEquals(700L, r.value(GoalKind.RENT_INCOME))
    }

    @Test fun debtFreeCountsFacilitiesOnlyWhileThereIsNoDebt() {
        val r = Rig()
        r.e.production.grantFacility(r.me, FacilityTypeId("grain_farm"), r.region, null, null, null, r.e.tick)
        assertEquals(1L, r.value(GoalKind.DEBT_FREE))
    }

    @Test fun marketLeadershipIsAllOrNothing() {
        val r = Rig()
        val v = r.value(GoalKind.MARKET_LEADER)
        assertTrue(v == 0L || v == 1L)
        r.e.finance.mint(r.e.tick, r.e.companies.require(r.me).owner, Money.units(900_000_000), "windfall")
        assertEquals(1L, r.value(GoalKind.MARKET_LEADER))
    }

    @Test fun aPlayerWithoutACompanyMeasuresZero() {
        val r = Rig()
        assertEquals(0L, GoalMeter.measure(r.e, 99L, Goal(GoalKind.CASH, 1)))
        assertTrue(GoalMeter.playerCompanies(r.e, 99L).isEmpty())
    }

    @Test fun progressListFollowsTheGoalOrder() {
        val r = Rig()
        val goals = listOf(Goal(GoalKind.CASH, 1_000_000), Goal(GoalKind.FACILITIES, 1))
        val p = GoalMeter.progress(r.e, 1L, goals)
        assertEquals(goals, p.map { it.goal }); assertEquals(listOf(false, false), p.map { it.done })
    }

    @Test fun creditRankAndTechsAreNonNegative() {
        val r = Rig()
        assertTrue(r.value(GoalKind.CREDIT_RANK) >= 0L); assertTrue(r.value(GoalKind.TECHNOLOGIES) >= 0L); assertTrue(r.value(GoalKind.INVESTED) >= 0L)
        assertEquals(0L, r.value(GoalKind.COMPANIES))
    }
}
