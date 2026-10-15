package app.many.economytycoon.session

import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.markets.DepthLevel
import app.many.economytycoon.markets.MarketKey
import app.many.economytycoon.markets.Party
import app.many.economytycoon.markets.Side
import app.many.economytycoon.simulation.Economy

/** One good on the market board, averaged over regions that have traded it. */
data class GoodQuote(
    val good: GoodId,
    val name: String,
    val category: GoodCategory,
    val price: Money,
    val reference: Money,
    val changePpm: Long,
    val deviationPpm: Long,
    val volume: Quantity,
    val unmet: Quantity,
    val unsold: Quantity,
)

data class RegionQuote(val region: RegionId, val regionName: String, val price: Money, val bid: Money?, val ask: Money?, val volume: Quantity, val changePpm: Long)

data class PricePoint(val ageTicks: Int, val price: Money, val volume: Quantity)

data class OrderRow(
    val id: Long,
    val good: GoodId,
    val goodName: String,
    val region: String,
    val side: Side,
    val limit: Money?,
    val remaining: Quantity,
    val quantity: Quantity,
    val expiresIn: Long,
)

data class TradeRow(val tick: Long, val good: String, val region: String, val quantity: Quantity, val price: Money, val buyer: String, val seller: String)

/** Market views: the price board, per-region quotes, order books, price history and a company's orders. */
class MarketReports(private val e: Economy) {

    fun board(category: GoodCategory? = null): List<GoodQuote> {
        val byGood = e.markets.states().groupBy { it.key.good }
        return byGood.mapNotNull { (good, states) ->
            val def = e.content.good(good)
            if (!def.tradable || (category != null && def.category != category)) return@mapNotNull null
            val avg = e.markets.averagePrice(good).perUnit
            val change = states.sumOf { it.changePpm } / states.size
            val dev = states.sumOf { it.deviationPpm } / states.size
            GoodQuote(
                good = good, name = def.name, category = def.category, price = avg, reference = def.referencePrice.perUnit,
                changePpm = change, deviationPpm = dev,
                volume = Quantity(states.sumOf { it.lastVolume.raw }),
                unmet = Quantity(states.sumOf { it.unmetDemand.raw }),
                unsold = Quantity(states.sumOf { it.unsoldSupply.raw }),
            )
        }.sortedBy { it.name }
    }

    fun regions(good: GoodId): List<RegionQuote> =
        e.world.regions().sortedBy { it.id }.mapNotNull { r ->
            val s = e.markets.state(MarketKey(r.id, good)) ?: return@mapNotNull null
            val q = e.markets.quote(MarketKey(r.id, good))
            RegionQuote(r.id, r.name, s.last.perUnit, q.bid?.perUnit, q.ask?.perUnit, s.lastVolume, s.changePpm)
        }

    fun depth(region: RegionId, good: GoodId, levels: Int = 6): List<DepthLevel> = e.markets.depth(MarketKey(region, good), levels)

    /** Closing prices, newest first, up to the history the market keeps. */
    fun history(region: RegionId, good: GoodId): List<PricePoint> {
        val s = e.markets.state(MarketKey(region, good)) ?: return emptyList()
        val closes = s.history.closesOldestFirst(); val volumes = s.history.volumesOldestFirst()
        return List(closes.size) { i ->
            val age = closes.size - 1 - i
            PricePoint(age, Money(closes[i]), Quantity(volumes[i]))
        }.reversed()
    }

    /** Average of the regional histories, aligned by age, oldest first, for the overview charts. */
    fun averageHistory(good: GoodId): List<Money> {
        val series = e.world.regions().mapNotNull { r -> e.markets.state(MarketKey(r.id, good))?.history?.closesOldestFirst() }
        if (series.isEmpty()) return emptyList()
        val n = series.maxOf { it.size }
        return List(n) { i ->
            val age = n - 1 - i
            val values = series.mapNotNull { s -> if (age < s.size) s[s.size - 1 - age] else null }
            Money(values.sum() / values.size)
        }
    }

    fun ordersOf(company: CompanyId): List<OrderRow> =
        e.markets.openOrders(Party.company(company)).sortedBy { it.id.value }.map { o ->
            OrderRow(
                id = o.id.value, good = o.key.good, goodName = e.content.good(o.key.good).name, region = e.world.region(o.key.region).name,
                side = o.side, limit = o.limit?.perUnit, remaining = o.remaining, quantity = o.quantity,
                expiresIn = (o.expires.value - e.tick.value).coerceAtLeast(0L),
            )
        }

    fun recentTrades(limit: Int = 30, good: GoodId? = null): List<TradeRow> =
        e.markets.recentTrades(400).filter { good == null || it.key.good == good }.takeLast(limit).reversed().map { t ->
            TradeRow(t.tick.value, e.content.good(t.key.good).name, e.world.region(t.key.region).name, t.quantity, t.price.perUnit, partyName(t.buyer), partyName(t.seller))
        }

    fun partyName(p: Party): String = when (p.kind) {
        app.many.economytycoon.markets.PartyKind.COMPANY -> e.companies.company(CompanyId(p.id))?.name ?: "Company ${p.id}"
        app.many.economytycoon.markets.PartyKind.HOUSEHOLDS -> "Households of ${regionNameOrId(p.id)}"
        app.many.economytycoon.markets.PartyKind.GOVERNMENT -> "Government"
        app.many.economytycoon.markets.PartyKind.EXTERNAL -> "Abroad"
    }

    private fun regionNameOrId(id: Long): String = e.world.regions().firstOrNull { it.id.value == id }?.name ?: "region $id"

    /** Price level of the consumer and producer baskets. */
    fun indices(): IndexReading? {
        val s = e.markets.indexLog.latest() ?: return null
        return IndexReading(s.tick, s.cpi, s.ppi, e.markets.indexLog.inflationPpm(e.tick))
    }

    fun lastPrice(good: GoodId): Price = e.markets.averagePrice(good)
}

data class IndexReading(val tick: Long, val cpi: Long, val ppi: Long, val inflationPpm: Long?)
