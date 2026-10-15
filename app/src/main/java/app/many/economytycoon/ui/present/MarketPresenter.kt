package app.many.economytycoon.ui.present

import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.markets.Side
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.visualization.DataPoint
import app.many.economytycoon.visualization.LineSeries

enum class MarketSort(val label: String) { NAME("Name"), PRICE("Price"), CHANGE("Change"), VOLUME("Volume"), SHORTAGE("Shortage"), SURPLUS("Surplus") }

data class BoardRow(
    val good: GoodId,
    val name: String,
    val category: String,
    val price: String,
    val change: String,
    val changeTone: Tone,
    val versusReference: String,
    val referenceTone: Tone,
    val volume: String,
    val note: String?,
    val noteTone: Tone,
)

data class BoardModel(val rows: List<BoardRow>, val categories: List<GoodCategory?>, val inflation: String?, val cpi: String?)

object MarketPresenter {
    fun categoryLabel(c: GoodCategory?): String = c?.let { Formatting.title(it.name.lowercase()) } ?: "All"

    fun board(session: GameSession, category: GoodCategory? = null, sort: MarketSort = MarketSort.NAME, query: String = ""): BoardModel {
        val reports = session.reports.market
        val q = query.trim().lowercase()
        val quotes = reports.board(category).filter { q.isEmpty() || it.name.lowercase().contains(q) }
        val ordered = when (sort) {
            MarketSort.NAME -> quotes.sortedBy { it.name }
            MarketSort.PRICE -> quotes.sortedByDescending { it.price.raw }
            MarketSort.CHANGE -> quotes.sortedByDescending { kotlin.math.abs(it.changePpm) }
            MarketSort.VOLUME -> quotes.sortedByDescending { it.volume.raw }
            MarketSort.SHORTAGE -> quotes.sortedByDescending { it.unmet.raw }
            MarketSort.SURPLUS -> quotes.sortedByDescending { it.unsold.raw }
        }
        val money = session.settings::money
        val rows = ordered.map { g ->
            val note = when {
                g.unmet.raw > g.volume.raw && g.unmet.raw > 0 -> "Shortage" to Tone.WARN
                g.unsold.raw > g.volume.raw && g.unsold.raw > 0 -> "Glut" to Tone.INFO
                else -> null
            }
            BoardRow(
                good = g.good, name = g.name, category = categoryLabel(g.category), price = money(g.price),
                change = Formatting.signedPercentPpm(g.changePpm), changeTone = changeTone(g.changePpm),
                versusReference = Formatting.signedPercentPpm(g.deviationPpm) + " vs. normal", referenceTone = deviationTone(g.deviationPpm),
                volume = Formatting.quantity(g.volume), note = note?.first, noteTone = note?.second ?: Tone.NEUTRAL,
            )
        }
        val index = reports.indices()
        return BoardModel(
            rows = rows, categories = listOf<GoodCategory?>(null) + GoodCategory.values().toList(),
            inflation = index?.inflationPpm?.let { Formatting.signedPercentPpm(it) }, cpi = index?.let { Formatting.grouped(it.cpi) },
        )
    }

    /** Rising prices are bad for buyers and good for sellers; the board is mostly read as a seller, so rising is green. */
    fun changeTone(ppm: Long): Tone = when {
        ppm > 5_000L -> Tone.GOOD
        ppm < -5_000L -> Tone.BAD
        else -> Tone.NEUTRAL
    }

    fun deviationTone(ppm: Long): Tone = when {
        kotlin.math.abs(ppm) < 100_000L -> Tone.NEUTRAL
        else -> Tone.WARN
    }
}

data class RegionQuoteItem(val region: RegionId, val name: String, val price: String, val spread: String, val volume: String, val change: String, val changeTone: Tone)

data class DepthItem(val side: Side, val price: String, val quantity: String, val orders: Int, val fraction: Double)

data class GoodDetailModel(
    val good: GoodId,
    val name: String,
    val category: String,
    val facts: List<Fact>,
    val regions: List<RegionQuoteItem>,
    val history: LineSeries,
    val producers: List<Pair<String, Int>>,
    val trades: List<TradeLine>,
)

data class TradeLine(val whenText: String, val region: String, val quantity: String, val price: String, val parties: String)

object GoodDetailPresenter {
    fun build(session: GameSession, good: GoodId): GoodDetailModel? {
        val reports = session.reports.market
        val quote = reports.board().firstOrNull { it.good == good } ?: return null
        val money = session.settings::money
        val regions = reports.regions(good).map { r ->
            val spread = if (r.bid != null && r.ask != null) money(r.ask) + " / " + money(r.bid) else "—"
            RegionQuoteItem(r.region, r.regionName, money(r.price), spread, Formatting.quantity(r.volume), Formatting.signedPercentPpm(r.changePpm), MarketPresenter.changeTone(r.changePpm))
        }
        val avg = reports.averageHistory(good)
        val now = session.tick.value
        val points = avg.mapIndexed { i, m -> DataPoint((now - (avg.size - 1 - i)).toDouble(), m.raw / 10_000.0) }
        val facts = listOf(
            Fact("Average price", money(quote.price)),
            Fact("Normal price", money(quote.reference)),
            Fact("Change", Formatting.signedPercentPpm(quote.changePpm), MarketPresenter.changeTone(quote.changePpm)),
            Fact("Traded recently", Formatting.quantity(quote.volume)),
            Fact("Unmet demand", Formatting.quantity(quote.unmet), if (quote.unmet.raw > 0) Tone.WARN else Tone.NEUTRAL),
            Fact("Unsold supply", Formatting.quantity(quote.unsold), if (quote.unsold.raw > 0) Tone.INFO else Tone.NEUTRAL),
        )
        val trades = reports.recentTrades(12, good).map { t ->
            TradeLine(Formatting.date(app.many.economytycoon.core.Tick(t.tick)), t.region, Formatting.quantity(t.quantity), money(t.price), "${t.seller} → ${t.buyer}")
        }
        return GoodDetailModel(good, quote.name, MarketPresenter.categoryLabel(quote.category), facts, regions, LineSeries(quote.name, points, 0), session.reports.rivals.producersOf(good), trades)
    }

    fun depth(session: GameSession, region: RegionId, good: GoodId): List<DepthItem> {
        val levels = session.reports.market.depth(region, good)
        val top = levels.maxOfOrNull { it.quantity.raw }?.coerceAtLeast(1L) ?: 1L
        val money = session.settings::money
        return levels.map { DepthItem(it.side, money(it.price.perUnit), Formatting.quantity(it.quantity), it.orders, it.quantity.raw.toDouble() / top) }
    }
}

data class OrderItem(val id: Long, val title: String, val detail: String, val side: Side, val fillFraction: Double, val expires: String)

object OrdersPresenter {
    fun build(session: GameSession): List<OrderItem> {
        val company = session.selectedCompany ?: return emptyList()
        val money = session.settings::money
        return session.reports.market.ordersOf(company).map { o ->
            val verb = if (o.side == Side.SELL) "Sell" else "Buy"
            val limit = o.limit?.let { "at ${money(it)}" } ?: "at market price"
            val filled = o.quantity.raw - o.remaining.raw
            OrderItem(
                o.id, "$verb ${Formatting.quantity(o.remaining)} ${o.goodName}", "${o.region} · $limit",
                o.side, if (o.quantity.raw == 0L) 0.0 else filled.toDouble() / o.quantity.raw, "expires in " + Formatting.duration(o.expiresIn),
            )
        }
    }
}
