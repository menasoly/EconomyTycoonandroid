package app.many.economytycoon.finance

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick

/** Tax rules for one jurisdiction. Rates come from government policy data, never from code. */
data class TaxPolicy(
    val corporateRate: Rate = Rate.percent(20),
    val salesRate: Rate = Rate.percent(8),
    val payrollRate: Rate = Rate.percent(10),
    val propertyRatePerYear: Rate = Rate.basisPoints(80),
    val carbonPerUnitEmission: Money = Money.ZERO,
    /** Share of a prior loss that may offset later profit in one year. */
    val lossOffsetCap: Rate = Rate.percent(80),
    /** Years a loss can be carried, in ticks. */
    val lossCarryTicks: Long = 360L * 5,
    val minimumProfitTaxed: Money = Money.ZERO,
    /** Small companies below this profit pay a reduced rate. */
    val smallProfitThreshold: Money = Money.units(100_000),
    val smallProfitRate: Rate = Rate.percent(12),
) {
    fun salesTax(net: Money): Money = net.times(salesRate)
    fun payrollTax(gross: Money): Money = gross.times(payrollRate)
}

/** A loss that can offset future profit until it expires. */
data class LossPool(val amount: Money, val incurred: Tick)

/** Per-company tax state: loss carryforwards and how much has been assessed. */
class TaxAccount(val owner: LedgerOwner) {
    private val losses = ArrayList<LossPool>()
    var lifetimeTaxPaid: Money = Money.ZERO
        private set
    var lastAssessedProfit: Money = Money.ZERO
        private set

    fun carriedLosses(): List<LossPool> = losses.toList()
    fun totalCarriedLoss(): Money = Money.sum(losses.map { it.amount })

    /**
     * Assesses tax on [profitBeforeTax] for a period ending at [tick]. Losses are carried forward and
     * offset later profit oldest-first, subject to the policy cap. Returns the tax due.
     */
    fun assess(tick: Tick, profitBeforeTax: Money, policy: TaxPolicy): Money {
        expire(tick, policy)
        lastAssessedProfit = profitBeforeTax
        if (profitBeforeTax.raw <= 0) {
            if (profitBeforeTax.raw < 0) losses.add(LossPool(-profitBeforeTax, tick))
            return Money.ZERO
        }
        val cap = profitBeforeTax.times(policy.lossOffsetCap)
        var room = cap
        var offset = Money.ZERO
        val iter = losses.listIterator()
        while (iter.hasNext() && room.isPositive) {
            val pool = iter.next()
            val use = Money.min(pool.amount, room)
            offset += use
            room -= use
            val left = pool.amount - use
            if (left.isPositive) iter.set(LossPool(left, pool.incurred)) else iter.remove()
        }
        var taxable = profitBeforeTax - offset
        if (taxable < policy.minimumProfitTaxed) taxable = policy.minimumProfitTaxed
        val rate = if (taxable <= policy.smallProfitThreshold) policy.smallProfitRate else policy.corporateRate
        // Progressive: the part below the threshold is taxed at the small rate.
        val tax = if (taxable <= policy.smallProfitThreshold) taxable.times(rate)
        else policy.smallProfitThreshold.times(policy.smallProfitRate) + (taxable - policy.smallProfitThreshold).times(policy.corporateRate)
        return tax
    }

    fun recordPaid(amount: Money) { lifetimeTaxPaid += amount }

    private fun expire(tick: Tick, policy: TaxPolicy) {
        losses.removeAll { tick.value - it.incurred.value > policy.lossCarryTicks }
    }

    internal fun restore(pools: List<LossPool>, paid: Money, lastProfit: Money) {
        losses.clear(); losses.addAll(pools); lifetimeTaxPaid = paid; lastAssessedProfit = lastProfit
    }
}

object TaxIntents {
    /** Corporate income tax payment to the government. Accrued on the books at assessment. */
    fun corporate(key: String, company: LedgerOwner, government: LedgerOwner, amount: Money): SettlementIntent =
        SettlementIntent.simple(key, company, government, amount, SettlementPriority.STATUTORY, ReasonCode.TAX,
            Chart.TAX_EXPENSE, Chart.REVENUE_OTHER, deferrable = true, memo = "corporate income tax")

    fun payroll(key: String, company: LedgerOwner, government: LedgerOwner, amount: Money): SettlementIntent =
        SettlementIntent.simple(key, company, government, amount, SettlementPriority.STATUTORY, ReasonCode.TAX,
            Chart.WAGES_EXPENSE, Chart.REVENUE_OTHER, deferrable = true, memo = "payroll tax")

    fun sales(key: String, company: LedgerOwner, government: LedgerOwner, amount: Money): SettlementIntent =
        SettlementIntent.simple(key, company, government, amount, SettlementPriority.STATUTORY, ReasonCode.TAX,
            Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, deferrable = true, memo = "sales tax")

    fun property(key: String, company: LedgerOwner, government: LedgerOwner, amount: Money): SettlementIntent =
        SettlementIntent.simple(key, company, government, amount, SettlementPriority.STATUTORY, ReasonCode.TAX,
            Chart.RENT_EXPENSE, Chart.REVENUE_OTHER, deferrable = true, memo = "property tax")
}
