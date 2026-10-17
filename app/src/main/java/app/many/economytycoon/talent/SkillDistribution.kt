package app.many.economytycoon.talent

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.splitLargestRemainder

/** Which people leave a group first when some are taken out of it. */
enum class TakePolicy { PROPORTIONAL, BEST_FIRST, WORST_FIRST }

/**
 * How many people sit at each level of one skill: [BUCKETS] equal bands of mastery from 0 to 1,000,000 ppm. A
 * histogram keeps counts exact (people are never created or lost by arithmetic), lets a whole group learn or forget
 * by shifting mass between bands, and answers questions such as "how many are proficient" without any floating
 * point. Fractions of a person that a shift cannot move this step wait in a per-band remainder and move later.
 */
class SkillDistribution private constructor(private val counts: LongArray, private val carry: LongArray) {

    constructor() : this(LongArray(BUCKETS), LongArray(BUCKETS))

    val total: Long get() = counts.sum()
    val isEmpty: Boolean get() = total == 0L

    fun count(bucket: Int): Long = counts[bucket]
    fun counts(): List<Long> = counts.toList()
    fun copy(): SkillDistribution = SkillDistribution(counts.copyOf(), carry.copyOf())

    /** Average level, weighted by head; null when the group is empty. */
    fun meanOrNull(): Long? {
        val n = total
        if (n == 0L) return null
        var sum = 0L
        for (i in 0 until BUCKETS) sum += counts[i] * midpoint(i)
        return sum / n
    }

    fun mean(default: Long = 0L): Long = meanOrNull() ?: default

    /** People at or above [level]; the band that straddles it counts in proportion. */
    fun countAtLeast(level: Long): Long {
        if (level <= 0L) return total
        if (level > Ppm.ONE) return 0L
        val b = bucketOf(level)
        var n = 0L
        for (i in b + 1 until BUCKETS) n += counts[i]
        val upper = (b + 1) * WIDTH
        n += counts[b] * (upper - level) / WIDTH
        return n
    }

    fun shareAtLeast(level: Long): Long = Ppm.ratio(countAtLeast(level), total)

    /** The level below which [p] ppm of the group sits, at band resolution. */
    fun percentile(p: Long): Long {
        val n = total
        if (n == 0L) return 0L
        val target = (n * Ppm.clamp(p) + Ppm.ONE - 1) / Ppm.ONE
        var run = 0L
        for (i in 0 until BUCKETS) {
            run += counts[i]
            if (run >= target && counts[i] > 0L) return midpoint(i)
        }
        return midpoint(BUCKETS - 1)
    }

    // ----- changes: only the owning service calls these --------------------------------------

    internal fun addAt(level: Long, n: Long) {
        require(n >= 0L) { "cannot add a negative number of people" }
        counts[bucketOf(level)] += n
    }

    internal fun addBucket(bucket: Int, n: Long) {
        require(n >= 0L) { "cannot add a negative number of people" }
        counts[bucket] += n
    }

    internal fun addAll(other: SkillDistribution) {
        for (i in 0 until BUCKETS) counts[i] += other.counts[i]
    }

    internal fun clear() { counts.fill(0L); carry.fill(0L) }

    /** Takes up to [n] people out and returns them as their own group. */
    internal fun take(n: Long, policy: TakePolicy = TakePolicy.PROPORTIONAL, minLevel: Long = 0L): SkillDistribution {
        val out = SkillDistribution()
        if (n <= 0L) return out
        val firstBucket = if (minLevel <= 0L) 0 else bucketOf(minLevel)
        when (policy) {
            TakePolicy.PROPORTIONAL -> {
                val pool = LongArray(BUCKETS) { if (it >= firstBucket) counts[it] else 0L }
                val avail = pool.sum()
                val want = minOf(n, avail)
                if (want == 0L) return out
                val parts = splitLargestRemainder(want, pool)
                for (i in 0 until BUCKETS) { counts[i] -= parts[i]; out.counts[i] += parts[i] }
            }
            TakePolicy.BEST_FIRST -> {
                var left = n
                var i = BUCKETS - 1
                while (left > 0L && i >= firstBucket) {
                    val t = minOf(left, counts[i]); counts[i] -= t; out.counts[i] += t; left -= t; i--
                }
            }
            TakePolicy.WORST_FIRST -> {
                var left = n
                var i = firstBucket
                while (left > 0L && i < BUCKETS) {
                    val t = minOf(left, counts[i]); counts[i] -= t; out.counts[i] += t; left -= t; i++
                }
            }
        }
        return out
    }

