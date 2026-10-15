package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.unions.Demand
import app.many.economytycoon.unions.DemandStatus
import app.many.economytycoon.unions.ResponseKind
import app.many.economytycoon.unions.Union
import app.many.economytycoon.unions.UnionEvent
import app.many.economytycoon.workforce.Cohort

/**
 * Runs labour relations: unhappy workers organise, unions grow or fade with the mood of their members,
 * make a wage demand every quarter and call a strike if a company turns them down.
 */
class UnionRunner(private val e: Economy) {
    /** How the workers of one company in one region are doing. */
    data class GroupStats(val members: Long, val moralePpm: Long, val wageRatioPpm: Long, val payroll: Money)

    fun cohortsOf(company: CompanyId, region: RegionId): List<Cohort> =
        e.workforce.cohortsOf(company).filter { it.key.region == region && it.headcount > 0L }

    fun stats(company: CompanyId, region: RegionId): GroupStats? {
        val cs = cohortsOf(company, region)
        val heads = cs.sumOf { it.headcount }
        if (heads <= 0L) return null
        val morale = cs.sumOf { it.moralePpm * it.headcount } / heads
        val ratio = cs.sumOf { e.workforce.wageRatio(it) * it.headcount } / heads
        val payroll = cs.fold(Money.ZERO) { a, c -> a + Money(c.wage.raw * c.headcount) }
        return GroupStats(heads, morale, ratio, payroll)
    }

    fun run(tick: Tick): List<UnionEvent> {
        val out = ArrayList<UnionEvent>()
        val svc = e.unions
        for (u in svc.active()) {
            val until = u.strikeUntil
            if (until != null && tick >= until) {
                svc.clearStrike(u)
                out += UnionEvent.StrikeEnded(u.id, tick)
            }
        }
        for (d in svc.demands().filter { it.isOpen && tick >= it.deadline }) {
            val r = respond(svc.get(d.union)!!.company, d.id, ResponseKind.REFUSE, 0L, tick)
            if (r is Outcome.Ok) out += r.value
        }
        out += review(tick)
        for (u in svc.active()) {
            if (!svc.isRoundDue(u, tick)) continue
            val s = stats(u.company, u.region) ?: continue
            val d = svc.makeDemand(u, svc.demandedRaise(u.strengthPpm, s.wageRatioPpm), tick)
            out += UnionEvent.Demanded(u.id, d.id, tick)
        }
        svc.prune()
        return out
    }

    private fun review(tick: Tick): List<UnionEvent> {
        val svc = e.unions
        val p = svc.params
        val out = ArrayList<UnionEvent>()
        for (c in e.companies.active().sortedBy { it.id }) {
            if ((tick.value + c.id.value) % p.reviewEvery != 0L) continue
            val regions = e.workforce.cohortsOf(c.id).filter { it.headcount > 0L }.map { it.key.region }.distinct().sorted()
            for (region in regions) {
                val s = stats(c.id, region) ?: continue
                val existing = svc.forGroup(c.id, region)
                if (existing == null) {
                    if (!svc.wantsUnion(s.moralePpm, s.wageRatioPpm, s.members)) continue
                    val random = e.random.stream("unions", c.id.value * 100_000L + region.value)
                    if (random.nextLong(1_000_000L) >= p.formChancePpm) continue
                    val made = svc.form(c.id, region, s.members, tick)
                    if (made is Outcome.Ok) {
                        for (co in cohortsOf(c.id, region)) e.workforce.setUnionized(co.key, true)
                        out += UnionEvent.Formed(made.value.id, c.id, tick)
                    }
                } else {
                    svc.updateStrength(existing, svc.targetStrength(s.moralePpm, s.wageRatioPpm), s.members)
                    if (svc.shouldDisband(existing) || s.members < p.minMembers / 2) disband(existing, tick)?.let { out += it }
                }
            }
            // A company that has let everyone in a region go has no union there any more.
            for (u in svc.ofCompany(c.id)) if (stats(c.id, u.region) == null) disband(u, tick)?.let { out += it }
        }
        return out
    }

