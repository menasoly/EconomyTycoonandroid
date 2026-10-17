package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.CreditRating

/** What is moving depositors' confidence in one bank. Every part is a rate; the sum is what confidence is pulled towards. */
data class ConfidenceDrivers(
    val capitalPenalty: Rate,
    val liquidityPenalty: Rate,
    val lossPenalty: Rate,
    val ratingPenalty: Rate,
    val systemicPenalty: Rate,
    val rumour: Rate,
    val support: Rate,
    val guarantee: Rate,
) {
    val penalties: Long get() = capitalPenalty.ppm + liquidityPenalty.ppm + lossPenalty.ppm + ratingPenalty.ppm + systemicPenalty.ppm + rumour.ppm
    val bonuses: Long get() = support.ppm + guarantee.ppm

    /** The level confidence settles at if nothing changes. */
    val target: Long get() = (Rate.ONE_PPM - penalties + bonuses).coerceIn(0L, Rate.ONE_PPM)

    /** The drivers as labelled figures, largest penalty first, for explaining a run. */
    fun explain(): List<Pair<String, Long>> = listOf(
        "Thin capital" to -capitalPenalty.ppm, "Weak liquidity" to -liquidityPenalty.ppm, "Recent losses" to -lossPenalty.ppm,
        "Credit rating" to -ratingPenalty.ppm, "Trouble elsewhere" to -systemicPenalty.ppm, "Rumours" to -rumour.ppm,
        "Public reassurance" to support.ppm, "Deposit guarantee" to guarantee.ppm,
    ).filter { it.second != 0L }.sortedBy { it.second }
}

/**
 * Bank runs. Depositors' confidence moves towards a target set by the bank's capital, liquidity, losses and rating, by
 * rumours and by trouble in other banks. It falls quickly and recovers slowly. Below the calm level depositors start
 * to leave, uninsured ones far faster than insured ones, and the pace rises with the square of the lost confidence.
 * Everything is deterministic: the same books and the same rumours give the same run.
 */
object BankRuns {
    fun drivers(
        s: BankSnapshot,
        capital: CapitalPosition,
        liquidity: LiquidityPosition,
        rumourPpm: Long,
        supportPpm: Long,
        systemicPpm: Long,
        guaranteeCoverage: Rate,
        p: RunParams,
    ): ConfidenceDrivers {
        val shortfall = (capital.cet1Requirement.ppm - capital.cet1Ratio.ppm).coerceAtLeast(0L)
        val capitalPenalty = mulDiv(shortfall, p.capitalWeight, Rate.ONE_PPM)
        val lcrGap = (Rate.ONE_PPM - liquidity.lcr.ppm).coerceIn(0L, Rate.ONE_PPM)
        val liquidityPenalty = mulDiv(lcrGap, p.lcrWeight, Rate.ONE_PPM)
        val recent = s.history.takeLast(3)
        val result = if (recent.isEmpty()) Money.ZERO else Money.sum(recent.map { it.netIncome })
        val lossPenalty = if (result.raw >= 0L || capital.cet1.raw <= 0L) {
            if (result.raw < 0L) 400_000L else 0L
        } else {
            mulDiv(BankMath.ratio(-result, capital.cet1).ppm, p.lossWeight, Rate.ONE_PPM).coerceAtMost(400_000L)
        }
        val notches = (s.rating.ordinal - CreditRating.A.ordinal).coerceAtLeast(0)
        return ConfidenceDrivers(
            Rate(capitalPenalty.coerceAtMost(Rate.ONE_PPM)), Rate(liquidityPenalty), Rate(lossPenalty),
            Rate(p.ratingPenaltyPerNotch.ppm * notches), Rate(mulDiv(systemicPpm.coerceIn(0L, Rate.ONE_PPM), p.systemicWeight.ppm, Rate.ONE_PPM)),
            Rate(rumourPpm.coerceIn(0L, Rate.ONE_PPM)), Rate(supportPpm.coerceIn(0L, Rate.ONE_PPM)),
            Rate(mulDiv(guaranteeCoverage.ppm.coerceIn(0L, Rate.ONE_PPM), p.guaranteeSupport.ppm, Rate.ONE_PPM)),
        )
    }

