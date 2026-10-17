package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.finance.SettlementPriority

/** The month of one grid in money: what customers were billed against what generators were paid. */
data class GridStatement(
    val grid: GridId,
    val billed: Money,
    val fixedCharges: Money,
    val poolPaid: Money,
    val capacityPaid: Money,
    val capCompensation: Money,
    val ppaNetToSellers: Money,
    val servedKwh: Long,
    val shedKwh: Long,
    val wholesaleReference: Money,
) {
    /** What the retailer kept after paying the pool and the capacity market, counting the compensation. */
    val retailerResult: Money get() = billed + capCompensation - poolPaid - capacityPaid
}

class BillingRun(val month: Long, val intents: List<SettlementIntent>, val statements: List<GridStatement>) {
    val total: Money get() = Money.sum(intents.map { it.total })
    fun statementOf(grid: GridId): GridStatement? = statements.firstOrNull { it.grid == grid }
}

/**
 * Builds the month's ledger intents. Customers are billed for the energy they were served at their class tariff, plus
 * the fixed charge for their connection; generators are paid what the pool owes them; capacity awards are paid for
 * the month; and each power purchase agreement is settled against the pool. Nothing is posted here: the intents go to
 * the settlement engine, which pays them in its usual priority order.
 */
object GridBilling {
    private const val DAYS = Calendar.DAYS_PER_MONTH.toLong()

    private fun unitPayee(g: Grid, u: GenUnit): LedgerOwner = u.owner?.let { LedgerOwner.company(it) } ?: LedgerOwner.government(g.country)

    internal fun run(state: EnergyGridState, tick: Tick): BillingRun {
        val month = tick.value / Calendar.DAYS_PER_MONTH
        val intents = ArrayList<SettlementIntent>()
        val statements = ArrayList<GridStatement>()
        for (g in state.gridMap.values) statements += billGrid(g, month, tick, intents)
        val ppaNet = settlePpas(state, month, tick, intents)
        return BillingRun(month, intents, statements.map { it.copy(ppaNetToSellers = ppaNet[it.grid] ?: Money.ZERO) })
    }

    private fun billGrid(g: Grid, month: Long, tick: Tick, intents: MutableList<SettlementIntent>): GridStatement {
        val wholesale = GridAnalytics.windowAveragePrice(g.historyList, Calendar.DAYS_PER_MONTH)
        var billed = Money.ZERO
        var fixedTotal = Money.ZERO
        var shortfall = Money.ZERO
        var served = 0L
        var shed = 0L
        for (l in g.loadMap.values) {
            val quote = Tariffs.quote(g.tariff, l.customer, wholesale, g.capacityLevy)
            val energy = Tariffs.energyBill(quote, l.monthServedKwh)
            val fixed = if (l.connected) Tariffs.fixedCharge(g.tariff, l.baseKw) else Money.ZERO
            val bill = energy + fixed
            served += l.monthServedKwh
            shed += l.monthShedKwh
            if (l.customer == CustomerClass.HOUSEHOLD) shortfall = shortfall + Tariffs.capShortfall(quote, l.monthServedKwh)
            if (bill.raw > 0L) {
                billed = billed + bill
                fixedTotal = fixedTotal + fixed
                val payer = if (l.company != null) LedgerOwner.company(l.company) else g.householdOwner
                intents += SettlementIntent.simple(
                    "energy:bill:${g.id.value}:$month:${l.id.value}", payer, g.systemOwner, bill,
                    if (l.company != null) SettlementPriority.SUPPLIERS else SettlementPriority.OTHER, ReasonCode.ENERGY,
                    Chart.ENERGY_EXPENSE, Chart.REVENUE_SERVICES, deferrable = l.company != null,
                    memo = "${g.name} electricity, ${l.customer.label.lowercase()}",
                )
            }
            l.monthServedKwh = 0L
            l.monthShedKwh = 0L
        }

        var poolPaid = Money.ZERO
        for (u in g.unitMap.values) {
            val revenue = u.poolRevenue
            if (revenue.raw > 0L) {
                poolPaid = poolPaid + revenue
                intents += SettlementIntent.simple(
                    "energy:pool:${g.id.value}:$month:${u.id.value}", g.systemOwner, unitPayee(g, u), revenue, SettlementPriority.SUPPLIERS, ReasonCode.SALE,
                    Chart.COGS, Chart.REVENUE_SALES, deferrable = true, memo = "${u.name} sales to the ${g.name} pool",
                )
            } else if (revenue.raw < 0L) {
                poolPaid = poolPaid + revenue
                intents += SettlementIntent.simple(
                    "energy:pool:${g.id.value}:$month:${u.id.value}", unitPayee(g, u), g.systemOwner, -revenue, SettlementPriority.SUPPLIERS, ReasonCode.PURCHASE,
                    Chart.ENERGY_EXPENSE, Chart.REVENUE_SERVICES, deferrable = true, memo = "${u.name} purchases from the ${g.name} pool",
                )
            }
            u.soldKwh = 0L
            u.poolRevenue = Money.ZERO
        }

        var capacityPaid = Money.ZERO
        val round = g.capacityRound
        if (round != null && round.validUntil > tick.value) {
            for (a in round.awards) {
                val u = g.unit(a.unit) ?: continue
                if (!u.isLive) continue
                val pay = CapacityMarket.dailyPayment(a.kw, round.clearingPrice) * DAYS
                if (pay.raw <= 0L) continue
                capacityPaid = capacityPaid + pay
                intents += SettlementIntent.simple(
                    "energy:capacity:${g.id.value}:$month:${u.id.value}", g.systemOwner, unitPayee(g, u), pay, SettlementPriority.SUPPLIERS, ReasonCode.SUBSIDY,
                    Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, deferrable = true, memo = "capacity payment for ${u.name}",
                )
            }
        }

        var compensation = Money.ZERO
        if (shortfall.raw > 0L && g.params.compensateCap) {
            compensation = shortfall
            intents += SettlementIntent.simple(
                "energy:cap:${g.id.value}:$month", LedgerOwner.government(g.country), g.systemOwner, shortfall, SettlementPriority.STATUTORY, ReasonCode.SUBSIDY,
                Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, deferrable = true, memo = "household price cap on ${g.name}",
            )
        } else {
            g.capShortfall = g.capShortfall + shortfall
        }
        g.monthDemandKwh = 0L
        return GridStatement(g.id, billed, fixedTotal, poolPaid, capacityPaid, compensation, Money.ZERO, served, shed, wholesale)
    }

