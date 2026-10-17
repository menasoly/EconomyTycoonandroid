package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.mulDiv

/** Risk-weighted assets by source. */
data class RwaBreakdown(val credit: Money, val securities: Money, val other: Money, val operational: Money) {
    val total: Money get() = credit + securities + other + operational
}

/** Where a bank stands against the capital rules. */
data class CapitalPosition(
    val rwa: RwaBreakdown,
    val cet1: Money,
    val tier1: Money,
    val totalCapital: Money,
    val cet1Ratio: Rate,
    val tier1Ratio: Rate,
    val totalRatio: Rate,
    val leverageRatio: Rate,
    /** The buffers on top of the minimum CET1 ratio: conservation, countercyclical and systemic. */
    val combinedBuffer: Rate,
    /** CET1 ratio the bank must hold to be free of restrictions: the minimum plus the combined buffer. */
    val cet1Requirement: Rate,
    val rung: SupervisoryRung,
    /** Share of profit that may be distributed. */
    val distributionLimit: Rate,
) {
    /** Points of CET1 ratio above the requirement; negative inside the buffer or below it. */
    val headroom: Rate get() = cet1Ratio - cet1Requirement

    /** CET1 capital above what the requirement asks of the current risk-weighted assets. May be negative. */
    val surplus: Money get() = cet1 - rwa.total.times(cet1Requirement)
}

/** Capital adequacy: risk-weighted assets, ratios, buffers and the supervisory ladder. */
object CapitalEngine {
    private val OTHER_ASSETS_FLOOR = Rate.percent(3)

    fun rwa(s: BankSnapshot, rules: CapitalRules): RwaBreakdown {
        var credit = Money.ZERO
        for ((key, pool) in s.loans) credit += pool.principal.times(RiskTables.riskWeight(key.sector, key.rating, key.stage))
        var securities = Money.ZERO
        for ((kind, h) in s.securities) securities += h.value.times(RiskTables.securityRiskWeight(kind))
        // Interbank claims weigh 20%; fixed assets 100%. Cash at the central bank weighs nothing. Intangibles are deducted
        // from capital instead of being weighted.
        val other = s.interbankLent.times(Rate.percent(20)) + s.fixedAssets
        val income = s.trailingGrossIncome()
        val operational = if (s.history.isEmpty()) s.totalAssets.times(OTHER_ASSETS_FLOOR) else income.times(rules.operationalRiskWeight)
        return RwaBreakdown(credit, securities, other, operational)
    }

    /** Buffers that apply to this bank: conservation, countercyclical and, for large banks, systemic. */
    fun combinedBuffer(s: BankSnapshot, rules: CapitalRules): Rate {
        val large = s.totalAssets >= Money.units(rules.systemicAssetsUnits)
        return rules.conservationBuffer + rules.countercyclicalBuffer + (if (large) rules.systemicBuffer else Rate.ZERO)
    }

    fun position(s: BankSnapshot, rules: CapitalRules): CapitalPosition {
        val rwa = rwa(s, rules)
        val cet1 = s.cet1Capital
        val tier1 = s.tier1Capital
        val total = s.totalCapital
        val whenNoRisk = if (cet1.raw > 0L) Rate.ONE else Rate.ZERO
        val cet1Ratio = BankMath.ratio(cet1, rwa.total, whenNoRisk)
        val tier1Ratio = BankMath.ratio(tier1, rwa.total, whenNoRisk)
        val totalRatio = BankMath.ratio(total, rwa.total, whenNoRisk)
        val exposure = s.totalAssets + s.allowance
        val leverage = BankMath.ratio(tier1, exposure, whenNoRisk)
        val buffer = combinedBuffer(s, rules)
        val requirement = rules.minCet1 + buffer
        val rung = rungFor(cet1Ratio, tier1Ratio, totalRatio, leverage, cet1, requirement, rules)
        return CapitalPosition(
            rwa, cet1, tier1, total, cet1Ratio, tier1Ratio, totalRatio, leverage, buffer, requirement, rung,
            distributionLimit(cet1Ratio, rung, buffer, rules),
        )
    }

    /** The rung the numbers alone put a bank on. The service adds hysteresis on the way back down. */
    fun rungFor(cet1Ratio: Rate, tier1Ratio: Rate, totalRatio: Rate, leverage: Rate, cet1: Money, requirement: Rate, rules: CapitalRules): SupervisoryRung = when {
        cet1.raw <= 0L || cet1Ratio < rules.criticalCet1 -> SupervisoryRung.CRITICAL
        cet1Ratio < rules.severeCet1 -> SupervisoryRung.SEVERE
        cet1Ratio < rules.minCet1 || tier1Ratio < rules.minTier1 || totalRatio < rules.minTotal || leverage < rules.minLeverage -> SupervisoryRung.RESTRICTED
        cet1Ratio < requirement -> SupervisoryRung.BUFFER
        else -> SupervisoryRung.NORMAL
    }

    /**
     * The share of profit a bank may pay out. Inside the buffer the limit steps down with each quarter of the buffer
     * that is used up: 60%, 40%, 20% and then nothing. Below the minimum it is nothing.
     */
    fun distributionLimit(cet1Ratio: Rate, rung: SupervisoryRung, buffer: Rate, rules: CapitalRules): Rate {
        if (rung.isBreach) return Rate.ZERO
        if (rung == SupervisoryRung.NORMAL || buffer.ppm <= 0L) return Rate.ONE
        val used = (cet1Ratio.ppm - rules.minCet1.ppm).coerceAtLeast(0L)
        val quarter = mulDiv(used, 4L, buffer.ppm)
        return when {
            quarter < 1L -> Rate.ZERO
            quarter < 2L -> Rate.percent(20)
            quarter < 3L -> Rate.percent(40)
            else -> Rate.percent(60)
        }
    }

    /**
     * New risk-weighted exposure a bank can take on before its CET1 ratio falls to the requirement plus [cushion].
     * Zero when it is already there.
     */
    fun exposureCapacity(s: BankSnapshot, rules: CapitalRules, cushion: Rate): Money {
        val pos = position(s, rules)
        val target = pos.cet1Requirement + cushion
        if (target.ppm <= 0L || pos.cet1.raw <= 0L) return Money.ZERO
        val allowedRwa = Money(mulDiv(pos.cet1.raw, Rate.ONE_PPM, target.ppm))
        return (allowedRwa - pos.rwa.total).coerceAtLeast(Money.ZERO)
    }

    /** Capital a bank would need to add to reach the requirement plus [cushion] at its current risk. Zero when it has enough. */
    fun capitalNeeded(s: BankSnapshot, rules: CapitalRules, cushion: Rate): Money {
        val pos = position(s, rules)
        val target = pos.rwa.total.times(pos.cet1Requirement + cushion)
        return (target - pos.cet1).coerceAtLeast(Money.ZERO)
    }

    /** The CET1 ratio after taking a loss (negative) or a gain (positive) straight to common equity. */
    fun ratioAfter(s: BankSnapshot, rules: CapitalRules, equityChange: Money): Rate {
        val rwa = rwa(s, rules).total
        return BankMath.ratio(s.cet1Capital + equityChange, rwa, if (s.cet1Capital.raw + equityChange.raw > 0L) Rate.ONE else Rate.ZERO)
    }
}
