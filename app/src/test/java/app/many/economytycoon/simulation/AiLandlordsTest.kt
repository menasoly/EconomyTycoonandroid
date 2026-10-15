package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiLandlordsTest {
    private class Rig {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val landlords = AiLandlords(e, CommandExecutor(e))
        /** AI companies with id % 3 == 1 speculate; make sure we have one. */
        val speculator: CompanyId = run {
            var found: CompanyId? = null
            var n = 0
            while (found == null) {
                val id = CompanyId((sim.execute(Actor.System, Command.FoundCompany("Land Bank ${n++}", country.id, null, Money.units(3_000_000), CompanyKind.AI)) as Outcome.Ok).value.created!!)
                if (id.value % 3L == 1L) found = id
            }
            found!!
        }
        fun lookDay(after: Long = 0): Long { var t = after + 1; while ((t + speculator.value) % 20L != 0L) t++; return t }
    }

    @Test fun aSpeculatorBuysAnOpenPlotOnItsDay() {
        val r = Rig()
        r.e.tick = Tick(r.lookDay())
        val report = r.landlords.run(Tick(r.lookDay()))
        assertEquals(1, report.bought)
        val p = r.e.world.land.ownedBy(r.speculator).single()
        assertEquals(Tick(r.lookDay()), r.e.property.heldSince(p.id))
        assertFalse(p.developed)
    }

    @Test fun nothingHappensOffItsDay() {
        val r = Rig()
        val report = r.landlords.run(Tick(r.lookDay() + 1))
        assertEquals(0, report.bought + report.listed + report.snapped)
    }

    @Test fun otherCompaniesAreLeftAlone() {
        val r = Rig()
        var other: CompanyId? = null
        var n = 0
        while (other == null) {
            val id = CompanyId((r.sim.execute(Actor.System, Command.FoundCompany("Plain $n", r.country.id, null, Money.units(3_000_000), CompanyKind.AI)) as Outcome.Ok).value.created!!)
            n++
            if (id.value % 3L != 1L) other = id
        }
        for (d in 1L..120L) r.landlords.run(Tick(d))
        assertTrue(r.e.world.land.ownedBy(other).isEmpty())
    }

    @Test fun landIsListedOnlyAfterTheHoldingPeriodAtAMarkup() {
        val r = Rig()
        var day = r.lookDay(); r.e.tick = Tick(day); r.landlords.run(Tick(day))
        val p = r.e.world.land.ownedBy(r.speculator).single()
        var early = 0
        var t = day
        while (t < day + 100) { t = r.lookDay(t); r.e.tick = Tick(t); early += r.landlords.run(Tick(t)).listed }
        assertEquals(0, early)
        val late = r.lookDay(day + 125)
        r.e.tick = Tick(late)
        assertTrue(r.landlords.run(Tick(late)).listed >= 1)
        val l = r.e.property.listing(p.id)!!
        assertTrue(l.ask > r.e.propertyRunner.appraise(p))
    }

    @Test fun aBargainListingIsSnappedUp() {
        val r = Rig()
        val seller = CompanyId((r.sim.execute(Actor.Player(1), Command.FoundCompany("Seller", r.country.id, null, Money.units(2_000_000))) as Outcome.Ok).value.created!!)
        val city = r.e.world.citiesOf(r.country.id).first()
        val p = r.e.world.land.available(city.id).filter { r.e.propertyRunner.appraise(it).raw > 0L }.minByOrNull { r.e.propertyRunner.appraise(it).raw }!!
        r.sim.execute(Actor.Player(1), Command.BuyParcel(seller, p.id))
        r.e.propertyRunner.list(seller, p.id, Money(r.e.propertyRunner.appraise(p).raw * 65 / 100), r.e.tick)
        // Other plots would be bought first, so count what the speculator ends up with.
        val report = r.landlords.run(Tick(r.lookDay()))
        assertEquals(1, report.snapped)
        assertEquals(r.speculator, r.e.world.land.get(p.id)!!.owner)
    }

    @Test fun anExpensiveListingIsIgnored() {
        val r = Rig()
        val seller = CompanyId((r.sim.execute(Actor.Player(1), Command.FoundCompany("Seller", r.country.id, null, Money.units(2_000_000))) as Outcome.Ok).value.created!!)
        val city = r.e.world.citiesOf(r.country.id).first()
        val p = r.e.world.land.available(city.id).filter { r.e.propertyRunner.appraise(it).raw > 0L }.minByOrNull { r.e.propertyRunner.appraise(it).raw }!!
        r.sim.execute(Actor.Player(1), Command.BuyParcel(seller, p.id))
        r.e.propertyRunner.list(seller, p.id, Money(r.e.propertyRunner.appraise(p).raw * 150 / 100), r.e.tick)
        assertEquals(0, r.landlords.run(Tick(r.lookDay())).snapped)
        assertEquals(seller, r.e.world.land.get(p.id)!!.owner)
    }

    @Test fun aSpeculatorStopsAtItsParcelLimit() {
        val r = Rig()
        var day = 0L
        repeat(10) { day = r.lookDay(day); r.e.tick = Tick(day); r.landlords.run(Tick(day)) }
        assertTrue(r.e.world.land.ownedBy(r.speculator).size <= 4)
    }
}
