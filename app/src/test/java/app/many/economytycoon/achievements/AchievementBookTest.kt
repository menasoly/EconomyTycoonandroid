package app.many.economytycoon.achievements

import app.many.economytycoon.scenarios.Medal
import app.many.economytycoon.scenarios.PlayerProfile
import app.many.economytycoon.scenarios.ScenarioCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementBookTest {
    @Test fun totalsOnlyGrow() {
        val s = LifetimeStats()
        s.add(Stat.TENDERS_WON); s.add(Stat.TENDERS_WON, 4)
        assertEquals(5L, s[Stat.TENDERS_WON]); assertEquals(0L, s[Stat.PARCELS_SOLD])
        var failed = false
        try { s.add(Stat.TENDERS_WON, -1) } catch (e: IllegalArgumentException) { failed = true }
        assertTrue(failed)
        assertEquals(listOf(Stat.TENDERS_WON to 5L), s.nonZero())
    }

    @Test fun statsCompareByContent() {
        val a = LifetimeStats(); val b = LifetimeStats()
        assertEquals(a, b)
        a.add(Stat.DAYS_PLAYED)
        assertFalse(a == b)
        b.add(Stat.DAYS_PLAYED)
        assertEquals(a, b); assertEquals(a.hashCode(), b.hashCode())
    }

    @Test fun anAchievementUnlocksOnceAndKeepsItsDay() {
        val b = AchievementBook()
        assertTrue(b.unlock("first-million", 120))
        assertFalse(b.unlock("first-million", 300))
        assertEquals(120L, b.unlockedAt("first-million")); assertTrue(b.isUnlocked("first-million"))
        assertNull(b.unlockedAt("six-figures")); assertEquals(1, b.count)
    }

    @Test fun unknownAchievementsAreRefused() {
        val b = AchievementBook()
        assertFalse(b.unlock("made-up", 1)); assertEquals(0, b.count)
    }

    @Test fun pointsAddUpByTier() {
        val b = AchievementBook()
        b.unlock("six-figures", 1); b.unlock("first-million", 2); b.unlock("ten-million", 3)
        assertEquals(1 + 2 + 3, b.points())
        assertEquals(setOf("six-figures", "first-million", "ten-million"), b.unlockedIn(Category.WEALTH).map { it.id }.toSet())
        assertTrue(b.unlockedIn(Category.PROPERTY).isEmpty())
    }

    @Test fun meterAchievementsReadTheGame() {
        val p = PlayerProfile()
        val a = AchievementCatalog.require("first-million")
        assertFalse(AchievementEvaluator.progress(a, p) { 999_999L }.done)
        assertTrue(AchievementEvaluator.progress(a, p) { 1_000_000L }.done)
        assertEquals(0.5, AchievementEvaluator.progress(a, p) { 500_000L }.ratio, 1e-9)
    }

    @Test fun totalAchievementsReadTheProfile() {
        val p = PlayerProfile()
        val a = AchievementCatalog.require("landlord")
        p.stats.add(Stat.LEASES_SIGNED, 4)
        assertFalse(AchievementEvaluator.progress(a, p) { 0L }.done)
        p.stats.add(Stat.LEASES_SIGNED)
        assertTrue(AchievementEvaluator.progress(a, p) { 0L }.done)
    }

    @Test fun medalAchievementsCountScenariosByRank() {
        val p = PlayerProfile()
        val ids = ScenarioCatalog.all.map { it.id }
        p.won(ids[0], Medal.GOLD, 100); p.won(ids[1], Medal.SILVER, 100); p.won(ids[2], Medal.BRONZE, 100)
        assertEquals(1, AchievementEvaluator.medalsAtLeast(p, Medal.GOLD))
        assertEquals(2, AchievementEvaluator.medalsAtLeast(p, Medal.SILVER))
        assertEquals(3, AchievementEvaluator.medalsAtLeast(p, Medal.BRONZE))
        val earned = AchievementEvaluator.earnable(p) { 0L }.map { it.id }
        assertTrue("first-win" in earned); assertFalse("campaigner" in earned); assertFalse("gold-standard" in earned)
    }

    @Test fun earnableLeavesOutWhatIsAlreadyUnlocked() {
        val p = PlayerProfile()
        val first = AchievementEvaluator.earnable(p) { 10_000_000_000L }.map { it.id }
        assertTrue("first-million" in first && "number-one" in first)
        p.achievements.unlock("first-million", 1)
        assertFalse("first-million" in AchievementEvaluator.earnable(p) { 10_000_000_000L }.map { it.id })
    }

    @Test fun anEmptyGameEarnsNothingFromMeters() {
        assertTrue(AchievementEvaluator.earnable(PlayerProfile()) { 0L }.isEmpty())
    }

    @Test fun scenarioResultsBumpTheLifetimeTotals() {
        val p = PlayerProfile(); val id = ScenarioCatalog.all[0].id
        p.won(id, Medal.BRONZE, 500); p.lost(id); p.lost(id)
        assertEquals(1L, p.stats[Stat.SCENARIOS_WON]); assertEquals(2L, p.stats[Stat.SCENARIOS_LOST])
    }

    @Test fun progressRatioIsClamped() {
        assertEquals(1.0, AchievementProgress(50, 10).ratio, 0.0)
        assertEquals(0.0, AchievementProgress(-5, 10).ratio, 0.0)
        assertEquals(1.0, AchievementProgress(0, 0).ratio, 0.0)
    }
}
