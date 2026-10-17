package app.many.economytycoon.simulation

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.markets.PartyKind
import app.many.economytycoon.markets.Side

/**
 * Checks that span modules. Each returns a list of problems, empty when the economy is consistent, and
 * every message starts with a stable code so tests and logs can match on it.
 */
object Invariants {
    fun check(e: Economy): List<String> {
        val out = ArrayList<String>()
        out += e.finance.invariants().map { "FIN: $it" }
        out += reservations(e)
        out += inventoryBooks(e)
        out += population(e)
        out += app.many.economytycoon.marketing.MarketingInvariants.check(e.marketing.state).map { "MKT: $it" }
        out += banks(e)
        if (e.talentActive) out += app.many.economytycoon.talent.TalentInvariants.violations(e.talent.state, e.talent.taxonomy, null, e.tick).map { "TAL: $it" }
        return out
    }

    /** SIM-04: every bank's books balance, and what banks lent each other in a country equals what they borrowed. */
    fun banks(e: Economy): List<String> {
        val out = ArrayList<String>()
        val banks = e.banking.banks()
        for (b in banks) if (b.imbalance != Money.ZERO) out += "SIM-04: bank ${b.name} books are out of balance by ${b.imbalance}"
        for ((country, group) in banks.groupBy { it.country }) {
            val lent = Money.sum(group.map { it.interbankLent })
            val borrowed = Money.sum(group.map { it.interbankBorrowed })
            if (lent != borrowed) out += "SIM-04: interbank lending $lent differs from borrowing $borrowed in country ${country.value}"
        }
        return out
    }

    /** SIM-01: every unit reserved in a facility is covered by an open sell order, and the other way round. */
    fun reservations(e: Economy): List<String> {
        val out = ArrayList<String>()
        val byFacility = java.util.TreeMap<Pair<Long, String>, Long>(compareBy({ it.first }, { it.second }))
        for (o in e.markets.allOrders()) {
            if (o.side != Side.SELL || o.party.kind != PartyKind.COMPANY) continue
            byFacility.merge(o.source to o.key.good.key, o.remaining.raw, Long::plus)
        }
        for (f in e.production.facilities()) {
            for ((good, slot) in f.inventory.entries()) {
                val ordered = byFacility.remove(f.id.value to good.key) ?: 0L
                // Manual reservations (shipments being prepared) may exceed orders, never the reverse.
                if (ordered > slot.reserved.raw) out += "SIM-01: facility ${f.id.value} has orders for $ordered of ${good.key} but only ${slot.reserved.raw} reserved"
            }
        }
        for ((k, v) in byFacility) if (v > 0L) out += "SIM-01: sell orders for ${k.second} reference missing facility ${k.first}"
        return out
    }

    /** SIM-02: the ledger's inventory account equals the value of goods the company physically holds. */
    fun inventoryBooks(e: Economy, tolerance: Money = Money.units(1)): List<String> {
        val out = ArrayList<String>()
        for (c in e.companies.all()) {
            val owner = LedgerOwner.company(c.id)
            val book = e.finance.ledger.balance(owner, Chart.INVENTORY)
            val physical = Reconciler.physicalInventory(e, c.id)
            val gap = (book - physical).raw
            if (gap > tolerance.raw || gap < -tolerance.raw) out += "SIM-02: ${c.name} books inventory at $book but holds $physical"
        }
        return out
    }

    /** SIM-03: nobody has negative headcount or unemployment. */
    fun population(e: Economy): List<String> {
        val out = ArrayList<String>()
        for (p in e.world.populations()) {
            if (p.total < 0 || p.employed < 0 || p.unemployed < 0) out += "SIM-03: city ${p.city.value} has negative counts"
        }
        return out
    }

    @Suppress("unused") private fun zero(): Pair<Money, Quantity> = Money.ZERO to Quantity.ZERO
}

/** Brings the ledger's inventory account in line with the goods companies actually hold. */
object Reconciler {
    fun physicalInventory(e: Economy, company: app.many.economytycoon.core.CompanyId): Money =
        e.production.inventoryValue(company) + e.logistics.warehouseValue(company) + e.logistics.inTransitValue(company)

    /** Posts one adjustment per company whose books differ from its stock. Returns the net change. */
    fun run(e: Economy, tick: app.many.economytycoon.core.Tick): Money {
        var net = Money.ZERO
        for (c in e.companies.all()) {
            val owner = LedgerOwner.company(c.id)
            val book = e.finance.ledger.balance(owner, Chart.INVENTORY)
            val gap = Reconciler.physicalInventory(e, c.id) - book
            if (gap.isZero) continue
            if (e.finance.adjustInventory("recon:${c.id.value}:${tick.value}", tick, owner, gap) is app.many.economytycoon.core.Outcome.Ok) net += gap
        }
        return net
    }
}
