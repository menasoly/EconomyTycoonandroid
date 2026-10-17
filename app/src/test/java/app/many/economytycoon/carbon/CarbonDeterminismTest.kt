package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.cyrus
import app.many.economytycoon.carbon.CarbonTestSupport.emitter
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbonDeterminismTest {
    private fun run(seed: Long, days: Long = 800): CarbonTestSupport.Fixture = CarbonTestSupport.Fixture(seed).also { CarbonTestSupport.busy(it, days) }

    @Test fun sameSeedSameHash() = assertEquals(run(5).state.stateHash(), run(5).state.stateHash())

    // Offset events hold the live project, which has identity equality, so they are compared by count and tick.
    @Test fun sameSeedSameEventStream() {
        val a = run(5).events; val b = run(5).events
        assertEquals(a.filter { it !is CarbonEvent.OffsetProjectChanged }, b.filter { it !is CarbonEvent.OffsetProjectChanged })
        assertEquals(a.filterIsInstance<CarbonEvent.OffsetProjectChanged>().map { it.tick }, b.filterIsInstance<CarbonEvent.OffsetProjectChanged>().map { it.tick })
    }

    @Test fun sameSeedSameMoney() {
        val a = run(5); val b = run(5)
        assertEquals(a.payments.map { it.total }, b.payments.map { it.total })
        assertEquals(a.adjustments.size, b.adjustments.size)
    }

    @Test fun differentSeedsDivergeThroughTheOffsetMarket() {
        assertNotEquals(run(5, 200).state.stateHash(), run(6, 200).state.stateHash())
    }

    @Test fun activityOrderDoesNotMatter() {
        val one = listOf(emitter(1, acme), emitter(2, borea), emitter(3, cyrus, coalUnits = 40))
        val a = CarbonTestSupport.Fixture(3)
        val b = CarbonTestSupport.Fixture(3)
        a.service.installRegime(CarbonTestSupport.capAndTrade(), Tick(0))
        b.service.installRegime(CarbonTestSupport.capAndTrade(), Tick(0))
        a.run(0, 400, one)
        b.run(0, 400, one.reversed())
        assertEquals(a.state.stateHash(), b.state.stateHash())
    }

    @Test fun stoppingAndResumingFromASaveMatchesRunningStraightThrough() {
        val plants = listOf(emitter(1, acme), emitter(2, borea))
        val straight = CarbonTestSupport.Fixture(8)
        straight.service.installRegime(CarbonTestSupport.capAndTrade(), Tick(0))
        straight.run(0, 600, plants)

        val first = CarbonTestSupport.Fixture(8)
        first.service.installRegime(CarbonTestSupport.capAndTrade(), Tick(0))
        first.run(0, 250, plants)
        val bytes = CarbonCodec.encode(first.state)
        val streams = first.service.random.snapshot()

        val second = CarbonTestSupport.Fixture(8)
        CarbonCodec.decode(bytes, second.state)
        second.service.random.restore(streams)
        second.run(251, 600, plants)
        assertEquals(straight.state.stateHash(), second.state.stateHash())
    }

    @Test fun readingBetweenStepsChangesNothing() {
        val plants = listOf(emitter(1, acme), emitter(2, borea))
        val a = CarbonTestSupport.Fixture(2); val b = CarbonTestSupport.Fixture(2)
        a.service.installRegime(CarbonTestSupport.capAndTrade(), Tick(0)); b.service.installRegime(CarbonTestSupport.capAndTrade(), Tick(0))
        for (d in 0L..300L) {
            a.step(d, plants); b.step(d, plants)
            if (d % 7 == 0L) { b.views.company(acme, Tick(d)); b.views.market(uk, Tick(d)); b.views.league(uk, 2000) }
        }
        assertEquals(a.state.stateHash(), b.state.stateHash())
    }

    @Test fun theLedgerIntentsNeverCarryNegativeAmounts() {
        val fx = run(9)
        assertTrue(fx.payments.all { !it.total.isNegative })
        assertTrue(fx.payments.map { it.key }.distinct().size == fx.payments.size)
    }

    @Test fun cashPoorCompaniesStillProduceTheSameStateTwice() {
        fun go(): CarbonTestSupport.Fixture {
            val fx = CarbonTestSupport.Fixture(4)
            fx.cash[acme] = Money.units(1_000); fx.cash[borea] = Money.units(2_000)
            val plants = listOf(emitter(1, acme), emitter(2, borea))
            fx.service.installRegime(CarbonTestSupport.capAndTrade(), Tick(0))
            fx.run(0, 20, plants)
            fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(500), Money.units(30)), 20)
            fx.exec(CarbonCommand.BidAtAuction(borea, uk, Tonnes.of(500), Money.units(30)), 20)
            fx.run(21, 500, plants)
            return fx
        }
        assertEquals(go().state.stateHash(), go().state.stateHash())
    }
}
