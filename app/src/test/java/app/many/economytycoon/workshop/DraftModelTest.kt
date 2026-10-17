package app.many.economytycoon.workshop

import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.simulation.SimConfig
import app.many.economytycoon.world.WorldSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftModelTest {
    @Test fun normalisedSortsEventsByDayKeepingTheOrderOfEqualDays() {
        val d = Samples.full().copy(events = listOf(
            ScriptedEvent(50, EventKind.ANNOUNCE, 0, "b"), ScriptedEvent(10, EventKind.ANNOUNCE, 0, "a"), ScriptedEvent(50, EventKind.ANNOUNCE, 0, "c"),
        )).normalised()
        assertEquals(listOf("a", "b", "c"), d.events.map { it.text })
    }

    @Test fun normalisedSortsAndDeduplicatesTagsAndPacks() {
        val d = Samples.minimal().copy(
            tags = listOf(DifficultyTag.HARSH, DifficultyTag.GENTLE, DifficultyTag.HARSH),
            packs = listOf("b:1.0", "a:1.0", "b:1.0"),
        ).normalised()
        assertEquals(listOf(DifficultyTag.GENTLE, DifficultyTag.HARSH), d.tags)
        assertEquals(listOf("a:1.0", "b:1.0"), d.packs)
    }

    @Test fun normalisingTwiceChangesNothing() {
        val d = Samples.full()
        assertEquals(d, d.normalised())
    }

    @Test fun theHashIgnoresOrderThatNormalisationRemoves() {
        val a = Samples.full()
        val b = a.copy(events = a.events.reversed(), tags = a.tags.reversed(), packs = a.packs.reversed())
        assertEquals(a.stateHash(), b.stateHash())
    }

    @Test fun everyFieldChangesTheHash() {
        val base = Samples.full()
        val variants = listOf(
            base.copy(id = "other"), base.copy(title = "Other"), base.copy(tagline = "x"), base.copy(story = "x"), base.copy(seed = 1L),
            base.copy(world = base.world.copy(size = WorldSize.MEDIUM)), base.copy(world = base.world.copy(rivals = 9)), base.copy(world = base.world.copy(countryIndex = 1)),
            base.copy(company = base.company.copy(name = "X Ltd")), base.copy(company = base.company.copy(capital = 1L)),
            base.copy(company = base.company.copy(starters = emptyList())), base.copy(rules = RuleOverrides()),
            base.copy(events = emptyList()), base.copy(goals = base.goals.take(1)), base.copy(fails = emptyList()),
            base.copy(timing = base.timing.copy(years = 9)), base.copy(tags = emptyList()), base.copy(hints = emptyList()), base.copy(packs = emptyList()),
            base.copy(goals = base.goals.map { it.copy(weight = it.weight + 1) }), base.copy(goals = base.goals.map { it.copy(bonus = !it.bonus) }),
        )
        val hashes = variants.map { it.stateHash() } + base.stateHash()
        assertEquals("all variants hash differently", hashes.size, hashes.toSet().size)
    }

    @Test fun theHashIsStableAcrossRuns() {
        // Pinned so that a change to the canonical layout is noticed: shared codes and baselines depend on it.
        assertEquals(GOLDEN_FULL_HASH, Samples.full().stateHash())
        assertEquals(64, Samples.minimal().stateHash().length)
    }

    @Test fun validIdsAreLowercaseWordsWithSingleDashes() {
        assertTrue(ScenarioDraft.validId("first-light"))
        assertTrue(ScenarioDraft.validId("a1"))
        assertFalse(ScenarioDraft.validId(""))
        assertFalse(ScenarioDraft.validId("Upper"))
        assertFalse(ScenarioDraft.validId("-lead"))
        assertFalse(ScenarioDraft.validId("trail-"))
        assertFalse(ScenarioDraft.validId("dou--ble"))
        assertFalse(ScenarioDraft.validId("has space"))
        assertFalse(ScenarioDraft.validId("a".repeat(41)))
    }

    @Test fun requiredGoalsLeaveOutBonusGoals() {
        assertEquals(listOf(GoalKind.NET_WORTH, GoalKind.FACILITIES), Samples.full().requiredGoals.map { it.kind })
    }

    @Test fun deadlineIsYearsTimesThreeHundredSixty() {
        assertEquals(1_440, Samples.full().deadlineDays)
    }

    @Test fun ruleKeysKnowTheEngineDefaults() {
        val c = SimConfig(seed = 0L)
        assertEquals(c.householdCashPerCapita, RuleKey.HOUSEHOLD_CASH.engineDefault())
        assertEquals(c.spendingPropensityPpm, RuleKey.SPENDING_PROPENSITY.engineDefault())
        assertEquals(c.governmentTreasury.wholeUnits(), RuleKey.TREASURY.engineDefault())
        assertEquals(c.aiStartingCapital.wholeUnits(), RuleKey.AI_CAPITAL.engineDefault())
        assertEquals(c.publicSpendingPerCapita.raw, RuleKey.PUBLIC_SPENDING.engineDefault())
    }

    @Test fun everyRuleDefaultLiesInsideItsRange() {
        for (k in RuleKey.values()) assertTrue("${k.key} default in range", k.inRange(k.engineDefault()))
    }

    @Test fun overridesApplyOnTopOfAConfig() {
        val base = SimConfig(seed = 3L)
        val cfg = RuleOverrides.of(RuleKey.SPENDING_PROPENSITY to 30_000L, RuleKey.TREASURY to 5_000L).applyTo(base)
        assertEquals(30_000L, cfg.spendingPropensityPpm)
        assertEquals(5_000L, cfg.governmentTreasury.wholeUnits())
        assertEquals(base.householdCashPerCapita, cfg.householdCashPerCapita)
    }

    @Test fun overridesAreKeptInKeyOrderWhateverTheInsertionOrder() {
        val a = RuleOverrides().with(RuleKey.PUBLIC_SPENDING, 1).with(RuleKey.HOUSEHOLD_CASH, 2)
        val b = RuleOverrides().with(RuleKey.HOUSEHOLD_CASH, 2).with(RuleKey.PUBLIC_SPENDING, 1)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(listOf(RuleKey.HOUSEHOLD_CASH, RuleKey.PUBLIC_SPENDING), a.entries().map { it.first })
    }

    @Test fun overridesCanBeRemovedAndRead() {
        val o = RuleOverrides.of(RuleKey.IMPORT_MARKUP to 5L)
        assertEquals(5L, o[RuleKey.IMPORT_MARKUP])
        assertNull(o.without(RuleKey.IMPORT_MARKUP)[RuleKey.IMPORT_MARKUP])
        assertEquals(RuleKey.IMPORT_MARKUP.engineDefault(), o.without(RuleKey.IMPORT_MARKUP).valueOf(RuleKey.IMPORT_MARKUP))
        assertTrue(RuleOverrides().isEmpty())
    }

    @Test fun keysAreFoundByTheirTextFormIgnoringCase() {
        assertEquals(RuleKey.BANK_MULTIPLE, RuleKey.byKey("Bank_Multiple"))
        assertNull(RuleKey.byKey("nonsense"))
        assertEquals(DifficultyTag.PUZZLE, DifficultyTag.byKey("PUZZLE"))
        assertEquals(EventKind.RIVAL_GRANT, EventKind.byKey("rival_grant"))
        assertEquals(FailKind.CASH_BELOW, FailKind.byKey("cash_below"))
    }

    @Test fun onlyAnnouncementsCarryNoMoney() {
        assertFalse(EventKind.ANNOUNCE.hasAmount)
        for (k in EventKind.values().filter { it != EventKind.ANNOUNCE }) assertTrue(k.hasAmount)
    }

    @Test fun differentDraftsDoNotEqualEachOther() {
        assertNotEquals(Samples.full(), Samples.minimal())
    }

    private companion object { const val GOLDEN_FULL_HASH = "e457fb2cbe5d6a171a8d2be7f89cec3301af0a2b58a334ef928d1a01e5cccc68" }
}
