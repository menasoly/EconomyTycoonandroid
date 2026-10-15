package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.exchange.ExchangeEvent
import app.many.economytycoon.exchange.PublicInvestors
import app.many.economytycoon.exchange.TradeSide
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.LedgerOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeRunnerTest {
    private class Rig(capital: Long = 400_000) {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val issuer = found("Issuer Works", 1, capital)
        val investor = found("Investor Works", 2, 1_000_000)

        init {
            e.finance.mint(e.tick, e.finance.households, Money.units(50_000_000), "test:households")
            sim.advance(31)
        }

        fun found(name: String, player: Long, capital: Long): CompanyId =
            CompanyId((sim.execute(Actor.Player(player), Command.FoundCompany(name, country.id, null, Money.units(capital))) as Outcome.Ok).value.created!!)

        fun cash(c: CompanyId) = e.finance.ledger.cash(LedgerOwner.company(c))
        fun publicShares(c: CompanyId) = e.exchangeRunner.publicShares(c)
        fun listIssuer(float: Long = 250_000L) = e.exchangeRunner.list(issuer, float, e.tick)
        fun problems() = e.finance.invariants()
    }

    @Test fun aYoungCompanyCannotList() {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val country = sim.economy.world.countries().first()
        val id = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Young Works", country.id, null, Money.units(400_000))) as Outcome.Ok).value.created!!)
        val r = sim.economy.exchangeRunner.list(id, 250_000L, sim.economy.tick)
        assertEquals(Rules.REGULATION, (r as Outcome.Rejected).rejection.rule)
        assertTrue(r.rejection.message.contains("traded for"))
    }

    @Test fun aSmallCompanyCannotList() {
        val r = Rig(capital = 20_000)
        val res = r.listIssuer()
        assertEquals(Rules.REGULATION, (res as Outcome.Rejected).rejection.rule)
    }

    @Test fun theFloatMustBeInRange() {
        val r = Rig()
        assertEquals(Rules.INVALID_ARGUMENT, (r.listIssuer(10_000L) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INVALID_ARGUMENT, (r.listIssuer(700_000L) as Outcome.Rejected).rejection.rule)
        assertFalse(r.e.exchange.isListed(r.issuer))
    }

    @Test fun floatingSellsNewSharesToThePublicAndRaisesCash() {
        val r = Rig()
        val before = r.cash(r.issuer)
        val res = (r.listIssuer() as Outcome.Ok).value
        assertTrue(r.e.exchange.isListed(r.issuer))
        assertEquals(res.shares, r.publicShares(r.issuer))
        assertEquals(res.raised - res.fee, r.cash(r.issuer) - before)
        val outstanding = r.e.exchangeRunner.outstanding(r.issuer)
        val pct = res.shares * 1_000_000L / outstanding
        assertTrue("float $pct", pct in 245_000L..255_000L)
        assertEquals(res.raised, r.e.exchange.basisOf(PublicInvestors.holder, r.issuer))
        assertEquals(res.price, r.e.exchange.listing(r.issuer)!!.price)
        assertTrue(r.problems().toString(), r.problems().isEmpty())
    }

    @Test fun theIssuePriceIsBelowFairValue() {
        val r = Rig()
        val fair = r.e.acquisitions.fairPricePerShare(r.issuer, r.e.tick)
        val res = (r.listIssuer() as Outcome.Ok).value
        assertTrue(res.price < fair)
    }

    @Test fun theMarketCannotAbsorbAnUnlimitedIssue() {
        val r = Rig()
        val hh = r.e.finance.households
        val drain = r.e.finance.ledger.cash(hh)
        val gov = r.e.governmentOf(r.country.id)
        r.e.finance.ledger.post(
            app.many.economytycoon.finance.TransactionDraft(
                "test:drain", r.e.tick, app.many.economytycoon.finance.ReasonCode.TRANSFER,
                listOf(
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(hh, Chart.OTHER_EXPENSE), drain),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(hh, Chart.CASH), -drain),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(gov, Chart.CASH), drain),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(gov, Chart.REVENUE_OTHER), -drain),
                ),
            ),
        )
        val res = r.listIssuer()
        assertEquals(Rules.LIMIT_EXCEEDED, (res as Outcome.Rejected).rejection.rule)
        assertFalse(r.e.exchange.isListed(r.issuer))
        assertTrue(r.problems().isEmpty())
    }

    @Test fun aListedCompanyCannotListAgain() {
        val r = Rig(); r.listIssuer()
        assertEquals(Rules.REGULATION, (r.listIssuer() as Outcome.Rejected).rejection.rule)
    }

    @Test fun buyingMovesSharesAndCashAndPaysDuty() {
        val r = Rig(); r.listIssuer()
        val cash = r.cash(r.investor)
        val publicBefore = r.publicShares(r.issuer)
        val gov = r.e.governmentOfCompany(r.investor)
        val govCash = r.e.finance.ledger.cash(gov)
        val priceBefore = r.e.exchange.listing(r.issuer)!!.price
        val n = publicBefore / 5
        val q = (r.e.exchangeRunner.buy(r.investor, r.issuer, n, r.e.tick) as Outcome.Ok).value
        assertEquals(cash - q.net, r.cash(r.investor))
        assertEquals(n, r.e.exchangeRunner.holdingOf(r.investor, r.issuer))
        assertEquals(publicBefore - n, r.publicShares(r.issuer))
        assertEquals(govCash + q.duty, r.e.finance.ledger.cash(gov))
        assertTrue(r.e.exchange.listing(r.issuer)!!.price > priceBefore)
        assertEquals(q.gross, r.e.finance.ledger.balance(LedgerOwner.company(r.investor), Chart.INVESTMENTS))
        assertEquals(q.gross, r.e.exchange.basisOf(r.e.companies.require(r.investor).holder, r.issuer))
        assertTrue(r.problems().toString(), r.problems().isEmpty())
    }

    @Test fun buyingIsRefusedWhenItShouldBe() {
        val r = Rig()
        assertEquals(Rules.UNKNOWN_ENTITY, (r.e.exchangeRunner.buy(r.investor, r.issuer, 1, r.e.tick) as Outcome.Rejected).rejection.rule)
        r.listIssuer()
        assertEquals(Rules.INVALID_ARGUMENT, (r.e.exchangeRunner.buy(r.issuer, r.issuer, 1, r.e.tick) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INSUFFICIENT_STOCK, (r.e.exchangeRunner.buy(r.investor, r.issuer, r.publicShares(r.issuer) + 1, r.e.tick) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INVALID_QUANTITY, (r.e.exchangeRunner.buy(r.investor, r.issuer, 0, r.e.tick) as Outcome.Rejected).rejection.rule)
    }

    @Test fun anInvestorWithoutCashCannotBuy() {
        val r = Rig(); r.listIssuer()
        val poor = r.found("Poor Works", 3, 60_000)
        val res = r.e.exchangeRunner.buy(poor, r.issuer, r.publicShares(r.issuer), r.e.tick)
        assertEquals(Rules.INSUFFICIENT_FUNDS, (res as Outcome.Rejected).rejection.rule)
        assertEquals(0L, r.e.exchangeRunner.holdingOf(poor, r.issuer))
    }

    @Test fun nobodyCanBuyControlOnTheMarket() {
        val r = Rig(); r.listIssuer(490_000L)
        r.e.finance.mint(r.e.tick, LedgerOwner.company(r.investor), Money.units(100_000_000), "test:rich")
        val res = r.e.exchangeRunner.buy(r.investor, r.issuer, r.publicShares(r.issuer), r.e.tick)
        assertTrue(res is Outcome.Ok || (res as Outcome.Rejected).rejection.rule == Rules.REGULATION)
        val total = r.e.exchangeRunner.outstanding(r.issuer)
        assertTrue(r.e.exchangeRunner.holdingOf(r.investor, r.issuer) * 1_000_000L / total <= 490_000L)
    }

    @Test fun sellingBringsCashBackLessCosts() {
        val r = Rig(); r.listIssuer()
        val start = r.cash(r.investor)
        r.e.exchangeRunner.buy(r.investor, r.issuer, 100, r.e.tick)
        val sold = (r.e.exchangeRunner.sell(r.investor, r.issuer, 100, r.e.tick) as Outcome.Ok).value
        assertEquals(0L, r.e.exchangeRunner.holdingOf(r.investor, r.issuer))
        assertTrue(r.cash(r.investor) < start)
        assertEquals(Money.ZERO, r.e.finance.ledger.balance(LedgerOwner.company(r.investor), Chart.INVESTMENTS))
        assertEquals(Money.ZERO, r.e.exchange.basisOf(r.e.companies.require(r.investor).holder, r.issuer))
        assertEquals(TradeSide.SELL, sold.side)
        assertTrue(r.problems().toString(), r.problems().isEmpty())
    }

    @Test fun aRisingPriceTurnsIntoABookedGain() {
        val r = Rig(); r.listIssuer()
        val n = r.publicShares(r.issuer) / 20
        r.e.exchangeRunner.buy(r.investor, r.issuer, n, r.e.tick)
        val owner = LedgerOwner.company(r.investor)
        val incomeBefore = r.e.finance.ledger.balance(owner, Chart.REVENUE_OTHER)
        // Other buying pushes the price up; selling at the higher level books a gain.
        val other = r.found("Other Works", 4, 2_000_000)
        r.e.exchangeRunner.buy(other, r.issuer, r.publicShares(r.issuer) / 3, r.e.tick)
        r.e.exchangeRunner.sell(r.investor, r.issuer, n, r.e.tick)
        assertTrue(r.e.finance.ledger.balance(owner, Chart.REVENUE_OTHER).raw < incomeBefore.raw)
        assertTrue(r.problems().toString(), r.problems().isEmpty())
    }

    @Test fun youCannotSellWhatYouDoNotHold() {
        val r = Rig(); r.listIssuer()
        r.e.exchangeRunner.buy(r.investor, r.issuer, 10, r.e.tick)
        val res = r.e.exchangeRunner.sell(r.investor, r.issuer, 11, r.e.tick)
        assertEquals(Rules.INSUFFICIENT_STOCK, (res as Outcome.Rejected).rejection.rule)
        assertEquals(10L, r.e.exchangeRunner.holdingOf(r.investor, r.issuer))
    }

    @Test fun partialSalesKeepTheRestOfTheCostBasis() {
        val r = Rig(); r.listIssuer()
        val q = (r.e.exchangeRunner.buy(r.investor, r.issuer, 100, r.e.tick) as Outcome.Ok).value
        r.e.exchangeRunner.sell(r.investor, r.issuer, 40, r.e.tick)
        val holder = r.e.companies.require(r.investor).holder
        assertEquals(q.gross.raw * 60 / 100, r.e.exchange.basisOf(holder, r.issuer).raw)
        assertEquals(60L, r.e.exchangeRunner.holdingOf(r.investor, r.issuer))
        assertEquals(r.e.exchange.basisOf(holder, r.issuer), r.e.finance.ledger.balance(LedgerOwner.company(r.investor), Chart.INVESTMENTS))
    }

    @Test fun theIssuerCanBuyBackPublicShares() {
        val r = Rig(); r.listIssuer()
        val before = r.publicShares(r.issuer)
        val cash = r.cash(r.issuer)
        val q = (r.e.exchangeRunner.buyBack(r.issuer, 50, r.e.tick) as Outcome.Ok).value
        assertEquals(before - 50, r.publicShares(r.issuer))
        assertEquals(cash - q.gross, r.cash(r.issuer))
        assertTrue(r.problems().toString(), r.problems().isEmpty())
    }

    @Test fun aBuybackCannotExceedWhatThePublicHolds() {
        val r = Rig(); r.listIssuer()
        val res = r.e.exchangeRunner.buyBack(r.issuer, r.publicShares(r.issuer) + 1, r.e.tick)
        assertEquals(Rules.INSUFFICIENT_STOCK, (res as Outcome.Rejected).rejection.rule)
    }

    @Test fun delistingBuysOutThePublic() {
        val r = Rig(); r.listIssuer()
        r.e.finance.mint(r.e.tick, LedgerOwner.company(r.issuer), Money.units(5_000_000), "test:cash")
        val res = r.e.exchangeRunner.delist(r.issuer, r.e.tick)
        assertTrue(res.toString(), res is Outcome.Ok)
        assertFalse(r.e.exchange.isListed(r.issuer))
        assertEquals(0L, r.publicShares(r.issuer))
        assertTrue(r.problems().toString(), r.problems().isEmpty())
    }

    @Test fun delistingACompanyThatIsNotListedIsRefused() {
        val r = Rig(); r.listIssuer()
        val res = r.e.exchangeRunner.delist(r.investor, r.e.tick)
        assertEquals(Rules.WRONG_STATE, (res as Outcome.Rejected).rejection.rule)
        assertTrue(r.e.exchange.isListed(r.issuer))
    }

    @Test fun delistingNeedsTheCashToBuyOutThePublic() {
        val r = Rig(); r.listIssuer()
        val owner = LedgerOwner.company(r.issuer)
        val cash = r.e.finance.ledger.cash(owner)
        val gov = r.e.governmentOf(r.country.id)
        r.e.finance.ledger.post(
            app.many.economytycoon.finance.TransactionDraft(
                "test:spend", r.e.tick, app.many.economytycoon.finance.ReasonCode.TRANSFER,
                listOf(
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(owner, Chart.OTHER_EXPENSE), cash),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(owner, Chart.CASH), -cash),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(gov, Chart.CASH), cash),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(gov, Chart.REVENUE_OTHER), -cash),
                ),
            ),
        )
        val res = r.e.exchangeRunner.delist(r.issuer, r.e.tick)
        assertEquals(Rules.INSUFFICIENT_FUNDS, (res as Outcome.Rejected).rejection.rule)
        assertTrue(r.e.exchange.isListed(r.issuer))
        assertTrue(r.e.exchangeRunner.publicShares(r.issuer) > 0L)
    }

    @Test fun portfolioValuesHoldingsAtTheMarket() {
        val r = Rig(); r.listIssuer()
        r.e.exchangeRunner.buy(r.investor, r.issuer, 30, r.e.tick)
        val p = r.e.exchangeRunner.portfolio(r.investor).single()
        assertEquals(30L, p.shares)
        assertEquals(r.e.exchange.listing(r.issuer)!!.price * 30L, p.value)
        assertEquals(p.value - p.cost, p.gain)
        assertTrue(r.e.exchangeRunner.portfolio(r.issuer).isEmpty())
    }

    @Test fun marketCapIsPriceTimesSharesOutstanding() {
        val r = Rig(); r.listIssuer()
        val l = r.e.exchange.listing(r.issuer)!!
        assertEquals(l.price * r.e.exchangeRunner.outstanding(r.issuer), r.e.exchangeRunner.marketCap(r.issuer))
        assertEquals(Money.ZERO, r.e.exchangeRunner.marketCap(r.investor))
    }

    @Test fun aCompanyThatClosesLeavesTheExchange() {
        val r = Rig(); r.listIssuer()
        r.e.exchangeRunner.buy(r.investor, r.issuer, 10, r.e.tick)
        r.e.retire(r.issuer)
        val events = r.e.exchangeRunner.closeDay(r.e.tick)
        assertFalse(r.e.exchange.isListed(r.issuer))
        assertTrue(events.any { it is ExchangeEvent.Delisted })
        assertTrue(events.any { it is ExchangeEvent.Listed })
    }

    @Test fun listingAndDelistingAreAnnouncedOnce() {
        val r = Rig(); r.listIssuer()
        val first = r.e.exchangeRunner.closeDay(r.e.tick)
        assertEquals(1, first.count { it is ExchangeEvent.Listed })
        assertEquals(0, r.e.exchangeRunner.closeDay(r.e.tick).count { it is ExchangeEvent.Listed })
        r.e.finance.mint(r.e.tick, LedgerOwner.company(r.issuer), Money.units(5_000_000), "test:cash")
        r.e.exchangeRunner.delist(r.issuer, r.e.tick)
        assertEquals(1, r.e.exchangeRunner.closeDay(r.e.tick).count { it is ExchangeEvent.Delisted })
    }

    @Test fun anInvestorThatClosesLosesItsRecordedPositions() {
        val r = Rig(); r.listIssuer()
        r.e.exchangeRunner.buy(r.investor, r.issuer, 10, r.e.tick)
        val holder = r.e.companies.require(r.investor).holder
        r.e.exchangeRunner.forget(r.investor)
        assertEquals(Money.ZERO, r.e.exchange.basisOf(holder, r.issuer))
    }

    @Test fun stateEnterprisesDoNotTradeShares() {
        val r = Rig(); r.listIssuer()
        val board = r.e.tradeBoard.boardOf(r.country.id)
        if (board != null) {
            val res = r.e.exchangeRunner.buy(board, r.issuer, 1, r.e.tick)
            assertEquals(CompanyKind.STATE, r.e.companies.require(board).kind)
            assertEquals(Rules.REGULATION, (res as Outcome.Rejected).rejection.rule)
        }
    }

    @Test fun commandsGoThroughTheExecutorToo() {
        val r = Rig()
        val listed = r.sim.execute(Actor.System, Command.ListCompany(r.issuer, 250_000L))
        assertTrue(listed.toString(), listed is Outcome.Ok)
        assertTrue(r.sim.execute(Actor.System, Command.BuyShares(r.investor, r.issuer, 20)) is Outcome.Ok)
        assertTrue(r.sim.execute(Actor.System, Command.SellShares(r.investor, r.issuer, 5)) is Outcome.Ok)
        assertTrue(r.sim.execute(Actor.System, Command.BuybackShares(r.issuer, 10)) is Outcome.Ok)
        assertEquals(15L, r.e.exchangeRunner.holdingOf(r.investor, r.issuer))
    }

    @Test fun anotherPlayersCompanyCannotBeCommanded() {
        val r = Rig()
        val res = r.sim.execute(Actor.Player(99), Command.ListCompany(r.issuer, 250_000L))
        assertEquals(Rules.NOT_OWNER, (res as Outcome.Rejected).rejection.rule)
    }

    @Test fun daysPassWithTradingAndTheBooksStayBalanced() {
        val r = Rig(); r.listIssuer()
        r.e.exchangeRunner.buy(r.investor, r.issuer, 80, r.e.tick)
        val first = r.e.exchange.listing(r.issuer)!!.price
        var moved = false
        repeat(40) {
            val s = r.sim.advance()
            if (r.e.exchange.listing(r.issuer)!!.price != first) moved = true
            if (it % 10 == 0) r.e.exchangeRunner.sell(r.investor, r.issuer, 5, r.e.tick)
            assertTrue(s.problems.toString(), s.problems.isEmpty())
        }
        assertTrue(moved)
        assertTrue(r.e.exchange.indexHistory().size >= 40)
        assertTrue(r.problems().toString(), r.problems().isEmpty())
    }

    @Test fun dividendsReachThePublicAsHouseholdCash() {
        val r = Rig(); r.listIssuer()
        r.e.companies.policies.setDividend(r.issuer, app.many.economytycoon.companies.DividendPolicy(1_000_000L, 0, 5))
        r.e.finance.mint(r.e.tick, LedgerOwner.company(r.issuer), Money.units(1_000_000), "test:profit")
        val before = r.e.finance.ledger.cash(r.e.finance.households)
        repeat(12) { r.sim.advance() }
        assertTrue(r.problems().toString(), r.problems().isEmpty())
        assertTrue(r.e.finance.ledger.cash(r.e.finance.households).raw != before.raw)
    }
}
