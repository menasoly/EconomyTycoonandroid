package app.many.economytycoon.talent

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.workforce.CohortKey

/** One step of a career ladder: the cohort people are promoted into and what it takes to get there. */
data class Rung(
    val cohort: CohortKey,
    /** Time in the cohort below before a person is considered, in ticks. */
    val minTenureTicks: Long,
    /** Level in the main skill of the cohort below that a person needs, ppm. */
    val minLevelPpm: Long,
    /** Raise over the pay in the cohort below, ppm, when that is more than the going wage of the new cohort. */
    val raisePpm: Long,
) {
    fun hashInto(h: CanonicalHasher) {
        h.putLong(cohort.company.value).putLong(cohort.facility.value).putString(cohort.role.key).putLong(cohort.region.value)
        h.putLong(minTenureTicks).putLong(minLevelPpm).putLong(raisePpm)
    }
}

/** An internal route up through the company's cohorts, lowest rung first. */
class CareerLadder(val id: LadderId, val company: CompanyId, val name: String, val rungs: List<Rung>) {
    var enabled: Boolean = true
        internal set
    var promotions: Long = 0L
        internal set
    var lastReview: Tick = Tick(0)
        internal set
    /** Share of a cohort carried over between reviews, per rung, ppm. */
    internal val carry: LongArray = LongArray(rungs.size)

    internal fun restore(enabled: Boolean, promotions: Long, lastReview: Tick, carries: List<Long>) {
        this.enabled = enabled; this.promotions = promotions; this.lastReview = lastReview
        for (i in carries.indices) if (i < carry.size) carry[i] = carries[i]
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(company.value).putString(name).putInt(rungs.size)
        for (r in rungs) r.hashInto(h)
        h.putBoolean(enabled).putLong(promotions).putLong(lastReview.value)
        for (c in carry) h.putLong(c)
    }
}

data class CareerParams(
    val maxLaddersPerCompany: Int = 6,
    val maxRungs: Int = 6,
    /** Largest share of a cohort that can move up in one review, ppm. */
    val maxPromotionSharePpm: Long = 100_000L,
    val reviewIntervalDays: Int = 90,
    val moraleHopePpm: Long = 40_000L,
    val moraleOfPromotedPpm: Long = 30_000L,
    val maxRaisePpm: Long = 500_000L,
)

/** What a review would do today, for the screen and for the rival heuristic. */
data class PromotionPreview(
    val ladder: LadderId,
    val rung: Int,
    val from: CohortKey,
    val to: CohortKey,
    val eligible: Long,
    val openings: Long,
    val planned: Long,
    val newWage: Money,
    val extraWagePerDay: Money,
    val blockedBy: String?,
)
