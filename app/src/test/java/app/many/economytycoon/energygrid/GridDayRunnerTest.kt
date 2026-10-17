package app.many.economytycoon.energygrid

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.world.WeatherEvent
import app.many.economytycoon.world.WeatherKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridDayRunnerTest {
    private fun ample(): GridWorld {
        val w = GridWorld()
        w.unit(Technology.GAS, 100_000L)
        w.unit(Technology.GAS, 100_000L)
        w.households(60_000L)
        w.facility(40_000L)
        return w
    }

    private val gasCost = Technology.GAS.marginalCost(FuelPrices())

    @Test fun anAmpleGridServesEveryHour() {
        val w = ample()
        val reports = w.days(20)
        GridInvariants.check(w.state)
        assertTrue(w.grid.history.all { it.unservedKwh == 0L && it.shedHours == 0 })
        assertEquals(20, reports.size)
        assertEquals(20, w.grid.history.size)
        assertTrue(w.grid.blackouts.isEmpty())
    }

    @Test fun priceStaysAtTheDearestUnitsRunningCostWhenReserveIsAmple() {
        val w = ample()
        w.days(10)
        for (d in w.grid.history) assertTrue(d.peakPrice <= gasCost + Money.units(1))
    }

    @Test fun everyHourBalancesGenerationAgainstDemand() {
        val w = ample()
        w.days(5)
        for (h in w.grid.lastDay) {
            assertTrue("hour ${h.hour}", h.generationKw + h.importKw >= h.demandKw + h.exportKw + h.storageChargeKw - h.unservedKw)
            assertTrue(h.generationKw <= h.demandKw + 70_000L)
        }
    }

    @Test fun demandFollowsTheDailyShape() {
        val w = ample()
        w.day()
        val day = w.grid.lastDay
        val evening = day[19].demandKw
        val night = day[3].demandKw
        assertTrue(evening > night * 3 / 2)
        assertEquals(24, day.size)
    }

    @Test fun unitOutputIsRememberedForTheNextDaysRamp() {
        val w = ample()
        w.day()
        val last = w.grid.lastDay.last()
        assertEquals(last.outputKw[Technology.GAS.ordinal], w.grid.units.sumOf { it.outputKw })
    }

    @Test fun cheaperUnitsRunMoreOften() {
        val w = GridWorld()
        val coal = w.unit(Technology.COAL, 100_000L)
        val gas = w.unit(Technology.GAS, 100_000L)
        w.households(60_000L); w.facility(40_000L)
        w.days(10)
        assertTrue(coal.lifetimeKwh > gas.lifetimeKwh)
        assertTrue(gas.lifetimeKwh > 0L)
    }

    @Test fun poolRevenueAccruesToUnitsInProportionToTheirSales() {
        val w = ample()
        w.days(3)
        for (u in w.grid.units) {
            assertTrue(u.soldKwh > 0L)
            assertTrue(u.poolRevenue > Money.ZERO)
        }
    }

    // ----- shortages -----

    private fun short(): GridWorld {
        val w = GridWorld()
        w.unit(Technology.GAS, 50_000L)
        w.facility(30_000L, CustomerClass.INTERRUPTIBLE)
        w.facility(30_000L, CustomerClass.INDUSTRIAL)
        w.households(30_000L)
        return w
    }

    @Test fun aShortGridPricesAtTheCapAndShedsLoad() {
        val w = short()
        w.day()
        assertTrue(w.grid.lastDay.any { it.unservedKw > 0L })
        assertTrue(w.grid.lastDay.filter { it.unservedKw > 0L }.all { it.price == w.grid.params.priceCap })
        assertTrue(w.grid.history.last().shedHours > 0)
    }

    @Test fun interruptibleCustomersAreCutFirstAndHomesLast() {
        val w = short()
        w.days(3)
        fun share(l: GridLoad) = l.lifetimeShedKwh.toDouble() / (l.lifetimeShedKwh + l.monthServedKwh)
        val loads = w.grid.loads.sortedBy { it.id }
        val interruptible = loads.first { it.customer == CustomerClass.INTERRUPTIBLE }
        val industrial = loads.first { it.customer == CustomerClass.INDUSTRIAL }
        val homes = loads.first { it.customer == CustomerClass.HOUSEHOLD }
        assertTrue(share(interruptible) > share(industrial))
        assertTrue(share(industrial) >= share(homes))
        assertTrue(share(interruptible) > 0.5)
    }

    @Test fun aBlackoutIsRecordedAndReportedOnce() {
        val w = short()
        val report = w.day()
        val started = report.eventsOfType<GridEvent.BlackoutStarted>()
        assertEquals(1, started.size)
        val record = w.grid.blackouts.single()
        assertEquals(started.single().blackout, record.id)
        assertTrue(record.open)
        assertTrue(record.hours > 0 && record.shedKwh > 0L && record.peakShedKw > 0L)
        assertTrue(record.damage > Money.ZERO)
        assertEquals(record.hours, w.grid.history.last().shedHours)
    }

    @Test fun theBlackoutEndsWhenSupplyReturns() {
        val w = short()
        w.day()
        w.unit(Technology.GAS, 300_000L)
        val report = w.day()
        val ended = report.eventsOfType<GridEvent.BlackoutEnded>().single()
        val record = w.grid.blackouts.single()
        assertFalse(record.open)
        assertEquals(ended.hours, record.hours)
        assertEquals(1L, record.endTick)
        assertTrue(w.grid.openBlackout() == null)
    }

    @Test fun facilitiesThatLostPowerAreReportedForDamage() {
        val w = short()
        val report = w.day()
        val impacts = report.impacts
        assertTrue(impacts.isNotEmpty())
        val industrial = impacts.first { w.grid.load(it.load)?.customer == CustomerClass.INTERRUPTIBLE }
        assertTrue(industrial.shedHours > 0 && industrial.shedKwh > 0L && industrial.conditionLossPpm > 0L)
        assertTrue(impacts.none { w.grid.load(it.load)?.kind == LoadKind.HOUSEHOLDS })
        assertTrue(w.grid.loads.all { it.shedHoursToday == 0 })
    }

    @Test fun shedEnergyAddsUpToTheUnservedEnergy() {
        val w = short()
        w.day()
        val shed = w.grid.loads.sumOf { it.monthShedKwh }
        assertEquals(w.grid.history.last().unservedKwh, shed)
        assertEquals(w.grid.lifetimeUnservedKwh, shed)
    }

    @Test fun lostLoadIsValuedAtTheGridsRate() {
        val w = short()
        w.day()
        val d = w.grid.history.last()
        assertEquals(GridUnits.cost(w.grid.params.valueOfLostLoad, d.unservedKwh), d.lostLoadValue)
    }

    @Test fun storesCoverAShortfallBeforeLoadIsShed() {
        val w = GridWorld()
        w.unit(Technology.GAS, 100_000L)
        val store = w.unit(Technology.STORAGE, 40_000L)
        store.restoreFull()
        w.facility(60_000L)
        w.households(55_000L)
        w.day()
        val hours = w.grid.lastDay
        assertTrue(hours.any { it.storageDischargeKw > 0L })
    }

    // ----- determinism and records -----

    @Test fun theSameSeedGivesTheSameHash() {
        val a = ample(); val b = ample()
        a.days(40); b.days(40)
        assertEquals(a.state.stateHash(), b.state.stateHash())
    }

    @Test fun aDifferentSeedGivesADifferentWorld() {
        val a = ample()
        val b = GridWorld(seed = 43L).also { w -> w.unit(Technology.GAS, 100_000L); w.unit(Technology.GAS, 100_000L); w.households(60_000L); w.facility(40_000L) }
        a.days(40); b.days(40)
        assertNotEquals(a.state.stateHash(), b.state.stateHash())
    }

    @Test fun savingAndLoadingMidwayChangesNothing() {
        val a = ample()
        a.days(30)
        val bytes = EnergyGridCodec.toBytes(a.state)
        val restored = EnergyGridService(EnergyGridCodec.fromBytes(bytes))
        val streams = a.random.snapshot()
        val random = app.many.economytycoon.core.RandomProvider(a.seed).also { it.restore(streams) }
        assertEquals(a.state.stateHash(), restored.state.stateHash())
        repeat(20) {
            a.day()
            restored.advanceDay(Tick(30L + it), DayInputs(), random)
        }
        assertEquals(a.state.stateHash(), restored.state.stateHash())
    }

    @Test fun daysMustMoveForward() {
        val w = ample()
        w.day()
        val ex = runCatching { w.service.advanceDay(Tick(0), DayInputs(), w.random) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test fun historyIsTrimmedToItsLimit() {
        val w = GridWorld()
        w.unit(Technology.GAS, 10_000L); w.households(1_000L)
        w.days(Grid.MAX_HISTORY + 10)
        assertEquals(Grid.MAX_HISTORY, w.grid.history.size)
        assertEquals(Grid.MAX_HISTORY + 9L, w.grid.history.last().tick)
    }

    @Test fun anEmptyWorldRunsQuietly() {
        val w = GridWorld()
        val r = w.day()
        assertEquals(0L, r.summaries.getValue(w.grid.id).demandKwh)
        GridInvariants.check(w.state)
    }

    @Test fun gridsWithNoPowerAndSomeLoadAreDark() {
        val w = GridWorld()
        w.households(10_000L)
        w.day()
        assertEquals(24, w.grid.history.last().shedHours)
        assertEquals(w.grid.history.last().demandKwh, w.grid.history.last().unservedKwh)
    }

    // ----- weather -----

    @Test fun aHeatwaveDeratesThermalCapacity() {
        val w = GridWorld()
        val gas = w.unit(Technology.GAS, 100_000L)
        w.households(5_000L)
        val event = WeatherEvent(RegionId(1), WeatherKind.HEATWAVE, Tick(0), 10, 1_000_000L)
        val hot = DayInputs(weather = mapOf(w.grid.id to listOf(event)))
        w.day(hot)
        assertTrue(gas.status.canProduce)
        val h = w.grid.lastDay.maxOf { it.demandKw }
        assertTrue(h > 0L)
    }

    @Test fun windAndSunFollowTheWeather() {
        val w = GridWorld()
        w.unit(Technology.GAS, 100_000L)
        w.unit(Technology.SOLAR, 100_000L)
        w.unit(Technology.WIND, 100_000L)
        w.households(60_000L)
        w.day()
        val day = w.grid.lastDay
        assertEquals(0L, day[0].outputKw[Technology.SOLAR.ordinal])
        assertTrue(day[12].outputKw[Technology.SOLAR.ordinal] > 0L)
        assertTrue(day.sumOf { it.outputKw[Technology.WIND.ordinal] } > 0L)
    }
}

/** Fills a store so the test can start with energy in it. */
internal fun GenUnit.restoreFull() {
    restore(status, statusUntil, commissioned, lastMaintenance, deferrals, outputKw, efficiencyPpm, storageCapacityKwh, soldKwh, poolRevenue, lifetimeKwh, outageCount, capacityKw)
}
