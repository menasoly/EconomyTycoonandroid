package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestmentPlannerTest {
    private val prices = FuelPrices()
    private val thermal = setOf(Technology.COAL, Technology.GAS, Technology.NUCLEAR)

    private fun view(
        peak: Long = 100_000L, growth: Long = 100_000L, firm: Long = 100_000L, building: Long = 0L,
        byTech: Map<Technology, Long> = mapOf(Technology.COAL to 50_000L, Technology.GAS to 50_000L),
        candidates: List<RetireCandidate> = emptyList(), buildable: Set<Technology> = thermal,
    ) = PlanningView(
        peak, growth, firm, building, Technology.values().map { byTech[it] ?: 0L }, candidates, 150_000L, buildable, prices,
    )

    @Test fun aShortGridOrdersCapacityFromTheCheapestFirmTechnology() {
        val r = InvestmentPlanner.plan(view())
        // forecast peak 110 MW, requirement 126.5 MW, firm 100 MW
        assertEquals(126_500L, r.requirementKw)
        assertEquals(26_500L, r.shortfallKw)
        val cheapest = thermal.minByOrNull { InvestmentPlanner.costPerFirmKwYear(it, prices).raw }
        assertEquals(1, r.builds.size)
        assertEquals(cheapest, r.builds.single().tech)
        assertTrue(CapacityMarket.firmKw(r.builds.single().tech, r.builds.single().capacityKw) >= 26_500L)
    }

    @Test fun gasIsTheDefaultChoice() {
        val r = InvestmentPlanner.plan(view(byTech = mapOf(Technology.COAL to 50_000L, Technology.GAS to 40_000L)))
        assertEquals(Technology.GAS, r.builds.first().tech)
    }

    @Test fun noTechnologyTakesTooLargeAShareOfTheFleet() {
        val r = InvestmentPlanner.plan(view(byTech = mapOf(Technology.GAS to 90_000L, Technology.COAL to 10_000L)))
        assertTrue(r.builds.first().tech != Technology.GAS)
    }

    @Test fun capacityAlreadyOnOrderCountsTowardTheNeed() {
        val r = InvestmentPlanner.plan(view(building = 30_000L))
        assertEquals(0L, r.shortfallKw)
        assertTrue(r.builds.isEmpty())
    }

    @Test fun ordersAreSplitIntoUnitsOfALimitedSize() {
        val r = InvestmentPlanner.plan(view(peak = 1_000_000L, firm = 500_000L))
        assertTrue(r.builds.size > 1)
        assertTrue(r.builds.all { it.capacityKw <= 300_000L })
    }

    @Test fun noBuildableTechnologyMeansNoOrders() {
        val r = InvestmentPlanner.plan(view(buildable = emptySet()))
        assertTrue(r.builds.isEmpty())
        assertEquals(26_500L, r.shortfallKw)
    }

    @Test fun storageIsNeverOrderedAsFirmCapacity() {
        val r = InvestmentPlanner.plan(view(buildable = setOf(Technology.STORAGE)))
        assertTrue(r.builds.isEmpty())
    }

    @Test fun reserveMarginIsReportedAgainstTheForecastPeak() {
        // firm 100 vs forecast 110 -> -9.09%
        assertEquals(-90_910L, InvestmentPlanner.plan(view()).reserveMarginPpm)
        assertEquals(500_000L, InvestmentPlanner.plan(view(growth = 0L, firm = 150_000L)).reserveMarginPpm)
    }

    @Test fun aLongGridRetiresItsWornestUnit() {
        val life = Technology.COAL.profile.lifetimeTicks.toLong()
        val old = RetireCandidate(UnitId(1), Technology.COAL, life, 20_000L, Money.units(40))
        val older = RetireCandidate(UnitId(2), Technology.COAL, life * 12 / 10, 20_000L, Money.units(35))
        val young = RetireCandidate(UnitId(3), Technology.COAL, life / 2, 20_000L, Money.units(60))
        val r = InvestmentPlanner.plan(view(firm = 200_000L, candidates = listOf(old, young, older)))
        assertEquals(listOf(UnitId(2)), r.retire)
        assertTrue(r.builds.isEmpty())
    }

    @Test fun aUnitIsNotRetiredIfThatWouldLeaveTheGridShort() {
        val life = Technology.COAL.profile.lifetimeTicks.toLong()
        val old = RetireCandidate(UnitId(1), Technology.COAL, life, 60_000L, Money.units(40))
        val r = InvestmentPlanner.plan(view(firm = 140_000L, candidates = listOf(old)))
        assertTrue(r.retire.isEmpty())
    }

    @Test fun nothingIsRetiredWhileTheGridIsShort() {
        val life = Technology.COAL.profile.lifetimeTicks.toLong()
        val old = RetireCandidate(UnitId(1), Technology.COAL, life, 1_000L, Money.units(40))
        assertTrue(InvestmentPlanner.plan(view(candidates = listOf(old))).retire.isEmpty())
    }

    @Test fun youngUnitsAreNeverRetired() {
        val young = RetireCandidate(UnitId(3), Technology.COAL, 10L, 20_000L, Money.units(60))
        assertTrue(InvestmentPlanner.plan(view(firm = 300_000L, candidates = listOf(young))).retire.isEmpty())
    }

    @Test fun fuelPricesChangeWhichTechnologyIsCheapest() {
        val gasPricey = prices.copy(gas = Money.units(120))
        val cheap = thermal.minByOrNull { InvestmentPlanner.costPerFirmKwYear(it, prices).raw }
        val dear = thermal.minByOrNull { InvestmentPlanner.costPerFirmKwYear(it, gasPricey).raw }
        assertEquals(Technology.GAS, cheap)
        assertTrue(dear != Technology.GAS)
    }

    @Test fun aCarbonPriceRaisesEvenTheFirmCostOfSolarSlightly() {
        val solar = InvestmentPlanner.costPerFirmKwYear(Technology.SOLAR, prices).raw
        val solarAlone = InvestmentPlanner.costPerFirmKwYear(Technology.SOLAR, prices.copy(carbonPerTonne = Money.units(100))).raw
        assertTrue(solarAlone > solar)
        assertTrue(InvestmentPlanner.expectedLoadFactorPpm(Technology.NUCLEAR) > InvestmentPlanner.expectedLoadFactorPpm(Technology.GAS))
    }

    @Test fun planningIsRepeatable() {
        assertEquals(InvestmentPlanner.plan(view()), InvestmentPlanner.plan(view()))
    }
}
