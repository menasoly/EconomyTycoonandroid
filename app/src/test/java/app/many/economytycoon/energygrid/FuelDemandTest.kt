package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelDemandTest {
    private val prices = FuelPrices(coal = Money.units(10), gas = Money.units(30), uranium = Money.units(5))

    private fun gen(vararg p: Pair<Technology, Long>): List<Long> {
        val a = LongArray(Technology.values().size)
        for ((t, v) in p) a[t.ordinal] = v
        return a.toList()
    }

    private fun day(g: List<Long>, demand: Long = g.sum(), carbon: Long = 0L) = DaySummary(
        0L, Money.units(50), Money.units(50), Money.units(50), demand, 0L, 0L, 1L, g, 0L, 0L, 0, 0, carbon, 0L, Money.ZERO,
    )

    @Test fun heatIsEnergyOverEfficiency() {
        val eff = Technology.GAS.profile.efficiencyPpm
        val heat = FuelDemand.heatKwh(Technology.GAS, 1_000_000L)
        assertTrue(heat >= 1_000_000L)
        assertTrue(Math.abs(1_000_000L * 1_000_000L / eff - heat) <= 1L)
    }

    @Test fun plantsWithoutFuelBurnNothing() {
        for (t in listOf(Technology.WIND, Technology.SOLAR, Technology.HYDRO, Technology.STORAGE)) assertEquals(0L, FuelDemand.heatKwh(t, 5_000_000L))
    }

    @Test fun nothingMadeNeedsNothing() {
        assertEquals(0L, FuelDemand.heatKwh(Technology.COAL, 0L))
        assertEquals(0L, FuelDemand.heatKwh(Technology.COAL, -5L))
        assertEquals(0L, FuelDemand.heatKwh(Technology.COAL, 100L, efficiencyPpm = 0L))
    }

    @Test fun aWornPlantNeedsMoreHeat() {
        val base = Technology.COAL.profile.efficiencyPpm
        assertTrue(FuelDemand.heatKwh(Technology.COAL, 1_000_000L, base - 50_000L) > FuelDemand.heatKwh(Technology.COAL, 1_000_000L, base))
    }

    @Test fun aDayIsSplitByFuel() {
        val uses = FuelDemand.ofDay(day(gen(Technology.COAL to 2_000_000L, Technology.GAS to 1_000_000L, Technology.WIND to 9_000_000L)), prices)
        assertEquals(listOf(FuelKind.COAL, FuelKind.GAS, FuelKind.URANIUM), uses.map { it.fuel })
        assertEquals(FuelDemand.heatKwh(Technology.COAL, 2_000_000L), uses[0].heatKwh)
        assertEquals(GridUnits.cost(Money.units(30), uses[1].heatKwh), uses[1].cost)
        assertEquals(0L, uses[2].heatKwh)
        assertEquals(Money.ZERO, uses[2].cost)
    }

    @Test fun aWindowAddsTheDays() {
        val h = listOf(day(gen(Technology.GAS to 1_000_000L)), day(gen(Technology.GAS to 3_000_000L)))
        val two = FuelDemand.ofWindow(h, 2, prices).first { it.fuel == FuelKind.GAS }
        val one = FuelDemand.ofWindow(h, 1, prices).first { it.fuel == FuelKind.GAS }
        assertTrue(Math.abs(FuelDemand.heatKwh(Technology.GAS, 4_000_000L) - two.heatKwh) <= 1L)
        assertEquals(FuelDemand.heatKwh(Technology.GAS, 3_000_000L), one.heatKwh)
        assertTrue(FuelDemand.ofWindow(emptyList(), 5, prices).all { it.heatKwh == 0L })
    }

    @Test fun fuelCostPerMwhServedFollowsTheMix() {
        val gas = listOf(day(gen(Technology.GAS to 1_000_000L)))
        val wind = listOf(day(gen(Technology.WIND to 1_000_000L)))
        assertTrue(FuelDemand.costPerMwhServed(gas, 1, prices).raw > 0L)
        assertEquals(Money.ZERO, FuelDemand.costPerMwhServed(wind, 1, prices))
        assertEquals(Money.ZERO, FuelDemand.costPerMwhServed(emptyList(), 1, prices))
    }

    @Test fun costPerMwhEqualsFuelPriceOverEfficiencyForAPureFleet() {
        val h = listOf(day(gen(Technology.GAS to 1_000_000L)))
        val expected = Technology.GAS.marginalCost(prices).raw - Technology.GAS.profile.variableOmPerMwh.raw
        assertTrue(Math.abs(FuelDemand.costPerMwhServed(h, 1, prices).raw - expected) <= 2L)
    }

    @Test fun theCarbonBillIsTonnesTimesThePrice() {
        val h = listOf(day(gen(Technology.COAL to 1L), carbon = 5_000L), day(gen(Technology.COAL to 1L), carbon = 3_000L))
        assertEquals(Money.units(80), FuelDemand.carbonBill(h, 2, Money.units(10)))
        assertEquals(Money.units(30), FuelDemand.carbonBill(h, 1, Money.units(10)))
        assertEquals(Money.ZERO, FuelDemand.carbonBill(h, 2, Money.ZERO))
    }

    @Test(expected = IllegalArgumentException::class) fun aDayNeedsOneTotalPerTechnology() {
        day(listOf(1L, 2L))
    }

    @Test fun aRealRunBurnsFuelForItsThermalOutput() {
        val w = richWorld(15)
        val uses = FuelDemand.ofWindow(w.grid.history, 15, prices)
        val thermal = w.grid.history.takeLast(15).sumOf { d -> Technology.values().filter { it.isThermal }.sumOf { d.generationKwh[it.ordinal] } }
        assertEquals(thermal > 0L, uses.sumOf { it.heatKwh } > 0L)
        assertTrue(uses.sumOf { it.heatKwh } >= thermal)
    }
}
