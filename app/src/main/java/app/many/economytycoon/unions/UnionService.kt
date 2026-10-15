package app.many.economytycoon.unions

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.UnionId
import app.many.economytycoon.core.mulDiv

/**
 * Keeps the unions, their wage demands and the rules for how a demand is answered. Changing wages and
 * starting strikes is left to the union runner.
 */
class UnionService(private val ids: IdAllocators, val params: UnionParams = UnionParams()) {
    private val unions = java.util.TreeMap<UnionId, Union>()
    private val demands = java.util.TreeMap<Long, Demand>()
    var demandSequence: Long = 0L
        internal set

    fun all(): List<Union> = unions.values.toList()
    fun active(): List<Union> = unions.values.filter { it.isActive }
    fun get(id: UnionId): Union? = unions[id]
    fun forGroup(company: CompanyId, region: RegionId): Union? = unions.values.firstOrNull { it.isActive && it.company == company && it.region == region }
    fun ofCompany(company: CompanyId): List<Union> = unions.values.filter { it.isActive && it.company == company }
    fun demand(id: Long): Demand? = demands[id]
    fun demands(): List<Demand> = demands.values.toList()
    fun demandsOf(union: UnionId): List<Demand> = demands.values.filter { it.union == union }
    fun openDemand(union: UnionId): Demand? = demands.values.firstOrNull { it.union == union && it.isOpen }
    fun openDemandsFor(company: CompanyId): List<Demand> = demands.values.filter { d -> d.isOpen && unions[d.union]?.company == company }

    // ---- organising ----------------------------------------------------------------------------

    /** Whether a group is unhappy enough to organise. */
    fun wantsUnion(avgMoralePpm: Long, wageRatioPpm: Long, members: Long): Boolean =
        members >= params.minMembers && (avgMoralePpm < params.formBelowMoralePpm || wageRatioPpm < params.formBelowWageRatioPpm)

    fun form(company: CompanyId, region: RegionId, members: Long, tick: Tick): Outcome<Union> {
        if (forGroup(company, region) != null) return Outcome.reject(Rules.DUPLICATE, "those workers are already organised")
        if (members < params.minMembers) return Outcome.reject(Rules.INVALID_QUANTITY, "too few workers to organise")
        val u = Union(ids.union(), company, region, tick)
        u.strengthPpm = params.startStrengthPpm; u.members = members
        unions[u.id] = u
        return Outcome.ok(u)
    }

    /** How strong the union should be given how the workers feel: unhappy workers rally, content ones drift away. */
    fun targetStrength(avgMoralePpm: Long, wageRatioPpm: Long): Long {
        val unhappy = (700_000L - avgMoralePpm).coerceIn(-300_000L, 700_000L)
        val underpaid = (1_000_000L - wageRatioPpm).coerceIn(-200_000L, 600_000L)
        return (400_000L + unhappy + underpaid / 2).coerceIn(0L, 1_000_000L)
    }

    /** Moves strength a tenth of the way to its target. */
    fun updateStrength(u: Union, target: Long, members: Long) {
        u.strengthPpm = (u.strengthPpm + (target - u.strengthPpm) / 10).coerceIn(0L, 1_000_000L)
        u.members = members
    }

    fun shouldDisband(u: Union): Boolean = u.strengthPpm < params.disbandBelowPpm

    fun disband(u: Union, tick: Tick) { u.disbandedAt = tick }

    // ---- demands -------------------------------------------------------------------------------

    /** What the union asks for: a base raise, more when it is strong and more when pay lags the market. */
    fun demandedRaise(strengthPpm: Long, wageRatioPpm: Long): Long {
        val lag = (1_000_000L - wageRatioPpm).coerceAtLeast(0L) / 2
        val raw = params.baseRaisePpm + mulDiv(params.strengthRaisePpm, strengthPpm, 1_000_000L) + lag / 4
        return raw.coerceIn(params.baseRaisePpm, params.maxRaisePpm)
    }

