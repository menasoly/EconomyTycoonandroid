package app.many.economytycoon.session

import app.many.economytycoon.contracts.ContractEvent
import app.many.economytycoon.contracts.ContractSide
import app.many.economytycoon.contracts.MissReason
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.ContractId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.simulation.TickSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractAlertsTest {
    private val wheat = GoodId("wheat")
    private val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
    private val e = sim.economy
    private val country = e.world.countries().first()
    private val region = e.world.regionsOf(country.id).first().id
    private val ref = e.content.good(wheat).referencePrice.perUnit

    private fun company(name: String, id: Long) =
        CompanyId((sim.execute(Actor.Player(id), Command.FoundCompany(name, country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)

    private val a = company("Seller Works", 1)
    private val b = company("Buyer Works", 2)
    private val fa = e.production.grantFacility(a, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
    private val fb = e.production.grantFacility(b, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)

    private fun offer(): ContractId =
        ContractId((sim.execute(Actor.System, Command.ProposeContract(a, b, ContractSide.SELLER, fa.id, wheat, Quantity.units(10), ref, 2, 5)) as Outcome.Ok).value.created!!)

    @Test fun anOfferToYouIsAnAlertWithARoute() {
        val id = offer()
        val alert = AlertScanner(e).scan(b).single { it.code == "contract.offer" }
        assertEquals(id.value, alert.subject)
        assertEquals(Severity.INFO, alert.severity)
        assertTrue(alert.title.contains("Seller Works"))
        assertTrue(AlertScanner(e).scan(a).none { it.code == "contract.offer" })
    }

    @Test fun answeringAnOfferClearsTheAlert() {
        val id = offer()
        sim.execute(Actor.System, Command.DeclineContract(b, id))
        assertTrue(AlertScanner(e).scan(b).none { it.code == "contract.offer" })
    }

    @Test fun missedDeliveriesRaiseAWarningThatGrowsToCritical() {
        val id = offer()
        sim.execute(Actor.System, Command.AcceptContract(b, id, fb.id))
        val k = e.contracts.get(id)!!
        e.contracts.recordMiss(k, e.tick, MissReason.SELLER_SHORT)
        assertEquals(Severity.WARNING, AlertScanner(e).scan(a).single { it.code == "contract.missed" }.severity)
        e.contracts.recordMiss(k, e.tick, MissReason.SELLER_SHORT)
        assertEquals(Severity.CRITICAL, AlertScanner(e).scan(b).single { it.code == "contract.missed" }.severity)
    }

    @Test fun aBreachAndACompletionBecomeHeadlines() {
        val id = offer()
        sim.execute(Actor.System, Command.AcceptContract(b, id, fb.id))
        val feed = NewsFeed(e)
        val summary = TickSummary(e.tick)
        summary.contractEvents = listOf(ContractEvent.Breached(id, e.tick, a), ContractEvent.Fulfilled(id, e.tick))
        feed.record(summary, emptyList())
        val lines = feed.items().map { it.headline }
        assertTrue(lines.any { it.contains("broke its supply contract") && it.contains("Seller Works") })
        assertTrue(lines.any { it.contains("completed a supply contract") })
    }

    @Test fun ordinaryDeliveriesStayOutOfTheNews() {
        val id = offer()
        val feed = NewsFeed(e)
        val summary = TickSummary(e.tick)
        summary.contractEvents = listOf(ContractEvent.Delivered(id, e.tick, Quantity.units(1), Money.units(1)))
        feed.record(summary, emptyList())
        assertTrue(feed.items().isEmpty())
    }
}
