package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv

/** Why a quote came out as it did, for the advisor and the screens. */
data class RateQuote(
    val rate: Money,
    val multiplierPpm: Long,
    val forecastOccupancyPpm: Long,
    val atFloor: Boolean,
    val atCeiling: Boolean,
)

/**
 * Pure revenue management: forecast how full a night will end up from what is already booked, price against that
 * forecast, and decide how many rooms to sell above capacity.
 */
object RevenueManagement {
    /** The pickup forecast counts for this much of the blend when nothing has been booked yet, ppm. */
    const val PICKUP_TRUST_FLOOR_PPM = 0L

    /** Gain applied to the gap between forecast and target occupancy. */
    const val GAP_GAIN_PPM = 1_500_000L

    /**
     * Forecast of the final occupancy of a night, ppm of sellable rooms. [bookedBeforePpm] is the share of a night's
     * bookings that is normally in hand at this point, from [Seasonality.bookedBeforeLeadPpm]. The pickup forecast
     * scales what is on the books up to a full night; far ahead, when little is normally booked, the forecast leans
     * on [baselinePpm] (what the property normally sells) instead.
     */
    fun forecastOccupancyPpm(onBooks: Int, sellable: Int, bookedBeforePpm: Long, baselinePpm: Long): Long {
        if (sellable <= 0) return 0L
        val booked = onBooks.toLong() * 1_000_000L / sellable
        val trust = bookedBeforePpm.coerceIn(PICKUP_TRUST_FLOOR_PPM, 1_000_000L)
        val pickup = if (trust <= 0L) baselinePpm else mulDiv(booked, 1_000_000L, trust)
        val blended = mulDiv(pickup, trust, 1_000_000L) + mulDiv(baselinePpm, 1_000_000L - trust, 1_000_000L)
        return maxOf(blended, booked).coerceIn(0L, 1_500_000L)
    }

    /**
     * The multiple of the rack rate to quote: above one when the night is filling faster than the target, below one
     * when it is slow. Weekend and event nights are expected to fill, so the forecast already carries them.
     */
    fun multiplierPpm(forecastPpm: Long, settings: RateSettings): Long {
        val gap = forecastPpm - settings.targetOccupancyPpm
        val move = mulDiv(mulDiv(gap, settings.aggressionPpm, 1_000_000L), GAP_GAIN_PPM, 1_000_000L)
        return (1_000_000L + move).coerceIn(settings.floorPpm, settings.ceilingPpm)
    }

    /**
     * The price to quote for [room] on a night. [onBooks] is the rooms of that class already sold for the night and
     * [sellable] the rooms of that class; the forecast looks at the whole property through [baselinePpm].
     */
    fun quote(
        p: Property, room: RoomClass, lead: Int, onBooks: Int, sellable: Int, baselinePpm: Long, mixPpm: LongArray,
    ): RateQuote {
        val rack = p.rackRate(room)
        val discount = 1_000_000L - p.cancellation.discountPpm
        if (p.settings.policy == RatePolicy.FIXED) {
            return RateQuote(Money(mulDiv(rack.raw, discount, 1_000_000L)), 1_000_000L, baselinePpm, atFloor = false, atCeiling = false)
        }
        val forecast = forecastOccupancyPpm(onBooks, sellable, Seasonality.bookedBeforeLeadPpm(mixPpm, lead), baselinePpm)
        val raw = 1_000_000L + mulDiv(mulDiv(forecast - p.settings.targetOccupancyPpm, p.settings.aggressionPpm, 1_000_000L), GAP_GAIN_PPM, 1_000_000L)
        val mult = raw.coerceIn(p.settings.floorPpm, p.settings.ceilingPpm)
        val rate = Money(mulDiv(mulDiv(rack.raw, mult, 1_000_000L), discount, 1_000_000L))
        return RateQuote(rate, mult, forecast, atFloor = (raw < p.settings.floorPpm), atCeiling = (raw > p.settings.ceilingPpm))
    }

    /** Average revenue per available room for an occupancy and rate: always [adr] times [occupancyPpm]. */
    fun revPar(adr: Money, occupancyPpm: Long): Money = Money(mulDiv(adr.raw, occupancyPpm, 1_000_000L))

    // ----- overbooking ---------------------------------------------------------------------------

