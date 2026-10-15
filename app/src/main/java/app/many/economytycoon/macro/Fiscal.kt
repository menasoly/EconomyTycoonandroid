package app.many.economytycoon.macro

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

/** Public finances of one country: what came in, what went out and what is owed. */
class FiscalBook(val country: CountryId) {
    var debt: Money = Money.ZERO
        private set
    private var revenue: Long = 0
    private var spending: Long = 0
    var lastBalance: Money = Money.ZERO
        private set
    var lastInterest: Money = Money.ZERO
        private set

    fun addRevenue(amount: Money) { if (amount.raw > 0) revenue += amount.raw }
    fun addSpending(amount: Money) { if (amount.raw > 0) spending += amount.raw }
    fun openRevenue() = Money(revenue)
    fun openSpending() = Money(spending)

    /**
     * Closes the period. Interest on the debt is charged at [ratePpm] per year; a deficit is borrowed and a
     * surplus repays debt. Returns the balance after interest.
     */
    fun close(ratePpm: Long, periodsPerYear: Long): Money {
        val interest = Money(mulDiv(debt.raw, ratePpm, 1_000_000L * periodsPerYear))
        val balance = Money(revenue - spending - interest.raw)
        debt = Money((debt.raw - balance.raw).coerceAtLeast(0L))
        lastBalance = balance; lastInterest = interest
        revenue = 0; spending = 0
        return balance
    }

    fun debtToGdpPpm(annualGdp: Money): Long = if (annualGdp.raw <= 0L) 0L else mulDiv(debt.raw, 1_000_000L, annualGdp.raw)

    internal fun restore(debt: Money, revenue: Long, spending: Long, balance: Money, interest: Money) {
        this.debt = debt; this.revenue = revenue; this.spending = spending; lastBalance = balance; lastInterest = interest
    }
    internal val revenueRaw get() = revenue
    internal val spendingRaw get() = spending

    fun hashInto(h: CanonicalHasher) {
        h.putLong(country.value).putMoney(debt).putLong(revenue).putLong(spending).putMoney(lastBalance).putMoney(lastInterest)
    }
}

/** New headline tax rates a government chooses. */
data class TaxProposal(val corporatePpm: Long, val salesPpm: Long) {
    fun changed(corporate: Long, sales: Long) = corporatePpm != corporate || salesPpm != sales
}

/**
 * Budget rule for AI-run governments: raise taxes when debt or deficits run high, cut them when the budget
 * is comfortably in surplus. Moves are small so that rates drift rather than jump.
 */
object FiscalRule {
    const val DEBT_HIGH_PPM = 900_000L
    const val DEBT_LOW_PPM = 400_000L
    const val DEFICIT_HIGH_PPM = 50_000L
    const val STEP_PPM = 5_000L
    val CORPORATE_RANGE = 50_000L..450_000L
    val SALES_RANGE = 0L..300_000L

    fun recommend(balance: Money, annualGdp: Money, debtToGdpPpm: Long, corporatePpm: Long, salesPpm: Long): TaxProposal {
        if (annualGdp.raw <= 0L) return TaxProposal(corporatePpm, salesPpm)
        val balanceShare = mulDiv(balance.raw * 12, 1_000_000L, annualGdp.raw)
        return when {
            debtToGdpPpm > DEBT_HIGH_PPM || balanceShare < -DEFICIT_HIGH_PPM ->
                TaxProposal((corporatePpm + STEP_PPM).coerceIn(CORPORATE_RANGE), (salesPpm + STEP_PPM / 2).coerceIn(SALES_RANGE))
            debtToGdpPpm < DEBT_LOW_PPM && balanceShare > DEFICIT_HIGH_PPM ->
                TaxProposal((corporatePpm - STEP_PPM / 2).coerceIn(CORPORATE_RANGE), (salesPpm - STEP_PPM / 4).coerceIn(SALES_RANGE))
            else -> TaxProposal(corporatePpm, salesPpm)
        }
    }

    /** Raises a minimum wage by the year's inflation when prices rose; it never falls. */
    fun indexWage(current: Long, inflationPpm: Long): Long =
        if (inflationPpm <= 0L) current else current + mulDiv(current, inflationPpm, 1_000_000L)
}
