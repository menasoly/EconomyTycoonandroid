package app.many.economytycoon.banking

import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/**
 * The daily round of the banks in one country: confidence, withdrawals, the overnight market, the central bank's
 * facilities, and the supervisor's decision to step in. It runs after the day's ordinary activity and never creates
 * or destroys money: deposits that leave a bank in trouble arrive at the other banks of the country.
 */
internal class DailyRound(
    private val book: BankingBook,
    private val params: BankingParams,
    private val interbank: InterbankMarket,
    private val liquidity: LiquidityManager,
    private val planner: ResolutionPlanner,
    private val runner: ResolutionRunner,
) {
    private val runs get() = params.runs

    fun run(country: CountryId, inputs: BankingInputs, tick: Tick, out: Outputs) {
        val live = book.operating(country).sortedBy { it.id }
        if (live.isEmpty()) { book.systemic.remove(country); return }
        val collateral = inputs.collateralIndex(country)
        val fund = book.fundOf(country)

        updateConfidence(live, fund, tick, out)
        val flows = payWithdrawals(live)
        redistribute(live, flows)

        val market = interbank.clear(live, inputs.policyRate(country))
        trackMarket(country, market, tick, out)

        val failed = ArrayList<Pair<Bank, ResolutionTrigger>>()
        for (b in live) {
            val withdrawn = b.lastOutflow
            val day = liquidity.cover(b, withdrawn, allowFireSale = b.runStage >= RunStage.RUN || withdrawn.isPositive)
            if (day.marginal.isPositive || day.emergency.isPositive) out.add(BankEvent.FacilityUsed(b.id, day.marginal, day.emergency, tick.value))
            if (day.fireSold.isPositive) out.add(BankEvent.FireSale(b.id, day.fireSold, tick.value))
            if (day.failed) {
                out.add(BankEvent.LiquidityFailure(b.id, day.unmet, tick.value))
                failed.add(b to ResolutionTrigger.LIQUIDITY_FAILURE)
            } else if (!day.withdrawn.isPositive) {
                liquidity.sweep(b)
            }
            if (b.runStage == RunStage.PANIC) b.panicDays += 1 else b.panicDays = 0
        }

        updateSystemic(country, live)
        val pending = failed.map { it.first.id }.toSet()
        for (b in live) {
            if (b.id in pending) continue
            if (b.panicDays >= runs.panicDaysToIntervene && b.supportPpm < runs.rumourDecay.ppm) failed.add(b to ResolutionTrigger.PANIC_UNRESOLVED)
            else {
                val snap = b.snapshot()
                val trigger = planner.trigger(snap, CapitalEngine.position(snap, params.capital), false, 0)
                if (trigger == ResolutionTrigger.INSOLVENT || trigger == ResolutionTrigger.CAPITAL_COLLAPSE) failed.add(b to trigger)
            }
        }
        for ((b, trigger) in failed.sortedBy { it.first.id }) runner.execute(b, trigger, tick, collateral, out)
    }

    private fun updateConfidence(live: List<Bank>, fund: GuaranteeFund, tick: Tick, out: Outputs) {
        val systemic = book.systemicStress(live.first().country)
        for (b in live) {
            val snap = b.snapshot()
            val capital = CapitalEngine.position(snap, params.capital)
            val position = LiquidityEngine.position(snap, params.liquidity, params.deposits::insuredShare)
            val coverage = GuaranteeScheme.coverage(fund, GuaranteeScheme.coveredDeposits(snap, params.deposits))
            val drivers = BankRuns.drivers(snap, capital, position, b.rumourPpm, b.supportPpm, systemic, coverage, runs)
            val next = BankRuns.nextConfidence(b.confidencePpm, drivers.target, runs)
            val stage = BankRuns.stageFor(next, b.runStage, runs)
            if (stage != b.runStage) out.add(BankEvent.RunStageChanged(b.id, b.runStage, stage, tick.value))
            b.applyConfidence(next, stage)
            b.setRumour(BankRuns.decay(b.rumourPpm, runs.rumourDecay))
            b.setSupport(BankRuns.decay(b.supportPpm, runs.supportDecay))
        }
    }

    /** Pays out the day's withdrawals and returns what left each bank, by pool. */
    private fun payWithdrawals(live: List<Bank>): Map<BankId, Map<DepositKey, Money>> {
        val taken = java.util.TreeMap<BankId, Map<DepositKey, Money>>()
        for (b in live) {
            val plan = BankRuns.withdrawals(b.snapshot(), b.confidencePpm, runs, params.deposits)
            if (plan.isEmpty()) { b.lastOutflow = Money.ZERO; continue }
            val actual = java.util.TreeMap<DepositKey, Money>()
            for ((key, amount) in plan) {
                val got = Money.min(amount, b.depositPool(key).balance)
                if (got.isPositive) actual[key] = got
            }
            liquidity.payWithdrawals(b, actual)
            taken[b.id] = actual
        }
        return taken
    }

    /** Customers who leave a troubled bank open accounts elsewhere: the cash and the deposits arrive at the others. */
    private fun redistribute(live: List<Bank>, flows: Map<BankId, Map<DepositKey, Money>>) {
        for ((id, byKey) in flows) {
            val receivers = live.filter { it.id != id && it.runStage < RunStage.RUN }.ifEmpty { live.filter { it.id != id } }
            if (receivers.isEmpty()) {
                // A one-bank country: the money returns to the same bank rather than disappearing.
                val only = live.firstOrNull { it.id == id } ?: continue
                for ((key, amount) in byKey) { only.addDeposits(key, amount); only.reserves += amount }
                continue
            }
            val weights = LongArray(receivers.size) { receivers[it].snapshot().depositsTotal.raw.coerceAtLeast(1L) }
            for ((key, amount) in byKey) {
                val parts = BankMath.split(amount, weights)
                for (i in receivers.indices) {
                    receivers[i].addDeposits(key, parts[i])
                    receivers[i].reserves += parts[i]
                }
            }
        }
    }

    private fun trackMarket(country: CountryId, result: InterbankResult, tick: Tick, out: Outputs) {
        val previous = book.markets[country]
        book.markets[country] = MarketStatus(result.rate, result.volume, result.scarcityPpm, result.frozen)
        if (result.frozen && previous?.frozen != true) out.add(BankEvent.InterbankFrozen(country, tick.value))
    }

    /** Systemic stress follows the share of deposits sitting in banks that are being run, and otherwise fades. */
    private fun updateSystemic(country: CountryId, live: List<Bank>) {
        val total = Money.sum(live.map { it.snapshot().depositsTotal })
        val troubled = Money.sum(live.filter { it.runStage >= RunStage.RUN }.map { it.snapshot().depositsTotal })
        val target = mulDiv(BankMath.ratio(troubled, total).ppm, 500_000L, Rate.ONE_PPM)
        val faded = BankRuns.decay(book.systemicStress(country), Rate.percent(3))
        val next = maxOf(faded, target)
        if (next == 0L) book.systemic.remove(country) else book.systemic[country] = next
    }
}
