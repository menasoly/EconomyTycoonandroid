package app.many.economytycoon.urban

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.finance.SettlementPriority

/**
 * Turns urban events into settlement intents. The urban model only describes who owes whom; the settlement engine moves
 * the money. A city's own budget is a memo kept by the model: where a payment involves a company, the counterparty on the
 * ledger is the government of the city's country. Every key is unique per payment so a replay cannot pay twice.
 */
object UrbanLedger {
    private fun gov(country: CountryId) = LedgerOwner.government(country)

    /** A company pays the city for filing a petition or rezoning study. */
    fun petitionFee(company: CompanyId, country: CountryId, petition: PetitionId, amount: Money, tick: Tick): List<SettlementIntent> {
        if (!amount.isPositive) return emptyList()
        return listOf(
            SettlementIntent.simple(
                "urban:fee:${petition.value}", LedgerOwner.company(company), gov(country), amount, SettlementPriority.STATUTORY, ReasonCode.FEE,
                Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, deferrable = false, memo = "petition fee at ${tick}",
            ),
        )
    }

    /** A company pays consultants and campaign costs for a lobbying effort. The counterparty is the system account. */
    fun lobbying(company: CompanyId, petition: PetitionId, sequence: Int, amount: Money, tick: Tick): List<SettlementIntent> {
        if (!amount.isPositive) return emptyList()
        return listOf(
            SettlementIntent.simple(
                "urban:lobby:${petition.value}:${sequence}", LedgerOwner.company(company), LedgerOwner.SYSTEM, amount, SettlementPriority.OTHER,
                ReasonCode.PURCHASE, Chart.MARKETING_EXPENSE, Chart.REVENUE_SERVICES, deferrable = false, memo = "lobbying at ${tick}",
            ),
        )
    }

    /** All the payments of one month under a concession. */
    fun concessionMonth(country: CountryId, s: ConcessionSettlement, tick: Tick): List<SettlementIntent> {
        val out = ArrayList<SettlementIntent>()
        val company = LedgerOwner.company(s.concessionaire)
        val key = "urban:cx:${s.concession.value}:${tick.value}"
        if (s.capexSpend.isPositive) {
            out += SettlementIntent.simple(
                "$key:capex", company, LedgerOwner.SYSTEM, s.capexSpend, SettlementPriority.SUPPLIERS, ReasonCode.PURCHASE,
                Chart.FIXED_ASSETS, Chart.REVENUE_SALES, deferrable = false, memo = "concession construction",
            )
        }
        if (s.userCharges.isPositive) {
            out += SettlementIntent.simple(
                "$key:charges", LedgerOwner.households(0L), company, s.userCharges, SettlementPriority.OTHER, ReasonCode.SALE,
                Chart.TRANSPORT_EXPENSE, Chart.REVENUE_SERVICES, deferrable = false, memo = "tolls and fares",
            )
        }
        if (s.cityShare.isPositive) {
            out += SettlementIntent.simple(
                "$key:share", company, gov(country), s.cityShare, SettlementPriority.STATUTORY, ReasonCode.FEE,
                Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, deferrable = true, memo = "city share of user charges",
            )
        }
        val availability = s.availabilityNet
        if (availability.isPositive) {
            out += SettlementIntent.simple(
                "$key:availability", gov(country), company, availability, SettlementPriority.SUPPLIERS, ReasonCode.SALE,
                Chart.TRANSPORT_EXPENSE, Chart.REVENUE_SERVICES, deferrable = true, memo = "availability payment",
            )
        }
        if (s.guarantee.isPositive) {
            out += SettlementIntent.simple(
                "$key:guarantee", gov(country), company, s.guarantee, SettlementPriority.SUPPLIERS, ReasonCode.SUBSIDY,
                Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, deferrable = true, memo = "traffic guarantee",
            )
        }
        return out
    }

    /** Compensation the city owes a concessionaire when it ends the concession early. */
    fun termination(country: CountryId, concession: ConcessionId, company: CompanyId, amount: Money, tick: Tick): List<SettlementIntent> {
        if (!amount.isPositive) return emptyList()
        return listOf(
            SettlementIntent.simple(
                "urban:cx:${concession.value}:end:${tick.value}", gov(country), LedgerOwner.company(company), amount, SettlementPriority.SECURED_DEBT,
                ReasonCode.ADJUSTMENT, Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, deferrable = true, memo = "concession terminated",
            ),
        )
    }

    /** Payment to a company that renegotiated a lower toll. */
    fun renegotiation(country: CountryId, concession: ConcessionId, company: CompanyId, amount: Money, tick: Tick): List<SettlementIntent> =
        if (!amount.isPositive) emptyList() else listOf(
            SettlementIntent.simple(
                "urban:cx:${concession.value}:renegotiate:${tick.value}", gov(country), LedgerOwner.company(company), amount, SettlementPriority.SUPPLIERS,
                ReasonCode.ADJUSTMENT, Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, deferrable = true, memo = "toll renegotiated",
            ),
        )
}
