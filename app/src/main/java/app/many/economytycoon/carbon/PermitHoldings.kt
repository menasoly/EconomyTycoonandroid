package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv

/** Permits of one vintage held by one company, with what they cost. */
class PermitLot(tonnes: Tonnes, cost: Money) {
    var tonnes: Tonnes = tonnes
        internal set
    var cost: Money = cost
        internal set
}

/** A read-only view of a lot. */
data class LotView(val vintage: Int, val tonnes: Tonnes, val cost: Money)

/** What a withdrawal took: how much, what it had cost, and from which vintages. */
class Withdrawal(val taken: Tonnes, val cost: Money, val parts: List<LotView>) {
    companion object {
        val EMPTY = Withdrawal(Tonnes.ZERO, Money.ZERO, emptyList())
    }
}

/** A lot removed because its vintage is no longer valid. */
data class ExpiredLot(val company: CompanyId, val vintage: Int, val tonnes: Tonnes, val cost: Money)

/**
 * Who holds which permits. A permit belongs to the year it was issued for (its vintage). Permits are taken out oldest
 * vintage first, and a lot keeps the money it cost so that the books can show a gain or a loss when it is sold and
 * the right expense when it is surrendered.
 */
class PermitHoldings {
    private val lots = java.util.TreeMap<CompanyId, java.util.TreeMap<Int, PermitLot>>()

    fun companies(): List<CompanyId> = lots.keys.toList()

    fun lotsOf(company: CompanyId): List<LotView> =
        lots[company]?.map { (v, l) -> LotView(v, l.tonnes, l.cost) } ?: emptyList()

    fun total(company: CompanyId): Tonnes = Tonnes.sum((lots[company] ?: emptyMap<Int, PermitLot>()).values.map { it.tonnes })

    fun totalCost(company: CompanyId): Money = Money.sum((lots[company] ?: emptyMap<Int, PermitLot>()).values.map { it.cost })

    /** Permits of vintages in [minVintage]..[maxVintage]. */
    fun held(company: CompanyId, minVintage: Int, maxVintage: Int): Tonnes =
        Tonnes.sum((lots[company] ?: emptyMap<Int, PermitLot>()).filterKeys { it in minVintage..maxVintage }.values.map { it.tonnes })

    fun totalAll(): Tonnes = Tonnes.sum(lots.values.flatMap { m -> m.values.map { it.tonnes } })

    fun add(company: CompanyId, vintage: Int, tonnes: Tonnes, cost: Money) {
        if (tonnes.raw <= 0L) return
        val lot = lots.getOrPut(company) { java.util.TreeMap() }.getOrPut(vintage) { PermitLot(Tonnes.ZERO, Money.ZERO) }
        lot.tonnes += tonnes
        lot.cost += cost
    }

    /** Takes up to [wanted] from vintages in the range, oldest first, with cost in proportion to the part taken. */
    fun take(company: CompanyId, wanted: Tonnes, minVintage: Int = Int.MIN_VALUE, maxVintage: Int = Int.MAX_VALUE): Withdrawal {
        if (wanted.raw <= 0L) return Withdrawal.EMPTY
        val mine = lots[company] ?: return Withdrawal.EMPTY
        var left = wanted
        var cost = Money.ZERO
        val parts = ArrayList<LotView>()
        val iter = mine.entries.iterator()
        while (iter.hasNext() && left.raw > 0L) {
            val (vintage, lot) = iter.next()
            if (vintage < minVintage || vintage > maxVintage) continue
            val part = Tonnes.min(left, lot.tonnes)
            val partCost = if (part == lot.tonnes) lot.cost else Money(mulDiv(lot.cost.raw, part.raw, lot.tonnes.raw, RoundingMode.HALF_EVEN))
            lot.tonnes -= part
            lot.cost -= partCost
            left -= part
            cost += partCost
            parts += LotView(vintage, part, partCost)
            if (lot.tonnes.raw == 0L) iter.remove()
        }
        if (mine.isEmpty()) lots.remove(company)
        return Withdrawal(wanted - left, cost, parts)
    }

    /** Removes every lot older than [oldestValid] and reports what was lost. */
    fun expire(oldestValid: Int): List<ExpiredLot> {
        val out = ArrayList<ExpiredLot>()
        val companies = lots.entries.iterator()
        while (companies.hasNext()) {
            val (company, mine) = companies.next()
            val old = mine.headMap(oldestValid)
            for ((v, l) in old) out += ExpiredLot(company, v, l.tonnes, l.cost)
            old.clear()
            if (mine.isEmpty()) companies.remove()
        }
        return out
    }

    /** Empties the book; used when a save is loaded. */
    internal fun clear() { lots.clear() }

    internal fun putLot(company: CompanyId, vintage: Int, tonnes: Tonnes, cost: Money) {
        lots.getOrPut(company) { java.util.TreeMap() }[vintage] = PermitLot(tonnes, cost)
    }
}
