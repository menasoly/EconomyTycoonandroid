package app.many.economytycoon.ui.present

import app.many.economytycoon.banking.BankAction
import app.many.economytycoon.banking.BankEvent
import app.many.economytycoon.banking.BankId
import app.many.economytycoon.banking.BankInsight
import app.many.economytycoon.banking.BankTopic
import app.many.economytycoon.banking.CapitalInstrument
import app.many.economytycoon.banking.ResolutionOutcome
import app.many.economytycoon.banking.ResolutionTool
import app.many.economytycoon.banking.ResolutionTrigger
import app.many.economytycoon.banking.RunStage
import app.many.economytycoon.banking.SupervisoryRung
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.finance.CreditRating
import app.many.economytycoon.session.Formatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankAdviceAndTextTest {
    private val money: (Money) -> String = { Formatting.compactMoney(it) }
    private val name: (BankId) -> String = { "Bank ${it.value}" }
    private val b = BankId(1)

    private fun insight(code: String, priority: Int, action: BankAction? = null) =
        BankInsight(code, BankTopic.CAPITAL, priority, "Title $code", "Detail $code", action)

    @Test fun adviceIsOrderedByPriorityThenCode() {
        val cards = BankAdvicePresenter.cards(listOf(insight("b", 40), insight("a", 40), insight("c", 90)))
        assertEquals(listOf("c", "a", "b"), cards.map { it.code })
    }

    @Test fun adviceHonoursTheLimit() {
        val cards = BankAdvicePresenter.cards((1..20).map { insight("i$it", it) }, limit = 5)
        assertEquals(5, cards.size)
        assertEquals("i20", cards.first().code)
    }

    @Test fun adviceTonesByPriority() {
        assertEquals(Tone.BAD, BankAdvicePresenter.tone(85))
        assertEquals(Tone.WARN, BankAdvicePresenter.tone(60))
        assertEquals(Tone.INFO, BankAdvicePresenter.tone(30))
        assertEquals(Tone.MUTED, BankAdvicePresenter.tone(5))
    }

    @Test fun cardsCarryActionLabelOnlyWithASuggestion() {
        val with = BankAdvicePresenter.cards(listOf(insight("x", 50, BankAction.SetLoanMargin(b, 25)))).single()
        assertEquals("Set loan margin to +25 bp", with.actionLabel)
        assertNull(BankAdvicePresenter.cards(listOf(insight("y", 50))).single().actionLabel)
    }

    @Test fun everyActionHasText() {
        val all = listOf(
            BankAction.SetLoanMargin(b, -10),
            BankAction.SetGrowthAppetite(b, app.many.economytycoon.core.Rate.percent(50)),
            BankAction.RaiseCapital(b, CapitalInstrument.TIER2, Money.units(1_000_000)),
            BankAction.BorrowWholesale(b, Money.units(5)),
            BankAction.RepayWholesale(b, Money.units(5)),
            BankAction.RequestEmergencyLiquidity(b, Money.units(5)),
            BankAction.ReassureDepositors(b, Money.units(5)),
            BankAction.RunStressTest(b, "severe"),
            BankAction.PayDividend(b, Money.units(5)),
            BankAction.FoundBank("New", CountryId(1), null, Money.units(5), false),
        )
        for (a in all) assertTrue(BankActionText.short(a).isNotBlank())
        assertEquals("Set loan margin to -10 bp", BankActionText.short(all[0]))
    }

    private fun text(e: BankEvent) = BankEventText.headline(e, name, money)

    @Test fun rungWorseningIsImportantOnlyWhenBreached() {
        val worse = text(BankEvent.RungChanged(b, SupervisoryRung.NORMAL, SupervisoryRung.BUFFER, 5))
        assertFalse(worse.important)
        assertEquals(Tone.WARN, worse.tone)
        val breach = text(BankEvent.RungChanged(b, SupervisoryRung.BUFFER, SupervisoryRung.SEVERE, 5))
        assertTrue(breach.important)
        val better = text(BankEvent.RungChanged(b, SupervisoryRung.SEVERE, SupervisoryRung.NORMAL, 5))
        assertEquals(Tone.GOOD, better.tone)
    }

    @Test fun runStagesWorsenAndRecover() {
        assertTrue(text(BankEvent.RunStageChanged(b, RunStage.CALM, RunStage.RUN, 1)).important)
        assertFalse(text(BankEvent.RunStageChanged(b, RunStage.CALM, RunStage.WATCHFUL, 1)).important)
        assertEquals(Tone.GOOD, text(BankEvent.RunStageChanged(b, RunStage.RUN, RunStage.CALM, 1)).tone)
    }

    @Test fun ratingMovesReadCorrectly() {
        assertTrue(text(BankEvent.RatingChanged(b, CreditRating.BBB, CreditRating.A, 1)).text.contains("upgraded"))
        assertTrue(text(BankEvent.RatingChanged(b, CreditRating.A, CreditRating.BBB, 1)).text.contains("downgraded"))
    }

    @Test fun resolutionTextNamesTheToolOutcome() {
        fun outcome(tool: ResolutionTool, acq: BankId? = null) =
            ResolutionOutcome(b, tool, ResolutionTrigger.INSOLVENT, 9, acq, Money.ZERO, Money.units(10), Money.ZERO, Money.units(3), Money.units(4), Money.ZERO)
        assertTrue(text(BankEvent.Resolved(outcome(ResolutionTool.RECAPITALISE), 9)).text.contains("rescued"))
        assertTrue(text(BankEvent.Resolved(outcome(ResolutionTool.MERGER, BankId(2)), 9)).text.contains("Bank 2"))
        assertTrue(text(BankEvent.Resolved(outcome(ResolutionTool.LIQUIDATION), 9)).text.contains("wound up"))
        assertTrue(text(BankEvent.Resolved(outcome(ResolutionTool.LIQUIDATION), 9)).important)
    }

    @Test fun stressTestHeadlineReflectsFailures() {
        assertEquals(Tone.GOOD, text(BankEvent.StressTestPublished(CountryId(1), "Severe", 0, 5, 1)).tone)
        val bad = text(BankEvent.StressTestPublished(CountryId(1), "Severe", 2, 5, 1))
        assertTrue(bad.text.contains("2 of 5"))
        assertTrue(bad.important)
    }

    @Test fun importantFilterKeepsOnlyImportant() {
        val events = listOf(
            BankEvent.Founded(b, CountryId(1), 1),
            BankEvent.LiquidityFailure(b, Money.units(5), 2),
            BankEvent.InterbankFrozen(CountryId(1), 3),
        )
        val all = BankEventText.headlines(events, name, money)
        val imp = BankEventText.headlines(events, name, money, onlyImportant = true)
        assertEquals(3, all.size)
        assertEquals(2, imp.size)
        assertNull(all.last().bank)
        assertEquals(b, all.first().bank)
    }

    @Test fun everyEventTypeProducesText() {
        val events = listOf(
            BankEvent.Founded(b, CountryId(1), 1), BankEvent.DividendPaid(b, Money.units(1), 1), BankEvent.Rumour(b, 100_000, 1),
            BankEvent.FacilityUsed(b, Money.units(1), Money.ZERO, 1), BankEvent.FireSale(b, Money.units(1), 1),
            BankEvent.Supervised(b, "capital", 1), BankEvent.Released(b, 1), BankEvent.CapitalRaised(b, CapitalInstrument.COMMON_EQUITY, Money.units(1), 1),
            BankEvent.LevyCollected(CountryId(1), Money.units(1), 1), BankEvent.Strategy(b, "grow", 1),
        )
        for (e in events) assertTrue(text(e).text.isNotBlank())
    }
}
