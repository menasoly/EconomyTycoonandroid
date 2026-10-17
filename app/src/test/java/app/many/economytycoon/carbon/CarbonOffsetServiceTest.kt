package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.capAndTrade
import app.many.economytycoon.carbon.CarbonTestSupport.de
import app.many.economytycoon.carbon.CarbonTestSupport.emitter
import app.many.economytycoon.carbon.CarbonTestSupport.rule
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.carbon.CarbonTestSupport.value
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.OwnerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbonOffsetServiceTest {
    private val fx = CarbonTestSupport.Fixture()
    private val plants = listOf(emitter(1, acme))
    private val exchange get() = fx.state.offsets.exchange

    private fun open() { fx.service.installRegime(capAndTrade(), Tick(0)).value(); fx.step(0, plants) }

    @Test fun theOpenMarketStartsWithThreeMonthsOfSupply() {
        open()
        for (k in OffsetKind.values()) assertEquals(OffsetProfiles.of(k).marketMonthly * 3L, exchange.stock(k))
    }

    @Test fun buyingCreditsPaysTheQuoteAndMovesStockToTheCompany() {
        open()
        val quote = exchange.quote(OffsetKind.CLEAN_COOKING, Tonnes.of(500))!!
        val e = fx.exec(CarbonCommand.BuyOffsets(acme, OffsetKind.CLEAN_COOKING, Tonnes.of(500))).value()
        assertEquals(Tonnes.of(500), fx.state.offsets.held(acme, OffsetKind.CLEAN_COOKING))
        assertEquals(quote.total, fx.state.offsets.costOf(acme, OffsetKind.CLEAN_COOKING))
        assertEquals(quote.total, e.settlement.payments.single().total)
        assertEquals(OwnerKind.HOUSEHOLDS, e.settlement.payments.single().payee.kind)
        assertEquals(OffsetProfiles.of(OffsetKind.CLEAN_COOKING).marketMonthly * 3L - Tonnes.of(500), exchange.stock(OffsetKind.CLEAN_COOKING))
    }

    @Test fun buyingNeedsStockAndMoneyAndAReasonableQuantity() {
        open()
        assertEquals(Rules.INVALID_QUANTITY, fx.exec(CarbonCommand.BuyOffsets(acme, OffsetKind.CLEAN_COOKING, Tonnes.ofKg(1))).rule())
        assertEquals(Rules.INSUFFICIENT_STOCK, fx.exec(CarbonCommand.BuyOffsets(acme, OffsetKind.DIRECT_AIR_CAPTURE, Tonnes.of(100_000))).rule())
        fx.cash[acme] = Money.units(10)
        assertEquals(Rules.INSUFFICIENT_FUNDS, fx.exec(CarbonCommand.BuyOffsets(acme, OffsetKind.CLEAN_COOKING, Tonnes.of(500))).rule())
    }

    @Test fun aDissolvedCompanyCannotBuy() {
        open()
        fx.inactive += acme
        assertEquals(Rules.UNKNOWN_ENTITY, fx.exec(CarbonCommand.BuyOffsets(acme, OffsetKind.CLEAN_COOKING, Tonnes.of(5))).rule())
    }

    @Test fun heavyBuyingLiftsTheSpotPriceNextMonth() {
        open()
        val before = exchange.spot(OffsetKind.METHANE_CAPTURE)
        fx.exec(CarbonCommand.BuyOffsets(acme, OffsetKind.METHANE_CAPTURE, Tonnes.of(15_000))).value()
        fx.run(1, 31, plants)
        assertTrue(exchange.spot(OffsetKind.METHANE_CAPTURE) > before)
    }

    @Test fun theMarketReplenishesItsStockEachMonth() {
        open()
        val start = exchange.stock(OffsetKind.CLEAN_COOKING)
        fx.run(1, 61, plants)
        assertTrue(exchange.stock(OffsetKind.CLEAN_COOKING) > start)
    }

    @Test fun fundingAProjectCostsTheUpfrontPerTonneAndStartsAProject() {
        open()
        val e = fx.exec(CarbonCommand.FundOffsetProject(acme, OffsetKind.METHANE_CAPTURE, de, Tonnes.of(1_000))).value()
        val p = fx.state.offsets.projects().single()
        assertEquals(Tonnes.of(1_000).cost(OffsetProfiles.of(OffsetKind.METHANE_CAPTURE).upfrontPerAnnualTonne), p.capex)
        assertEquals(p.capex, e.settlement.payments.single().total)
        assertEquals(de, p.host)
        assertEquals(OffsetStatus.DEVELOPMENT, p.status)
        assertEquals(acme, p.owner)
    }

    @Test fun projectRulesAreEnforced() {
        open()
        assertEquals(Rules.INVALID_QUANTITY, fx.exec(CarbonCommand.FundOffsetProject(acme, OffsetKind.METHANE_CAPTURE, de, Tonnes.of(10))).rule())
        assertEquals(Rules.INVALID_QUANTITY, fx.exec(CarbonCommand.FundOffsetProject(acme, OffsetKind.METHANE_CAPTURE, de, Tonnes.of(900_000))).rule())
        repeat(fx.params.maxOffsetProjectsPerCompany) { fx.exec(CarbonCommand.FundOffsetProject(acme, OffsetKind.METHANE_CAPTURE, de, Tonnes.of(200))).value() }
        assertEquals(Rules.LIMIT_EXCEEDED, fx.exec(CarbonCommand.FundOffsetProject(acme, OffsetKind.METHANE_CAPTURE, de, Tonnes.of(200))).rule())
        fx.cash[borea] = Money.units(100)
        assertEquals(Rules.INSUFFICIENT_FUNDS, fx.exec(CarbonCommand.FundOffsetProject(borea, OffsetKind.METHANE_CAPTURE, de, Tonnes.of(200))).rule())
    }

    @Test fun aFundedProjectEventuallyDeliversCreditsToItsOwnerOrIsRejectedWithARefund() {
        open()
        fx.exec(CarbonCommand.FundOffsetProject(acme, OffsetKind.CLEAN_COOKING, de, Tonnes.of(4_000))).value()
        fx.run(1, 500, plants)
        val p = fx.state.offsets.projects().single()
        if (p.status == OffsetStatus.REJECTED) {
            assertTrue(fx.payments.any { it.key.contains(":project:") && it.payee.id == acme.value })
            assertEquals(Tonnes.ZERO, fx.state.offsets.held(acme, OffsetKind.CLEAN_COOKING))
        } else {
            assertTrue(p.issued.isPositive)
            assertEquals(p.issued, fx.state.offsets.held(acme, OffsetKind.CLEAN_COOKING) + fx.state.offsets.retiredBy(acme, uk, 2000))
            assertTrue(fx.eventsOf<CarbonEvent.OffsetProjectChanged>().isNotEmpty())
        }
    }

    @Test fun creditsFromAProjectCarryTheirShareOfTheMoneyPaid() {
        open()
        fx.exec(CarbonCommand.FundOffsetProject(acme, OffsetKind.CLEAN_COOKING, de, Tonnes.of(4_000))).value()
        fx.run(1, 500, plants)
        val p = fx.state.offsets.projects().single()
        if (p.status == OffsetStatus.REJECTED) return
        assertTrue(fx.state.offsets.costOf(acme, OffsetKind.CLEAN_COOKING).isPositive)
        assertTrue(fx.state.offsets.costOf(acme, OffsetKind.CLEAN_COOKING) <= p.capex)
        assertFalse(p.basisLeft > p.capex)
    }

    @Test fun retiredCreditsAreWrittenOffAtCost() {
        open()
        fx.exec(CarbonCommand.BuyOffsets(acme, OffsetKind.CLEAN_COOKING, Tonnes.of(100))).value()
        val paid = fx.state.offsets.costOf(acme, OffsetKind.CLEAN_COOKING)
        fx.exec(CarbonCommand.RetireOffsets(acme, uk, OffsetKind.CLEAN_COOKING, Tonnes.of(100), false)).value()
        val adj = fx.adjustments.single { it.memo == "credits retired" }
        assertEquals(paid, adj.entries.first { it.amount.isPositive }.amount)
    }

    @Test fun theSameSeedProducesTheSameMarket() {
        fun run(): List<Long> {
            val f = CarbonTestSupport.Fixture(seed = 21)
            f.service.installRegime(capAndTrade(), Tick(0)).value()
            f.run(0, 400, plants)
            return OffsetKind.values().flatMap { listOf(f.state.offsets.exchange.stock(it).raw, f.state.offsets.exchange.spot(it).raw) }
        }
        assertEquals(run(), run())
    }

    @Test fun differentSeedsGiveDifferentSupplyJitter() {
        fun stock(seed: Long): Long {
            val f = CarbonTestSupport.Fixture(seed = seed)
            f.service.installRegime(capAndTrade(), Tick(0)).value()
            f.run(0, 200, plants)
            return f.state.offsets.exchange.stock(OffsetKind.CLEAN_COOKING).raw
        }
        assertTrue(stock(1) != stock(2))
    }
}
