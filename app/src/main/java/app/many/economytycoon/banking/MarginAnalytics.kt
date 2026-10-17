package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.mulDiv

/** What one sector of lending earns and costs, annual. */
data class SectorMargin(
    val sector: LoanSector,
    val balance: Money,
    val share: Rate,
    val yield: Rate,
    val expectedLoss: Rate,
    /** Yield less the bank's funding cost and expected loss. */
    val riskAdjustedSpread: Rate,
    val impairedShare: Rate,
)

/** One line of funding with its balance and what it costs. */
data class FundingLine(val label: String, val balance: Money, val rate: Rate)

/** Net interest margin and the rest of the profit picture for one bank. */
data class MarginReport(
    val netInterestMargin: Rate,
    val trailingMargin: Rate,
    val assetYield: Rate,
    val fundingCost: Rate,
    val loanYield: Rate,
    val costToIncome: Rate,
    val returnOnEquity: Rate,
    val returnOnAssets: Rate,
    val costOfRisk: Rate,
    val bySector: List<SectorMargin>,
    val funding: List<FundingLine>,
    /** Yearly change in net interest income for a one point rise in all rates. */
    val sensitivityPerPoint: Money,
    val monthlyMargin: List<Rate>,
)

/** Where a change in net interest income came from. */
data class NiiAttribution(val total: Money, val volume: Money, val rate: Money, val residual: Money)

object MarginAnalytics {
    /**
     * Yearly change in net interest income from a parallel rate shift: assets that reprice (floating loans, short
     * securities, cash) less funding that reprices (wholesale, borrowing and deposits in proportion to their beta).
     */
    fun annualNiiChange(s: BankSnapshot, shock: Rate, deposits: DepositParams): Money {
        var repricing = Money.ZERO
        for ((key, pool) in s.loans) if (key.stage != LoanStage.IMPAIRED) repricing += pool.principal.times(RiskTables.floatingShare(key.sector))
        repricing += s.securityOf(SecurityKind.GOVERNMENT_SHORT)
        repricing += s.reserves.coerceAtLeast(Money.ZERO)
        var funding = s.wholesale + s.interbankBorrowed + s.centralBankDebt
        for ((key, pool) in s.deposits) {
            val beta = deposits.beta(key.product).ppm + if (key.depositor == Depositor.CORPORATE) deposits.corporateBetaBonus.ppm else 0L
            funding += pool.balance.times(Rate(beta.coerceAtMost(Rate.ONE_PPM)))
        }
        return Money(mulDiv((repricing - funding).raw, shock.ppm, Rate.ONE_PPM))
    }

    /** Splits the change in monthly net interest income between a bigger or smaller balance sheet and a better or worse margin. */
    fun attribution(before: MonthlyResult, after: MonthlyResult): NiiAttribution {
        val total = after.netInterestIncome - before.netInterestIncome
        val ea0 = before.averageEarningAssets
        val ea1 = after.averageEarningAssets
        if (ea0.raw <= 0L || ea1.raw <= 0L) return NiiAttribution(total, Money.ZERO, Money.ZERO, total)
        val m0 = before.netInterestIncome
        val volume = Money(mulDiv(m0.raw, (ea1 - ea0).raw, ea0.raw))
        // Rate effect: the change in margin per unit of assets, on today's assets.
        val rate = Money(mulDiv(ea1.raw, after.netInterestIncome.raw, ea1.raw) - mulDiv(ea1.raw, m0.raw, ea0.raw))
        return NiiAttribution(total, volume, rate, total - volume - rate)
    }

    fun report(s: BankSnapshot, policy: Rate, params: BankingParams, multiplier: (LoanSector) -> Long, collateralIndex: Long): MarginReport {
        val last = s.lastResult
        val recent = s.history.takeLast(12)
        val trailing = if (recent.isEmpty()) Rate.ZERO else {
            val nii = Money.sum(recent.map { it.netInterestIncome })
            val assets = Money.sum(recent.map { it.averageEarningAssets })
            if (assets.raw <= 0L) Rate.ZERO else Rate(mulDiv(nii.raw, 12L * Rate.ONE_PPM, assets.raw))
        }
        val funding = fundingLines(s)
        val fundingCost = weightedRate(funding)
        val loanYield = Rate(s.averageLoanRatePpm())
        val earning = s.earningAssets
        var income = Money.ZERO
        for ((key, pool) in s.loans) if (key.stage != LoanStage.IMPAIRED) income += BankMath.monthlyInterest(pool.principal, pool.ratePpm)
        for (h in s.securities.values) income += BankMath.monthlyInterest(h.value, h.yieldPpm)
        val assetYield = if (earning.raw <= 0L) Rate.ZERO else Rate(mulDiv(income.raw, 12L * Rate.ONE_PPM, earning.raw))
        val sectors = LoanSector.values().filter { s.loansOf(it).isPositive }.map { sector ->
            val balance = s.loansOf(sector)
            val perf = s.loans.filter { it.key.sector == sector && it.key.stage != LoanStage.IMPAIRED }
            val perfBalance = perf.values.sumOf { it.principal.raw }
            val yieldPpm = if (perfBalance <= 0L) 0L else perf.values.sumOf { mulDiv(it.principal.raw, it.ratePpm, perfBalance) }
            val loss = Underwriter.expectedLossRateOfBook(s, sector, multiplier(sector), collateralIndex)
            SectorMargin(
                sector, balance, s.sectorShare(sector), Rate(yieldPpm), loss, Rate(yieldPpm - fundingCost.ppm - loss.ppm),
                BankMath.ratio(s.loans.filter { it.key.sector == sector && it.key.stage == LoanStage.IMPAIRED }.values.fold(Money.ZERO) { a, p -> a + p.principal }, balance),
            )
        }
        return MarginReport(
            last?.netInterestMargin ?: Rate.ZERO, trailing, assetYield, fundingCost, loanYield,
            last?.costToIncome ?: Rate.ZERO, last?.returnOnEquity ?: Rate.ZERO, last?.returnOnAssets ?: Rate.ZERO, last?.costOfRisk ?: Rate.ZERO,
            sectors, funding, annualNiiChange(s, Rate.percent(1), params.deposits), s.history.map { it.netInterestMargin },
        )
    }

    fun fundingLines(s: BankSnapshot): List<FundingLine> {
        val lines = ArrayList<FundingLine>()
        for ((key, pool) in s.deposits) if (pool.balance.isPositive) lines.add(FundingLine("${key.depositor.label} ${key.product.label.lowercase()}", pool.balance, Rate(pool.ratePpm)))
        if (s.wholesale.isPositive) lines.add(FundingLine("Wholesale funding", s.wholesale, Rate(s.wholesaleRatePpm)))
        return lines
    }

    private fun weightedRate(lines: List<FundingLine>): Rate {
        val total = lines.sumOf { it.balance.raw }
        if (total <= 0L) return Rate.ZERO
        var acc = java.math.BigInteger.ZERO
        for (l in lines) acc = acc.add(java.math.BigInteger.valueOf(l.balance.raw).multiply(java.math.BigInteger.valueOf(l.rate.ppm)))
        return Rate(acc.divide(java.math.BigInteger.valueOf(total)).toLong())
    }
}
