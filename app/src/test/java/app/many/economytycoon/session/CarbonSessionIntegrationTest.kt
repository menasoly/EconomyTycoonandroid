package app.many.economytycoon.session

import app.many.economytycoon.agenda.AgendaBuilder
import app.many.economytycoon.carbon.CarbonCommand
import app.many.economytycoon.carbon.OffsetKind
import app.many.economytycoon.carbon.Tonnes
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.Invariants
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.AdvisorPresenter
import app.many.economytycoon.ui.present.CarbonMarketPresenter
import app.many.economytycoon.ui.present.CarbonPresenter
import app.many.economytycoon.ui.present.Glossary
import app.many.economytycoon.ui.present.HelpContent
import app.many.economytycoon.ui.present.Tips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The carbon screens as the player meets them: through a real session, the route and the reference texts. */
class CarbonSessionIntegrationTest {
    private fun start(): GameSession =
        (NewGame.start(NewGameOptions("Smoke Works", Difficulty.RELAXED, seed = 5L), SimTestSupport.content, GameSettings(autosave = false, pauseOnCritical = false), null) as Outcome.Ok).value

    @Test fun aNewGameHasCarbonPoliciesAndTheDeskReadsThem() {
        val s = start()
        assertTrue(s.simulation.economy.carbonRunner.active)
        val desk = s.carbonDesk()
        assertNotNull(desk)
        val model = CarbonPresenter.build(desk!!)
        assertEquals(4, model.tiles.size)
        assertEquals(s.selectedCompany, desk.company)
        assertTrue(desk.countryName(s.simulation.economy.world.countries().first().id).isNotBlank())
    }

    @Test fun theDeskSubmitsThroughTheSessionAndReportsRefusals() {
        val s = start()
        repeat(3) { s.simulation.advance() }
        val desk = s.carbonDesk()!!
        assertNull(CarbonPresenter.buyOffsets(desk, OffsetKind.METHANE_CAPTURE, "5"))
        assertTrue(s.simulation.economy.carbonState.offsets.held(desk.company, OffsetKind.METHANE_CAPTURE).isPositive)
        assertNotNull(desk.submit(CarbonCommand.BuyOffsets(desk.company, OffsetKind.METHANE_CAPTURE, Tonnes.ZERO)))
        assertEquals("Enter a number of tonnes above zero.", CarbonPresenter.buyOffsets(desk, OffsetKind.METHANE_CAPTURE, "zero"))
    }

    @Test fun aYearOfPlayKeepsTheNewsAlertsAgendaAndAdviceWorking() {
        val s = start()
        while (s.simulation.tick.value < 400L) s.step()
        val company = s.selectedCompany!!
        assertNotNull(s.reports.alerts.scan(company))
        assertTrue(s.news.items().isNotEmpty())
        assertNotNull(AgendaBuilder(s.simulation.economy).build(company, s.simulation.tick.value))
        assertNotNull(AdvisorPresenter.build(s))
        assertTrue(Invariants.check(s.simulation.economy).isEmpty())
        for (c in s.simulation.economy.world.countries()) s.carbonDesk()!!.market(c.id)?.let { assertNotNull(CarbonMarketPresenter.build(s.carbonDesk()!!, c.id)) }
    }

    @Test fun theRouteAndReferenceTextsAreWired() {
        assertEquals(Route.Carbon, Route.parse(Route.Carbon.key))
        assertEquals(Route.Carbon, AdvisorPresenter.routeFor("carbon.shortfall"))
        assertTrue(Glossary.search("cap and trade").any { it.id == "cap-and-trade" })
        assertNotNull(HelpContent.byId("carbon"))
        assertNotNull(Tips.byId("carbon-deadline"))
        assertTrue(Glossary.terms.flatMap { it.seeAlso }.all { id -> Glossary.terms.any { it.id == id } })
    }
}
