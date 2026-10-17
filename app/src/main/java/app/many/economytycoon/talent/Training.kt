package app.many.economytycoon.talent

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.workforce.CohortKey

/**
 * The ways an employer trains its people. Costs are whole currency units per trainee per day at the reference wage;
 * [gainPerMonthPpm] is the level a trainee gains a month, [capMarginPpm] how far above the job's requirement the
 * course can take them, and [workLossPpm] the share of a trainee's time taken away from work.
 */
enum class ProgrammeKind(
    val label: String,
    val defaultDays: Int,
    val costPerHeadDayUnits: Long,
    val gainPerMonthPpm: Long,
    val capMarginPpm: Long,
    val workLossPpm: Long,
    /** Paid when the course ends, and only for people who finish it. */
    val paidAtEnd: Boolean,
    val awardsCertificate: Boolean,
    val bondable: Boolean,
) {
    ON_THE_JOB("On-the-job coaching", 90, 4L, 22_000L, 100_000L, 150_000L, false, false, false),
    APPRENTICESHIP("Apprenticeship", 360, 9L, 30_000L, 200_000L, 400_000L, false, false, true),
    CERTIFICATION("Certification course", 60, 35L, 70_000L, 150_000L, 500_000L, false, true, true),
    TUITION_REIMBURSEMENT("Tuition reimbursement", 540, 8L, 18_000L, 250_000L, 100_000L, true, true, true);
}

enum class ProgrammeStatus { ACTIVE, COMPLETED, CANCELLED }

/** Trainees who leave within [bondDays] of finishing repay [clawbackPpm] of what the course cost per head, less for each month served. */
data class RetentionClause(val bondDays: Int, val clawbackPpm: Long) {
    init {
        require(bondDays in 1..1_080) { "bond must be between a day and three years" }
        require(clawbackPpm in 1..Ppm.ONE) { "clawback must be a share of the cost" }
    }
}

class TrainingProgramme(
    val id: ProgrammeId,
    val company: CompanyId,
    val cohort: CohortKey,
    val skill: SkillId,
    val kind: ProgrammeKind,
    val seats: Long,
    val startTick: Tick,
    val endTick: Tick,
    val costPerHead: Money,
    val clause: RetentionClause?,
    /** Where the course is taught, when it is bought from a training provider. */
    val provider: ProviderId?,
    val course: CourseId?,
    /** Price per trainee agreed with an outside provider, paid when the programme starts. */
    val fee: Money? = null,
) {
    var status: ProgrammeStatus = ProgrammeStatus.ACTIVE
        internal set
    var paid: Money = Money.ZERO
        internal set
    var gainDelivered: Long = 0L
        internal set
    var finishedAt: Tick? = null
        internal set
    var bondedHeads: Long = 0L
        internal set
    var leaversCarry: Long = 0L
        internal set
    var clawedBack: Money = Money.ZERO
        internal set

    val isActive: Boolean get() = status == ProgrammeStatus.ACTIVE
    val totalCost: Money get() = costPerHead * seats
    val bondUntil: Tick? get() = finishedAt?.let { f -> clause?.let { f + it.bondDays.toLong() } }
    fun isBonded(tick: Tick): Boolean = bondedHeads > 0L && bondUntil?.let { tick <= it } == true

    internal fun restore(
        status: ProgrammeStatus, paid: Money, gain: Long, finished: Tick?, bonded: Long, carry: Long, clawed: Money,
    ) {
        this.status = status; this.paid = paid; gainDelivered = gain; finishedAt = finished; bondedHeads = bonded; leaversCarry = carry; clawedBack = clawed
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(company.value).putLong(cohort.company.value).putLong(cohort.facility.value).putString(cohort.role.key).putLong(cohort.region.value)
        h.putString(skill.key).putInt(kind.ordinal).putLong(seats).putLong(startTick.value).putLong(endTick.value).putMoney(costPerHead)
        h.putInt(clause?.bondDays ?: -1).putLong(clause?.clawbackPpm ?: -1L).putLong(provider?.value ?: -1L).putLong(course?.value ?: -1L).putLong(fee?.raw ?: -1L)
        h.putInt(status.ordinal).putMoney(paid).putLong(gainDelivered).putLong(finishedAt?.value ?: -1L).putLong(bondedHeads).putLong(leaversCarry).putMoney(clawedBack)
    }
}

