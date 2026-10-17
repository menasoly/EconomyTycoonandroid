package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate

/** What a bank had to do on one day to keep paying out. */
data class LiquidityDay(
    val withdrawn: Money,
    val marginal: Money,
    val emergency: Money,
    val fireSold: Money,
    /** Reserves still missing after everything above; positive means the bank could not meet its obligations. */
    val unmet: Money,
) {
    val failed: Boolean get() = unmet.isPositive

    companion object { val NONE = LiquidityDay(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO) }
}

/**
 * The daily cash management of a bank: paying out withdrawals, then covering a shortage from the central bank's
 * facilities and, as a last resort, by selling securities at a discount.
 */
class LiquidityManager(private val params: BankingParams) {

    /** Pays out withdrawals from reserves. Reserves may go below zero until [cover] restores them. Returns the amount paid. */
    fun payWithdrawals(bank: Bank, plan: Map<DepositKey, Money>): Money {
        var paid = Money.ZERO
        for ((key, amount) in plan) {
            val got = bank.removeDeposits(key, amount)
            bank.reserves -= got
            paid += got
        }
        bank.lastOutflow = paid
        return paid
    }

    private fun discountFor(kind: SecurityKind): Rate {
        val base = params.runs.fireSaleDiscount
        return when (kind) {
            SecurityKind.GOVERNMENT_SHORT -> Rate(base.ppm / 4L)
            SecurityKind.GOVERNMENT_LONG -> base
            SecurityKind.CORPORATE_BONDS -> Rate(base.ppm * 2L)
        }
    }

    /**
     * Brings reserves back up to the legal requirement. Borrows from the ordinary facility first, then emergency
     * assistance if the bank is eligible, then sells securities, short government debt first. [withdrawn] is only recorded.
     */
    fun cover(bank: Bank, withdrawn: Money, allowFireSale: Boolean): LiquidityDay {
        val snap = bank.snapshot()
        val required = LiquidityEngine.requiredReserves(snap, params.liquidity)
        var need = required - bank.reserves
        if (need.raw <= 0L) return LiquidityDay(withdrawn, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO)

        val marginal = CentralBankFacilities.borrowMarginal(bank, need, params.facilities)
        need -= marginal
        var emergency = Money.ZERO
        if (need.isPositive) {
            val cet1 = CapitalEngine.position(bank.snapshot(), params.capital).cet1Ratio
            emergency = CentralBankFacilities.borrowEmergency(bank, need, cet1, params.facilities)
            need -= emergency
        }
        var sold = Money.ZERO
        if (need.isPositive && allowFireSale) {
            for (kind in listOf(SecurityKind.GOVERNMENT_SHORT, SecurityKind.GOVERNMENT_LONG, SecurityKind.CORPORATE_BONDS)) {
                if (need.raw <= 0L) break
                val holding = bank.securities[kind] ?: continue
                // Selling X at a discount raises X(1-d); sell enough to raise what is needed.
                val d = discountFor(kind)
                val gross = Money(java.math.BigInteger.valueOf(need.raw).multiply(java.math.BigInteger.valueOf(Rate.ONE_PPM))
                    .divide(java.math.BigInteger.valueOf((Rate.ONE_PPM - d.ppm).coerceAtLeast(1L))).toLong() + 1L)
                val done = bank.sellSecurities(kind, Money.min(gross, holding.value), d)
                sold += done
                need = required - bank.reserves
            }
        }
        // Only a negative balance is an actual failure to pay; falling short of the requirement is a breach.
        val unmet = if (bank.reserves.raw < 0L) -bank.reserves else Money.ZERO
        return LiquidityDay(withdrawn, marginal, emergency, sold, unmet)
    }

    /** Repays facility borrowing out of reserves held above the working target. Returns the amount repaid. */
    fun sweep(bank: Bank): Money {
        if (bank.centralBankDebt.raw <= 0L) return Money.ZERO
        val snap = bank.snapshot()
        val desired = LiquidityEngine.position(snap, params.liquidity, params.deposits::insuredShare).desiredReserves
        val spare = bank.reserves - desired
        if (spare.raw <= 0L) return Money.ZERO
        return CentralBankFacilities.repay(bank, spare)
    }
}