    private fun settlePpas(state: EnergyGridState, month: Long, tick: Tick, intents: MutableList<SettlementIntent>): Map<GridId, Money> {
        val net = java.util.TreeMap<GridId, Money>()
        for (p in state.ppaMap.values) {
            val hasAccrual = p.deliveredKwh > 0L || p.shortfallKwh > 0L
            if (hasAccrual) {
                val s = PpaSettler.settle(p)
                val seller = LedgerOwner.company(p.seller)
                val buyer = LedgerOwner.company(p.buyer)
                if (s.difference.raw > 0L) {
                    intents += SettlementIntent.simple(
                        "energy:ppa:${p.id.value}:$month", buyer, seller, s.difference, SettlementPriority.SUPPLIERS, ReasonCode.ENERGY,
                        Chart.ENERGY_EXPENSE, Chart.REVENUE_SALES, deferrable = true, memo = "power purchase agreement ${p.id.value} settlement",
                    )
                } else if (s.difference.raw < 0L) {
                    intents += SettlementIntent.simple(
                        "energy:ppa:${p.id.value}:$month", seller, buyer, -s.difference, SettlementPriority.SUPPLIERS, ReasonCode.ENERGY,
                        Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, deferrable = true, memo = "power purchase agreement ${p.id.value} settlement",
                    )
                }
                val penalties = s.penalty + s.replacementPremium
                if (penalties.raw > 0L) {
                    intents += SettlementIntent.simple(
                        "energy:ppa:pen:${p.id.value}:$month", seller, buyer, penalties, SettlementPriority.OTHER, ReasonCode.FEE,
                        Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, deferrable = true, memo = "shortfall penalty on power purchase agreement ${p.id.value}",
                    )
                }
                p.netPaidBySeller = p.netPaidBySeller + penalties - s.difference
                net[p.grid] = (net[p.grid] ?: Money.ZERO) + s.netToSeller
                p.clearAccrual()
            }
            if (p.isActive && p.endsAt <= tick.value) {
                p.status = PpaStatus.COMPLETED
                p.closedAt = tick.value
            }
        }
        return net
    }
}
