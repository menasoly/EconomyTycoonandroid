package app.many.economytycoon.world

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DepositId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.mulDiv

enum class DepositKind { FINITE, RENEWABLE }

/**
 * A natural resource body. Finite deposits deplete as they are worked and become harder to extract;
 * renewable ones regrow toward their capacity.
 */
class Deposit(
    val id: DepositId,
    val region: RegionId,
    val location: Point,
    val good: GoodId,
    val kind: DepositKind,
    val initialReserve: Quantity,
    /** 1,000,000 means a normal grade ore; lower is poorer. */
    val gradePpm: Long,
    /** Share of the reserve that regrows per tick for renewable deposits. */
    val regrowthPpm: Long = 0,
) {
    var remaining: Quantity = initialReserve
        internal set
    var surveyed: Boolean = false
        internal set
    var owner: CompanyId? = null
        internal set

    val depletionPpm: Long
        get() = if (initialReserve.raw == 0L) Rate.ONE_PPM else Rate.ONE_PPM - mulDiv(remaining.raw, Rate.ONE_PPM, initialReserve.raw)

    /** Yield multiplier from 0..1 ppm: easy ore first, the last third gets progressively harder. */
    val difficultyFactorPpm: Long
        get() {
            val left = Rate.ONE_PPM - depletionPpm
            return if (left >= 400_000) Rate.ONE_PPM else 300_000 + mulDiv(left, 700_000, 400_000)
        }

    val exhausted: Boolean get() = remaining.raw <= 0

    /** Removes up to [requested] units and returns the amount obtained, reduced by difficulty. */
    fun extract(requested: Quantity): Quantity {
        if (requested.raw <= 0 || exhausted) return Quantity.ZERO
        val capped = Quantity.min(requested, remaining)
        val obtained = Quantity(mulDiv(capped.raw, difficultyFactorPpm, Rate.ONE_PPM))
        remaining -= capped
        return obtained
    }

    /** Applies one tick of regrowth; returns the quantity restored. */
    fun regrow(): Quantity {
        if (kind != DepositKind.RENEWABLE || regrowthPpm == 0L) return Quantity.ZERO
        val room = initialReserve - remaining
        if (room.raw <= 0) return Quantity.ZERO
        val growth = Quantity.min(room, Quantity(mulDiv(initialReserve.raw, regrowthPpm, Rate.ONE_PPM)))
        remaining += growth
        return growth
    }

    internal fun restore(remaining: Quantity, surveyed: Boolean, owner: CompanyId?) {
        this.remaining = remaining; this.surveyed = surveyed; this.owner = owner
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putQuantity(remaining).putBoolean(surveyed).putLong(owner?.value ?: -1L)
    }
}

class DepositRegistry {
    private val deposits = java.util.TreeMap<DepositId, Deposit>()

    fun add(d: Deposit) { deposits[d.id] = d }
    fun get(id: DepositId): Deposit? = deposits[id]
    fun all(): List<Deposit> = deposits.values.toList()
    fun inRegion(region: RegionId): List<Deposit> = deposits.values.filter { it.region == region }
    fun ofGood(good: GoodId): List<Deposit> = deposits.values.filter { it.good == good }
    fun unclaimed(region: RegionId): List<Deposit> = inRegion(region).filter { it.owner == null && !it.exhausted }
    fun ownedBy(company: CompanyId): List<Deposit> = deposits.values.filter { it.owner == company }

    fun survey(id: DepositId): Boolean {
        val d = deposits[id] ?: return false
        if (d.surveyed) return false
        d.surveyed = true
        return true
    }

    fun claim(id: DepositId, company: CompanyId): Boolean {
        val d = deposits[id] ?: return false
        if (d.owner != null || !d.surveyed || d.exhausted) return false
        d.owner = company
        return true
    }

    fun release(id: DepositId) { deposits[id]?.owner = null }

    /** Known reserves of a good visible to the public (surveyed deposits only). */
    fun knownReserve(good: GoodId): Quantity = Quantity.sum(ofGood(good).filter { it.surveyed }.map { it.remaining })

    fun regrowAll() { for (d in deposits.values) d.regrow() }
}