    /** Moves confidence towards the target: fast down, slowly up, always at least one unit so it does arrive. */
    fun nextConfidence(current: Long, target: Long, p: RunParams): Long {
        val c = current.coerceIn(0L, Rate.ONE_PPM)
        val t = target.coerceIn(0L, Rate.ONE_PPM)
        if (c == t) return c
        return if (t < c) {
            val step = maxOf(1L, mulDiv(c - t, p.fallSpeed.ppm, Rate.ONE_PPM))
            maxOf(t, c - step)
        } else {
            val step = maxOf(1L, mulDiv(t - c, p.recoverSpeed.ppm, Rate.ONE_PPM))
            minOf(t, c + step)
        }
    }

    private fun rawStage(confidence: Long, p: RunParams): RunStage = when {
        confidence < p.panicThreshold.ppm -> RunStage.PANIC
        confidence < p.runThreshold.ppm -> RunStage.RUN
        confidence < p.calmThreshold.ppm -> RunStage.WATCHFUL
        else -> RunStage.CALM
    }

    private fun threshold(stage: RunStage, p: RunParams): Long = when (stage) {
        RunStage.PANIC -> p.panicThreshold.ppm
        RunStage.RUN -> p.runThreshold.ppm
        RunStage.WATCHFUL -> p.calmThreshold.ppm
        RunStage.CALM -> 0L
    }

    /** The stage for a confidence level. Getting better needs a margin over the threshold, so the stage does not flicker. */
    fun stageFor(confidence: Long, previous: RunStage, p: RunParams): RunStage {
        var stage = rawStage(confidence, p)
        if (stage >= previous) return stage
        // Improving: step down one stage at a time and only while the margin is met.
        var current = previous
        while (current > stage) {
            val better = RunStage.values()[current.ordinal - 1]
            if (confidence >= threshold(current, p) + p.hysteresis.ppm) current = better else break
        }
        stage = current
        return stage
    }

    /** How lost confidence is, squared: 0 when calm, 1,000,000 when confidence is gone. */
    fun intensityPpm(confidence: Long, p: RunParams): Long {
        val calm = p.calmThreshold.ppm
        if (confidence >= calm || calm <= 0L) return 0L
        val x = mulDiv(calm - confidence.coerceAtLeast(0L), Rate.ONE_PPM, calm)
        return mulDiv(x, x, Rate.ONE_PPM)
    }

    /** Share of uninsured and of insured deposits that leave on a day at this confidence. */
    fun dailyRates(confidence: Long, p: RunParams): Pair<Rate, Rate> {
        val i = intensityPpm(confidence, p)
        return Rate(mulDiv(p.maxDailyUninsured.ppm, i, Rate.ONE_PPM)) to Rate(mulDiv(p.maxDailyInsured.ppm, i, Rate.ONE_PPM))
    }

    private fun stickiness(product: DepositProduct): Long = when (product) {
        DepositProduct.DEMAND -> Rate.ONE_PPM
        DepositProduct.SAVINGS -> 700_000L
        DepositProduct.TERM -> 300_000L
    }

    /** The withdrawals a day of this confidence would bring, by deposit book. Empty when confidence is calm. */
    fun withdrawals(s: BankSnapshot, confidence: Long, runs: RunParams, deposits: DepositParams): Map<DepositKey, Money> {
        val (uninsuredRate, insuredRate) = dailyRates(confidence, runs)
        if (uninsuredRate.ppm == 0L && insuredRate.ppm == 0L) return emptyMap()
        val out = java.util.TreeMap<DepositKey, Money>()
        for ((key, pool) in s.deposits) {
            val insuredBalance = pool.balance.times(deposits.insuredShare(key.depositor))
            val uninsuredBalance = pool.balance - insuredBalance
            val gross = insuredBalance.times(insuredRate) + uninsuredBalance.times(uninsuredRate)
            val taken = Money(mulDiv(gross.raw, stickiness(key.product), Rate.ONE_PPM)).coerceAtMost(pool.balance)
            if (taken.isPositive) out[key] = taken
        }
        return out
    }

    fun decay(value: Long, rate: Rate): Long = (value - mulDiv(value, rate.ppm, Rate.ONE_PPM)).let { if (it < 1_000L) 0L else it }
}
