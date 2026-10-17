package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.SettlementIntent

/** A new institution and what it cost to start. */
data class OpenResult(val institution: Institution, val intents: List<SettlementIntent>)

/** A capacity project that was started. */
data class ExpansionResult(val institution: Institution, val seats: Long, val readyAt: Tick, val intents: List<SettlementIntent>)

/** What a pass of the education system produced. */
data class EducationReport(
    val graduations: List<Graduation>,
    val intents: List<SettlementIntent>,
    val events: List<TalentEvent>,
) {
    companion object { val NONE = EducationReport(emptyList(), emptyList(), emptyList()) }
}

/**
 * The school, college and university system. Students enrol on the intake day, drop out a little each month, and
 * graduate a fixed number of years later at a level set by teaching quality. How many enrol depends on what the
 * subject pays, what it costs and how many places there are, so a region that pays well for engineers fills its
 * engineering courses a few years before the graduates arrive. Money is only described, as intents.
 */
class EducationService(private val state: TalentState, private val taxonomy: SkillTaxonomy, val params: EducationParams = EducationParams()) {

    fun all(): List<Institution> = state.institutions.values.toList()
    fun get(id: InstitutionId): Institution? = state.institutions[id]
    fun open(): List<Institution> = state.institutions.values.filter { it.isOpen }
    fun inRegion(region: RegionId, kind: InstitutionKind? = null): List<Institution> =
        state.institutions.values.filter { it.isOpen && it.region == region && (kind == null || it.kind == kind) }
    fun ownedBy(company: CompanyId): List<Institution> = state.institutions.values.filter { it.owner == company && it.isOpen }

    fun qualifications(region: RegionId): QualificationStock = state.qualifications[region] ?: QualificationStock()

    /** Education level the stock of qualified people gives a region, 0..1,000,000. */
    fun educationIndex(region: RegionId, workingAge: Long): Long = qualifications(region).indexPpm(workingAge)

    fun seats(region: RegionId, kind: InstitutionKind): Long = inRegion(region, kind).sumOf { it.capacity }

    // ----- starting, changing and closing ------------------------------------------------------

    private fun fieldsFor(kind: InstitutionKind, fields: List<SkillId>): Outcome<List<SkillId>> {
        if (kind == InstitutionKind.SCHOOL) return Outcome.ok(listOf(DefaultTaxonomy.GENERAL))
        if (fields.isEmpty()) return Outcome.reject(Rules.INVALID_ARGUMENT, "choose at least one subject to teach")
        if (fields.toSet().size != fields.size) return Outcome.reject(Rules.DUPLICATE, "a subject is listed twice")
        for (f in fields) {
            val s = taxonomy.skillOrNull(f) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown subject ${f.key}")
            if (s.pathway != kind.pathway) return Outcome.reject(Rules.INVALID_ARGUMENT, "${s.name} is not taught at a ${kind.label.lowercase()}")
        }
        return Outcome.ok(fields.sorted())
    }

    private fun wageIndex(dailyWage: Money): Long =
        if (params.referenceWage.raw <= 0L) Ppm.ONE else Ppm.ratio(dailyWage.raw, params.referenceWage.raw)

    fun buildCost(kind: InstitutionKind, seats: Long, dailyWage: Money): Money =
        Money.units(kind.buildCostPerSeatUnits * seats).times(app.many.economytycoon.core.Rate(wageIndex(dailyWage)))

