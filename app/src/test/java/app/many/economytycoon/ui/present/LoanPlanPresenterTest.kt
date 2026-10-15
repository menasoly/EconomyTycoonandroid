package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanPlanPresenterTest {
    private fun session(seed: Long) = PresentSupport.session("Plan Works", seed).also {
        it.economy.finance.mint(it.economy.tick, it.economy.finance.bank, Money.units(5_000_000), "test:lender")
    }

    @Test fun noLoansMeansNoPlans() {
        assertEquals(emptyList<LoanPlanCard>(), LoanPlanPresenter.build(session(21L)))
    }

    @Test fun aLoanGetsACardWithItsNextPayments() {
        val s = session(22L)
        assertTrue(s.issueNow(Command.Borrow(s.selectedCompany!!, Money.units(60_000), 360)) is Outcome.Ok)
        val card = LoanPlanPresenter.build(s).single()
        assertEquals(LoanPlanPresenter.SHOWN, card.lines.size)
        assertTrue(card.headline.contains("payments left"))
        assertEquals(card.lines.size + card.moreCount, Regex("""\d+""").find(card.headline)!!.value.toInt())
        assertTrue(card.facts.map { it.label }.containsAll(listOf("Still to pay", "Of which interest", "Largest payment", "Last payment")))
    }

    @Test fun interestIsPartOfWhatIsStillToPay() {
        val s = session(23L)
        s.issueNow(Command.Borrow(s.selectedCompany!!, Money.units(60_000), 360))
        val card = LoanPlanPresenter.build(s).single()
        assertEquals(Tone.WARN, card.facts.first { it.label == "Of which interest" }.tone)
    }

    @Test fun lineTextNamesTheSplit() {
        val s = session(24L)
        s.issueNow(Command.Borrow(s.selectedCompany!!, Money.units(60_000), 360))
        val line = LoanPlanPresenter.build(s).single().lines.first()
        assertTrue(line.split.contains("off the debt") && line.split.contains("interest"))
    }

    @Test fun twoLoansGiveTwoCardsInOrder() {
        val s = session(25L)
        val me = s.selectedCompany!!
        s.issueNow(Command.Borrow(me, Money.units(30_000), 180))
        s.issueNow(Command.Borrow(me, Money.units(30_000), 360))
        val cards = LoanPlanPresenter.build(s)
        assertEquals(2, cards.size)
        assertEquals(cards.map { it.id.value }.sorted(), cards.map { it.id.value })
    }
}
