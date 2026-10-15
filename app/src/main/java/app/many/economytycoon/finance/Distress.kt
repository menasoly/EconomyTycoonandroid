package app.many.economytycoon.finance

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.splitLargestRemainder

enum class DistressState { HEALTHY, WATCH, DISTRESSED, INSOLVENT, LIQUIDATING }

data class DistressThresholds(
    val watchCoverage: Rate = Rate.percent(150),
    val distressedCoverage: Rate = Rate.percent(100),
    val watchCashRatio: Rate = Rate.percent(15),
    val distressedCashRatio: Rate = Rate.percent(5),
    val insolvencyGraceTicks: Int = 60,
    val distressGraceTicks: Int = 30,
    val recoveryTicks: Int = 30,
    val maxArrearsShare: Rate = Rate.percent(25),
)

data class DistressReading(
    val tick: Tick,
    val state: DistressState,
    val reasons: List<String>,
)

/**
 * Ratio-based distress state machine with grace periods: conditions must persist before the state
 * worsens, and must clear for a recovery period before it improves.
 */
class DistressMonitor(private val thresholds: DistressThresholds = DistressThresholds()) {
    var state: DistressState = DistressState.HEALTHY
        private set
    private var badTicks = 0
    private var improveTicks = 0
    private var insolventTicks = 0

    fun evaluate(tick: Tick, ratios: Ratios, balance: BalanceSheet, arrears: Money, missedLoanPayments: Int): DistressReading {
        if (state == DistressState.LIQUIDATING) return DistressReading(tick, state, listOf("liquidating"))
        val reasons = ArrayList<String>()
        val negativeEquity = balance.totalEquity.raw < 0
        val noCash = balance.cash.raw <= 0
        val coverageLow = ratios.interestCoverage < thresholds.distressedCoverage && balance.totalDebt.isPositive
        val coverageWatch = ratios.interestCoverage < thresholds.watchCoverage && balance.totalDebt.isPositive
        val cashLow = ratios.cashRatio < thresholds.distressedCashRatio
        val cashWatch = ratios.cashRatio < thresholds.watchCashRatio
        val arrearsHeavy = balance.currentLiabilities.isPositive && arrears.isPositive &&
            Rate.ratio(arrears.raw, balance.currentLiabilities.raw) > thresholds.maxArrearsShare
        if (negativeEquity) reasons.add("negative equity")
        if (noCash) reasons.add("no cash")
        if (coverageLow) reasons.add("interest coverage below 1x")
        if (cashLow) reasons.add("cash ratio critical")
        if (arrearsHeavy) reasons.add("arrears exceed limit")
        if (missedLoanPayments > 0) reasons.add("missed loan payments")

        val severe = negativeEquity && (noCash || arrearsHeavy || missedLoanPayments > 0)
        val distressed = severe || (coverageLow && cashLow) || arrearsHeavy || missedLoanPayments >= 2
        val watch = distressed || coverageWatch || cashWatch || missedLoanPayments > 0 || negativeEquity

        if (severe) insolventTicks++ else insolventTicks = 0
        if (distressed) badTicks++ else badTicks = 0
        val level = when {
            insolventTicks >= thresholds.insolvencyGraceTicks -> DistressState.INSOLVENT
            distressed && badTicks >= thresholds.distressGraceTicks -> DistressState.DISTRESSED
            distressed || watch -> DistressState.WATCH
            else -> DistressState.HEALTHY
        }
        if (level > state) {
            state = level
            improveTicks = 0
        } else if (level < state) {
            improveTicks++
            if (improveTicks >= thresholds.recoveryTicks) { state = stepDown(state); improveTicks = 0 }
        } else {
            improveTicks = 0
        }
        return DistressReading(tick, state, reasons)
    }

    private fun stepDown(s: DistressState) = when (s) {
        DistressState.INSOLVENT -> DistressState.DISTRESSED
        DistressState.DISTRESSED -> DistressState.WATCH
        DistressState.WATCH -> DistressState.HEALTHY
        else -> s
    }

    fun beginLiquidation() { state = DistressState.LIQUIDATING }

    internal fun restore(state: DistressState, bad: Int, improve: Int, insolvent: Int) {
        this.state = state; badTicks = bad; improveTicks = improve; insolventTicks = insolvent
    }
    internal val badTicksValue get() = badTicks
    internal val improveTicksValue get() = improveTicks
    internal val insolventTicksValue get() = insolventTicks
}

enum class ClaimClass { SECURED, STATUTORY, WAGES, UNSECURED, SHAREHOLDERS }

data class Claim(val claimant: LedgerOwner, val cls: ClaimClass, val amount: Money, val label: String = "")

data class Distribution(val claimant: LedgerOwner, val cls: ClaimClass, val claim: Money, val paid: Money) {
    val recovery: Rate get() = if (claim.isZero) Rate.ZERO else Rate.ratio(paid.raw, claim.raw)
}

/** Pays creditors from liquidation proceeds in strict class order, pro rata inside a class. */
object LiquidationWaterfall {
    fun distribute(proceeds: Money, claims: List<Claim>): List<Distribution> {
        require(proceeds.raw >= 0) { "proceeds cannot be negative" }
        var left = proceeds
        val out = ArrayList<Distribution>()
        for (cls in ClaimClass.values()) {
            val inClass = claims.filter { it.cls == cls && it.amount.isPositive }.sortedWith(compareBy({ it.claimant }, { it.label }))
            if (inClass.isEmpty()) continue
            val totalClaim = Money.sum(inClass.map { it.amount })
            if (left >= totalClaim) {
                for (c in inClass) out.add(Distribution(c.claimant, cls, c.amount, c.amount))
                left -= totalClaim
            } else {
                val parts = splitLargestRemainder(left.raw, LongArray(inClass.size) { inClass[it].amount.raw })
                for (i in inClass.indices) {
                    val paid = Money(minOf(parts[i], inClass[i].amount.raw))
                    out.add(Distribution(inClass[i].claimant, cls, inClass[i].amount, paid))
                }
                left = Money.ZERO
            }
        }
        return out
    }

    /** Builds claims from the books of the failing company. */
    fun claimsOf(company: LedgerOwner, ledger: Ledger, loans: LoanBook, arrears: List<Arrear>, government: LedgerOwner, households: LedgerOwner): List<Claim> {
        val claims = ArrayList<Claim>()
        for (l in loans.activeFor(company)) {
            val cls = if (l.isSecured) ClaimClass.SECURED else ClaimClass.UNSECURED
            claims.add(Claim(l.lender, cls, l.outstanding + l.accruedInterest, "loan ${l.id.value}"))
        }
        for (a in arrears.filter { it.payer == company }) {
            val cls = when {
                a.payee == government -> ClaimClass.STATUTORY
                a.priority == SettlementPriority.WAGES -> ClaimClass.WAGES
                else -> ClaimClass.UNSECURED
            }
            claims.add(Claim(a.payee, cls, a.amount, a.key))
        }
        val wages = -ledger.balance(company, Chart.WAGES_PAYABLE)
        if (wages.isPositive) claims.add(Claim(households, ClaimClass.WAGES, wages, "wages payable"))
        val tax = -ledger.balance(company, Chart.TAX_PAYABLE)
        if (tax.isPositive) claims.add(Claim(government, ClaimClass.STATUTORY, tax, "tax payable"))
        return claims
    }
}
