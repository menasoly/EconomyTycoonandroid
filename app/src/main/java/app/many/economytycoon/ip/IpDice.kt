package app.many.economytycoon.ip

import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RandomProvider

/**
 * Stateless deterministic draws. Every draw is a pure function of the world seed, a channel name and up to two keys,
 * so the outcome of one examination or one trial never depends on how many other draws happened before it. That keeps
 * replays stable when unrelated activity changes, and nothing here needs to be saved beyond the seed.
 */
class IpDice(val worldSeed: Long) {
    fun stream(channel: String, a: Long, b: Long = 0L): DeterministicRandom =
        DeterministicRandom(RandomProvider.deriveSeed(worldSeed, "ip.$channel", a * KEY_MIX + b))

    /** Uniform value in [0, 1) at ppm resolution. */
    fun rate(channel: String, a: Long, b: Long = 0L): Rate = stream(channel, a, b).nextRate()

    /** True with probability [p]. */
    fun chance(channel: String, p: Rate, a: Long, b: Long = 0L): Boolean = stream(channel, a, b).chance(p)

    /** Uniform integer in [-spread, spread]. */
    fun spread(channel: String, spread: Long, a: Long, b: Long = 0L): Long =
        if (spread <= 0L) 0L else stream(channel, a, b).nextInRange(-spread, spread)

    /** Uniform integer in [min, max]. */
    fun between(channel: String, min: Long, max: Long, a: Long, b: Long = 0L): Long = stream(channel, a, b).nextInRange(min, max)

    companion object {
        private const val KEY_MIX = 1_000_003L

        /** A stable Long for a text key such as a technology id (FNV-1a). */
        fun keyOf(text: String): Long {
            var h = -0x340d631b7bdddcdbL
            for (ch in text) h = (h xor ch.code.toLong()) * 0x100000001b3L
            return h
        }
    }
}
