package app.many.economytycoon.talent

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick

/**
 * The three kinds of place that teach. Durations are in days (a year is 360). Money figures are whole currency units per
 * student-year at the reference wage and scale with a region's pay.
 */
enum class InstitutionKind(
    val label: String,
    val pathway: Pathway,
    val durationDays: Int,
    /** Level a graduate of an average institution reaches in the skill. */
    val graduateLevelPpm: Long,
    /** Share of an intake that finishes, at average quality. */
    val completionPpm: Long,
    /** Share of the entry cohort that wants a place, before pay and price effects. */
    val participationPpm: Long,
    /** How strongly wage premia change that share: 0 for compulsory schooling. */
    val premiumSensitivityPpm: Long,
    val benchmarkSpendUnits: Long,
    val buildCostPerSeatUnits: Long,
    val defaultTuitionUnits: Long,
) {
    SCHOOL("School", Pathway.SCHOOL, 1_440, 300_000L, 900_000L, 850_000L, 0L, 1_200L, 3_000L, 0L),
    VOCATIONAL("Vocational college", Pathway.VOCATIONAL, 720, 520_000L, 780_000L, 220_000L, 800_000L, 2_200L, 5_000L, 900L),
    UNIVERSITY("University", Pathway.UNIVERSITY, 1_440, 700_000L, 740_000L, 260_000L, 1_000_000L, 4_500L, 9_000L, 2_500L);

    val durationMonths: Int get() = durationDays / 30
}

/** Knobs of the education system. */
data class EducationParams(
    /** Day of the year (0..359) on which every institution takes its new intake and graduates the old one. */
    val intakeDayOfYear: Int = 240,
    /** Fee that makes a place unaffordable, in years of the region's average pay. */
    val affordabilityYears: Long = 1_500_000L,
    val minAffordabilityPpm: Long = 100_000L,
    val qualityStepPpm: Long = 50_000L,
    val crowdingStartsPpm: Long = 900_000L,
    val crowdingPenaltyPpm: Long = 400_000L,
    /** Completion falls by this much when an institution is full to the brim at the end of the year. */
    val completionSpreadPpm: Long = 400_000L,
    val maxCompletionPpm: Long = 980_000L,
    /** How far a graduate's level moves between the worst and best quality, around the kind's average. */
    val levelSpreadPpm: Long = 300_000L,
    val graduateSpreadPpm: Long = 120_000L,
    val referenceWage: Money = Money.units(90),
    /** Days a capacity project takes. */
    val expansionDays: Int = 360,
    val maxInstitutionsPerCompany: Int = 6,
    val maxSeatsPerInstitution: Long = 200_000L,
    val minPrivateCapacity: Long = 20L,
    /** Share of working people who leave working life each year; qualifications leave with them. */
    val retirementPpm: Long = 25_000L,
    val minPremiumPpm: Long = 400_000L,
    val maxPremiumPpm: Long = 1_800_000L,
)

/** A batch of students who started together. */
class Intake(val startTick: Long, val skill: SkillId, val initial: Long, val completionPpm: Long) {
    var enrolled: Long = initial
        internal set
    var dropCarry: Long = 0L
        internal set

    /** People who will not finish, decided when the batch starts. */
    val dropPlan: Long get() = initial - Ppm.of(initial, completionPpm)

    val dropped: Long get() = initial - enrolled

    fun endTick(kind: InstitutionKind): Long = startTick + kind.durationDays

    fun hashInto(h: CanonicalHasher) {
        h.putLong(startTick).putString(skill.key).putLong(initial).putLong(completionPpm).putLong(enrolled).putLong(dropCarry)
    }
}

/** Seats that are being built. */
data class Expansion(val readyAt: Tick, val seats: Long)

/** A school, college or university. Public ones belong to the government; private ones to a company. */
class Institution(
    val id: InstitutionId,
    val country: CountryId,
    val region: RegionId,
    val kind: InstitutionKind,
    val owner: CompanyId?,
    val openedAt: Tick,
) {
    var name: String = ""
        internal set
    var capacity: Long = 0L
        internal set
    /** Fee per student-year. */
    var tuition: Money = Money.ZERO
        internal set
    /** Spend per student-year on teaching. */
    var spend: Money = Money.ZERO
        internal set
    var qualityPpm: Long = 500_000L
        internal set
    var fields: List<SkillId> = emptyList()
        internal set
    var closedAt: Tick? = null
        internal set
    var graduatesTotal: Long = 0L
        internal set
    var droppedTotal: Long = 0L
        internal set
    var unmetLastIntake: Long = 0L
        internal set
    var revenueYear: Money = Money.ZERO
        internal set
    var costYear: Money = Money.ZERO
        internal set
    internal val intakes = ArrayList<Intake>()
    internal val expansions = ArrayList<Expansion>()

    val isPublic: Boolean get() = owner == null
    val isOpen: Boolean get() = closedAt == null
    val enrolled: Long get() = intakes.sumOf { it.enrolled }
    val freeSeats: Long get() = (capacity - enrolled).coerceAtLeast(0L)
    val occupancyPpm: Long get() = Ppm.ratio(enrolled, capacity)
    val underConstruction: Long get() = expansions.sumOf { it.seats }
    fun intakesView(): List<Intake> = intakes.toList()
    fun expansionsView(): List<Expansion> = expansions.toList()

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(country.value).putLong(region.value).putInt(kind.ordinal).putLong(owner?.value ?: -1L).putLong(openedAt.value)
        h.putString(name).putLong(capacity).putMoney(tuition).putMoney(spend).putLong(qualityPpm).putLong(closedAt?.value ?: -1L)
        h.putInt(fields.size); for (f in fields) h.putString(f.key)
        h.putLong(graduatesTotal).putLong(droppedTotal).putLong(unmetLastIntake).putMoney(revenueYear).putMoney(costYear)
        h.putInt(intakes.size); for (i in intakes) i.hashInto(h)
        h.putInt(expansions.size); for (e in expansions) h.putLong(e.readyAt.value).putLong(e.seats)
    }
}

/** People who finished at an institution, ready to join the labour pool or be hired by a campaign. */
data class Graduation(
    val institution: InstitutionId,
    val region: RegionId,
    val kind: InstitutionKind,
    val skill: SkillId,
    val count: Long,
    val levelPpm: Long,
    val tick: Tick,
)

/** Share of the working population holding each kind of qualification, kept per region as head counts. */
class QualificationStock {
    val byKind = LongArray(InstitutionKind.values().size)

    fun of(kind: InstitutionKind): Long = byKind[kind.ordinal]

    /** Weighted share of the workforce that is educated: schooling counts a little, a degree fully. */
    fun indexPpm(working: Long): Long {
        if (working <= 0L) return 0L
        val weighted = of(InstitutionKind.SCHOOL) * 350_000L + of(InstitutionKind.VOCATIONAL) * 700_000L + of(InstitutionKind.UNIVERSITY) * 1_000_000L
        return Ppm.clamp(weighted / working)
    }

    fun hashInto(h: CanonicalHasher) { for (v in byKind) h.putLong(v) }
}
