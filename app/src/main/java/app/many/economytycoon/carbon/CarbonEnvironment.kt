package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money

/**
 * The read-only view of the rest of the game that the carbon package needs. The simulation supplies an implementation
 * that reads the ledger and the company and production services; tests supply a fake. Nothing here changes anything.
 */
interface CarbonEnvironment {
    /** Cash the company has right now. */
    fun cash(company: CompanyId): Money

    /** True while the company is trading (not dissolved, acquired or being wound up). */
    fun isActive(company: CompanyId): Boolean

    /** Cash in the treasury of [country]. */
    fun governmentCash(country: CountryId): Money

    /** The company that owns an operating facility, or null if there is no such facility. */
    fun ownerOf(facility: FacilityId): CompanyId?

    /** What it cost to build the facility; the size of an abatement project is a share of this. */
    fun buildCost(facility: FacilityId): Money
}

/** An environment where everyone is active, rich and honest about ownership; for tests and tools. */
class FixedEnvironment(
    private val cashOf: (CompanyId) -> Money = { Money.units(10_000_000) },
    private val treasury: (CountryId) -> Money = { Money.units(10_000_000) },
    private val owners: (FacilityId) -> CompanyId? = { null },
    private val active: (CompanyId) -> Boolean = { true },
    private val buildCosts: (FacilityId) -> Money = { Money.units(2_000_000) },
) : CarbonEnvironment {
    override fun cash(company: CompanyId) = cashOf(company)
    override fun isActive(company: CompanyId) = active(company)
    override fun governmentCash(country: CountryId) = treasury(country)
    override fun ownerOf(facility: FacilityId) = owners(facility)
    override fun buildCost(facility: FacilityId) = buildCosts(facility)
}

/**
 * Tracks the cash a caller has promised away during one call, so several payments made in the same call cannot each be
 * checked against the same money.
 */
class CashBook(private val env: CarbonEnvironment) {
    private val spent = java.util.TreeMap<CompanyId, Long>()
    private val govSpent = java.util.TreeMap<CountryId, Long>()

    fun available(company: CompanyId): Money = (env.cash(company) - Money(spent[company] ?: 0L)).coerceAtLeast(Money.ZERO)

    fun governmentAvailable(country: CountryId): Money = (env.governmentCash(country) - Money(govSpent[country] ?: 0L)).coerceAtLeast(Money.ZERO)

    /** Reserves [amount]; false and no change if the company cannot cover it. */
    fun spend(company: CompanyId, amount: Money): Boolean {
        if (amount.raw < 0L || available(company) < amount) return false
        spent.merge(company, amount.raw, Long::plus)
        return true
    }

    fun governmentSpend(country: CountryId, amount: Money): Boolean {
        if (amount.raw < 0L || governmentAvailable(country) < amount) return false
        govSpent.merge(country, amount.raw, Long::plus)
        return true
    }

    /** Gives back money reserved for a payment that is not going ahead. */
    fun refund(company: CompanyId, amount: Money) { spent.merge(company, -amount.raw, Long::plus) }
}

/**
 * Lets other parts of the game add to the abatement subsidy a government pays, for instance a grant programme that
 * rewards clean investment. The sum of the hook and the regime's own share is still limited by the green fund.
 */
fun interface GreenSubsidyHook {
    fun bonus(company: CompanyId, country: CountryId, kind: AbatementKind, capex: Money): Money

    companion object {
        val None = GreenSubsidyHook { _, _, _, _ -> Money.ZERO }
    }
}
