package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.exchange.Listing
import app.many.economytycoon.exchange.TradeSide
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.simulation.HoldingValue
import app.many.economytycoon.visualization.DataPoint
import app.many.economytycoon.visualization.LineSeries

/** One company on the board. */
data class StockRow(
    val id: CompanyId,
    val name: String,
    val price: String,
    val change: String,
    val tone: Tone,
    val marketCap: String,
    val spark: List<Double>,
    val held: Long,
    val mine: Boolean,
)

data class HoldingRow(
    val id: CompanyId,
    val name: String,
    val shares: String,
    val price: String,
    val value: String,
    val cost: String,
    val gain: String,
    val gainTone: Tone,
    val gainPercent: String,
)

/** Whether and how the player's company can float, with what a chosen float would do. */
data class FlotationModel(
    val listed: Boolean,
    val problem: String?,
    val minPercent: Long,
    val maxPercent: Long,
    val preview: String?,
)

data class ExchangeModel(
    val tiles: List<Tile>,
    val index: LineSeries,
    val board: List<StockRow>,
    val holdings: List<HoldingRow>,
    val flotation: FlotationModel,
    val portfolioNote: String,
)

data class StockDetailModel(
    val id: CompanyId,
    val name: String,
    val price: String,
    val change: String,
    val tone: Tone,
    val history: LineSeries,
    val facts: List<Fact>,
    val position: List<Fact>,
    val verdict: String,
    val verdictTone: Tone,
    val canBuy: Boolean,
    val canSell: Boolean,
    val mine: Boolean,
    val publicShares: Long,
    val held: Long,
    val canBuyBack: Boolean,
    val canDelist: Boolean,
)

/** A priced trade shown before the player confirms it. */
data class TradeQuoteView(val summary: String, val total: String, val impact: String, val tone: Tone, val problem: String?)

object ExchangePresenter {
    private fun settings(session: GameSession) = session.settings

    fun changeText(ppm: Long): String = Formatting.signedPercentPpm(ppm)
    fun changeTone(ppm: Long): Tone = when {
        ppm > 5_000L -> Tone.GOOD
        ppm < -5_000L -> Tone.BAD
        else -> Tone.NEUTRAL
    }

    private fun nameOf(session: GameSession, id: CompanyId) = session.economy.companies.company(id)?.name ?: "Company ${id.value}"

    fun sparkOf(l: Listing): List<Double> = (l.history + l.price).map { it.raw / 10_000.0 }

    fun row(session: GameSession, l: Listing, me: CompanyId?): StockRow {
        val e = session.economy
        val money = settings(session)::money
        return StockRow(
            id = l.issuer, name = nameOf(session, l.issuer), price = money(l.price), change = changeText(l.changePpm), tone = changeTone(l.changePpm),
            marketCap = Formatting.compactMoney(e.exchangeRunner.marketCap(l.issuer)), spark = sparkOf(l),
            held = if (me != null) e.exchangeRunner.holdingOf(me, l.issuer) else 0L, mine = l.issuer == me,
        )
    }

    fun holding(session: GameSession, h: HoldingValue): HoldingRow {
        val money = settings(session)::money
        val gainPpm = if (h.cost.raw <= 0L) 0L else h.gain.raw * 1_000_000L / h.cost.raw
        return HoldingRow(
            id = h.issuer, name = nameOf(session, h.issuer), shares = Formatting.grouped(h.shares), price = money(h.price), value = money(h.value),
            cost = money(h.cost), gain = Formatting.signedMoney(h.gain), gainTone = Tones.bySign(h.gain.raw), gainPercent = Formatting.signedPercentPpm(gainPpm),
        )
    }

    fun flotation(session: GameSession, floatPpm: Long? = null): FlotationModel {
        val me = session.selectedCompany
        val e = session.economy
        val p = e.exchange.params
        val c = me?.let { e.companies.company(it) }
        val min = p.minFloatPpm / 10_000L
        val max = p.maxFloatPpm / 10_000L
        if (c == null) return FlotationModel(false, "no company selected", min, max, null)
        if (e.exchange.isListed(c.id)) return FlotationModel(true, null, min, max, null)
        val problem = e.exchangeRunner.listingProblem(c, session.tick)
        val preview = floatPpm?.let { e.exchangeRunner.previewListing(c.id, it, session.tick) }?.let {
            val money = settings(session)::money
            "Sells ${Formatting.grouped(it.shares)} new shares at ${money(it.price)} to raise ${money(it.raised)}, less a ${money(it.fee)} bankers' fee."
        }
        return FlotationModel(false, problem, min, max, preview)
    }

    fun build(session: GameSession, floatPpm: Long? = null): ExchangeModel? {
        val me = session.selectedCompany ?: return null
        val e = session.economy
        val money = settings(session)::money
        val listings = e.exchange.listings()
        val board = listings.map { row(session, it, me) }.sortedWith(compareByDescending<StockRow> { it.mine }.thenBy { it.name })
        val holdings = e.exchangeRunner.portfolio(me).map { holding(session, it) }.sortedBy { it.name }
        val value = e.exchangeRunner.portfolio(me).fold(Money.ZERO) { a, h -> a + h.value }
        val gain = e.exchangeRunner.portfolio(me).fold(Money.ZERO) { a, h -> a + h.gain }
        val ix = e.exchange.index
        val tiles = listOf(
            Tile("Index", String.format("%.2f", ix / 1000.0), changeTone(e.exchange.indexChangePpm), changeText(e.exchange.indexChangePpm)),
            Tile("Listed", listings.size.toString(), note = "companies"),
            Tile("Your holdings", money(value), note = "at market"),
            Tile("Unrealised gain", Formatting.signedMoney(gain), Tones.bySign(gain.raw)),
        )
        val closes = e.exchange.indexHistory()
        val start = (session.tick.value - closes.size).coerceAtLeast(0L)
        val points = closes.mapIndexed { i, v -> DataPoint((start + i).toDouble(), v / 1000.0) } + DataPoint(session.tick.value.toDouble(), ix / 1000.0)
        val note = if (holdings.isEmpty()) "You hold no shares. Buy into a listed company to earn from its rise." else "Shares are valued at the last close; selling costs the spread and a fee."
        return ExchangeModel(tiles, LineSeries("Index", points, 0), board, holdings, flotation(session, floatPpm), note)
    }

