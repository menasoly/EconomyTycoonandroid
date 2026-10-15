package app.many.economytycoon.ui.present

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.markets.MarketKey
import app.many.economytycoon.watch.FakeNames
import app.many.economytycoon.watch.RuleEntry
import app.many.economytycoon.watch.RuleBook
import app.many.economytycoon.watch.Severity
import app.many.economytycoon.watch.WatchHit
import app.many.economytycoon.watch.WatchRule
import app.many.economytycoon.watch.plainMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchPresenterTest {
    @Test fun severityTonesDiffer() {
        assertEquals(setOf(Tone.INFO, Tone.WARN, Tone.BAD), Severity.values().map { WatchPresenter.severityTone(it) }.toSet())
    }

    @Test fun ruleRowDescribesStatus() {
        val e = RuleEntry(1, WatchRule.Cash(Money.units(100)))
        assertEquals("Has not fired", WatchPresenter.rule(e, null, FakeNames, plainMoney).status)
        assertEquals("Last fired on day 40", WatchPresenter.rule(e, 40, FakeNames, plainMoney).status)
        assertEquals("Switched off", WatchPresenter.rule(e.copy(enabled = false), 40, FakeNames, plainMoney).status)
        assertEquals("Cash below 100", WatchPresenter.rule(e, null, FakeNames, plainMoney).text)
    }

    @Test fun presetsMakeRules() {
        assertEquals(WatchRule.Cash(Money.units(1_000)), WatchPresenter.fromPreset("cash", Money.units(10_000)))
        assertNull(WatchPresenter.fromPreset("cash", Money.ZERO))
        assertTrue(WatchPresenter.fromPreset("loss", Money.ZERO) is WatchRule.Profit)
        assertTrue(WatchPresenter.fromPreset("loan", Money.ZERO) is WatchRule.Loan)
        assertTrue(WatchPresenter.fromPreset("delivery", Money.ZERO) is WatchRule.Delivery)
        assertTrue(WatchPresenter.fromPreset("idle", Money.ZERO) is WatchRule.Idle)
        assertNull(WatchPresenter.fromPreset("nonsense", Money.units(5)))
    }

    @Test fun everyListedPresetMakesARule() {
        for (p in WatchPresenter.presets) assertTrue(p.id, WatchPresenter.fromPreset(p.id, Money.units(10_000)) != null)
    }

    @Test fun modelCountsAndLimitsHits() {
        val hits = (1L..30L).map { WatchHit(1, it, "x", Severity.INFO) }
        val m = WatchPresenter.model(listOf(RuleEntry(1, WatchRule.Idle(1))), { null }, hits, FakeNames, plainMoney, false)
        assertEquals("1", m.tiles[0].value)
        assertEquals(20, m.hits.size)
        assertEquals("30", m.tiles[1].value)
    }

    @Test fun criticalHitsColourTheTile() {
        val m = WatchPresenter.model(emptyList(), { null }, listOf(WatchHit(1, 1, "x", Severity.CRITICAL)), FakeNames, plainMoney, false)
        assertEquals(Tone.BAD, m.tiles[1].tone)
    }

    @Test fun liveSessionAddsTogglesAndRemovesRules() {
        val s = PresentSupport.session("Watchers", 15L).also { x -> repeat(5) { x.step() } }
        assertNull(WatchPresenter.addPreset(s, "idle"))
        assertEquals(1, s.watch.size)
        val id = s.watch.all().single().id
        WatchPresenter.toggle(s, id)
        assertEquals(false, s.watch.get(id)!!.enabled)
        WatchPresenter.remove(s, id)
        assertEquals(0, s.watch.size)
    }

    @Test fun priceRulesNeedAPositiveLevel() {
        val s = PresentSupport.session("Pricey", 15L)
        val key = s.simulation.economy.markets.states().firstOrNull()?.key
        val region = key?.region ?: RegionId(1)
        val good = key?.good ?: GoodId("wheat")
        assertEquals("Enter a price above zero.", WatchPresenter.addPrice(s, region, good, true, Money.ZERO))
        assertNull(WatchPresenter.addPrice(s, region, good, true, Money.units(5)))
    }

    @Test fun theBookHasALimitThatIsReported() {
        val s = PresentSupport.session("Limits", 15L)
        repeat(RuleBook.MAX_RULES) { s.watch.add(WatchRule.Idle(1)) }
        assertTrue(WatchPresenter.addPreset(s, "idle")!!.contains("Remove a rule"))
        assertTrue(WatchPresenter.build(s).full)
    }

    @Test fun sinkIsCalledWhenRulesChange() {
        val s = PresentSupport.session("Saves", 15L)
        var saved = 0
        s.watchSink = { saved++ }
        WatchPresenter.addPreset(s, "idle")
        WatchPresenter.remove(s, s.watch.all().single().id)
        assertEquals(2, saved)
    }

    @Test fun firingRulesPostNewsAndLog() {
        val s = PresentSupport.session("Loud", 15L)
        s.watch.add(WatchRule.Cash(Money.units(1_000_000_000)), cooldownDays = 0)
        s.step()
        assertEquals(1, s.watchHits().size)
        assertTrue(s.news.items().any { it.headline.startsWith("Watch: ") })
    }

    @Test fun criticalHitsPauseTheGame() {
        val s = PresentSupport.session("Critical", 15L)
        s.watch.add(WatchRule.Cash(Money.units(1_000_000_000), Severity.CRITICAL))
        s.applySettings(app.many.economytycoon.session.GameSettings(autosave = false, pauseOnCritical = true))
        s.setSpeed(app.many.economytycoon.session.GameSpeed.NORMAL)
        s.step()
        assertEquals(app.many.economytycoon.session.GameSpeed.PAUSED, s.speed)
    }

    @Test fun priceNoteShowsTheCurrentPrice() {
        val s = PresentSupport.session("Notes", 15L).also { x -> repeat(3) { x.step() } }
        val key = s.simulation.economy.markets.states().first().key
        assertTrue(WatchPresenter.priceNote(s, key.region, key.good).startsWith("Now "))
    }
}
