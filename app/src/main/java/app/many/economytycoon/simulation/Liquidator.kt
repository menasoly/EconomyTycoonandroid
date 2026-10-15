package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.markets.Party

class LiquidationReport(val company: CompanyId, val raised: Money, val result: app.many.economytycoon.companies.LiquidationResult?)

/**
 * Sells what a failed company owns so the insolvency service has cash to pay creditors with. Stock goes
 * at a heavy discount to the book value, buildings for their demolition refund, vehicles at resale.
 * The buyers are the households, who stand in for the scrap trade.
 */
class Liquidator(private val e: Economy, private val stockRecoveryPpm: Long = 400_000L) {

    fun run(tick: Tick): List<LiquidationReport> {
        val out = ArrayList<LiquidationReport>()
        for (id in e.insolvency.awaitingLiquidation().sorted()) {
            val c = e.companies.company(id) ?: continue
            if (c.status != CompanyStatus.LIQUIDATING) continue
            out += liquidate(id, tick)
        }
        return out
    }

    fun liquidate(id: CompanyId, tick: Tick): LiquidationReport {
        for (o in e.markets.cancelAll(Party.company(id))) e.releaseReservation(o)
        var raised = Money.ZERO
        for (f in e.production.facilitiesOf(id)) {
            val stock = f.inventory.totalValue()
            if (stock.raw > 0L) {
                raised += e.payer.credit(id, Money(mulDiv(stock.raw, stockRecoveryPpm, 1_000_000L)), "liquidation stock sale")
                for (g in f.inventory.goods()) f.inventory.clear(g)
            }
            val d = e.production.demolish(id, f.id)
            if (d is Outcome.Ok) raised += e.payer.credit(id, d.value.refund, "liquidation demolition")
            e.facilityStats.forget(f.id)
        }
        for (v in e.logistics.vehiclesOf(id)) {
            val sold = e.logistics.sellVehicle(id, v.id)
            if (sold is Outcome.Ok) raised += e.payer.credit(id, sold.value, "liquidation vehicle sale")
        }
        for (w in e.logistics.warehousesOf(id)) e.logistics.closeWarehouse(id, w.id)
        val result = e.insolvency.liquidate(id, tick)
        e.retire(id)
        return LiquidationReport(id, raised, result.getOrNull())
    }
}
