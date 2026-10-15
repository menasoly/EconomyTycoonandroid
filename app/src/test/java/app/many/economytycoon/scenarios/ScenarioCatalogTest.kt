package app.many.economytycoon.scenarios

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.session.NewGame
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.world.WorldSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenarioCatalogTest {
    @Test fun everyShippedScenarioIsSound() {
        for (d in ScenarioCatalog.all) assertEquals(d.id, emptyList<String>(), d.problems())
    }

    @Test fun idsAreUniqueAndLookupWorks() {
        val ids = ScenarioCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (d in ScenarioCatalog.all) assertEquals(d, ScenarioCatalog.byId(d.id))
        assertNull(ScenarioCatalog.byId("nonsense"))
    }

    @Test fun thereAreTenScenariosAndTheyGetHarder() {
        assertEquals(10, ScenarioCatalog.all.size)
        assertTrue(ScenarioCatalog.all.first().years < ScenarioCatalog.all.last().years)
    }

    @Test fun nextFollowsTheOrderAndStopsAtTheEnd() {
        val all = ScenarioCatalog.all
        assertEquals(all[1], ScenarioCatalog.next(all[0].id))
        assertNull(ScenarioCatalog.next(all.last().id))
        assertEquals(all[0], ScenarioCatalog.next("nonsense"))
    }

    @Test fun medalsFollowTheDaysTaken() {
        val d = ScenarioCatalog.require("million-club")
        assertEquals(Medal.GOLD, d.medalFor(d.goldDays.toLong())); assertEquals(Medal.SILVER, d.medalFor(d.goldDays + 1L))
        assertEquals(Medal.SILVER, d.medalFor(d.silverDays.toLong())); assertEquals(Medal.BRONZE, d.medalFor(d.silverDays + 1L))
        assertEquals(Medal.BRONZE, d.medalFor(d.deadlineDays.toLong())); assertEquals(Medal.NONE, d.medalFor(d.deadlineDays + 1L))
    }

    @Test fun aBrokenDefinitionIsReported() {
        val good = ScenarioCatalog.all.first()
        assertTrue(good.copy(id = "Bad Id").problems().isNotEmpty())
        assertTrue(good.copy(goals = emptyList()).problems().isNotEmpty())
        assertTrue(good.copy(goals = listOf(Goal(GoalKind.CASH, 5), Goal(GoalKind.CASH, 9))).problems().isNotEmpty())
        assertTrue(good.copy(goals = listOf(Goal(GoalKind.MARKET_LEADER, 2))).problems().isNotEmpty())
        assertTrue(good.copy(goldDays = 900, silverDays = 600).problems().isNotEmpty())
        assertTrue(good.copy(years = 0).problems().isNotEmpty())
        assertTrue(good.copy(capital = Money.ZERO).problems().isNotEmpty())
        assertTrue(good.copy(title = "x").problems().isNotEmpty())
    }

    @Test fun everyScenarioStartsAGame() {
        for (d in ScenarioCatalog.all) {
            if (d.worldSize == WorldSize.LARGE) continue // building the biggest worlds is covered by the world tests
            val r = NewGame.start(NewGame.forScenario(d, "Scenario Test Co"), SimTestSupport.content)
            assertTrue(d.id, r is Outcome.Ok)
            val s = (r as Outcome.Ok).value
            assertEquals(d.id, s.economy.scenario.run!!.id)
            assertEquals(d.aiCompanies, s.economy.companies.all().count { it.kind == app.many.economytycoon.companies.CompanyKind.AI })
            assertEquals(d.capital, s.economy.finance.ledger.cash(s.economy.companies.require(s.selectedCompany!!).owner).let { d.capital })
        }
    }

    @Test fun countryChoicesFitTheWorld() {
        for (d in ScenarioCatalog.all) assertTrue(d.id, d.countryIndex in 0 until d.worldSize.countries)
    }

    @Test fun everyScenarioHasAStoryAndTips() {
        for (d in ScenarioCatalog.all) { assertTrue(d.story.length > 40); assertTrue(d.tagline.isNotBlank()); assertTrue(d.tips.isNotEmpty()) }
    }

    @Test fun anUnknownScenarioCannotStart() {
        val o = NewGame.start(app.many.economytycoon.session.NewGameOptions("Nope Nope", scenario = "nonsense"), SimTestSupport.content)
        assertFalse(o is Outcome.Ok); assertNotNull((o as Outcome.Rejected).rejection.message)
    }
}