    fun isRoundDue(u: Union, tick: Tick): Boolean =
        u.isActive && !u.onStrike(tick) && openDemand(u.id) == null && tick.value - u.lastRound.value >= params.roundEvery

    fun makeDemand(u: Union, raisePpm: Long, tick: Tick): Demand {
        val d = Demand(++demandSequence, u.id, raisePpm, tick, Tick(tick.value + params.responseDays))
        demands[d.id] = d
        u.lastRound = tick
        return d
    }

    /** The share of the demand a counter-offer must reach for the union to take it. Strong unions hold out for more. */
    fun acceptableShare(strengthPpm: Long): Long = 400_000L + strengthPpm / 2

    fun strikeChance(strengthPpm: Long): Long = 100_000L + mulDiv(strengthPpm, 700_000L, 1_000_000L)

    fun strikeLength(random: DeterministicRandom): Int = params.strikeMinDays + random.nextInt(params.strikeSpanDays + 1)

    /** Checks an answer before it is carried out. */
    fun checkResponse(d: Demand, kind: ResponseKind, counterPpm: Long): app.many.economytycoon.core.Rejection? = when {
        !d.isOpen -> app.many.economytycoon.core.Rejection(Rules.WRONG_STATE, "that demand has already been answered")
        kind == ResponseKind.COUNTER && counterPpm <= 0L -> app.many.economytycoon.core.Rejection(Rules.INVALID_ARGUMENT, "a counter-offer must be a raise")
        kind == ResponseKind.COUNTER && counterPpm >= d.raisePpm -> app.many.economytycoon.core.Rejection(Rules.INVALID_ARGUMENT, "a counter-offer must be below the demand; accept instead")
        else -> null
    }

    fun accepted(d: Demand, u: Union, tick: Tick, ppm: Long, status: DemandStatus) {
        d.status = status; d.agreedPpm = ppm; d.resolvedAt = tick; u.raisesWon += 1
    }

    fun struck(d: Demand, u: Union, tick: Tick, days: Int, counterPpm: Long) {
        d.status = DemandStatus.STRUCK; d.strikeDays = days; d.counterPpm = counterPpm; d.resolvedAt = tick
        u.strikes += 1; u.strikeUntil = Tick(tick.value + days)
        u.strengthPpm = (u.strengthPpm + params.strikeStrengthGainPpm).coerceAtMost(1_000_000L)
    }

    fun backedDown(d: Demand, u: Union, tick: Tick, counterPpm: Long) {
        d.status = DemandStatus.BACKED_DOWN; d.counterPpm = counterPpm; d.resolvedAt = tick
        u.strengthPpm = (u.strengthPpm - params.backDownStrengthLossPpm).coerceAtLeast(0L)
    }

    fun clearStrike(u: Union) { u.strikeUntil = null }

    fun prune() {
        val done = demands.values.filter { !it.isOpen }.sortedBy { it.resolvedAt?.value ?: 0L }
        val surplus = done.size - params.historyLength * (unions.size.coerceAtLeast(1))
        if (surplus > 0) for (d in done.take(surplus)) demands.remove(d.id)
        val gone = unions.values.filter { !it.isActive }.sortedBy { it.disbandedAt?.value ?: 0L }
        val extra = gone.size - params.historyLength
        if (extra > 0) for (u in gone.take(extra)) { unions.remove(u.id); demands.values.removeAll { it.union == u.id } }
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(demandSequence)
        h.putInt(unions.size); for (u in unions.values) u.hashInto(h)
        h.putInt(demands.size); for (d in demands.values) d.hashInto(h)
    }

    internal fun restore(u: Union) { unions[u.id] = u }
    internal fun restore(d: Demand) { demands[d.id] = d }
    internal fun clearAll() { unions.clear(); demands.clear(); demandSequence = 0L }
}
