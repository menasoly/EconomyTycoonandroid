package app.many.economytycoon.urban

import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.core.splitLargestRemainder

enum class TravelMode(val label: String) { CAR("Car"), TRANSIT("Public transport"), WALK("Walking") }

/** The cost of making one trip by each mode, in seconds of travel time; [UNREACHABLE] if the mode is not available. */
data class ModeCosts(val car: Long, val transit: Long, val walk: Long) {
    fun of(mode: TravelMode): Long = when (mode) {
        TravelMode.CAR -> car
        TravelMode.TRANSIT -> transit
        TravelMode.WALK -> walk
    }
}

/** Shares of trips by mode, in ppm, always adding up to exactly one million. */
data class ModeShares(val car: Long, val transit: Long, val walk: Long) {
    init { require(car + transit + walk == Scale.PPM) { "shares must add up to 100%" } }

    fun of(mode: TravelMode): Long = when (mode) {
        TravelMode.CAR -> car
        TravelMode.TRANSIT -> transit
        TravelMode.WALK -> walk
    }
}

/**
 * A multinomial logit choice between car, public transport and walking over generalised cost. The exponential is a table
 * of e^(-k/16) with exact integer steps, so the shares are identical on every device.
 */
object ModeChoice {
    private val EXP_TABLE = longArrayOf(
        1_000_000L, 939_413L, 882_497L, 829_029L, 778_801L, 731_616L, 687_289L, 645_649L,
        606_531L, 569_783L, 535_261L, 502_832L, 472_367L, 443_747L, 416_862L, 391_606L,
    )
    private const val E_INVERSE_PPM = 367_879L
    /** Cost scale of the logit: a difference of this many seconds divides the weight by e. */
    const val SCALE_SECONDS = 600L
    /** Walking longer than this is not considered. */
    const val MAX_WALK_SECONDS = 2_400L
    /** Walking is more tiring than the same time in a vehicle: its time counts this much more, ppm. */
    const val WALK_PENALTY_PPM = 1_500_000L

    /** `e^(-sixteenths / 16)` in ppm. */
    fun expNegPpm(sixteenths: Long): Long {
        require(sixteenths >= 0) { "exponent must not be negative" }
        val whole = sixteenths / 16L
        if (whole >= 20L) return 0L
        var r = EXP_TABLE[(sixteenths % 16L).toInt()]
        repeat(whole.toInt()) { r = mulDiv(r, E_INVERSE_PPM, Scale.PPM) }
        return r
    }

    /**
     * Mode shares for one origin-destination pair. [carAvailabilityPpm] is the share of people who can drive (owning a
     * car, holding a licence); it scales the weight of the car. If no mode is available everything is walking.
     */
    fun shares(costs: ModeCosts, carAvailabilityPpm: Long = Scale.PPM): ModeShares {
        val effective = LongArray(3) { UNREACHABLE }
        if (costs.car != UNREACHABLE) effective[0] = costs.car + TravelTimes.PARKING_SECONDS
        if (costs.transit != UNREACHABLE) effective[1] = costs.transit
        if (costs.walk != UNREACHABLE && costs.walk <= MAX_WALK_SECONDS) effective[2] = mulDiv(costs.walk, WALK_PENALTY_PPM, Scale.PPM)
        val best = effective.filter { it != UNREACHABLE }.minOrNull() ?: return ModeShares(0L, 0L, Scale.PPM)
        val weights = LongArray(3)
        for (i in 0 until 3) {
            if (effective[i] == UNREACHABLE) continue
            val sixteenths = (effective[i] - best) * 16L / SCALE_SECONDS
            weights[i] = expNegPpm(sixteenths).coerceAtLeast(1L)
        }
        weights[0] = mulDiv(weights[0], carAvailabilityPpm.coerceIn(0L, Scale.PPM), Scale.PPM)
        if (weights.sum() == 0L) { // nobody can use the available modes: fall back to the best one that is left
            val i = effective.indices.filter { effective[it] != UNREACHABLE }.minByOrNull { effective[it] } ?: 2
            weights[i] = 1L
        }
        val parts = splitLargestRemainder(Scale.PPM, weights)
        return ModeShares(parts[0], parts[1], parts[2])
    }

    /** Share of people who can drive, from the share on low incomes. */
    fun carAvailabilityPpm(lowIncomeSharePpm: Long): Long = (Scale.PPM - lowIncomeSharePpm * 6L / 10L).coerceIn(250_000L, Scale.PPM)
}
