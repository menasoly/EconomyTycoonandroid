package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.Ratios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancePresenterTest {
    private val session by lazy { PresentSupport.session("Ledger Works").also { s -> repeat(45) { s.step() } } }

    @Test fun theFinanceModelCarriesTilesAndSeries() {
        val m = FinancePresenter.build(session)!!
        assertEquals(4, m.tiles.size)
        assertEquals(8, m.ratios.size)
        assertTrue(m.netWorthSeries.points.size >= 2)
        assertNotNull(m.standing)
    }

    @Test fun statementSectionsAreComplete() {
        val s = session
        val pack = s.reports.company.finance(s.selectedCompany!!)!!
        val income = StatementsPresenter.income(pack.month, s.settings)
        assertEquals(listOf("Revenue", "Costs", "Profit"), income.map { it.title })
        val net = income.last().lines.last()
        assertEquals("Net income", net.label)
        assertEquals(pack.month.netIncome.raw, net.raw)
        val balance = StatementsPresenter.balance(pack.balance, s.settings)
        assertEquals(pack.balance.totalAssets.raw, balance.first().lines.last().raw)
        assertEquals(pack.balance.totalEquity.raw, balance.last().lines.last().raw)
        val cash = StatementsPresenter.cashFlow(pack.cashFlow, s.settings)
        assertEquals(pack.cashFlow.closingCash.raw, cash.last().lines.last().raw)
    }

    @Test fun costBreakdownDropsEmptyCosts() {
        val s = session
        val pack = s.reports.company.finance(s.selectedCompany!!)!!
        val parts = FinancePresenter.costBreakdown(pack.month)
        assertTrue(parts.all { it.second > 0.0 })
        assertTrue(parts.size <= 12)
    }

    @Test fun unboundedRatiosShowAnInfinitySign() {
        val r = Ratios(Ratios.UNBOUNDED, Rate.percent(50), Rate.percent(200), Ratios.UNBOUNDED, Rate.percent(40), Rate.percent(-5), Rate.percent(10), Rate.percent(3), Rate.ZERO, Rate.percent(40))
        val facts = FinancePresenter.ratios(r)
        assertEquals("∞", facts.first { it.label == "Current ratio" }.value)
        assertEquals("∞", facts.first { it.label == "Interest cover" }.value)
        assertEquals(Tone.BAD, facts.first { it.label == "Net margin" }.tone)
        assertEquals("2.00x", facts.first { it.label == "Debt to equity" }.value)
    }

    @Test fun loanRowsShowProgressAndStatus() {
        val s = session
        val c = s.selectedCompany!!
        val limit = s.reports.company.finance(c)!!.creditLimit!!
        assertTrue(limit.raw > 0)
        val r = s.issueNow(app.many.economytycoon.simulation.Command.Borrow(c, Money(limit.raw / 2), 180)); assertTrue(r.toString(), r.isOk)
        val items = FinancePresenter.build(s)!!.loans
        assertEquals(1, items.size)
        assertEquals("Current", items.single().status)
        assertTrue(items.single().repaid in 0.0..1.0)
        assertTrue(items.single().active)
    }
}
