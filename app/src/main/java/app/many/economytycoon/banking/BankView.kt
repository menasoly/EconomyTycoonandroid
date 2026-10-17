package app.many.economytycoon.banking

import app.many.economytycoon.core.Rate

/**
 * A bank's snapshot together with the figures worked out from it and the conditions it operates in. The planners and the
 * advisor read everything from here, so each figure is computed at most once per decision round.
 */
class BankView(val bank: BankSnapshot, val inputs: BankingInputs, val params: BankingParams) {
    val capital: CapitalPosition by lazy { CapitalEngine.position(bank, params.capital) }
    val liquidity: LiquidityPosition by lazy { LiquidityEngine.position(bank, params.liquidity, params.deposits::insuredShare) }
    val policyRate: Rate get() = inputs.policyRate(bank.country)
    val margin: MarginReport by lazy {
        MarginAnalytics.report(bank, policyRate, params, { inputs.defaultMultiplier(bank.country, it) }, inputs.collateralIndex(bank.country))
    }
    val dividend: DividendCapacity by lazy { Dividends.capacity(bank, capital, liquidity, params) }
    val lending: LendingContext by lazy { LendingContext(inputs, params, bank) }

    /** Securities as a share of total assets. */
    val securitiesShare: Rate get() = BankMath.ratio(bank.securitiesValue(), bank.totalAssets)

    /** Capital above the requirement and the bank's own cushion, as a rate of risk-weighted assets; negative when short. */
    val cushionHeadroom: Rate get() = capital.headroom - bank.policy.capitalCushion

    /** Deposit funding that is up for grabs: the share of deposits the market could move in a month. */
    val depositMomentum: Rate
        get() {
            val last = bank.history.lastOrNull() ?: return Rate.ZERO
            return BankMath.ratio(last.averageDeposits - bank.depositsTotal, bank.depositsTotal)
        }
}
