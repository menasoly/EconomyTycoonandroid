package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate

/** The liquidity standing of a bank: the thirty-day coverage ratio, stable funding and the reserve requirement. */
data class LiquidityPosition(
    val level1: Money,
    val level2: Money,
    /** The liquid buffer after haircuts and the cap on level 2 assets. */
    val hqla: Money,
    val outflows: Money,
    val inflows: Money,
    val netOutflows: Money,
    val lcr: Rate,
    val availableStableFunding: Money,
    val requiredStableFunding: Money,
    val nsfr: Rate,
    val requiredReserves: Money,
    /** Reserves wanted: the requirement plus a working margin. */
    val desiredReserves: Money,
    val reserves: Money,
) {
    /** Reserves beyond the working target; negative when short. */
    val excessReserves: Money get() = reserves - desiredReserves

    /** Reserves short of the legal requirement, zero when met. */
    val reserveShortfall: Money get() = (requiredReserves - reserves).coerceAtLeast(Money.ZERO)

    val meetsReserve: Boolean get() = reserves >= requiredReserves
}

/** Liquidity coverage, net stable funding and reserve requirements. */
object LiquidityEngine {
    /** Reported when there are no net outflows at all: the ratio is not meaningful, only very high. */
    val UNBOUNDED = Rate.percent(10_000)

    fun requiredReserves(s: BankSnapshot, rules: LiquidityRules): Money {
        var req = Money.ZERO
        for ((key, pool) in s.deposits) {
            val ratio = if (key.product == DepositProduct.DEMAND) rules.reserveRatioDemand else rules.reserveRatioOther
            req += pool.balance.times(ratio)
        }
        return req
    }

    fun position(s: BankSnapshot, rules: LiquidityRules, insured: (Depositor) -> Rate): LiquidityPosition {
        val level1 = s.reserves.coerceAtLeast(Money.ZERO) + s.securityOf(SecurityKind.GOVERNMENT_SHORT) + s.securityOf(SecurityKind.GOVERNMENT_LONG)
        val level2Gross = s.securityOf(SecurityKind.CORPORATE_BONDS)
        val level2Haircut = level2Gross.times(Rate.ONE - rules.level2Haircut)
        // Level 2 may be at most [cap] of the buffer, which means at most cap/(1-cap) of level 1.
        val maxLevel2 = Money(java.math.BigInteger.valueOf(level1.raw).multiply(java.math.BigInteger.valueOf(rules.level2Cap.ppm))
            .divide(java.math.BigInteger.valueOf((Rate.ONE_PPM - rules.level2Cap.ppm).coerceAtLeast(1L))).toLong())
        val level2 = Money.min(level2Haircut, maxLevel2)
        val hqla = level1 + level2

        var outflows = Money.ZERO
        for ((key, pool) in s.deposits) {
            val share = insured(key.depositor)
            val insuredBalance = pool.balance.times(share)
            val uninsuredBalance = pool.balance - insuredBalance
            outflows += insuredBalance.times(rules.runoff(key.depositor, key.product, true))
            outflows += uninsuredBalance.times(rules.runoff(key.depositor, key.product, false))
        }
        outflows += s.wholesale.times(rules.wholesaleMaturingShare) + s.interbankBorrowed + s.centralBankMarginal
        val rawInflows = s.loansPerforming().times(rules.loanInflowShare).times(rules.inflowFactor) + s.interbankLent
        val cap = outflows.times(rules.inflowCap)
        val inflows = Money.min(rawInflows, cap)
        val net = outflows - inflows
        val lcr = if (net.raw <= 0L) UNBOUNDED else BankMath.ratio(hqla, net).coerceIn(Rate.ZERO, UNBOUNDED)

        val asf = availableStable(s, rules)
        val rsf = requiredStable(s)
        val nsfr = if (rsf.raw <= 0L) UNBOUNDED else BankMath.ratio(asf, rsf).coerceIn(Rate.ZERO, UNBOUNDED)

        val required = requiredReserves(s, rules)
        val desired = required + required.times(rules.reserveMargin)
        return LiquidityPosition(level1, level2, hqla, outflows, inflows, net, lcr, asf, rsf, nsfr, required, desired, s.reserves)
    }

    private fun availableStable(s: BankSnapshot, rules: LiquidityRules): Money {
        var asf = s.totalCapital.coerceAtLeast(Money.ZERO)
        for ((key, pool) in s.deposits) asf += pool.balance.times(rules.stableFactor(key.depositor, key.product))
        // Wholesale funding is assumed to be half long term.
        asf += s.wholesale.times(Rate.percent(50))
        return asf
    }

    private fun requiredStable(s: BankSnapshot): Money {
        var rsf = Money.ZERO
        for ((key, pool) in s.loans) {
            val factor = when {
                key.stage == LoanStage.IMPAIRED -> Rate.percent(100)
                key.sector == LoanSector.MORTGAGE -> Rate.percent(65)
                key.sector == LoanSector.TRADE_FINANCE -> Rate.percent(50)
                else -> Rate.percent(85)
            }
            rsf += pool.principal.times(factor)
        }
        rsf += s.securityOf(SecurityKind.GOVERNMENT_SHORT).times(Rate.percent(5))
        rsf += s.securityOf(SecurityKind.GOVERNMENT_LONG).times(Rate.percent(5))
        rsf += s.securityOf(SecurityKind.CORPORATE_BONDS).times(Rate.percent(50))
        rsf += s.interbankLent.times(Rate.percent(10))
        rsf += s.fixedAssets
        return rsf
    }

    /** Shortfall of the buffer against a target coverage ratio, in money. Zero when the target is met. */
    fun bufferShortfall(p: LiquidityPosition, target: Rate): Money {
        val needed = p.netOutflows.times(target)
        return (needed - p.hqla).coerceAtLeast(Money.ZERO)
    }
}
