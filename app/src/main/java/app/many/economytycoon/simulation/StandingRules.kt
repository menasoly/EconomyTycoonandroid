package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.companies.RulePlanner
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.OwnerKind
import app.many.economytycoon.markets.MarketKey
import app.many.economytycoon.markets.Party
import app.many.economytycoon.markets.Side
import app.many.economytycoon.production.FacilityStatus

class RuleReport {
    var sellOrders: Int = 0
    var buyOrders: Int = 0
    var cancelledStale: Int = 0
    var dividendsPaid: Money = Money.ZERO
}

/**
 * Carries out the selling, buying and dividend rules companies have set. Rule orders live for a single
 * tick: stale ones are pulled before planning and replaced from current stock, prices and cash.
 */
class StandingRules(private val e: Economy) {
    /** Pulls orders that would expire in the coming clearing, releasing the stock they reserved. */
    fun expireStale(tick: Tick): Int {
        var n = 0
        for (o in e.markets.allOrders().filter { it.expires <= tick }) {
            val r = e.markets.cancelOrder(o.party, o.id)
            if (r is app.many.economytycoon.core.Outcome.Ok) { e.releaseReservation(o); n++ }
        }
        return n
    }

    fun plan(tick: Tick): RuleReport {
        val report = RuleReport()
        report.cancelledStale = expireStale(tick)
        for (c in e.companies.active().sortedBy { it.id }) {
            if (c.status != CompanyStatus.ACTIVE) continue
            planSells(c.id, report)
            planBuys(c.id, report)
        }
        return report
    }

    private fun premium(company: CompanyId): Long =
        e.companies.brands.brandsOf(company).maxOfOrNull { it.second.premiumPpm } ?: 0L

    private fun planSells(company: CompanyId, report: RuleReport) {
        val party = Party.company(company)
        for (rule in e.companies.policies.sellRules(company)) {
            val f = e.production.facility(rule.facility) ?: continue
            if (f.owner != company || f.status == FacilityStatus.DEMOLISHED) continue
            val key = MarketKey(f.region, rule.good)
            val plan = RulePlanner.sellPlan(
                rule, f.inventory.available(rule.good), f.inventory.unitCost(rule.good), e.markets.price(key),
                e.marketingRunner.premium(company, rule.good, f.region, premium(company)),
            ) ?: continue
            if (f.inventory.reserve(rule.good, plan.quantity) is app.many.economytycoon.core.Outcome.Rejected) continue
            val shelf = e.marketingRunner.shelfPrice(company, rule.good, f.region, e.tick, plan.limit)
            val placed = e.markets.placeOrder(party, key, Side.SELL, shelf, plan.quantity, 1, f.id.value, e.tick)
            if (placed is app.many.economytycoon.core.Outcome.Rejected) f.inventory.release(rule.good, plan.quantity) else report.sellOrders++
        }
    }

    private fun onOrder(company: CompanyId, facility: FacilityId, good: GoodId): Quantity {
        var total = 0L
        for (o in e.markets.openOrders(Party.company(company))) {
            if (o.side == Side.BUY && o.source == facility.value && o.key.good == good) total += o.remaining.raw
        }
        return Quantity(total)
    }

    private fun planBuys(company: CompanyId, report: RuleReport) {
        val party = Party.company(company)
        var budget = e.finance.ledger.cash(LedgerOwner.company(company))
        for (rule in e.companies.policies.buyRules(company)) {
            if (budget.raw <= 0L) break
            val f = e.production.facility(rule.facility) ?: continue
            if (f.owner != company || f.status == FacilityStatus.DEMOLISHED) continue
            val key = MarketKey(f.region, rule.good)
            val plan = RulePlanner.buyPlan(rule, f.inventory.quantity(rule.good), onOrder(company, f.id, rule.good), e.markets.price(key)) ?: continue
            // Never order more than the facility can store or the company can pay for at the limit.
            var qty = Quantity.min(plan.quantity, f.inventory.room(rule.good))
            val affordable = Quantity((app.many.economytycoon.core.mulDiv(budget.raw, Quantity.SCALE, plan.limit.perUnit.raw.coerceAtLeast(1L)) - 1L).coerceAtLeast(0L))
            qty = Quantity.min(qty, affordable)
            if (qty.raw <= 0L) continue
            val placed = e.markets.placeOrder(party, key, Side.BUY, plan.limit, qty, 1, f.id.value, e.tick)
            if (placed is app.many.economytycoon.core.Outcome.Ok) {
                report.buyOrders++
                budget -= plan.limit.valueOf(qty)
            }
        }
    }

    /** Pays dividends on each company's own schedule. */
    fun payDividends(tick: Tick): Money {
        var total = Money.ZERO
        for (c in e.companies.active().sortedBy { it.id }) {
            if (c.status != CompanyStatus.ACTIVE) continue
            val policy = e.companies.policies.dividend(c.id)
            if (policy.payoutPpm == 0L || tick.value == 0L || (tick.value + c.id.value) % policy.intervalTicks != 0L) continue
            val owner = c.owner
            val from = Tick((tick.value - policy.intervalTicks).coerceAtLeast(0L))
            val income = e.finance.statements.income(owner, from, tick)
            val ebitdaLike = income.revenue - income.cogs - income.wages - income.energy - income.rent - income.maintenance -
                income.transport - income.marketing - income.research - income.waste - income.otherExpenses
            val opCost = Money(((income.revenue - ebitdaLike).raw.coerceAtLeast(0L)) / policy.intervalTicks)
            val cash = e.finance.ledger.cash(owner)
            val cap = e.finance.equity.maxDividend(owner, e.finance.statements, Tick(tick.value - 1), Money(opCost.raw * policy.reserveTicks))
            val amount = RulePlanner.dividend(policy, cap, cash, opCost)
            if (amount.raw <= 0L) continue
            val paid = e.finance.equity.payDividend(owner, amount, tick, e.finance.statements, Money(opCost.raw * policy.reserveTicks)) { holder ->
                if (holder.kind == "owner") LedgerOwner(OwnerKind.COMPANY, holder.id) else null
            }
            if (paid is app.many.economytycoon.core.Outcome.Ok) total += amount
        }
        return total
    }
}
