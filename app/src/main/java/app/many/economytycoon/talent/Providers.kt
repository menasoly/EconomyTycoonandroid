package app.many.economytycoon.talent

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick

/**
 * A recognition level for a training provider. Employers and students trust a recognised name: [trustPpm] scales
 * demand, and the level has to be earned (quality and results) and renewed every year for [annualFeeUnits].
 */
enum class Accreditation(
    val label: String,
    val minQualityPpm: Long,
    val minReputationPpm: Long,
    val minGraduates: Long,
    val annualFeeUnits: Long,
    val trustPpm: Long,
) {
    NONE("Unaccredited", 0L, 0L, 0L, 0L, 700_000L),
    BASIC("Basic", 350_000L, 0L, 0L, 2_000L, 900_000L),
    RECOGNISED("Recognised", 550_000L, 450_000L, 40L, 6_000L, 1_100_000L),
    PREMIER("Premier", 750_000L, 650_000L, 150L, 15_000L, 1_350_000L);

    val annualFee: Money get() = Money.units(annualFeeUnits)
}

/** What a course teaches and how: [gainPpm] is the level a graduate gains, [baseCompletionPpm] the share who finish. */
enum class CourseKind(val label: String, val days: Int, val gainPpm: Long, val baseCompletionPpm: Long, val awardsCertificate: Boolean) {
    EVENING("Evening classes", 90, 90_000L, 750_000L, false),
    BOOTCAMP("Bootcamp", 120, 180_000L, 700_000L, false),
    CERTIFICATE("Certificate course", 240, 220_000L, 800_000L, true),
    DIPLOMA("Diploma", 540, 320_000L, 780_000L, true);
}

/** Students who started together and finish together. */
class CourseIntake(val start: Tick, val end: Tick, val students: SkillDistribution, val startMean: Long) {
    fun hashInto(h: CanonicalHasher) { h.putLong(start.value).putLong(end.value).putLong(startMean); students.hashInto(h) }
}

class Course(
    val id: CourseId,
    val provider: ProviderId,
    val name: String,
    val skill: SkillId,
    val kind: CourseKind,
    seats: Long,
    tuition: Money,
) {
    var seats: Long = seats
        internal set
    var tuition: Money = tuition
        internal set
    var open: Boolean = true
        internal set
    internal val intakes = ArrayList<CourseIntake>()
    var enrolledTotal: Long = 0L
        internal set
    var completedTotal: Long = 0L
        internal set

    val students: Long get() = intakes.sumOf { it.students.total }

    internal fun restore(open: Boolean, enrolled: Long, completed: Long) { this.open = open; enrolledTotal = enrolled; completedTotal = completed }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(provider.value).putString(name).putString(skill.key).putInt(kind.ordinal).putLong(seats).putMoney(tuition)
        h.putBoolean(open).putLong(enrolledTotal).putLong(completedTotal).putInt(intakes.size)
        for (i in intakes) i.hashInto(h)
    }
}

class TrainingProvider(val id: ProviderId, val owner: CompanyId, val region: RegionId, val name: String, val openedAt: Tick) {
    var qualityPpm: Long = 400_000L
        internal set
    /** How much is spent on teachers compared with the going rate, ppm (1,000,000 is par). */
    var teachingLevelPpm: Long = 1_000_000L
        internal set
    var accreditation: Accreditation = Accreditation.NONE
        internal set
    var accreditedUntil: Tick = Tick(0)
        internal set
    var reputationPpm: Long = 400_000L
        internal set
    var closed: Boolean = false
        internal set
    var revenue: Money = Money.ZERO
        internal set
    var costs: Money = Money.ZERO
        internal set
    var graduates: Long = 0L
        internal set
    var dropouts: Long = 0L
        internal set
    var gainSumPpm: Long = 0L
        internal set
    internal val courses = java.util.TreeMap<CourseId, Course>()

    val completionRatePpm: Long get() = Ppm.ratio(graduates, graduates + dropouts)
    val meanGainPpm: Long get() = if (graduates == 0L) 0L else gainSumPpm / graduates
    fun courseList(): List<Course> = courses.values.toList()

    internal fun restore(
        quality: Long, teaching: Long, acc: Accreditation, until: Tick, reputation: Long, closed: Boolean,
        revenue: Money, costs: Money, graduates: Long, dropouts: Long, gain: Long,
    ) {
        qualityPpm = quality; teachingLevelPpm = teaching; accreditation = acc; accreditedUntil = until; reputationPpm = reputation
        this.closed = closed; this.revenue = revenue; this.costs = costs; this.graduates = graduates; this.dropouts = dropouts; gainSumPpm = gain
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(owner.value).putLong(region.value).putString(name).putLong(openedAt.value)
        h.putLong(qualityPpm).putLong(teachingLevelPpm).putInt(accreditation.ordinal).putLong(accreditedUntil.value).putLong(reputationPpm).putBoolean(closed)
        h.putMoney(revenue).putMoney(costs).putLong(graduates).putLong(dropouts).putLong(gainSumPpm).putInt(courses.size)
        for (c in courses.values) c.hashInto(h)
    }
}

data class ProviderParams(
    val setupCost: Money = Money.units(60_000),
    val maxProvidersPerCompany: Int = 3,
    val maxCoursesPerProvider: Int = 8,
    val maxSeatsPerCourse: Long = 2_000L,
    val minTeachingPpm: Long = 100_000L,
    val maxTeachingPpm: Long = 2_000_000L,
    /** Students one instructor can teach. */
    val studentsPerInstructor: Long = 20L,
    /** Instructors earn this multiple of the regional average wage. */
    val instructorPayPpm: Long = 1_600_000L,
    val qualityPaceMonthlyPpm: Long = 100_000L,
    val qualityHalfPoint: Long = 250_000L,
    /** Monthly share of the job seekers in the pool who look at one course at full attraction. */
    val intakeSharePpm: Long = 12_000L,
    val corporateDiscountPpm: Long = 100_000L,
    val accreditationYearDays: Int = 360,
    val reputationPaceInPpm: Long = 200_000L,
    val minTuition: Money = Money.units(20),
    val maxTuitionYears: Long = 400_000L,
    val minReviewGraduates: Long = 5L,
)

/** One line of the outcomes table. */
data class ProviderRank(val provider: ProviderId, val rank: Int, val scorePpm: Long, val qualityPpm: Long, val completionPpm: Long, val meanGainPpm: Long, val graduates: Long)
