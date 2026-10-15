package app.many.economytycoon.workforce

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CohortId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Tick

/** Identity of a cohort: who employs them, where, doing what. FacilityId 0 means company-level staff. */
data class CohortKey(
    val company: CompanyId,
    val facility: FacilityId,
    val role: RoleId,
    val region: RegionId,
) : Comparable<CohortKey> {
    override fun compareTo(other: CohortKey): Int {
        var c = company.compareTo(other.company); if (c != 0) return c
        c = facility.compareTo(other.facility); if (c != 0) return c
        c = role.compareTo(other.role); if (c != 0) return c
        return region.compareTo(other.region)
    }
}

data class PendingHire(val arrival: Tick, val count: Long)

/** A group of identical employees tracked in aggregate. */
class Cohort(val id: CohortId, val key: CohortKey, var wage: Money) {
    var headcount: Long = 0
        internal set
    /** Desired headcount; hiring and layoffs move toward it. */
    var target: Long = 0
        internal set
    /** Average skill 0..1,000,000 within the role's tier. */
    var skillPpm: Long = 400_000
        internal set
    var moralePpm: Long = 650_000
        internal set
    var burnoutPpm: Long = 0
        internal set
    var tenureTicks: Long = 0
        internal set
    /** Share of the cohort in training, ppm. */
    var trainingPpm: Long = 0
        internal set
    var strikeUntil: Long = -1
        internal set
    var unionized: Boolean = false
        internal set
    /** Utilization from the production side: >1,000,000 means overtime. */
    var loadPpm: Long = 1_000_000
        internal set
    var accruedWages: Money = Money.ZERO
        internal set

    internal val pending = ArrayList<PendingHire>()
    internal val carry = LongArray(3)

    val pendingCount: Long get() = pending.sumOf { it.count }
    val openings: Long get() = (target - headcount - pendingCount).coerceAtLeast(0)
    fun onStrike(tick: Tick) = tick.value < strikeUntil

    internal fun restore(
        headcount: Long, target: Long, skill: Long, morale: Long, burnout: Long, tenure: Long, training: Long,
        strikeUntil: Long, union: Boolean, load: Long, accrued: Money, pending: List<PendingHire>, carries: LongArray,
    ) {
        this.headcount = headcount; this.target = target; skillPpm = skill; moralePpm = morale; burnoutPpm = burnout
        tenureTicks = tenure; trainingPpm = training; this.strikeUntil = strikeUntil; unionized = union; loadPpm = load
        accruedWages = accrued; this.pending.clear(); this.pending.addAll(pending)
        for (i in carries.indices.take(carry.size)) carry[i] = carries[i]
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(headcount).putLong(target).putLong(skillPpm).putLong(moralePpm).putLong(burnoutPpm)
            .putLong(tenureTicks).putLong(strikeUntil).putMoney(wage).putMoney(accruedWages)
        for (p in pending) h.putLong(p.arrival.value).putLong(p.count)
        for (c in carry) h.putLong(c)
    }
}
