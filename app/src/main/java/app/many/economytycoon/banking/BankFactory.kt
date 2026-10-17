package app.many.economytycoon.banking

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.CreditRating

/** How a new bank's balance sheet is laid out, as shares of its total assets before the allowance for losses. */
data class OpeningSpec(
    /** Common equity paid in. Everything else follows from it. */
    val capital: Money,
    /** Share of assets that is common equity; sets the size of the bank. */
    val equityShare: Rate = Rate.percent(7),
    val loanShare: Rate = Rate.percent(68),
    val securitiesShare: Rate = Rate.percent(25),
    val fixedAssetShare: Rate = Rate.percent(2),
    val wholesaleShare: Rate = Rate.percent(8),
    val householdDepositShare: Rate = Rate.percent(62),
    /** Share of the loan book that starts out watch-listed or impaired. */
    val watchShare: Rate = Rate.percent(5),
    val impairedShare: Rate = Rate.percent(2),
    val additionalTier1Share: Rate = Rate.ZERO,
) {
    init {
        require(capital.raw > 0L) { "a bank needs capital" }
        require(equityShare.ppm in 10_000L..500_000L) { "equity share out of range" }
        require(loanShare.ppm + securitiesShare.ppm + fixedAssetShare.ppm < Rate.ONE_PPM) { "assets must leave room for reserves" }
    }
}

/**
 * Lays out the opening books of a bank. The assets are spread over sectors by the bank's appetite and over ratings
 * by the usual mix of applicants, the allowance is the expected loss at normal conditions, and deposits are whatever
 * is left to balance the books, so a new bank always starts with zero imbalance.
 */
object BankFactory {
    private val PRODUCT_MIX = mapOf(
        Depositor.HOUSEHOLD to longArrayOf(500_000L, 350_000L, 150_000L),
        Depositor.CORPORATE to longArrayOf(700_000L, 100_000L, 200_000L),
    )

    fun open(
        id: BankId, name: String, country: CountryId, operator: BankOperator, owner: CompanyId?, strategy: BankStrategy?,
        spec: OpeningSpec, inputs: BankingInputs, params: BankingParams, tick: Tick,
    ): Bank {
        val bank = Bank(id, name, country, operator, owner, tick, strategy)
        val total = Money(mulDiv(spec.capital.raw, Rate.ONE_PPM, spec.equityShare.ppm))
        bank.shareCapital = spec.capital

        // Loans by sector (weighted by appetite) and rating (by the applicant mix), most in stage 1.
        val loansTotal = total.times(spec.loanShare)
        val sectors = LoanSector.values()
        val sectorParts = BankMath.split(loansTotal, LongArray(sectors.size) { bank.policy.appetiteOf(sectors[it]).ppm.coerceAtLeast(1L) })
        val policyRate = inputs.policyRate(country)
        for ((i, sector) in sectors.withIndex()) {
            val mix = RiskTables.applicantMix(sector)
            val byRating = BankMath.split(sectorParts[i], mix.copyOf().also { if (it.all { w -> w == 0L }) it[3] = 1L })
            for ((r, rating) in RiskTables.LENDABLE.withIndex()) {
                val amount = byRating[r]
                if (amount.raw <= 0L) continue
                val rate = policyRate.ppm.coerceAtLeast(0L) + LoanPricing.creditSpread(sector, rating).ppm + 10_000L
                val impaired = amount.times(spec.impairedShare)
                val watch = amount.times(spec.watchShare)
                bank.addLoans(PoolKey(sector, rating, LoanStage.IMPAIRED), impaired, rate)
                bank.addLoans(PoolKey(sector, rating, LoanStage.WATCH), watch, rate)
                bank.addLoans(PoolKey(sector, rating, LoanStage.PERFORMING), amount - impaired - watch, rate)
            }
        }

        // Securities: short and long government debt and some corporate bonds.
        val securities = total.times(spec.securitiesShare)
        val sec = BankMath.split(securities, longArrayOf(400_000L, 450_000L, 150_000L))
        bank.putSecurity(SecurityKind.GOVERNMENT_SHORT, SecurityHolding(sec[0], inputs.sovereignYield(country, 6).ppm, 5))
        bank.putSecurity(SecurityKind.GOVERNMENT_LONG, SecurityHolding(sec[1], inputs.sovereignYield(country, 84).ppm, 60))
        bank.putSecurity(SecurityKind.CORPORATE_BONDS, SecurityHolding(sec[2], inputs.sovereignYield(country, 60).ppm + 150_00L, 36))
        bank.fixedAssets = total.times(spec.fixedAssetShare)
        bank.reserves = total - bank.loansGrossValue() - securities - bank.fixedAssets
        bank.wholesale = total.times(spec.wholesaleShare)
        bank.wholesaleRatePpm = policyRate.ppm + 30_000L
        bank.additionalTier1 = total.times(spec.additionalTier1Share)

        // Expected losses at normal conditions, and the deposits that balance everything.
        bank.allowance = Provisioning.ecl(bank.snapshot(), { Rate.ONE_PPM }, Rate.ONE_PPM).total
        val deposits = total - bank.allowance - spec.capital - bank.wholesale - bank.additionalTier1
        require(deposits.raw > 0L) { "the opening books leave nothing for depositors" }
        val household = deposits.times(spec.householdDepositShare)
        for ((depositor, amount) in listOf(Depositor.HOUSEHOLD to household, Depositor.CORPORATE to deposits - household)) {
            val parts = BankMath.split(amount, PRODUCT_MIX.getValue(depositor))
            for ((p, product) in DepositProduct.values().withIndex()) bank.addDeposits(DepositKey(depositor, product), parts[p])
        }
        DepositMarket.reprice(bank, policyRate, params.deposits)
        bank.setRating(CreditRating.BBB, 500, 0, 0)
        return bank
    }

    private fun Bank.loansGrossValue(): Money = Money.sum(loans.values.map { it.principal })
}
