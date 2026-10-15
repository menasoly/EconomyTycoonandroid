package app.many.economytycoon.session

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.regulation.Fine
import app.many.economytycoon.regulation.Law
import app.many.economytycoon.regulation.Lever
import app.many.economytycoon.regulation.RegulationEvent
import app.many.economytycoon.regulation.Stance
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.simulation.TickSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegulationNewsAndAlertsTest {
    private val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
    private val e = sim.economy
    private val country = e.world.countries().first()
    private val region = e.world.regionsOf(country.id).first().id
    private val owner = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Policy Works", country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
    private val farm = e.production.grantFacility(owner, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
    private val scanner = AlertScanner(e)
    init { e.regulationRunner.ensureGovernments(e.tick) }

    private fun law(l: Lever = Lever.CORPORATE_TAX, raise: Boolean = true): Law {
        val c = e.world.country(country.id); val g = e.regulation.government(c.id)!!
        return (e.regulation.propose(c, l, e.regulation.target(l, l.valueIn(c), raise, g.baselineWage)!!, e.tick) as Outcome.Ok).value
    }

    private fun headlines(vararg events: RegulationEvent): List<String> {
        val feed = NewsFeed(e)
        val s = TickSummary(Tick(5))
        s.regulationEvents = events.toList()
        feed.record(s, emptyList())
        return feed.items().map { it.headline }
    }

    @Test fun lawsAreNewsAtEveryStage() {
        val l = law()
        val lines = headlines(
            RegulationEvent.Proposed(l.id, country.id, Tick(5)), RegulationEvent.Passed(l.id, country.id, Tick(5)),
            RegulationEvent.Rejected(l.id, country.id, Tick(5)), RegulationEvent.Enacted(l.id, country.id, Tick(5)),
        )
        assertEquals(4, lines.size)
        assertTrue(lines.any { it.contains("proposes") && it.contains("corporate tax") })
        assertTrue(lines.any { it.contains("passes") }); assertTrue(lines.any { it.contains("rejects") }); assertTrue(lines.any { it.contains("into force") })
        assertTrue(lines.all { it.contains(country.name) })
    }

    @Test fun electionsReadDifferentlyWhenTheGovernmentChanges() {
        val kept = headlines(RegulationEvent.Election(country.id, Tick(5), Stance.LABOUR, Stance.LABOUR, 500_000L))
        assertTrue(kept.single().contains("re-elects its labour government"))
        val swung = headlines(RegulationEvent.Election(country.id, Tick(5), Stance.LABOUR, Stance.BUSINESS, 550_000L))
        assertTrue(swung.single().contains("swings from labour to pro-business government"))
    }

    @Test fun finesNameTheCompanyAndMentionClosures() {
        val open = Fine(owner, farm.id, country.id, Tick(5), 900_000L, 600_000L, Money.units(1_200), false)
        val shut = open.copy(closed = true)
        val lines = headlines(RegulationEvent.Fined(open), RegulationEvent.Fined(shut))
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.contains("Policy Works is fined") })
        assertEquals(1, lines.count { it.contains("ordered shut") })
    }

    @Test fun lawSummariesReadNaturally() {
        val up = law(Lever.CORPORATE_TAX, true)
        assertTrue(Formatting.lawSummary(up).startsWith("a rise to the corporate tax from "))
        val wage = law(Lever.MINIMUM_WAGE, true)
        assertTrue(Formatting.lawSummary(wage).contains(" a day"))
        assertEquals("12.5%", Formatting.leverValue(Lever.SALES_TAX, 125_000L))
    }

    @Test fun anImminentVoteOnALocalLawIsAnAlertWithTheCompanysSide() {
        val l = law()
        e.tick = Tick(l.voteAt.value - 3)
        val alert = scanner.scan(owner).single { it.code == "law.vote" }
        assertEquals(l.id.value, alert.subject)
        assertTrue(alert.title.contains("3 days"))
        assertTrue(alert.detail.contains("can still lobby"))
        e.regulation.lobby(l.id, owner, true, Money.units(2_000))
        assertTrue(scanner.scan(owner).single { it.code == "law.vote" }.detail.contains("backing"))
        e.regulation.lobby(l.id, CompanyId(77), true, Money.units(1_000))
    }

    @Test fun farOffVotesAndAbroadVotesAreNotAlerts() {
        val l = law()
        e.tick = Tick(l.voteAt.value - 15)
        assertFalse(scanner.scan(owner).any { it.code == "law.vote" })
        val other = e.world.countries().firstOrNull { it.id != country.id }
        if (other != null) {
            val g = e.regulation.government(other.id)!!
            e.regulation.propose(other, Lever.TARIFF, e.regulation.target(Lever.TARIFF, other.tariffPpm, true, g.baselineWage) ?: return, e.tick)
            e.tick = Tick(l.voteAt.value - 3)
            assertEquals(1, scanner.scan(owner).count { it.code == "law.vote" })
        }
    }

    @Test fun recentFinesAlertAndOldOnesFadeAway() {
        e.tick = Tick(100)
        e.regulation.recordFine(Fine(owner, farm.id, country.id, Tick(95), 900_000L, 600_000L, Money.units(900), false))
        val a = scanner.scan(owner).single { it.code == "fine.issued" }
        assertEquals(farm.id.value, a.subject); assertEquals(Severity.WARNING, a.severity)
        e.tick = Tick(130)
        assertFalse(scanner.scan(owner).any { it.code.startsWith("fine") })
    }

    @Test fun aClosureIsCritical() {
        e.tick = Tick(100)
        e.regulation.recordFine(Fine(owner, farm.id, country.id, Tick(98), 900_000L, 600_000L, Money.units(900), true))
        val a = scanner.scan(owner).single { it.code.startsWith("fine") }
        assertEquals("fine.closed", a.code); assertEquals(Severity.CRITICAL, a.severity)
    }

    @Test fun otherCompaniesFinesAreTheirOwnBusiness() {
        e.tick = Tick(100)
        e.regulation.recordFine(Fine(CompanyId(999), farm.id, country.id, Tick(98), 900_000L, 600_000L, Money.units(900), false))
        assertFalse(scanner.scan(owner).any { it.code.startsWith("fine") })
    }
}
