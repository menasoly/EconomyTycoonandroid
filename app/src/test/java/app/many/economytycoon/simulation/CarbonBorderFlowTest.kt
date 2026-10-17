package app.many.economytycoon.simulation

import app.many.economytycoon.carbon.BorderPolicy
import app.many.economytycoon.carbon.CarbonCalendar
import app.many.economytycoon.carbon.RegimePresets
import app.many.economytycoon.carbon.Tonnes
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.markets.MarketKey
import app.many.economytycoon.markets.MarketReport
import app.many.economytycoon.markets.Party
import app.many.economytycoon.markets.Trade
import app.many.economytycoon.production.Facility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Goods that cross a border in a cleared trade reach the carbon border adjustment, and its charge is settled with the day's payments. */
class CarbonBorderFlowTest {
    private class Rig {
        val sim = Simulation(app.many.economytycoon.simulation.SimConfig(seed = 9L, worldSize = app.many.economytycoon.world.WorldSize.MEDIUM, aiCompanies = 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val home = e.world.countries().sortedBy { it.id }[0]
        val abroad = e.world.countries().sortedBy { it.id }[1]
        fun company(name: String, country: app.many.economytycoon.world.Country, capital: Money = Money.units(900_000)): CompanyId =
            CompanyId(((sim.execute(Actor.Player(name.length.toLong() + name.hashCode()), Command.FoundCompany(name, country.id, null, capital)) as Outcome.Ok).value.created!!))
        fun farm(owner: CompanyId, country: app.many.economytycoon.world.Country): Facility =
            e.production.grantFacility(owner, FacilityTypeId("grain_farm"), e.world.regionsOf(country.id).sortedBy { it.id }.first().id, null, null, null, e.tick)
    }

    private val steel = GoodId("steel")

    private fun report(r: Rig, buyer: CompanyId, seller: CompanyId, from: Facility, to: Facility, good: GoodId, qty: Quantity): MarketReport {
        val rep = MarketReport(r.e.tick)
        rep.trades += Trade(
            r.e.ids.trade(), MarketKey(to.region, good), Party.company(buyer), Party.company(seller), qty, Price(Money.units(20)), r.e.tick,
            null, null, to.id.value, from.id.value,
        )
        return rep
    }

    @Test fun aTradeAcrossABorderIsReportedAndAnInlandTradeIsNot() {
        val r = Rig()
        val a = r.company("Exporter Works", r.abroad); val b = r.company("Importer Works", r.home); val c = r.company("Neighbour Works", r.home)
        val fa = r.farm(a, r.abroad); val fb = r.farm(b, r.home); val fc = r.farm(c, r.home)
        val wheat = GoodId("wheat")
        fa.inventory.add(wheat, Quantity.units(100), Money.units(1_000)); fa.inventory.reserve(wheat, Quantity.units(40))
        fc.inventory.add(wheat, Quantity.units(100), Money.units(1_000)); fc.inventory.reserve(wheat, Quantity.units(40))
        val across = Fulfillment(r.e).run(report(r, b, a, fa, fb, wheat, Quantity.units(40)))
        assertEquals(1, across.crossBorder.size)
        val flow = across.crossBorder.single()
        assertEquals(r.abroad.id, flow.from); assertEquals(r.home.id, flow.to)
        assertEquals(b, flow.importer); assertEquals(a, flow.exporter)
        assertEquals(Quantity.units(40), flow.quantity)
        val inland = Fulfillment(r.e).run(report(r, b, c, fc, fb, wheat, Quantity.units(40)))
        assertTrue(inland.crossBorder.isEmpty())
    }

    @Test fun theBorderAdjustmentChargesTheImporterAtTheNextSettlement() {
        val r = Rig()
        val b = r.company("Importer Works", r.home); val a = r.company("Exporter Works", r.abroad)
        val year = CarbonCalendar.yearOf(r.e.tick)
        r.e.carbon.installRegime(RegimePresets.carbonTax(r.home.id, year, Money.units(80), coverage = RegimePresets.HEAVY, threshold = Tonnes.ZERO), r.e.tick)
        r.e.carbon.setBorderPolicy(BorderPolicy(r.home.id, minIntensityGrams = 0L, startYear = year, phaseInYears = 1))
        val runner = r.e.carbonRunner
        val flow = app.many.economytycoon.carbon.TradeFlow(steel, Quantity.units(500), r.abroad.id, r.home.id, b, a)
        runner.assessTrade(r.e.tick, listOf(flow))
        val settlement = runner.drain()
        val levy = settlement.payments.filter { it.payer == app.many.economytycoon.finance.LedgerOwner.company(b) }
        assertTrue("the importer is charged", levy.isNotEmpty() && levy.all { it.total.isPositive })
        assertTrue(runner.drain().isEmpty)
        // With no border policy, nothing is charged.
        r.e.carbonState.border.remove(r.home.id)
        runner.assessTrade(r.e.tick, listOf(flow))
        assertTrue(runner.drain().isEmpty)
    }
}
