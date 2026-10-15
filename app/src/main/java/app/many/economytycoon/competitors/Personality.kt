package app.many.economytycoon.competitors

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.DeterministicRandom

enum class Archetype { DISCOUNTER, PREMIUM, BALANCED, INNOVATOR, EXPANDER, CONSERVATIVE }

/**
 * Character of an AI company. Every trait is a ppm weight; the planners read them to decide how
 * boldly to price, spend, borrow and grow.
 */
data class AiPersonality(
    val archetype: Archetype,
    /** Willingness to undercut rivals and to make takeover bids. */
    val aggression: Long,
    /** Willingness to borrow and to build with a thin cash cushion. */
    val riskTolerance: Long,
    val qualityFocus: Long,
    val costFocus: Long,
    val expansionAppetite: Long,
    /** Share of revenue put into research, ppm. */
    val rdShare: Long,
    /** Dividend payout of distributable earnings, ppm. */
    val payout: Long,
    /** Ticks between planning rounds. */
    val thinkInterval: Int,
    /** Operating cost ticks held as cash. */
    val cashBufferTicks: Int,
    /** Most facilities the company will run at once. */
    val maxFacilities: Int,
) {
    init {
        require(thinkInterval > 0) { "think interval must be positive" }
        require(cashBufferTicks >= 0 && maxFacilities > 0) { "bad personality limits" }
    }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(archetype.ordinal).putLong(aggression).putLong(riskTolerance).putLong(qualityFocus).putLong(costFocus)
            .putLong(expansionAppetite).putLong(rdShare).putLong(payout).putInt(thinkInterval).putInt(cashBufferTicks).putInt(maxFacilities)
    }
}

object Personalities {
    fun of(a: Archetype): AiPersonality = when (a) {
        Archetype.DISCOUNTER -> AiPersonality(a, 800_000, 500_000, 300_000, 900_000, 600_000, 10_000, 200_000, 5, 20, 8)
        Archetype.PREMIUM -> AiPersonality(a, 300_000, 400_000, 900_000, 300_000, 400_000, 40_000, 300_000, 6, 30, 6)
        Archetype.BALANCED -> AiPersonality(a, 500_000, 500_000, 500_000, 500_000, 500_000, 25_000, 250_000, 5, 25, 8)
        Archetype.INNOVATOR -> AiPersonality(a, 450_000, 650_000, 700_000, 400_000, 450_000, 90_000, 100_000, 5, 25, 7)
        Archetype.EXPANDER -> AiPersonality(a, 650_000, 800_000, 400_000, 600_000, 950_000, 15_000, 50_000, 4, 15, 14)
        Archetype.CONSERVATIVE -> AiPersonality(a, 200_000, 150_000, 600_000, 600_000, 200_000, 20_000, 500_000, 8, 45, 4)
    }

    /** Draws an archetype and nudges each trait a little so rivals of one type still differ. */
    fun roll(random: DeterministicRandom): AiPersonality {
        val a = Archetype.values()[random.nextInt(Archetype.values().size)]
        val base = of(a)
        fun jitter(v: Long) = (v + random.nextInRange(-80_000L, 80_000L)).coerceIn(10_000L, 990_000L)
        return base.copy(
            aggression = jitter(base.aggression), riskTolerance = jitter(base.riskTolerance), qualityFocus = jitter(base.qualityFocus),
            costFocus = jitter(base.costFocus), expansionAppetite = jitter(base.expansionAppetite),
            thinkInterval = (base.thinkInterval + random.nextInt(3)).coerceAtLeast(1),
        )
    }
}
