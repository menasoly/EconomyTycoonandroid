package app.many.economytycoon.session

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.exchange.ExchangeEvent
import app.many.economytycoon.exchange.TradeSide
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.simulation.TickSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeNewsTest {
    private val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
    private val e = sim.economy
    private val country = e.world.countries().first()

    private fun found(name: String, p: Long) =
        CompanyId((sim.execute(Actor.Player(p), Command.FoundCompany(name, country.id, null, Money.units(900_000))) as Outcome.Ok).value.created!!)

    private val issuer = found("Issuer Works", 1)
    private val investor = found("Investor Works", 2)

    init {
        e.finance.mint(e.tick, e.finance.households, Money.units(60_000_000), "test:households")
        sim.advance(35)
        sim.execute(Actor.System, Command.ListCompany(issuer, 300_000L))
    }

    private fun feedFor(vararg events: ExchangeEvent): List<String> {
        val feed = NewsFeed(e)
        val s = TickSummary(Tick(40))
        s.exchangeEvents = events.toList()
        feed.record(s, emptyList())
        return feed.items().map { it.headline }
    }

    @Test fun aFlotationIsNewsWithItsPrice() {
        val lines = feedFor(ExchangeEvent.Listed(issuer, Tick(40), Money.units(12), 1_000))
        assertTrue(lines.single().contains("Issuer Works floats"))
        assertTrue(lines.single().contains("12"))
    }

    @Test fun surgesSlumpsAndDelistingsAreNews() {
        val lines = feedFor(
            ExchangeEvent.Surge(issuer, Tick(40), 90_000), ExchangeEvent.Slump(issuer, Tick(40), -120_000), ExchangeEvent.Delisted(issuer, Tick(40)),
        )
        assertEquals(3, lines.size)
        assertTrue(lines.any { it.contains("jump 9") })
        assertTrue(lines.any { it.contains("fall 12") })
        assertTrue(lines.any { it.contains("leaves the stock exchange") })
    }

    @Test fun aHoldingThatHasLostALotRaisesAnAlert() {
        sim.execute(Actor.System, Command.BuyShares(investor, issuer, e.exchangeRunner.publicShares(issuer) / 10))
        assertTrue(AlertScanner(e).scan(investor).none { it.code == "portfolio.loss" })
        val sale = (e.exchange.quote(issuer, TradeSide.SELL, e.exchangeRunner.publicShares(issuer), e.exchangeRunner.publicShares(issuer)) as Outcome.Ok).value
        repeat(6) { e.exchange.recordTrade(sale, e.exchangeRunner.publicShares(issuer)) }
        val alert = AlertScanner(e).scan(investor).single { it.code == "portfolio.loss" }
        assertEquals(issuer.value, alert.subject)
        assertTrue(alert.title.contains("Issuer Works"))
    }

    @Test fun thePipelineCarriesExchangeEventsToTheSummary() {
        val s = sim.advance()
        assertTrue(s.exchangeEvents.any { it is ExchangeEvent.Listed })
        assertTrue(sim.advance().exchangeEvents.none { it is ExchangeEvent.Listed })
    }
}
