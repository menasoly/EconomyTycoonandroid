package app.many.economytycoon.automation

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.simulation.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagersTest {
    @Test fun maintenanceLeavesHealthyBuildingsAlone() {
        val r = AutomationRig(); r.farm()
        assertTrue(Managers.maintenance(r.ctx(r.on(ManagerKind.MAINTENANCE))).isEmpty())
    }

    @Test fun maintenanceRepairsWornBuildings() {
        val r = AutomationRig(); val f = r.farm()
        f.conditionPpm = 400_000L
        val plan = Managers.maintenance(r.ctx(r.on(ManagerKind.MAINTENANCE)))
        assertEquals(1, plan.size)
        assertTrue(plan[0].command is Command.Maintain)
        assertTrue(r.run(plan).all { it is Outcome.Ok })
        assertTrue(f.conditionPpm > 400_000L)
    }

    @Test fun maintenanceRespectsTheReserve() {
        val r = AutomationRig(Money.units(100_000)); val f = r.farm()
        f.conditionPpm = 300_000L
        val tight = r.on(ManagerKind.MAINTENANCE).copy(reserve = Money.units(10_000_000))
        assertTrue(Managers.maintenance(r.ctx(tight)).isEmpty())
    }

    @Test fun worstBuildingIsRepairedFirstWhenMoneyIsShort() {
        val r = AutomationRig(); val a = r.farm(); val b = r.farm()
        a.conditionPpm = 600_000L; b.conditionPpm = 200_000L
        val plan = Managers.maintenance(r.ctx(r.on(ManagerKind.MAINTENANCE)))
        assertEquals(2, plan.size)
        assertEquals(b.id, (plan[0].command as Command.Maintain).facility)
    }

    @Test fun repairThresholdIsConfigurable() {
        val r = AutomationRig(); val f = r.farm()
        f.conditionPpm = 700_000L
        assertEquals(1, Managers.maintenance(r.ctx(r.on(ManagerKind.MAINTENANCE))).size)
        assertTrue(Managers.maintenance(r.ctx(r.on(ManagerKind.MAINTENANCE).copy(repairBelowPpm = 500_000L))).isEmpty())
    }

    @Test fun wasteIsClearedOnceItPilesUp() {
        val r = AutomationRig(); val f = r.farm()
        assertTrue(Managers.waste(r.ctx(r.on(ManagerKind.WASTE))).isEmpty())
        f.inventory.add(GoodId("organic_waste"), Quantity.units(50), Money.ZERO)
        val plan = Managers.waste(r.ctx(r.on(ManagerKind.WASTE)))
        assertEquals(1, plan.size)
        assertTrue(plan[0].command is Command.DisposeWaste)
        assertEquals(50L, Managers.wasteUnits(r.ctx(r.on(ManagerKind.WASTE)), f))
    }

    @Test fun smallWasteHeapsAreIgnored() {
        val r = AutomationRig(); val f = r.farm()
        f.inventory.add(GoodId("organic_waste"), Quantity.units(5), Money.ZERO)
        assertTrue(Managers.waste(r.ctx(r.on(ManagerKind.WASTE))).isEmpty())
        assertEquals(1, Managers.waste(r.ctx(r.on(ManagerKind.WASTE).copy(wasteUnits = 3L))).size)
    }

    @Test fun nothingToSellWithoutARecipe() {
        val r = AutomationRig(); r.farm()
        assertTrue(Managers.selling(r.ctx(r.on(ManagerKind.SELLING))).isEmpty())
    }

    @Test fun sellingCreatesRulesForOutputsOnce() {
        val r = AutomationRig(); val f = r.farm()
        val recipe = r.e.content.recipesForFacility(f.type).first()
        f.lines.first().recipe = recipe.id
        val first = Managers.selling(r.ctx(r.on(ManagerKind.SELLING)))
        assertTrue(first.isNotEmpty())
        assertTrue(r.run(first).all { it is Outcome.Ok })
        assertTrue(Managers.selling(r.ctx(r.on(ManagerKind.SELLING))).isEmpty())
    }

    @Test fun staffingRaisesTargetsForRunningLines() {
        val r = AutomationRig(); val f = r.farm()
        f.lines.first().recipe = r.e.content.recipesForFacility(f.type).first().id
        val plan = Managers.staffing(r.ctx(r.on(ManagerKind.STAFFING)))
        assertTrue(plan.isNotEmpty())
        assertTrue(r.run(plan).all { it is Outcome.Ok })
        assertTrue(Managers.staffing(r.ctx(r.on(ManagerKind.STAFFING))).isEmpty())
    }

    @Test fun staffingIgnoresIdleFacilities() {
        val r = AutomationRig(); r.farm()
        assertTrue(Managers.staffing(r.ctx(r.on(ManagerKind.STAFFING))).isEmpty())
    }

    @Test fun researchPicksTheCheapestAvailable() {
        val r = AutomationRig()
        val plan = Managers.research(r.ctx(r.on(ManagerKind.RESEARCH)))
        if (plan.isEmpty()) return
        val picked = r.e.research.available(r.company).minOf { it.researchCost.raw }
        val cmd = plan[0].command as Command.StartResearch
        assertEquals(picked, r.e.research.available(r.company).first { it.id == cmd.tech }.researchCost.raw)
    }

    @Test fun researchFocusIsPreferred() {
        val r = AutomationRig()
        val options = r.e.research.available(r.company)
        val focus = options.map { it.category }.distinct().firstOrNull() ?: return
        val plan = Managers.research(r.ctx(r.on(ManagerKind.RESEARCH).copy(researchFocus = focus)))
        val cmd = plan.first().command as Command.StartResearch
        assertEquals(focus, r.e.research.available(r.company).first { it.id == cmd.tech }.category)
    }

    @Test fun repaymentNeedsALoan() {
        val r = AutomationRig()
        assertTrue(Managers.repayment(r.ctx(r.on(ManagerKind.REPAYMENT))).isEmpty())
    }

    @Test fun repaymentPaysTheDearestLoan() {
        val r = AutomationRig(Money.units(2_000_000))
        val owner = r.e.companies.require(r.company).owner
        r.e.finance.mint(r.e.tick, r.e.finance.bank, Money.units(5_000_000), "test")
        r.e.finance.borrow(r.e.tick, owner, Money.units(100_000), 360)
        val plan = Managers.repayment(r.ctx(r.on(ManagerKind.REPAYMENT)))
        assertEquals(1, plan.size)
        assertTrue(plan[0].command is Command.Repay)
        assertTrue(r.run(plan).all { it is Outcome.Ok })
    }

    @Test fun repaymentKeepsTheReserve() {
        val r = AutomationRig(Money.units(200_000))
        val owner = r.e.companies.require(r.company).owner
        r.e.finance.mint(r.e.tick, r.e.finance.bank, Money.units(5_000_000), "test")
        r.e.finance.borrow(r.e.tick, owner, Money.units(100_000), 360)
        val tight = r.on(ManagerKind.REPAYMENT).copy(reserve = Money.units(100_000_000))
        assertTrue(Managers.repayment(r.ctx(tight)).isEmpty())
    }

    @Test fun planDispatchesToEveryKind() {
        val r = AutomationRig()
        for (k in ManagerKind.values()) assertTrue(Managers.plan(k, r.ctx(r.on(k))).all { it.kind == k })
    }
}
