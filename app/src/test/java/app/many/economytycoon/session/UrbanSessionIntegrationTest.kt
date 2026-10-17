package app.many.economytycoon.session

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.Invariants
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.AdvisorPresenter
import app.many.economytycoon.ui.present.Glossary
import app.many.economytycoon.ui.present.HelpContent
import app.many.economytycoon.ui.present.Tips
import app.many.economytycoon.ui.present.UrbanMapPresenter
import app.many.economytycoon.ui.present.UrbanPresenter
import app.many.economytycoon.urban.Service
import app.many.economytycoon.urban.UrbanCommand
import app.many.economytycoon.urban.OverlayKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** City planning as the player meets it: through the session, the presenters, the feeds, the routes and the reference texts. */
class UrbanSessionIntegrationTest {
    private fun start(): GameSession =
        (NewGame.start(NewGameOptions("Metro Works", Difficulty.RELAXED, seed = 5L), SimTestSupport.content, GameSettings(autosave = false, pauseOnCritical = false), null) as Outcome.Ok).value

    @Test fun aNewGameHasPlansAndThePresentersReadThemFromTheSession() {
        val s = start()
        assertTrue(s.hasUrban)
        repeat(40) { s.simulation.advance() }
        val view = s.urbanView()
        assertTrue(view.cities.isNotEmpty())
        assertTrue(UrbanPresenter.overview(view).tiles.isNotEmpty() || view.cities.isNotEmpty())
        val city = view.cities.first()
        assertNotNull(UrbanPresenter.city(view, city.city))
        for (kind in OverlayKind.values()) {
            val overlay = s.urbanOverlay(city.city, kind)
            assertNotNull(kind.name, overlay)
            assertTrue(UrbanMapPresenter.build(overlay ?: return, city.name).colors.isNotEmpty())
        }
        assertTrue(Invariants.check(s.economy).isEmpty())
    }

    @Test fun ordersAreAcceptedForTheGovernedCityAndRefusedElsewhere() {
        val s = start()
        repeat(5) { s.simulation.advance() }
        val me = s.selectedCompany ?: return
        val home = s.simulation.economy.companies.company(me)?.hq ?: return
        val own = s.issueUrban(UrbanCommand.SetServiceLevel(home, Service.POLICE, 3))
        assertTrue(own, own.isNotBlank())
        val other = s.urbanView().cities.map { it.city }.firstOrNull { it != home } ?: return
        val before = s.simulation.hash()
        s.issueUrban(UrbanCommand.SetServiceLevel(other, Service.POLICE, 3))
        assertEquals(before, s.simulation.hash())
    }

    @Test fun theFeedsAdvisorAndAgendaKeepWorking() {
        val s = start()
        repeat(130) { s.step() }
        s.urbanInsights().forEach { assertTrue(it.code.startsWith("urban.")) }
        assertEquals(Route.Urban, AdvisorPresenter.routeFor("urban.budget.city"))
        assertTrue(s.alerts().all { it.code.isNotBlank() })
        assertNotNull(s.agenda())
        assertTrue(s.news.items().isNotEmpty())
    }

    @Test fun theRoutesAndReferenceTextsAreWired() {
        for (r in listOf(Route.Urban, Route.UrbanCouncil, Route.UrbanTransit, Route.UrbanBudget)) assertEquals(r, Route.parse(r.key))
        assertTrue(Glossary.search("zoning").any { it.id.startsWith("urban-") })
        assertNotNull(Glossary.byId("urban-far"))
        assertNotNull(HelpContent.byId("cities"))
        assertEquals(Route.Urban, Tips.byId("cities")?.route)
    }

    @Test fun aSavedGameKeepsItsCityPlansAndCarriesOn() {
        val s = start()
        repeat(35) { s.simulation.advance() }
        val bytes = app.many.economytycoon.persistence.SimulationSnapshot.save(s.simulation)
        val copy = app.many.economytycoon.persistence.SimulationSnapshot.load(bytes, SimTestSupport.content)
        assertEquals(s.simulation.hash(), copy.hash())
        assertEquals(s.economy.urban.size, copy.economy.urban.size)
    }
}
