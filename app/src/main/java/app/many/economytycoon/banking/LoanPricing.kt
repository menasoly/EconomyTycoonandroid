package app.many.economytycoon.banking

import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditRating

/**
 * What a loan costs, in parts: the central bank's rate, the borrower's credit spread, a premium for lending long
 * and a premium for the bank's own funding strain, plus whatever margin the bank's management adds.
 */
data class LoanQuote(
    val sector: LoanSector,
    val rating: CreditRating,
    val termMonths: Int,
    val baseRate: Rate,
    val creditSpread: Rate,
    val termPremium: Rate,
    val liquidityPremium: Rate,
    val margin: Rate,
) {
    /** The annual rate the borrower is quoted. Never below the base rate. */
    val total: Rate get() = Rate(maxOf(baseRate.ppm, baseRate.ppm + creditSpread.ppm + termPremium.ppm + liquidityPremium.ppm + margin.ppm))

    /** The parts as labelled pairs, for display. */
    fun parts(): List<Pair<String, Rate>> = listOf(
        "Base rate" to baseRate, "Credit spread" to creditSpread, "Term premium" to termPremium,
        "Liquidity premium" to liquidityPremium, "Bank margin" to margin,
    )
}

object LoanPricing {
    fun baseRate(inputs: MonetaryView, bank: BankSnapshot): Rate = Rate(inputs.policyRate(bank.country).ppm.coerceAtLeast(0L))

    /** Premium for the curve's slope out to the term, or for the time itself when the curve is flat. */
    fun termPremium(inputs: BankingInputs, bank: BankSnapshot, termMonths: Int, p: LendingParams): Rate {
        val policy = inputs.policyRate(bank.country).ppm
        val curve = (inputs.sovereignYield(bank.country, termMonths.coerceAtMost(120)).ppm - policy).coerceAtLeast(0L)
        val perYear = p.termPremiumPerYear.ppm * (termMonths.coerceAtMost(60).toLong()) / 12L
        return Rate(maxOf(curve, perYear).coerceAtMost(p.maxTermPremium.ppm))
    }

    fun creditSpread(sector: LoanSector, rating: CreditRating): Rate =
        Rate(rating.spread.ppm * RiskTables.spreadFactor(sector).ppm / Rate.ONE_PPM)

    /** Premium for the strain on the bank's own liquidity and funding. */
    fun liquidityPremium(bank: BankSnapshot, liquidity: LiquidityPosition, targetLcr: Rate, p: LendingParams): Rate {
        val shortfall = (targetLcr.ppm - liquidity.lcr.ppm).coerceIn(0L, Rate.ONE_PPM)
        val fromLcr = p.liquidityPremiumPerShortfall.ppm * shortfall / Rate.ONE_PPM
        val fromWholesale = p.liquidityPremiumPerWholesale.ppm * bank.wholesaleShare.ppm / Rate.ONE_PPM
        return Rate((fromLcr + fromWholesale).coerceAtMost(p.maxLiquidityPremium.ppm))
    }

    fun quote(
        inputs: BankingInputs, bank: BankSnapshot, liquidity: LiquidityPosition, sector: LoanSector, rating: CreditRating, params: BankingParams,
    ): LoanQuote {
        val term = RiskTables.termMonths(sector)
        return LoanQuote(
            sector, rating, term, baseRate(inputs, bank), creditSpread(sector, rating),
            termPremium(inputs, bank, term, params.lending),
            liquidityPremium(bank, liquidity, bank.policy.targetLcr, params.lending),
            Rate.basisPoints(bank.policy.loanMarginBps),
        )
    }

    /** Average cost of the bank's interest-bearing funding, annual. */
    fun fundingCost(bank: BankSnapshot, inputs: MonetaryView, facilities: FacilityParams): Rate {
        val policy = inputs.policyRate(bank.country).ppm
        var weighted = java.math.BigInteger.ZERO
        var total = 0L
        fun add(balance: Long, rate: Long) {
            if (balance <= 0L) return
            weighted = weighted.add(java.math.BigInteger.valueOf(balance).multiply(java.math.BigInteger.valueOf(rate)))
            total += balance
        }
        for (pool in bank.deposits.values) add(pool.balance.raw, pool.ratePpm)
        add(bank.wholesale.raw, bank.wholesaleRatePpm)
        add(bank.interbankBorrowed.raw, policy)
        add(bank.centralBankMarginal.raw, policy + facilities.corridorHalfWidth.ppm)
        add(bank.centralBankEmergency.raw, policy + facilities.corridorHalfWidth.ppm + facilities.emergencyPenalty.ppm)
        return if (total <= 0L) Rate((policy - 50_000L).coerceAtLeast(0L)) else Rate(weighted.divide(java.math.BigInteger.valueOf(total)).toLong())
    }
}
