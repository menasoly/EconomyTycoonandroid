package app.many.economytycoon.workshop

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.scenarios.DefFailKind
import app.many.economytycoon.scenarios.Goal
import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.scenarios.ScenarioCatalog
import app.many.economytycoon.session.Difficulty
import app.many.economytycoon.session.NewGame
import app.many.economytycoon.session.NewGameOptions
import app.many.economytycoon.simulation.SimConfig
import app.many.economytycoon.world.WorldSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftConversionTest {
    private fun convert(d: ScenarioDraft) = DraftConverter.convert(d)

    private fun ok(d: ScenarioDraft): Conversion = (convert(d) as Outcome.Ok).value

    @Test fun aSoundDraftBecomesADefinitionThatPassesTheGamesOwnChecks() {
        val c = ok(Samples.full())
        assertEquals(emptyList<String>(), c.def.problems())
    }

    @Test fun fieldsCarryOver() {
        val d = Samples.full()
        val def = ok(d).def
        assertEquals("full-house", def.id)
        assertEquals("Full House", def.title)
        assertEquals(d.tagline, def.tagline)
        assertEquals(d.story, def.story)
        assertEquals(Money.units(400_000), def.capital)
        assertEquals(2, def.aiCompanies)
        assertEquals(WorldSize.SMALL, def.worldSize)
        assertEquals(4242L, def.seed)
        assertEquals(4, def.years)
        assertEquals(700, def.goldDays)
        assertEquals(1_100, def.silverDays)
        assertEquals(50_000L, def.ruinBelow)
        assertEquals(d.hints, def.tips)
    }

    @Test fun bonusGoalsDoNotBecomeRequiredGoals() {
        val def = ok(Samples.full()).def
        assertEquals(listOf(Goal(GoalKind.NET_WORTH, 900_000), Goal(GoalKind.FACILITIES, 4)), def.goals)
    }

    @Test fun whatDoesNotFitIsListedInTheNotes() {
        val c = ok(Samples.full())
        assertFalse(c.lossless)
        val text = c.notes.joinToString("\n")
        for (word in listOf("bonus goal", "weights", "difficulty tags")) {
            assertTrue("notes mention '$word':\n$text", text.contains(word))
        }
    }

    @Test fun whatTheGameCanPlayIsNotListedAsLeftOut() {
        val c = ok(Samples.full())
        val text = c.notes.joinToString("\n")
        for (word in listOf("failure condition", "grace period", "scripted event", "starting site", "rule override")) {
            assertFalse("notes do not mention '$word':\n$text", text.contains(word))
        }
    }

    @Test fun eventsFailureConditionsAndStartingSitesCarryOver() {
        val d = Samples.full()
        val def = ok(d).def
        assertEquals(d.events.map { it.day }, def.events.map { it.day })
        assertEquals(d.events.map { it.kind.name }, def.events.map { it.kind.name })
        assertEquals(d.events.map { it.amount }, def.events.map { it.amount })
        assertEquals(d.company.starters.map { it.recipe to it.regionIndex }, def.starters.map { it.recipe to it.regionIndex })
        // The net worth floor with its grace period is the definition's own ruin limit; the other condition is listed beside it.
        assertEquals(50_000L, def.ruinBelow)
        assertEquals(30, def.ruinAfterDay)
        assertEquals(listOf(DefFailKind.FACILITIES_BELOW to 100), def.fails.map { it.kind to it.afterDay })
    }

    @Test fun aPlainDraftConvertsWithoutLoss() {
        val d = DraftDefaults.blank("plain-one", "Plain One").copy(tags = emptyList())
        assertTrue(ok(d).lossless)
    }

    @Test fun aDraftWithErrorsIsRefusedNamingTheFirst() {
        val bad = Samples.minimal().copy(goals = emptyList(), title = "x")
        val r = convert(bad)
        assertTrue(r is Outcome.Rejected)
        val rej = (r as Outcome.Rejected).rejection
        assertEquals(Rules.INVALID_ARGUMENT, rej.rule)
        assertTrue(rej.message, rej.message.contains("(and 1 more)") || rej.message.contains("more)"))
    }

    @Test fun theFirstNetWorthFloorBecomesTheRuinLimit() {
        val d = Samples.minimal().copy(fails = listOf(FailCondition(FailKind.CASH_BELOW, 10), FailCondition(FailKind.NET_WORTH_BELOW, 7_000)))
        assertEquals(7_000L, ok(d).def.ruinBelow)
        assertNull(ok(Samples.minimal()).def.ruinBelow)
    }

    @Test fun theConfigurationCarriesRulesCapitalRivalsAndSeed() {
        val cfg = DraftConverter.config(Samples.full())
        assertEquals(4242L, cfg.seed)
        assertEquals(2, cfg.aiCompanies)
        assertEquals(Money.units(400_000), cfg.playerStartingCapital)
        assertEquals(20_000L, cfg.spendingPropensityPpm)
        assertEquals(200L, cfg.householdCashPerCapita)
        assertEquals(SimConfig(seed = 0).bankCapitalMultiple, cfg.bankCapitalMultiple)
    }

    @Test fun newGameParametersDescribeAnUnregisteredGame() {
        val p = ok(Samples.full()).params
        assertNull(p.options.scenario)
        assertEquals("Full House Ltd", p.options.companyName)
        assertEquals(Difficulty.STANDARD, p.options.difficulty)
        assertEquals(WorldSize.SMALL, p.options.worldSize)
        assertEquals(4242L, p.options.seed)
        assertEquals(Money.units(400_000), p.capital)
    }

    @Test fun conversionRegistersNothingInTheCatalog() {
        val before = ScenarioCatalog.all.size
        ok(Samples.full())
        assertEquals(before, ScenarioCatalog.all.size)
        assertNull(ScenarioCatalog.byId("full-house"))
    }

    @Test fun theNewGameHelperAcceptsTheOptions() {
        val p = ok(Samples.minimal()).params
        val cfg = NewGame.config(p.options)
        // Without a catalog entry the helper builds from difficulty; the draft's own config is the one to use.
        assertEquals(Difficulty.STANDARD.aiCompanies, cfg.aiCompanies)
        assertEquals(Samples.minimal().world.rivals, p.config.aiCompanies)
        assertTrue(p.options is NewGameOptions)
    }

    @Test fun conversionIsRepeatable() {
        assertEquals(convert(Samples.full()), convert(Samples.full()))
    }

    @Test fun contentIsConsultedWhenGiven() {
        val d = Samples.minimal().copy(company = Samples.minimal().company.copy(starters = listOf(StarterSite("nope", 0))))
        assertTrue(DraftConverter.convert(d, null) is Outcome.Ok)
        assertTrue(DraftConverter.convert(d, Samples.content) is Outcome.Rejected)
    }

    @Test fun anUnnormalisedDraftConvertsTheSameAsItsNormalForm() {
        val d = Samples.full()
        val shuffled = d.copy(events = d.events.reversed(), tags = d.tags.reversed())
        assertEquals(ok(d), ok(shuffled))
    }
}
