package app.many.economytycoon.ui.present

import app.many.economytycoon.core.LoanId
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.RepaymentSchedule
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

data class PlanLine(val label: String, val payment: String, val split: String)

data class LoanPlanCard(
    val id: LoanId,
    val headline: String,
    val lines: List<PlanLine>,
    val moreCount: Int,
    val facts: List<Fact>,
)

/** What is still to pay on each running loan, from the next payment to the last. */
object LoanPlanPresenter {
    /** How many upcoming payments are spelled out before the rest is summarised. */
    const val SHOWN = 3

    fun build(session: GameSession): List<LoanPlanCard> {
        val company = session.selectedCompany ?: return emptyList()
        val money = session.settings::money
        val owner = LedgerOwner.company(company)
        return session.economy.finance.loans.activeFor(owner).sortedBy { it.id.value }.mapNotNull { loan ->
            val plan = RepaymentSchedule.of(loan)
            val first = plan.first ?: return@mapNotNull null
            val lines = plan.instalments.take(SHOWN).map {
                PlanLine(Formatting.date(app.many.economytycoon.core.Tick(it.day)), money(it.payment), "${money(it.principal)} off the debt, ${money(it.interest)} interest")
            }
            LoanPlanCard(
                id = loan.id,
                headline = "${Formatting.plural(plan.count.toLong(), "payment")} left, the next on ${Formatting.date(app.many.economytycoon.core.Tick(first.day))}",
                lines = lines,
                moreCount = (plan.count - lines.size).coerceAtLeast(0),
                facts = listOf(
                    Fact("Still to pay", money(plan.totalPaid)),
                    Fact("Of which interest", money(plan.totalInterest), if (plan.totalInterest.raw > 0L) Tone.WARN else Tone.NEUTRAL),
                    Fact("Largest payment", money(plan.peak)),
                    Fact("Last payment", Formatting.date(app.many.economytycoon.core.Tick(plan.last!!.day))),
                ),
            )
        }
    }
}
