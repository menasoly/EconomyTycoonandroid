package app.many.economytycoon.marketing

import app.many.economytycoon.core.Money
import app.many.economytycoon.marketing.MarketingTestSupport.beer
import app.many.economytycoon.marketing.MarketingTestSupport.bread
import app.many.economytycoon.marketing.MarketingTestSupport.context
import app.many.economytycoon.marketing.MarketingTestSupport.north
import app.many.economytycoon.marketing.MarketingTestSupport.south
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetOptimiserTest {
    private val params = MarketingParams()
    private val opt = BudgetOptimiser(params)
    private val rich = listOf(context(beer, north, 500_000L, 60_000), context(bread, south, 200_000L, 40_000, income = 1_300_000L))

    private fun c(total: Long, step: Long = 100) =
        OptimiserConstraints(Money.units(total), step = Money.units(step))

    @Test fun nothingToAdvertiseGivesAnEmptyPlan() {
        val r = opt.optimise(emptyList(), c(1_000))
        assertTrue(r.allocations.isEmpty())
        assertEquals(StopReason.NO_OPTIONS, r.stopReason)
        assertEquals(Money.units(1_000), r.leftover)
    }

    @Test fun aZeroBudgetBuysNothing() {
        val r = opt.optimise(rich, c(0))
        assertTrue(r.allocations.isEmpty())
        assertEquals(StopReason.NO_OPTIONS, r.stopReason)
    }

    @Test fun neverSpendsMoreThanTheBudget() {
        val r = opt.optimise(rich, c(6_000, 100))
        assertTrue(r.spend <= Money.units(6_000))
        assertEquals(r.spend, Money.sum(r.allocations.map { it.dailyBudget }))
        assertEquals(r.leftover, Money.units(6_000) - r.spend)
    }

    @Test fun everyAllocationRespectsItsChannelLimits() {
        val r = opt.optimise(rich, c(30_000, 250))
        assertTrue(r.allocations.isNotEmpty())
        for (a in r.allocations) {
            assertTrue(a.dailyBudget >= a.channel.spec.minDailySpend)
            assertTrue(a.dailyBudget <= a.channel.spec.maxDailySpend)
            assertTrue(a.reachPpm > 0L)
        }
    }

    @Test fun aSmallMarketWithTinySalesIsNotWorthAdvertising() {
        val poor = listOf(context(beer, north, 500_000L, 20, 300_000L))
        val r = opt.optimise(poor, c(5_000, 50))
        assertTrue(r.allocations.isEmpty())
        assertEquals(StopReason.DIMINISHING, r.stopReason)
        assertEquals(Money.ZERO, r.spend)
    }

    @Test fun spendingStopsOnceTheNextStepStopsPaying() {
        val r = opt.optimise(rich, c(500_000, 500))
        assertTrue(r.spend < Money.units(500_000))
        assertEquals(StopReason.DIMINISHING, r.stopReason)
        assertTrue(r.netProfit.raw > 0L)
    }

    @Test fun spendAllKeepsGoingWhileAnythingIsEarned() {
        val frugal = opt.optimise(rich, c(40_000, 500))
        val all = opt.optimise(rich, OptimiserConstraints(Money.units(40_000), step = Money.units(500), spendAll = true))
        assertTrue(all.spend >= frugal.spend)
    }

    @Test fun anyChannelShareCapIsHonoured() {
        val capped = opt.optimise(rich, OptimiserConstraints(Money.units(10_000), step = Money.units(100), maxChannelSharePpm = 300_000L, spendAll = true))
        for ((_, money) in capped.byChannel()) assertTrue(money <= Money.units(3_000) + Money.units(400))
    }

    @Test fun anyRegionShareCapIsHonoured() {
        val capped = opt.optimise(rich, OptimiserConstraints(Money.units(10_000), step = Money.units(100), maxRegionSharePpm = 600_000L, spendAll = true))
        val inNorth = capped.allocations.filter { it.market.region == north }.sumOf { it.dailyBudget.raw }
        assertTrue(inNorth <= Money.units(6_000).raw + Money.units(400).raw)
    }

    @Test fun onlyAllowedChannelsAreUsed() {
        val r = opt.optimise(rich, OptimiserConstraints(Money.units(8_000), step = Money.units(100), allowedChannels = setOf(Channel.ONLINE, Channel.RADIO)))
        assertTrue(r.allocations.all { it.channel == Channel.ONLINE || it.channel == Channel.RADIO })
        assertTrue(r.allocations.isNotEmpty())
    }

    @Test fun noChannelsAllowedMeansNothingIsBought() {
        val r = opt.optimise(rich, OptimiserConstraints(Money.units(8_000), step = Money.units(100), allowedChannels = emptySet()))
        assertTrue(r.allocations.isEmpty())
    }

    @Test fun theResultIsReproducible() {
        val a = opt.optimise(rich, c(9_000, 150))
        val b = opt.optimise(rich.reversed(), c(9_000, 150))
        assertEquals(a.allocations, b.allocations)
        assertEquals(a.expectedProfit, b.expectedProfit)
        assertEquals(a.steps, b.steps)
    }

    @Test fun greedyBeatsSpreadingTheMoneyEvenly() {
        val ctx = rich[0]
        val budget = Money.units(6_000)
        val plan = opt.optimise(listOf(ctx), OptimiserConstraints(budget, step = Money.units(100), spendAll = true))
        val share = Money(budget.raw / Channel.values().size)
        val even = ModelledLift.value(ctx, Channel.ordered.associateWith { Money.min(Money.max(share, it.spec.minDailySpend), it.spec.maxDailySpend) }, params)
        assertTrue(plan.valueByMarket.getValue(MarketKey(beer, north)).netProfit > even.netProfit)
    }

    @Test fun biggerBudgetsNeverEarnLessGrossProfit() {
        var last = Money.ZERO
        for (b in listOf(500L, 2_000L, 8_000L, 20_000L)) {
            val r = opt.optimise(rich, OptimiserConstraints(Money.units(b), step = Money.units(100), spendAll = true))
            assertTrue(r.expectedProfit >= last)
            last = r.expectedProfit
        }
    }

    @Test fun bestMarketGetsTheMoneyFirst() {
        val big = context(beer, north, 800_000L, 90_000)
        val small = context(bread, south, 100_000L, 3_000)
        val r = opt.optimise(listOf(small, big), OptimiserConstraints(Money.units(2_000), step = Money.units(100)))
        val toBig = r.forMarket(MarketKey(beer, north)).sumOf { it.dailyBudget.raw }
        val toSmall = r.forMarket(MarketKey(bread, south)).sumOf { it.dailyBudget.raw }
        assertTrue(toBig > toSmall)
    }

    @Test fun byChannelListsChannelsInOrder() {
        val r = opt.optimise(rich, c(12_000, 200))
        val order = r.byChannel().keys.map { it.ordinal }
        assertEquals(order.sorted(), order)
    }

    @Test fun resultTotalsAreTheSumOfTheMarkets() {
        val r = opt.optimise(rich, c(9_000, 150))
        assertEquals(r.expectedProfit, Money.sum(r.valueByMarket.values.map { it.incrementalProfit }))
        assertEquals(r.expectedRevenue, Money.sum(r.valueByMarket.values.map { it.incrementalRevenue }))
        assertTrue(r.returnPpm >= 1_000_000L)
    }

    @Test(expected = IllegalArgumentException::class) fun stepMustBePositive() {
        OptimiserConstraints(Money.units(10), step = Money.ZERO)
    }
}
