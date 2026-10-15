package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.finance.LedgerOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiInvestorsTest {
    private class Rig {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        fun company(name: String, kind: CompanyKind, capital: Long = 600_000): CompanyId {
            val actor = if (kind == CompanyKind.AI) Actor.System else Actor.Player(name.hashCode().toLong())
            return CompanyId((sim.execute(actor, Command.FoundCompany(name, country.id, null, Money.units(capital), kind)) as Outcome.Ok).value.created!!)
        }
        init { e.finance.mint(e.tick, e.finance.households, Money.units(80_000_000), "test:households") }
        val investors = AiInvestors(e)
    }

    @Test fun aMatureWealthyAiCompanyListsItself() {
        val r = Rig(); val ai = r.company("Rival Works", CompanyKind.AI, 900_000)
        r.sim.advance(40)
        var tick = r.e.tick.value
        while ((tick + ai.value) % 45L != 0L) tick++
        r.e.tick = app.many.economytycoon.core.Tick(tick)
        r.investors.run(r.e.tick)
        assertTrue(r.e.exchange.isListed(ai))
        assertTrue(r.e.finance.invariants().toString(), r.e.finance.invariants().isEmpty())
    }

    @Test fun aYoungAiCompanyStaysPrivate() {
        val r = Rig(); val ai = r.company("Rival Works", CompanyKind.AI, 900_000)
        repeat(20) {
            r.investors.run(r.e.tick)
            r.sim.advance()
        }
        assertFalse(r.e.exchange.isListed(ai))
    }

    @Test fun playerCompaniesAreNeverListedForThem() {
        val r = Rig(); val me = r.company("My Works", CompanyKind.PLAYER, 900_000)
        r.sim.advance(40)
        var tick = r.e.tick.value
        while ((tick + me.value) % 45L != 0L) tick++
        r.e.tick = app.many.economytycoon.core.Tick(tick)
        r.investors.run(r.e.tick)
        assertFalse(r.e.exchange.isListed(me))
    }

    @Test fun anAiCompanyBuysAStockTradingBelowItsWorth() {
        val r = Rig(); val issuer = r.company("Cheap Works", CompanyKind.PLAYER, 800_000); val ai = r.company("Investor AI", CompanyKind.AI, 3_000_000)
        r.sim.advance(40)
        r.e.exchangeRunner.list(issuer, 250_000L, r.e.tick)
        // A wave of selling pushes the price under what the business is worth.
        val sale = (r.e.exchange.quote(issuer, app.many.economytycoon.exchange.TradeSide.SELL, r.e.exchangeRunner.publicShares(issuer), r.e.exchangeRunner.publicShares(issuer)) as Outcome.Ok).value
        r.e.exchange.recordTrade(sale, r.e.exchangeRunner.publicShares(issuer))
        val l = r.e.exchange.listing(issuer)!!
        assertTrue(l.price.raw * 1_000_000L / l.fairValue.raw < 880_000L)
        var tick = r.e.tick.value
        while ((tick + ai.value) % 7L != 0L) tick++
        r.e.tick = app.many.economytycoon.core.Tick(tick)
        r.investors.run(r.e.tick)
        assertTrue(r.e.exchangeRunner.holdingOf(ai, issuer) > 0L)
        assertTrue(r.e.finance.invariants().toString(), r.e.finance.invariants().isEmpty())
    }

    @Test fun anAiCompanySellsWhenThePriceRunsFarAheadOfWorth() {
        val r = Rig(); val issuer = r.company("Hot Works", CompanyKind.PLAYER, 800_000); val ai = r.company("Investor AI", CompanyKind.AI, 3_000_000)
        r.sim.advance(40)
        r.e.exchangeRunner.list(issuer, 250_000L, r.e.tick)
        r.e.exchangeRunner.buy(ai, issuer, r.e.exchangeRunner.publicShares(issuer) / 50, r.e.tick)
        val l = r.e.exchange.listing(issuer)!!
        // Buying frenzy: other money pushes the price far above worth.
        val big = (r.e.exchange.quote(issuer, app.many.economytycoon.exchange.TradeSide.BUY, r.e.exchangeRunner.publicShares(issuer) / 2, r.e.exchangeRunner.publicShares(issuer)) as Outcome.Ok).value
        repeat(8) { r.e.exchange.recordTrade(big, r.e.exchangeRunner.publicShares(issuer)) }
        assertTrue(l.price.raw * 1_000_000L / l.fairValue.raw > 1_250_000L)
        var tick = r.e.tick.value
        while ((tick + ai.value) % 7L != 0L) tick++
        r.e.tick = app.many.economytycoon.core.Tick(tick)
        r.investors.run(r.e.tick)
        assertEquals(0L, r.e.exchangeRunner.holdingOf(ai, issuer))
        assertTrue(r.e.finance.invariants().toString(), r.e.finance.invariants().isEmpty())
    }

    @Test fun anAiCompanyWithLittleCashStaysOutOfTheMarket() {
        val r = Rig(); val issuer = r.company("Cheap Works", CompanyKind.PLAYER, 800_000); val ai = r.company("Poor AI", CompanyKind.AI, 50_000)
        r.sim.advance(40)
        r.e.exchangeRunner.list(issuer, 250_000L, r.e.tick)
        var tick = r.e.tick.value
        while ((tick + ai.value) % 7L != 0L) tick++
        r.e.tick = app.many.economytycoon.core.Tick(tick)
        r.investors.run(r.e.tick)
        assertEquals(0L, r.e.exchangeRunner.holdingOf(ai, issuer))
    }
}
