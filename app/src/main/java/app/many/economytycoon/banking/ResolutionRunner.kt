package app.many.economytycoon.banking

import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick

/**
 * Carries out a resolution decision: asks the planner for the cheapest tool, runs it, and turns the result into events,
 * ledger intents and a rise in systemic stress.
 */
internal class ResolutionRunner(
    private val book: BankingBook,
    private val params: BankingParams,
    private val planner: ResolutionPlanner,
    private val engine: ResolutionEngine,
) {
    /** Failures frighten the whole country; this much systemic stress is added per share of deposits that failed. */
    private val failureShock = 600_000L

    fun execute(target: Bank, trigger: ResolutionTrigger, tick: Tick, collateral: Long, out: Outputs): ResolutionOutcome? {
        if (!target.status.isOperating) return null
        val country = target.country
        val banks = book.banks.values.filter { it.country == country }
        val live = banks.filter { it.status.isOperating && it.id != target.id }
        val snapshots = live.map { it.snapshot() }
        val before = target.snapshot()
        val plan = planner.plan(before, trigger, snapshots, collateral)
        val fund = book.fundOf(country)
        val outcome = when (plan.tool) {
            ResolutionTool.RECAPITALISE -> engine.recapitalise(target, plan, collateral, tick)
            ResolutionTool.MERGER -> {
                val acquirer = plan.acquirer?.let { id -> live.firstOrNull { it.id == id } }
                if (acquirer == null) engine.liquidate(target, fund, banks, liquidationOf(plan), collateral, tick)
                else engine.merge(target, acquirer, fund, banks, plan, collateral, tick)
            }
            ResolutionTool.LIQUIDATION -> engine.liquidate(target, fund, banks, plan, collateral, tick)
        }
        record(target, outcome, tick, out)
        raiseSystemic(country, before, outcome)
        return outcome
    }

    private fun liquidationOf(plan: ResolutionPlan) = plan.copy(tool = ResolutionTool.LIQUIDATION, acquirer = null)

    private fun record(target: Bank, outcome: ResolutionOutcome, tick: Tick, out: Outputs) {
        val t = tick.value
        out.add(BankEvent.Resolved(outcome, t))
        val country = target.country
        when (outcome.tool) {
            ResolutionTool.RECAPITALISE -> {
                if (outcome.injection.isPositive) {
                    out.add(
                        if (target.owner != null) BankingIntents.capitalInjection(target.id, target.owner, outcome.injection, t)
                        else BankingIntents.stateRecapitalisation(target.id, country, outcome.injection, t),
                    )
                }
            }
            ResolutionTool.MERGER -> {
                val acquirer = outcome.acquirer
                if (acquirer != null && outcome.price.isPositive) out.add(BankingIntents.acquisitionPrice(acquirer, country, target.owner, outcome.price, t))
            }
            ResolutionTool.LIQUIDATION -> out.add(BankingIntents.insuredPayout(target.id, country, outcome.insuredPaid, t))
        }
        book.stress.remove(target.id)
    }

    private fun raiseSystemic(country: CountryId, before: BankSnapshot, outcome: ResolutionOutcome) {
        if (outcome.tool == ResolutionTool.RECAPITALISE) return
        val others = book.banks.values.filter { it.country == country && it.status.isOperating }
        val total = Money.sum(others.map { it.snapshot().depositsTotal }) + before.depositsTotal
        val share = BankMath.ratio(before.depositsTotal, total).ppm
        val shock = share * failureShock / app.many.economytycoon.core.Rate.ONE_PPM + 50_000L
        book.systemic[country] = ((book.systemic[country] ?: 0L) + shock).coerceAtMost(app.many.economytycoon.core.Rate.ONE_PPM)
    }
}
