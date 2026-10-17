package app.many.economytycoon.energygrid

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilityAiTest {
    private val company = CompanyId(7)
    private val prices = FuelPrices()

    private fun view(
        stance: UtilityStance = UtilityStance.BALANCED,
        reserve: Long = 300_000L,
        price: Long = 80,
        cash: Money = Money.units(5_000_000_000L),
        assets: List<UtilityAsset> = emptyList(),
        shortfall: Long = 0L,
        orders: Int = 0,
        buildable: Set<Technology> = GridParams().buildable(),
    ) = UtilityView(company, GridId(1), 100L, cash, stance, reserve, 150_000L, Money.units(price), prices, assets, buildable, shortfall, orders)

    private fun asset(id: Long, tech: Technology, age: Long = 100L, status: UnitStatus = UnitStatus.AVAILABLE, net: Long = 50, kw: Long = 100_000L, ppa: Boolean = false) =
        UtilityAsset(UnitId(id), tech, kw, age, status, Money.units(net), ppa)

    // ----- economics -----

    @Test fun expectedPriceRisesWhenReserveIsShort() {
        assertEquals(Money.units(80), UtilityAi.expectedPrice(view(reserve = 200_000L)))
        assertTrue(UtilityAi.expectedPrice(view(reserve = 50_000L)) > Money.units(80))
        assertEquals(Money.units(160), UtilityAi.expectedPrice(view(reserve = -500_000L)))
    }

    @Test fun grossEarningsFollowTheMarginAndTheLoadFactor() {
        val t = Technology.GAS
        val margin = Money.units(80) - t.marginalCost(prices)
        val expected = margin.raw * 8_640L / 1_000L * InvestmentPlanner.expectedLoadFactorPpm(t) / 1_000_000L
        assertTrue(Math.abs(expected - UtilityAi.grossPerKwYear(t, Money.units(80), prices).raw) <= 8_640L)
    }

    @Test fun nothingIsEarnedBelowTheRunningCost() {
        assertEquals(Money.ZERO, UtilityAi.grossPerKwYear(Technology.COAL, Money.ZERO, prices))
        assertNull(UtilityAi.paybackYears(Technology.COAL, Money.ZERO, prices))
    }

    @Test fun aHigherPriceShortensPayback() {
        val low = UtilityAi.paybackYears(Technology.WIND, Money.units(10), prices) ?: error("pays back")
        val high = UtilityAi.paybackYears(Technology.WIND, Money.units(40), prices) ?: error("pays back")
        assertTrue(high < low)
    }

    @Test fun cautiousStancesKeepMoreCash() {
        assertTrue(UtilityAi.cashCoverPpm(UtilityStance.CAUTIOUS) > UtilityAi.cashCoverPpm(UtilityStance.BOLD))
        assertTrue(UtilityAi.cashCoverPpm(UtilityStance.BOLD) >= 1_000_000L)
    }

    // ----- building -----

    @Test fun aShortGridGetsANewUnit() {
        val moves = UtilityAi.decide(view(reserve = 0L, price = 200, shortfall = 60_000L))
        val b = moves.filterIsInstance<UtilityMove.Build>().single()
        assertTrue(b.capacityKw >= 60_000L * 1_000_000L / b.tech.profile.capacityCreditPpm)
        assertTrue(b.tech in GridParams().buildable())
    }

    @Test fun aGridWithEnoughReserveAndACautiousUtilityBuildsNothing() {
        val moves = UtilityAi.decide(view(stance = UtilityStance.CAUTIOUS, reserve = 400_000L, price = 200))
        assertTrue(moves.none { it is UtilityMove.Build })
    }

    @Test fun anExpansiveUtilityBuildsEvenWhenTheGridIsFine() {
        val moves = UtilityAi.decide(view(stance = UtilityStance.BOLD, reserve = 400_000L, price = 250))
        assertTrue(moves.any { it is UtilityMove.Build })
    }

    @Test fun cheapPowerMakesNothingWorthBuilding() {
        assertTrue(UtilityAi.decide(view(reserve = 0L, price = 1, shortfall = 50_000L)).none { it is UtilityMove.Build })
    }

    @Test fun aBuildIsLimitedByCash() {
        val poor = UtilityAi.decide(view(reserve = 0L, price = 200, shortfall = 200_000L, cash = Money.units(60_000_000L)))
        val rich = UtilityAi.decide(view(reserve = 0L, price = 200, shortfall = 200_000L))
        val a = poor.filterIsInstance<UtilityMove.Build>().singleOrNull()
        val b = rich.filterIsInstance<UtilityMove.Build>().single()
        assertTrue(a == null || a.capacityKw < b.capacityKw)
    }

    @Test fun noBuildWithoutCashForTheMinimum() {
        assertTrue(UtilityAi.decide(view(reserve = 0L, price = 200, shortfall = 50_000L, cash = Money.units(1))).none { it is UtilityMove.Build })
    }

    @Test fun orderedUnitsAreNotOrderedTwice() {
        assertTrue(UtilityAi.decide(view(stance = UtilityStance.CAUTIOUS, reserve = 0L, price = 200, shortfall = 50_000L, orders = 1)).none { it is UtilityMove.Build })
    }

    @Test fun aSingleUnitNeverExceedsTheLimit() {
        val b = UtilityAi.decide(view(reserve = -300_000L, price = 250, shortfall = 5_000_000L)).filterIsInstance<UtilityMove.Build>().single()
        assertTrue(b.capacityKw <= 300_000L)
    }

    @Test fun onlyBuildableTechnologiesAreChosen() {
        val b = UtilityAi.decide(view(reserve = 0L, price = 250, shortfall = 50_000L, buildable = setOf(Technology.GAS))).filterIsInstance<UtilityMove.Build>().single()
        assertEquals(Technology.GAS, b.tech)
        assertTrue(UtilityAi.decide(view(reserve = 0L, price = 250, shortfall = 50_000L, buildable = emptySet())).none { it is UtilityMove.Build })
    }

    // ----- retiring and mothballing -----

    @Test fun wornLossMakingPlantIsRetiredWhenTheGridCanSpareIt() {
        val old = asset(1, Technology.COAL, age = Technology.COAL.profile.lifetimeTicks.toLong(), net = -20)
        val moves = UtilityAi.decide(view(assets = listOf(old, asset(2, Technology.GAS))))
        assertTrue(UtilityMove.Retire(UnitId(1)) in moves)
    }

    @Test fun wornPlantStaysWhenTheGridIsShort() {
        val old = asset(1, Technology.COAL, age = Technology.COAL.profile.lifetimeTicks.toLong(), net = -20)
        assertTrue(UtilityAi.decide(view(reserve = 20_000L, assets = listOf(old))).none { it is UtilityMove.Retire })
    }

    @Test fun profitableOldPlantIsKept() {
        val old = asset(1, Technology.COAL, age = Technology.COAL.profile.lifetimeTicks.toLong(), net = 30)
        assertTrue(UtilityAi.decide(view(assets = listOf(old))).none { it is UtilityMove.Retire })
    }

    @Test fun onlyOneUnitIsRetiredPerRound() {
        val life = Technology.COAL.profile.lifetimeTicks.toLong()
        val moves = UtilityAi.decide(view(assets = listOf(asset(1, Technology.COAL, life, net = -5), asset(2, Technology.COAL, life, net = -9))))
        assertEquals(listOf<UtilityMove>(UtilityMove.Retire(UnitId(2))), moves.filterIsInstance<UtilityMove.Retire>())
    }

    @Test fun youngLossMakingPlantIsMothballedInAGlut() {
        val moves = UtilityAi.decide(view(price = 5, assets = listOf(asset(1, Technology.GAS, net = -30))))
        assertTrue(UtilityMove.Mothball(UnitId(1)) in moves)
    }

    @Test fun nuclearAndStorageAreNeverMothballedByTheRule() {
        val moves = UtilityAi.decide(view(price = 5, assets = listOf(asset(1, Technology.NUCLEAR, net = -30), asset(2, Technology.STORAGE, net = -30))))
        assertTrue(moves.none { it is UtilityMove.Mothball })
    }

    @Test fun mothballedPlantComesBackWhenThePriceReturns() {
        val idle = asset(1, Technology.GAS, status = UnitStatus.MOTHBALLED, net = -5)
        assertTrue(UtilityAi.decide(view(price = 400, assets = listOf(idle))).contains(UtilityMove.Restart(UnitId(1))))
        assertFalse(UtilityAi.decide(view(price = 5, assets = listOf(idle))).contains(UtilityMove.Restart(UnitId(1))))
        assertTrue(UtilityAi.decide(view(price = 5, reserve = 0L, assets = listOf(idle))).contains(UtilityMove.Restart(UnitId(1))))
    }

    // ----- contracts -----

    @Test fun cautiousUtilitiesSeekAFixedPrice() {
        val moves = UtilityAi.decide(view(stance = UtilityStance.CAUTIOUS, assets = listOf(asset(3, Technology.GAS), asset(1, Technology.COAL))))
        val ppa = moves.filterIsInstance<UtilityMove.SeekPpa>().single()
        assertEquals(UnitId(1), ppa.unit)
        assertEquals(40_000L, ppa.volumeKw)
        assertTrue(ppa.strike >= Technology.COAL.marginalCost(prices))
    }

    @Test fun boldUtilitiesStayOnThePool() {
        assertTrue(UtilityAi.decide(view(stance = UtilityStance.BOLD, assets = listOf(asset(1, Technology.GAS)))).none { it is UtilityMove.SeekPpa })
    }

    @Test fun unitsAlreadyCoveredAreSkipped() {
        val moves = UtilityAi.decide(view(stance = UtilityStance.CAUTIOUS, assets = listOf(asset(1, Technology.COAL, ppa = true), asset(2, Technology.GAS))))
        assertEquals(UnitId(2), moves.filterIsInstance<UtilityMove.SeekPpa>().single().unit)
    }

    // ----- determinism and execution -----

    @Test fun theSameViewGivesTheSameMoves() {
        val v = view(reserve = 20_000L, price = 150, shortfall = 40_000L, assets = listOf(asset(1, Technology.COAL), asset(2, Technology.GAS)))
        assertEquals(UtilityAi.decide(v), UtilityAi.decide(v))
    }

    @Test fun movesAreExecutedAgainstTheService() {
        val w = GridWorld()
        w.unit(Technology.GAS, 100_000L)
        w.households(80_000L)
        val before = w.grid.units.size
        val v = UtilityAi.viewOf(w.state, w.grid, company, 10L, Money.units(5_000_000_000L), UtilityStance.BOLD, prices, 90_000L)
        val results = UtilityAi.execute(w.service, v, listOf(UtilityMove.Build(Technology.GAS, 50_000L), UtilityMove.SeekPpa(UnitId(1), 1_000L, Money.units(50), 30)))
        assertTrue(results[0].outcome?.isOk == true)
        assertNull(results[1].outcome)
        assertEquals(before + 1, w.grid.units.size)
        assertEquals(company, w.grid.units.last().owner)
    }

    @Test fun refusedMovesAreReported() {
        val w = GridWorld()
        val v = UtilityAi.viewOf(w.state, w.grid, company, 0L, Money.ZERO, UtilityStance.BOLD, prices, 0L)
        val r = UtilityAi.execute(w.service, v, listOf(UtilityMove.Retire(UnitId(999)), UtilityMove.Mothball(UnitId(999)), UtilityMove.Restart(UnitId(999))))
        assertTrue(r.all { it.outcome?.isRejected == true })
    }

    @Test fun theViewReadsTheCompanysOwnUnits() {
        val w = GridWorld()
        val mine = w.unit(Technology.GAS, 90_000L, owner = company)
        w.unit(Technology.COAL, 90_000L, owner = CompanyId(8))
        w.unit(Technology.WIND, 90_000L)
        w.households(100_000L)
        w.days(5)
        val v = UtilityAi.viewOf(w.state, w.grid, company, 5L, Money.units(1), UtilityStance.BALANCED, prices, 100_000L)
        assertEquals(listOf(mine.id), v.assets.map { it.unit })
        assertEquals(GridId(w.grid.id.value), v.grid)
        assertEquals(0, v.ordersOpen)
    }

    @Test fun aSimulatedUtilityBuildsItsWayOutOfAShortage() {
        val w = GridWorld()
        w.unit(Technology.GAS, 60_000L, owner = company)
        w.households(120_000L)
        w.facility(30_000L)
        w.days(3)
        var orders = 0
        for (round in 0 until 6) {
            val v = UtilityAi.viewOf(w.state, w.grid, company, w.service.state.lastTick, Money.units(50_000_000_000L), UtilityStance.BOLD, prices, w.service.peakDemandKw(w.grid.id))
            val r = UtilityAi.execute(w.service, v, UtilityAi.decide(v))
            orders += r.count { it.move is UtilityMove.Build && it.outcome?.isOk == true }
            w.days(40)
            GridInvariants.check(w.state)
        }
        assertTrue(orders >= 1)
        assertTrue(w.grid.units.count { it.owner == company } > 1)
    }
}
