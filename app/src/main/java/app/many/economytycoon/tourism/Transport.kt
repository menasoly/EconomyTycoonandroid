package app.many.economytycoon.tourism

import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/**
 * The only thing tourism needs to know about the transport system: how many seats a day can bring visitors from an
 * origin market to a destination. The logistics side implements it; tourism never imports it.
 */
fun interface TransportCapacity {
    fun seatsPerDay(origin: OriginId, destination: DestinationId, tick: Tick): Long
}

/** No limit: every visitor who wants to come can. */
object UnlimitedTransport : TransportCapacity {
    override fun seatsPerDay(origin: OriginId, destination: DestinationId, tick: Tick): Long = Long.MAX_VALUE / 4
}

/** A fixed number of seats per route, with a default for routes not listed. */
class FixedTransport(private val seats: Map<Pair<OriginId, DestinationId>, Long>, private val default: Long = Long.MAX_VALUE / 4) : TransportCapacity {
    override fun seatsPerDay(origin: OriginId, destination: DestinationId, tick: Tick): Long = seats[origin to destination] ?: default
}

/** Visitors that wanted to travel on a route and the seats that were there for them. */
data class RouteLoad(val origin: OriginId, val destination: DestinationId, val wanted: Long, val carried: Long) {
    val turnedAway: Long get() = wanted - carried
    val loadFactorPpm: Long get() = mulDiv(carried, 1_000_000L, (wanted).coerceAtLeast(1L))
}

object TransportCoupling {
    /**
     * Limits the visitors on each route to the seats available, scaled by [transportPpm] (a strike or a storm takes
     * part of the capacity away). Within a route every segment is cut by the same share. Returns the cells that fit
     * and the load of each route that had any demand, in route order.
     */
    fun limit(
        cells: List<FlowCell>, destination: DestinationId, transport: TransportCapacity, tick: Tick, transportPpm: Long,
    ): Pair<List<FlowCell>, List<RouteLoad>> {
        val byOrigin = java.util.TreeMap<OriginId, MutableList<FlowCell>>()
        for (c in cells) byOrigin.getOrPut(c.origin) { ArrayList() } += c
        val kept = ArrayList<FlowCell>()
        val loads = ArrayList<RouteLoad>()
        for ((origin, group) in byOrigin) {
            val wanted = group.sumOf { it.persons }
            val raw = transport.seatsPerDay(origin, destination, tick).coerceAtLeast(0L)
            val seats = mulDiv(raw, transportPpm.coerceIn(0L, 1_000_000L), 1_000_000L)
            if (wanted <= seats) {
                kept += group
                if (wanted > 0L) loads += RouteLoad(origin, destination, wanted, wanted)
            } else {
                var carried = 0L
                for (c in group) {
                    val n = mulDiv(c.persons, seats, wanted, app.many.economytycoon.core.RoundingMode.DOWN)
                    carried += n
                    kept += c.copy(persons = n)
                }
                loads += RouteLoad(origin, destination, wanted, carried)
            }
        }
        return kept to loads
    }
}
