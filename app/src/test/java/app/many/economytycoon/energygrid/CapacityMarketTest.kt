package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapacityMarketTest {
    private fun bid(unit: Long, kw: Long, price: Long) = CapacityBid(UnitId(unit), kw, Money.units(price))

    @Test fun requirementIsPeakPlusTheTargetReserve() {
        assertEquals(115_000L, CapacityMarket.requirementKw(100_000L, 150_000L))
        assertEquals(0L, CapacityMarket.requirementKw(0L, 150_000L))
    }

    @Test fun cheapestBidsAreTakenUntilTheRequirementIsMet() {
        val round = CapacityMarket.auction(0L, 100_000L, listOf(bid(3, 30_000, 35), bid(1, 50_000, 10), bid(2, 40_000, 20)))
        assertEquals(listOf(CapacityAward(UnitId(1), 50_000), CapacityAward(UnitId(2), 40_000), CapacityAward(UnitId(3), 10_000)), round.awards)
        assertEquals(Money.units(35), round.clearingPrice)
        assertEquals(0L, round.shortfallKw)
        assertEquals(100_000L, round.clearedKw)
        assertEquals(Money.units(3_500), round.dailyPayment())
    }

    @Test fun everyAwardIsPaidTheClearingPrice() {
        val round = CapacityMarket.auction(0L, 60_000L, listOf(bid(1, 50_000, 10), bid(2, 40_000, 20)))
        assertEquals(Money.units(20), round.clearingPrice)
        assertEquals(Money.units(1_200), round.dailyPayment())
    }

    @Test fun notEnoughBidsLeavesAShortfallAtTheCap() {
        val round = CapacityMarket.auction(0L, 150_000L, listOf(bid(1, 50_000, 10), bid(2, 70_000, 20)))
        assertEquals(30_000L, round.shortfallKw)
        assertEquals(CapacityMarket.DEFAULT_CAP, round.clearingPrice)
        assertEquals(120_000L, round.clearedKw)
    }

    @Test fun bidsAboveTheCapAreIgnored() {
        val round = CapacityMarket.auction(0L, 10_000L, listOf(bid(1, 50_000, 1_000)), Money.units(900))
        assertTrue(round.awards.isEmpty())
        assertEquals(10_000L, round.shortfallKw)
    }

    @Test fun equalBidsGoToTheLowerUnitIdFirst() {
        val round = CapacityMarket.auction(0L, 10_000L, listOf(bid(8, 10_000, 15), bid(4, 10_000, 15)))
        assertEquals(listOf(CapacityAward(UnitId(4), 10_000)), round.awards)
    }

    @Test fun noRequirementMeansNoAuction() {
        val round = CapacityMarket.auction(5L, 0L, listOf(bid(1, 50_000, 10)))
        assertTrue(round.awards.isEmpty())
        assertEquals(Money.ZERO, round.clearingPrice)
    }

    @Test fun zeroFirmCapacityCannotBid() {
        val round = CapacityMarket.auction(0L, 10_000L, listOf(bid(1, 0, 0), bid(2, 10_000, 5)))
        assertEquals(listOf(UnitId(2)), round.awards.map { it.unit })
    }

    @Test fun firmCapacityIsNameplateTimesTheTechnologyCredit() {
        assertEquals(88_000L, CapacityMarket.firmKw(Technology.COAL, 100_000L))
        assertEquals(15_000L, CapacityMarket.firmKw(Technology.WIND, 100_000L))
        assertEquals(10_000L, CapacityMarket.firmKw(Technology.SOLAR, 100_000L))
    }

    @Test fun missingMoneyIsFixedCostLessEnergyRent() {
        val mc = Technology.COAL.marginalCost(FuelPrices())
        assertEquals(Money(242_000L), CapacityMarket.fixedCostPerMwDay(Technology.COAL))
        assertEquals(Money(153_085L), CapacityMarket.bidPerMwDay(Technology.COAL, mc, Money(360_000L)))
        assertEquals(Money.ZERO, CapacityMarket.bidPerMwDay(Technology.COAL, mc, Money.units(80)))
        assertEquals(Money(242_000L), CapacityMarket.bidPerMwDay(Technology.COAL, mc, Money.units(10)))
    }

    @Test fun roundsLastAYearAndPayInProportion() {
        val round = CapacityMarket.auction(100L, 1_000L, listOf(bid(1, 1_000, 10)))
        assertEquals(460L, round.validUntil)
        assertEquals(1_000L, round.awardOf(UnitId(1)))
        assertEquals(0L, round.awardOf(UnitId(2)))
        assertEquals(Money.units(5), CapacityMarket.dailyPayment(500L, Money.units(10)))
    }
}
