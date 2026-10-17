package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DispatchEngineTest {
    private fun firm(id: Long, cost: Long, avail: Long, prev: Long = avail, up: Long = avail, down: Long = avail, minStable: Long = 0L, tech: Technology = Technology.COAL) =
        DispatchUnit(UnitId(id), tech, avail, Money.units(cost), prev, up, down, minStable)

    private fun variable(id: Long, cost: Long, avail: Long, tech: Technology = Technology.WIND) =
        DispatchUnit(UnitId(id), tech, avail, Money.units(cost), 0L, avail, avail, 0L)

    private fun run(demand: Long, vararg units: DispatchUnit, params: DispatchParams = DispatchParams(), extra: (DispatchInput) -> DispatchInput = { it }) =
        DispatchEngine.dispatch(extra(DispatchInput(demand, units.toList(), params = params)))

    private val trio = arrayOf(firm(1, 20, 100_000), firm(2, 40, 100_000), firm(3, 80, 100_000))

    @Test fun cheapestUnitsRunFirstAndTheDearestRunningUnitSetsThePrice() {
        val r = run(150_000, *trio)
        assertEquals(100_000L, r.outputOf(UnitId(1)))
        assertEquals(50_000L, r.outputOf(UnitId(2)))
        assertEquals(0L, r.outputOf(UnitId(3)))
        assertEquals(Money.units(40), r.price)
        assertEquals(UnitId(2), r.setter)
        assertEquals(0L, r.unservedKw)
    }

    @Test fun reserveIsWhatTheUnitsCouldStillAdd() {
        val r = run(150_000, *trio)
        assertEquals(150_000L, r.reserveKw)
        assertEquals(1_000_000L, r.reservePpm)
        assertFalse(r.scarcity)
    }

    @Test fun anExactlyFilledUnitStillSetsThePrice() {
        val r = run(100_000, *trio)
        assertEquals(UnitId(1), r.setter)
        assertEquals(Money.units(20), r.price)
    }

    @Test fun listOrderDoesNotMatter() {
        val a = run(230_000, *trio)
        val b = run(230_000, trio[2], trio[0], trio[1])
        assertEquals(a.units, b.units)
        assertEquals(a.price, b.price)
    }

    @Test fun equalCostsAreSplitByUnitIdInOrder() {
        val r = run(120_000, firm(7, 30, 100_000), firm(5, 30, 100_000))
        assertEquals(100_000L, r.outputOf(UnitId(5)))
        assertEquals(20_000L, r.outputOf(UnitId(7)))
    }

    @Test fun nothingLeftMeansScarcityAtTheCap() {
        val r = run(300_000, *trio)
        assertEquals(0L, r.unservedKw)
        assertEquals(0L, r.reserveKw)
        assertEquals(DispatchParams().priceCap, r.price)
        assertEquals(Money.units(80), r.basePrice)
        assertTrue(r.scarcity)
    }

    @Test fun unmetLoadIsUnservedAtTheCap() {
        val r = run(350_000, *trio)
        assertEquals(50_000L, r.unservedKw)
        assertEquals(300_000L, r.generationKw)
        assertEquals(300_000L, r.servedKw)
        assertEquals(DispatchParams().priceCap, r.price)
    }

    @Test fun thinReserveLiftsThePriceInProportion() {
        // 300 MW can run, load is 285 MW: reserve 15 MW is 5.26% of load, threshold 10%.
        val r = run(285_000, *trio)
        assertEquals(Money.units(80), r.basePrice)
        assertEquals(15_000L, r.reserveKw)
        // ratio = 15000 * 1e6 / 285000 = 52631; uplift = (500-80) * (100000 - 52631) / 100000
        val ratio = 15_000L * 1_000_000L / 285_000L
        val uplift = (Money.units(420).raw * (100_000L - ratio)) / 100_000L
        assertEquals(Money.units(80).raw + uplift, r.price.raw)
    }

    @Test fun scarcityCanBeSwitchedOff() {
        val r = run(299_000, *trio, params = DispatchParams(scarcityThresholdPpm = 0L))
        assertEquals(Money.units(80), r.price)
    }

    @Test fun rampLimitsHoldAUnitBackAndTheNextOneTakesOver() {
        val slow = firm(1, 20, 100_000, prev = 50_000, up = 20_000, down = 20_000)
        val r = run(120_000, slow, firm(2, 60, 100_000))
        assertEquals(70_000L, r.outputOf(UnitId(1)))
        assertEquals(50_000L, r.outputOf(UnitId(2)))
        assertEquals(Money.units(60), r.price)
    }

    @Test fun rampDownKeepsAFloorUnderDemand() {
        val slow = firm(1, 20, 100_000, prev = 80_000, up = 20_000, down = 20_000)
        val r = run(30_000, slow, firm(2, 60, 100_000, prev = 0))
        assertEquals(60_000L, r.outputOf(UnitId(1)))
        assertEquals(30_000L, r.surplusKw)
        assertEquals(Money.ZERO, r.price)
    }

    @Test fun variableUnitsAreCurtailedBeforeAFloorIsBroken() {
        val r = run(100_000, variable(1, 1, 80_000), firm(2, 30, 100_000, prev = 60_000, up = 10_000, down = 10_000))
        // the thermal unit cannot drop below 50 MW, so the wind farm only gets 50 MW
        assertEquals(50_000L, r.outputOf(UnitId(2)))
        assertEquals(50_000L, r.outputOf(UnitId(1)))
        assertEquals(30_000L, r.curtailedKw)
        assertEquals(UnitId(1), r.setter)
    }

    @Test fun windFillsLoadBeforeThermalAndTheThermalUnitSetsThePrice() {
        val r = run(150_000, variable(1, 1, 80_000), firm(2, 30, 100_000))
        assertEquals(80_000L, r.outputOf(UnitId(1)))
        assertEquals(70_000L, r.outputOf(UnitId(2)))
        assertEquals(Money.units(30), r.price)
        assertEquals(0L, r.curtailedKw)
    }

    @Test fun plentyOfWindDropsThePriceToTheWindUnitsCost() {
        val r = run(60_000, variable(1, 1, 80_000), firm(2, 30, 100_000, prev = 0))
        assertEquals(60_000L, r.outputOf(UnitId(1)))
        assertEquals(20_000L, r.curtailedKw)
        assertEquals(Money.units(1), r.price)
    }

    @Test fun anIdleUnitIsNotStartedForLessThanItsMinimumOutput() {
        val big = firm(1, 10, 100_000, prev = 0, up = 100_000, minStable = 50_000)
        val small = firm(2, 70, 100_000, prev = 0, up = 100_000)
        val r = run(30_000, big, small)
        assertEquals(0L, r.outputOf(UnitId(1)))
        assertEquals(30_000L, r.outputOf(UnitId(2)))
    }

    @Test fun anIdleUnitStartsOnceTheLoadReachesItsMinimum() {
        val big = firm(1, 10, 100_000, prev = 0, up = 100_000, minStable = 50_000)
        val r = run(60_000, big, firm(2, 70, 100_000, prev = 0, up = 100_000))
        assertEquals(60_000L, r.outputOf(UnitId(1)))
        assertEquals(0L, r.outputOf(UnitId(2)))
    }

    @Test fun anIdleUnitIsStartedAtItsMinimumRatherThanLeaveLoadUnserved() {
        val big = firm(1, 10, 100_000, prev = 0, up = 100_000, minStable = 50_000)
        val r = run(30_000, big)
        assertEquals(50_000L, r.outputOf(UnitId(1)))
        assertEquals(20_000L, r.surplusKw)
        assertEquals(0L, r.unservedKw)
    }

    @Test fun overshootFromAForcedStartIsTakenBackFromWind() {
        val big = firm(1, 10, 100_000, prev = 0, up = 100_000, minStable = 50_000)
        val wind = variable(2, 1, 60_000)
        val r = run(30_000 + 40_000, big.copy(marginalCost = Money.units(10)), wind)
        // wind takes 60 MW first, leaving 10 MW below the unit's 50 MW minimum, so the start is forced
        assertEquals(50_000L, r.outputOf(UnitId(1)))
        assertEquals(20_000L, r.outputOf(UnitId(2)))
        assertEquals(40_000L, r.curtailedKw)
        assertEquals(0L, r.surplusKw)
    }

    @Test fun aRunningUnitMayShutDownFromItsMinimumLevel() {
        val u = firm(1, 10, 100_000, prev = 40_000, up = 10_000, down = 10_000, minStable = 40_000)
        val r = run(0, u)
        assertEquals(0L, r.outputOf(UnitId(1)))
        assertEquals(0L, r.surplusKw)
    }

    @Test fun aRunningUnitAboveMinimumMustComeDownGradually() {
        val u = firm(1, 10, 100_000, prev = 90_000, up = 10_000, down = 20_000, minStable = 40_000)
        val r = run(0, u)
        assertEquals(70_000L, r.outputOf(UnitId(1)))
        assertEquals(70_000L, r.surplusKw)
    }

    @Test fun anUnavailableUnitProducesNothing() {
        val r = run(50_000, firm(1, 10, 0), firm(2, 50, 100_000))
        assertEquals(0L, r.outputOf(UnitId(1)))
        assertEquals(50_000L, r.outputOf(UnitId(2)))
    }

    @Test fun aDeratedUnitIsHeldToItsAvailability() {
        val r = run(80_000, firm(1, 10, 100_000, prev = 90_000, up = 5_000, down = 50_000).copy(availableKw = 60_000L), firm(2, 50, 100_000))
        assertEquals(60_000L, r.outputOf(UnitId(1)))
        assertEquals(20_000L, r.outputOf(UnitId(2)))
    }

    @Test fun storageDischargeIsTakenFirstAndChargingAddsLoad() {
        val d = run(100_000, *trio) { it.copy(storageDischargeKw = 30_000L) }
        assertEquals(70_000L, d.generationKw)
        val c = run(100_000, *trio) { it.copy(storageChargeKw = 20_000L) }
        assertEquals(120_000L, c.generationKw)
        assertEquals(Money.units(40), c.price)
    }

    @Test fun importsAndExportsMoveTheLoadTheUnitsCarry() {
        val i = run(150_000, *trio) { it.copy(importKw = 60_000L) }
        assertEquals(90_000L, i.generationKw)
        assertEquals(Money.units(20), i.price)
        val e = run(150_000, *trio) { it.copy(exportKw = 100_000L) }
        assertEquals(250_000L, e.generationKw)
        assertEquals(Money.units(80), e.price)
    }

    @Test fun suppliesLargerThanLoadAreCountedAsSurplus() {
        val r = run(10_000, *trio) { it.copy(importKw = 25_000L) }
        assertEquals(0L, r.generationKw)
        assertEquals(15_000L, r.surplusKw)
    }

    @Test fun noLoadMeansTheFloorPrice() {
        val r = run(0, *trio)
        assertEquals(Money.ZERO, r.price)
        assertNull(r.setter)
    }

    @Test fun priceStaysBetweenTheFloorAndTheCap() {
        val params = DispatchParams(priceCap = Money.units(50), priceFloor = Money.units(5))
        assertEquals(Money.units(50), run(300_000, *trio, params = params).price)
        assertEquals(Money.units(5), run(0, *trio, params = params).price)
    }

    @Test fun headroomAndNextCostDescribeWhatIsLeft() {
        val r = run(150_000, *trio)
        assertEquals(50_000L, r.headroomUpTo(Money.units(40)))
        assertEquals(150_000L, r.headroomUpTo(Money.units(80)))
        assertEquals(Money.units(40), r.nextCost())
        assertEquals(50_000L + 0L, r.outputAbove(Money.units(20)))
    }

    @Test fun outputByTechnologyAddsUp() {
        val r = run(150_000, variable(1, 1, 40_000), firm(2, 30, 200_000, tech = Technology.GAS))
        val by = r.outputByTech()
        assertEquals(40_000L, by[Technology.WIND.ordinal])
        assertEquals(110_000L, by[Technology.GAS.ordinal])
        assertEquals(r.generationKw, by.sum())
    }

    @Test fun generationAlwaysEqualsServedLoadPlusSurplus() {
        for (demand in listOf(0L, 1L, 49_999L, 100_000L, 250_000L, 299_999L, 300_000L, 340_000L)) {
            val r = run(demand, *trio)
            assertEquals(demand, r.generationKw + r.unservedKw - r.surplusKw)
        }
    }
}
