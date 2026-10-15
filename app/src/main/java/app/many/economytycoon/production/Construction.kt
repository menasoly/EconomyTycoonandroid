package app.many.economytycoon.production

import app.many.economytycoon.content.GoodAmount
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DepositId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.core.ProjectId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

enum class ProjectKind { BUILD, EXPAND_STORAGE }

enum class ProjectState { ACTIVE, WAITING_FUNDS, WAITING_MATERIALS, DONE, CANCELLED }

/**
 * A building or upgrade under construction. Cost is paid in equal installments over the build time and
 * the material inputs must be on site before the last stage can finish.
 */
class ConstructionProject(
    val id: ProjectId,
    val kind: ProjectKind,
    val owner: CompanyId,
    val facilityType: FacilityTypeId,
    val region: RegionId,
    val city: CityId?,
    val parcel: ParcelId?,
    val deposit: DepositId?,
    /** Facility being upgraded; null for new builds. */
    val target: FacilityId?,
    val started: Tick,
    val totalTicks: Int,
    val totalCost: Money,
    val required: List<GoodAmount>,
    val name: String,
) {
    var ticksDone: Int = 0
        internal set
    var paid: Money = Money.ZERO
        internal set
    var state: ProjectState = ProjectState.ACTIVE
        internal set
    val delivered = java.util.TreeMap<GoodId, Quantity>()

    /** Cost due for the stage that is about to run; installments differ by at most one raw unit. */
    fun nextInstallment(): Money {
        if (ticksDone >= totalTicks) return Money.ZERO
        val upTo = mulDiv(totalCost.raw, (ticksDone + 1).toLong(), totalTicks.toLong())
        val before = mulDiv(totalCost.raw, ticksDone.toLong(), totalTicks.toLong())
        return Money(upTo - before)
    }

    fun missing(good: GoodId): Quantity {
        val need = required.firstOrNull { it.good == good }?.quantity ?: return Quantity.ZERO
        return (need - (delivered[good] ?: Quantity.ZERO)).coerceAtLeast(Quantity.ZERO)
    }

    fun materialsComplete(): Boolean = required.all { (delivered[it.good] ?: Quantity.ZERO) >= it.quantity }

    /** Accepts up to the outstanding need and returns the quantity taken. */
    internal fun deliver(good: GoodId, quantity: Quantity): Quantity {
        val take = Quantity.min(quantity, missing(good))
        if (take.raw > 0) delivered[good] = (delivered[good] ?: Quantity.ZERO) + take
        return take
    }

    val progressPpm: Long get() = mulDiv(ticksDone.toLong(), 1_000_000L, totalTicks.toLong())
    val isOpen: Boolean get() = state != ProjectState.DONE && state != ProjectState.CANCELLED

    internal fun restore(ticksDone: Int, paid: Money, state: ProjectState) { this.ticksDone = ticksDone; this.paid = paid; this.state = state }

    internal fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putInt(kind.ordinal).putLong(owner.value).putString(facilityType.key).putInt(ticksDone).putMoney(paid).putInt(state.ordinal)
        for ((g, q) in delivered) h.putString(g.key).putQuantity(q)
    }
}