    /** A company opens a private institution. The cost of the buildings is paid at once. */
    fun open(
        owner: CompanyId, country: CountryId, region: RegionId, kind: InstitutionKind, name: String, capacity: Long,
        tuition: Money, spend: Money, fields: List<SkillId>, tick: Tick, dailyWage: Money,
    ): Outcome<OpenResult> {
        if (name.isBlank()) return Outcome.reject(Rules.INVALID_ARGUMENT, "an institution needs a name")
        if (capacity < params.minPrivateCapacity || capacity > params.maxSeatsPerInstitution) return Outcome.reject(Rules.INVALID_QUANTITY, "capacity must be between ${params.minPrivateCapacity} and ${params.maxSeatsPerInstitution}")
        if (tuition.isNegative || spend.isNegative) return Outcome.reject(Rules.INVALID_PRICE, "fees and spending cannot be negative")
        if (ownedBy(owner).size >= params.maxInstitutionsPerCompany) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many institutions")
        val f = fieldsFor(kind, fields).let { if (it is Outcome.Rejected) return it else (it as Outcome.Ok).value }
        val inst = Institution(state.counters.institution(), country, region, kind, owner, tick)
        inst.name = name.trim(); inst.capacity = capacity; inst.tuition = tuition; inst.spend = spend; inst.fields = f
        inst.qualityPpm = 400_000L
        state.institutions[inst.id] = inst
        val cost = buildCost(kind, capacity, dailyWage)
        return Outcome.ok(OpenResult(inst, listOfNotNull(TalentLedger.spendToHouseholds("edubuild", inst.id.value, owner, region, cost, tick, "build ${inst.name}", Chart.FIXED_ASSETS))))
    }

    /** Registers a government institution, for new games and for tests. */
    fun registerPublic(country: CountryId, region: RegionId, kind: InstitutionKind, name: String, capacity: Long, spend: Money, fields: List<SkillId>, tick: Tick): Outcome<Institution> {
        if (capacity <= 0L) return Outcome.reject(Rules.INVALID_QUANTITY, "capacity must be positive")
        val f = fieldsFor(kind, fields).let { if (it is Outcome.Rejected) return it else (it as Outcome.Ok).value }
        val inst = Institution(state.counters.institution(), country, region, kind, null, tick)
        inst.name = name; inst.capacity = capacity; inst.spend = spend; inst.fields = f; inst.qualityPpm = 500_000L
        state.institutions[inst.id] = inst
        return Outcome.ok(inst)
    }

    /**
     * Gives a region the qualified people it already has when the module is switched on, so that the education index
     * starts where the world says it is. Most people are taken to have finished school; the rest of the weight is
     * split between college and university graduates.
     */
    fun seedQualifications(region: RegionId, workingAge: Long, indexPpm: Long) {
        if (workingAge <= 0L || state.qualifications.containsKey(region)) return
        val total = Ppm.of(workingAge, indexPpm.coerceIn(0L, Ppm.ONE))
        val school = minOf(Ppm.of(workingAge, 900_000L), total * Ppm.ONE / 350_000L)
        val rest = (total - school * 350_000L / Ppm.ONE).coerceAtLeast(0L)
        val university = rest * 400_000L / Ppm.ONE
        val vocational = (rest - university) * Ppm.ONE / 700_000L
        val q = QualificationStock()
        q.byKind[InstitutionKind.SCHOOL.ordinal] = school
        q.byKind[InstitutionKind.VOCATIONAL.ordinal] = vocational
        q.byKind[InstitutionKind.UNIVERSITY.ordinal] = university
        state.qualifications[region] = q
    }

