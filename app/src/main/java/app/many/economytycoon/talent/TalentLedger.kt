package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.finance.SettlementPriority

/**
 * Builds the settlement intents this module hands back. Nothing here moves money: the caller posts the intents
 * through the settlement engine. Keys are unique per payment, so posting the same intent twice is harmless.
 */
object TalentLedger {
    private fun key(kind: String, id: Long, tick: Tick, extra: Long = 0L) = "talent:$kind:$id:${tick.value}:$extra"

    private fun positive(m: Money) = m.raw > 0L

    /** A company pays people for work done outside its payroll (instructors, builders, trainers). */
    fun spendToHouseholds(kind: String, id: Long, company: CompanyId, region: RegionId, amount: Money, tick: Tick, memo: String, chart: Chart = Chart.OTHER_EXPENSE): SettlementIntent? =
        if (!positive(amount)) null else SettlementIntent.simple(
            key(kind, id, tick), LedgerOwner.company(company), LedgerOwner.households(region.value), amount, SettlementPriority.SUPPLIERS,
            ReasonCode.PURCHASE, chart, Chart.REVENUE_OTHER, deferrable = true, memo = memo,
        )

    /** One company pays another (tuition to a provider, a fee to a rival for buying out a covenant). */
    fun companyToCompany(kind: String, id: Long, payer: CompanyId, payee: CompanyId, amount: Money, tick: Tick, memo: String, extra: Long = 0L): SettlementIntent? =
        if (!positive(amount) || payer == payee) null else SettlementIntent.simple(
            key(kind, id, tick, extra), LedgerOwner.company(payer), LedgerOwner.company(payee), amount, SettlementPriority.SUPPLIERS,
            ReasonCode.FEE, Chart.OTHER_EXPENSE, Chart.REVENUE_SERVICES, deferrable = true, memo = memo,
        )

    /** A company pays an outside service (a search firm); the payee is the system account. */
    fun fee(kind: String, id: Long, company: CompanyId, amount: Money, tick: Tick, memo: String, extra: Long = 0L): SettlementIntent? =
        if (!positive(amount)) null else SettlementIntent.simple(
            key(kind, id, tick, extra), LedgerOwner.company(company), LedgerOwner.SYSTEM, amount, SettlementPriority.OTHER,
            ReasonCode.FEE, Chart.OTHER_EXPENSE, Chart.REVENUE_SERVICES, deferrable = false, memo = memo,
        )

    /** Households pay a company: tuition, or a trainee's repayment under a retention clause. */
    fun householdsToCompany(kind: String, id: Long, region: RegionId, company: CompanyId, amount: Money, tick: Tick, memo: String, extra: Long = 0L): SettlementIntent? =
        if (!positive(amount)) null else SettlementIntent.simple(
            key(kind, id, tick, extra), LedgerOwner.households(region.value), LedgerOwner.company(company), amount, SettlementPriority.OTHER,
            ReasonCode.FEE, Chart.OTHER_EXPENSE, Chart.REVENUE_SERVICES, deferrable = false, memo = memo,
        )

    /** A government pays for its public schools and colleges. */
    fun publicFunding(id: Long, country: CountryId, region: RegionId, amount: Money, tick: Tick): SettlementIntent? =
        if (!positive(amount)) null else SettlementIntent.simple(
            key("public", id, tick), LedgerOwner.government(country), LedgerOwner.households(region.value), amount, SettlementPriority.STATUTORY,
            ReasonCode.STIPEND, Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, deferrable = true, memo = "public education",
        )

    /** A company pays a wage-like cost to its own people outside normal payroll (retention bonus, signing bonus). */
    fun bonus(kind: String, id: Long, company: CompanyId, region: RegionId, amount: Money, tick: Tick, memo: String, extra: Long = 0L): SettlementIntent? =
        if (!positive(amount)) null else SettlementIntent.simple(
            key(kind, id, tick, extra), LedgerOwner.company(company), LedgerOwner.households(region.value), amount, SettlementPriority.WAGES,
            ReasonCode.WAGES, Chart.WAGES_EXPENSE, Chart.REVENUE_OTHER, deferrable = true, memo = memo,
        )
}
