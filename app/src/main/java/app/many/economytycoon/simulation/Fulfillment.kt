package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.finance.TaxIntents
import app.many.economytycoon.macro.GdpKind
import app.many.economytycoon.markets.MarketReport
import app.many.economytycoon.markets.PartyKind
import app.many.economytycoon.markets.Trade

/** What fulfilment did with a tick's trades. */
class FulfillmentReport {
    var executed: Int = 0
    var skippedNoCash: Int = 0
    var skippedNoStock: Int = 0
    var skippedNoRoom: Int = 0
    var delivered: Money = Money.ZERO
    var consumerSpend: Money = Money.ZERO
    val taxIntents = ArrayList<SettlementIntent>()
    /** Goods that crossed a border in today's trades, for the carbon border adjustment. */
    val crossBorder = ArrayList<app.many.economytycoon.carbon.TradeFlow>()
}

/**
 * Turns cleared trades into goods and money. A trade that cannot complete (the buyer ran out of cash,
 * the seller's stock vanished, the buyer's store is full) is dropped and any seller reservation it held
 * is released, so a failed trade never leaves goods or money in limbo.
 */
class Fulfillment(private val e: Economy) {
    fun run(report: MarketReport): FulfillmentReport {
        val out = FulfillmentReport()
        for (trade in report.trades) settle(trade, out)
        return out
    }

    private fun settle(trade: Trade, out: FulfillmentReport) {
        val seller = trade.seller.companyId
        val sellerFacility = if (seller != null) e.production.facility(FacilityId(trade.sellerSource)) else null
        if (seller == null || sellerFacility == null || sellerFacility.owner != seller) {
            out.skippedNoStock++
            return
        }
        val good = trade.key.good
        var qty = trade.quantity

        // Buyer side first: a failure here only needs the seller's reservation released.
        val buyerCompany = trade.buyer.companyId
        val buyerFacility = if (buyerCompany != null) e.production.facility(FacilityId(trade.buyerSource)) else null
        if (trade.buyer.kind == PartyKind.COMPANY) {
            if (buyerFacility == null || buyerFacility.owner != buyerCompany) { drop(sellerFacility, good, qty, out) { out.skippedNoStock++ }; return }
            val room = buyerFacility.inventory.room(good)
            if (room < qty) qty = room
            if (qty.raw <= 0L) { drop(sellerFacility, good, trade.quantity, out) { out.skippedNoRoom++ }; return }
        }
        val value = trade.price.valueOf(qty)
        if (value.raw <= 0L) { drop(sellerFacility, good, trade.quantity, out) { out.skippedNoStock++ }; return }

        val buyerOwner = when (trade.buyer.kind) {
            PartyKind.COMPANY -> LedgerOwner.company(buyerCompany!!)
            PartyKind.HOUSEHOLDS -> e.finance.households
            PartyKind.GOVERNMENT -> LedgerOwner.government(CountryId(trade.buyer.id))
            PartyKind.EXTERNAL -> { drop(sellerFacility, good, trade.quantity, out) { out.skippedNoStock++ }; return }
        }
        val sellerOwner = LedgerOwner.company(seller)
        if (e.finance.ledger.cash(buyerOwner) < value) { drop(sellerFacility, good, trade.quantity, out) { out.skippedNoCash++ }; return }

        // Take stock out of the reservation; anything beyond what the buyer could take goes back on the shelf.
        val cost = sellerFacility.inventory.removeReserved(good, qty).getOrNull()
        if (cost == null) { drop(sellerFacility, good, trade.quantity, out) { out.skippedNoStock++ }; return }
        if (qty < trade.quantity) sellerFacility.inventory.release(good, trade.quantity - qty)

        val buyerChart = if (trade.buyer.kind == PartyKind.COMPANY) Chart.INVENTORY else Chart.OTHER_EXPENSE
        val booked = e.finance.recordSale("trade:${trade.id.value}", trade.tick, sellerOwner, buyerOwner, value, cost, Chart.REVENUE_SALES, buyerChart)
        if (booked is app.many.economytycoon.core.Outcome.Rejected) {
            sellerFacility.inventory.add(good, qty, cost, allowOverflow = true)
            out.skippedNoCash++
            return
        }
        if (buyerFacility != null) {
            // Room was checked above; rounding in volume must never lose goods that were paid for.
            buyerFacility.inventory.add(good, qty, value, allowOverflow = true)
        }

        out.executed++
        out.delivered += value
        val country = e.world.region(trade.key.region).country
        if (e.ipActive) e.ipContext.recordSale(trade.tick, seller, good, country, qty, value)
        val origin = e.world.region(sellerFacility.region).country
        val destination = buyerFacility?.let { e.world.region(it.region).country } ?: country
        if (origin != destination) out.crossBorder += app.many.economytycoon.carbon.TradeFlow(good, qty, origin, destination, buyerCompany.takeIf { buyerFacility != null }, seller)
        if (trade.buyer.kind == PartyKind.HOUSEHOLDS) {
            out.consumerSpend += value
            e.macro.record(country, GdpKind.CONSUMPTION, value)
            val policy = e.finance.taxPolicy(e.governmentOf(country))
            val tax = value.times(policy.salesRate)
            if (tax.raw > 0L) {
                out.taxIntents += TaxIntents.sales("salestax:${trade.id.value}", sellerOwner, e.governmentOf(country), tax)
            }
        }
    }

    private inline fun drop(f: app.many.economytycoon.production.Facility, good: app.many.economytycoon.core.GoodId, qty: Quantity, out: FulfillmentReport, count: () -> Unit) {
        f.inventory.release(good, qty)
        count()
    }
}
