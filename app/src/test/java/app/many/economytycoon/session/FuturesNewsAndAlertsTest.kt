package app.many.economytycoon.session

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.futures.FuturesEvent
import app.many.economytycoon.futures.Side
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.FuturesTestSupport
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.simulation.TickSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuturesNewsAndAlertsTest {
    private val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
    private val e = sim.economy
    private val country = e.world.countries().first()
    private val wheat = GoodId("wheat")
    private val ref = e.content.good(wheat).referencePrice.perUnit
    private val me = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Forward Works", country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
    private val scanner = AlertScanner(e)

    private fun open(side: Side = Side.LONG, tenor: Int = 30): app.many.economytycoon.futures.Position {
        FuturesTestSupport.setSpot(e, wheat, ref)
        return (e.futuresRunner.open(me, wheat, side, 5, tenor, e.tick) as Outcome.Ok).value
    }

    private fun headlines(vararg events: FuturesEvent): List<String> {
        val feed = NewsFeed(e)
        val s = TickSummary(Tick(5))
        s.futuresEvents = events.toList()
        feed.record(s, emptyList())
        return feed.items().map { it.headline }
    }

    @Test fun onlyClosingOutIsNews() {
        val p = open()
        val lines = headlines(
            FuturesEvent.Opened(p.id, me, Tick(5)), FuturesEvent.MarginCall(p.id, me, Tick(5)),
            FuturesEvent.Expired(p.id, me, Money.units(5), Tick(5)), FuturesEvent.Liquidated(p.id, me, Money.units(40), Tick(5)),
        )
        assertEquals(1, lines.size)
        assertTrue(lines.single().contains("Forward Works") && lines.single().contains("closed out"))
    }

    @Test fun aCalledPositionIsACriticalAlertWithTheShortfall() {
        val p = open()
        e.tick = Tick(1); FuturesTestSupport.setSpot(e, wheat, Money(ref.raw * 92 / 100)); e.futuresRunner.run(Tick(1))
        val a = scanner.scan(me).first { it.code == "futures.call" }
        assertEquals(p.id.value, a.subject); assertEquals(Severity.CRITICAL, a.severity)
        assertTrue(a.detail.contains("Add"))
    }

    @Test fun addingMarginClearsTheCall() {
        val p = open()
        e.tick = Tick(1); FuturesTestSupport.setSpot(e, wheat, Money(ref.raw * 92 / 100)); e.futuresRunner.run(Tick(1))
        e.futuresRunner.addMargin(me, p.id, e.futures.shortfall(p) + Money.units(500), e.tick)
        assertTrue(scanner.scan(me).none { it.code == "futures.call" })
    }

    @Test fun expiryIsAnInfoNoticeInTheLastDays() {
        val p = open(tenor = 30)
        e.tick = Tick(10); assertTrue(scanner.scan(me).none { it.code.startsWith("futures") })
        e.tick = Tick(p.expiresAt.value - 3)
        val a = scanner.scan(me).first { it.code == "futures.expiring" }
        assertEquals(Severity.INFO, a.severity); assertEquals(p.id.value, a.subject)
    }

    @Test fun closedPositionsRaiseNothing() {
        val p = open()
        e.futuresRunner.close(me, p.id, e.tick)
        e.tick = Tick(p.expiresAt.value - 1)
        assertTrue(scanner.scan(me).none { it.code.startsWith("futures") })
    }

    @Test fun otherCompaniesSeeNothing() {
        open(); e.tick = Tick(29)
        val other = CompanyId((sim.execute(Actor.Player(2), Command.FoundCompany("Other Works", country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        assertFalse(scanner.scan(other).any { it.code.startsWith("futures") })
    }
}
