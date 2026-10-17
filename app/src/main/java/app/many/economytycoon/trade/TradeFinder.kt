package app.many.economytycoon.trade

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.markets.MarketKey
import app.many.economytycoon.simulation.Economy

/** Looks for goods that sell for more in one region than they cost in another, after moving them. */
class TradeFinder(private val e: Economy) {
    private data class Side(val region: RegionId, val name: String, val price: Money, val city: CityId)

    /**
     * Best legs for every tradable good, [perGood] at most, for a shipment of [quantity]. Only legs that make a profit
     * after freight and tariff are returned.
     */
    fun scan(quantity: Quantity = DEFAULT_QUANTITY, perGood: Int = 3): List<TradeLeg> {
        val legs = ArrayList<TradeLeg>()
        for (def in e.content.allGoods) {
            if (!def.tradable) continue
            legs += forGood(def.id, quantity).filter { it.isProfitable }.take(perGood)
        }
        return TradeMath.sorted(legs, TradeSort.PROFIT)
    }

    /** Every leg worth pricing for one good, best first. Profitable or not; callers filter. */
    fun forGood(good: GoodId, quantity: Quantity = DEFAULT_QUANTITY): List<TradeLeg> {
        val def = e.content.goodOrNull(good) ?: return emptyList()
        val buys = ArrayList<Side>(); val sells = ArrayList<Side>()
        for (r in e.world.regions().sortedBy { it.id }) {
            val key = MarketKey(r.id, good)
            val state = e.markets.state(key) ?: continue
            val city = e.world.citiesIn(r.id).minByOrNull { it.id.value }?.id ?: continue
            val quote = e.markets.quote(key)
            buys += Side(r.id, r.name, quote.ask?.perUnit ?: state.last.perUnit, city)
            sells += Side(r.id, r.name, quote.bid?.perUnit ?: state.last.perUnit, city)
        }
        val cheapest = buys.sortedWith(compareBy({ it.price.raw }, { it.region.value })).take(SOURCES)
        val dearest = sells.sortedWith(compareBy({ -it.price.raw }, { it.region.value })).take(SOURCES)
        val out = ArrayList<TradeLeg>()
        for (from in cheapest) for (to in dearest) {
            if (from.region == to.region || to.price.raw <= from.price.raw) continue
            val worth = from.price.valueOf(quantity)
            val freight = e.logistics.quote(from.city, to.city, good, quantity, e.tick, goodsValue = worth) ?: continue
            out += TradeLeg(
                good = good, goodName = def.name, from = from.region, fromName = from.name, to = to.region, toName = to.name,
                buyPrice = from.price, sellPrice = to.price, quantity = quantity, freight = freight.freight, tariff = freight.tariff,
                days = freight.ticks, crossesBorder = freight.crossesBorder,
            )
        }
        return TradeMath.sorted(out, TradeSort.PROFIT)
    }

    companion object {
        /** Regions considered as sources and as destinations for each good. */
        const val SOURCES = 4
        val DEFAULT_QUANTITY: Quantity = Quantity.units(100)
    }
}
