package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.SettlementIntent

data class ProviderReport(val intents: List<SettlementIntent>, val events: List<TalentEvent>)

/**
 * Education as a business. A company opens a provider in a region, lists courses, and sells places to job seekers
 * and to employers. What students get out of it decides the provider's name, and the name decides who comes.
 */
class ProviderService(
    private val state: TalentState,
    private val taxonomy: SkillTaxonomy,
    private val markets: SkillMarketService,
    private val training: TrainingService,
    private val seed: Long,
    val params: ProviderParams = ProviderParams(),
    private val education: EducationParams = EducationParams(),
) {
    fun provider(id: ProviderId): TrainingProvider? = state.providers[id]
    fun providers(): List<TrainingProvider> = state.providers.values.toList()
    fun providersOf(owner: CompanyId): List<TrainingProvider> = state.providers.values.filter { it.owner == owner && !it.closed }
    fun inRegion(region: app.many.economytycoon.core.RegionId): List<TrainingProvider> = state.providers.values.filter { it.region == region && !it.closed }
    fun course(id: CourseId): Course? = state.providers.values.firstNotNullOfOrNull { it.courses[id] }

    /** Seats in employer programmes that are running now. */
    fun corporateSeats(course: Course): Long = training.all().filter { it.isActive && it.course == course.id }.sumOf { it.seats }
    fun freeSeats(course: Course): Long = (course.seats - course.students - corporateSeats(course)).coerceAtLeast(0L)

    // ----- owner actions ------------------------------------------------------------------------------

    fun open(owner: CompanyId, region: app.many.economytycoon.core.RegionId, name: String, tick: Tick, cash: Money): Outcome<Pair<TrainingProvider, List<SettlementIntent>>> {
        if (name.isBlank()) return Outcome.reject(Rules.INVALID_ARGUMENT, "a provider needs a name")
        if (providersOf(owner).size >= params.maxProvidersPerCompany) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many providers")
        if (state.providers.values.any { !it.closed && it.name.equals(name.trim(), ignoreCase = true) }) return Outcome.reject(Rules.NAME_TAKEN, "that name is in use")
        if (cash < params.setupCost) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot pay for the premises")
        val p = TrainingProvider(state.counters.provider(), owner, region, name.trim(), tick)
        p.costs = params.setupCost
        state.providers[p.id] = p
        return Outcome.ok(p to listOfNotNull(TalentLedger.spendToHouseholds("provider-setup", p.id.value, owner, region, params.setupCost, tick, "new training provider")))
    }

    private fun owned(id: ProviderId, owner: CompanyId): Outcome<TrainingProvider> {
        val p = state.providers[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown provider")
        if (p.owner != owner) return Outcome.reject(Rules.NOT_OWNER, "not your provider")
        if (p.closed) return Outcome.reject(Rules.WRONG_STATE, "the provider has closed")
        return Outcome.ok(p)
    }

    fun addCourse(owner: CompanyId, providerId: ProviderId, name: String, skill: SkillId, kind: CourseKind, seats: Long, tuition: Money, world: TalentWorld): Outcome<Course> {
        val p = when (val o = owned(providerId, owner)) { is Outcome.Ok -> o.value; is Outcome.Rejected -> return o }
        if (name.isBlank()) return Outcome.reject(Rules.INVALID_ARGUMENT, "a course needs a name")
        if (taxonomy.skillOrNull(skill) == null) return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown skill")
        if (p.courses.size >= params.maxCoursesPerProvider) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many courses")
        if (seats !in 1L..params.maxSeatsPerCourse) return Outcome.reject(Rules.INVALID_QUANTITY, "seats must be 1..${params.maxSeatsPerCourse}")
        priceCheck(tuition, p, world)?.let { return Outcome.Rejected(it) }
        val c = Course(state.counters.course(), p.id, name.trim(), skill, kind, seats, tuition)
        p.courses[c.id] = c
        return Outcome.ok(c)
    }

    private fun priceCheck(tuition: Money, p: TrainingProvider, world: TalentWorld): app.many.economytycoon.core.Rejection? {
        if (tuition < params.minTuition) return app.many.economytycoon.core.Rejection(Rules.INVALID_PRICE, "tuition below the minimum")
        val yearly = world.averageWage(p.region).raw * 360L
        if (yearly > 0L && tuition.raw > Ppm.of(yearly, params.maxTuitionYears)) return app.many.economytycoon.core.Rejection(Rules.INVALID_PRICE, "tuition far above what people can pay")
        return null
    }

    fun setTuition(owner: CompanyId, courseId: CourseId, tuition: Money, world: TalentWorld): Outcome<Course> {
        val c = course(courseId) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown course")
        val p = state.providers.getValue(c.provider)
        if (p.owner != owner) return Outcome.reject(Rules.NOT_OWNER, "not your course")
        priceCheck(tuition, p, world)?.let { return Outcome.Rejected(it) }
        c.tuition = tuition
        return Outcome.ok(c)
    }

    fun setSeats(owner: CompanyId, courseId: CourseId, seats: Long): Outcome<Course> {
        val c = course(courseId) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown course")
        if (state.providers.getValue(c.provider).owner != owner) return Outcome.reject(Rules.NOT_OWNER, "not your course")
        if (seats !in 1L..params.maxSeatsPerCourse) return Outcome.reject(Rules.INVALID_QUANTITY, "seats must be 1..${params.maxSeatsPerCourse}")
        c.seats = seats
        return Outcome.ok(c)
    }

    /** Closes a course to new students; those already in it finish. */
    fun closeCourse(owner: CompanyId, courseId: CourseId): Outcome<Course> {
        val c = course(courseId) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown course")
        if (state.providers.getValue(c.provider).owner != owner) return Outcome.reject(Rules.NOT_OWNER, "not your course")
        if (!c.open) return Outcome.reject(Rules.WRONG_STATE, "already closed")
        c.restore(false, c.enrolledTotal, c.completedTotal)
        return Outcome.ok(c)
    }

    fun setTeaching(owner: CompanyId, providerId: ProviderId, levelPpm: Long): Outcome<TrainingProvider> {
        val p = when (val o = owned(providerId, owner)) { is Outcome.Ok -> o.value; is Outcome.Rejected -> return o }
        if (levelPpm !in params.minTeachingPpm..params.maxTeachingPpm) return Outcome.reject(Rules.INVALID_ARGUMENT, "teaching level out of range")
        p.teachingLevelPpm = levelPpm
        return Outcome.ok(p)
    }

    /** Applies for [level]. Needs the quality, reputation and track record the level asks for, and its annual fee. */
    fun accredit(owner: CompanyId, providerId: ProviderId, level: Accreditation, tick: Tick, cash: Money): Outcome<Pair<TrainingProvider, List<SettlementIntent>>> {
        val p = when (val o = owned(providerId, owner)) { is Outcome.Ok -> o.value; is Outcome.Rejected -> return o }
        if (level == Accreditation.NONE) return Outcome.reject(Rules.INVALID_ARGUMENT, "choose a level to apply for")
        if (level <= p.accreditation && tick < p.accreditedUntil) return Outcome.reject(Rules.DUPLICATE, "already at or above that level")
        if (!qualifies(p, level)) return Outcome.reject(Rules.PREREQUISITE_MISSING, "quality, results or track record fall short of ${level.label}")
        if (cash < level.annualFee) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot pay the accreditation fee")
        p.accreditation = level; p.accreditedUntil = tick + params.accreditationYearDays.toLong()
        p.costs = p.costs + level.annualFee
        return Outcome.ok(p to listOfNotNull(TalentLedger.fee("accreditation", p.id.value, owner, level.annualFee, tick, "accreditation fee", extra = level.ordinal.toLong())))
    }

    fun qualifies(p: TrainingProvider, level: Accreditation): Boolean =
        p.qualityPpm >= level.minQualityPpm && p.reputationPpm >= level.minReputationPpm && p.graduates >= level.minGraduates

    fun close(owner: CompanyId, providerId: ProviderId, tick: Tick): Outcome<TrainingProvider> {
        val p = when (val o = owned(providerId, owner)) { is Outcome.Ok -> o.value; is Outcome.Rejected -> return o }
        if (training.all().any { it.isActive && it.provider == p.id }) return Outcome.reject(Rules.WRONG_STATE, "employer programmes are still running")
        for (c in p.courses.values) {
            c.restore(false, c.enrolledTotal, c.completedTotal)
            for (i in c.intakes) markets.release(p.region, c.skill, i.students)
            c.intakes.clear()
        }
        p.closed = true
        return Outcome.ok(p)
    }

    // ----- employers ------------------------------------------------------------------------------------

    /** What an employer pays per place: the list price less the corporate discount. */
    fun corporatePrice(course: Course): Money = Money(course.tuition.raw - Ppm.of(course.tuition.raw, params.corporateDiscountPpm))

    /** Sends [seats] people of a cohort on a course. The programme itself is run by the training service. */
    fun buySeats(company: CompanyId, courseId: CourseId, fact: CohortFact, seats: Long, clause: RetentionClause?, cash: Money, tick: Tick): Outcome<StartedProgramme> {
        val c = course(courseId) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown course")
        val p = state.providers.getValue(c.provider)
        if (p.closed || !c.open) return Outcome.reject(Rules.WRONG_STATE, "the course is not taking students")
        if (fact.key.region != p.region) return Outcome.reject(Rules.INVALID_ARGUMENT, "the course is in another region")
        if (seats > freeSeats(c)) return Outcome.reject(Rules.INSUFFICIENT_CAPACITY, "only ${freeSeats(c)} places are free")
        val days = c.kind.days.coerceIn(training.params.minDays, training.params.maxDays)
        val started = training.start(company, fact, c.skill, ProgrammeKind.CERTIFICATION, seats, days, clause, cash, tick, p.id, c.id, corporatePrice(c), p.owner)
        if (started is Outcome.Ok) p.revenue = p.revenue + corporatePrice(c) * seats
        return started
    }

    // ----- time ---------------------------------------------------------------------------------------------

    /** Daily: courses finish. Monthly: students enrol, teachers are paid, quality moves, accreditation is reviewed. */
    fun advance(tick: Tick, world: TalentWorld): ProviderReport {
        val intents = ArrayList<SettlementIntent>(); val events = ArrayList<TalentEvent>()
        for (p in state.providers.values) {
            if (p.closed) continue
            for (c in p.courses.values) finish(p, c, tick, events)
        }
        if (tick.value > 0L && tick.value % 30L == 0L) for (p in state.providers.values) if (!p.closed) monthly(p, tick, world, intents, events)
        return ProviderReport(intents, events)
    }

    private fun completionFor(p: TrainingProvider, c: Course): Long =
        Ppm.clamp(Ppm.mul(c.kind.baseCompletionPpm, 600_000L + Ppm.mul(p.qualityPpm, 400_000L)))

    private fun finish(p: TrainingProvider, c: Course, tick: Tick, events: MutableList<TalentEvent>) {
        val done = c.intakes.filter { it.end <= tick }
        if (done.isEmpty()) return
        c.intakes.removeAll(done)
        for (i in done) {
            val total = i.students.total
            if (total <= 0L) continue
            val rate = completionFor(p, c)
            val finishers = i.students.take(Ppm.of(total, rate), TakePolicy.PROPORTIONAL)
            val gain = Ppm.mul(c.kind.gainPpm, 700_000L + Ppm.mul(p.qualityPpm, 600_000L))
            SkillDynamics.train(finishers, gain, Ppm.ONE, Ppm.ONE)
            val n = finishers.total
            val gainedMean = if (n > 0L) finishers.mean() - i.startMean else 0L
            markets.deposit(p.region, c.skill, finishers)
            markets.release(p.region, c.skill, i.students)
            c.restore(c.open, c.enrolledTotal, c.completedTotal + n)
            p.graduates += n; p.dropouts += total - n
            p.gainSumPpm += gainedMean.coerceAtLeast(0L) * n
            val score = (Ppm.ratio(n, total) + Ppm.clamp(Ppm.ratio(gainedMean.coerceAtLeast(0L), 250_000L))) / 2L
            if (total >= params.minReviewGraduates) p.reputationPpm = Ppm.approach(p.reputationPpm, score, params.reputationPaceInPpm)
            events.add(TalentEvent.CourseCompleted(p.id, c.id, n, tick))
        }
    }

    private fun monthly(p: TrainingProvider, tick: Tick, world: TalentWorld, intents: MutableList<SettlementIntent>, events: MutableList<TalentEvent>) {
        val wage = world.averageWage(p.region)
        var students = 0L
        for (c in p.courses.values) {
            if (c.open) enrol(p, c, tick, world, intents)
            students += c.students
        }
        val instructors = if (students == 0L) 0L else (students + params.studentsPerInstructor - 1L) / params.studentsPerInstructor
        val pay = Money(Ppm.mul(Ppm.mul(wage.raw * 30L, params.instructorPayPpm), p.teachingLevelPpm)) * instructors
        TalentLedger.spendToHouseholds("instructors", p.id.value, p.owner, p.region, pay, tick, "instructors")?.let { intents.add(it) }
        p.costs = p.costs + pay
        val target = Ppm.ratio(p.teachingLevelPpm, p.teachingLevelPpm + params.qualityHalfPoint)
        p.qualityPpm = Ppm.approach(p.qualityPpm, target, params.qualityPaceMonthlyPpm)
        review(p, tick, events, intents)
    }

    private fun trust(p: TrainingProvider): Long = Ppm.mul(p.accreditation.trustPpm, 700_000L + Ppm.mul(p.reputationPpm, 600_000L))

    private fun enrol(p: TrainingProvider, c: Course, tick: Tick, world: TalentWorld, intents: MutableList<SettlementIntent>) {
        val room = freeSeats(c)
        if (room <= 0L) return
        val pool = markets.poolSize(p.region, c.skill)
        if (pool <= 0L) return
        val afford = EducationDemand.affordabilityPpm(c.tuition, world.averageWage(p.region), education)
        val pull = EducationDemand.attractionPpm(Ppm.mul(p.qualityPpm, trust(p)).coerceAtMost(Ppm.ONE), afford)
        val premium = markets.premium(p.region, c.skill).coerceIn(500_000L, 2_000_000L)
        val rate = Ppm.mul(Ppm.mul(params.intakeSharePpm, pull), premium)
        val exact = pool * rate
        val r = DeterministicRandom(RandomProvider.deriveSeed(seed, "provider.intake", c.id.value * 1_000_003L + tick.value))
        val want = exact / Ppm.ONE + if (r.nextLong(Ppm.ONE) < exact % Ppm.ONE) 1L else 0L
        val n = minOf(room, want, pool)
        if (n <= 0L) return
        val group = markets.takeFromPool(p.region, c.skill, n)
        if (group.total <= 0L) return
        c.intakes.add(CourseIntake(tick, tick + c.kind.days.toLong(), group, group.mean()))
        c.restore(c.open, c.enrolledTotal + group.total, c.completedTotal)
        val fees = c.tuition * group.total
        p.revenue = p.revenue + fees
        TalentLedger.householdsToCompany("tuition", c.id.value, p.region, p.owner, fees, tick, "course tuition")?.let { intents.add(it) }
    }

    /** Renews accreditation once a year at the best level the provider still earns, or withdraws it. */
    private fun review(p: TrainingProvider, tick: Tick, events: MutableList<TalentEvent>, intents: MutableList<SettlementIntent>) {
        if (p.accreditation == Accreditation.NONE || tick < p.accreditedUntil) return
        var level = p.accreditation
        while (level != Accreditation.NONE && !qualifies(p, level)) level = Accreditation.values()[level.ordinal - 1]
        if (level != Accreditation.NONE) {
            p.accreditedUntil = tick + params.accreditationYearDays.toLong()
            p.costs = p.costs + level.annualFee
            TalentLedger.fee("accreditation", p.id.value, p.owner, level.annualFee, tick, "accreditation renewal", extra = level.ordinal.toLong())?.let { intents.add(it) }
        }
        if (level != p.accreditation) {
            p.accreditation = level
            events.add(TalentEvent.AccreditationChanged(p.id, level, tick))
        }
    }

    // ----- outcomes ranking -----------------------------------------------------------------------------------

    fun score(p: TrainingProvider): Long {
        val results = (p.completionRatePpm + Ppm.clamp(Ppm.ratio(p.meanGainPpm, 250_000L))) / 2L
        val accredited = Ppm.ratio(p.accreditation.ordinal.toLong(), Accreditation.PREMIER.ordinal.toLong())
        return Ppm.mul(p.qualityPpm, 350_000L) + Ppm.mul(p.reputationPpm, 250_000L) + Ppm.mul(results, 250_000L) + Ppm.mul(accredited, 150_000L)
    }

    /** All providers in a region (or everywhere), best first. */
    fun ranking(region: app.many.economytycoon.core.RegionId? = null): List<ProviderRank> {
        val list = state.providers.values.filter { !it.closed && (region == null || it.region == region) }
            .sortedWith(compareByDescending<TrainingProvider> { score(it) }.thenBy { it.id })
        return list.mapIndexed { i, p -> ProviderRank(p.id, i + 1, score(p), p.qualityPpm, p.completionRatePpm, p.meanGainPpm, p.graduates) }
    }
}