/** What an employer puts into a training decision. */
data class TrainingPlan(
    val kind: ProgrammeKind,
    val seats: Long,
    val durationDays: Int,
    val costPerHead: Money,
    val dailyWage: Money,
    val currentLevelPpm: Long,
    val requiredLevelPpm: Long,
    /** Share of the cohort that leaves in a year, ppm. */
    val quitRateAnnualPpm: Long,
    val clause: RetentionClause? = null,
    val horizonDays: Int = 720,
)

data class TrainingCase(
    val totalCost: Money,
    val lostOutput: Money,
    val levelGainPpm: Long,
    val productivityGainPpm: Long,
    val benefit: Money,
    val expectedClawback: Money,
    val net: Money,
    val paybackDays: Int?,
    val survivingSharePpm: Long,
) {
    val worthIt: Boolean get() = net.isPositive && paybackDays != null
}

/** Cost and benefit of a training programme, as pure arithmetic over a plan. */
object TrainingEconomics {

    fun levelGain(kind: ProgrammeKind, days: Int, current: Long, required: Long): Long {
        val months = (days / 30).coerceAtLeast(1)
        val cap = (required + kind.capMarginPpm).coerceAtMost(Ppm.ONE)
        return minOf(kind.gainPerMonthPpm * months, (cap - current).coerceAtLeast(0L))
    }

    /** Fee and lost work for a plan. [wageIndexPpm] scales the course price to local pay. */
    fun costPerHead(kind: ProgrammeKind, days: Int, wageIndexPpm: Long): Money =
        Money.units(kind.costPerHeadDayUnits * days).times(app.many.economytycoon.core.Rate(wageIndexPpm))

    fun evaluate(plan: TrainingPlan): TrainingCase {
        val gain = levelGain(plan.kind, plan.durationDays, plan.currentLevelPpm, plan.requiredLevelPpm)
        val before = SkillMatch.factor(plan.currentLevelPpm, plan.requiredLevelPpm)
        val after = SkillMatch.factor(plan.currentLevelPpm + gain, plan.requiredLevelPpm)
        val delta = after - before
        val total = plan.costPerHead * plan.seats
        val lost = Money(Ppm.of(plan.dailyWage.raw, plan.kind.workLossPpm)) * (plan.seats * plan.durationDays)
        val monthlyKeep = Ppm.ONE - Ppm.perDays(plan.quitRateAnnualPpm, 30L)
        // month by month after the course: trainees who stay produce the gain, those who leave take it with them
        var surviving = Ppm.ONE
        var benefit = Money.ZERO
        var cumulative = Money.ZERO
        var payback: Int? = null
        var clawback = Money.ZERO
        val months = (plan.horizonDays - plan.durationDays).coerceAtLeast(0) / 30
        val perMonth = Money(Ppm.of(plan.dailyWage.raw, delta)) * (plan.seats * 30L)
        for (m in 1..months) {
            val leavers = surviving - Ppm.mul(surviving, monthlyKeep)
            surviving = Ppm.mul(surviving, monthlyKeep)
            val month = Money(Ppm.of(perMonth.raw, surviving))
            benefit += month
            cumulative += month
            if (plan.clause != null) {
                val left = (plan.clause.bondDays - (m - 1) * 30).coerceAtLeast(0)
                val owed = Ppm.mul(Ppm.of(total.raw, leavers), Ppm.mul(plan.clause.clawbackPpm, Ppm.ratio(left.toLong(), plan.clause.bondDays.toLong())))
                clawback += Money(owed)
            }
            if (payback == null && cumulative.raw + clawback.raw >= total.raw + lost.raw) payback = plan.durationDays + m * 30
        }
        val net = benefit + clawback - total - lost
        return TrainingCase(total, lost, gain, delta, benefit, clawback, net, payback, surviving)
    }
}
