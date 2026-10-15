package app.many.economytycoon.simulation

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.markets.MarketKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventsTest {
    private fun hash(q: EventQueue) = CanonicalHasher().also { q.hashInto(it) }.finish().toHex()

    @Test fun eventsComeOutInTickThenScheduleOrder() {
        val q = EventQueue()
        val g = GoodId("wheat")
        q.schedule(Tick(5), EconomicEvent.SupplyShock(g, 10, "late"))
        q.schedule(Tick(2), EconomicEvent.SupplyShock(g, 20, "early-a"))
        q.schedule(Tick(2), EconomicEvent.SupplyShock(g, 30, "early-b"))
        assertTrue(q.due(Tick(1)).isEmpty())
        assertEquals(listOf("early-a", "early-b"), q.due(Tick(3)).map { it.event.title })
        assertEquals(listOf("late"), q.due(Tick(9)).map { it.event.title })
        assertTrue(q.due(Tick(99)).isEmpty())
    }

    @Test fun historyKeepsTheMostRecentEvents() {
        val q = EventQueue()
        repeat(150) { q.schedule(Tick(it.toLong()), EconomicEvent.SupplyShock(GoodId("wheat"), 1, "e$it")) }
        q.due(Tick(1000))
        assertEquals(EventQueue.MAX_HISTORY, q.history.size)
        assertEquals("e149", q.history.last().event.title)
    }

    @Test fun demandShiftsAreRememberedUntilRestored() {
        val q = EventQueue()
        val g = GoodId("bread")
        assertEquals(1_000_000L, q.demandShift(g))
        q.setDemandShift(g, 1_200_000L)
        assertEquals(1_200_000L, q.demandShift(g))
        q.setDemandShift(g, 1_000_000L)
        assertEquals(1_000_000L, q.demandShift(g))
    }

    @Test fun stateChangesTheHash() {
        val q = EventQueue(); val h0 = hash(q)
        q.schedule(Tick(3), EconomicEvent.SupplyShock(GoodId("wheat"), 1, "x"))
        val h1 = hash(q)
        q.setDemandShift(GoodId("wheat"), 900_000L)
        assertTrue(h0 != h1 && h1 != hash(q))
    }

    @Test fun supplyShocksReachEveryMarketOfTheGood() {
        val sim = SimTestSupport.sim(4, 0)
        sim.pipeline.events.schedule(sim.tick, EconomicEvent.SupplyShock(GoodId("wheat"), 100_000, "blight"))
        val s = sim.advance()
        assertEquals(1, s.eventsApplied)
        for (r in sim.economy.world.regions()) {
            val st = sim.economy.markets.state(MarketKey(r.id, GoodId("wheat")))
            assertTrue(st != null)
        }
    }

    @Test fun confidenceShocksShowUpAtTheNextMonthlyReview() {
        val calm = SimTestSupport.sim(4, 0); val shocked = SimTestSupport.sim(4, 0)
        val c = shocked.economy.world.countries().first().id
        shocked.pipeline.events.schedule(shocked.tick, EconomicEvent.ConfidenceShock(c, -400_000, "panic"))
        calm.advance(31); shocked.advance(31)
        assertTrue(shocked.economy.macro.demandShiftPpm(c) < calm.economy.macro.demandShiftPpm(c))
    }

    @Test fun generatorIsDeterministicAndQuietOffSchedule() {
        val a = SimTestSupport.sim(8, 0); val b = SimTestSupport.sim(8, 0)
        val qa = EventQueue(); val qb = EventQueue()
        val ga = EventGenerator(a.economy); val gb = EventGenerator(b.economy)
        for (t in 1..360L) { ga.generate(Tick(t), qa); gb.generate(Tick(t), qb) }
        assertEquals(hash(qa), hash(qb))
        assertTrue(qa.pendingEvents().isNotEmpty())
        assertTrue(qa.pendingEvents().all { it.tick.value % 30L == 0L || it.event is EconomicEvent.DemandRestored })
        val quiet = EventQueue(); ga.generate(Tick(7), quiet)
        assertTrue(quiet.pendingEvents().isEmpty())
    }
}
