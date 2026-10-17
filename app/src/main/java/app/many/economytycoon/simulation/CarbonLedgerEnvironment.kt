package app.many.economytycoon.simulation

import app.many.economytycoon.carbon.CarbonEnvironment
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.finance.LedgerOwner

/** Lets the carbon package read the ledger, the company service and the production service; it changes nothing. */
class CarbonLedgerEnvironment(private val e: Economy) : CarbonEnvironment {
    override fun cash(company: CompanyId): Money = e.finance.ledger.cash(LedgerOwner.company(company))

    override fun isActive(company: CompanyId): Boolean = e.companies.company(company)?.isActive == true

    override fun governmentCash(country: CountryId): Money = e.finance.ledger.cash(LedgerOwner.government(country))

    override fun ownerOf(facility: FacilityId): CompanyId? = e.production.facility(facility)?.owner

    override fun buildCost(facility: FacilityId): Money {
        val f = e.production.facility(facility) ?: return Money.ZERO
        return e.content.facilityTypeOrNull(f.type)?.buildCost ?: Money.ZERO
    }
}
