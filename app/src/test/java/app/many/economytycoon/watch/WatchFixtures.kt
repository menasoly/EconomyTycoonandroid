package app.many.economytycoon.watch

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId

class FakeFacts(
    override var day: Long = 100L,
    override var cash: Money = Money.units(10_000),
    override var monthlyProfit: Money = Money.units(500),
    override var idleFacilities: Int = 0,
    var prices: MutableMap<Pair<Long, String>, Money> = mutableMapOf(),
    var stockMoves: MutableMap<Long, Long> = mutableMapOf(),
    var loanIn: Long? = null,
    var deliveryIn: Long? = null,
) : WatchFacts {
    override fun price(region: RegionId, good: GoodId): Money? = prices[region.value to good.key]
    override fun stockMovePpm(issuer: CompanyId): Long? = stockMoves[issuer.value]
    override fun daysToLoanPayment(): Long? = loanIn
    override fun daysToDelivery(): Long? = deliveryIn
}

object FakeNames : WatchNames {
    override fun good(id: GoodId) = "Good ${id.key}"
    override fun region(id: RegionId) = "Region ${id.value}"
    override fun company(id: CompanyId) = "Company ${id.value}"
}

val plainMoney: (Money) -> String = { it.wholeUnits().toString() }