    private fun owned(id: InstitutionId, company: CompanyId): Outcome<Institution> {
        val i = state.institutions[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown institution")
        if (i.owner != company) return Outcome.reject(Rules.NOT_OWNER, "that institution is not yours")
        if (!i.isOpen) return Outcome.reject(Rules.WRONG_STATE, "that institution is closed")
        return Outcome.ok(i)
    }

    fun setTuition(id: InstitutionId, company: CompanyId, tuition: Money): Outcome<Institution> = owned(id, company).flatMap {
        if (tuition.isNegative) Outcome.reject(Rules.INVALID_PRICE, "a fee cannot be negative") else { it.tuition = tuition; Outcome.ok(it) }
    }

    fun setSpend(id: InstitutionId, company: CompanyId, spend: Money): Outcome<Institution> = owned(id, company).flatMap {
        if (spend.isNegative) Outcome.reject(Rules.INVALID_PRICE, "spending cannot be negative") else { it.spend = spend; Outcome.ok(it) }
    }

    fun setFields(id: InstitutionId, company: CompanyId, fields: List<SkillId>): Outcome<Institution> = owned(id, company).flatMap { i ->
        fieldsFor(i.kind, fields).map { f -> i.fields = f; i }
    }

    /** Starts building [seats] more places; they open after [EducationParams.expansionDays]. */
    fun expand(id: InstitutionId, company: CompanyId, seats: Long, tick: Tick, dailyWage: Money): Outcome<ExpansionResult> = owned(id, company).flatMap { i ->
        if (seats <= 0L) return@flatMap Outcome.reject(Rules.INVALID_QUANTITY, "build at least one place")
        if (i.capacity + i.underConstruction + seats > params.maxSeatsPerInstitution) return@flatMap Outcome.reject(Rules.LIMIT_EXCEEDED, "that would be too large")
        val ready = tick + params.expansionDays.toLong()
        i.expansions.add(Expansion(ready, seats))
        val cost = buildCost(i.kind, seats, dailyWage)
        Outcome.ok(ExpansionResult(i, seats, ready, listOfNotNull(TalentLedger.spendToHouseholds("eduexpand", i.id.value, company, i.region, cost, tick, "expand ${i.name}", Chart.FIXED_ASSETS))))
    }

    /** Closes a private institution; students in the pipeline leave without finishing. */
    fun close(id: InstitutionId, company: CompanyId, tick: Tick): Outcome<Institution> = owned(id, company).map { i ->
        i.closedAt = tick
        i.droppedTotal += i.enrolled
        i.intakes.clear(); i.expansions.clear()
        i
    }

    // ----- time ----------------------------------------------------------------------------------

    /** Runs whatever falls on [tick]: monthly upkeep and, on the intake day, graduation and enrolment. */
    fun advance(tick: Tick, world: TalentWorld, premium: (RegionId, SkillId) -> Long): EducationReport {
        val graduations = ArrayList<Graduation>(); val intents = ArrayList<SettlementIntent>(); val events = ArrayList<TalentEvent>()
        if (tick.value > 0L && tick.value % 30L == 0L) monthly(tick, world, intents, events)
        if (tick.value % 360L == params.intakeDayOfYear.toLong()) yearly(tick, world, premium, graduations, events)
        return EducationReport(graduations, intents, events)
    }

    private fun monthly(tick: Tick, world: TalentWorld, intents: MutableList<SettlementIntent>, events: MutableList<TalentEvent>) {
        for (i in state.institutions.values) {
            if (!i.isOpen) continue
            val done = i.expansions.filter { it.readyAt <= tick }
            if (done.isNotEmpty()) {
                val seats = done.sumOf { it.seats }
                i.capacity += seats
                i.expansions.removeAll(done)
                events += TalentEvent.CapacityReady(i.id, seats, tick)
            }
            val months = i.kind.durationMonths
            for (intake in i.intakes) {
                if (tick.value <= intake.startTick) continue
                val x = intake.dropPlan + intake.dropCarry
                val drop = minOf(x / months, intake.enrolled)
                intake.dropCarry = x % months
                intake.enrolled -= drop
            }
            val wage = world.averageWage(i.region)
            val target = EducationDemand.qualityTargetPpm(i.spend, EducationDemand.benchmarkSpend(i.kind, wage, params), i.occupancyPpm, params)
            i.qualityPpm = Ppm.approach(i.qualityPpm, target, params.qualityStepPpm)
            val students = i.enrolled
            val cost = i.spend.scale(students, 12L)
            i.costYear += cost
            if (i.owner != null) {
                val revenue = i.tuition.scale(students, 12L)
                i.revenueYear += revenue
                TalentLedger.householdsToCompany("tuition", i.id.value, i.region, i.owner, revenue, tick, "tuition at ${i.name}")?.let { intents += it }
                TalentLedger.spendToHouseholds("teaching", i.id.value, i.owner, i.region, cost, tick, "teaching at ${i.name}")?.let { intents += it }
            } else {
                TalentLedger.publicFunding(i.id.value, i.country, i.region, cost, tick)?.let { intents += it }
            }
        }
    }

    private fun avgPremium(region: RegionId, kind: InstitutionKind, premium: (RegionId, SkillId) -> Long): Long {
        val skills = taxonomy.skillsOf(kind.pathway)
        if (skills.isEmpty()) return Ppm.ONE
        return skills.sumOf { premium(region, it.id) } / skills.size
    }

    private fun yearly(tick: Tick, world: TalentWorld, premium: (RegionId, SkillId) -> Long, out: MutableList<Graduation>, events: MutableList<TalentEvent>) {
        // 1. finish the classes whose time is up
        for (i in state.institutions.values) {
            if (!i.isOpen) continue
            val finished = i.intakes.filter { it.endTick(i.kind) <= tick.value }
            for (intake in finished) {
                val completing = intake.initial - intake.dropPlan
                i.droppedTotal += intake.initial - completing
                i.graduatesTotal += completing
                i.intakes.remove(intake)
                if (completing <= 0L) continue
                val level = EducationDemand.graduateLevelPpm(i.kind, i.qualityPpm, params)
                state.qualifications.getOrPut(i.region) { QualificationStock() }.byKind[i.kind.ordinal] += completing
                events += TalentEvent.ClassGraduated(i.id, intake.skill, completing, level, tick)
                if (i.kind == InstitutionKind.SCHOOL) {
                    state.leavers.getOrPut(i.region) { SkillDistribution() }.addAll(SkillDistribution.around(level, params.graduateSpreadPpm, completing))
                } else {
                    out += Graduation(i.id, i.region, i.kind, intake.skill, completing, level, tick)
                }
            }
            i.revenueYear = Money.ZERO; i.costYear = Money.ZERO
        }
        // 2. enrol the new year, region by region
        for (region in world.allRegions()) {
            val wage = world.averageWage(region)
            enrol(region, InstitutionKind.SCHOOL, world.youthCohort(region), wage, premium, tick, events)
            val leavers = state.leavers.getOrPut(region) { SkillDistribution() }
            for (kind in listOf(InstitutionKind.VOCATIONAL, InstitutionKind.UNIVERSITY)) {
                val placed = enrol(region, kind, leavers.total, wage, premium, tick, events)
                leavers.take(placed, TakePolicy.BEST_FIRST)
            }
            // whoever is left goes to look for work
            if (!leavers.isEmpty) {
                val n = leavers.total
                out += Graduation(InstitutionId(0L), region, InstitutionKind.SCHOOL, DefaultTaxonomy.GENERAL, n, leavers.mean(), tick)
                leavers.clear()
            }
            // people retire and take their qualifications with them
            state.qualifications[region]?.let { q ->
                for (k in q.byKind.indices) q.byKind[k] -= Ppm.of(q.byKind[k], params.retirementPpm)
            }
        }
    }

    /** Returns how many students were placed. */
    private fun enrol(region: RegionId, kind: InstitutionKind, entrants: Long, wage: Money, premium: (RegionId, SkillId) -> Long, tick: Tick, events: MutableList<TalentEvent>): Long {
        val places = inRegion(region, kind)
        if (places.isEmpty() || entrants <= 0L) return 0L
        val pull = avgPremium(region, kind, premium)
        val seekers = Ppm.of(entrants, EducationDemand.participationPpm(kind, pull, params))
        val weights = places.map { i ->
            SeatOffer(i.id, EducationDemand.attractionPpm(i.qualityPpm, EducationDemand.affordabilityPpm(i.tuition, wage, params)), i.freeSeats)
        }
        val (placed, unmet) = EducationDemand.allocate(seekers, weights)
        var total = 0L
        for (i in places) {
            val n = placed[i.id] ?: 0L
            i.unmetLastIntake = if (places.size == 1) unmet else unmet * n / seekers.coerceAtLeast(1L)
            if (n <= 0L) continue
            val completion = EducationDemand.completionPpm(kind, i.qualityPpm, Ppm.ratio(i.enrolled + n, i.capacity), params)
            val split = EducationDemand.splitByField(n, i.fields) { premium(region, it) }
            for ((skill, count) in split) i.intakes.add(Intake(tick.value, skill, count, completion))
            total += n
        }
        if (unmet > 0L && Ppm.ratio(unmet, seekers) > 50_000L) events += TalentEvent.PlacesShort(region, kind, unmet, tick)
        return total
    }
}
