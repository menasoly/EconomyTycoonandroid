package app.many.economytycoon.ui.present

import app.many.economytycoon.achievements.AchievementCatalog
import app.many.economytycoon.achievements.Category
import app.many.economytycoon.achievements.Stat
import app.many.economytycoon.achievements.Tier
import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.scenarios.PlayerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsPresenterTest {
    private val none: (GoalKind) -> Long = { 0L }

    @Test fun aFreshProfileShowsEverythingLocked() {
        val m = AchievementsPresenter.build(PlayerProfile(), none)
        assertEquals("0 of ${AchievementCatalog.maxPoints} points", m.points)
        assertEquals("0 of ${AchievementCatalog.all.size} unlocked", m.summary)
        assertEquals(AchievementCatalog.all.size, m.groups.sumOf { it.items.size })
        assertTrue(m.groups.all { g -> g.items.none { it.unlocked } && g.unlockedCount == 0 })
        assertTrue(m.stats.isEmpty())
    }

    @Test fun groupsFollowTheCategories() {
        val m = AchievementsPresenter.build(PlayerProfile(), none)
        assertEquals(Category.values().map { it.label }, m.groups.map { it.name })
    }

    @Test fun unlockedOnesAreDoneWithToneByTier() {
        val p = PlayerProfile()
        p.achievements.unlock("six-figures", 1); p.achievements.unlock("first-million", 1); p.achievements.unlock("ten-million", 1)
        val items = AchievementsPresenter.build(p, none).groups.flatMap { it.items }.associateBy { it.id }
        assertEquals(Tone.WARN, items.getValue("six-figures").tone)
        assertEquals(Tone.INFO, items.getValue("first-million").tone)
        assertEquals(Tone.GOOD, items.getValue("ten-million").tone)
        assertEquals("Done", items.getValue("first-million").progress); assertEquals(1.0, items.getValue("first-million").ratio, 0.0)
        assertEquals(Tone.NEUTRAL, items.getValue("number-one").tone)
        assertEquals("6 of ${AchievementCatalog.maxPoints} points", AchievementsPresenter.build(p, none).points)
    }

    @Test fun moneyProgressReadsInCompactUnits() {
        val p = PlayerProfile()
        val item = AchievementsPresenter.item(AchievementCatalog.require("first-million"), p) { 250_000L }
        assertEquals(0.25, item.ratio, 1e-9)
        assertTrue(item.progress.endsWith(" of ${app.many.economytycoon.session.Formatting.compactMoney(app.many.economytycoon.core.Money.units(1_000_000))}"))
    }

    @Test fun countProgressUsesPlainNumbers() {
        val p = PlayerProfile()
        assertEquals("3 of 5", AchievementsPresenter.item(AchievementCatalog.require("small-chain"), p) { 3L }.progress)
        p.stats.add(Stat.LEASES_SIGNED, 2)
        assertEquals("2 of 5", AchievementsPresenter.item(AchievementCatalog.require("landlord"), p, none).progress)
    }

    @Test fun leadershipReadsYesOrNot() {
        val a = AchievementCatalog.require("number-one")
        assertEquals("Not yet", AchievementsPresenter.item(a, PlayerProfile(), none).progress)
        assertEquals("Done", AchievementsPresenter.item(a, PlayerProfile()) { 1L }.progress)
    }

    @Test fun valueNeverOvershootsTheTarget() {
        val item = AchievementsPresenter.item(AchievementCatalog.require("small-chain"), PlayerProfile()) { 4L }
        assertEquals("4 of 5", item.progress)
        assertFalse(item.unlocked)
    }

    @Test fun lifetimeTotalsAreListedWithLabels() {
        val p = PlayerProfile(); p.stats.add(Stat.DAYS_PLAYED, 1_250); p.stats.add(Stat.TENDERS_WON, 2)
        val facts = AchievementsPresenter.build(p, none).stats
        assertEquals(listOf(Stat.DAYS_PLAYED.label, Stat.TENDERS_WON.label), facts.map { it.label })
        assertEquals(app.many.economytycoon.session.Formatting.grouped(1_250), facts[0].value)
    }

    @Test fun tiersMapToTones() {
        assertEquals(Tone.GOOD, AchievementsPresenter.tierTone(Tier.GOLD))
        assertEquals(Tone.INFO, AchievementsPresenter.tierTone(Tier.SILVER))
        assertEquals(Tone.WARN, AchievementsPresenter.tierTone(Tier.BRONZE))
    }

    @Test fun recentListsNewestFirst() {
        val p = PlayerProfile()
        p.achievements.unlock("six-figures", 10); p.achievements.unlock("first-million", 50); p.achievements.unlock("open-for-business", 30)
        assertEquals(listOf("First Million", "Open for Business"), AchievementsPresenter.recent(p, 2))
        assertTrue(AchievementsPresenter.recent(PlayerProfile()).isEmpty())
    }

    @Test fun aSessionBuildsOnlyWhenAProfileIsAttached() {
        val s = PresentSupport.session("Showing Off Co")
        assertNull(AchievementsPresenter.build(s))
        s.achievements.profile = PlayerProfile()
        assertNotNull(AchievementsPresenter.build(s))
        val m = AchievementsPresenter.build(s)!!
        assertTrue(m.groups.flatMap { it.items }.first { it.id == "open-for-business" }.ratio >= 0.0)
    }
}
