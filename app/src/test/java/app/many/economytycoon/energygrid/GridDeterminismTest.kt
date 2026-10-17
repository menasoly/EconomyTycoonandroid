package app.many.economytycoon.energygrid

import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridDeterminismTest {
    private fun linked(seed: Long): GridWorld {
        val w = GridWorld(seed = seed)
        w.unit(Technology.COAL, 90_000L, owner = w.owner)
        w.unit(Technology.WIND, 60_000L)
        w.unit(Technology.STORAGE, 15_000L)
        w.households(60_000L)
        w.facility(25_000L)
        val b = w.otherGrid()
        w.unit(Technology.GAS, 50_000L, grid = b)
        w.households(55_000L, grid = b)
        w.service.addLink(w.grid.id, b.id, 25_000L, 30_000L, app.many.economytycoon.core.Money.units(1)).getOrThrow()
        return w
    }

    @Test fun theSameSeedGivesTheSameState() {
        val a = linked(7L); val b = linked(7L)
        a.days(40); b.days(40)
        assertEquals(a.state.stateHash(), b.state.stateHash())
    }

    @Test fun anotherSeedGivesAnotherHistory() {
        val a = linked(7L); val b = linked(8L)
        a.days(60); b.days(60)
        assertNotEquals(a.state.stateHash(), b.state.stateHash())
    }

    @Test fun eventsComeInTheSameOrder() {
        val a = linked(11L).days(40).flatMap { it.events }
        val b = linked(11L).days(40).flatMap { it.events }
        assertEquals(a, b)
    }

    @Test fun savingAndLoadingInTheMiddleChangesNothing() {
        val straight = linked(5L)
        straight.days(40)
        val first = linked(5L)
        first.days(20)
        val bytes = EnergyGridCodec.toBytes(first.state)
        val streams = first.random.snapshot()
        val restored = EnergyGridService(EnergyGridCodec.fromBytes(bytes))
        val random = RandomProvider(5L).also { it.restore(streams) }
        for (t in 20L until 40L) restored.advanceDay(Tick(t), DayInputs(), random)
        assertEquals(straight.state.stateHash(), restored.state.stateHash())
    }

    @Test fun billingIsRepeatable() {
        val a = linked(3L); val b = linked(3L)
        a.days(30); b.days(30)
        val x = a.service.settleMonth(Tick(30)).intents.map { Triple(it.key, it.total, it.payer) }
        val y = b.service.settleMonth(Tick(30)).intents.map { Triple(it.key, it.total, it.payer) }
        assertTrue(x.isNotEmpty())
        assertEquals(x, y)
    }

    @Test fun aGridRunsWithAndWithoutANeighbour() {
        val a = linked(9L)
        a.days(25)
        val b = GridWorld(seed = 9L)
        b.unit(Technology.COAL, 90_000L, owner = b.owner)
        b.unit(Technology.WIND, 60_000L)
        b.unit(Technology.STORAGE, 15_000L)
        b.households(60_000L)
        b.facility(25_000L)
        b.days(25)
        GridInvariants.check(a.state)
        GridInvariants.check(b.state)
        assertEquals(25, a.grid.history.size)
        assertEquals(25, b.grid.history.size)
    }

    @Test fun readingNeverChangesTheHash() {
        val w = linked(2L)
        w.days(10)
        val before = w.state.stateHash()
        GridAnalytics.priceStats(w.grid.history, 10, w.grid.params.priceCap)
        GridAnalytics.reliability(w.grid.history, 10)
        GridAnalytics.fleetMix(w.grid)
        GridAdvisor().all(GridFacts.of(w.state, w.grid, 10L, w.service.peakDemandKw(w.grid.id)))
        w.service.peakDemandKw(w.grid.id)
        assertEquals(before, w.state.stateHash())
    }

    @Test fun linkedGridsKeepTheirInvariantsForAYear() {
        val w = linked(13L)
        for (block in 0 until 6) {
            w.days(60)
            GridInvariants.check(w.state)
        }
        assertTrue(w.state.links.single().sentAbKwh + w.state.links.single().sentBaKwh > 0L)
    }
}
