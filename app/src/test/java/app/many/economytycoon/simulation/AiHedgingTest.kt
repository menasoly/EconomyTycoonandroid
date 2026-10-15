package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import app.many.economytycoon.futures.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiHedgingTest {
    private class Rig(cash: Long = 2_000_000) {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        val wheat = GoodId("wheat")
        val hedger = AiHedging(e, CommandExecutor(e))
        val rival = CompanyId((sim.execute(Actor.System, Command.FoundCompany("Rival Farms", country.id, null, Money.units(cash), CompanyKind.AI)) as Outcome.Ok).value.created!!)
        val farm = e.production.grantFacility(rival, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
        /** The next day the company looks. */
        fun lookDay(after: Long = 0): Long { var t = after + 1; while ((t + rival.value) % 30L != 0L) t++; return t }
        fun stock(units: Long) { farm.inventory.add(wheat, Quantity.units(units), Money.units(units * 8)) }
    }

    @Test fun aCompanyHoldingStockSellsSomeForward() {
        val r = Rig(); r.stock(2_000)
        val report = r.hedger.run(Tick(r.lookDay()))
        assertEquals(1, report.hedges)
        val p = r.e.futures.openOf(r.rival).single()
        assertEquals(Side.SHORT, p.side); assertEquals(r.wheat, p.good)
        assertTrue(p.contracts in 1..r.e.futures.params.maxContracts / 4)
        assertTrue(p.contracts * r.e.futures.params.contractSize.raw <= 2_000L * 1000L)
    }

    @Test fun itHedgesAGoodOnlyOnce() {
        val r = Rig(); r.stock(2_000)
        val first = r.lookDay()
        r.hedger.run(Tick(first)); r.hedger.run(Tick(r.lookDay(first)))
        assertEquals(1, r.e.futures.openOf(r.rival).count { it.good == r.wheat })
    }

    @Test fun thereIsNoLookOutsideTheMonthlyDay() {
        val r = Rig(); r.stock(2_000)
        val day = r.lookDay()
        assertEquals(0, r.hedger.run(Tick(day + 1)).hedges)
        assertTrue(r.e.futures.all().isEmpty())
    }

    @Test fun aCashStarvedCompanyStaysOut() {
        val r = Rig(cash = 300_000); r.stock(2_000)
        val gov = app.many.economytycoon.finance.LedgerOwner.government(r.country.id)
        val owner = r.e.companies.company(r.rival)!!.owner
        val take = r.e.finance.ledger.cash(owner) - Money.units(50_000)
        r.e.finance.ledger.post(app.many.economytycoon.finance.TransactionDraft("drain", Tick(0), app.many.economytycoon.finance.ReasonCode.TRANSFER, listOf(
            app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(owner, app.many.economytycoon.finance.Chart.CASH), -take),
            app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(gov, app.many.economytycoon.finance.Chart.CASH), take)), "drain"))
        assertEquals(0, r.hedger.run(Tick(r.lookDay())).hedges)
    }

    @Test fun humanCompaniesAreNeverTraded() {
        val r = Rig()
        val me = CompanyId((r.sim.execute(Actor.Player(1), Command.FoundCompany("Human Farms", r.country.id, null, Money.units(2_000_000))) as Outcome.Ok).value.created!!)
        val farm = r.e.production.grantFacility(me, FacilityTypeId("grain_farm"), r.region, null, null, null, r.e.tick)
        farm.inventory.add(r.wheat, Quantity.units(2_000), Money.units(16_000))
        for (d in 1L..60L) r.hedger.run(Tick(d))
        assertTrue(r.e.futures.openOf(me).isEmpty())
    }

    @Test fun overTimeSomeRivalsPuntAndPositionsStayWithinLimits() {
        val r = Rig()
        val rivals = (1..8).map { i -> CompanyId((r.sim.execute(Actor.System, Command.FoundCompany("Punter $i", r.country.id, null, Money.units(3_000_000), CompanyKind.AI)) as Outcome.Ok).value.created!!) }
        var punts = 0
        for (d in 1L..360L) punts += r.hedger.run(Tick(d)).punts
        assertTrue(punts > 0)
        for (c in rivals + r.rival) assertTrue(r.e.futures.openOf(c).size <= r.e.futures.params.maxOpenPerCompany)
        assertFalse(r.e.futures.all().isEmpty())
    }
}
