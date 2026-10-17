package app.many.economytycoon.workshop

import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.scenarios.ScenarioCatalog
import app.many.economytycoon.world.WorldSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftValidatorTest {
    private val ok = Samples.minimal()

    private fun report(d: ScenarioDraft, content: Boolean = false) = DraftValidator.validate(d, if (content) Samples.content else null)

    private fun assertIssue(r: ValidationReport, code: String, path: String, severity: IssueSeverity = IssueSeverity.ERROR) {
        val hit = r.issues.firstOrNull { it.code == code && it.path == path }
        assertNotNull("expected $code at $path in:\n${r.issues.joinToString("\n")}", hit)
        assertEquals(severity, hit?.severity)
    }

    @Test fun theBlankDraftIsSound() {
        val r = report(DraftDefaults.blank())
        assertTrue(r.issues.joinToString("\n"), r.ok)
    }

    @Test fun theFullSampleIsSoundAgainstTheShippedContent() {
        val r = report(Samples.full(), content = true)
        assertTrue(r.issues.joinToString("\n"), r.ok)
    }

    @Test fun everyTemplateIsSound() {
        for (t in DraftTemplates.all) {
            val r = report(t.create(), content = true)
            assertTrue("${t.key}: ${r.errors.joinToString()}", r.ok)
        }
    }

    @Test fun everyBuiltInScenarioConvertsToASoundDraftExceptForItsId() {
        for (d in DraftTemplates.builtIns()) {
            val r = report(d, content = true)
            assertTrue("${d.id}: ${r.errors.joinToString()}", r.ok)
        }
    }

    // ---- identity ---------------------------------------------------------------------------

    @Test fun badIdsAreErrorsAtTheIdPath() {
        assertIssue(report(ok.copy(id = "Not Valid")), "id.format", "id")
        assertIssue(report(ok.copy(id = "")), "id.format", "id")
    }

    @Test fun anIdFromTheCatalogIsTaken() {
        assertIssue(report(ok.copy(id = ScenarioCatalog.all.first().id)), "id.taken", "id")
    }

    @Test fun titleLengthIsChecked() {
        assertIssue(report(ok.copy(title = "ab")), "title.length", "title")
        assertIssue(report(ok.copy(title = "x".repeat(41))), "title.length", "title")
        assertFalse(report(ok.copy(title = "x".repeat(40))).has("title.length"))
    }

    @Test fun lineBreaksAreRefusedInTitleAndTagline() {
        assertIssue(report(ok.copy(title = "two\nlines")), "title.chars", "title")
        assertIssue(report(ok.copy(tagline = "two\nlines")), "tagline.chars", "tagline")
    }

    @Test fun overlongTextIsAnError() {
        assertIssue(report(ok.copy(tagline = "x".repeat(81))), "tagline.length", "tagline")
        assertIssue(report(ok.copy(story = "x".repeat(601))), "story.length", "story")
    }

    @Test fun missingTaglineAndStoryAreOnlyNotes() {
        val r = report(ok)
        assertIssue(r, "tagline.empty", "tagline", IssueSeverity.NOTE)
        assertIssue(r, "story.empty", "story", IssueSeverity.NOTE)
    }

    // ---- world and company ------------------------------------------------------------------

    @Test fun rivalsAndCountryMustExist() {
        assertIssue(report(ok.copy(world = ok.world.copy(rivals = -1))), "world.rivals", "world.rivals")
        assertIssue(report(ok.copy(world = ok.world.copy(rivals = 17))), "world.rivals", "world.rivals")
        assertIssue(report(ok.copy(world = ok.world.copy(countryIndex = 1))), "world.country", "world.country")
        assertFalse(report(ok.copy(world = WorldSettings(WorldSize.MEDIUM, 3, 2))).has("world.country"))
    }

    @Test fun tooManyRivalsForTheRegionsIsAWarning() {
        assertIssue(report(ok.copy(world = ok.world.copy(rivals = 12))), "world.crowded", "world.rivals", IssueSeverity.WARNING)
    }

    @Test fun companyNameAndCapitalAreChecked() {
        assertIssue(report(ok.copy(company = ok.company.copy(name = "ab"))), "company.name", "company.name")
        assertIssue(report(ok.copy(company = ok.company.copy(capital = 0))), "company.capital", "company.capital")
        assertIssue(report(ok.copy(company = ok.company.copy(capital = -5))), "company.capital", "company.capital")
        assertIssue(report(ok.copy(company = ok.company.copy(capital = 5_000))), "company.poor", "company.capital", IssueSeverity.WARNING)
    }

    @Test fun startingSitesAreCheckedAgainstTheWorld() {
        val d = ok.copy(company = ok.company.copy(starters = listOf(StarterSite("grow_wheat", 9))))
        assertIssue(report(d), "starter.region", "company.starters[0].region")
    }

    @Test fun duplicateStartingSitesWarn() {
        val s = StarterSite("grow_wheat", 0)
        assertIssue(report(ok.copy(company = ok.company.copy(starters = listOf(s, s)))), "starter.duplicate", "company.starters[1]", IssueSeverity.WARNING)
    }

    @Test fun startingSitesNeedRealOpenRecipesWhenContentIsKnown() {
        fun withRecipe(key: String) = ok.copy(company = ok.company.copy(starters = listOf(StarterSite(key, 0))))
        assertIssue(report(withRecipe("no_such_recipe"), content = true), "starter.unknown", "company.starters[0].recipe")
        assertIssue(report(withRecipe("Bad Key"), content = true), "starter.recipe", "company.starters[0].recipe")
        assertFalse(report(withRecipe("grow_wheat"), content = true).has("starter.unknown"))
        val locked = Samples.content.allRecipes.firstOrNull { it.requiredTech != null }
        if (locked != null) assertIssue(report(withRecipe(locked.id.key), content = true), "starter.locked", "company.starters[0].recipe")
    }

    @Test fun extractingFacilitiesCannotBeStartingSites() {
        val recipe = Samples.content.allRecipes.firstOrNull { r -> Samples.content.facilityType(r.facilityType).extracts != null && r.requiredTech == null && r.machines.isEmpty() && Samples.content.facilityType(r.facilityType).requiredTech == null }
        if (recipe != null) {
            val d = ok.copy(company = ok.company.copy(starters = listOf(StarterSite(recipe.id.key, 0))))
            assertTrue(report(d, content = true).has("starter.extractor"))
        }
    }

    @Test fun tooManyStartingSitesAreRefused() {
        val d = ok.copy(company = ok.company.copy(starters = List(7) { StarterSite("grow_wheat", it % 5) }))
        assertIssue(report(d), "starters.count", "company.starters")
    }

    // ---- rules and timing ---------------------------------------------------------------------

    @Test fun ruleValuesOutsideTheirRangeAreErrors() {
        val d = ok.copy(rules = RuleOverrides.of(RuleKey.SPENDING_PROPENSITY to 1L, RuleKey.BANK_MULTIPLE to 99L))
        val r = report(d)
        assertIssue(r, "rules.range", "rules.spending_propensity")
        assertIssue(r, "rules.range", "rules.bank_multiple")
    }

    @Test fun anOverrideEqualToTheDefaultIsANote() {
        val d = ok.copy(rules = RuleOverrides.of(RuleKey.BANK_MULTIPLE to RuleKey.BANK_MULTIPLE.engineDefault()))
        assertIssue(report(d), "rules.redundant", "rules.bank_multiple", IssueSeverity.NOTE)
    }

    @Test fun timingMustGrowFromGoldToTheDeadline() {
        assertIssue(report(ok.copy(timing = Timing(5, 0, 100))), "timing.gold", "timing.gold")
        assertIssue(report(ok.copy(timing = Timing(5, 500, 400))), "timing.silver", "timing.silver")
        assertIssue(report(ok.copy(timing = Timing(1, 100, 400))), "timing.deadline", "timing.years")
        assertIssue(report(ok.copy(timing = Timing(0, 100, 400))), "timing.years", "timing.years")
        assertIssue(report(ok.copy(timing = Timing(41, 100, 400))), "timing.years", "timing.years")
    }

    @Test fun aGoldTimeUnderAQuarterIsAWarning() {
        assertIssue(report(ok.copy(timing = Timing(5, 60, 400))), "timing.gold_tight", "timing.gold", IssueSeverity.WARNING)
    }

    @Test fun theDeadlineMessageSaysHowManyYearsAreNeeded() {
        val issue = report(ok.copy(timing = Timing(1, 100, 700))).withCode("timing.deadline").single()
        assertTrue(issue.fix, issue.fix?.contains("2 years") == true)
    }

    // ---- goals --------------------------------------------------------------------------------

    @Test fun aDraftNeedsGoalsAndAtLeastOneRequiredGoal() {
        assertIssue(report(ok.copy(goals = emptyList())), "goals.none", "goals")
        assertIssue(report(ok.copy(goals = listOf(DraftGoal(GoalKind.NET_WORTH, 900_000L, bonus = true)))), "goals.no_required", "goals")
    }

    @Test fun duplicateGoalKindsPointAtTheSecond() {
        val d = ok.copy(goals = listOf(DraftGoal(GoalKind.NET_WORTH, 900_000L), DraftGoal(GoalKind.CASH, 600_000L), DraftGoal(GoalKind.NET_WORTH, 2_000_000L)))
        assertIssue(report(d), "goal.duplicate", "goals[2].kind")
    }

    @Test fun targetsAndWeightsAreChecked() {
        assertIssue(report(ok.copy(goals = listOf(DraftGoal(GoalKind.NET_WORTH, 0L)))), "goal.target", "goals[0].target")
        assertIssue(report(ok.copy(goals = listOf(DraftGoal(GoalKind.NET_WORTH, 900_000L, weight = 11)))), "goal.weight", "goals[0].weight")
        assertIssue(report(ok.copy(goals = listOf(DraftGoal(GoalKind.NET_WORTH, 900_000L, weight = 0)))), "goal.weight", "goals[0].weight")
    }

    @Test fun marketLeadershipHasATargetOfOne() {
        assertIssue(report(ok.copy(goals = listOf(DraftGoal(GoalKind.MARKET_LEADER, 2)))), "goal.leader_target", "goals[0].target")
        assertFalse(report(ok.copy(goals = listOf(DraftGoal(GoalKind.MARKET_LEADER, 1)))).has("goal.leader_target"))
    }

    @Test fun leadershipWithNoRivalsIsANote() {
        val d = ok.copy(world = ok.world.copy(rivals = 0), goals = listOf(DraftGoal(GoalKind.MARKET_LEADER, 1)))
        assertIssue(report(d), "goal.leader_alone", "goals[0].kind", IssueSeverity.NOTE)
    }

    @Test fun creditRankCannotExceedTheRatingScale() {
        assertIssue(report(ok.copy(goals = listOf(DraftGoal(GoalKind.CREDIT_RANK, 9)))), "goal.rank_range", "goals[0].target")
        assertFalse(report(ok.copy(goals = listOf(DraftGoal(GoalKind.CREDIT_RANK, 8)))).has("goal.rank_range"))
    }

    @Test fun controllingMoreCompaniesThanThereAreRivalsIsImpossible() {
        val d = ok.copy(world = ok.world.copy(rivals = 2), goals = listOf(DraftGoal(GoalKind.COMPANIES, 3)))
        assertIssue(report(d), "goal.companies", "goals[0].target")
    }

    @Test fun contentLimitsGoalCountsWhenContentIsKnown() {
        val d = ok.copy(goals = listOf(DraftGoal(GoalKind.TECHNOLOGIES, 100_000L), DraftGoal(GoalKind.INDUSTRIES, 100_000L)))
        val r = report(d, content = true)
        assertIssue(r, "goal.technologies", "goals[0].target")
        assertIssue(r, "goal.industries", "goals[1].target")
        assertFalse(report(d).has("goal.technologies"))
    }

    @Test fun aTargetBelowTheStartingCapitalIsAlreadyMet() {
        val d = ok.copy(goals = listOf(DraftGoal(GoalKind.NET_WORTH, 100_000L), DraftGoal(GoalKind.FACILITIES, 3)))
        assertIssue(report(d), "goal.met_at_start", "goals[0].target", IssueSeverity.WARNING)
    }

    @Test fun ifEveryRequiredGoalIsMetAtTheStartTheGameIsWonImmediately() {
        val d = ok.copy(goals = listOf(DraftGoal(GoalKind.NET_WORTH, 100_000L), DraftGoal(GoalKind.CASH, 50_000L)))
        assertIssue(report(d), "goals.won_at_start", "goals")
    }

    @Test fun startingSitesCountTowardsFacilityGoals() {
        val d = ok.copy(company = ok.company.copy(starters = listOf(StarterSite("grow_wheat", 0), StarterSite("grow_wheat", 1))), goals = listOf(DraftGoal(GoalKind.FACILITIES, 2)))
        assertTrue(report(d).has("goals.won_at_start"))
    }

    @Test fun aSteepNetWorthTargetIsAmbitious() {
        val d = ok.copy(goals = listOf(DraftGoal(GoalKind.NET_WORTH, 500_000_000L)), timing = Timing(2, 300, 500))
        assertIssue(report(d), "goal.ambitious", "goals[0].target", IssueSeverity.WARNING)
        val easy = ok.copy(goals = listOf(DraftGoal(GoalKind.NET_WORTH, 600_000L)))
        assertFalse(report(easy).has("goal.ambitious"))
    }

    @Test fun oneGoalCarryingMostOfTheWeightIsANote() {
        val d = ok.copy(goals = listOf(DraftGoal(GoalKind.NET_WORTH, 900_000L, weight = 10), DraftGoal(GoalKind.EMPLOYEES, 20, weight = 1)))
        assertIssue(report(d), "goal.lopsided", "goals", IssueSeverity.NOTE)
    }

    @Test fun tooManyGoalsAreRefused() {
        val kinds = GoalKind.values().take(13)
        val d = ok.copy(goals = kinds.map { DraftGoal(it, 900_000L) })
        assertTrue(report(d).has("goals.count"))
    }

    // ---- events, fails, extras -----------------------------------------------------------------

    @Test fun eventDaysMustFallInsideTheGame() {
        val d = ok.copy(events = listOf(ScriptedEvent(-1, EventKind.GRANT, 10), ScriptedEvent(5_000, EventKind.GRANT, 10)))
        val r = report(d)
        assertIssue(r, "event.day", "events[0].day")
        assertIssue(r, "event.late", "events[1].day", IssueSeverity.WARNING)
    }

    @Test fun moneyEventsNeedAPositiveAmountAndAnnouncementsNeedText() {
        val d = ok.copy(events = listOf(ScriptedEvent(5, EventKind.GRANT, 0), ScriptedEvent(6, EventKind.ANNOUNCE, 0, " "), ScriptedEvent(7, EventKind.ANNOUNCE, 5, "hello")))
        val r = report(d)
        assertIssue(r, "event.amount", "events[0].amount")
        assertIssue(r, "event.text", "events[1].text")
        assertIssue(r, "event.amount_ignored", "events[2].amount", IssueSeverity.NOTE)
    }

    @Test fun grantsFarBeyondTheCapitalWarn() {
        val d = ok.copy(events = listOf(ScriptedEvent(5, EventKind.GRANT, 5_000_000)))
        assertIssue(report(d), "events.windfall", "events", IssueSeverity.WARNING)
    }

    @Test fun failureConditionsThatTriggerOnDayOneAreErrors() {
        val d = ok.copy(fails = listOf(
            FailCondition(FailKind.NET_WORTH_BELOW, 600_000), FailCondition(FailKind.CASH_BELOW, 500_000), FailCondition(FailKind.FACILITIES_BELOW, 3),
        ))
        val r = report(d)
        assertIssue(r, "fail.instant", "fails[0].threshold")
        assertIssue(r, "fail.instant", "fails[1].threshold")
        assertIssue(r, "fail.instant", "fails[2].threshold")
    }

    @Test fun aGracePeriodMakesAHighFloorLegal() {
        val d = ok.copy(fails = listOf(FailCondition(FailKind.NET_WORTH_BELOW, 600_000, afterDay = 100)))
        assertFalse(report(d).has("fail.instant"))
    }

    @Test fun onlyTheFirstNetWorthFloorFitsADefinition() {
        val d = ok.copy(fails = listOf(FailCondition(FailKind.NET_WORTH_BELOW, 10_000), FailCondition(FailKind.NET_WORTH_BELOW, 20_000)))
        assertIssue(report(d), "fail.not_in_def", "fails[1]", IssueSeverity.NOTE)
    }

    @Test fun duplicateFailureConditionsWarn() {
        val f = FailCondition(FailKind.CASH_BELOW, 1_000)
        assertIssue(report(ok.copy(fails = listOf(f, f))), "fail.duplicate", "fails[1]", IssueSeverity.WARNING)
    }

    @Test fun contradictoryTagsWarn() {
        assertIssue(report(ok.copy(tags = listOf(DifficultyTag.GENTLE, DifficultyTag.HARSH))), "tags.conflict", "tags", IssueSeverity.WARNING)
        assertIssue(report(ok.copy(tags = listOf(DifficultyTag.SPRINT))), "tags.sprint", "tags", IssueSeverity.WARNING)
        assertIssue(report(ok.copy(tags = listOf(DifficultyTag.MARATHON), timing = Timing(2, 300, 500))), "tags.marathon", "tags", IssueSeverity.WARNING)
        assertIssue(report(ok.copy(tags = emptyList())), "tags.none", "tags", IssueSeverity.NOTE)
    }

    @Test fun hintsAreChecked() {
        val d = ok.copy(hints = listOf(" ", "x".repeat(141), "Same", "same"))
        val r = report(d)
        assertIssue(r, "hint.empty", "hints[0]")
        assertIssue(r, "hint.length", "hints[1]")
        assertIssue(r, "hint.duplicate", "hints[3]", IssueSeverity.WARNING)
    }

    @Test fun packSpecsMustBeWrittenAsIdColonVersion() {
        assertIssue(report(ok.copy(packs = listOf("base-1.0"))), "pack.format", "packs[0]")
        assertFalse(report(ok.copy(packs = listOf("base:1.0"))).has("pack.format"))
    }

    // ---- the report ----------------------------------------------------------------------------

    @Test fun issuesAreOrderedErrorsFirstThenByPath() {
        val d = ok.copy(id = "BAD", title = "x", tagline = "")
        val r = report(d)
        val severities = r.issues.map { it.severity.ordinal }
        assertEquals(severities.sorted(), severities)
    }

    @Test fun atFindsIssuesBelowAPath() {
        val d = ok.copy(goals = listOf(DraftGoal(GoalKind.NET_WORTH, 0L)))
        val r = report(d)
        assertTrue(r.at("goals[0]").any { it.code == "goal.target" })
        assertTrue(r.at("goals").isNotEmpty())
        assertTrue(r.at("title").none { it.code == "goal.target" })
    }

    @Test fun summaryCountsEachKind() {
        val r = report(ok.copy(title = "x"))
        assertEquals("${r.errors.size} errors, ${r.warnings.size} warnings, ${r.notes.size} notes", r.summary())
    }

    @Test fun validationIsDeterministic() {
        assertEquals(report(Samples.full(), true).issues, report(Samples.full(), true).issues)
    }
}
