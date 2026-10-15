package app.many.economytycoon.persistence

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertySnapshotTest {
    private fun world(): Simulation {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val country = sim.economy.world.countries().first()
        for (i in 1..9) sim.execute(Actor.System, Command.FoundCompany("Rival $i", country.id, null, Money.units(3_000_000), CompanyKind.AI))
        return sim
    }

    @Test fun listingsLeasesAndHoldingPeriodsSurviveASave() {
        val sim = world()
        sim.advance(300)
        val e = sim.economy
        assertTrue(e.property.heldParcels().isNotEmpty())
        val country = e.world.countries().first()
        val me = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Human Works", country.id, null, Money.units(2_000_000))) as Outcome.Ok).value.created!!)
        val city = e.world.citiesOf(country.id).first()
        val p = e.world.land.available(city.id).filter { e.propertyRunner.appraise(it).raw > 0L }.first()
        sim.execute(Actor.Player(1), Command.BuyParcel(me, p.id))
        e.property.startLease(e.property.addOffer(p.id, me, 180, Money.units(400), e.tick), 5, e.tick)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertEquals(sim.hash(), copy.hash())
        val a = e.property; val b = copy.economy.property
        assertEquals(a.listings().map { it.parcel to it.ask }, b.listings().map { it.parcel to it.ask })
        assertEquals(a.leases().map { Triple(it.id, it.monthlyRent, it.tenant) }, b.leases().map { Triple(it.id, it.monthlyRent, it.tenant) })
        assertEquals(a.leases().map { it.nextRent to it.collected }, b.leases().map { it.nextRent to it.collected })
        assertEquals(a.heldParcels(), b.heldParcels())
        assertEquals(a.leaseSequence, b.leaseSequence)
    }

    @Test fun aLoadedGameKeepsTradingLandInStep() {
        val sim = world()
        sim.advance(150)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        for (i in 0 until 300) { sim.advance(); copy.advance(); assertEquals("tick ${sim.tick.value}", sim.hash(), copy.hash()) }
        assertTrue(sim.economy.world.land.all().any { it.owner != null })
    }

    @Test fun anEmptyRegisterRoundTrips() {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertTrue(copy.economy.property.listings().isEmpty() && copy.economy.property.leases().isEmpty())
        assertEquals(sim.hash(), copy.hash())
    }
}