    private fun disband(u: Union, tick: Tick): UnionEvent? {
        if (!u.isActive) return null
        e.unions.disband(u, tick)
        for (co in e.workforce.cohortsOf(u.company)) if (co.key.region == u.region) e.workforce.setUnionized(co.key, false)
        e.unions.openDemand(u.id)?.let { d -> e.unions.backedDown(d, u, tick, 0L) }
        return UnionEvent.Disbanded(u.id, u.company, tick)
    }

    // ---- answering a demand --------------------------------------------------------------------

    private fun applyRaise(u: Union, raisePpm: Long) {
        for (co in cohortsOf(u.company, u.region)) {
            e.workforce.raiseWage(co.key, raisePpm)
            e.workforce.adjustMorale(co.key, e.unions.params.dealMoraleBoostPpm)
            e.workforce.endStrike(co.key)
        }
    }

    private fun strike(u: Union, d: Demand, tick: Tick, counterPpm: Long): UnionEvent {
        val days = e.unions.strikeLength(e.random.stream("unions.strike", u.id.value))
        e.unions.struck(d, u, tick, days, counterPpm)
        for (co in cohortsOf(u.company, u.region)) e.workforce.startStrike(co.key, Tick(tick.value + days))
        return UnionEvent.Struck(u.id, d.id, days, tick)
    }

    /** Answers a demand: accept it, offer a smaller raise, or turn it down and risk a strike. */
    fun respond(company: CompanyId, demandId: Long, kind: ResponseKind, counterPpm: Long, tick: Tick): Outcome<UnionEvent> {
        val svc = e.unions
        val d = svc.demand(demandId) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown demand")
        val u = svc.get(d.union) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown union")
        if (u.company != company) return Outcome.reject(Rules.NOT_OWNER, "that is not your union")
        svc.checkResponse(d, kind, counterPpm)?.let { return Outcome.Rejected(it) }
        return when (kind) {
            ResponseKind.ACCEPT -> {
                applyRaise(u, d.raisePpm); svc.accepted(d, u, tick, d.raisePpm, DemandStatus.ACCEPTED)
                Outcome.ok(UnionEvent.Agreed(u.id, d.id, d.raisePpm, tick))
            }
            ResponseKind.COUNTER -> {
                if (counterPpm >= mulDiv(d.raisePpm, svc.acceptableShare(u.strengthPpm), 1_000_000L)) {
                    applyRaise(u, counterPpm); svc.accepted(d, u, tick, counterPpm, DemandStatus.SETTLED)
                    Outcome.ok(UnionEvent.Agreed(u.id, d.id, counterPpm, tick))
                } else Outcome.ok(turnedDown(u, d, tick, counterPpm))
            }
            ResponseKind.REFUSE -> Outcome.ok(turnedDown(u, d, tick, 0L))
        }
    }

    private fun turnedDown(u: Union, d: Demand, tick: Tick, counterPpm: Long): UnionEvent {
        val roll = e.random.stream("unions.roll", u.id.value).nextLong(1_000_000L)
        return if (roll < e.unions.strikeChance(u.strengthPpm)) strike(u, d, tick, counterPpm)
        else { e.unions.backedDown(d, u, tick, counterPpm); UnionEvent.BackedDown(u.id, d.id, tick) }
    }

    /** What a raise would add to the monthly wage bill of the group. */
    fun monthlyCost(u: Union, raisePpm: Long): Money {
        val payroll = stats(u.company, u.region)?.payroll ?: return Money.ZERO
        return Money(mulDiv(payroll.raw * 30, raisePpm, 1_000_000L))
    }

    fun forget(company: CompanyId, tick: Tick) { for (u in e.unions.ofCompany(company)) disband(u, tick) }
}
