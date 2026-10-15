package app.many.economytycoon.session

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.grants.GrantEvent
import app.many.economytycoon.grants.GrantKind
import app.many.economytycoon.grants.GrantProgram
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.simulation.TickSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrantNewsAndAlertsTest {
    private val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
    private val e = sim.economy
    private val country = e.world.countries().first()
    private val region = e.world.regionsOf(country.id).first().id
    private val owner = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Grant Works", country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
    private val scanner = AlertScanner(e)

    private fun jobs(): GrantProgram = (e.grants.announce(country.id, GrantKind.JOBS, null, Money.units(100), Money.units(5_000), Money.units(50_000), e.tick) as Outcome.Ok).value
    private fun build(): GrantProgram = (e.grants.announce(country.id, GrantKind.FACILITIES, IndustryFamily.AGRICULTURE, Money.units(5_000), Money.units(20_000), Money.units(50_000), e.tick) as Outcome.Ok).value
    private fun farm() = e.production.grantFacility(owner, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
    private fun codes() = scanner.scan(owner).map { it.code }

    private fun headlines(vararg events: GrantEvent): List<String> {
        val feed = NewsFeed(e)
        val s = TickSummary(Tick(5))
        s.grantEvents = events.toList()
        feed.record(s, emptyList())
        return feed.items().map { it.headline }
    }

    @Test fun announcementsNameTheCountryAndTheKind() {
        val j = jobs(); val b = build()
        val lines = headlines(GrantEvent.Announced(j.id, country.id, GrantKind.JOBS, Tick(5)), GrantEvent.Announced(b.id, country.id, GrantKind.FACILITIES, Tick(5)))
        assertTrue(lines.toString(), lines.any { it.contains(country.name) && it.contains("new jobs") })
        assertTrue(lines.toString(), lines.any { it.contains("agriculture facilities") })
    }

    @Test fun onlyRealPaymentsAreNews() {
        val j = jobs()
        val lines = headlines(GrantEvent.Settled(j.id, owner, 0L, Money.ZERO, Tick(5)), GrantEvent.Settled(j.id, owner, 4L, Money.units(400), Tick(5)), GrantEvent.Closed(j.id, Money.units(400), 1, Tick(5)))
        assertEquals(1, lines.size)
        assertTrue(lines.single(), lines.single().contains("Grant Works") && lines.single().contains("grants from"))
    }

    @Test fun noApplicantsIsQuiet() {
        val j = jobs()
        assertEquals(emptyList<String>(), headlines(GrantEvent.NoApplicants(j.id, Tick(5))))
    }

    @Test fun anOpenProgrammeFarFromClosingIsNotRaised() {
        farm(); jobs()
        assertFalse(codes().contains("grant.window"))
    }

    @Test fun aProgrammeClosingSoonIsRaisedForACompanyThatOperatesThere() {
        farm(); val g = jobs()
        e.tick = Tick(g.applyUntil.value - 2)
        val alert = scanner.scan(owner).first { it.code == "grant.window" }
        assertEquals(g.id.value, alert.subject)
        assertTrue(alert.title, alert.title.contains("2 days"))
    }

    @Test fun aCompanyWithNothingInTheCountryIsNotNagged() {
        val g = jobs()
        e.tick = Tick(g.applyUntil.value - 2)
        assertFalse(codes().contains("grant.window"))
    }

    @Test fun applyingSilencesTheWindowAlert() {
        farm(); val g = jobs()
        e.grantRunner.apply(owner, g.id, e.tick)
        e.tick = Tick(g.applyUntil.value - 2)
        assertFalse(codes().contains("grant.window"))
    }

    @Test fun anActiveGrantWithNoGrowthNearTheEndIsAWarning() {
        farm(); val g = build()
        e.grantRunner.apply(owner, g.id, e.tick)
        e.grants.closeApplications(g.applyUntil)
        e.tick = Tick(g.endsAt.value - 3)
        val alert = scanner.scan(owner).first { it.code == "grant.slipping" }
        assertEquals(Severity.WARNING, alert.severity)
    }

    @Test fun growthClearsTheSlippingWarning() {
        farm(); val g = build()
        e.grantRunner.apply(owner, g.id, e.tick)
        e.grants.closeApplications(g.applyUntil)
        farm()
        e.tick = Tick(g.endsAt.value - 3)
        assertFalse(codes().contains("grant.slipping"))
    }

    @Test fun anActiveGrantFarFromTheEndIsLeftAlone() {
        farm(); val g = build()
        e.grantRunner.apply(owner, g.id, e.tick)
        e.grants.closeApplications(g.applyUntil)
        e.tick = Tick(g.applyUntil.value + 1)
        assertFalse(codes().contains("grant.slipping"))
    }
}
