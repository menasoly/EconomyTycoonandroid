package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.PaymentLine
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.finance.SettlementPriority
import app.many.economytycoon.finance.TransactionDraft

/**
 * Money the package wants moved, as the finance module understands it: [payments] between owners, to go through
 * the settlement engine, and [adjustments] that only reclassify a company's own books (writing off a surrendered
 * permit). The package returns these and never posts them; the runner does.
 */
class CarbonSettlement(val payments: List<SettlementIntent> = emptyList(), val adjustments: List<TransactionDraft> = emptyList()) {
    val isEmpty: Boolean get() = payments.isEmpty() && adjustments.isEmpty()

    operator fun plus(other: CarbonSettlement) = CarbonSettlement(payments + other.payments, adjustments + other.adjustments)

    /** Total cash the payments move. */
    val paymentTotal: Money get() = Money.sum(payments.map { it.total })

    companion object {
        val EMPTY = CarbonSettlement()
    }
}

/** Collects intents while a step or command runs. */
class SettlementBuilder(private val tick: Tick, private val nextSeq: () -> Long) {
    private val payments = ArrayList<SettlementIntent>()
    private val adjustments = ArrayList<TransactionDraft>()

    private fun key(tag: String) = "carbon:$tag:${tick.value}:${nextSeq()}"

    fun build() = CarbonSettlement(payments.toList(), adjustments.toList())

    fun isEmpty() = payments.isEmpty() && adjustments.isEmpty()

    /** Pays for permits or credits bought outright. The asset is carried at what was paid. */
    fun purchase(tag: String, payer: LedgerOwner, payee: LedgerOwner, amount: Money, memo: String, asset: Chart = Chart.INTANGIBLES) {
        if (amount.raw <= 0L) return
        payments += SettlementIntent.simple(key(tag), payer, payee, amount, SettlementPriority.OTHER, ReasonCode.PURCHASE, asset, Chart.REVENUE_OTHER, deferrable = false, memo = memo)
    }

    /** A tax, levy or fine owed to a government. It is expensed and, if the payer is short of cash, becomes a payable. */
    fun charge(tag: String, payer: LedgerOwner, government: LedgerOwner, amount: Money, reason: ReasonCode, memo: String) {
        if (amount.raw <= 0L) return
        payments += SettlementIntent.simple(key(tag), payer, government, amount, SettlementPriority.STATUTORY, reason, Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, deferrable = true, memo = memo)
    }

    /** A payment from a government to a company. It is not deferred: a treasury that cannot pay does not owe. */
    fun subsidy(tag: String, government: LedgerOwner, company: LedgerOwner, amount: Money, memo: String) {
        if (amount.raw <= 0L) return
        payments += SettlementIntent.simple(key(tag), government, company, amount, SettlementPriority.OTHER, ReasonCode.SUBSIDY, Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, deferrable = false, memo = memo)
    }

    /** Capital spending on a measure at a facility, paid to the contractors behind it. */
    fun capital(tag: String, company: LedgerOwner, contractors: LedgerOwner, amount: Money, memo: String) {
        if (amount.raw <= 0L) return
        payments += SettlementIntent.simple(key(tag), company, contractors, amount, SettlementPriority.OTHER, ReasonCode.PURCHASE, Chart.FIXED_ASSETS, Chart.REVENUE_OTHER, deferrable = false, memo = memo)
    }

    /** A running cost paid to the people behind it. */
    fun running(tag: String, company: LedgerOwner, contractors: LedgerOwner, amount: Money, memo: String) {
        if (amount.raw <= 0L) return
        payments += SettlementIntent.simple(key(tag), company, contractors, amount, SettlementPriority.SUPPLIERS, ReasonCode.FEE, Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, deferrable = true, memo = memo)
    }

    /** Money coming back to a company for something cancelled or rejected. */
    fun refund(tag: String, from: LedgerOwner, company: LedgerOwner, amount: Money, creditChart: Chart, memo: String) {
        if (amount.raw <= 0L) return
        payments += SettlementIntent.simple(key(tag), from, company, amount, SettlementPriority.OTHER, ReasonCode.TRANSFER, Chart.OTHER_EXPENSE, creditChart, deferrable = false, memo = memo)
    }

    /**
     * A sale of permits between companies. [basis] is what the seller's books carry them at. The seller's asset leaves at
     * its basis and the difference to the price is a gain, or a loss booked as a separate adjustment.
     */
    fun tradeOfPermits(buyer: LedgerOwner, seller: LedgerOwner, price: Money, basis: Money, memo: String) {
        if (price.raw <= 0L) return
        val lines = ArrayList<PaymentLine>()
        val kept = Money.min(price, basis.coerceAtLeast(Money.ZERO))
        if (kept.raw > 0L) lines += PaymentLine(Chart.INTANGIBLES, Chart.INTANGIBLES, kept)
        if (price > kept) lines += PaymentLine(Chart.INTANGIBLES, Chart.REVENUE_OTHER, price - kept)
        payments += SettlementIntent(key("trade"), buyer, seller, SettlementPriority.OTHER, ReasonCode.PURCHASE, lines, deferrable = false, memo = memo)
        if (basis > price) writeDown(seller, Chart.INTANGIBLES, basis - price, "loss on sale of permits")
    }

    /** Takes an asset off a company's books as an expense: a permit surrendered, a credit retired, a project written off. */
    fun writeDown(owner: LedgerOwner, asset: Chart, amount: Money, memo: String) {
        if (amount.raw <= 0L) return
        adjustments += TransactionDraft(
            key("expense"), tick, ReasonCode.ADJUSTMENT,
            listOf(Entry(AccountRef(owner, Chart.OTHER_EXPENSE), amount), Entry(AccountRef(owner, asset), -amount)),
            memo,
        )
    }

    companion object {
        fun company(id: CompanyId) = LedgerOwner.company(id)
        fun government(country: CountryId) = LedgerOwner.government(country)
        val contractors: LedgerOwner get() = LedgerOwner.households()
    }
}
