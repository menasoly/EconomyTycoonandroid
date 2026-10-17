package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv

/** Hourly dispatch of all grids after power has moved over the interconnectors. */
class TradeOutcome(val results: Map<GridId, DispatchResult>, val transfers: List<Transfer>)

/**
 * Lets cheap grids supply dear ones. Every grid is first dispatched alone. Then each link, in id order, compares the
 * two prices: when the cost of the exporter's next unit, grossed up for losses and plus the wheeling charge, is below
 * what the importer is paying, power flows from the cheap side up to the line's capacity, the exporter's spare
 * capacity at a cost the importer would accept, and the importer's own output that it displaces. The flow is halved
 * until the two prices no longer cross, so a link never turns a price gap into the opposite gap. Both grids are
 * dispatched again with the flow, and later links see the changed prices.
 */
object TradeDispatcher {
    private const val MAX_HALVINGS = 4

    fun run(inputs: Map<GridId, DispatchInput>, links: List<LinkView>): TradeOutcome {
        val grids = inputs.keys.sorted()
        val imports = HashMap<GridId, Long>()
        val exports = HashMap<GridId, Long>()
        val results = java.util.TreeMap<GridId, DispatchResult>()
        for (g in grids) results[g] = DispatchEngine.dispatch(inputs.getValue(g))
        val transfers = ArrayList<Transfer>()

        fun redispatch(g: GridId, extraImport: Long, extraExport: Long): DispatchResult =
            DispatchEngine.dispatch(inputs.getValue(g).copy(importKw = (imports[g] ?: 0L) + extraImport, exportKw = (exports[g] ?: 0L) + extraExport))

        for (link in links.sortedBy { it.id }) {
            val ra = results[link.a] ?: continue
            val rb = results[link.b] ?: continue
            if (link.capacityKw <= 0L) continue
            val (from, to) = if (ra.price <= rb.price) link.a to link.b else link.b to link.a
            val exporter = results.getValue(from)
            val importer = results.getValue(to)
            val keep = Rate.ONE_PPM - link.lossPpm
            val landed = Money(mulDiv(exporter.nextCost().raw, Rate.ONE_PPM, keep, RoundingMode.UP)) + link.wheelingPerMwh
            if (landed >= importer.price) continue

            val acceptable = Money(mulDiv((importer.price - link.wheelingPerMwh).raw.coerceAtLeast(0L), keep, Rate.ONE_PPM, RoundingMode.DOWN))
            val wanted = importer.outputAbove(landed) + importer.unservedKw
            var send = minOf(link.capacityKw, exporter.headroomUpTo(acceptable), mulDiv(wanted, Rate.ONE_PPM, keep, RoundingMode.UP))
            var done = false
            var tries = 0
            while (send > 0L && !done) {
                val got = mulDiv(send, keep, Rate.ONE_PPM, RoundingMode.DOWN)
                val newExp = redispatch(from, 0L, send)
                val newImp = redispatch(to, got, 0L)
                if (newExp.price <= newImp.price || tries >= MAX_HALVINGS) {
                    if (newExp.price <= newImp.price) {
                        results[from] = newExp; results[to] = newImp
                        exports[from] = (exports[from] ?: 0L) + send
                        imports[to] = (imports[to] ?: 0L) + got
                        transfers += Transfer(link.id, from, to, send, got)
                    }
                    done = true
                } else {
                    send /= 2L
                    tries++
                }
            }
        }
        return TradeOutcome(results, transfers)
    }
}
