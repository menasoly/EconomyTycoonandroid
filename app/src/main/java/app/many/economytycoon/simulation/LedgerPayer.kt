package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.AssetClasses
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.finance.FinanceService
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.TransactionDraft
import app.many.economytycoon.logistics.LogisticsMemo
import app.many.economytycoon.production.Payer

/**
 * Books the immediate charges that production and logistics commands make. Capital items become fixed
 * assets; running costs become expenses. The money lands with households, which stand in for the
 * workers and suppliers behind construction, repairs and haulage.
 */
class LedgerPayer(
    private val finance: FinanceService,
    private val governmentOf: (CountryId) -> LedgerOwner,
    private val clock: () -> Tick,
) : Payer {
    private var seq = 0L
    private val capital = java.util.TreeMap<CompanyId, Long>()

    /** Capital spending since the last call, per company, for the investment line of GDP. */
    fun drainCapital(): Map<CompanyId, Money> {
        val out = capital.mapValues { Money(it.value) }
        capital.clear()
        return out
    }

    override fun charge(owner: CompanyId, amount: Money, memo: String): Boolean {
        val ok = post(owner, amount, memo)
        val isCapital = memo.startsWith("construction") || memo.startsWith("line for") || memo.startsWith("machine") || memo.startsWith(LogisticsMemo.VEHICLE)
        if (ok && isCapital && amount.raw > 0L) capital.merge(owner, amount.raw, Long::plus)
        return ok
    }

    private fun post(owner: CompanyId, amount: Money, memo: String): Boolean {
        if (amount.raw <= 0L) return true
        val payer = LedgerOwner.company(owner)
        val tick = clock()
        val key = "pay:${tick.value}:${seq++}"
        val ledger = finance.ledger
        if (ledger.cash(payer) < amount) return false
        val households = finance.households
        return when {
            memo.startsWith("construction") -> finance.purchaseAsset(key, tick, payer, households, AssetClasses.BUILDING, memo, amount).isOk
            memo.startsWith("line for") -> finance.purchaseAsset(key, tick, payer, households, AssetClasses.MACHINERY, memo, amount).isOk
            memo.startsWith("machine") -> finance.purchaseAsset(key, tick, payer, households, AssetClasses.MACHINERY, memo, amount).isOk
            memo.startsWith(LogisticsMemo.VEHICLE) -> finance.purchaseAsset(key, tick, payer, households, AssetClasses.VEHICLES, memo, amount).isOk
            memo.startsWith("land") -> holding(key, tick, payer, households, amount, memo)
            memo.startsWith(LogisticsMemo.TARIFF) -> {
                val country = memo.substringAfter(':', "").toLongOrNull()?.let { CountryId(it) }
                val payee = country?.let(governmentOf) ?: households
                expense(key, tick, payer, payee, amount, Chart.OTHER_EXPENSE, ReasonCode.TAX, memo)
            }
            memo.startsWith("maintenance") || memo == LogisticsMemo.REPAIR -> expense(key, tick, payer, households, amount, Chart.MAINTENANCE_EXPENSE, ReasonCode.FEE, memo)
            memo.startsWith("waste") -> expense(key, tick, payer, households, amount, Chart.WASTE_EXPENSE, ReasonCode.FEE, memo)
            memo == LogisticsMemo.RENT -> expense(key, tick, payer, households, amount, Chart.RENT_EXPENSE, ReasonCode.RENT, memo)
            memo == LogisticsMemo.FREIGHT || memo == LogisticsMemo.FLEET_RUNNING -> expense(key, tick, payer, households, amount, Chart.TRANSPORT_EXPENSE, ReasonCode.TRANSPORT, memo)
            else -> expense(key, tick, payer, households, amount, Chart.OTHER_EXPENSE, ReasonCode.FEE, memo)
        }
    }

    /** Land is kept at what was paid for it, under investments, until it is sold. */
    private fun holding(key: String, tick: Tick, payer: LedgerOwner, payee: LedgerOwner, amount: Money, memo: String): Boolean =
        finance.ledger.post(
            TransactionDraft(
                key, tick, ReasonCode.PURCHASE,
                listOf(
                    Entry(AccountRef(payer, Chart.INVESTMENTS), amount), Entry(AccountRef(payer, Chart.CASH), -amount),
                    Entry(AccountRef(payee, Chart.CASH), amount), Entry(AccountRef(payee, Chart.REVENUE_OTHER), -amount),
                ),
                memo,
            ),
        ).isOk

    private fun expense(key: String, tick: Tick, payer: LedgerOwner, payee: LedgerOwner, amount: Money, chart: Chart, reason: ReasonCode, memo: String): Boolean =
        finance.ledger.post(
            TransactionDraft(
                key, tick, reason,
                listOf(
                    Entry(AccountRef(payer, chart), amount), Entry(AccountRef(payer, Chart.CASH), -amount),
                    Entry(AccountRef(payee, Chart.CASH), amount), Entry(AccountRef(payee, Chart.REVENUE_OTHER), -amount),
                ),
                memo,
            ),
        ) is Outcome.Ok

    /** Pays a company from the household pool, for refunds and resale proceeds. Pays what the pool can cover. */
    fun credit(owner: CompanyId, amount: Money, memo: String): Money {
        if (amount.raw <= 0L) return Money.ZERO
        val households = finance.households
        val pay = Money.min(amount, finance.ledger.cash(households).coerceAtLeast(Money.ZERO))
        if (pay.raw <= 0L) return Money.ZERO
        val payee = LedgerOwner.company(owner)
        val tick = clock()
        val ok = finance.ledger.post(
            TransactionDraft(
                "credit:${tick.value}:${seq++}", tick, ReasonCode.TRANSFER,
                listOf(
                    Entry(AccountRef(households, Chart.OTHER_EXPENSE), pay), Entry(AccountRef(households, Chart.CASH), -pay),
                    Entry(AccountRef(payee, Chart.CASH), pay), Entry(AccountRef(payee, Chart.REVENUE_OTHER), -pay),
                ),
                memo,
            ),
        ) is Outcome.Ok
        return if (ok) pay else Money.ZERO
    }
}

/** Technology licence fees paid between companies. */
class LedgerLicenses(private val finance: FinanceService, private val clock: () -> Tick) : app.many.economytycoon.research.LicensePayer {
    private var seq = 0L

    override fun pay(from: CompanyId, to: CompanyId, amount: Money, memo: String): Boolean {
        if (amount.raw <= 0L) return true
        val payer = LedgerOwner.company(from)
        val payee = LedgerOwner.company(to)
        if (finance.ledger.cash(payer) < amount) return false
        val tick = clock()
        return finance.ledger.post(
            TransactionDraft(
                "license:${tick.value}:${seq++}", tick, ReasonCode.FEE,
                listOf(
                    Entry(AccountRef(payer, Chart.RESEARCH_EXPENSE), amount), Entry(AccountRef(payer, Chart.CASH), -amount),
                    Entry(AccountRef(payee, Chart.CASH), amount), Entry(AccountRef(payee, Chart.REVENUE_SERVICES), -amount),
                ),
                memo,
            ),
        ) is Outcome.Ok
    }
}
