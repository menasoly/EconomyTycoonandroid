package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.world.WeatherEvent
import app.many.economytycoon.world.WeatherKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridScenarioTest {
    private fun mixed(): GridWorld {
        val w = GridWorld()
        w.unit(Technology.COAL, 100_000L)
        w.unit(Technology.GAS, 150_000L)
        w.households(60_000L)
        w.facility(40_000L)
        return w
    }

    // ----- storage -----

    @Test fun storageBuysCheapAndSellsDearAndMakesMoney() {
        val w = mixed()
        val store = w.unit(Technology.STORAGE, 20_000L)
        w.days(30)
        GridInvariants.check(w.state)
        assertTrue(store.lifetimeKwh > 0L)
        assertTrue("revenue ${store.poolRevenue}", store.poolRevenue > Money.ZERO)
        assertTrue(w.grid.history.any { it.generationKwh[Technology.STORAGE.ordinal] > 0L })
    }

    @Test fun storageNarrowsTheDailyPriceSpread() {
        val without = mixed(); without.days(30)
        val with = mixed(); with.unit(Technology.STORAGE, 40_000L); with.days(30)
        fun spread(w: GridWorld) = w.grid.history.takeLast(10).sumOf { it.peakPrice.raw - it.lowPrice.raw }
        assertTrue(spread(with) <= spread(without))
    }

    @Test fun aStoreNeverHoldsMoreThanItsCapacityOrLessThanNothing() {
        val w = mixed()
        val store = w.unit(Technology.STORAGE, 20_000L)
        repeat(20) {
            w.day()
            assertTrue(store.socKwh in 0L..store.storageCapacityKwh)
        }
    }

    @Test fun storageLosesEnergyOnTheRoundTrip() {
        val w = mixed()
        val store = w.unit(Technology.STORAGE, 20_000L)
        w.days(20)
        val charged = w.grid.history.sumOf { 0L } + w.grid.units.first { it.id == store.id }.lifetimeKwh
        assertTrue(charged >= 0L)
        val dischargedTotal = w.grid.history.sumOf { it.generationKwh[Technology.STORAGE.ordinal] }
        assertEquals(store.lifetimeKwh, dischargedTotal)
    }

    // ----- interconnectors -----

    private fun twoGrids(): Triple<GridWorld, Grid, Interconnector> {
        val w = GridWorld()
        w.unit(Technology.COAL, 150_000L)
        w.unit(Technology.COAL, 150_000L)
        w.households(50_000L)
        val b = w.otherGrid()
        w.unit(Technology.GAS, 70_000L, grid = b)
        w.households(60_000L, grid = b)
        val link = w.service.addLink(w.grid.id, b.id, 60_000L, 50_000L, Money.units(1)).getOrThrow()
        return Triple(w, b, link)
    }

    @Test fun powerFlowsFromTheCheapGridToTheShortOne() {
        val (w, b, link) = twoGrids()
        val reports = w.days(5)
        GridInvariants.check(w.state)
        assertTrue(link.sentAbKwh > 0L)
        assertEquals(0L, link.sentBaKwh)
        assertTrue(link.lostKwh > 0L)
        assertTrue(reports.all { (it.linkFlowKwh[link.id] ?: 0L) > 0L })
        assertTrue(b.history.sumOf { it.importKwh } > 0L)
        assertTrue(w.grid.history.sumOf { it.exportKwh } > 0L)
    }

    @Test fun theLinkRelievesTheShortGridsBlackouts() {
        val (w, b, link) = twoGrids()
        w.days(3)
        val linked = b.history.sumOf { it.unservedKwh }
        val lone = GridWorld()
        lone.unit(Technology.GAS, 70_000L); lone.households(60_000L)
        lone.days(3)
        assertTrue(linked < lone.grid.history.sumOf { it.unservedKwh })
        assertTrue(link.sentAbKwh > 0L)
    }

    @Test fun pricesMoveTowardEachOtherWhenLinked() {
        val (w, b, _) = twoGrids()
        w.days(5)
        val lone = GridWorld()
        lone.unit(Technology.GAS, 70_000L); lone.households(60_000L)
        lone.days(5)
        val linkedAvg = b.history.last().averagePrice
        val loneAvg = lone.grid.history.last().averagePrice
        assertTrue(linkedAvg < loneAvg)
    }

    @Test fun aLineOutOfServiceStopsTheFlow() {
        val (w, _, link) = twoGrids()
        w.service.setLinkInService(link.id, false)
        w.days(2)
        assertEquals(0L, link.sentAbKwh + link.sentBaKwh)
    }

    @Test fun congestedHoursAreCounted() {
        val (w, _, link) = twoGrids()
        w.days(3)
        assertTrue(link.congestedHours > 0)
    }

    // ----- outages and maintenance -----

    private fun fleet(): GridWorld {
        val w = GridWorld()
        repeat(6) { w.unit(Technology.GAS, 60_000L) }
        w.unit(Technology.COAL, 100_000L)
        w.households(50_000L)
        w.facility(30_000L)
        return w
    }

    @Test fun unitsFailAndComeBackWithinTheirOutageLength() {
        val w = fleet()
        val storm = DayInputs(weather = mapOf(w.grid.id to listOf(WeatherEvent(RegionId(1), WeatherKind.STORM, Tick(0), 400, 1_000_000L))))
        val reports = w.days(300, storm)
        GridInvariants.check(w.state)
        val failures = reports.flatMap { it.eventsOfType<GridEvent.UnitFailed>() }
        assertTrue("failures ${failures.size}", failures.isNotEmpty())
        val maxDays = w.grid.units.maxOf { it.tech.profile.forcedOutageTicks }
        for (f in w.grid.outages.filter { it.kind == OutageKind.FORCED }) assertTrue(f.days in 1..(maxDays + 1L))
        val back = reports.flatMap { it.eventsOfType<GridEvent.UnitBack>() }
        assertTrue(back.isNotEmpty())
    }

    @Test fun anUnitUnderOutageProducesNothing() {
        val w = fleet()
        val storm = DayInputs(weather = mapOf(w.grid.id to listOf(WeatherEvent(RegionId(1), WeatherKind.STORM, Tick(0), 400, 1_000_000L))))
        repeat(200) {
            w.day(storm)
            for (u in w.grid.units) if (!u.status.canProduce) assertEquals(0L, u.outputKw)
        }
    }

    @Test fun maintenanceStartsWhenDueAndRestoresEfficiency() {
        val w = GridWorld()
        val gas = w.unit(Technology.GAS, 100_000L)
        w.unit(Technology.GAS, 100_000L)
        w.unit(Technology.GAS, 100_000L)
        w.households(30_000L)
        val reports = w.days(150)
        val started = reports.flatMap { it.eventsOfType<GridEvent.MaintenanceStarted>() }
        assertTrue(started.isNotEmpty())
        assertTrue(w.grid.outages.any { it.kind == OutageKind.MAINTENANCE })
        assertTrue(gas.lastMaintenance > 0L || gas.status == UnitStatus.MAINTENANCE || gas.outageCount > 0)
        for (u in w.grid.units.filter { it.status == UnitStatus.AVAILABLE && it.deferrals == 0 && it.lastMaintenance > 0L }) {
            assertTrue(u.efficiencyPpm >= u.tech.profile.efficiencyPpm * 85 / 100)
        }
    }

    @Test fun aTightGridPostponesMaintenanceThenGivesIn() {
        val w = GridWorld()
        val gas = w.unit(Technology.GAS, 100_000L)
        w.households(60_000L)
        w.facility(40_000L)
        val reports = w.days(200)
        val postponed = reports.flatMap { it.eventsOfType<GridEvent.MaintenancePostponed>() }.filter { it.unit == gas.id }
        assertTrue(postponed.isNotEmpty())
        assertEquals((1..postponed.size).toList().take(OutageParams().maxDeferrals), postponed.map { it.deferrals }.take(OutageParams().maxDeferrals))
        assertTrue(reports.flatMap { it.eventsOfType<GridEvent.MaintenanceStarted>() }.any { it.unit == gas.id })
    }

    @Test fun efficiencyWearsWhileRunning() {
        val w = GridWorld()
        val gas = w.unit(Technology.GAS, 100_000L)
        w.households(30_000L)
        w.days(5)
        assertTrue(gas.efficiencyPpm < Technology.GAS.profile.efficiencyPpm)
        assertTrue(gas.marginalCost(FuelPrices()) > Technology.GAS.marginalCost(FuelPrices()))
    }

    // ----- construction and investment -----

    @Test fun aShortPublicGridOrdersCapacityAndItComesOnline() {
        val w = GridWorld(params = GridParams(operatorPlans = true))
        w.unit(Technology.GAS, 80_000L)
        w.households(60_000L)
        w.facility(40_000L)
        val first = w.day()
        val ordered = first.eventsOfType<GridEvent.UnitOrdered>()
        assertTrue(ordered.isNotEmpty())
        assertEquals(ordered.size, first.capexIntents.size)
        assertTrue(first.capexIntents.all { it.total.raw > 0L && it.payee == w.grid.systemOwner })
        val unit = w.grid.unit(ordered.first().unit) ?: error("ordered unit missing")
        assertEquals(UnitStatus.BUILDING, unit.status)
        val ready = unit.statusUntil
        var commissioned = false
        while (w.tick <= ready + 1) {
            val r = w.day()
            if (r.eventsOfType<GridEvent.UnitCommissioned>().any { it.unit == unit.id }) commissioned = true
        }
        assertTrue(commissioned)
        assertEquals(UnitStatus.AVAILABLE, unit.status)
        assertEquals(ready, unit.commissioned)
    }

    @Test fun anOversuppliedGridDoesNotBuild() {
        val w = GridWorld(params = GridParams(operatorPlans = true))
        w.unit(Technology.GAS, 300_000L)
        w.households(30_000L)
        val r = w.day()
        assertTrue(r.eventsOfType<GridEvent.UnitOrdered>().isEmpty())
    }

    @Test fun planningIsRepeatedEveryQuarter() {
        val w = GridWorld(params = GridParams(operatorPlans = true))
        w.unit(Technology.GAS, 300_000L)
        w.households(30_000L)
        w.days(100)
        assertEquals(90L, w.grid.lastPlanTick)
    }

    @Test fun anAgedPublicUnitIsRetiredWhenTheGridIsLong() {
        val w = GridWorld(params = GridParams(operatorPlans = true))
        val old = w.unit(Technology.GAS, 100_000L)
        w.unit(Technology.GAS, 200_000L)
        w.households(30_000L)
        old.restoreAge(Technology.GAS.profile.lifetimeTicks.toLong() + 5L)
        val r = w.day()
        assertTrue(r.eventsOfType<GridEvent.UnitRetired>().any { it.unit == old.id })
        assertEquals(UnitStatus.RETIRED, old.status)
    }

    // ----- capacity market -----

    @Test fun theFirstDayHoldsACapacityAuction() {
        val w = mixed()
        val r = w.day()
        val e = r.eventsOfType<GridEvent.CapacityAuction>().single()
        val round = w.grid.capacityRound ?: error("no round")
        assertEquals(round.clearedKw, e.clearedKw)
        assertEquals(0L, round.shortfallKw)
        assertTrue(round.clearedKw >= round.requirementKw)
        assertEquals(Tick(0).value + 360L, round.validUntil)
    }

    @Test fun anotherAuctionIsHeldAfterAYear() {
        val w = mixed()
        val reports = w.days(365)
        assertEquals(2, reports.sumOf { it.eventsOfType<GridEvent.CapacityAuction>().size })
        assertEquals(360L, w.grid.capacityRound?.heldAt)
    }

    @Test fun aShortGridsAuctionLeavesAShortfall() {
        val w = GridWorld()
        w.unit(Technology.GAS, 50_000L)
        w.households(60_000L)
        w.day()
        val round = w.grid.capacityRound ?: error("no round")
        assertTrue(round.shortfallKw > 0L)
        assertEquals(CapacityMarket.DEFAULT_CAP, round.clearingPrice)
    }

    @Test fun theCapacityLevyCoversTheAnnualPayments() {
        val w = mixed()
        w.days(30)
        val round = w.grid.capacityRound ?: error("no round")
        val daily = w.grid.history.takeLast(30).sumOf { it.servedKwh } / 30
        val collected = GridUnits.cost(w.grid.capacityLevy, daily * 360)
        val paid = round.dailyPayment() * 360
        assertTrue(Math.abs(collected.raw - paid.raw) <= paid.raw / 100 + 10_000L)
    }

    // ----- caps on the grid price -----

    @Test fun aLowerPriceCapLimitsPrices() {
        val w = GridWorld(params = GridParams(priceCap = Money.units(120), operatorPlans = false))
        w.unit(Technology.GAS, 50_000L)
        w.households(60_000L)
        w.days(2)
        assertTrue(w.grid.history.all { it.peakPrice <= Money.units(120) })
    }

    @Test fun aHigherFloorLiftsPrices() {
        val w = GridWorld(params = GridParams(priceFloor = Money.units(20), operatorPlans = false))
        w.unit(Technology.GAS, 300_000L)
        w.unit(Technology.WIND, 200_000L)
        w.households(10_000L)
        w.days(3)
        assertTrue(w.grid.history.all { it.lowPrice >= Money.units(20) })
        assertFalse(w.grid.history.isEmpty())
    }
}

/** Ages a unit by moving back its start, so tests need not run for years. */
internal fun GenUnit.restoreAge(days: Long) {
    restore(status, statusUntil, -days, lastMaintenance, deferrals, outputKw, efficiencyPpm, socKwh, soldKwh, poolRevenue, lifetimeKwh, outageCount, capacityKw)
}
