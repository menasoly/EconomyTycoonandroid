package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.workforce.CohortKey

data class TrainingParams(
    val maxActivePerCompany: Int = 6,
    val maxActivePerCohort: Int = 2,
    /** The company must hold this many days of the programme's running cost in cash to start. */
    val cashDaysRequired: Int = 30,
    val payEveryDays: Int = 7,
    val maxSeatsShareOfCohortPpm: Long = 800_000L,
    val minDays: Int = 14,
    val maxDays: Int = 720,
    /** Trainees under a bond feel it: morale falls by this much while one is running. */
    val bondMoralePpm: Long = 15_000L,
    val referenceWage: Money = Money.units(90),
)

data class StartedProgramme(val programme: TrainingProgramme, val intents: List<SettlementIntent>)

data class TrainingReport(val intents: List<SettlementIntent>, val effects: List<WorkforceEffect>, val events: List<TalentEvent>)

/**
 * Employer training. A programme takes a number of seats in one cohort, trains one of the skills its role needs, and
 * ends with a lasting gain in the cohort's levels. Courses cost money by the day (or at the end), take part of the
 * trainees' working time, and can come with a retention clause that claws money back from people who leave soon after.
 */
class TrainingService(
    private val state: TalentState,
    private val taxonomy: SkillTaxonomy,
    private val skills: CohortSkillService,
    val params: TrainingParams = TrainingParams(),
) {
    fun all(): List<TrainingProgramme> = state.programmes.values.toList()
    fun get(id: ProgrammeId): TrainingProgramme? = state.programmes[id]
    fun active(): List<TrainingProgramme> = state.programmes.values.filter { it.isActive }
    fun activeOf(company: CompanyId): List<TrainingProgramme> = state.programmes.values.filter { it.isActive && it.company == company }
    fun forCohort(key: CohortKey): List<TrainingProgramme> = state.programmes.values.filter { it.cohort == key }
    fun bondedShare(key: CohortKey, headcount: Long, tick: Tick): Long =
        Ppm.ratio(state.programmes.values.filter { it.cohort == key && it.isBonded(tick) }.sumOf { it.bondedHeads }, headcount).coerceAtMost(Ppm.ONE)

    /** Share of a cohort's working time that its running programmes take, ppm. */
    fun trainingShare(key: CohortKey, headcount: Long): Long {
        if (headcount <= 0L) return 0L
        val loss = state.programmes.values.filter { it.isActive && it.cohort == key }.sumOf { it.seats * it.kind.workLossPpm }
        return Ppm.clamp(loss / headcount)
    }

    private fun wageIndex(fact: CohortFact): Long =
        if (params.referenceWage.raw <= 0L) Ppm.ONE else Ppm.ratio(fact.marketWage.raw, params.referenceWage.raw).coerceAtLeast(100_000L)

    /** What a programme would cost per trainee, for previews and for starting it. */
    fun quote(kind: ProgrammeKind, days: Int, fact: CohortFact): Money = TrainingEconomics.costPerHead(kind, days, wageIndex(fact))

    /**
     * Starts a programme. [fee] is a price per trainee agreed with an outside provider (null for in-house training),
     * paid at once to [payee]. [cash] is what the company can spend now.
     */
    fun start(
        company: CompanyId, fact: CohortFact, skill: SkillId, kind: ProgrammeKind, seats: Long, days: Int, clause: RetentionClause?,
        cash: Money, tick: Tick, provider: ProviderId? = null, course: CourseId? = null, fee: Money? = null, payee: CompanyId? = null,
    ): Outcome<StartedProgramme> {
        val key = fact.key
        if (key.company != company) return Outcome.reject(Rules.NOT_OWNER, "that cohort belongs to another company")
        val profile = taxonomy.profile(key.role) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no skill profile for ${key.role.key}")
        if (profile.requirementFor(skill) == null) return Outcome.reject(Rules.INVALID_ARGUMENT, "${key.role.key} does not use ${skill.key}")
        if (seats <= 0L) return Outcome.reject(Rules.INVALID_QUANTITY, "train at least one person")
        if (seats > Ppm.of(fact.headcount, params.maxSeatsShareOfCohortPpm)) return Outcome.reject(Rules.INVALID_QUANTITY, "too large a share of the cohort would be away at once")
        if (days < params.minDays || days > params.maxDays) return Outcome.reject(Rules.INVALID_ARGUMENT, "a programme runs ${params.minDays} to ${params.maxDays} days")
        if (clause != null && !kind.bondable) return Outcome.reject(Rules.INVALID_ARGUMENT, "${kind.label.lowercase()} cannot carry a retention clause")
        if (activeOf(company).size >= params.maxActivePerCompany) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many programmes running")
        if (state.programmes.values.count { it.isActive && it.cohort == key } >= params.maxActivePerCohort) return Outcome.reject(Rules.LIMIT_EXCEEDED, "that cohort is already in training")
        val perHead = fee ?: quote(kind, days, fact)
        val dailyRunning = if (kind.paidAtEnd || fee != null) Money.ZERO else Money(perHead.raw / days) * seats
        val needed = if (fee != null) fee * seats else dailyRunning * params.cashDaysRequired.toLong()
        if (cash < needed) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "not enough cash to start the programme")
        val p = TrainingProgramme(
            state.counters.programme(), company, key, skill, kind, seats, tick, tick + days.toLong(), perHead, clause, provider, course, fee,
        )
        state.programmes[p.id] = p
        val intents = ArrayList<SettlementIntent>()
        if (fee != null && payee != null) {
            TalentLedger.companyToCompany("course", p.id.value, company, payee, fee * seats, tick, "course fees for ${key.role.key}")?.let { intents += it }
            p.paid = fee * seats
        }
        return Outcome.ok(StartedProgramme(p, intents))
    }

    fun cancel(id: ProgrammeId, company: CompanyId, tick: Tick): Outcome<TrainingProgramme> {
        val p = state.programmes[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown programme")
        if (p.company != company) return Outcome.reject(Rules.NOT_OWNER, "that programme is not yours")
        if (!p.isActive) return Outcome.reject(Rules.WRONG_STATE, "that programme has ended")
        p.status = ProgrammeStatus.CANCELLED
        p.finishedAt = tick
        return Outcome.ok(p)
    }

    // ----- time ---------------------------------------------------------------------------------

    /** Pays running costs, delivers monthly gains and completes programmes whose time is up. */
    fun advance(tick: Tick, inputs: TalentInputs): TrainingReport {
        val intents = ArrayList<SettlementIntent>(); val effects = ArrayList<WorkforceEffect>(); val events = ArrayList<TalentEvent>()
        val touched = java.util.TreeSet<CohortKey>()
        for (p in state.programmes.values) {
            if (!p.isActive) continue
            val fact = inputs.cohort(p.cohort)
            touched += p.cohort
            if (fact == null || fact.headcount <= 0L) { p.status = ProgrammeStatus.CANCELLED; p.finishedAt = tick; events += TalentEvent.ProgrammeCancelled(p.id, p.company, tick); continue }
            if (runsInHouse(p) && tick.value > p.startTick.value && (tick.value - p.startTick.value) % params.payEveryDays == 0L) {
                payInHouse(p, Money(dailyCost(p).raw * params.payEveryDays), tick, intents)
            }
            if (tick.value > p.startTick.value && (tick.value - p.startTick.value) % 30L == 0L) deliverMonth(p, fact)
            if (tick >= p.endTick) complete(p, fact, tick, intents, effects, events)
        }
        for (key in touched) {
            val fact = inputs.cohort(key) ?: continue
            effects += WorkforceEffect.TrainingShare(key, trainingShare(key, fact.headcount))
        }
        return TrainingReport(intents, effects, events)
    }

    private fun runsInHouse(p: TrainingProgramme) = p.fee == null && !p.kind.paidAtEnd

    private fun dailyCost(p: TrainingProgramme): Money = Money(p.totalCost.raw / (p.endTick.value - p.startTick.value).coerceAtLeast(1L))

    /** Pays [amount] of a programme's in-house cost, never more than what is still unpaid. */
    private fun payInHouse(p: TrainingProgramme, amount: Money, tick: Tick, intents: MutableList<SettlementIntent>) {
        val due = Money.min(amount, p.totalCost - p.paid)
        TalentLedger.spendToHouseholds("training", p.id.value, p.company, p.cohort.region, due, tick, "training for ${p.cohort.role.key}", Chart.OTHER_EXPENSE)?.let { intents += it; p.paid += due }
    }

    private fun deliverMonth(p: TrainingProgramme, fact: CohortFact) {
        val set = skills.get(p.cohort) ?: return
        val dist = set.dist(p.skill) ?: return
        val profile = taxonomy.profile(p.cohort.role) ?: return
        val required = profile.requirementFor(p.skill)?.requiredPpm ?: return
        val before = dist.mean()
        SkillDynamics.train(dist, p.kind.gainPerMonthPpm, Ppm.ratio(p.seats, fact.headcount), (required + p.kind.capMarginPpm).coerceAtMost(Ppm.ONE))
        p.gainDelivered += dist.mean() - before
    }

    private fun complete(p: TrainingProgramme, fact: CohortFact, tick: Tick, intents: MutableList<SettlementIntent>, effects: MutableList<WorkforceEffect>, events: MutableList<TalentEvent>) {
        p.status = ProgrammeStatus.COMPLETED
        p.finishedAt = tick
        if (runsInHouse(p)) payInHouse(p, p.totalCost, tick, intents)
        if (p.kind.paidAtEnd) {
            val lump = p.totalCost
            TalentLedger.spendToHouseholds("reimburse", p.id.value, p.company, p.cohort.region, lump, tick, "tuition reimbursed")?.let { intents += it; p.paid += lump }
        }
        if (p.kind.awardsCertificate) {
            val set = skills.get(p.cohort)
            if (set != null) set.certified[p.skill] = (set.certifiedShare(p.skill) + Ppm.ratio(p.seats, fact.headcount)).coerceAtMost(Ppm.ONE)
        }
        if (p.clause != null) {
            p.bondedHeads = p.seats
            effects += WorkforceEffect.Morale(p.cohort, -Ppm.mul(params.bondMoralePpm, Ppm.ratio(p.seats, fact.headcount)))
        }
        events += TalentEvent.ProgrammeCompleted(p.id, p.company, p.seats, tick)
    }

    /**
     * Called when people left a cohort (quit or were let go). Bonded trainees are assumed to leave in proportion to
     * their share of the cohort; each one repays part of the cost, less the longer they stayed after finishing.
     */
    fun onDepartures(key: CohortKey, leavers: Long, headcountBefore: Long, tick: Tick): TrainingReport {
        val intents = ArrayList<SettlementIntent>(); val events = ArrayList<TalentEvent>()
        if (leavers <= 0L || headcountBefore <= 0L) return TrainingReport(intents, emptyList(), events)
        for (p in state.programmes.values) {
            val clause = p.clause ?: continue
            if (p.cohort != key || !p.isBonded(tick)) continue
            val numer = leavers * p.bondedHeads + p.leaversCarry
            val gone = (numer / headcountBefore).coerceAtMost(p.bondedHeads)
            p.leaversCarry = numer % headcountBefore
            if (gone <= 0L) continue
            val finished = p.finishedAt ?: continue
            val served = (tick.value - finished.value).coerceIn(0L, clause.bondDays.toLong())
            val remaining = Ppm.ratio(clause.bondDays - served, clause.bondDays.toLong())
            val owed = Money(Ppm.of(Ppm.of(p.costPerHead.raw, clause.clawbackPpm), remaining)) * gone
            p.bondedHeads -= gone
            if (owed.isPositive) {
                TalentLedger.householdsToCompany("clawback", p.id.value, key.region, p.company, owed, tick, "training repaid", extra = tick.value)?.let { intents += it }
                p.clawedBack += owed
                events += TalentEvent.ClawbackCharged(p.id, p.company, gone, tick)
            }
        }
        return TrainingReport(intents, emptyList(), events)
    }

    /** Programmes older than [keepDays] that have ended are forgotten. */
    internal fun prune(tick: Tick, keepDays: Long = 720L) {
        state.programmes.values.removeAll { !it.isActive && (it.finishedAt?.value ?: 0L) + keepDays < tick.value && !it.isBonded(tick) }
    }
}
