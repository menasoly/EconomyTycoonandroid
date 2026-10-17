package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.cyrus
import app.many.economytycoon.carbon.CarbonTestSupport.de
import app.many.economytycoon.carbon.CarbonTestSupport.rule
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.carbon.CarbonTestSupport.value
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.world.WorldTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbonBorderServiceTest {
    private val fx = CarbonTestSupport.Fixture()
    private val content = WorldTestSupport.content
    private val calc = EmissionCalculator(StandardFactors.table, content)
    private val service = CarbonService(fx.state, fx.env, calc, RandomProvider(3L))
    private val footprints = ProductFootprints(content, calc)
    private val steel = GoodId("steel")
    private val year2000 = Tick(10)

    private fun priced(price: Long) {
        service.installRegime(RegimePresets.capAndTrade(uk, 2000, Tonnes.of(1_000_000)), Tick(0)).value()
        fx.state.market(uk)!!.setTradePrice(Money.units(price), Tonnes.of(1))
    }

    private fun flow(quantity: Long = 100, importer: app.many.economytycoon.core.CompanyId? = acme, exporter: app.many.economytycoon.core.CompanyId? = borea) =
        TradeFlow(steel, Quantity.units(quantity), de, uk, importer, exporter)

    @Test fun anImportFromAnUnpricedCountryPaysTheFullGap() {
        priced(40)
        service.setBorderPolicy(BorderPolicy(uk, startYear = 2000, phaseInYears = 1, minIntensityGrams = 0L)).value()
        val r = service.assessTrade(year2000, listOf(flow()), footprints)
        val embedded = footprints.embedded(steel, Quantity.units(100))
        val levy = r.settlement.payments.single()
        assertEquals(embedded.cost(Money.units(40)), levy.total)
        assertEquals(ReasonCode.TAX, levy.reason)
        assertEquals(acme.value, levy.payer.id)
        assertEquals(1, r.events.filterIsInstance<CarbonEvent.BorderLevy>().size)
    }

    @Test fun theLevyIsCountedAsReceiptsAndInTheBorderTotals() {
        priced(40)
        service.setBorderPolicy(BorderPolicy(uk, startYear = 2000, phaseInYears = 1, minIntensityGrams = 0L)).value()
        val r = service.assessTrade(year2000, listOf(flow()), footprints)
        assertEquals(r.settlement.paymentTotal, fx.state.border.levied(uk))
        assertEquals(r.settlement.paymentTotal, fx.state.receipts(uk))
    }

    @Test fun noPolicyMeansNoCharge() {
        priced(40)
        assertTrue(service.assessTrade(year2000, listOf(flow()), footprints).settlement.isEmpty)
    }

    @Test fun theLevyPhasesIn() {
        priced(40)
        service.setBorderPolicy(BorderPolicy(uk, startYear = 2000, phaseInYears = 4, minIntensityGrams = 0L)).value()
        val first = service.assessTrade(year2000, listOf(flow()), footprints).settlement.paymentTotal
        val later = service.assessTrade(Tick(3 * 360 + 10), listOf(flow()), footprints).settlement.paymentTotal
        assertTrue(Math.abs(first.raw * 4 - later.raw) <= 4L)
    }

    @Test fun anAnonymousImporterIsNotCharged() {
        priced(40)
        service.setBorderPolicy(BorderPolicy(uk, startYear = 2000, phaseInYears = 1, minIntensityGrams = 0L)).value()
        assertTrue(service.assessTrade(year2000, listOf(flow(importer = null)), footprints).settlement.isEmpty)
    }

    @Test fun anInactiveImporterIsNotCharged() {
        priced(40)
        service.setBorderPolicy(BorderPolicy(uk, startYear = 2000, phaseInYears = 1, minIntensityGrams = 0L)).value()
        fx.inactive += acme
        assertTrue(service.assessTrade(year2000, listOf(flow()), footprints).settlement.isEmpty)
    }

    @Test fun cleanGoodsBelowTheIntensityThresholdPassFree() {
        priced(40)
        service.setBorderPolicy(BorderPolicy(uk, startYear = 2000, phaseInYears = 1, minIntensityGrams = 900_000_000L)).value()
        assertTrue(service.assessTrade(year2000, listOf(flow()), footprints).settlement.isEmpty)
    }

    @Test fun exportersFromAPricingCountryGetARebateFromTheirTreasury() {
        priced(40)
        service.setBorderPolicy(BorderPolicy(uk, startYear = 2000, phaseInYears = 1, minIntensityGrams = 0L, exportRebatePpm = 1_000_000L)).value()
        val outbound = TradeFlow(steel, Quantity.units(100), uk, de, importer = cyrus, exporter = acme)
        val r = service.assessTrade(year2000, listOf(outbound), footprints)
        val rebate = r.settlement.payments.single()
        assertEquals(ReasonCode.SUBSIDY, rebate.reason)
        assertEquals(acme.value, rebate.payee.id)
        assertEquals(rebate.total, fx.state.border.rebated(uk))
    }

    @Test fun aTreasuryThatCannotPayOwesNoRebate() {
        priced(40)
        fx.treasury[uk] = Money.ZERO
        service.setBorderPolicy(BorderPolicy(uk, startYear = 2000, phaseInYears = 1, minIntensityGrams = 0L, exportRebatePpm = 1_000_000L)).value()
        val outbound = TradeFlow(steel, Quantity.units(100), uk, de, importer = cyrus, exporter = acme)
        assertTrue(service.assessTrade(year2000, listOf(outbound), footprints).settlement.isEmpty)
    }

    @Test fun policiesAreValidatedAndCanBeRemoved() {
        assertEquals(Rules.UNKNOWN_ENTITY, service.clearBorderPolicy(uk).rule())
        service.setBorderPolicy(BorderPolicy(uk, startYear = 2000)).value()
        service.clearBorderPolicy(uk).value()
        assertEquals(null, fx.state.border.policy(uk))
    }

    @Test fun domesticTradeIsNeverAdjusted() {
        priced(40)
        service.setBorderPolicy(BorderPolicy(uk, startYear = 2000, phaseInYears = 1, minIntensityGrams = 0L)).value()
        assertTrue(service.assessTrade(year2000, listOf(TradeFlow(steel, Quantity.units(100), uk, uk, acme, borea)), footprints).settlement.isEmpty)
    }
}
