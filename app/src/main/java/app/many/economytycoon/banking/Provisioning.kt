package app.many.economytycoon.banking

import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.mulDiv

/** Expected credit loss held against each stage, in money. */
data class EclBreakdown(val performing: Money, val watch: Money, val impaired: Money) {
    val total: Money get() = performing + watch + impaired
}

/** What moved between stages in a month. */
data class MigrationReport(val toWatch: Money, val toImpaired: Money, val cured: Money, val curedImpaired: Money) {
    companion object { val NONE = MigrationReport(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO) }
}

/** Loans removed from the book in a month. */
data class WriteOffReport(val written: Money, val recovered: Money) {
    /** The part of the written-off balance that was lost. */
    val loss: Money get() = written - recovered

    companion object { val NONE = WriteOffReport(Money.ZERO, Money.ZERO) }
}

/**
 * Expected credit loss accounting in three stages. Performing loans carry the loss expected over twelve months, loans
 * with a significant rise in risk carry the loss expected over their remaining life, and impaired loans carry the whole
 * expected loss given default. Loans move between stages with the state of the economy and are written off after
 * work-out, the recovered part coming back as cash.
 */
object Provisioning {
    private fun remainingMonths(sector: LoanSector): Int = (RiskTables.termMonths(sector) / 2).coerceAtLeast(12)

    fun ecl(s: BankSnapshot, multiplier: (LoanSector) -> Long, collateralIndexPpm: Long): EclBreakdown {
        var performing = Money.ZERO
        var watch = Money.ZERO
        var impaired = Money.ZERO
        for ((key, pool) in s.loans) {
            val lgd = RiskTables.lgd(key.sector, collateralIndexPpm)
            when (key.stage) {
                LoanStage.PERFORMING -> {
                    val pd = RiskTables.stressedPd(key.rating, multiplier(key.sector))
                    performing += pool.principal.times(pd).times(lgd)
                }
                LoanStage.WATCH -> {
                    val pd = RiskTables.cumulativePd(RiskTables.stressedPd(key.rating, multiplier(key.sector)).let { Rate(maxOf(it.ppm, 4_000L)) }, remainingMonths(key.sector))
                    watch += pool.principal.times(pd).times(lgd)
                }
                LoanStage.IMPAIRED -> impaired += pool.principal.times(lgd)
            }
        }
        return EclBreakdown(performing, watch, impaired)
    }

    /** Loss expected over the next year on the performing and watch-listed book. Impaired loans are already provided for. */
    fun annualExpectedLoss(s: BankSnapshot, multiplier: (LoanSector) -> Long, collateralIndexPpm: Long = Rate.ONE_PPM): Money {
        var total = Money.ZERO
        for ((key, pool) in s.loans) {
            if (key.stage == LoanStage.IMPAIRED) continue
            total += pool.principal.times(Underwriter.expectedLossRate(key.sector, key.rating, multiplier(key.sector), collateralIndexPpm))
        }
        return total
    }

    /**
     * Moves loans between stages for one month. The expected migration is scaled by a noise factor drawn from [random]
     * so that banks with identical books do not move in lockstep; the factor stays within the configured band.
     */
    fun migrate(bank: Bank, multiplier: (LoanSector) -> Long, noise: Rate, random: DeterministicRandom): MigrationReport {
        var toWatch = Money.ZERO
        var toImpaired = Money.ZERO
        var cured = Money.ZERO
        var curedImpaired = Money.ZERO
        val keys = bank.loans.keys.toList()
        for (key in keys) {
            val pool = bank.loans[key] ?: continue
            val m = multiplier(key.sector)
            val jitter = Rate.ONE_PPM + mulDiv(random.nextGaussianPpm(), noise.ppm, Rate.ONE_PPM).coerceIn(-noise.ppm * 2L, noise.ppm * 2L)
            fun moved(rate: Long): Money = Money(mulDiv(mulDiv(pool.principal.raw, rate, Rate.ONE_PPM), jitter, Rate.ONE_PPM)).coerceAtMost(pool.principal)
            when (key.stage) {
                LoanStage.PERFORMING -> {
                    val monthly = RiskTables.stressedPd(key.rating, m).ppm / 12L
                    val direct = moved(mulDiv(monthly, RiskTables.directDefaultShare().ppm, Rate.ONE_PPM))
                    val watch = moved(monthly * RiskTables.watchlistMultiple()).coerceAtMost(pool.principal - direct)
                    shift(bank, key, LoanStage.IMPAIRED, direct)
                    shift(bank, key, LoanStage.WATCH, watch)
                    toImpaired += direct
                    toWatch += watch
                }
                LoanStage.WATCH -> {
                    val worse = moved(RiskTables.watchToImpaired(m).ppm)
                    val better = moved(RiskTables.watchCure(m).ppm).coerceAtMost(pool.principal - worse)
                    shift(bank, key, LoanStage.IMPAIRED, worse)
                    shift(bank, key, LoanStage.PERFORMING, better)
                    toImpaired += worse
                    cured += better
                }
                LoanStage.IMPAIRED -> {
                    val back = Money(mulDiv(pool.principal.raw, RiskTables.impairedCure().ppm, Rate.ONE_PPM))
                    shift(bank, key, LoanStage.WATCH, back)
                    curedImpaired += back
                }
            }
        }
        return MigrationReport(toWatch, toImpaired, cured, curedImpaired)
    }

    private fun shift(bank: Bank, from: PoolKey, to: LoanStage, amount: Money) {
        if (amount.raw <= 0L) return
        val pool = bank.loans[from] ?: return
        val taken = bank.removeLoans(from, amount)
        bank.addLoans(from.copy(stage = to), taken, pool.ratePpm)
    }

    /**
     * Writes off a share of the impaired loans. The recovered part comes back as cash and the lost part is taken from the
     * allowance, which [adjustAllowance] then tops up or releases.
     */
    fun writeOff(bank: Bank, collateralIndexPpm: Long): WriteOffReport {
        var written = Money.ZERO
        var recovered = Money.ZERO
        for (key in bank.loans.keys.toList()) {
            if (key.stage != LoanStage.IMPAIRED) continue
            val pool = bank.loans[key] ?: continue
            val amount = Money(mulDiv(pool.principal.raw, RiskTables.monthlyWriteOff().ppm, Rate.ONE_PPM))
            if (amount.raw <= 0L) continue
            val taken = bank.removeLoans(key, amount)
            val back = taken - taken.times(RiskTables.lgd(key.sector, collateralIndexPpm))
            written += taken
            recovered += back
        }
        if (written.raw > 0L) {
            bank.reserves += recovered
            bank.allowance -= (written - recovered)
        }
        return WriteOffReport(written, recovered)
    }

    /**
     * Sets the allowance to the expected loss. The change goes through retained earnings and is returned: a positive
     * figure is a charge, a negative one a release.
     */
    fun adjustAllowance(bank: Bank, multiplier: (LoanSector) -> Long, collateralIndexPpm: Long): Money {
        val required = ecl(bank.snapshot(), multiplier, collateralIndexPpm).total
        val charge = required - bank.allowance
        bank.allowance = required
        bank.retained -= charge
        return charge
    }
}
