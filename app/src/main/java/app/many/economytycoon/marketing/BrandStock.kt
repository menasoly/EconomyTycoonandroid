package app.many.economytycoon.marketing

import app.many.economytycoon.core.CanonicalHasher

/**
 * What people in one region think of one company's product: how many know it, how many keep buying it, the
 * lingering mark left by a scandal, and how much of each channel's recent exposure is still felt.
 */
class BrandStock {
    var awarenessPpm: Long = 0L
        internal set
    var loyaltyPpm: Long = 0L
        internal set
    /** Distrust left by brand damage; blunts new exposure and fades with time. */
    var taintPpm: Long = 0L
        internal set
    private val adstock = LongArray(Channel.values().size)

    fun adstock(channel: Channel): Long = adstock[channel.ordinal]
    internal fun setAdstock(channel: Channel, value: Long) { adstock[channel.ordinal] = Ppm.clamp(value) }
    fun adstockSnapshot(): LongArray = adstock.copyOf()

    /** True when nothing is left to track, so the entry can be dropped. */
    val isDormant: Boolean get() = awarenessPpm <= 0L && loyaltyPpm <= 0L && taintPpm <= 0L && adstock.all { it <= 0L }

    internal fun restore(awareness: Long, loyalty: Long, taint: Long, stock: LongArray) {
        awarenessPpm = Ppm.clamp(awareness); loyaltyPpm = Ppm.clamp(loyalty); taintPpm = Ppm.clamp(taint)
        for (i in adstock.indices) adstock[i] = Ppm.clamp(stock.getOrElse(i) { 0L })
    }

    internal fun set(awareness: Long, loyalty: Long) { awarenessPpm = Ppm.clamp(awareness); loyaltyPpm = Ppm.clamp(loyalty) }
    internal fun setTaint(value: Long) { taintPpm = Ppm.clamp(value) }

    fun copy(): BrandStock = BrandStock().also { it.restore(awarenessPpm, loyaltyPpm, taintPpm, adstock) }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(awarenessPpm).putLong(loyaltyPpm).putLong(taintPpm)
        for (a in adstock) h.putLong(a)
    }
}

/** The daily law of motion of a [BrandStock]: exposure builds awareness with diminishing returns, and time erodes it. */
object StockDynamics {
    /**
     * Moves [stock] one day on. [impulse] is the exposure each channel produced today (indexed by channel
     * ordinal), already scaled by creative strength, wear and ramp-up. [qualityPpm] is delivered quality against
     * expectations, one million meaning as expected.
     */
    fun step(stock: BrandStock, impulse: LongArray, qualityPpm: Long, params: MarketingParams) {
        val channels = Channel.values()
        val awareParts = LongArray(channels.size)
        val loyalParts = LongArray(channels.size)
        for (c in channels) {
            val carry = c.spec.carryOverPpm
            val now = stock.adstock(c)
            val next = Ppm.approach(now, impulse[c.ordinal], Ppm.ONE - carry)
            stock.setAdstock(c, next)
            awareParts[c.ordinal] = Ppm.mul(stock.adstock(c), c.spec.awarenessWeightPpm)
            loyalParts[c.ordinal] = Ppm.mul(stock.adstock(c), c.spec.loyaltyWeightPpm)
        }
        val blunt = Ppm.ONE - Ppm.mul(stock.taintPpm, params.taintBluntPpm)
        val awareExposure = Ppm.mul(Ppm.union(awareParts), blunt)
        val loyalExposure = Ppm.mul(Ppm.union(loyalParts), blunt)

        val a = stock.awarenessPpm
        val learned = Ppm.mul(Ppm.mul(Ppm.ONE - a, params.awarenessLearnPpm), awareExposure)
        val newAwareness = Ppm.shrink(a + learned, params.awarenessDecayPpm)

        val l = stock.loyaltyPpm
        val earned = Ppm.mul(Ppm.mul(Ppm.ONE - l, params.loyaltyLearnPpm), Ppm.mul(loyalExposure, a))
        val qualityPull = Ppm.mul(Ppm.mul(qualityPpm - Ppm.ONE, params.qualityLoyaltyPpm), a)
        val newLoyalty = Ppm.shrink(l + earned + qualityPull, params.loyaltyDecayPpm)

        stock.set(newAwareness, newLoyalty.coerceAtMost(newAwareness))
        stock.setTaint(Ppm.shrink(stock.taintPpm, params.taintRecoveryPpm))
    }

    /**
     * The awareness and loyalty a constant exposure settles at, found by running the law of motion until it stops
     * moving or [maxDays] pass. Used to compare plans without touching live state.
     */
    fun steadyState(impulse: LongArray, qualityPpm: Long, params: MarketingParams, maxDays: Int = 720): BrandStock {
        val s = BrandStock()
        var days = 0
        while (days < maxDays) {
            val a = s.awarenessPpm; val l = s.loyaltyPpm
            val adsBefore = s.adstockSnapshot()
            step(s, impulse, qualityPpm, params)
            days++
            if (a == s.awarenessPpm && l == s.loyaltyPpm && adsBefore.contentEquals(s.adstockSnapshot())) break
        }
        return s
    }

    /**
     * The awareness and loyalty a constant [impulse] settles at, in closed form: awareness balances learning
     * against forgetting, `A = k*E / (k*E + d)`, and loyalty likewise on top of it. Cheap enough for optimisers.
     */
    fun equilibrium(impulse: LongArray, qualityPpm: Long, params: MarketingParams, taintPpm: Long = 0L): Pair<Long, Long> {
        val channels = Channel.values()
        val awareParts = LongArray(channels.size) { Ppm.mul(impulse[it], channels[it].spec.awarenessWeightPpm) }
        val loyalParts = LongArray(channels.size) { Ppm.mul(impulse[it], channels[it].spec.loyaltyWeightPpm) }
        val blunt = Ppm.ONE - Ppm.mul(taintPpm, params.taintBluntPpm)
        val pushA = Ppm.mul(params.awarenessLearnPpm, Ppm.mul(Ppm.union(awareParts), blunt))
        val a = if (pushA + params.awarenessDecayPpm <= 0L) 0L else Ppm.ratio(pushA, pushA + params.awarenessDecayPpm)
        val pushL = Ppm.mul(params.loyaltyLearnPpm, Ppm.mul(Ppm.mul(Ppm.union(loyalParts), blunt), a))
        val pull = Ppm.mul(Ppm.mul(qualityPpm - Ppm.ONE, params.qualityLoyaltyPpm), a)
        val denominator = pushL + params.loyaltyDecayPpm
        val l = if (denominator <= 0L) 0L else Ppm.clamp(Ppm.ratio(pushL + pull, denominator)).coerceAtMost(a)
        return a to l
    }

    /** Awareness after [days] days with no exposure at all. */
    fun fadedAwareness(awarenessPpm: Long, days: Int, params: MarketingParams): Long = Ppm.decay(awarenessPpm, params.awarenessDecayPpm, days)
}
