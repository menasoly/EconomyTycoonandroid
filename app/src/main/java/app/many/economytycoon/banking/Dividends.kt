package app.many.economytycoon.banking

import app.many.economytycoon.core.Money

enum class DividendLimit(val label: String) {
    NONE("Paid in full at the payout target"),
    NO_PROFIT("No profit to distribute"),
    CAPITAL_BUFFER("Capital buffer restricts distributions"),
    CAPITAL_TARGET("Capital would fall below the bank's own target"),
    LIQUIDITY("Liquidity would fall too low"),
    SUPERVISOR("The supervisor has stopped distributions"),
}

/** What a bank can pay its owners now, and what holds it back. */
data class DividendCapacity(
    val profit: Money,
    val policyAmount: Money,
    val capitalAmount: Money,
    val liquidityAmount: Money,
    val amount: Money,
    val limitedBy: DividendLimit,
)

object Dividends {
    /** Months of profit a dividend is paid from. */
    const val PERIOD_MONTHS = 3

    /**
     * The dividend a bank can pay: the payout share of the last quarter's profit, cut back by the distribution limit of the
     * capital buffer, by the capital the bank wants to keep above its requirement, by liquidity, and to nothing while the
     * supervisor forbids it.
     */
    fun capacity(s: BankSnapshot, capital: CapitalPosition, liquidity: LiquidityPosition, params: BankingParams): DividendCapacity {
        val recent = s.history.takeLast(PERIOD_MONTHS)
        val profit = Money.sum(recent.map { it.netIncome }).coerceAtLeast(Money.ZERO)
        val distributable = Money.min(profit, s.retained.coerceAtLeast(Money.ZERO))
        if (s.status != BankStatus.ACTIVE || s.rung.isBreach || s.runStage != RunStage.CALM) {
            return DividendCapacity(profit, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, DividendLimit.SUPERVISOR)
        }
        if (distributable.raw <= 0L) return DividendCapacity(profit, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, DividendLimit.NO_PROFIT)
        val byPolicy = distributable.times(s.policy.dividendPayout)
        val byBuffer = byPolicy.times(capital.distributionLimit)
        val keepRatio = capital.cet1Requirement + s.policy.capitalCushion
        val surplus = capital.cet1 - capital.rwa.total.times(keepRatio)
        val byCapital = Money.min(byBuffer, surplus.coerceAtLeast(Money.ZERO))
        val spare = liquidity.reserves - liquidity.desiredReserves
        val byLiquidity = Money.min(byCapital, spare.coerceAtLeast(Money.ZERO))
        val amount = byLiquidity
        val limit = when {
            amount == byPolicy -> DividendLimit.NONE
            byLiquidity < byCapital -> DividendLimit.LIQUIDITY
            byCapital < byBuffer -> DividendLimit.CAPITAL_TARGET
            else -> DividendLimit.CAPITAL_BUFFER
        }
        return DividendCapacity(profit, byPolicy, byCapital, byLiquidity, amount, limit)
    }

    /** Pays a dividend out of reserves and retained earnings. Returns the amount paid, zero if it would unbalance anything. */
    fun pay(bank: Bank, amount: Money): Money {
        val pay = Money.min(Money.min(amount, bank.reserves.coerceAtLeast(Money.ZERO)), bank.retained.coerceAtLeast(Money.ZERO))
        if (pay.raw <= 0L) return Money.ZERO
        bank.reserves -= pay
        bank.retained -= pay
        return pay
    }
}
