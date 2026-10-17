package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.Chart

/** The rules in force for a permit market in a year. */
internal class Venue(val regime: CarbonRegime, val step: RegimeStep, val market: PermitMarket)

internal const val AUCTIONS_PER_YEAR = 4
internal const val DAYS_PER_AUCTION_QUARTER = CarbonCalendar.DAYS_PER_YEAR / AUCTIONS_PER_YEAR

internal fun CarbonService.venue(country: CountryId, year: Int): Outcome<Venue> {
    val regime = state.regime(country) ?: return Outcome.reject(Rules.WRONG_STATE, "that country has no carbon policy")
    if (!regime.tradesPermits) return Outcome.reject(Rules.WRONG_STATE, "that country does not trade permits")
    val step = regime.stepFor(year) ?: return Outcome.reject(Rules.WRONG_STATE, "the permit scheme has not begun")
    val market = state.market(country) ?: return Outcome.reject(Rules.WRONG_STATE, "that country has no permit market")
    return Outcome.ok(Venue(regime, step, market))
}

/** Most permits a company may hold in a market: a share of the year's cap. */
internal fun holdingLimit(v: Venue, year: Int): Tonnes = v.step.capIn(year).times(Rate(v.regime.holdingLimitPpm))

// ---- auction bids --------------------------------------------------------------------------------

internal fun CarbonService.bid(ctx: CarbonService.Ctx, c: CarbonCommand.BidAtAuction): Outcome<CarbonEffect> {
    val v = venue(c.country, ctx.year).let { if (it is Outcome.Rejected) return it else (it as Outcome.Ok).value }
    if (c.quantity < params.minTrade) return Outcome.reject(Rules.INVALID_QUANTITY, "the smallest bid is ${params.minTrade.format()} t")
    if (!c.limit.isPositive) return Outcome.reject(Rules.INVALID_PRICE, "a bid needs a price")
    if (v.step.ceiling.isPositive && c.limit > v.step.ceiling.times(Rate(2_000_000L))) return Outcome.reject(Rules.INVALID_PRICE, "that price is far above the ceiling")
    if (v.market.bidsOf(c.company).size >= params.maxBidsPerCompany) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many bids waiting")
    val room = holdingLimit(v, ctx.year) - v.market.holdings.total(c.company) - Tonnes.sum(v.market.bidsOf(c.company).map { it.quantity })
    if (c.quantity > room) return Outcome.reject(Rules.LIMIT_EXCEEDED, "that would take you over the holding limit")
    if (ctx.cash.available(c.company) < c.quantity.cost(c.limit)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "you could not pay for that bid")
    val bid = AuctionBid(state.counters.bid(), c.company, c.quantity, c.limit, ctx.tick)
    v.market.bidMap[bid.id] = bid
    return Outcome.ok(CarbonEffect.of("Bid for ${c.quantity.format()} t at up to ${c.limit} entered for the next auction"))
}

internal fun CarbonService.cancelBid(c: CarbonCommand.CancelBid): Outcome<CarbonEffect> {
    val market = state.market(c.country) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such market")
    val bid = market.bidMap[c.bid] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such bid")
    if (bid.company != c.company) return Outcome.reject(Rules.NOT_OWNER, "that is not your bid")
    market.bidMap.remove(c.bid)
    return Outcome.ok(CarbonEffect.of("Bid withdrawn"))
}

// ---- the quarterly auction -----------------------------------------------------------------------

/** Holds the auction of every permit market whose auction day it is. */
internal fun CarbonService.runAuctions(ctx: CarbonService.Ctx) {
    val day = CarbonCalendar.dayOfYear(ctx.tick)
    if (day % DAYS_PER_AUCTION_QUARTER != params.auctionDayOfQuarter) return
    val quarter = day / DAYS_PER_AUCTION_QUARTER
    for (market in state.markets()) {
        val regime = state.regime(market.country) ?: continue
        if (!regime.tradesPermits) continue
        val step = regime.stepFor(ctx.year) ?: continue
        val supply = market.supply(ctx.year) ?: continue
        if (supply.auctionsHeld >= AUCTIONS_PER_YEAR) continue
        val left = AUCTIONS_PER_YEAR - supply.auctionsHeld
        val offer = Tonnes(supply.remaining.raw / left)
        holdAuction(ctx, market, regime, step, supply, offer, quarter)
    }
}

