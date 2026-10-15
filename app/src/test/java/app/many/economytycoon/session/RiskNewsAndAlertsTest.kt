package app.many.economytycoon.session

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.EventId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import app.many.economytycoon.risk.CoverLevel
import app.many.economytycoon.risk.Deductible
import app.many.economytycoon.risk.Disaster
import app.many.economytycoon.risk.Peril
import app.many.economytycoon.risk.RiskEvent
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.simulation.TickSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskNewsAndAlertsTest {
    private val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
    private val e = sim.economy
    private val country = e.world.countries().first()
    private val region = e.world.regionsOf(country.id).first()
    private val owner = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Cover Works", country.id, null, Money.units(900_000))) as Outcome.Ok).value.created!!)
    private val farm = e.production.grantFacility(owner, FacilityTypeId("grain_farm"), region.id, null, null, null, e.tick).also {
        it.inventory.add(GoodId("wheat"), Quantity.units(2_000), Money.units(60_000))
    }
    private val alerts = AlertScanner(e)

    private fun headlines(vararg events: RiskEvent): List<String> {
        val feed = NewsFeed(e)
        val s = TickSummary(Tick(5))
        s.riskEvents = events.toList()
        feed.record(s, emptyList())
        return feed.items().map { it.headline }
    }

    private fun disaster(peril: Peril) = Disaster(EventId(1), peril, region.id, Tick(5), 400_000L, 3, Money.units(9), Money.ZERO)

    @Test fun eachPerilHasItsOwnHeadline() {
        val lines = Peril.values().map { headlines(RiskEvent.Struck(disaster(it))).single() }
        assertEquals(4, lines.toSet().size)
        assertTrue(lines.all { it.contains(region.name) && it.contains("3 facilities") })
    }

    @Test fun claimsThemselvesAreNotNews() {
        assertTrue(headlines().isEmpty())
    }

    @Test fun aLapsedPolicyIsReportedWithTheCompany() {
        val line = headlines(RiskEvent.Lapsed(app.many.economytycoon.core.PolicyId(1), owner, farm.id, Tick(5))).single()
        assertTrue(line.contains("Cover Works"))
    }

    @Test fun anUninsuredValuableFacilityRaisesAnAlert() {
        val list = alerts.scan(owner)
        assertTrue(list.any { it.code == "insurance.none" && it.subject == farm.id.value })
    }

    @Test fun insuringTheFacilityClearsTheAlert() {
        assertTrue(sim.execute(Actor.Player(1), Command.BuyPolicy(owner, farm.id, CoverLevel.BASIC, 800_000L, Deductible.MEDIUM)) is Outcome.Ok)
        assertFalse(alerts.scan(owner).any { it.code == "insurance.none" })
    }

    @Test fun aLapsedPolicyRaisesAWarningForAWhileOnly() {
        val p = (e.riskRunner.buy(owner, farm.id, CoverLevel.BASIC, 800_000L, Deductible.MEDIUM, e.tick) as Outcome.Ok).value
        e.risk.lapse(p, e.tick)
        assertTrue(alerts.scan(owner).any { it.code == "insurance.lapsed" })
        e.tick = Tick(e.tick.value + 40)
        assertFalse(alerts.scan(owner).any { it.code == "insurance.lapsed" })
    }
}
