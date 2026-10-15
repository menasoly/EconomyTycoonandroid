package app.many.economytycoon.core

/**
 * SplitMix64 generator. Chosen for its tiny state (one Long), speed, and an algorithm that is
 * trivial to reproduce on other platforms. Constants are part of the simulation specification.
 */
class DeterministicRandom(seed: Long) {
    var state: Long = seed
        private set

    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        return z xor (z ushr 31)
    }

    /** Uniform integer in [0, bound). Uses rejection sampling to avoid modulo bias. */
    fun nextLong(bound: Long): Long {
        require(bound > 0) { "bound must be positive" }
        val threshold = (Long.MAX_VALUE - (Long.MAX_VALUE % bound + 1) % bound)
        while (true) {
            val r = nextLong() ushr 1
            if (r <= threshold) return r % bound
        }
    }

    fun nextInt(bound: Int): Int = nextLong(bound.toLong()).toInt()

    /** Uniform integer in the closed range [min, max]. */
    fun nextInRange(min: Long, max: Long): Long {
        require(min <= max) { "min must not exceed max" }
        return min + nextLong(max - min + 1)
    }

    fun nextBoolean(): Boolean = (nextLong() and 1L) == 1L

    /** Uniform rate in [0, 1) with ppm resolution. */
    fun nextRate(): Rate = Rate(nextLong(Rate.ONE_PPM))

    /** True with probability [p]. */
    fun chance(p: Rate): Boolean = when {
        p.ppm <= 0 -> false
        p.ppm >= Rate.ONE_PPM -> true
        else -> nextLong(Rate.ONE_PPM) < p.ppm
    }

    /**
     * Approximately normal deviation in ppm of the standard unit using the sum of 12 uniforms
     * (Irwin-Hall): mean 0, standard deviation 1,000,000.
     */
    fun nextGaussianPpm(): Long {
        var sum = 0L
        repeat(12) { sum += nextLong(Rate.ONE_PPM) }
        return sum - 6L * Rate.ONE_PPM
    }

    fun <T> pick(list: List<T>): T {
        require(list.isNotEmpty()) { "cannot pick from empty list" }
        return list[nextInt(list.size)]
    }

    /** Fisher-Yates shuffle returning a new list; input is untouched. */
    fun <T> shuffled(list: List<T>): List<T> {
        val out = list.toMutableList()
        for (i in out.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val tmp = out[i]; out[i] = out[j]; out[j] = tmp
        }
        return out
    }

    /** Picks an index with probability proportional to [weights]. */
    fun weightedIndex(weights: LongArray): Int {
        var total = 0L
        for (w in weights) { require(w >= 0) { "negative weight" }; total += w }
        require(total > 0) { "weights must have a positive sum" }
        var roll = nextLong(total)
        for (i in weights.indices) {
            if (roll < weights[i]) return i
            roll -= weights[i]
        }
        return weights.size - 1
    }

    fun restore(newState: Long) { state = newState }

    private companion object {
        const val GOLDEN_GAMMA = -0x61c8864680b583ebL
        const val MIX1 = -0x40a7b892e31b1a47L
        const val MIX2 = -0x6b2fb644ecceee15L
    }
}

/**
 * Hands out independent random streams per (name, partition) derived from the world seed.
 * Adding a consumer never changes the sequence of an existing stream.
 */
class RandomProvider(val worldSeed: Long) {
    private val streams = LinkedHashMap<String, DeterministicRandom>()

    fun stream(name: String, partition: Long = 0L): DeterministicRandom =
        streams.getOrPut("$name#$partition") { DeterministicRandom(deriveSeed(worldSeed, name, partition)) }

    /** Stream states in stable key order for persistence. */
    fun snapshot(): Map<String, Long> = streams.toSortedMap().mapValues { it.value.state }

    fun restore(states: Map<String, Long>) {
        streams.clear()
        for ((key, state) in states.toSortedMap()) {
            streams[key] = DeterministicRandom(0L).also { it.restore(state) }
        }
    }

    companion object {
        fun deriveSeed(worldSeed: Long, name: String, partition: Long): Long {
            var h = FNV_OFFSET
            for (ch in name) {
                h = (h xor ch.code.toLong()) * FNV_PRIME
            }
            h = (h xor partition) * FNV_PRIME
            h = (h xor worldSeed) * FNV_PRIME
            // Final avalanche so that nearby seeds diverge.
            h = (h xor (h ushr 33)) * -0xae502812aa7333L
            h = (h xor (h ushr 33)) * -0x3b314601e57a13adL
            return h xor (h ushr 33)
        }

        private const val FNV_OFFSET = -0x340d631b7bdddcdbL
        private const val FNV_PRIME = 0x100000001b3L
    }
}
