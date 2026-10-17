package app.many.economytycoon.tourism

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

/**
 * What guests think of one property. [scorePpm] is the published rating; it moves toward each night's satisfaction
 * by an amount that depends on how many reviews came in against how many there already are, so a young property
 * is easy to move and an established one keeps its reputation through a bad week.
 */
class ReviewBook {
    var scorePpm: Long = 700_000L
        internal set
    var volume: Long = 0L
        internal set

    /** Slow measure of reputation used by the destination's word of mouth. */
    var reputationPpm: Long = 700_000L
        internal set

    internal val recentList = ArrayList<Long>()
    val recent: List<Long> get() = recentList.toList()

    /** The published rating from 1.0 to 5.0 in tenths of a star. */
    val ratingTenths: Int get() = (10L + 40L * scorePpm / 1_000_000L).toInt()

    /** Mean satisfaction over the last [days] nights recorded. */
    fun recentAverage(days: Int = 30): Long {
        val tail = recentList.takeLast(days)
        return if (tail.isEmpty()) scorePpm else tail.sum() / tail.size
    }

    /** Takes in one night: its satisfaction and the number of guests who stayed. */
    internal fun record(satisfactionPpm: Long, guests: Int) {
        val s = satisfactionPpm.coerceIn(0L, 1_000_000L)
        recentList += s
        if (recentList.size > RECENT_DAYS) recentList.subList(0, recentList.size - RECENT_DAYS).clear()
        val written = guests.toLong() * REVIEW_RATE_PPM / 1_000_000L
        if (written > 0L) {
            val inertia = minOf(volume, VOLUME_CAP) + PRIOR_REVIEWS
            scorePpm = (scorePpm + mulDiv(s - scorePpm, written, written + inertia)).coerceIn(0L, 1_000_000L)
            volume += written
        }
        reputationPpm = (reputationPpm + (scorePpm - reputationPpm) / REPUTATION_DAYS).coerceIn(0L, 1_000_000L)
    }

    internal fun restore(score: Long, volume: Long, reputation: Long, recent: List<Long>) {
        scorePpm = score; this.volume = volume; reputationPpm = reputation
        recentList.clear(); recentList.addAll(recent)
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(scorePpm).putLong(volume).putLong(reputationPpm).putInt(recentList.size)
        for (r in recentList) h.putLong(r)
    }

    companion object {
        const val RECENT_DAYS = 60
        /** Share of guests who write a review, ppm. */
        const val REVIEW_RATE_PPM = 80_000L
        const val VOLUME_CAP = 400L
        const val PRIOR_REVIEWS = 20L
        const val REPUTATION_DAYS = 30L
    }
}

/** What went into one night's satisfaction. All ratios are ppm. */
data class SatisfactionInputs(
    val stars: Int,
    val conditionPpm: Long,
    /** Staff on duty as a share of staff needed. */
    val serviceLevelPpm: Long,
    val paidAdr: Money,
    val fairAdr: Money,
    /** Guests who had to be sent elsewhere, as a share of those who arrived. */
    val walkedSharePpm: Long = 0L,
    /** How the rest of the stay went: weather, queues at attractions, crowds, 0..1,000,000. */
    val experiencePpm: Long = 700_000L,
)

/** Pure rules for how happy a guest is, and how a destination's name follows its properties. */
object GuestSatisfaction {
    const val QUALITY_WEIGHT = 450_000L
    const val VALUE_WEIGHT = 300_000L
    const val EXPERIENCE_WEIGHT = 150_000L
    const val WALK_WEIGHT = 100_000L

    /** What a guest expects from a property of the stars, 200,000 per star. */
    fun expectationPpm(stars: Int): Long = stars.coerceIn(1, 5) * 200_000L

    /** Satisfaction with the service and the building: 750,000 when both are as they should be. */
    fun qualityPpm(conditionPpm: Long, serviceLevelPpm: Long): Long {
        val service = 400_000L + mulDiv(600_000L, serviceLevelPpm.coerceIn(0L, 1_000_000L), 1_000_000L)
        val building = 500_000L + mulDiv(500_000L, conditionPpm.coerceIn(0L, 1_000_000L), 1_000_000L)
        return mulDiv(mulDiv(750_000L, service, 1_000_000L), building, 1_000_000L)
    }

    /** 500,000 when the price is fair, rising toward 1,000,000 as the room gets cheaper and falling as it gets dearer. */
    fun valuePpm(paid: Money, fair: Money): Long {
        if (fair.raw <= 0L) return 500_000L
        val gap = mulDiv(fair.raw - paid.raw, 1_000_000L, fair.raw)
        return (500_000L + gap / 2L).coerceIn(0L, 1_000_000L)
    }

    fun score(i: SatisfactionInputs): Long {
        val quality = qualityPpm(i.conditionPpm, i.serviceLevelPpm)
        val value = valuePpm(i.paidAdr, i.fairAdr)
        val walk = (1_000_000L - i.walkedSharePpm * 4L).coerceIn(0L, 1_000_000L)
        val total = quality * QUALITY_WEIGHT + value * VALUE_WEIGHT + i.experiencePpm.coerceIn(0L, 1_000_000L) * EXPERIENCE_WEIGHT + walk * WALK_WEIGHT
        return (total / 1_000_000L).coerceIn(0L, 1_000_000L)
    }

    /**
     * Moves a destination's word of mouth toward the average rating of what is on offer, weighted by rooms sold, a
     * thirtieth of the way each day. With nothing sold the reputation drifts back toward neutral.
     */
    fun destinationReputation(current: Long, ratingsPpm: List<Pair<Long, Long>>): Long {
        val weight = ratingsPpm.sumOf { it.second }
        val target = if (weight <= 0L) 600_000L else ratingsPpm.sumOf { it.first * it.second } / weight
        return (current + (target - current) / ReviewBook.REPUTATION_DAYS).coerceIn(0L, 1_000_000L)
    }
}
