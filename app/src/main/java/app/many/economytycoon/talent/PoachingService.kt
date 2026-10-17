package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.workforce.CohortKey

/** An approach that has been made: the record plus the retainer owed on the day. */
data class SubmittedOffer(val offer: HeadhuntOffer, val intents: List<SettlementIntent>)

data class PoachingReport(val intents: List<SettlementIntent>, val effects: List<WorkforceEffect>, val events: List<TalentEvent>) {
    companion object { val EMPTY = PoachingReport(emptyList(), emptyList(), emptyList()) }
}

/**
 * Headhunting between companies. A hunter pays a search firm to approach people at a rival; the people decide
 * (pay, morale, tenure, bonds, the hunter's name), the victim may counter, non-competes may stand in the way, and
 * the ones who move take their skills with them.
 */
class PoachingService(
    private val state: TalentState,
    private val taxonomy: SkillTaxonomy,
    private val skills: CohortSkillService,
    private val training: TrainingService,
    private val succession: SuccessionService,
    private val seed: Long,
    val params: PoachParams = PoachParams(),
    /** How attractive a company is as an employer in a region, 0..1,000,000. */
    private val brandOf: (CompanyId, app.many.economytycoon.core.RegionId) -> Long = { _, _ -> 500_000L },
) {
    private fun rng(name: String, id: Long, tick: Tick) = DeterministicRandom(RandomProvider.deriveSeed(seed, "poach.$name", id * 1_000_003L + tick.value))

    fun offer(id: OfferId): HeadhuntOffer? = state.offers[id]
    fun offers(): List<HeadhuntOffer> = state.offers.values.toList()
    fun pendingOf(hunter: CompanyId): List<HeadhuntOffer> = state.offers.values.filter { it.isPending && it.hunter == hunter }
    fun covenants(company: CompanyId): List<NonCompete> = state.covenants.values.filter { it.company == company }
    fun activeCovenants(company: CompanyId, tick: Tick): List<NonCompete> = covenants(company).filter { it.isActive(tick) }
    fun counterPolicy(company: CompanyId): CounterPolicy? = state.counterPolicies[company]
    fun retentionOf(cohort: CohortKey, tick: Tick): Long = state.retention[cohort]?.takeIf { it.until > tick }?.strengthPpm ?: 0L

    // ----- the victim's side ---------------------------------------------------------------------------

    fun setCounterPolicy(company: CompanyId, enabled: Boolean, maxMatchPpm: Long): Outcome<CounterPolicy> {
        if (maxMatchPpm !in 0L..params.referenceCounterRaisePpm * 4) return Outcome.reject(Rules.INVALID_ARGUMENT, "counter-offer ceiling out of range")
        val policy = state.counterPolicies.getOrPut(company) { CounterPolicy(company) }
        policy.restore(enabled, maxMatchPpm)
        return Outcome.ok(policy)
    }

    /** Has the people of [cohort] (or one [person]) sign a non-compete running [months] months. */
    fun signNonCompete(company: CompanyId, cohort: CohortKey?, person: PersonId?, months: Int, penaltyPerHead: Money, tick: Tick, inputs: TalentInputs): Outcome<NonCompete> {
        if ((cohort == null) == (person == null)) return Outcome.reject(Rules.INVALID_ARGUMENT, "cover a cohort or a person")
        if (months < 1 || months > params.maxCovenantMonths) return Outcome.reject(Rules.INVALID_ARGUMENT, "term must be 1..${params.maxCovenantMonths} months")
        if (penaltyPerHead.raw <= 0L) return Outcome.reject(Rules.INVALID_PRICE, "a covenant needs a penalty")
        if (activeCovenants(company, tick).size >= params.maxCovenantsPerCompany) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many covenants")
        val key: CohortKey; val heads: Long; val wage: Money; val region: app.many.economytycoon.core.RegionId
        if (cohort != null) {
            if (cohort.company != company) return Outcome.reject(Rules.NOT_OWNER, "not your cohort")
            val fact = inputs.cohort(cohort) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown cohort")
            if (fact.headcount <= 0L) return Outcome.reject(Rules.WRONG_STATE, "nobody to cover")
            key = cohort; heads = fact.headcount; wage = fact.wage; region = cohort.region
        } else {
            val p = state.people[person] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown person")
            if (p.company != company) return Outcome.reject(Rules.NOT_OWNER, "not your employee")
            key = p.cohort; heads = 1L; wage = p.wage; region = p.region
        }
        if (penaltyPerHead.raw > wage.raw * 360L * params.maxPenaltyYears) return Outcome.reject(Rules.INVALID_PRICE, "penalty above the legal cap")
        if (state.covenants.values.any { it.isActive(tick) && it.company == company && it.covers(if (cohort != null) key else null, person) }) return Outcome.reject(Rules.DUPLICATE, "already covered")
        val c = NonCompete(state.counters.covenant(), company, cohort, person, region, heads, tick, tick + months * 30L, penaltyPerHead)
        state.covenants[c.id] = c
        return Outcome.ok(c)
    }

    fun releaseCovenant(id: CovenantId, company: CompanyId): Outcome<NonCompete> {
        val c = state.covenants[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown covenant")
        if (c.company != company) return Outcome.reject(Rules.NOT_OWNER, "not your covenant")
        if (c.lapsed) return Outcome.reject(Rules.WRONG_STATE, "already lapsed")
        c.restore(c.breaches, true)
        return Outcome.ok(c)
    }

    /** A bonus pool that makes the cohort harder to hire away for [days] days; the cost is one month of wages scaled by [strengthPpm]. */
    fun grantRetention(company: CompanyId, cohort: CohortKey, strengthPpm: Long, days: Int, tick: Tick, inputs: TalentInputs): Outcome<List<SettlementIntent>> {
        if (cohort.company != company) return Outcome.reject(Rules.NOT_OWNER, "not your cohort")
        if (strengthPpm !in 1L..Ppm.ONE || days !in 30..360) return Outcome.reject(Rules.INVALID_ARGUMENT, "strength 0..1 and 30..360 days")
        val fact = inputs.cohort(cohort) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown cohort")
        val cost = Money(Ppm.of(fact.wage.raw * 30L * fact.headcount, strengthPpm))
        if (inputs.cashOf(company) < cost) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot afford the bonus pool")
        if (retentionOf(cohort, tick) >= strengthPpm) return Outcome.reject(Rules.DUPLICATE, "an equal or stronger grant is running")
        state.retention[cohort] = RetentionGrant(cohort, tick + days.toLong(), strengthPpm)
        return Outcome.ok(listOfNotNull(TalentLedger.bonus("retention", company.value, company, cohort.region, cost, tick, "retention bonus", extra = cohort.region.value * 1_000_003L + cohort.role.key.hashCode())))
    }

    // ----- the hunter's side ---------------------------------------------------------------------------

    private fun covering(victim: CompanyId, key: CohortKey, who: PersonId?, tick: Tick): List<NonCompete> =
        state.covenants.values.filter { it.isActive(tick) && it.company == victim && it.covers(key, who) }

    fun submit(
        hunter: CompanyId, destination: CohortKey, target: PoachTarget, offeredWage: Money, signingBonus: Money,
        stance: CovenantStance, tick: Tick, inputs: TalentInputs,
    ): Outcome<SubmittedOffer> {
        if (destination.company != hunter) return Outcome.reject(Rules.NOT_OWNER, "destination must be your cohort")
        if (taxonomy.profile(destination.role) == null) return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown role")
        if (offeredWage.raw <= 0L) return Outcome.reject(Rules.INVALID_PRICE, "offer a wage")
        if (signingBonus.raw < 0L) return Outcome.reject(Rules.INVALID_PRICE, "negative bonus")
        if (pendingOf(hunter).size >= params.maxPendingPerHunter) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many approaches under way")
        val key: CohortKey; val heads: Long; val who: PersonId?; val wage: Money
        when (target) {
            is PoachTarget.Group -> {
                key = target.cohort
                val fact = inputs.cohort(key) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown cohort")
                if (target.count <= 0L) return Outcome.reject(Rules.INVALID_QUANTITY, "pick at least one person")
                val cap = (fact.headcount * params.maxShareOfCohortPpm / Ppm.ONE).coerceAtLeast(if (fact.headcount > 0L) 1L else 0L)
                if (cap <= 0L) return Outcome.reject(Rules.WRONG_STATE, "nobody to approach")
                heads = minOf(target.count, cap); who = null; wage = fact.wage
            }
            is PoachTarget.Individual -> {
                val p = state.people[target.person] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown person")
                if (!p.isActive) return Outcome.reject(Rules.WRONG_STATE, "that person has retired")
                key = p.cohort; heads = 1L; who = p.id; wage = p.wage
            }
        }
        val victim = key.company
        if (victim == hunter) return Outcome.reject(Rules.INVALID_ARGUMENT, "cannot hunt your own people")
        if (state.cooldowns[CooldownKey(hunter, key)]?.let { it > tick } == true) return Outcome.reject(Rules.RATE_LIMITED, "approached too recently")
        val bound = covering(victim, key, who, tick)
        if (bound.isNotEmpty() && stance == CovenantStance.RESPECT) return Outcome.reject(Rules.REGULATION, "covered by a non-compete")
        val fee = PoachOdds.fee(offeredWage, heads, params)
        val retainer = Money(Ppm.of(fee.raw, params.retainerShareOfFeePpm))
        val upfront = retainer + signingBonus * heads
        if (inputs.cashOf(hunter) < upfront) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot cover the retainer and bonuses")
        val id = state.counters.offer()
        val offer = HeadhuntOffer(id, hunter, victim, target, destination, offeredWage, signingBonus, stance, tick, tick + params.resolveDays.toLong(), heads)
        state.offers[id] = offer
        val intents = listOfNotNull(TalentLedger.fee("retainer", id.value, hunter, retainer, tick, "search retainer"))
        offer.feePaid = retainer
        return Outcome.ok(SubmittedOffer(offer, intents))
    }

    // ----- resolution -----------------------------------------------------------------------------------

    private fun oddsFor(o: HeadhuntOffer, fact: CohortFact, who: Person?, tick: Tick): Long {
        val primary = taxonomy.profile(fact.key.role)?.primary
        val certified = primary?.let { skills.get(fact.key)?.certifiedShare(it) } ?: 0L
        val inputs = OddsInputs(
            currentWage = who?.wage ?: fact.wage, offeredWage = o.offeredWage, signingBonus = o.signingBonus,
            moralePpm = fact.moralePpm, tenureTicks = who?.let { tick.value - it.hiredAt.value } ?: fact.tenureTicks,
            bondedSharePpm = training.bondedShare(fact.key, fact.headcount, tick),
            hunterBrandPpm = brandOf(o.hunter, o.destination.region), certifiedSharePpm = certified,
            retentionPpm = retentionOf(fact.key, tick), loyaltyPpm = who?.loyaltyPpm ?: 500_000L,
        )
        return PoachOdds.acceptance(inputs, params)
    }

    /** Heads out of [n] who say yes at [oddsPpm]: the expected number, with the remainder settled by a draw. */
    private fun draw(n: Long, oddsPpm: Long, r: DeterministicRandom): Long {
        val exact = n * oddsPpm
        val base = exact / Ppm.ONE
        return minOf(n, base + if (r.nextLong(Ppm.ONE) < exact % Ppm.ONE) 1L else 0L)
    }

    /** Resolves approaches that are due, pays covenants, lapses and prunes. */
    fun advance(tick: Tick, world: TalentWorld, inputs: TalentInputs): PoachingReport {
        val intents = ArrayList<SettlementIntent>(); val effects = ArrayList<WorkforceEffect>(); val events = ArrayList<TalentEvent>()
        for (o in state.offers.values.toList()) if (o.isPending && o.resolveAt <= tick) resolve(o, tick, world, inputs, intents, effects, events)
        covenantsDaily(tick, inputs, intents, effects)
        state.retention.entries.removeAll { it.value.until <= tick }
        state.cooldowns.entries.removeAll { it.value <= tick }
        prune(tick)
        return PoachingReport(intents, effects, events)
    }

    private fun resolve(o: HeadhuntOffer, tick: Tick, world: TalentWorld, inputs: TalentInputs, intents: MutableList<SettlementIntent>, effects: MutableList<WorkforceEffect>, events: MutableList<TalentEvent>) {
        val who = (o.target as? PoachTarget.Individual)?.let { state.people[it.person] }
        val key = when (val t = o.target) { is PoachTarget.Group -> t.cohort; is PoachTarget.Individual -> who?.cohort }
        val fact = key?.let { inputs.cohort(it) ?: who?.let { w -> CohortFact(it, 1L, 1L, w.wage, w.wage, 600_000L, tick.value - w.hiredAt.value) } }
        o.resolvedAt = tick
        state.cooldowns[CooldownKey(o.hunter, key ?: o.destination)] = tick + params.cooldownDays.toLong()
        val heads = if (who != null) (if (who.isActive && who.company == o.victim) 1L else 0L) else minOf(o.heads, fact?.headcount ?: 0L)
        if (key == null || fact == null || heads <= 0L) {
            o.status = OfferStatus.REJECTED; events.add(TalentEvent.PoachFailed(o.id, o.hunter, o.victim, tick)); return
        }
        val bound = covering(o.victim, key, who?.id, tick)
        if (bound.isNotEmpty() && o.stance == CovenantStance.RESPECT) {
            o.status = OfferStatus.BLOCKED; events.add(TalentEvent.PoachFailed(o.id, o.hunter, o.victim, tick)); return
        }
        val odds = oddsFor(o, fact, who, tick); o.oddsPpm = odds
        val r = rng("offer", o.id.value, tick)
        var movers = draw(heads, odds, r)
        var kept = 0L
        val policy = state.counterPolicies[o.victim]
        if (movers > 0L && policy != null && policy.enabled) {
            val raise = minOf(policy.maxMatchPpm, params.referenceCounterRaisePpm * 2)
            val counter = Money(fact.wage.raw + Ppm.mul(fact.wage.raw, raise))
            val bill = Money((counter.raw - fact.wage.raw).coerceAtLeast(0L) * 30L) * movers
            if (inputs.cashOf(o.victim) >= bill && counter > fact.wage) {
                val p = PoachOdds.counterSuccess(counter, o.offeredWage, fact.moralePpm, who?.loyaltyPpm ?: 500_000L, params)
                kept = draw(movers, p, r)
                if (kept > 0L) {
                    o.counterWage = counter; movers -= kept
                    effects.add(WorkforceEffect.RaiseWage(key, counter))
                    TalentLedger.bonus("counter", o.id.value, o.victim, key.region, bill, tick, "counter-offer", extra = 1L)?.let { intents.add(it) }
                    if (who != null) who.wage = counter
                }
            }
        }
        o.keptHeads = kept
        if (movers <= 0L) {
            o.status = if (kept > 0L) OfferStatus.COUNTERED else OfferStatus.REJECTED
            events.add(if (kept > 0L) TalentEvent.PoachCountered(o.id, o.hunter, o.victim, kept, tick) else TalentEvent.PoachFailed(o.id, o.hunter, o.victim, tick))
            return
        }
        val minLevel = (o.target as? PoachTarget.Group)?.minLevelPpm ?: 0L
        val moved = skills.move(world, key, o.destination, movers, TakePolicy.BEST_FIRST, minLevel)
        if (moved <= 0L && who == null) {
            o.status = OfferStatus.REJECTED; events.add(TalentEvent.PoachFailed(o.id, o.hunter, o.victim, tick)); return
        }
        val count = if (who != null) 1L else moved
        o.movedHeads = count; o.status = OfferStatus.SUCCEEDED
        effects.add(WorkforceEffect.Transfer(key, o.destination, count, o.offeredWage))
        effects.add(WorkforceEffect.Morale(key, -Ppm.mul(Ppm.ratio(count, fact.headcount), params.poachedMoralePpm)))
        val totalFee = PoachOdds.fee(o.offeredWage, count, params)
        val remainder = Money((totalFee.raw - o.feePaid.raw).coerceAtLeast(0L))
        TalentLedger.fee("success", o.id.value, o.hunter, remainder, tick, "search fee")?.let { intents.add(it) }
        o.feePaid = o.feePaid + remainder
        TalentLedger.bonus("signing", o.id.value, o.hunter, o.destination.region, o.signingBonus * count, tick, "signing bonus")?.let { intents.add(it) }
        if (who != null) {
            val report = succession.personLeft(who.id, tick)
            intents.addAll(report.intents); effects.addAll(report.effects); events.addAll(report.events)
            who.company = o.hunter; who.role = o.destination.role; who.region = o.destination.region; who.hiredAt = tick; who.wage = o.offeredWage
        }
        for (c in bound) breach(c, o, count, tick, r, intents, events)
        events.add(TalentEvent.PoachSucceeded(o.id, o.hunter, o.victim, count, tick))
    }

    private fun breach(c: NonCompete, o: HeadhuntOffer, count: Long, tick: Tick, r: DeterministicRandom, intents: MutableList<SettlementIntent>, events: MutableList<TalentEvent>) {
        val owed = c.penaltyPerHead * count
        if (o.stance == CovenantStance.BUY_OUT) {
            TalentLedger.companyToCompany("buyout", o.id.value, o.hunter, o.victim, owed, tick, "non-compete buy-out", extra = c.id.value)?.let { intents.add(it) }
            return
        }
        c.restore(c.breaches + 1, c.lapsed)
        events.add(TalentEvent.CovenantBreached(c.id, c.company, o.hunter, tick))
        if (r.nextLong(Ppm.ONE) < params.courtWinsPpm) {
            val damages = Money(Ppm.of(owed.raw, params.damagesMultiplierPpm))
            TalentLedger.companyToCompany("damages", o.id.value, o.hunter, o.victim, damages, tick, "non-compete damages", extra = c.id.value)?.let { intents.add(it) }
        }
    }

    private fun covenantsDaily(tick: Tick, inputs: TalentInputs, intents: MutableList<SettlementIntent>, effects: MutableList<WorkforceEffect>) {
        val monthly = tick.value % 30L == 0L
        for (c in state.covenants.values) {
            if (c.lapsed) continue
            if (tick >= c.until) { c.restore(c.breaches, true); continue }
            if (!monthly) continue
            val fact = c.cohort?.let { inputs.cohort(it) }
            val wage = fact?.wage ?: c.person?.let { state.people[it]?.wage } ?: continue
            val pay = Money(Ppm.of(wage.raw * 30L * c.headsCovered, params.covenantPayPpm))
            TalentLedger.bonus("covenant", c.id.value, c.company, c.region ?: continue, pay, tick, "non-compete allowance")?.let { intents.add(it) }
            if (c.cohort != null) effects.add(WorkforceEffect.Morale(c.cohort, -params.covenantMoralePpm))
        }
    }

    internal fun prune(tick: Tick) {
        val horizon = params.keepDays.toLong()
        state.offers.entries.removeAll { val at = it.value.resolvedAt; at != null && tick.value - at.value > horizon }
        state.covenants.entries.removeAll { it.value.lapsed && tick.value - it.value.until.value > horizon }
    }
}
