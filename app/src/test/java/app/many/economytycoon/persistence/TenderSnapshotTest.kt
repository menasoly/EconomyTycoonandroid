package app.many.economytycoon.persistence

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TenderSnapshotTest {
    private fun world(): Simulation {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        for (i in 1..2) {
            val ai = CompanyId((sim.execute(Actor.System, Command.FoundCompany("Rival $i", country.id, null, Money.units(300_000), CompanyKind.AI)) as Outcome.Ok).value.created!!)
            val farm = e.production.grantFacility(ai, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
            farm.inventory.add(GoodId("wheat"), Quantity.units(50_000), Money.units(300_000))
        }
        return sim
    }

    @Test fun tendersBidsAndFailuresSurviveASave() {
        val sim = world()
        sim.advance(200)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertEquals(sim.hash(), copy.hash())
        val a = sim.economy.tenders; val b = copy.economy.tenders
        assertEquals(a.all().map { it.id }, b.all().map { it.id })
        assertEquals(a.all().map { it.status }, b.all().map { it.status })
        assertEquals(a.all().map { it.awardedPrice }, b.all().map { it.awardedPrice })
        assertEquals(a.all().map { t -> t.bids.map { it.company to it.unitPrice } }, b.all().map { t -> t.bids.map { it.company to it.unitPrice } })
        assertEquals(a.bidSequence, b.bidSequence)
        assertTrue(a.all().isNotEmpty())
    }

    @Test fun aLoadedGameKeepsIssuingTheSameTenders() {
        val sim = world()
        sim.advance(50)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        for (i in 0 until 120) { sim.advance(); copy.advance(); assertEquals("tick ${sim.tick.value}", sim.hash(), copy.hash()) }
        assertEquals(sim.economy.tenders.all().size, copy.economy.tenders.all().size)
    }
}
