package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick

data class CareerReport(val effects: List<WorkforceEffect>, val events: List<TalentEvent>)

/** Promotion ladders inside a company: the best of one cohort move up into the next when there is room. */
class CareerService(
    private val state: TalentState,
    private val taxonomy: SkillTaxonomy,
    private val skills: CohortSkillService,
    val params: CareerParams = CareerParams(),
) {
    fun ladder(id: LadderId): CareerLadder? = state.ladders[id]
    fun ladders(): List<CareerLadder> = state.ladders.values.toList()
    fun laddersOf(company: CompanyId): List<CareerLadder> = state.ladders.values.filter { it.company == company }

    /** Rungs run from the entry cohort at index 0 upwards; promotion into rung i comes from rung i-1. */
    fun create(company: CompanyId, name: String, rungs: List<Rung>): Outcome<CareerLadder> {
        if (name.isBlank()) return Outcome.reject(Rules.INVALID_ARGUMENT, "a ladder needs a name")
        if (rungs.size < 2 || rungs.size > params.maxRungs) return Outcome.reject(Rules.INVALID_ARGUMENT, "a ladder has 2..${params.maxRungs} rungs")
        if (laddersOf(company).size >= params.maxLaddersPerCompany) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many ladders")
        if (rungs.any { it.cohort.company != company }) return Outcome.reject(Rules.NOT_OWNER, "rungs must be your cohorts")
        if (rungs.any { taxonomy.profile(it.cohort.role) == null }) return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown role on a rung")
        if (rungs.map { it.cohort }.toSet().size != rungs.size) return Outcome.reject(Rules.DUPLICATE, "a cohort can sit on a ladder once")
        if (rungs.drop(1).any { it.minLevelPpm !in 0L..Ppm.ONE || it.minTenureTicks < 0L || it.raisePpm !in 0L..params.maxRaisePpm }) return Outcome.reject(Rules.INVALID_ARGUMENT, "rung limits out of range")
        val l = CareerLadder(state.counters.ladder(), company, name.trim(), rungs.toList())
        state.ladders[l.id] = l
        return Outcome.ok(l)
    }

    fun setEnabled(id: LadderId, company: CompanyId, enabled: Boolean): Outcome<CareerLadder> {
        val l = state.ladders[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown ladder")
        if (l.company != company) return Outcome.reject(Rules.NOT_OWNER, "not your ladder")
        l.restore(enabled, l.promotions, l.lastReview, l.carry.toList())
        return Outcome.ok(l)
    }

    fun remove(id: LadderId, company: CompanyId): Outcome<CareerLadder> {
        val l = state.ladders[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown ladder")
        if (l.company != company) return Outcome.reject(Rules.NOT_OWNER, "not your ladder")
        state.ladders.remove(id)
        return Outcome.ok(l)
    }

    /** What a review would do for every step of [ladder] with today's facts. */
    fun preview(ladder: CareerLadder, inputs: TalentInputs): List<PromotionPreview> =
        (1 until ladder.rungs.size).map { i -> previewStep(ladder, i, inputs) }

    private fun previewStep(ladder: CareerLadder, i: Int, inputs: TalentInputs): PromotionPreview {
        val lowKey = ladder.rungs[i - 1].cohort; val rung = ladder.rungs[i]
        val low = inputs.cohort(lowKey); val high = inputs.cohort(rung.cohort)
        fun blocked(why: String, eligible: Long = 0L, openings: Long = 0L) =
            PromotionPreview(ladder.id, i, lowKey, rung.cohort, eligible, openings, 0L, Money.ZERO, Money.ZERO, why)
        if (low == null || high == null) return blocked("a cohort on the ladder does not exist")
        val dist = skills.get(lowKey)?.dist(taxonomy.profile(lowKey.role)?.primary ?: return blocked("unknown role"))
            ?: return blocked("no skill picture yet")
        val eligible = if (low.tenureTicks >= rung.minTenureTicks) dist.countAtLeast(rung.minLevelPpm) else 0L
        if (high.openings <= 0L) return blocked("no opening above", eligible)
        if (eligible <= 0L) return blocked("nobody qualifies yet", 0L, high.openings)
        val cap = (Ppm.of(low.headcount, params.maxPromotionSharePpm) + 1L).coerceAtMost(low.headcount - 1L).coerceAtLeast(0L)
        val planned = minOf(eligible, high.openings, cap)
        val wage = Money(maxOf(high.wage.raw, low.wage.raw + Ppm.mul(low.wage.raw, rung.raisePpm)))
        val extra = Money((wage.raw - low.wage.raw).coerceAtLeast(0L)) * planned
        return PromotionPreview(ladder.id, i, lowKey, rung.cohort, eligible, high.openings, planned, wage, extra, if (planned <= 0L) "the cohort below must keep someone" else null)
    }

    /** Reviews every enabled ladder that is due. */
    fun advance(tick: Tick, world: TalentWorld, inputs: TalentInputs): CareerReport {
        val effects = ArrayList<WorkforceEffect>(); val events = ArrayList<TalentEvent>()
        for (l in state.ladders.values) {
            if (!l.enabled || tick.value - l.lastReview.value < params.reviewIntervalDays) continue
            l.lastReview = tick
            var moved = 0L
            // top-down, so room made at the top is filled from below in the same review
            for (i in l.rungs.size - 1 downTo 1) {
                val p = previewStep(l, i, inputs)
                if (p.planned <= 0L) continue
                val n = skills.move(world, p.from, p.to, p.planned, TakePolicy.BEST_FIRST, l.rungs[i].minLevelPpm)
                if (n <= 0L) continue
                moved += n
                effects.add(WorkforceEffect.Transfer(p.from, p.to, n, p.newWage))
                val heads = inputs.cohort(p.from)?.headcount ?: 1L
                effects.add(WorkforceEffect.Morale(p.from, Ppm.mul(Ppm.ratio(n, heads.coerceAtLeast(1L)), params.moraleOfPromotedPpm) + params.moraleHopePpm / 4))
            }
            l.promotions += moved
            if (moved > 0L) events.add(TalentEvent.Promoted(l.id, l.company, moved, tick))
        }
        return CareerReport(effects, events)
    }
}
