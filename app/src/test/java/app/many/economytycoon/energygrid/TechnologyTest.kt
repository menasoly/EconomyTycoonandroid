package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnologyTest {
    private val prices = FuelPrices()

    @Test fun runningCostsAddFuelOtherCostsAndCarbon() {
        // coal: 12 / 0.38 = 31.5790 (rounded up) + 4
        assertEquals(Money(355_790L), Technology.COAL.marginalCost(prices))
        assertEquals(Money(590_000L), Technology.GAS.marginalCost(prices))
        assertEquals(Money(261_819L), Technology.NUCLEAR.marginalCost(prices))
        assertEquals(Money(10_000L), Technology.WIND.marginalCost(prices))
        assertEquals(Money(5_000L), Technology.SOLAR.marginalCost(prices))
    }

    @Test fun aCarbonPriceIsChargedPerTonneEmitted() {
        val taxed = prices.copy(carbonPerTonne = Money.units(50))
        // 950 kg per MWh at 50 a tonne is 47.5 on top
        assertEquals(Money(355_790L + 475_000L), Technology.COAL.marginalCost(taxed))
        assertEquals(Money(590_000L + 200_000L), Technology.GAS.marginalCost(taxed))
        assertEquals(Technology.WIND.marginalCost(prices) + Money(5_500L), Technology.WIND.marginalCost(taxed))
    }

    @Test fun lowerEfficiencyCostsMoreFuel() {
        val worn = Technology.GAS.marginalCost(prices, 450_000L)
        assertTrue(worn > Technology.GAS.marginalCost(prices))
    }

    @Test fun normalOrderOfRunningCostsIsNuclearCoalGas() {
        val c = Technology.COAL.marginalCost(prices)
        val g = Technology.GAS.marginalCost(prices)
        val n = Technology.NUCLEAR.marginalCost(prices)
        assertTrue(n < c && c < g)
    }

    @Test fun rampsAndMinimumsScaleWithCapacity() {
        assertEquals(30_000L, Technology.GAS.rampUpKw(100_000L))
        assertEquals(30_000L, Technology.GAS.rampDownKw(100_000L))
        assertEquals(40_000L, Technology.COAL.minStableKw(100_000L))
        assertEquals(60_000L, Technology.NUCLEAR.minStableKw(100_000L))
        assertEquals(100_000L, Technology.WIND.rampUpKw(100_000L))
    }

    @Test fun profilesAreSane() {
        for (t in Technology.values()) {
            val p = t.profile
            assertTrue(t.name, p.rampUpPpmPerHour in 1L..1_000_000L)
            assertTrue(t.name, p.minStablePpm in 0L..1_000_000L)
            assertTrue(t.name, p.capacityCreditPpm in 1L..1_000_000L)
            assertTrue(t.name, p.buildTicks > 0 && p.lifetimeTicks > p.buildTicks)
            assertTrue(t.name, p.maintenanceEveryTicks > p.maintenanceTicks)
            assertEquals(t.name, t.isThermal, p.efficiencyPpm > 0L)
        }
    }

    @Test fun onlyStorageHasARoundTripAndHours() {
        for (t in Technology.values()) {
            assertEquals(t.isStorage, t.profile.roundTripPpm > 0L)
            assertEquals(t.isStorage, t.profile.storageHours > 0)
        }
    }

    @Test fun windAndSunAreVariableAndTheRestAreDispatchable() {
        assertTrue(Technology.WIND.isVariable && Technology.SOLAR.isVariable)
        assertFalse(Technology.HYDRO.isVariable)
        assertTrue(Technology.WIND.isRenewable && Technology.HYDRO.isRenewable && !Technology.COAL.isRenewable)
    }

    @Test fun carbonIntensityOrdersCoalOverGasOverNuclear() {
        assertTrue(Technology.COAL.profile.carbonKgPerMwh > Technology.GAS.profile.carbonKgPerMwh)
        assertTrue(Technology.GAS.profile.carbonKgPerMwh > Technology.NUCLEAR.profile.carbonKgPerMwh)
    }

    @Test fun energyAndCostConversionsUseMegawattHours() {
        assertEquals(Money.units(50), GridUnits.cost(Money.units(50), 1_000L))
        assertEquals(Money.units(25), GridUnits.cost(Money.units(50), 500L))
        assertEquals(Money.units(50), GridUnits.priceOf(Money.units(25), 500L))
        assertEquals(Money.ZERO, GridUnits.priceOf(Money.units(25), 0L))
        assertEquals(3L, GridUnits.mwh(3_999L))
    }

    @Test fun fuelPricesAreLookedUpByKind() {
        assertEquals(Money.units(12), prices.of(FuelKind.COAL))
        assertEquals(Money.units(28), prices.of(FuelKind.GAS))
        assertEquals(Money.units(6), prices.of(FuelKind.URANIUM))
        assertEquals(Money.ZERO, prices.of(FuelKind.NONE))
    }
}
