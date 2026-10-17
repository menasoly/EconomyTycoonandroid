package app.many.economytycoon.ip

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** What a design-around would cost and how likely it is to work. */
data class DesignAroundQuote(val cost: Money, val days: Int, val successPpm: Long)

/**
 * Re-engineering a product so it stays outside a patent's claims. It costs a share of what the research cost, takes
 * longer the broader the claims are, and may fail. A success clears the company to sell without a licence, at a small
 * penalty to cost and quality that [IpEffects] reads from the clearances.
 */
class DesignAroundService(
    private val state: IpState,
    private val disputes: DisputeService,
    val params: IpParams = IpParams(),
) {
    private val dp get() = params.designAround

    fun quote(p: Patent): DesignAroundQuote {
        val breadth = p.grantedBreadthPpm().coerceAtLeast(p.claimedBreadthPpm / 2).coerceIn(0L, 1_000_000L)
        val sizeFactor = 500_000L + breadth
        val cost = Money(mulDiv(mulDiv(p.researchCost.raw, dp.baseCostShareOfResearchPpm, 1_000_000L), sizeFactor, 1_000_000L))
        val days = dp.baseDays + mulDiv(dp.baseDays.toLong(), mulDiv(breadth, dp.breadthDaysPpm, 1_000_000L), 1_000_000L).toInt()
        val easy = (1_000_000L - p.noveltyPpm) / 10
        val success = (dp.baseSuccessPpm - mulDiv(breadth, dp.breadthPenaltyPpm, 1_000_000L) + easy).coerceIn(50_000L, 950_000L)
        return DesignAroundQuote(cost.coerceAtLeast(Money.units(100)), days, success)
    }

    fun running(company: CompanyId): List<DesignAround> =
        state.designAroundMap.values.filter { it.company == company && it.status.live }

    fun start(run: IpRun, company: CompanyId, patent: PatentId, dispute: DisputeId? = null): Outcome<DesignAround> {
        val p = state.patentMap[patent] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown patent")
        if (p.owner == IpOwner.company(company)) return Outcome.reject(Rules.INVALID_ARGUMENT, "you own that patent")
        if (!p.isLive) return Outcome.reject(Rules.WRONG_STATE, "the patent is not in force anywhere")
        if (state.hasClearance(company, patent)) return Outcome.reject(Rules.DUPLICATE, "already designed around")
        if (state.designAroundMap.values.any { it.company == company && it.patent == patent && it.status.live }) return Outcome.reject(Rules.DUPLICATE, "already under way")
        if (running(company).size >= dp.maxConcurrent) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many redesigns at once")
        val q = quote(p)
        val me = IpOwner.company(company)
        if (!run.wallet.tryReserve(me, q.cost)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot pay for the redesign")
        val d = DesignAround(state.ids.designAround(), company, patent, run.tick, Tick(run.tick.value + q.days), q.cost, q.successPpm, dispute)
        state.designAroundMap[d.id] = d
        run.out.charge(run.tick, ChargeKind.DESIGN_AROUND_COST, IpLedger.of(company), IpLedger.professionals, q.cost, "design around ${p.title}", d.id.value.toString())
        return Outcome.ok(d)
    }

    fun cancel(run: IpRun, id: DesignAroundId, by: CompanyId): Outcome<DesignAround> {
        val d = state.designAroundMap[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown redesign")
        if (d.company != by) return Outcome.reject(Rules.NOT_OWNER, "not your project")
        if (!d.status.live) return Outcome.reject(Rules.WRONG_STATE, "already finished")
        d.status = DesignAroundStatus.CANCELLED
        d.endedAt = run.tick
        return Outcome.ok(d)
    }

    fun step(run: IpRun) {
        for (d in state.designAroundMap.values.toList()) {
            if (!d.status.live || run.tick < d.completesAt) continue
            if (!run.ctx.isActive(d.company)) { d.status = DesignAroundStatus.CANCELLED; d.endedAt = run.tick; continue }
            val ok = state.dice.chance("designaround", Rate(d.successPpm), d.id.value)
            d.endedAt = run.tick
            if (ok) {
                d.status = DesignAroundStatus.SUCCEEDED
                state.clearanceSet.add(Clearance(d.company, d.patent, run.tick))
                disputes.onDesignAround(run, d.company, d.patent)
            } else {
                d.status = DesignAroundStatus.FAILED
            }
            run.out.event(IpEvent.DesignAroundFinished(d.id, d.company, d.patent, ok, run.tick))
        }
    }

    /** Clearances held by [company], used to price the penalty of running a redesigned product. */
    fun clearancesOf(company: CompanyId): List<Clearance> = state.clearanceSet.filter { it.company == company }

    /** A clearance lapses when the patent it cleared has ended; kept tidy once a quarter. */
    fun prune(tick: Tick) {
        val dead = state.clearanceSet.filter { c -> state.patentMap[c.patent]?.isLive != true }
        state.clearanceSet.removeAll(dead.toSet())
    }
}
