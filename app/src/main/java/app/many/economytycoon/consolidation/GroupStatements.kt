package app.many.economytycoon.consolidation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.finance.BalanceSheet
import app.many.economytycoon.finance.IncomeStatement

/** One company's own statements and how much of it the group owns. */
class MemberStatements(
    val company: CompanyId,
    val name: String,
    val interestPpm: Long,
    val income: IncomeStatement,
    val balance: BalanceSheet,
) {
    val shareOfRevenue: Money get() = income.revenue.share(interestPpm)
    val shareOfNetIncome: Money get() = income.netIncome.share(interestPpm)
    val shareOfAssets: Money get() = balance.totalAssets.share(interestPpm)
    val shareOfEquity: Money get() = balance.totalEquity.share(interestPpm)
}

/** One company's contribution to a line of the group statements. */
class Contribution(val company: CompanyId, val name: String, val amount: Money)

/**
 * A group seen as one business: each member's statements weighted by the share the root owns of it, and added up.
 * Trade between members is not netted out, so revenue and costs of intragroup sales are counted on both sides.
 */
class GroupStatements private constructor(
    val root: CompanyId,
    val members: List<MemberStatements>,
    val income: IncomeStatement,
    val balance: BalanceSheet,
) {
    val size: Int get() = members.size
    val subsidiaries: List<MemberStatements> get() = members.filter { it.company != root }

    /** Net income of the root on its own, for comparing with the group figure. */
    val rootAlone: MemberStatements? get() = members.firstOrNull { it.company == root }

    /** Revenue and profit that come from subsidiaries rather than the root's own operations. */
    val subsidiaryRevenue: Money get() = subsidiaries.fold(Money.ZERO) { a, m -> a + m.shareOfRevenue }
    val subsidiaryNetIncome: Money get() = subsidiaries.fold(Money.ZERO) { a, m -> a + m.shareOfNetIncome }

    fun revenueBy(): List<Contribution> = members.map { Contribution(it.company, it.name, it.shareOfRevenue) }.sortedByDescending { it.amount.raw }
    fun netIncomeBy(): List<Contribution> = members.map { Contribution(it.company, it.name, it.shareOfNetIncome) }.sortedByDescending { it.amount.raw }
    fun assetsBy(): List<Contribution> = members.map { Contribution(it.company, it.name, it.shareOfAssets) }.sortedByDescending { it.amount.raw }

    /** Share of the group's revenue a member brings, in parts per million. Zero when the group has no revenue. */
    fun revenueShare(company: CompanyId): Long {
        val total = income.revenue.raw
        if (total <= 0L) return 0L
        val mine = members.firstOrNull { it.company == company }?.shareOfRevenue?.raw ?: return 0L
        return app.many.economytycoon.core.mulDiv(mine, 1_000_000L, total)
    }

    /** The group leans on one subsidiary when it brings more than this share of revenue. */
    fun dominantMember(thresholdPpm: Long = 600_000L): MemberStatements? =
        members.firstOrNull { it.company != root && revenueShare(it.company) >= thresholdPpm }

    companion object {
        /**
         * Builds the group figures. [members] must start with the root, held at its full interest; the root's statement
         * supplies the owner and period of the result.
         */
        fun of(root: CompanyId, members: List<MemberStatements>): GroupStatements? {
            val first = members.firstOrNull() ?: return null
            if (first.company != root) return null
            var income = first.income.scaled(first.interestPpm)
            var balance = first.balance.scaled(first.interestPpm)
            for (m in members.drop(1)) {
                income += m.income.scaled(m.interestPpm)
                balance += m.balance.scaled(m.interestPpm)
            }
            return GroupStatements(root, members, income, balance)
        }
    }
}
