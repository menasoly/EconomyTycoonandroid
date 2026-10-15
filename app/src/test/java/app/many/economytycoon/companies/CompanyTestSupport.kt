package app.many.economytycoon.companies

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.FinanceService
import app.many.economytycoon.finance.Holder
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.OwnerKind
import app.many.economytycoon.finance.TaxPolicy

class CompanyRig {
    val ids = IdAllocators()
    val finance = FinanceService(ids)
    val gov = LedgerOwner(OwnerKind.GOVERNMENT, 1)
    val companies = CompanyService(ids, finance)
    val insolvency = InsolvencyService(finance, companies)
    val acquisitions = AcquisitionService(finance, companies)
    val country = CountryId(1)
    var tick = 0L

    init {
        finance.setTaxPolicy(gov, TaxPolicy())
        finance.mint(Tick(0), finance.bank, Money.units(100_000_000), "bank")
        finance.mint(Tick(0), finance.households, Money.units(50_000_000), "households")
    }

    fun found(name: String, kind: CompanyKind = CompanyKind.AI, capital: Money = Money.units(500_000), founder: Holder = Holder("founder", nextFounder++)): Company =
        companies.found(name, kind, country, null, founder, capital, gov, Tick(tick)).getOrThrow()

    private var nextFounder = 100L

    fun hash(): String = CanonicalHasher().also { finance.hashInto(it); companies.hashInto(it); insolvency.hashInto(it); acquisitions.hashInto(it) }.finish().toHex()
}