    private const val SCALE = 1_000_000_000_000_000L

    /**
     * Probability mass of a binomial with [n] trials and success probability [pPpm], as weights summing to [SCALE].
     * Computed outwards from the mode so that long chains do not underflow.
     */
    internal fun binomialWeights(n: Int, pPpm: Long): LongArray {
        if (n <= 0) return longArrayOf(SCALE)
        val p = pPpm.coerceIn(0L, 1_000_000L)
        if (p == 0L) return LongArray(n + 1).also { it[0] = SCALE }
        if (p == 1_000_000L) return LongArray(n + 1).also { it[n] = SCALE }
        val q = 1_000_000L - p
        val w = LongArray(n + 1)
        val mode = ((n.toLong() + 1L) * p / 1_000_000L).toInt().coerceIn(0, n)
        w[mode] = SCALE
        for (k in mode until n) w[k + 1] = mulDiv(w[k], (n - k).toLong() * p, (k + 1).toLong() * q, RoundingMode.HALF_EVEN)
        for (k in mode downTo 1) w[k - 1] = mulDiv(w[k], k.toLong() * q, (n - k + 1).toLong() * p, RoundingMode.HALF_EVEN)
        val total = w.sum()
        if (total <= 0L) return LongArray(n + 1).also { it[mode] = SCALE }
        // Normalise so the weights sum to SCALE, putting the rounding remainder on the mode.
        var sum = 0L
        for (k in w.indices) { w[k] = mulDiv(w[k], SCALE, total); sum += w[k] }
        w[mode] += SCALE - sum
        return w
    }

    /** Expected guests turned away, in thousandths of a guest, when [sold] rooms are sold to [capacity] rooms and each shows with probability [showPpm]. */
    fun expectedWalksMilli(sold: Int, capacity: Int, showPpm: Long): Long {
        val w = binomialWeights(sold, showPpm)
        var over = 0L
        for (k in capacity + 1..sold) over += (k - capacity).toLong() * (w[k] / 1_000_000L)
        return over / (SCALE / 1_000_000L / 1_000L)
    }

    /** Expected rooms used, in thousandths of a room: guests who show, capped at capacity. */
    fun expectedUsedMilli(sold: Int, capacity: Int, showPpm: Long): Long {
        val w = binomialWeights(sold, showPpm)
        var used = 0L
        for (k in w.indices) used += minOf(k, capacity).toLong() * (w[k] / 1_000_000L)
        return used / (SCALE / 1_000_000L / 1_000L)
    }

    /**
     * How many rooms above capacity to sell. For each limit the profit is the rate on every used room less the cost of
     * every guest turned away, and the rate cancels out of the comparison, so only [walkCostMultiplePpm] matters: the
     * cost of walking a guest as a multiple of the rate (compensation, a room elsewhere, goodwill). The best limit wins,
     * and the smaller one on a tie.
     */
    fun bestOverbooking(capacity: Int, showPpm: Long, walkCostMultiplePpm: Long, maxExtraPpm: Long = 300_000L): Int {
        if (capacity <= 0) return 0
        val maxExtra = (capacity.toLong() * maxExtraPpm / 1_000_000L).toInt()
        var best = 0
        var bestProfit = Long.MIN_VALUE
        for (extra in 0..maxExtra) {
            val sold = capacity + extra
            val w = binomialWeights(sold, showPpm)
            var used = 0L
            var walked = 0L
            for (k in w.indices) {
                val weight = w[k] / 1_000_000L
                used += minOf(k, capacity).toLong() * weight
                if (k > capacity) walked += (k - capacity).toLong() * weight
            }
            val profit = used * 1_000_000L - walked * walkCostMultiplePpm
            if (profit > bestProfit) { bestProfit = profit; best = extra }
        }
        return best
    }

    /** The overbooking allowance, ppm of capacity, that [bestOverbooking] suggests for a property's rooms. */
    fun suggestedOverbookPpm(capacity: Int, showPpm: Long, walkCostMultiplePpm: Long = 1_600_000L): Long {
        if (capacity <= 0) return 0L
        val extra = bestOverbooking(capacity, showPpm, walkCostMultiplePpm)
        return extra.toLong() * 1_000_000L / capacity
    }
}
