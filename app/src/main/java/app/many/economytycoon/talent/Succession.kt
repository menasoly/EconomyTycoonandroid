package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.workforce.CohortKey

data class SuccessionParams(
    val retirementAge: Int = 63,
    /** Yearly chance of retiring once past the retirement age, rising each year after. */
    val retireChanceStepPpm: Long = 250_000L,
    val baseQuitAnnualPpm: Long = 60_000L,
    /** A successor at or above this readiness steps in at once. */
    val readyPpm: Long = 550_000L,
    /** Output lost while a successor settles in is this much of the gap to full readiness. */
    val settlingLossPpm: Long = 500_000L,
    val settlingDays: Int = 180,
    val searchDays: Int = 90,
    val searchFeePpm: Long = 250_000L,
    val vacantEffectivenessPpm: Long = 600_000L,
    val interimEffectivenessPpm: Long = 700_000L,
    val maxSuccessorsPerPosition: Int = 4,
    val maxPositionsPerCompany: Int = 12,
    /** Monthly gain in readiness-driving skill at the pace of mentoring, before the multiplier. */
    val developmentLearnPerYearPpm: Long = 160_000L,
    val loyaltyDriftPpm: Long = 8_000L,
    val promotionLoyaltyBoostPpm: Long = 150_000L,
    val namedSuccessorLoyaltyBoostPpm: Long = 60_000L,
)

/** What filling a position from outside produced. */
data class AppointmentResult(val person: Person, val intents: List<SettlementIntent>, val effects: List<WorkforceEffect>)

/** Result of a monthly pass over people and positions. */
data class SuccessionReport(val intents: List<SettlementIntent>, val effects: List<WorkforceEffect>, val events: List<TalentEvent>)

/**
 * Named people in key roles: who holds each position, who is being groomed to take over, and what happens when the
 * holder goes. A ready successor steps in with little loss; without one the company searches outside, paying a fee,
 * and runs the position badly in the meantime.
 */