    fun detail(session: GameSession, issuer: CompanyId): StockDetailModel? {
        val me = session.selectedCompany ?: return null
        val e = session.economy
        val l = e.exchange.listing(issuer) ?: return null
        val money = settings(session)::money
        val held = e.exchangeRunner.holdingOf(me, issuer)
        val publicShares = e.exchangeRunner.publicShares(issuer)
        val outstanding = e.exchangeRunner.outstanding(issuer)
        val closes = l.history
        val start = (session.tick.value - closes.size).coerceAtLeast(0L)
        val points = closes.mapIndexed { i, v -> DataPoint((start + i).toDouble(), v.raw / 10_000.0) } + DataPoint(session.tick.value.toDouble(), l.price.raw / 10_000.0)
        val gapPpm = if (l.fairValue.raw <= 0L) 0L else (l.price.raw - l.fairValue.raw) * 1_000_000L / l.fairValue.raw
        val (verdict, verdictTone) = when {
            gapPpm < -120_000L -> "Trading well below the business's value." to Tone.GOOD
            gapPpm > 120_000L -> "Trading well above the business's value." to Tone.WARN
            else -> "Priced close to the business's value." to Tone.NEUTRAL
        }
        val mine = issuer == me
        val facts = listOf(
            Fact("Market value", Formatting.compactMoney(e.exchangeRunner.marketCap(issuer))),
            Fact("Business value", money(l.fairValue), hint = "What the company is worth per share on its books and earnings"),
            Fact("Day range", "${money(minOf(l.price, l.previousClose))} – ${money(maxOf(l.price, l.previousClose))}"),
            Fact("Period range", "${money(l.low)} – ${money(l.high)}"),
            Fact("Shares", Formatting.grouped(outstanding), hint = "${Formatting.grouped(publicShares)} held by the public"),
            Fact("Volume today", Formatting.grouped(l.volumeToday)),
            Fact("Listed since", Formatting.date(l.listedAt)),
        )
        val cost = e.exchange.basisOf(e.companies.company(me)!!.holder, issuer)
        val value = l.price * held
        val position = if (held <= 0L) emptyList() else listOf(
            Fact("You hold", Formatting.grouped(held), hint = if (outstanding > 0) Formatting.percentPpm(held * 1_000_000L / outstanding) + " of the company" else null),
            Fact("Worth", money(value)),
            Fact("Paid", money(cost)),
            Fact("Gain", Formatting.signedMoney(value - cost), Tones.bySign((value - cost).raw)),
        )
        return StockDetailModel(
            id = issuer, name = nameOf(session, issuer), price = money(l.price), change = changeText(l.changePpm), tone = changeTone(l.changePpm),
            history = LineSeries("Price", points, 0), facts = facts, position = position, verdict = verdict, verdictTone = verdictTone,
            canBuy = publicShares > 0L && !mine, canSell = held > 0L, mine = mine, publicShares = publicShares, held = held,
            canBuyBack = mine && publicShares > 0L, canDelist = mine,
        )
    }

    /** Prices a trade of [shares] for the player's company. */
    fun quote(session: GameSession, issuer: CompanyId, buying: Boolean, shares: Long): TradeQuoteView {
        val me = session.selectedCompany ?: return TradeQuoteView("", "", "", Tone.NEUTRAL, "no company selected")
        val e = session.economy
        val money = settings(session)::money
        if (shares <= 0L) return TradeQuoteView("", "", "", Tone.NEUTRAL, null)
        val publicShares = e.exchangeRunner.publicShares(issuer)
        val side = if (buying) TradeSide.BUY else TradeSide.SELL
        val q = (e.exchange.quote(issuer, side, shares, publicShares) as? app.many.economytycoon.core.Outcome.Ok)?.value
            ?: return TradeQuoteView("", "", "", Tone.NEUTRAL, "That company is not listed.")
        val cash = e.finance.ledger.cash(e.companies.company(me)!!.owner)
        val problem = when {
            buying && shares > publicShares -> "The public is only selling ${Formatting.grouped(publicShares)} shares."
            !buying && shares > e.exchangeRunner.holdingOf(me, issuer) -> "You do not hold that many shares."
            buying && cash < q.net -> "You need ${money(q.net)} in cash."
            else -> null
        }
        val verb = if (buying) "Pay" else "Receive"
        val tone = if (q.impactPpm > 50_000L) Tone.WARN else Tone.NEUTRAL
        return TradeQuoteView(
            summary = "${Formatting.grouped(shares)} shares at about ${money(q.averagePrice)} = ${money(q.gross)}, fee ${money(q.fee)}" + if (buying) ", stamp duty ${money(q.duty)}" else "",
            total = "$verb ${money(q.net)}", impact = "Moves the price ${Formatting.percentPpm(q.impactPpm)}", tone = tone, problem = problem,
        )
    }
}