private fun CarbonService.holdAuction(ctx: CarbonService.Ctx, market: PermitMarket, regime: CarbonRegime, step: RegimeStep, supply: YearSupply, offer: Tonnes, quarter: Int) {
    val bids = market.bids()
    val outcome = AuctionClearing.clear(offer, step.floor, step.ceiling, bids) { ctx.cash.available(it) }
    var revenue = Money.ZERO
    var issued = Tonnes.ZERO
    var failed = Tonnes.ZERO
    for (fill in outcome.fills) {
        if (!env.isActive(fill.company) || !ctx.cash.spend(fill.company, fill.cost)) { failed += fill.tonnes; continue }
        market.holdings.add(fill.company, ctx.year, fill.tonnes, fill.cost)
        ctx.settle.purchase("auction", company(fill.company), government(market.country), fill.cost, "permits bought at auction")
        revenue += fill.cost
        issued += fill.tonnes
    }
    val ceilingPart = Tonnes.min(outcome.ceilingSold, issued)
    val soldPart = issued - ceilingPart
    val unsold = outcome.unsold + failed
    supply.offered += offer
    supply.sold += soldPart
    supply.ceilingSold += ceilingPart
    supply.unsold += unsold
    supply.auctionsHeld += 1
    market.mutateReserve(market.reserve + unsold)
    market.addCeilingReleased(ceilingPart)
    val bidders = bids.map { it.company }.distinct().size
    val cover = if (offer.raw <= 0L) 0L else mulDiv(outcome.demand.raw, Rate.ONE_PPM, offer.raw)
    val result = AuctionResult(ctx.tick, ctx.year, quarter, offer, soldPart, ceilingPart, unsold, outcome.price, bidders, cover)
    market.auctionLog += result
    while (market.auctionLog.size > params.keepAuctions) market.auctionLog.removeAt(0)
    if (issued.isPositive) {
        market.setAuctionPrice(outcome.price)
        market.quarterlyPrices += outcome.price
        while (market.quarterlyPrices.size > params.keepPrices) market.quarterlyPrices.removeAt(0)
    }
    market.bidMap.clear()
    if (revenue.isPositive) {
        state.noteReceipt(market.country, revenue)
        state.addToFund(market.country, Money(mulDiv(revenue.raw, regime.greenFundSharePpm, Rate.ONE_PPM)))
    }
    ctx.emit(CarbonEvent.AuctionCleared(ctx.tick, market.country, result))
    val release = StabilityReserve.priceRelease(regime.reserve, step.capIn(ctx.year), market.reserve, market.prices())
    if (release.isPositive) {
        supply.released += release
        market.mutateReserve(market.reserve - release)
        ctx.emit(CarbonEvent.ReserveAdjusted(ctx.tick, market.country, ctx.year, Tonnes.ZERO, release, Tonnes.ZERO))
    }
}

// ---- secondary market ----------------------------------------------------------------------------

internal fun CarbonService.placeOrder(ctx: CarbonService.Ctx, c: CarbonCommand.PlaceOrder): Outcome<CarbonEffect> {
    val v = venue(c.country, ctx.year).let { if (it is Outcome.Rejected) return it else (it as Outcome.Ok).value }
    val market = v.market
    if (c.quantity < params.minTrade) return Outcome.reject(Rules.INVALID_QUANTITY, "the smallest order is ${params.minTrade.format()} t")
    if (!c.limit.isPositive) return Outcome.reject(Rules.INVALID_PRICE, "an order needs a price")
    if (market.ordersOf(c.company).size >= params.maxOrdersPerCompany) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many orders resting")
    val held = market.holdings.total(c.company)
    when (c.side) {
        PermitSide.SELL -> if (held - market.reservedForSale(c.company) < c.quantity) return Outcome.reject(Rules.INSUFFICIENT_STOCK, "you do not hold that many permits that are not already for sale")
        PermitSide.BUY -> {
            if (held + c.quantity > holdingLimit(v, ctx.year)) return Outcome.reject(Rules.LIMIT_EXCEEDED, "that would take you over the holding limit")
            if (ctx.cash.available(c.company) < c.quantity.cost(c.limit)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "you could not pay for that order")
        }
    }
    val order = PermitOrder(state.counters.order(), c.company, c.side, c.limit, c.quantity, ctx.tick)
    val limit = holdingLimit(v, ctx.year)
    val capacity = object : OrderMatcher.Capacity {
        override fun canBuy(company: CompanyId, price: Money): Tonnes {
            if (!env.isActive(company) || !price.isPositive) return Tonnes.ZERO
            val affordable = Tonnes(mulDiv(ctx.cash.available(company).raw, Tonnes.SCALE, price.raw))
            val room = (limit - market.holdings.total(company)).coerceAtLeast(Tonnes.ZERO)
            return Tonnes.min(affordable, room)
        }

        override fun canSell(company: CompanyId): Tonnes = if (env.isActive(company)) market.holdings.total(company) else Tonnes.ZERO

        override fun settle(trade: PermitTrade) = settleTrade(ctx, market, trade)
    }
    val result = OrderMatcher.cross(market.orderMap.values, order, ctx.tick, capacity)
    if (order.isOpen) market.orderMap[order.id] = order
    val filled = Tonnes.sum(result.trades.map { it.tonnes })
    val message = when {
        result.trades.isEmpty() -> "Order for ${c.quantity.format()} t rests in the book at ${c.limit}"
        order.isOpen -> "${filled.format()} t traded; ${order.remaining.format()} t rest in the book at ${c.limit}"
        else -> "${filled.format()} t traded"
    }
    return Outcome.ok(CarbonEffect(ctx.settle.build(), message, result.trades))
}

