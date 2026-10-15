package app.many.economytycoon.session

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.property.PropertyEvent
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.simulation.TickSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyNewsAndAlertsTest {
    private val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
    private val e = sim.economy
    private val country = e.world.countries().first()
    private val owner = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Landed Co", country.id, null, Money.units(1_000_000))) as Outcome.Ok).value.created!!)
    private val plot: ParcelId = run {
        val city = e.world.citiesOf(country.id).first()
        val p = e.world.land.available(city.id).filter { e.propertyRunner.appraise(it).raw > 0L }.minByOrNull { e.propertyRunner.appraise(it).raw }!!
        sim.execute(Actor.Player(1), Command.BuyParcel(owner, p.id)); p.id
    }
    private val scanner = AlertScanner(e)

    private fun headlines(vararg events: PropertyEvent): List<String> {
        val feed = NewsFeed(e)
        val s = TickSummary(Tick(5))
        s.propertyEvents = events.toList()
        feed.record(s, emptyList())
        return feed.items().map { it.headline }
    }

    @Test fun onlySalesAreNews() {
        val other = CompanyId((sim.execute(Actor.Player(2), Command.FoundCompany("Other Co", country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        val lines = headlines(
            PropertyEvent.Listed(plot, owner, Money.units(5), Tick(5)), PropertyEvent.Sold(plot, owner, other, Money.units(50_000), Tick(5)),
            PropertyEvent.LeaseStarted(plot, owner, 1, Tick(5)), PropertyEvent.RentMissed(plot, owner, 1, Tick(5)),
        )
        assertEquals(1, lines.size)
        assertTrue(lines.single().contains("Other Co buys land") && lines.single().contains("Landed Co"))
    }

    @Test fun lateRentIsAWarningUntilPaid() {
        val lease = e.property.startLease(e.property.addOffer(plot, owner, 180, Money.units(300), Tick(0)), 1, Tick(0))
        e.tick = Tick(10)
        assertTrue(scanner.scan(owner).none { it.code == "property.rent" })
        e.property.rentMissed(lease)
        val a = scanner.scan(owner).first { it.code == "property.rent" }
        assertEquals(Severity.WARNING, a.severity); assertEquals(lease.id, a.subject)
        e.property.rentPaid(lease, Money.units(300))
        assertTrue(scanner.scan(owner).none { it.code == "property.rent" })
    }

    @Test fun anExpiringLeaseIsAnInfoNotice() {
        val lease = e.property.startLease(e.property.addOffer(plot, owner, 90, Money.units(300), Tick(0)), 1, Tick(0))
        e.tick = Tick(50); assertTrue(scanner.scan(owner).none { it.code == "property.lease" })
        e.tick = Tick(lease.endsAt.value - 4)
        val a = scanner.scan(owner).first { it.code == "property.lease" }
        assertEquals(Severity.INFO, a.severity)
    }

    @Test fun aListingAboutToLapseIsFlagged() {
        e.propertyRunner.list(owner, plot, e.propertyRunner.appraise(e.world.land.get(plot)!!), Tick(0))
        val l = e.property.listing(plot)!!
        e.tick = Tick(10); assertTrue(scanner.scan(owner).none { it.code == "property.listing" })
        e.tick = Tick(l.expiresAt.value - 2)
        assertEquals(plot.value, scanner.scan(owner).first { it.code == "property.listing" }.subject)
    }

    @Test fun otherCompaniesSeeNothing() {
        e.property.startLease(e.property.addOffer(plot, owner, 90, Money.units(300), Tick(0)), 1, Tick(0)).also { e.property.rentMissed(it) }
        val other = CompanyId((sim.execute(Actor.Player(2), Command.FoundCompany("Other Co", country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        assertFalse(scanner.scan(other).any { it.code.startsWith("property") })
    }
}
