package app.many.economytycoon.workshop

import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.world.WorldSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftTextTest {
    private fun parse(text: String) = DraftParser.parse(text)

    private fun parsed(text: String): ScenarioDraft {
        val r = parse(text)
        assertTrue("unexpected problems: ${r.issues}", r.ok)
        return r.draft ?: error("no draft")
    }

    private fun firstError(text: String): ParseIssue = parse(text).errors.firstOrNull() ?: error("expected an error in:\n$text")

    // ---- printing --------------------------------------------------------------------------------

    @Test fun theMinimalDraftPrintsCompactly() {
        val text = DraftPrinter.print(Samples.minimal())
        assertEquals(
            """
            scenario tiny {
              title "Tiny Test"
              seed 1
              world small rivals 3 country 0
              company "Founders Ltd" capital 500_000
              timing years 5 gold 1000 silver 1500
              tags standard
              goal net_worth 1_000_000
            }

            """.trimIndent(),
            text,
        )
    }

    @Test fun theFullDraftPrintsEveryStatementKind() {
        val text = DraftPrinter.print(Samples.full())
        for (kw in listOf("title", "tagline", "story", "seed", "world", "company", "starter", "rule", "timing", "tags", "pack", "goal", "fail", "event", "hint")) {
            assertTrue("has $kw", text.lines().any { it.trim().startsWith("$kw ") })
        }
    }

    @Test fun bonusGoalsAndWeightsArePrinted() {
        val text = DraftPrinter.print(Samples.full())
        assertTrue(text.contains("goal net_worth 900_000 weight 3"))
        assertTrue(text.contains("goal bonus cash 300_000"))
        assertFalse(text.contains("weight 1"))
    }

    @Test fun eventsAreInDayOrderInTheText() {
        val days = DraftPrinter.print(Samples.full()).lines().filter { it.trim().startsWith("event") }.map { it.trim().split(' ')[1].toInt() }
        assertEquals(days.sorted(), days)
    }

    @Test fun stringsAreEscaped() {
        assertEquals("\"a \\\"b\\\" c\\\\d\\ne\\tf\"", DraftPrinter.quote("a \"b\" c\\d\ne\tf"))
        assertEquals("\"\\u0001\"", DraftPrinter.quote("\u0001"))
    }

    @Test fun numbersGetGroupedFromFiveDigits() {
        assertEquals("9999", DraftPrinter.num(9_999))
        assertEquals("10_000", DraftPrinter.num(10_000))
        assertEquals("-1_234_567", DraftPrinter.num(-1_234_567))
        assertEquals("0", DraftPrinter.num(0))
    }

    @Test fun printingIsDeterministic() {
        assertEquals(DraftPrinter.print(Samples.full()), DraftPrinter.print(Samples.full()))
    }

    // ---- round trips ------------------------------------------------------------------------------

    @Test fun printThenParseGivesBackTheDraft() {
        val d = Samples.full()
        assertEquals(d, parsed(DraftPrinter.print(d)))
    }

    @Test fun everyTemplateAndBuiltInRoundTrips() {
        for (d in DraftTemplates.all.map { it.create() } + DraftTemplates.builtIns() + Samples.cheap()) {
            assertEquals(d.id, d, parsed(DraftPrinter.print(d)))
        }
    }

    @Test fun parseThenPrintIsStable() {
        val once = DraftPrinter.print(Samples.full())
        assertEquals(once, DraftPrinter.print(parsed(once)))
    }


    @Test fun awkwardTextRoundTrips() {
        val d = Samples.minimal().copy(
            title = "Quote \" and \\ slash", tagline = "tab\there", story = "line1\nline2\n\nline4 # not a comment // either",
            hints = listOf("unicode \u00e9\u2603", "control \u0001\u007f"),
            events = listOf(ScriptedEvent(3, EventKind.ANNOUNCE, 0, "brace } and { inside")),
        )
        assertEquals(d, parsed(DraftPrinter.print(d)))
    }

    @Test fun extremeNumbersRoundTrip() {
        val d = Samples.minimal().copy(seed = Long.MIN_VALUE + 1, company = Samples.minimal().company.copy(capital = Long.MAX_VALUE))
        assertEquals(d, parsed(DraftPrinter.print(d)))
    }

    // ---- reading by hand -------------------------------------------------------------------------

    @Test fun commentsBlankLinesAndIndentationAreIgnored() {
        val d = parsed(
            """
            # my scenario
            scenario   hand-made   {   // opening
                title    "Hand Made"   # trailing
            
              seed 5
              goal   net_worth   2_000_000
            }
            """.trimIndent(),
        )
        assertEquals("hand-made", d.id)
        assertEquals("Hand Made", d.title)
        assertEquals(5L, d.seed)
        assertEquals(DraftGoal(GoalKind.NET_WORTH, 2_000_000), d.goals.single())
    }

    @Test fun keywordsAndValuesAreCaseInsensitive() {
        val d = parsed("scenario x1 {\n TITLE \"Shout\"\n World MEDIUM Rivals 4\n goal NET_WORTH 5_000_000\n tags GENTLE\n}")
        assertEquals(WorldSize.MEDIUM, d.world.size)
        assertEquals(4, d.world.rivals)
        assertEquals(listOf(DifficultyTag.GENTLE), d.tags)
    }

    @Test fun colonsAndEqualsSignsAfterKeywordsAreTolerated() {
        val d = parsed("scenario x1 {\n title: \"Colon Title\"\n seed = 77\n}")
        assertEquals("Colon Title", d.title)
        assertEquals(77L, d.seed)
    }

    @Test fun aBareWordCanStandInForAShortString() {
        val d = parsed("scenario x1 {\n title Plain\n}")
        assertEquals("Plain", d.title)
    }

    @Test fun optionsMayComeInAnyOrder() {
        val d = parsed("scenario x1 {\n title \"Order Test\"\n world large country 3 rivals 7\n timing silver 900 years 4 gold 500\n goal net_worth 9_000_000 weight 4\n}")
        assertEquals(WorldSettings(WorldSize.LARGE, 7, 3), d.world)
        assertEquals(Timing(4, 500, 900), d.timing)
        assertEquals(4, d.goals.single().weight)
    }

    @Test fun leftOutPartsTakeTheNewDraftDefaults() {
        val d = parsed("scenario bare {\n title \"Barely\"\n goal cash 700_000\n}")
        val base = DraftDefaults.blank()
        assertEquals(base.world, d.world)
        assertEquals(base.timing, d.timing)
        assertEquals(base.company.capital, d.company.capital)
        assertEquals(base.company.name, d.company.name)
    }

    @Test fun partialTimingKeepsTheDefaultsForTheRest() {
        val d = parsed("scenario x1 {\n title \"Timing\"\n timing years 7\n goal cash 700_000\n}")
        assertEquals(Timing(7, DraftDefaults.blank().timing.goldDays, DraftDefaults.blank().timing.silverDays), d.timing)
    }

    @Test fun eventsParseAllFourKinds() {
        val d = parsed("scenario x1 {\n title \"Events\"\n event 5 grant 1_000 \"gift\"\n event 6 levy 500\n event 7 rival_grant 2_000\n event 8 announce \"Hello\"\n}")
        assertEquals(
            listOf(
                ScriptedEvent(5, EventKind.GRANT, 1_000, "gift"), ScriptedEvent(6, EventKind.LEVY, 500), ScriptedEvent(7, EventKind.RIVAL_GRANT, 2_000),
                ScriptedEvent(8, EventKind.ANNOUNCE, 0, "Hello"),
            ),
            d.events,
        )
    }

    @Test fun parsedEventsAreNormalisedByDay() {
        val d = parsed("scenario x1 {\n title \"Events\"\n event 9 announce \"late\"\n event 1 announce \"early\"\n}")
        assertEquals(listOf("early", "late"), d.events.map { it.text })
    }

    @Test fun unicodeEscapesAreReadAndCurlyQuotesAreAccepted() {
        val d = parsed("scenario x1 {\n title \"Sn\\u00f6w\"\n tagline \u201Ccurly\u201D\n}")
        assertEquals("Sn\u00f6w", d.title)
        assertEquals("curly", d.tagline)
    }

    // ---- errors with places ----------------------------------------------------------------------

    @Test fun anEmptyTextIsAnErrorAtTheStart() {
        val e = firstError("")
        assertEquals(TextPosition(1, 1), e.at)
        assertNull(parse("").draft)
    }

    @Test fun textThatDoesNotStartWithScenarioIsRefused() {
        val e = firstError("hello there {\n}")
        assertEquals(TextPosition(1, 1), e.at)
        assertTrue(e.message, e.message.contains("scenario"))
    }

    @Test fun aMissingIdIsReportedWhereItShouldBe() {
        val e = firstError("scenario {\n}")
        assertTrue(e.message, e.message.contains("id"))
        assertEquals(1, e.at.line)
    }

    @Test fun anUnknownStatementSuggestsTheNearestKeyword() {
        val e = firstError("scenario x1 {\n  tittle \"A\"\n}")
        assertEquals(TextPosition(2, 3), e.at)
        assertTrue(e.message, e.message.contains("did you mean 'title'"))
    }

    @Test fun aWildStatementGetsNoSuggestion() {
        val e = firstError("scenario x1 {\n  zzzzzzzz 5\n}")
        assertFalse(e.message, e.message.contains("did you mean"))
    }

    @Test fun aBadNumberIsPointedAt() {
        val e = firstError("scenario x1 {\n  seed abc\n}")
        assertEquals(TextPosition(2, 8), e.at)
        assertTrue(e.message, e.message.contains("'abc'"))
    }

    @Test fun aMissingValueIsReportedJustAfterTheLastToken() {
        val e = firstError("scenario x1 {\n  goal net_worth\n}")
        assertEquals(TextPosition(2, 17), e.at)
        assertTrue(e.message, e.message.contains("target"))
    }

    @Test fun anOptionWithoutAValueIsReported() {
        val e = firstError("scenario x1 {\n  world small rivals\n}")
        assertEquals(2, e.at.line)
        assertTrue(e.message, e.message.contains("rivals"))
    }

    @Test fun anUnknownOptionSuggestsTheNearestAndSkipsItsNumber() {
        val r = parse("scenario x1 {\n  fail cash_below 5 afte 3\n}")
        assertEquals(1, r.errors.size)
        assertTrue(r.errors[0].message, r.errors[0].message.contains("did you mean 'after'"))
        assertEquals(TextPosition(2, 21), r.errors[0].at)
    }

    @Test fun anUnknownKindsAreNamedWithTheChoices() {
        assertTrue(firstError("scenario x1 {\n  world huge2\n}").message.contains("small"))
        assertTrue(firstError("scenario x1 {\n  goal nett_worth 5\n}").message.contains("did you mean 'net_worth'"))
        assertTrue(firstError("scenario x1 {\n  tags gentel\n}").message.contains("did you mean 'gentle'"))
        assertTrue(firstError("scenario x1 {\n  event 5 gift 5\n}").message.contains("grant"))
        assertTrue(firstError("scenario x1 {\n  fail mood 5\n}").message.contains("net_worth_below"))
        assertTrue(firstError("scenario x1 {\n  rule spendng 5\n}").message.contains("did you mean 'spending_propensity'"))
    }

    @Test fun anUnterminatedStringIsReportedAtItsOpeningQuote() {
        val e = firstError("scenario x1 {\n  title \"oops\n  seed 5\n}")
        assertEquals(TextPosition(2, 9), e.at)
        assertTrue(e.message, e.message.contains("closing quote"))
    }

    @Test fun aMissingClosingBraceIsReportedAtTheEnd() {
        val e = firstError("scenario x1 {\n  seed 5\n")
        assertTrue(e.message, e.message.contains("not closed"))
        assertEquals(2, e.at.line)
    }

    @Test fun textAfterTheClosingBraceIsAnError() {
        val e = firstError("scenario x1 {\n}\nseed 5\n")
        assertEquals(3, e.at.line)
        assertTrue(e.message, e.message.contains("closing"))
    }

    @Test fun extraTokensAtTheEndOfAStatementAreReported() {
        val e = firstError("scenario x1 {\n  seed 5 6\n}")
        assertEquals(TextPosition(2, 10), e.at)
        assertTrue(e.message, e.message.contains("'6'"))
    }

    @Test fun oneBadLineDoesNotHideTheOthers() {
        val r = parse("scenario x1 {\n  tittle \"A\"\n  seed abc\n  title \"Good Title\"\n  goal cash 700_000\n}")
        assertEquals(listOf(2, 3), r.errors.map { it.at.line })
        val d = r.draft
        assertNotNull(d)
        assertEquals("Good Title", d?.title)
        assertEquals(1, d?.goals?.size)
        assertFalse(r.ok)
    }

    @Test fun anIntegerTooLargeForAnIntFieldIsReported() {
        val e = firstError("scenario x1 {\n  world small rivals 99999999999\n}")
        assertTrue(e.message, e.message.contains("too large"))
        assertEquals(TextPosition(2, 22), e.at)
    }

    @Test fun repeatedSingleStatementsWarnAndTheLastWins() {
        val r = parse("scenario x1 {\n  title \"First One\"\n  title \"Second One\"\n  goal cash 700_000\n}")
        assertTrue(r.ok)
        assertEquals("Second One", r.draft?.title)
        assertEquals(1, r.warnings.size)
        assertEquals(3, r.warnings[0].at.line)
    }

    @Test fun unknownEscapesWarnAndAreKept() {
        val r = parse("scenario x1 {\n  title \"a\\qb\"\n  goal cash 700_000\n}")
        assertEquals("a\\qb", r.draft?.title)
        assertEquals(1, r.warnings.size)
        assertEquals(TextPosition(2, 11), r.warnings[0].at)
    }

    @Test fun aMissingTitleIsAWarningNotAnError() {
        val r = parse("scenario x1 {\n  goal cash 700_000\n}")
        assertTrue(r.ok)
        assertTrue(r.warnings.any { it.message.contains("title") })
    }

    @Test fun veryLongTextIsRefusedAtOnce() {
        val r = parse("scenario x {\n" + "  hint \"x\"\n".repeat(30_000) + "}")
        assertNull(r.draft)
        assertTrue(r.errors[0].message.contains("longer than"))
    }

    // ---- source map and validation locations --------------------------------------------------------

    @Test fun theSourceMapKnowsWhereFieldsWere() {
        val text = DraftPrinter.print(Samples.full())
        val map = parse(text).sourceMap
        val lines = text.lines()
        fun lineOf(path: String) = map.locate(path)?.line?.let { lines[it - 1].trim() }
        assertTrue(requireNotNull(lineOf("title")).startsWith("title"))
        assertTrue(requireNotNull(lineOf("goals[0].target")).startsWith("goal net_worth"))
        assertTrue(requireNotNull(lineOf("goals[2].target")).startsWith("goal bonus cash"))
        assertTrue(requireNotNull(lineOf("events[1].amount")).startsWith("event"))
        assertTrue(requireNotNull(lineOf("company.starters[1].region")).startsWith("starter raise_poultry"))
        assertTrue(requireNotNull(lineOf("rules.spending_propensity")).startsWith("rule spending_propensity"))
        assertTrue(requireNotNull(lineOf("timing.gold")).startsWith("timing"))
        assertTrue(requireNotNull(lineOf("hints[1]")).startsWith("hint \"Watch"))
    }

    @Test fun theColumnPointsAtTheValueNotTheKeyword() {
        val text = "scenario x1 {\n  goal net_worth 900_000 weight 3\n}"
        val map = parse(text).sourceMap
        assertEquals(TextPosition(2, 18), map.locate("goals[0].target"))
        assertEquals(TextPosition(2, 33), map.locate("goals[0].weight"))
    }

    @Test fun anUnmappedPathFallsBackToItsParent() {
        val map = parse("scenario x1 {\n  goal cash 5_000\n}").sourceMap
        assertEquals(map.locate("goals[0]"), map.locate("goals[0].something"))
        assertNull(map.locate("nothing.here"))
    }

    @Test fun validationFindingsGetTextPositions() {
        val text = "scenario x1 {\n  title \"Located\"\n  goal net_worth 0\n  timing years 1 gold 500 silver 400\n}"
        val r = parse(text)
        val report = DraftValidator.validate(requireNotNull(r.draft))
        val located = DraftParser.locate(report, r.sourceMap)
        val target = located.first { it.issue.code == "goal.target" }
        assertEquals(TextPosition(3, 18), target.at)
        val silver = located.first { it.issue.code == "timing.silver" }
        assertEquals(4, silver.at?.line)
        assertTrue(target.toString(), target.toString().startsWith("line 3, column 18:"))
    }

    @Test fun issuesForDraftsNotFromTextHaveNoPosition() {
        val report = DraftValidator.validate(Samples.minimal().copy(title = "x"))
        val located = DraftParser.locate(report, SourceMap(emptyMap()))
        assertTrue(located.all { it.at == null })
        assertEquals(located.first().issue.toString(), located.first().toString())
    }

    @Test fun parseIssuesPrintTheirPosition() {
        assertEquals("line 2, column 3: boom", ParseIssue(TextPosition(2, 3), ParseSeverity.ERROR, "boom").toString())
    }
}
