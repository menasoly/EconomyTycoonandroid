package app.many.economytycoon.ui.present

import app.many.economytycoon.carbon.AuctionResult
import app.many.economytycoon.carbon.BookLevel
import app.many.economytycoon.carbon.CarbonCommand
import app.many.economytycoon.carbon.MarketView
import app.many.economytycoon.carbon.PermitSide
import app.many.economytycoon.carbon.PermitTrade
import app.many.economytycoon.carbon.RegimeKind
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.session.Formatting

data class CarbonBookRow(val price: String, val tonnes: String, val orders: String)

data class CarbonAuctionRow(val title: String, val result: String, val detail: String, val tone: Tone)

data class CarbonTradeRow(val day: String, val text: String)

data class CarbonMarketModel(
    val country: CountryId,
    val title: String,
    val tiles: List<Tile>,
    val supply: List<Fact>,
    val bids: List<CarbonBookRow>,
    val asks: List<CarbonBookRow>,
    val auctions: List<CarbonAuctionRow>,
    val trades: List<CarbonTradeRow>,
    val prices: List<Long>,
    val note: String?,
    val canTrade: Boolean,
)

/** A country's permit market: the price, this year's supply, the order book and what has cleared lately. */
object CarbonMarketPresenter {
    const val NO_MARKET = "This country has no carbon policy, so there is no market."
    const val NO_PERMITS = "This country taxes carbon instead of issuing permits. There is nothing to trade."

    fun level(l: BookLevel, desk: CarbonDesk) = CarbonBookRow(desk.money(l.price), CarbonPresenter.tonnes(l.tonnes), Formatting.plural(l.orders.toLong(), "order"))

    fun auction(a: AuctionResult, desk: CarbonDesk): CarbonAuctionRow {
        val over = a.coverPpm
        val tone = when {
            a.unsold.isPositive -> Tone.WARN
            over > 1_500_000L -> Tone.GOOD
            else -> Tone.NEUTRAL
        }
        val detail = buildString {
            append("${CarbonPresenter.tonnes(a.sold)} sold of ${CarbonPresenter.tonnes(a.supply)} to ${Formatting.plural(a.bidders.toLong(), "bidder")}")
            if (a.ceilingSold.isPositive) append("; ${CarbonPresenter.tonnes(a.ceilingSold)} more released at the ceiling")
            if (a.unsold.isPositive) append("; ${CarbonPresenter.tonnes(a.unsold)} went to the reserve")
        }
        return CarbonAuctionRow("${a.year} auction ${a.quarter + 1}", desk.money(a.price), detail, tone)
    }

    fun trade(t: PermitTrade, desk: CarbonDesk) =
        CarbonTradeRow("Day ${t.tick.value}", "${CarbonPresenter.tonnes(t.tonnes)} at ${desk.money(t.price)}")

    fun nextAuction(m: MarketView, desk: CarbonDesk): String {
        val next = m.nextAuction ?: return "No more auctions this year"
        val days = (next.value - desk.tick.value).toInt()
        return if (days <= 0) "Auction today" else "Next auction in ${Formatting.plural(days.toLong(), "day")}"
    }

    fun supply(m: MarketView): List<Fact> {
        val s = m.supply ?: return emptyList()
        return listOf(
            Fact("Yearly cap", CarbonPresenter.tonnes(s.cap)),
            Fact("Given away free", CarbonPresenter.tonnes(s.free)),
            Fact("For auction", CarbonPresenter.tonnes(s.auctionPlanned), hint = "${s.auctionsHeld} of 4 held"),
            Fact("Sold so far", CarbonPresenter.tonnes(s.sold)),
            Fact("Held back", CarbonPresenter.tonnes(s.withheld), if (s.withheld.isPositive) Tone.INFO else Tone.NEUTRAL, "Stability reserve"),
            Fact("Reserve", CarbonPresenter.tonnes(m.reserve)),
            Fact("In circulation", CarbonPresenter.tonnes(m.circulation)),
        )
    }

    fun build(desk: CarbonDesk, country: CountryId): CarbonMarketModel {
        val name = desk.countryName(country)
        val m = desk.market(country) ?: return CarbonMarketModel(country, name, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), NO_MARKET, false)
        val trades = m.kind == RegimeKind.CAP_AND_TRADE
        val spread = if (m.bestBid != null && m.bestAsk != null) desk.money(m.bestAsk - m.bestBid) else "none"
        val tiles = listOf(
            Tile("Price", desk.money(m.price), note = "${desk.money(m.floor)} floor, ${desk.money(m.ceiling)} ceiling"),
            Tile("Reserve", CarbonPresenter.tonnes(m.reserve), note = nextAuction(m, desk)),
            Tile("Spread", spread, note = "Best bid and ask"),
            Tile("Green fund", desk.money(m.greenFund), if (m.greenFund.raw > 0L) Tone.GOOD else Tone.NEUTRAL, "Revenue set aside"),
        )
        return CarbonMarketModel(
            country, name, tiles, supply(m),
            m.bids.take(6).map { level(it, desk) }, m.asks.take(6).map { level(it, desk) },
            m.auctions.takeLast(6).reversed().map { auction(it, desk) }, m.trades.takeLast(8).reversed().map { trade(it, desk) },
            m.prices.takeLast(24).map { it.raw / Money.SCALE },
            if (trades) null else NO_PERMITS, trades,
        )
    }

    fun bid(desk: CarbonDesk, country: CountryId, quantity: String, limit: String): String? {
        val q = CarbonPresenter.wholeTonnes(quantity) ?: return "Enter a number of tonnes above zero."
        val p = limit.trim().toLongOrNull()?.takeIf { it > 0L } ?: return "Enter the price as a whole number above zero."
        return desk.submit(CarbonCommand.BidAtAuction(desk.company, country, q, Money.units(p)))
    }

    fun order(desk: CarbonDesk, country: CountryId, side: PermitSide, quantity: String, limit: String): String? {
        val q = CarbonPresenter.wholeTonnes(quantity) ?: return "Enter a number of tonnes above zero."
        val p = limit.trim().toLongOrNull()?.takeIf { it > 0L } ?: return "Enter the price as a whole number above zero."
        return desk.submit(CarbonCommand.PlaceOrder(desk.company, country, side, q, Money.units(p)))
    }
}
