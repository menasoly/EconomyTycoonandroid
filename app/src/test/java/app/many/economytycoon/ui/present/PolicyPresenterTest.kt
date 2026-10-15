package app.many.economytycoon.ui.present

import app.many.economytycoon.companies.Department
import app.many.economytycoon.core.Money
import app.many.economytycoon.ui.model.BudgetForm
import app.many.economytycoon.ui.model.DividendForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyPresenterTest {
    @Test fun everyDepartmentStartsAtZero() {
        val m = PolicyPresenter.build(PresentSupport.session())!!
        assertEquals(Department.values().size, m.departments.size)
        assertTrue(m.departments.all { it.budgetEditText == "0" && it.blurb.isNotBlank() })
        assertEquals("No dividends are paid.", m.dividend.summary)
    }

    @Test fun budgetsAndDividendsFeedBack() {
        val s = PresentSupport.session("Policy Works", 4L)
        val c = s.selectedCompany!!
        val budget = BudgetForm(c, Department.MARKETING, "125.5").validate().commandOrNull!!
        assertTrue(s.issueNow(budget).isOk)
        val dividend = DividendForm(c, "40", 20, 60).validate().commandOrNull!!
        assertTrue(s.issueNow(dividend).isOk)
        val m = PolicyPresenter.build(s)!!
        assertEquals("125.5", m.departments.first { it.department == Department.MARKETING }.budgetEditText)
        assertEquals("40.0", m.dividend.payoutText)
        assertTrue(m.dividend.summary.contains("every 60 days") && m.dividend.summary.contains("20 days"))
        assertTrue(m.totalBudget.contains("125"))
    }

    @Test fun editTextTrimsOnlyWholeAmounts() {
        assertEquals("1500", PolicyPresenter.editText(Money.units(1_500)))
        assertEquals("0.25", PolicyPresenter.editText(Money(2_500)))
    }
}
