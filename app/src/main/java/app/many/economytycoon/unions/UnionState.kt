package app.many.economytycoon.unions

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.UnionId

data class UnionParams(
    /** Days between looks at whether workers organise, and at how strong each union is. */
    val reviewEvery: Int = 30,
    /** A group needs this many people before it can organise. */
    val minMembers: Long = 15L,
    /** Average morale below this (ppm) makes organising likely; so does pay this far under the market. */
    val formBelowMoralePpm: Long = 500_000L,
    val formBelowWageRatioPpm: Long = 900_000L,
    /** Chance per review that an unhappy group organises. */
    val formChancePpm: Long = 350_000L,
    val startStrengthPpm: Long = 300_000L,
    /** A union that falls below this strength dissolves. */
    val disbandBelowPpm: Long = 100_000L,
    /** Days between wage rounds, and the days a company has to answer a demand. */
    val roundEvery: Int = 90,
    val responseDays: Int = 10,
    val baseRaisePpm: Long = 20_000L,
    val strengthRaisePpm: Long = 60_000L,
    val maxRaisePpm: Long = 150_000L,
    val strikeMinDays: Int = 6,
    val strikeSpanDays: Int = 16,
    /** After a deal, morale rises by this much; after a strike, strength rises. */
    val dealMoraleBoostPpm: Long = 60_000L,
    val strikeStrengthGainPpm: Long = 80_000L,
    val backDownStrengthLossPpm: Long = 100_000L,
    val historyLength: Int = 30,
)

enum class DemandStatus { OPEN, ACCEPTED, SETTLED, STRUCK, BACKED_DOWN }

enum class ResponseKind { ACCEPT, COUNTER, REFUSE }

/** The workers of one company in one region, organised. */
class Union(val id: UnionId, val company: CompanyId, val region: RegionId, val formedAt: Tick) {
    var strengthPpm: Long = 0L
        internal set
    var members: Long = 0L
        internal set
    var lastRound: Tick = formedAt
        internal set
    var strikeUntil: Tick? = null
        internal set
    var strikes: Int = 0
        internal set
    var raisesWon: Int = 0
        internal set
    var disbandedAt: Tick? = null
        internal set

    val isActive get() = disbandedAt == null
    fun onStrike(tick: Tick) = strikeUntil?.let { tick < it } ?: false

    internal fun restore(strength: Long, members: Long, lastRound: Tick, strikeUntil: Tick?, strikes: Int, raises: Int, disbandedAt: Tick?) {
        strengthPpm = strength; this.members = members; this.lastRound = lastRound; this.strikeUntil = strikeUntil
        this.strikes = strikes; raisesWon = raises; this.disbandedAt = disbandedAt
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(company.value).putLong(region.value).putLong(formedAt.value).putLong(strengthPpm).putLong(members)
        h.putLong(lastRound.value).putLong(strikeUntil?.value ?: -1L).putInt(strikes).putInt(raisesWon).putLong(disbandedAt?.value ?: -1L)
    }
}

/** What the union asks for in a wage round, and how it was answered. */
class Demand(val id: Long, val union: UnionId, val raisePpm: Long, val issuedAt: Tick, val deadline: Tick) {
    var status: DemandStatus = DemandStatus.OPEN
        internal set
    var agreedPpm: Long = 0L
        internal set
    var counterPpm: Long = 0L
        internal set
    var strikeDays: Int = 0
        internal set
    var resolvedAt: Tick? = null
        internal set

    val isOpen get() = status == DemandStatus.OPEN

    internal fun restore(status: DemandStatus, agreed: Long, counter: Long, strikeDays: Int, resolvedAt: Tick?) {
        this.status = status; agreedPpm = agreed; counterPpm = counter; this.strikeDays = strikeDays; this.resolvedAt = resolvedAt
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id).putLong(union.value).putLong(raisePpm).putLong(issuedAt.value).putLong(deadline.value).putInt(status.ordinal)
        h.putLong(agreedPpm).putLong(counterPpm).putInt(strikeDays).putLong(resolvedAt?.value ?: -1L)
    }
}

sealed class UnionEvent {
    abstract val union: UnionId
    data class Formed(override val union: UnionId, val company: CompanyId, val tick: Tick) : UnionEvent()
    data class Disbanded(override val union: UnionId, val company: CompanyId, val tick: Tick) : UnionEvent()
    data class Demanded(override val union: UnionId, val demand: Long, val tick: Tick) : UnionEvent()
    data class Agreed(override val union: UnionId, val demand: Long, val raisePpm: Long, val tick: Tick) : UnionEvent()
    data class Struck(override val union: UnionId, val demand: Long, val days: Int, val tick: Tick) : UnionEvent()
    data class BackedDown(override val union: UnionId, val demand: Long, val tick: Tick) : UnionEvent()
    data class StrikeEnded(override val union: UnionId, val tick: Tick) : UnionEvent()
}