class SuccessionService(
    private val state: TalentState,
    private val taxonomy: SkillTaxonomy,
    private val seed: Long,
    val params: SuccessionParams = SuccessionParams(),
) {
    fun people(): List<Person> = state.people.values.toList()
    fun person(id: PersonId): Person? = state.people[id]
    fun peopleOf(company: CompanyId): List<Person> = state.people.values.filter { it.company == company && it.isActive }
    fun positions(): List<KeyPosition> = state.positions.values.toList()
    fun position(id: PositionId): KeyPosition? = state.positions[id]
    fun positionsOf(company: CompanyId): List<KeyPosition> = state.positions.values.filter { it.company == company }
    fun successorsOf(position: PositionId): List<Successor> = state.successors[position]?.values?.sortedByDescending { it.readinessPpm } ?: emptyList()
    fun holderOf(position: PositionId): Person? = state.positions[position]?.holder?.let { state.people[it] }

    private fun stream(name: String, id: Long, extra: Long = 0L): DeterministicRandom =
        DeterministicRandom(RandomProvider.deriveSeed(seed, name, id * 1_000_003L + extra))

    // ----- people ------------------------------------------------------------------------------------

    /** Levels a candidate offers, from the role's requirements, the pay on offer against the role's base wage and a roll. */
    fun candidateLevels(profile: RoleProfile, wageRatioPpm: Long, rnd: DeterministicRandom): Map<SkillId, Long> {
        val quality = (wageRatioPpm - 700_000L).coerceIn(0L, 700_000L)
        val out = java.util.TreeMap<SkillId, Long>()
        for (r in profile.requirements) {
            val spread = rnd.nextGaussianPpm() / 20L
            val lvl = Ppm.mul(r.requiredPpm, 800_000L + Ppm.mul(quality, 450_000L)) + spread
            out[r.skill] = lvl.coerceIn(50_000L, 980_000L)
        }
        return out
    }

    /**
     * Brings a new named person into a company, counted in the company-level cohort of their role. [levels] may be
     * given; otherwise they are drawn from the role's requirements.
     */
    fun addPerson(
        company: CompanyId, name: String, role: RoleId, region: RegionId, wage: Money, age: Int, tick: Tick,
        levels: Map<SkillId, Long>? = null, potentialPpm: Long = 700_000L, loyaltyPpm: Long = 600_000L,
    ): Outcome<Person> {
        val profile = taxonomy.profile(role) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no profile for ${role.key}")
        if (name.isBlank()) return Outcome.reject(Rules.INVALID_ARGUMENT, "a person needs a name")
        if (wage.raw <= 0L) return Outcome.reject(Rules.INVALID_PRICE, "pay must be positive")
        if (age !in 18..80) return Outcome.reject(Rules.INVALID_ARGUMENT, "age out of range")
        val p = Person(state.counters.person(), company, name.trim(), role, region, tick)
        p.ageYears = age; p.wage = wage; p.potentialPpm = Ppm.clamp(potentialPpm); p.loyaltyPpm = Ppm.clamp(loyaltyPpm)
        val drawn = levels ?: candidateLevels(profile, Ppm.ratio(wage.raw, profile.baseWage.raw.coerceAtLeast(1L)), stream("talent.person", p.id.value))
        p.levels.putAll(drawn)
        state.people[p.id] = p
        return Outcome.ok(p)
    }

    /** How well a person does the job of [role] on their own levels. */
    fun fit(person: Person, role: RoleId): Productivity? {
        val profile = taxonomy.profile(role) ?: return null
        return SkillMatch.person(profile) { person.level(it) }
    }

    fun readinessFor(person: Person, role: RoleId): Long {
        val p = fit(person, role) ?: return 0L
        return Ppm.clamp(Ppm.ratio(p.multiplierPpm - SkillMatch.FLOOR_PPM, Ppm.ONE - SkillMatch.FLOOR_PPM))
    }

    // ----- positions -----------------------------------------------------------------------------------

    fun openPosition(company: CompanyId, role: RoleId, region: RegionId, title: String): Outcome<KeyPosition> {
        val profile = taxonomy.profile(role) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no profile for ${role.key}")
        if (!profile.keyRole) return Outcome.reject(Rules.INVALID_ARGUMENT, "${role.key} is not a key role")
        if (title.isBlank()) return Outcome.reject(Rules.INVALID_ARGUMENT, "a position needs a title")
        if (positionsOf(company).size >= params.maxPositionsPerCompany) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many key positions")
        val pos = KeyPosition(state.counters.position(), company, role, region, title.trim())
        pos.vacantSince = null
        state.positions[pos.id] = pos
        return Outcome.ok(pos)
    }

    /** Puts [person] in [position]. They must work for the company, and the role of the position must be one they could hold. */
    fun appoint(position: PositionId, person: PersonId, tick: Tick): Outcome<KeyPosition> {
        val pos = state.positions[position] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown position")
        val p = state.people[person] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown person")
        if (p.company != pos.company) return Outcome.reject(Rules.NOT_OWNER, "that person works for another company")
        if (!p.isActive) return Outcome.reject(Rules.WRONG_STATE, "that person has left")
        if (pos.holder != null) return Outcome.reject(Rules.WRONG_STATE, "the position is already held")
        if (p.position != null) return Outcome.reject(Rules.DUPLICATE, "that person already holds a position")
        pos.holder = p.id; pos.vacantSince = null; pos.searchUntil = null; pos.settlingUntil = null; pos.settlingPpm = 0L
        p.position = pos.id
        p.role = pos.role; p.region = pos.region
        state.successors[position]?.remove(person)
        return Outcome.ok(pos)
    }

    // ----- succession plans ------------------------------------------------------------------------------

    fun nameSuccessor(position: PositionId, person: PersonId, development: DevelopmentKind, tick: Tick): Outcome<Successor> {
        val pos = state.positions[position] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown position")
        val p = state.people[person] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown person")
        if (p.company != pos.company || !p.isActive) return Outcome.reject(Rules.NOT_OWNER, "only your own people can be named")
        if (pos.holder == p.id) return Outcome.reject(Rules.INVALID_ARGUMENT, "the holder cannot succeed themselves")
        val plan = state.successors.getOrPut(position) { java.util.TreeMap() }
        if (plan.containsKey(person)) return Outcome.reject(Rules.DUPLICATE, "already named as a successor")
        if (plan.size >= params.maxSuccessorsPerPosition) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many successors named")
        val s = Successor(person, readinessFor(p, pos.role), development, tick)
        plan[person] = s
        p.loyaltyPpm = Ppm.clamp(p.loyaltyPpm + params.namedSuccessorLoyaltyBoostPpm)
        return Outcome.ok(s)
    }

    fun removeSuccessor(position: PositionId, person: PersonId): Outcome<Unit> {
        val plan = state.successors[position] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no succession plan")
        if (plan.remove(person) == null) return Outcome.reject(Rules.UNKNOWN_ENTITY, "not a named successor")
        return Outcome.ok(Unit)
    }

    fun setDevelopment(position: PositionId, person: PersonId, development: DevelopmentKind): Outcome<Successor> {
        val s = state.successors[position]?.get(person) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "not a named successor")
        s.development = development
        return Outcome.ok(s)
    }

    /** Share of a company's key positions that have at least one successor at or above the ready level. */
    fun coveragePpm(company: CompanyId): Long {
        val ps = positionsOf(company)
        if (ps.isEmpty()) return Ppm.ONE
        val covered = ps.count { pos -> successorsOf(pos.id).any { it.readinessPpm >= params.readyPpm } }
        return Ppm.ratio(covered.toLong(), ps.size.toLong())
    }

    // ----- effectiveness ---------------------------------------------------------------------------------

    /** Output of a position relative to a fully capable holder, ppm. */
    fun effectiveness(position: PositionId, tick: Tick): Long {
        val pos = state.positions[position] ?: return Ppm.ONE
        val holder = pos.holder?.let { state.people[it] }
        if (holder == null) return if (pos.searchUntil != null) params.interimEffectivenessPpm else params.vacantEffectivenessPpm
        val base = fit(holder, pos.role)?.multiplierPpm ?: Ppm.ONE
        val until = pos.settlingUntil
        if (until == null || tick >= until || pos.settlingPpm <= 0L) return base
        val remaining = Ppm.ratio(until.value - tick.value, params.settlingDays.toLong())
        return Ppm.mul(base, Ppm.ONE - Ppm.mul(pos.settlingPpm, remaining))
    }

    /** Average effectiveness of a company's key positions; 1.0 when it has none. */
    fun keyRoleIndex(company: CompanyId, tick: Tick): Long {
        val ps = positionsOf(company)
        if (ps.isEmpty()) return Ppm.ONE
        return ps.sumOf { effectiveness(it.id, tick) } / ps.size
    }

    // ----- leaving ---------------------------------------------------------------------------------------

    /**
     * The holder of a position goes (retired, quit, poached). A ready successor steps up; otherwise an outside search
     * starts. Returns the effects on the workforce and the money for a search.
     */
    fun vacate(position: PositionId, tick: Tick, departed: Boolean): SuccessionReport {
        val pos = state.positions[position] ?: return SuccessionReport(emptyList(), emptyList(), emptyList())
        val intents = ArrayList<SettlementIntent>(); val effects = ArrayList<WorkforceEffect>(); val events = ArrayList<TalentEvent>()
        val leaving = pos.holder?.let { state.people[it] }
        if (leaving != null) {
            leaving.position = null
            if (departed) leaving.retiredAt = tick
        }
        pos.holder = null
        val ready = successorsOf(position).firstOrNull { s ->
            val person = state.people[s.person]
            person != null && person.isActive && person.company == pos.company && person.position == null && s.readinessPpm >= params.readyPpm
        }
        if (ready != null) {
            val person = state.people.getValue(ready.person)
            val from = person.cohort
            appoint(position, person.id, tick)
            val to = CohortKey(pos.company, app.many.economytycoon.core.FacilityId(0L), pos.role, pos.region)
            if (from != to) effects += WorkforceEffect.Transfer(from, to, 1L, person.wage)
            pos.settlingPpm = Ppm.mul(Ppm.ONE - ready.readinessPpm, params.settlingLossPpm)
            pos.settlingUntil = tick + params.settlingDays.toLong()
            person.loyaltyPpm = Ppm.clamp(person.loyaltyPpm + params.promotionLoyaltyBoostPpm)
            events += TalentEvent.PositionVacated(position, pos.company, true, tick)
            events += TalentEvent.PositionFilled(position, pos.company, person.id, tick)
        } else {
            pos.vacantSince = tick
            pos.searchUntil = tick + params.searchDays.toLong()
            val profile = taxonomy.profile(pos.role)
            val annual = (profile?.baseWage ?: Money.units(100)) * 360L
            val fee = Money(Ppm.of(annual.raw, params.searchFeePpm))
            pos.searchSpent = fee
            TalentLedger.fee("search", pos.id.value, pos.company, fee, tick, "executive search for ${pos.title}")?.let { intents += it }
            events += TalentEvent.PositionVacated(position, pos.company, false, tick)
        }
        return SuccessionReport(intents, effects, events)
    }

    /** Called when a person leaves their company by another route (poached): clears their post. */
    fun personLeft(person: PersonId, tick: Tick): SuccessionReport {
        val p = state.people[person] ?: return SuccessionReport(emptyList(), emptyList(), emptyList())
        val pos = p.position
        for (plan in state.successors.values) plan.remove(person)
        return if (pos != null) vacate(pos, tick, departed = false) else SuccessionReport(emptyList(), emptyList(), emptyList())
    }

    // ----- time ------------------------------------------------------------------------------------------

    /** Monthly: development, loyalty drift, searches that end, quits and retirements. */
    fun monthly(tick: Tick, wageRatio: (Person) -> Long): SuccessionReport {
        val intents = ArrayList<SettlementIntent>(); val effects = ArrayList<WorkforceEffect>(); val events = ArrayList<TalentEvent>()
        // development of named successors
        for ((posId, plan) in state.successors) {
            val pos = state.positions[posId] ?: continue
            val profile = taxonomy.profile(pos.role) ?: continue
            for (s in plan.values) {
                val person = state.people[s.person] ?: continue
                if (!person.isActive) continue
                if (s.development != DevelopmentKind.NONE) {
                    val perDay = Money.units(s.development.costPerDayUnits)
                    TalentLedger.spendToHouseholds("develop", s.person.value * 10_000L + posId.value, pos.company, person.region, perDay * 30L, tick, "developing ${person.name}")?.let { intents += it }
                }
                val mentor = (s.development.paceMultiplierPpm - Ppm.ONE).coerceAtLeast(0L)
                val pace = if (s.development == DevelopmentKind.NONE) 300_000L else Ppm.ONE
                for (r in profile.requirements) {
                    val skill = taxonomy.skillOrNull(r.skill) ?: continue
                    val ceiling = person.potentialPpm.coerceAtLeast(r.requiredPpm)
                    val base = SkillDynamics.learnDelta(skill.copy(learnPerYearPpm = params.developmentLearnPerYearPpm), person.level(r.skill), ceiling, 30L, Ppm.ONE, mentor)
                    val gain = Ppm.mul(base, pace)
                    if (gain > 0L) person.levels[r.skill] = (person.level(r.skill) + gain).coerceAtMost(Ppm.ONE)
                }
                s.readinessPpm = readinessFor(person, pos.role)
            }
        }
        // searches that finish
        for (pos in state.positions.values) {
            val until = pos.searchUntil
            if (pos.holder == null && until != null && tick >= until) {
                val profile = taxonomy.profile(pos.role) ?: continue
                val rnd = stream("talent.search", pos.id.value, tick.value)
                val wage = profile.baseWage.times(Rate(1_200_000L))
                val levels = candidateLevels(profile, 1_200_000L, rnd)
                val hired = addPerson(pos.company, "Hire ${state.counters.peek("person")}", pos.role, pos.region, wage, 38 + rnd.nextInt(14), tick, levels, 700_000L, 500_000L)
                if (hired is Outcome.Ok) {
                    appoint(pos.id, hired.value.id, tick)
                    events += TalentEvent.PositionFilled(pos.id, pos.company, hired.value.id, tick)
                    effects += WorkforceEffect.Hire(hired.value.cohort, 1L)
                }
            }
        }
        // loyalty, quits, retirements
        for (p in state.people.values.toList()) {
            if (!p.isActive) continue
            val ratio = wageRatio(p)
            val drift = if (ratio >= Ppm.ONE) params.loyaltyDriftPpm else -params.loyaltyDriftPpm * 2
            p.loyaltyPpm = Ppm.clamp(p.loyaltyPpm + drift)
            val posId = p.position ?: continue
            val rnd = stream("talent.exit", p.id.value, tick.value)
            val quitMonthly = Ppm.perDays(Ppm.mul(params.baseQuitAnnualPpm, (Ppm.ONE + (500_000L - p.loyaltyPpm).coerceAtLeast(0L) * 3L).coerceAtMost(6_000_000L)), 30L)
            val retireMonthly = if (p.ageYears < params.retirementAge) 0L else Ppm.perDays(minOf((p.ageYears - params.retirementAge + 1) * params.retireChanceStepPpm, Ppm.ONE), 30L)
            if (rnd.chance(Rate(retireMonthly)) || rnd.chance(Rate(quitMonthly))) {
                val pos = state.positions[posId] ?: continue
                effects += WorkforceEffect.Depart(p.cohort, 1L)
                val r = vacate(posId, tick, departed = true)
                intents += r.intents; effects += r.effects; events += r.events
            }
        }
        return SuccessionReport(intents, effects, events)
    }

    /** Everybody is a year older. */
    internal fun rollYear() {
        for (p in state.people.values) if (p.isActive) p.ageYears += 1
    }
}
