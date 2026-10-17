package app.many.economytycoon.persistence

import app.many.economytycoon.content.ShippedContent
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndustriesIntegrationTest {
    private val shipped = ShippedContent.repository

    @Test fun shippedContentCarriesTheIndustryPacks() {
        assertEquals(16, shipped.versions.size)
        assertTrue(shipped.allGoods.size > SimTestSupport.content.allGoods.size)
    }

    @Test fun anOlderSaveIsRefusedWithATypedErrorWhenTheContentGrew() {
        val old = SimulationSnapshot.save(SimTestSupport.sim(5L, 2))
        var seen: IncompatibleContentException? = null
        try { SimulationSnapshot.load(old, shipped) } catch (e: IncompatibleContentException) { seen = e }
        assertTrue(seen != null)
        assertTrue(seen?.message?.contains("save was made with content") == true)
    }

    @Test fun anOlderSaveStillLoadsWithItsOwnContent() {
        val sim = SimTestSupport.sim(5L, 2)
        sim.advance(30)
        val back = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertEquals(sim.hash(), back.hash())
    }

    @Test fun aSaveMadeWithShippedContentRoundTripsAndContinuesIdentically() {
        val cfg = SimTestSupport.config(9L, 2)
        val a = Simulation(cfg, shipped)
        a.advance(40)
        val b = SimulationSnapshot.load(SimulationSnapshot.save(a), shipped)
        assertEquals(a.hash(), b.hash())
        a.advance(40)
        b.advance(40)
        assertEquals(a.hash(), b.hash())
    }

    @Test fun theSameSeedRunsTheSameWithShippedContent() {
        val cfg = SimTestSupport.config(9L, 2)
        val x = Simulation(cfg, shipped).also { it.advance(60) }
        val y = Simulation(cfg, shipped).also { it.advance(60) }
        assertEquals(x.hash(), y.hash())
    }

    @Test fun theNewDepositGoodsChangeTheWorld() {
        val cfg = SimTestSupport.config(9L, 2)
        assertNotEquals(Simulation(cfg, shipped).hash(), Simulation(cfg, SimTestSupport.content).hash())
    }
}
