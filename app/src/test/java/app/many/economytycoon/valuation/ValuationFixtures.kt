package app.many.economytycoon.valuation

import app.many.economytycoon.core.Money

object ValuationFixtures {
    fun input(
        revenue: Long = 1_200_000, ebitda: Long = 300_000, net: Long = 180_000, fcf: Long = 150_000,
        cash: Long = 100_000, debt: Long = 200_000, receivables: Long = 50_000, inventory: Long = 80_000,
        fixed: Long = 400_000, intangibles: Long = 20_000, investments: Long = 0, loans: Long = 0,
        liabilities: Long = 260_000, book: Long = 390_000, shares: Long = 1_000, price: Long? = null, history: Long = 90,
    ) = ValuationInput(
        Money.units(revenue), Money.units(ebitda), Money.units(net), Money.units(fcf), Money.units(cash), Money.units(debt),
        Money.units(receivables), Money.units(inventory), Money.units(fixed), Money.units(intangibles), Money.units(investments),
        Money.units(loans), Money.units(liabilities), Money.units(book), shares, price?.let { Money.units(it) }, history,
    )

    val healthy = input()
    val lossMaker = input(ebitda = -50_000, net = -80_000, fcf = -60_000, book = 100_000)
    val noSales = input(revenue = 0, ebitda = 0, net = 0, fcf = 0)
}
