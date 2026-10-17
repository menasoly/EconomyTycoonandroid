package app.many.economytycoon.ui.present

import app.many.economytycoon.companies.Department
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

data class DepartmentItem(val department: Department, val label: String, val budget: String, val budgetEditText: String, val blurb: String)

data class DividendItem(val payoutText: String, val reserveDays: Int, val intervalDays: Int, val summary: String)

data class PolicyModel(val departments: List<DepartmentItem>, val dividend: DividendItem, val totalBudget: String)

object PolicyPresenter {
    fun blurb(d: Department): String = when (d) {
        Department.RESEARCH -> "Turns money into research points."
        Department.MARKETING -> "Pays for your advertising campaigns first; what is left is general marketing. Book campaigns on the Marketing page."
        Department.HR -> "Raises morale and makes hiring easier."
        Department.LEGAL -> "Lowers the cost of fines and disputes."
        Department.OPERATIONS -> "Keeps equipment running and cuts waste."
    }

    /** Plain decimal text for an edit box: no thousands separators, trailing zeros trimmed. */
    fun editText(m: app.many.economytycoon.core.Money): String {
        val s = Formatting.money(m).replace(",", "")
        return when {
            s.endsWith(".00") -> s.dropLast(3)
            s.endsWith("0") -> s.dropLast(1)
            else -> s
        }
    }

    fun build(session: GameSession): PolicyModel? {
        val company = session.selectedCompany ?: return null
        val money = session.settings::money
        val budgets = session.economy.companies.budgets(company)
        val items = Department.values().map { d ->
            val b = budgets.budget(d)
            DepartmentItem(d, d.label, money(b) + " a day", editText(b), blurb(d))
        }
        val policy = session.economy.companies.policies.dividend(company)
        val payout = Formatting.percentPpm(policy.payoutPpm).removeSuffix("%")
        val summary = if (policy.payoutPpm == 0L) "No dividends are paid." else "Pays out ${Formatting.percentPpm(policy.payoutPpm)} of earnings every ${Formatting.plural(policy.intervalTicks.toLong(), "day")}, keeping ${Formatting.plural(policy.reserveTicks.toLong(), "day")} of costs in cash."
        return PolicyModel(items, DividendItem(payout, policy.reserveTicks, policy.intervalTicks, summary), money(budgets.total()) + " a day")
    }
}
