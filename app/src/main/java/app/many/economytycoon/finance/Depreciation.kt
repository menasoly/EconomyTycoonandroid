package app.many.economytycoon.finance

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

enum class DepreciationMethod { STRAIGHT_LINE, DECLINING_BALANCE }

/** How a class of assets loses book value. */
data class AssetClass(
    val id: String,
    val usefulLifeTicks: Long,
    val method: DepreciationMethod,
    /** Share of cost left after the useful life. */
    val salvage: Rate = Rate.percent(5),
    /** Multiple of straight-line rate used by declining balance, in ppm (2,000,000 = double declining). */
    val decliningFactorPpm: Long = 2_000_000,
)

object AssetClasses {
    val BUILDING = AssetClass("building", 360L * 40, DepreciationMethod.STRAIGHT_LINE, Rate.percent(10))
    val MACHINERY = AssetClass("machinery", 360L * 12, DepreciationMethod.DECLINING_BALANCE, Rate.percent(8))
    val VEHICLES = AssetClass("vehicles", 360L * 8, DepreciationMethod.DECLINING_BALANCE, Rate.percent(10))
    val EQUIPMENT = AssetClass("equipment", 360L * 6, DepreciationMethod.STRAIGHT_LINE, Rate.percent(5))
    val SOFTWARE = AssetClass("software", 360L * 4, DepreciationMethod.STRAIGHT_LINE, Rate.ZERO)
    val INFRASTRUCTURE = AssetClass("infrastructure", 360L * 30, DepreciationMethod.STRAIGHT_LINE, Rate.percent(5))
    val ALL = listOf(BUILDING, MACHINERY, VEHICLES, EQUIPMENT, SOFTWARE, INFRASTRUCTURE)
    fun byId(id: String): AssetClass = ALL.first { it.id == id }
}

class FixedAsset(
    val id: Long,
    val owner: LedgerOwner,
    val assetClass: AssetClass,
    val label: String,
    val cost: Money,
    val acquired: Tick,
) {
    var accumulated: Money = Money.ZERO
        internal set
    var disposed: Boolean = false
        internal set
    var impairment: Money = Money.ZERO
        internal set

    val salvageValue: Money get() = cost.times(assetClass.salvage)
    val depreciableBase: Money get() = cost - salvageValue
    val bookValue: Money get() = cost - accumulated - impairment
    val fullyDepreciated: Boolean get() = bookValue <= salvageValue

    /** Depreciation for [ticks] ticks of use, capped so book value never drops below salvage. */
    fun depreciationFor(ticks: Long): Money {
        if (disposed || ticks <= 0) return Money.ZERO
        val room = bookValue - salvageValue
        if (room.raw <= 0) return Money.ZERO
        val charge = when (assetClass.method) {
            DepreciationMethod.STRAIGHT_LINE ->
                Money(mulDiv(depreciableBase.raw, ticks, assetClass.usefulLifeTicks))
            DepreciationMethod.DECLINING_BALANCE -> {
                // annual rate = factor / life in years, applied to the current book value per tick.
                val perTickPpm = mulDiv(assetClass.decliningFactorPpm, 1L, assetClass.usefulLifeTicks)
                Money(mulDiv(bookValue.raw, perTickPpm * ticks, Rate.ONE_PPM))
            }
        }
        return Money.min(charge, room)
    }
}

/** Register of fixed assets. Depreciation is posted at period close by the finance service. */
class AssetRegister {
    private val assets = java.util.TreeMap<Long, FixedAsset>()
    private var nextId = 1L

    fun all(): List<FixedAsset> = assets.values.toList()
    fun of(owner: LedgerOwner): List<FixedAsset> = assets.values.filter { it.owner == owner && !it.disposed }
    fun get(id: Long): FixedAsset? = assets[id]
    fun nextIdValue(): Long = nextId

    fun add(owner: LedgerOwner, cls: AssetClass, label: String, cost: Money, tick: Tick): FixedAsset {
        require(cost.raw > 0) { "asset cost must be positive" }
        val a = FixedAsset(nextId++, owner, cls, label, cost, tick)
        assets[a.id] = a
        return a
    }

    fun totalCost(owner: LedgerOwner): Money = Money.sum(of(owner).map { it.cost })
    fun totalBookValue(owner: LedgerOwner): Money = Money.sum(of(owner).map { it.bookValue })

    /** Depreciation charge for [owner] over [ticks], applied to each asset. */
    fun depreciate(owner: LedgerOwner, ticks: Long): Money {
        var total = Money.ZERO
        for (a in of(owner)) {
            val charge = a.depreciationFor(ticks)
            a.accumulated += charge
            total += charge
        }
        return total
    }

    fun impair(id: Long, amount: Money) {
        val a = assets[id] ?: return
        val room = a.bookValue - Money.ZERO
        a.impairment += Money.min(amount, room)
    }

    internal fun restore(list: List<FixedAsset>, next: Long) {
        assets.clear(); for (a in list) assets[a.id] = a; nextId = next
    }

    internal fun dispose(id: Long) { assets[id]?.disposed = true }
}
