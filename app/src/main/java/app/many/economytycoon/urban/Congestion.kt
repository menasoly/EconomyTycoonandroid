package app.many.economytycoon.urban

import app.many.economytycoon.core.mulDiv

/** Level of service of a road, from free flowing (A) to jammed (F), by volume over capacity. */
enum class LevelOfService(val label: String) {
    A("Free flowing"), B("Reasonably free"), C("Stable flow"), D("Approaching unstable"), E("At capacity"), F("Breakdown");

    companion object {
        fun of(volumeCapacityPpm: Long): LevelOfService = when {
            volumeCapacityPpm < 350_000L -> A
            volumeCapacityPpm < 550_000L -> B
            volumeCapacityPpm < 750_000L -> C
            volumeCapacityPpm < 900_000L -> D
            volumeCapacityPpm <= 1_000_000L -> E
            else -> F
        }
    }
}

/**
 * Volume-delay functions in integer arithmetic. The Bureau of Public Roads curve is
 * `time = free_flow * (1 + alpha * (volume / capacity) ^ beta)`; here every ratio is ppm and the power is taken by
 * repeated multiplication, so results are identical on every platform.
 */
object Congestion {
    const val BPR_ALPHA_PPM = 150_000L
    const val BPR_BETA = 4
    /** The ratio is capped here so that a hopeless overload cannot overflow or dominate the whole network. */
    const val MAX_RATIO_PPM = 4_000_000L

    fun volumeCapacityPpm(volume: Long, capacity: Long): Long {
        if (volume <= 0L) return 0L
        if (capacity <= 0L) return MAX_RATIO_PPM
        return mulDiv(volume, Scale.PPM, capacity).coerceAtMost(MAX_RATIO_PPM)
    }

    /** `ratio ^ exponent` in ppm; the exponent must not be negative. */
    fun powPpm(ratioPpm: Long, exponent: Int): Long {
        require(exponent >= 0) { "exponent must not be negative" }
        var r = Scale.PPM
        repeat(exponent) { r = mulDiv(r, ratioPpm, Scale.PPM) }
        return r
    }

    /** Multiplier on free-flow time, ppm, for a volume-capacity ratio. */
    fun delayFactorPpm(ratioPpm: Long, alphaPpm: Long = BPR_ALPHA_PPM, beta: Int = BPR_BETA): Long =
        Scale.PPM + mulDiv(alphaPpm, powPpm(ratioPpm.coerceIn(0L, MAX_RATIO_PPM), beta), Scale.PPM)

    /** Travel time in seconds of a link carrying [volume] vehicles an hour on [capacity]. */
    fun travelSeconds(freeFlowSeconds: Long, volume: Long, capacity: Long, alphaPpm: Long = BPR_ALPHA_PPM, beta: Int = BPR_BETA): Long =
        mulDiv(freeFlowSeconds, delayFactorPpm(volumeCapacityPpm(volume, capacity), alphaPpm, beta), Scale.PPM)

    /** Crowding on transit: slower boarding and dwell as vehicles fill up. Gentler than roads at the same ratio. */
    fun crowdingFactorPpm(loadRatioPpm: Long): Long = delayFactorPpm(loadRatioPpm, 400_000L, 3)

    /** Seconds lost per second of free-flow travel, ppm of the free-flow time (0 when flowing freely). */
    fun delayPpm(ratioPpm: Long): Long = delayFactorPpm(ratioPpm) - Scale.PPM
}
