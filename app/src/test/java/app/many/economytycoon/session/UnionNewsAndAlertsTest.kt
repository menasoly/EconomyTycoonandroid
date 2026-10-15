package app.many.economytycoon.session

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.simulation.TickSummary
import app.many.economytycoon.unions.UnionEvent
import app.many.economytycoon.workforce.CohortKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnionNewsAndAlertsTest {
    private val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
    private val e = sim.economy
    private val country = e.world.countries().first()
    private val region = e.world.regionsOf(country.id).first().id
    private val role = e.content.allRoles.first().id
    private val owner = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Shop Floor", country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
    private val union = run {
        val c = e.workforce.ensure(CohortKey(owner, FacilityId(1), role, region))
        c.headcount = 40; c.moralePpm = 300_000L; c.target = 40
        (e.unions.form(owner, region, 40, Tick(1)) as Outcome.Ok).value
    }
    private val scanner = AlertScanner(e)

    private fun headlines(vararg events: UnionEvent): List<String> {
        val feed = NewsFeed(e)
        val s = TickSummary(Tick(5))
        s.unionEvents = events.toList()
        feed.record(s, emptyList())
        return feed.items().map { it.headline }
    }

    @Test fun organisingStrikesAndDealsAreNews() {
        val lines = headlines(
            UnionEvent.Formed(union.id, owner, Tick(5)), UnionEvent.Struck(union.id, 1, 9, Tick(5)),
            UnionEvent.Agreed(union.id, 1, 45_000L, Tick(5)), UnionEvent.StrikeEnded(union.id, Tick(5)),
            UnionEvent.BackedDown(union.id, 1, Tick(5)), UnionEvent.Disbanded(union.id, owner, Tick(5)),
        )
        assertEquals(6, lines.size)
        assertTrue(lines.any { it.contains("organise") && it.contains("Shop Floor") })
        assertTrue(lines.any { it.contains("walk out") && it.contains("9 days") })
        assertTrue(lines.any { it.contains("agrees a") })
        assertTrue(lines.any { it.contains("strike") && it.contains("ends") })
        assertTrue(lines.any { it.contains("drops its wage demand") })
        assertTrue(lines.any { it.contains("dissolves") })
    }

    @Test fun aDemandOnItsOwnIsNotNews() {
        assertTrue(headlines(UnionEvent.Demanded(union.id, 1, Tick(5))).isEmpty())
    }

    @Test fun anOpenDemandIsAWarningThatTurnsCriticalNearTheDeadline() {
        val d = e.unions.makeDemand(union, 60_000L, Tick(100))
        e.tick = Tick(100)
        val early = scanner.scan(owner).first { it.code == "union.demand" }
        assertEquals(d.id, early.subject); assertEquals(Severity.WARNING, early.severity)
        e.tick = Tick(d.deadline.value - 2)
        assertEquals(Severity.CRITICAL, scanner.scan(owner).first { it.code == "union.demand" }.severity)
    }

    @Test fun answeringClearsTheAlert() {
        val d = e.unions.makeDemand(union, 60_000L, Tick(100))
        e.tick = Tick(101)
        e.unionRunner.respond(owner, d.id, app.many.economytycoon.unions.ResponseKind.ACCEPT, 0L, e.tick)
        assertTrue(scanner.scan(owner).none { it.code == "union.demand" })
    }

    @Test fun aStrikeIsAnAlertWhileItLasts() {
        e.tick = Tick(100)
        val d = e.unions.makeDemand(union, 60_000L, Tick(100))
        e.unions.struck(d, union, Tick(100), 10, 0L)
        assertTrue(scanner.scan(owner).any { it.code == "union.strike" && it.subject == union.id.value })
        e.tick = Tick(111)
        assertTrue(scanner.scan(owner).none { it.code == "union.strike" })
    }

    @Test fun otherCompaniesSeeNothing() {
        e.unions.makeDemand(union, 60_000L, Tick(100)); e.tick = Tick(100)
        val other = CompanyId((sim.execute(Actor.Player(2), Command.FoundCompany("Other Works", country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        assertFalse(scanner.scan(other).any { it.code.startsWith("union") })
    }
}
