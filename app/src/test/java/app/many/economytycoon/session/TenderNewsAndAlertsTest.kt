package app.many.economytycoon.session

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.simulation.TickSummary
import app.many.economytycoon.tenders.TenderEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TenderNewsAndAlertsTest {
    private val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
    private val e = sim.economy
    private val country = e.world.countries().first()
    private val region = e.world.regionsOf(country.id).first().id
    private val owner = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Bid Works", country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
    private val farm = e.production.grantFacility(owner, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
    private val ref = e.content.good(GoodId("wheat")).referencePrice.perUnit
    private val scanner = AlertScanner(e)

    private fun awarded(qty: Long = 50) = (e.tenders.issue(country.id, GoodId("wheat"), Quantity.units(qty), Money(ref.raw * 13 / 10), e.tick) as Outcome.Ok).value.also {
        e.tenderRunner.bid(owner, it.id, farm.id, ref, e.tick)
        e.tick = it.closesAt
        e.tenderRunner.run(e.tick)
    }

    private fun headlines(vararg events: TenderEvent): List<String> {
        val feed = NewsFeed(e)
        val s = TickSummary(Tick(5))
        s.tenderEvents = events.toList()
        feed.record(s, emptyList())
        return feed.items().map { it.headline }
    }

    @Test fun awardsFailuresAndMissingBiddersAreNews() {
        val t = awarded()
        val lines = headlines(
            TenderEvent.Awarded(t.id, owner, ref, Tick(5)), TenderEvent.Failed(t.id, owner, Money.units(5), Tick(5)),
            TenderEvent.NoBids(t.id, Tick(5)), TenderEvent.Issued(t.id, country.id, GoodId("wheat"), Tick(5)),
        )
        assertEquals(4, lines.size)
        assertTrue(lines.any { it.contains("Bid Works wins") })
        assertTrue(lines.any { it.contains("fails to deliver") })
        assertTrue(lines.any { it.contains("no bidders") })
        assertTrue(lines.any { it.contains("invites bids") })
    }

    @Test fun deliveriesAreNotNews() {
        val t = awarded()
        assertTrue(headlines(TenderEvent.Delivered(t.id, owner, Money.units(10), Tick(6))).isEmpty())
    }

    @Test fun aWonTenderWithoutStockWarnsThenGoesCritical() {
        val t = awarded()
        assertTrue(scanner.scan(owner).any { it.code == "tender.won" && it.subject == t.id.value })
        e.tick = Tick(t.deliverBy.value - 2)
        assertTrue(scanner.scan(owner).any { it.code == "tender.late" })
    }

    @Test fun stockInPlaceTurnsTheAlertIntoANotice() {
        farm.inventory.add(GoodId("wheat"), Quantity.units(100), Money.units(800))
        val t = (e.tenders.issue(country.id, GoodId("wheat"), Quantity.units(50), Money(ref.raw * 13 / 10), e.tick) as Outcome.Ok).value
        e.tenderRunner.bid(owner, t.id, farm.id, ref, e.tick)
        e.tenders.closeBidding(t.closesAt) { true }
        e.tick = t.closesAt
        val alerts = scanner.scan(owner)
        assertTrue(alerts.any { it.code == "tender.ready" })
        assertFalse(alerts.any { it.code == "tender.won" || it.code == "tender.late" })
    }

    @Test fun repeatedFailureBarsAndAlerts() {
        repeat(3) {
            val t = awarded(10)
            e.tick = t.deliverBy
            e.tenderRunner.run(e.tick)
        }
        assertTrue(e.tenders.isBarred(owner, e.tick))
        assertTrue(scanner.scan(owner).any { it.code == "tender.barred" })
    }

    @Test fun otherCompaniesSeeNothing() {
        awarded()
        val other = CompanyId((sim.execute(Actor.Player(2), Command.FoundCompany("Other Works", country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        assertTrue(scanner.scan(other).none { it.code.startsWith("tender") })
    }
}
