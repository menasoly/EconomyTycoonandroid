package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.CreditRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankAdvisorTest {
    private val params = BankingFx.params
    private val advisor = BankAdvisor()

    private fun view(b: Bank) = BankView(b.snapshot(), BankingFx.calm, params)
    private fun ctx(tick: Long = 100L, stress: BankStressResult? = null, stressTick: Long? = null, coverage: Rate = Rate.ONE) = AdvisorContext(tick, stress, stressTick, coverage, 360L)
    private fun codes(b: Bank, c: AdvisorContext = ctx()) = advisor.all(view(b), c).map { it.code }

    @Test fun insightsAreSortedByPriorityThenCode() {
        val b = BankingFx.bank()
        val loss = b.snapshot().cet1Capital.times(Rate.percent(60))
        b.reserves -= loss; b.retained -= loss
        val all = advisor.all(view(b), ctx())
        assertTrue(all.zipWithNext().all { (x, y) -> x.priority > y.priority || (x.priority == y.priority && x.code <= y.code) })
    }

    @Test fun everyInsightHasTextAndAValidPriority() {
        val b = BankingFx.bank()
        val loss = b.snapshot().cet1Capital.times(Rate.percent(60))
        b.reserves -= loss; b.retained -= loss
        b.applyConfidence(300_000L, RunStage.RUN)
        for (i in advisor.all(view(b), ctx())) {
            assertTrue(i.title.isNotBlank() && i.detail.isNotBlank())
            assertTrue(i.priority in 0..100)
        }
    }

    @Test fun codesAreUnique() {
        val b = BankingFx.bank()
        val loss = b.snapshot().cet1Capital.times(Rate.percent(60))
        b.reserves -= loss; b.retained -= loss
        val c = codes(b)
        assertEquals(c.size, c.toSet().size)
    }

    @Test fun capitalBreachIsTopPriority() {
        val b = BankingFx.bank()
        val s = b.snapshot()
        val target = CapitalEngine.rwa(s, params.capital).total.times(Rate.percent(3))
        val loss = s.cet1Capital - target
        b.reserves -= loss; b.retained -= loss
        val top = advisor.all(view(b), ctx()).first()
        assertEquals(BankTopic.CAPITAL, top.topic)
    }

    @Test fun supervisionIsFlagged() {
        val b = BankingFx.bank()
        b.changeStatus(BankStatus.SUPERVISED, Tick(1))
        assertTrue(advisor.all(view(b), ctx()).any { it.topic == BankTopic.SUPERVISION })
    }

    @Test fun missingStressTestIsSuggested() {
        val i = advisor.all(view(BankingFx.bank()), ctx()).firstOrNull { it.code == "stress-due" }
        assertNotNull(i)
        assertTrue(i!!.suggestion is BankAction.RunStressTest)
    }

    @Test fun recentPassedStressTestSilencesTheReminder() {
        val b = BankingFx.bank()
        val result = StressTester(params).run(b.snapshot(), StressScenario.BASELINE, Rate.percent(4))
        assertFalse(codes(b, ctx(stress = result, stressTick = 90L)).contains("stress-due"))
        assertTrue(codes(b, ctx(tick = 1_000L, stress = result, stressTick = 90L)).contains("stress-due"))
    }

    @Test fun failedStressTestIsReported() {
        val b = BankingFx.bank(spec = OpeningSpec(Money.units(1_000_000), equityShare = Rate.percent(3)))
        val result = StressTester(params).run(b.snapshot(), StressScenario.SEVERE, Rate.percent(4))
        assertFalse(result.passed)
        assertTrue(codes(b, ctx(stress = result, stressTick = 90L)).contains("stress-fail"))
    }

    @Test fun hiddenInsightsDisappearUntilTheyExpire() {
        val b = BankingFx.bank()
        val code = advisor.all(view(b), ctx()).first().code
        val a = BankAdvisor()
        a.hide(code, 100L, 10L)
        assertTrue(a.isHidden(code, 105L))
        assertFalse(a.advise(view(b), ctx(105L)).any { it.code == code })
        assertTrue(a.advise(view(b), ctx(110L)).any { it.code == code })
        assertEquals(listOf(code), a.hiddenCodes(105L))
        a.unhide(code)
        assertFalse(a.isHidden(code, 105L))
        a.hide(code, 100L)
        a.unhideAll()
        assertTrue(a.hiddenCodes(101L).isEmpty())
    }

    @Test fun limitCutsTheList() {
        val b = BankingFx.bank()
        val loss = b.snapshot().cet1Capital.times(Rate.percent(60))
        b.reserves -= loss; b.retained -= loss
        assertEquals(2, advisor.advise(view(b), ctx(), 2).size)
    }

    @Test fun thinFundIsFlagged() {
        val b = BankingFx.bank()
        assertTrue(codes(b, ctx(coverage = Rate.ZERO)).any { it == "guarantee-thin" })
        assertFalse(codes(b, ctx(coverage = Rate.ONE)).any { it == "guarantee-thin" })
    }

    @Test fun runRiskAppearsWhenConfidenceFalls() {
        val b = BankingFx.bank(); b.applyConfidence(300_000L, RunStage.RUN)
        assertTrue(advisor.all(view(b), ctx()).any { it.topic == BankTopic.LIQUIDITY || it.topic == BankTopic.RISK })
    }

    @Test fun weakRatingIsMentioned() {
        val b = BankingFx.bank(); b.setRating(CreditRating.CCC, 100, 0, 0)
        assertTrue(codes(b).isNotEmpty())
        assertTrue(advisor.all(view(b), ctx()).size >= advisor.all(view(BankingFx.bank(2)), ctx()).size)
    }

    @Test fun suggestionsTargetTheBankBeingAdvised() {
        val b = BankingFx.bank()
        val loss = b.snapshot().cet1Capital.times(Rate.percent(50))
        b.reserves -= loss; b.retained -= loss
        for (i in advisor.all(view(b), ctx())) i.suggestion?.let { assertEquals(b.id, it.bank) }
    }

    @Test fun ruleListHasTwentyDistinctRules() {
        assertEquals(20, BankAdvisorRules.all.size)
        assertEquals(20, BankAdvisorRules.all.toSet().size)
    }
}
