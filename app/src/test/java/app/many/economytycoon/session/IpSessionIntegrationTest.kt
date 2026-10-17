package app.many.economytycoon.session

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.TechId
import app.many.economytycoon.ip.IpCommand
import app.many.economytycoon.ip.IpEvent
import app.many.economytycoon.ip.SecretStatus
import app.many.economytycoon.simulation.Invariants
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.TickSummary
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.AdvisorPresenter
import app.many.economytycoon.ui.present.Glossary
import app.many.economytycoon.ui.present.HelpContent
import app.many.economytycoon.ui.present.IpPresenter
import app.many.economytycoon.ui.present.IpTab
import app.many.economytycoon.ui.present.Tips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The patent screens as the player meets them: through a real session, the routes, the feed and the reference texts. */
class IpSessionIntegrationTest {
    private fun start(): GameSession =
        (NewGame.start(NewGameOptions("Mill Works", Difficulty.RELAXED, seed = 6L), SimTestSupport.content, GameSettings(autosave = false, pauseOnCritical = false), null) as Outcome.Ok).value

    private fun techOf(s: GameSession): TechId = SimTestSupport.content.allRecipes.first { it.requiredTech != null }.requiredTech!!

    @Test fun aNewGameHasAPatentOfficeAndTheScreenReadsTheRealModel() {
        val s = start()
        assertTrue(s.economy.ipRunner.active)
        val model = s.ipView.ipModel()
        assertNotNull(model)
        assertEquals(s.selectedCompany, model!!.company)
        assertEquals(3, model.npes.size)
        for (tab in IpTab.values()) assertNotNull(IpPresenter.build(s.ipView, tab))
        assertEquals("Mill Works", s.ipView.companyName(s.selectedCompany!!))
        assertTrue(s.ipView.money(app.many.economytycoon.core.Money.units(5)).isNotBlank())
        assertEquals(model.npes.first().name, s.ipView.npeName(app.many.economytycoon.ip.IpOwner.npe(model.npes.first().id)))
    }

    @Test fun aSessionWithoutTheOfficeShowsNothing() {
        val sim = app.many.economytycoon.simulation.Simulation(SimTestSupport.config(5L, 2), SimTestSupport.content)
        val s = GameSession(sim, 1L, GameSettings(autosave = false, pauseOnCritical = false), null)
        assertNull(s.ipView.ipModel())
        assertTrue(s.ipInsights().isEmpty())
        assertTrue(s.alerts().none { it.code == "ip.event" })
    }

    @Test fun commandsFromTheScreensGoThroughTheExecutorAndComeBackAsMessages() {
        val s = start()
        val me = s.selectedCompany!!
        val tech = techOf(s)
        val country = s.economy.companies.company(me)!!.country
        val refused = s.ipView.issue(IpCommand.FilePatent(me, tech, listOf(country)))
        assertTrue(refused, refused.contains("researched"))
        s.economy.research.grant(me, tech, s.economy.tick)
        val kept = s.ipView.issue(IpCommand.KeepSecret(me, tech))
        assertTrue(kept, kept.startsWith("Kept secret"))
        val model = s.ipView.ipModel()!!
        assertEquals(1, model.secrets.size)
        assertTrue(s.ipView.issue(IpCommand.DropSecret(me, model.secrets.first().id)).isNotBlank())
        assertTrue(s.economy.ip.state.secretsOf(me).none { it.isLive })
    }

    @Test fun theFeedNamesBigNewsAndAlertsShowWhatHappenedToTheCompany() {
        val s = start()
        val me = s.selectedCompany!!
        val tech = techOf(s)
        s.economy.research.grant(me, tech, s.economy.tick)
        s.ipView.issue(IpCommand.KeepSecret(me, tech))
        val secret = s.economy.ip.state.secretsOf(me).first()
        val summary = TickSummary(s.economy.tick)
        summary.ipEvents = listOf(IpEvent.SecretLost(secret.id, me, SecretStatus.LEAKED, s.economy.tick))
        s.news.record(summary, emptyList())
        assertTrue(s.news.items().any { it.headline.contains("loses a trade secret") })
        // Alerts read the recent events of the runner; a filing reaches the office and the answer arrives as an alert.
        val country = s.economy.companies.company(me)!!.country
        s.ipView.issue(IpCommand.DropSecret(me, secret.id))
        assertTrue(s.ipView.issue(IpCommand.FilePatent(me, tech, listOf(country))).startsWith("Filed"))
        var seen = false
        var days = 0
        while (!seen && days < 500) {
            s.step(); days++
            seen = s.alerts().any { it.code == "ip.event" }
        }
        assertTrue("no word from the patent office in $days days", seen)
        assertTrue(Invariants.check(s.economy).isEmpty())
    }

    @Test fun theRoutesAndReferenceTextsAreWired() {
        assertEquals(Route.Ip, Route.parse(Route.Ip.key))
        assertEquals(Route.Ip, AdvisorPresenter.routeFor("ip-unprotected"))
        for (id in listOf("patent", "filing", "trade-secret", "licence", "royalty", "patent-pool", "cross-licence", "frand", "standard-essential", "compulsory-licence", "injunction", "design-around", "patent-box", "npe")) {
            assertNotNull(id, Glossary.byId(id))
        }
        assertNotNull(HelpContent.byId("ip"))
        assertNotNull(Tips.byId("ip-protect"))
        assertEquals(Route.Ip, Tips.byId("ip-renew")!!.route)
    }

    @Test fun savingAndLoadingASessionKeepsThePatentOffice() {
        val s = start()
        val me = s.selectedCompany!!
        val tech = techOf(s)
        s.economy.research.grant(me, tech, s.economy.tick)
        s.ipView.issue(IpCommand.KeepSecret(me, tech))
        repeat(30) { s.step() }
        val copy = app.many.economytycoon.persistence.SimulationSnapshot.load(app.many.economytycoon.persistence.SimulationSnapshot.save(s.simulation), SimTestSupport.content)
        assertEquals(s.simulation.hash(), copy.hash())
        assertTrue(copy.economy.ipActive)
        assertEquals(1, copy.economy.ip.state.secretsOf(me).size)
    }
}