/** Moves the permits of a trade, oldest first, and the money for them, and keeps the market's price record. */
private fun CarbonService.settleTrade(ctx: CarbonService.Ctx, market: PermitMarket, trade: PermitTrade) {
    val value = trade.value
    val taken = market.holdings.take(trade.seller, trade.tonnes)
    var left = value
    var remaining = taken.taken
    for ((i, part) in taken.parts.withIndex()) {
        val share = if (i == taken.parts.lastIndex) left else Money(mulDiv(value.raw, part.tonnes.raw, taken.taken.raw))
        market.holdings.add(trade.buyer, part.vintage, part.tonnes, share)
        left -= share
        remaining -= part.tonnes
    }
    ctx.cash.spend(trade.buyer, value)
    ctx.settle.tradeOfPermits(company(trade.buyer), company(trade.seller), value, taken.cost, "permits traded")
    market.setTradePrice(trade.price, trade.tonnes)
    market.tradeLog += trade
    while (market.tradeLog.size > params.keepTrades) market.tradeLog.removeAt(0)
}

internal fun CarbonService.cancelOrder(c: CarbonCommand.CancelOrder): Outcome<CarbonEffect> {
    val market = state.market(c.country) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such market")
    val order = market.orderMap[c.order] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such order")
    if (order.company != c.company) return Outcome.reject(Rules.NOT_OWNER, "that is not your order")
    market.orderMap.remove(c.order)
    return Outcome.ok(CarbonEffect.of("Order withdrawn"))
}

// ---- surrender -----------------------------------------------------------------------------------

internal fun CarbonService.surrender(ctx: CarbonService.Ctx, c: CarbonCommand.Surrender): Outcome<CarbonEffect> {
    val regime = state.regime(c.country)
    val market = state.market(c.country)
    if (regime == null || !regime.tradesPermits || market == null) return Outcome.reject(Rules.WRONG_STATE, "that country does not use permits")
    val pending = state.compliance.account(c.company, c.country)?.pending ?: return Outcome.reject(Rules.WRONG_STATE, "no year is waiting to be settled")
    if (!c.quantity.isPositive) return Outcome.reject(Rules.INVALID_QUANTITY, "surrender a positive amount")
    if (c.quantity > pending.outstanding) return Outcome.reject(Rules.INVALID_QUANTITY, "you only owe ${pending.outstanding.format()} t")
    val usable = market.holdings.held(c.company, firstValidVintage(regime, pending.year), pending.year)
    if (usable < c.quantity) return Outcome.reject(Rules.INSUFFICIENT_STOCK, "you hold ${usable.format()} t of usable permits")
    surrenderPermits(ctx, market, regime, c.company, pending, c.quantity)
    return Outcome.ok(CarbonEffect(ctx.settle.build(), "${c.quantity.format()} t surrendered for ${pending.year}"))
}

/** Takes up to [wanted] usable permits out of the holdings against [pending], writing their cost off. Returns the amount taken. */
internal fun CarbonService.surrenderPermits(
    ctx: CarbonService.Ctx, market: PermitMarket, regime: CarbonRegime, company: CompanyId, pending: PendingYear, wanted: Tonnes,
): Tonnes {
    val taken = market.holdings.take(company, wanted, firstValidVintage(regime, pending.year), pending.year)
    if (!taken.taken.isPositive) return Tonnes.ZERO
    pending.surrendered += taken.taken
    ctx.settle.writeDown(company(company), Chart.INTANGIBLES, taken.cost, "permits surrendered for ${pending.year}")
    return taken.taken
}
