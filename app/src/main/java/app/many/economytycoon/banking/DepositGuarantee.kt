package app.many.economytycoon.banking

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.CreditRating

/**
 * The deposit guarantee fund of a country. Banks pay levies into it in good times; when a bank fails it pays insured
 * depositors or supports a merger. If it runs dry the banks are levied again, up to a cap, and the state lends the rest.
 */
class GuaranteeFund(val country: CountryId) {
    var balance: Money = Money.ZERO
        internal set

    /** What the fund owes the state. Repaid out of levies before the balance builds again. */
    var stateLoan: Money = Money.ZERO
        internal set
    var totalLevied: Money = Money.ZERO
        internal set
    var totalPaid: Money = Money.ZERO
        internal set
    var lastLevy: Long = 0L
        internal set

    /** Extraordinary levies raised in the current year, and the day the year began. */
    var extraordinaryThisYear: Money = Money.ZERO
        internal set
    var yearStart: Long = 0L
        internal set

    fun hashInto(h: CanonicalHasher) {
        h.putLong(country.value).putMoney(balance).putMoney(stateLoan).putMoney(totalLevied).putMoney(totalPaid)
            .putLong(lastLevy).putMoney(extraordinaryThisYear).putLong(yearStart)
    }

    internal fun restore(balance: Money, loan: Money, levied: Money, paid: Money, lastLevy: Long, extra: Money, yearStart: Long) {
        this.balance = balance; stateLoan = loan; totalLevied = levied; totalPaid = paid
        this.lastLevy = lastLevy; extraordinaryThisYear = extra; this.yearStart = yearStart
    }
}

/** A levy collected from one bank. */
data class Levy(val bank: BankId, val amount: Money)

/** What a payout from the fund achieved. */
data class FundPayout(val paid: Money, val fromBalance: Money, val extraordinary: Money, val stateLoan: Money)

object GuaranteeScheme {
    /** Deposits the scheme stands behind. */
    fun coveredDeposits(s: BankSnapshot, p: DepositParams): Money {
        var total = Money.ZERO
        for ((key, pool) in s.deposits) total += pool.balance.times(p.insuredShare(key.depositor))
        return total
    }

    fun target(covered: Money, p: ResolutionParams): Money = covered.times(p.fundTarget)

    /** Risky banks pay more: from the best factor at AAA to the worst at CCC and below, in even steps. */
    fun riskFactor(rating: CreditRating, p: ResolutionParams): Rate {
        val steps = CreditRating.CCC.ordinal
        val position = rating.ordinal.coerceIn(0, steps).toLong()
        return Rate(p.riskFactorBest.ppm + (p.riskFactorWorst.ppm - p.riskFactorBest.ppm) * position / steps)
    }

    /** How much of the covered deposits the fund could pay today, from 0 to 1,000,000. */
    fun coverage(fund: GuaranteeFund, covered: Money): Rate =
        if (covered.raw <= 0L) Rate.ONE else Rate(BankMath.ratio(fund.balance, covered).ppm.coerceIn(0L, Rate.ONE_PPM))

    fun isLevyDay(fund: GuaranteeFund, tick: Long, p: ResolutionParams): Boolean = tick - fund.lastLevy >= p.levyEvery && tick > 0L

    /** The levy each bank owes on a levy day. Nothing is asked once the fund has reached its target and owes nothing. */
    fun levies(fund: GuaranteeFund, banks: List<BankSnapshot>, p: ResolutionParams, deposits: DepositParams, daysSinceLast: Long): List<Levy> {
        val live = banks.filter { it.isOperating }.sortedBy { it.id }
        if (live.isEmpty()) return emptyList()
        val covered = live.map { coveredDeposits(it, deposits) }
        val totalCovered = Money.sum(covered)
        val room = (target(totalCovered, p) + fund.stateLoan - fund.balance).coerceAtLeast(Money.ZERO)
        if (room.isZero) return live.map { Levy(it.id, Money.ZERO) }
        val wanted = live.indices.map { i ->
            val base = covered[i].times(p.levyRate)
            val timed = Money(mulDiv(base.raw, daysSinceLast.coerceIn(0L, 360L), 360L))
            timed.times(riskFactor(live[i].rating, p))
        }
        val totalWanted = Money.sum(wanted)
        val scale = if (totalWanted > room) room else totalWanted
        val parts = BankMath.split(scale, LongArray(live.size) { wanted[it].raw })
        return live.indices.map { Levy(live[it].id, parts[it]) }
    }

    /** Takes the levies out of the banks and puts them in the fund, repaying the state first. */
    fun collect(fund: GuaranteeFund, banks: List<Bank>, levies: List<Levy>, tick: Long): Money {
        var total = Money.ZERO
        val byId = banks.associateBy { it.id }
        for (levy in levies) {
            val bank = byId[levy.bank] ?: continue
            val amount = levy.amount
            if (amount.raw <= 0L) continue
            bank.reserves -= amount
            bank.retained -= amount
            bank.levyPaid += amount
            total += amount
        }
        val repay = Money.min(total, fund.stateLoan)
        fund.stateLoan -= repay
        fund.balance += total - repay
        fund.totalLevied += total
        fund.lastLevy = tick
        return total
    }

    /** Starts a new year of extraordinary levy accounting when a year has passed. */
    fun rollYear(fund: GuaranteeFund, tick: Long) {
        if (tick - fund.yearStart >= BankMath.YEAR_TICKS) {
            fund.yearStart = tick
            fund.extraordinaryThisYear = Money.ZERO
        }
    }

    /**
     * Pays [amount] out of the fund. If the balance is short the banks are levied once more, up to the yearly cap, and
     * the state lends what is still missing, so the payout is always made in full.
     */
    fun payOut(fund: GuaranteeFund, amount: Money, banks: List<Bank>, p: ResolutionParams, deposits: DepositParams, tick: Long): FundPayout {
        if (amount.raw <= 0L) return FundPayout(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO)
        rollYear(fund, tick)
        val fromBalance = Money.min(amount, fund.balance)
        fund.balance -= fromBalance
        var missing = amount - fromBalance
        var extraordinary = Money.ZERO
        if (missing.isPositive) {
            val live = banks.filter { it.status.isOperating }.sortedBy { it.id }
            val covered = live.map { coveredDeposits(it.snapshot(), deposits) }
            val capTotal = Money.sum(covered).times(p.extraordinaryCap)
            val room = (capTotal - fund.extraordinaryThisYear).coerceAtLeast(Money.ZERO)
            val ask = Money.min(missing, room)
            if (ask.isPositive && live.isNotEmpty() && covered.any { it.isPositive }) {
                val parts = BankMath.split(ask, LongArray(live.size) { covered[it].raw })
                for (i in live.indices) {
                    live[i].reserves -= parts[i]; live[i].retained -= parts[i]; live[i].levyPaid += parts[i]
                }
                extraordinary = ask
                fund.extraordinaryThisYear += ask
                fund.totalLevied += ask
                missing -= ask
            }
        }
        val loan = missing
        fund.stateLoan += loan
        fund.totalPaid += amount
        return FundPayout(amount, fromBalance, extraordinary, loan)
    }
}
