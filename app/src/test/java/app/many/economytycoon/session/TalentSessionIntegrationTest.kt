package app.many.economytycoon.session

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.Invariants
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.talent.TalentCommand
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.AdvisorPresenter
import app.many.economytycoon.ui.present.Glossary
import app.many.economytycoon.ui.present.HelpContent
import app.many.economytycoon.ui.present.Tips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Skills and education as the player meets them: through the session, the feeds, the routes and the reference texts. */
class TalentSessionIntegrationTest {
    private fun start(): GameSession =
        (NewGame.start(NewGameOptions("People Works", Difficulty.RELAXED, seed = 5L), SimTestSupport.content, GameSettings(autosave = false, pauseOnCritical = false), null) as Outcome.Ok).value

    @Test fun aNewGameHasTheModuleAndTheViewReadsFromTheSession() {
        val s = start()
        assertTrue(s.hasTalent)
        repeat(40) { s.simulation.advance() }
        assertNotNull(s.talentView())
        assertTrue(Invariants.check(s.economy).isEmpty())
    }

    @Test fun ordersGoThroughTheSessionAndAnswerInWords() {
        val s = start()
        repeat(5) { s.simulation.advance() }
        val me = s.selectedCompany ?: return
        val said = s.issueTalent(TalentCommand.SetCounterPolicy(me, true, 150_000L))
        assertTrue(said, said.isNotBlank())
    }

    @Test fun theFeedsAdvisorAndAgendaKeepWorking() {
        val s = start()
        repeat(130) { s.step() }
        s.talentInsights().forEach { assertTrue(it.code.startsWith("talent-")) }
        assertEquals(Route.Talent, AdvisorPresenter.routeFor("talent-shortage"))
        assertTrue(s.alerts().all { it.code.isNotBlank() })
        assertNotNull(s.agenda())
        assertTrue(s.news.items().isNotEmpty())
    }

    @Test fun theRoutesAndReferenceTextsAreWired() {
        for (r in listOf(Route.Talent, Route.TalentEducation, Route.TalentTraining, Route.TalentRecruiting, Route.TalentCareers, Route.TalentProviders)) assertEquals(r, Route.parse(r.key))
        assertNotNull(Glossary.byId("talent-shortage"))
        assertTrue(Glossary.search("skills shortage").any { it.id.startsWith("talent-") })
        assertNotNull(HelpContent.byId("people-skills"))
        assertEquals(Route.TalentTraining, Tips.byId("talent-train")?.route)
        assertEquals(Route.TalentRecruiting, Tips.byId("talent-poach")?.route)
    }

    @Test fun aSavedGameKeepsTheModuleAndCarriesOn() {
        val s = start()
        repeat(35) { s.simulation.advance() }
        val bytes = app.many.economytycoon.persistence.SimulationSnapshot.save(s.simulation)
        val copy = app.many.economytycoon.persistence.SimulationSnapshot.load(bytes, SimTestSupport.content)
        assertEquals(s.simulation.hash(), copy.hash())
        assertTrue(copy.economy.talentRunner.active)
    }
}
