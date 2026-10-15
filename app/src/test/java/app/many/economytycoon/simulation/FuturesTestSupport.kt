package app.many.economytycoon.simulation

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Price
import app.many.economytycoon.markets.MarketKey
import app.many.economytycoon.markets.MarketState

/** Pins a good's price to one value so settlement results can be worked out by hand. */
object FuturesTestSupport {
    fun setSpot(e: Economy, good: GoodId, price: Money) {
        val region = e.world.regions().first().id
        e.markets.restoreState(MarketState(MarketKey(region, good), Price(price)))
    }
}
