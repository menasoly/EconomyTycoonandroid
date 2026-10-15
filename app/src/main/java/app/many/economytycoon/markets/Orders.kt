package app.many.economytycoon.markets

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.OrderId
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.TradeId

enum class Side { BUY, SELL }

enum class PartyKind { COMPANY, HOUSEHOLDS, GOVERNMENT, EXTERNAL }

/** Who stands behind an order or a trade. Households are aggregated per region. */
data class Party(val kind: PartyKind, val id: Long) : Comparable<Party> {
    override fun compareTo(other: Party): Int = compareValuesBy(this, other, { it.kind.ordinal }, { it.id })

    companion object {
        fun company(id: CompanyId) = Party(PartyKind.COMPANY, id.value)
        fun households(region: RegionId) = Party(PartyKind.HOUSEHOLDS, region.value)
        fun government(country: CountryId) = Party(PartyKind.GOVERNMENT, country.value)
        val EXTERNAL = Party(PartyKind.EXTERNAL, 0)
    }

    val companyId: CompanyId? get() = if (kind == PartyKind.COMPANY) CompanyId(id) else null
}

/** One market is one good traded in one region. */
data class MarketKey(val region: RegionId, val good: GoodId) : Comparable<MarketKey> {
    override fun compareTo(other: MarketKey): Int = compareValuesBy(this, other, { it.region.value }, { it.good.key })
    override fun toString() = "${good.key}@${region.value}"
}

/**
 * A standing order. [limit] null means "at any price". [source] is an opaque handle the caller uses to
 * find the stock or the destination (a facility id, typically); the market never interprets it.
 */
class Order(
    val id: OrderId,
    val key: MarketKey,
    val side: Side,
    val party: Party,
    val limit: Price?,
    val quantity: Quantity,
    val placed: Tick,
    val expires: Tick,
    val source: Long,
) {
    var remaining: Quantity = quantity
        internal set

    val filled: Quantity get() = quantity - remaining
    val isOpen: Boolean get() = remaining.raw > 0

    /** The sort key for matching: highest bid first, lowest ask first, market orders ahead of limits. */
    internal val limitRaw: Long
        get() = limit?.perUnit?.raw ?: if (side == Side.BUY) Long.MAX_VALUE else 0L

    internal fun restoreRemaining(r: Quantity) { remaining = r }
}

/** A cleared exchange between two parties at the market's uniform price. */
data class Trade(
    val id: TradeId,
    val key: MarketKey,
    val buyer: Party,
    val seller: Party,
    val quantity: Quantity,
    val price: Price,
    val tick: Tick,
    val buyOrder: OrderId?,
    val sellOrder: OrderId?,
    val buyerSource: Long,
    val sellerSource: Long,
) {
    val value: Money get() = price.valueOf(quantity)
}