    /** Removes people so that [newTotal] remain, proportionally. Returns who left. */
    internal fun shrinkTo(newTotal: Long): SkillDistribution = take((total - newTotal).coerceAtLeast(0L), TakePolicy.PROPORTIONAL)

    /**
     * Moves every person by the shift [deltaOf] gives for their band (ppm, signed). Whole bands move at once; the
     * remainder moves a share of the band's people up or down one band, keeping the fraction for later. Nobody
     * moves twice in one call, and the ends of the scale hold whoever reaches them.
     */
    internal fun shift(deltaOf: (Int) -> Long) {
        val next = LongArray(BUCKETS)
        for (i in 0 until BUCKETS) {
            val c = counts[i]
            if (c == 0L) continue
            val d = deltaOf(i)
            if (d == 0L) { next[i] += c; continue }
            val sign = if (d > 0) 1 else -1
            val whole = (Math.abs(d) / WIDTH).toInt()
            val rest = (Math.abs(d) % WIDTH) * sign
            val base = (i + sign * whole).coerceIn(0, BUCKETS - 1)
            carry[i] += rest * c
            var moved = carry[i] / WIDTH
            carry[i] %= WIDTH
            moved = if (sign > 0) moved.coerceIn(0L, c) else (-moved).coerceIn(0L, c)
            val stay = c - moved
            next[base] += stay
            next[(base + sign).coerceIn(0, BUCKETS - 1)] += moved
        }
        for (i in 0 until BUCKETS) counts[i] = next[i]
    }

    /**
     * Divides everyone into groups sized in proportion to [weights] (the group sizes sum to the head count). Each
     * group is a cross-section of the whole. This group is emptied.
     */
    internal fun splitInto(weights: LongArray): List<SkillDistribution> {
        require(weights.isNotEmpty()) { "weights must not be empty" }
        val n = total
        val sizes = if (n == 0L || weights.all { it == 0L }) LongArray(weights.size) else splitLargestRemainder(n, weights)
        val out = ArrayList<SkillDistribution>()
        for (i in weights.indices) out += take(sizes[i], TakePolicy.PROPORTIONAL)
        // anything left over (all-zero weights) goes to the first group
        if (!isEmpty) { out[0].addAll(this.copy()); clear() }
        return out
    }

    /** Everyone gains (or loses) the same [delta]. */
    internal fun shiftAll(delta: Long) = shift { delta }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(BUCKETS)
        for (i in 0 until BUCKETS) h.putLong(counts[i]).putLong(carry[i])
    }

    internal fun carries(): LongArray = carry.copyOf()

    override fun equals(other: Any?) = other is SkillDistribution && counts.contentEquals(other.counts) && carry.contentEquals(other.carry)
    override fun hashCode() = counts.contentHashCode() * 31 + carry.contentHashCode()
    override fun toString() = "SkillDistribution(n=$total, mean=${meanOrNull()})"

    companion object {
        const val BUCKETS = 20
        const val WIDTH = 50_000L

        fun bucketOf(level: Long): Int = (level.coerceIn(0L, Ppm.ONE) / WIDTH).toInt().coerceAtMost(BUCKETS - 1)
        fun midpoint(bucket: Int): Long = bucket * WIDTH + WIDTH / 2

        /** A bell-shaped group of [n] people centred on [mean] with the given [spread] (half-width, ppm). */
        fun around(mean: Long, spread: Long, n: Long): SkillDistribution {
            val d = SkillDistribution()
            if (n <= 0L) return d
            val half = spread.coerceAtLeast(WIDTH / 2)
            val weights = LongArray(BUCKETS) { i ->
                val off = Math.abs(midpoint(i) - mean)
                if (off >= 2 * half) 0L else {
                    val x = Ppm.ratio(off, 2 * half)
                    val bell = Ppm.ONE - Ppm.mul(x, x)
                    Ppm.mul(bell, bell) + 1L
                }
            }
            val parts = splitLargestRemainder(n, weights)
            for (i in 0 until BUCKETS) d.counts[i] = parts[i]
            return d
        }

        fun single(level: Long, n: Long): SkillDistribution = SkillDistribution().also { it.addAt(level, n) }

        /** Rebuilds a group from saved counts and remainders. */
        fun restore(counts: LongArray, carry: LongArray): SkillDistribution {
            require(counts.size == BUCKETS && carry.size == BUCKETS) { "bad distribution size" }
            require(counts.all { it >= 0L }) { "negative count" }
            return SkillDistribution(counts.copyOf(), carry.copyOf())
        }
    }
}
