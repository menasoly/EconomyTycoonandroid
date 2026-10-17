package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

/**
 * The buildings of one district as floor area split into five age bands, each with a condition. Buildings age from one
 * band to the next, decay unless maintained, and are demolished worst first when the land is redeveloped. The total
 * area only changes through [addNew], [demolish] and [damage], never through ageing.
 */
class BuildingStock {
    private val area = LongArray(BANDS)
    private val condition = LongArray(BANDS) { Scale.PPM }
    private val carry = LongArray(BANDS)

    val totalArea: Long get() = area.sum()
    fun areaOf(band: Int): Long = area[band]
    fun conditionOf(band: Int): Long = condition[band]

    /** Area-weighted condition of the whole stock in ppm; an empty district counts as new. */
    val averageConditionPpm: Long
        get() {
            val total = totalArea
            if (total == 0L) return Scale.PPM
            var acc = 0L
            for (i in 0 until BANDS) acc += mulDiv(area[i], condition[i], 1L)
            return acc / total
        }

    /** Area-weighted age in years, using the middle of each band. */
    val averageAgeYears: Int
        get() {
            val total = totalArea
            if (total == 0L) return 0
            var acc = 0L
            for (i in 0 until BANDS) acc += area[i] * MID_AGE[i]
            return (acc / total).toInt()
        }

    /** Share of the floor area in the oldest band, ppm. */
    val oldShare: Long get() = if (totalArea == 0L) 0L else mulDiv(area[BANDS - 1], Scale.PPM, totalArea)

    /** Adds newly built floor area to the youngest band. */
    fun addNew(floorArea: Long, conditionPpm: Long = NEW_CONDITION) {
        require(floorArea >= 0) { "area must not be negative" }
        mixInto(0, floorArea, conditionPpm.coerceIn(0L, Scale.PPM))
    }

    private fun mixInto(band: Int, extra: Long, extraCondition: Long) {
        if (extra <= 0L) return
        val before = area[band]
        val total = before + extra
        condition[band] = (before * condition[band] + extra * extraCondition + total / 2) / total
        area[band] = total
    }

    /** One month of ageing: a slice of each band moves up to the next, and every band wears. */
    fun ageOneMonth() {
        val moved = LongArray(BANDS)
        val movedCondition = LongArray(BANDS)
        for (i in 0 until BANDS - 1) {
            val denom = BAND_YEARS[i].toLong() * Scale.MONTHS_PER_YEAR
            val acc = area[i] + carry[i]
            val slice = (acc / denom).coerceAtMost(area[i])
            carry[i] = acc - slice * denom
            moved[i] = slice
            movedCondition[i] = condition[i]
            area[i] -= slice
        }
        for (i in 0 until BANDS - 1) mixInto(i + 1, moved[i], movedCondition[i])
        for (i in 0 until BANDS) condition[i] = (condition[i] - WEAR_PPM[i]).coerceAtLeast(MIN_CONDITION)
    }

    /** Spends maintenance: each band recovers [recoveryPpm] of the way back to new. */
    fun maintain(recoveryPpm: Long) {
        if (recoveryPpm <= 0L) return
        for (i in 0 until BANDS) {
            if (area[i] == 0L) continue
            condition[i] = condition[i] + mulDiv(Scale.PPM - condition[i], recoveryPpm.coerceAtMost(Scale.PPM), Scale.PPM)
        }
    }

    /**
     * Removes up to [wanted] square metres, the worst-condition bands first. With [sparePrewar] the oldest band is
     * protected as heritage. Returns the area actually removed.
     */
    fun demolish(wanted: Long, sparePrewar: Boolean): Long {
        var left = wanted.coerceAtLeast(0L)
        var removed = 0L
        val order = (0 until BANDS).filter { !(sparePrewar && it == BANDS - 1) }.sortedWith(compareBy({ condition[it] }, { -it }))
        for (b in order) {
            if (left == 0L) break
            val take = minOf(left, area[b])
            area[b] -= take
            left -= take
            removed += take
        }
        return removed
    }

    /**
     * Loses a share of the stock to a disaster. Poor condition makes a band lose more; no band loses more than all of
     * its area. Returns the area lost.
     */
    fun damage(sharePpm: Long): Long {
        var lost = 0L
        for (i in 0 until BANDS) {
            val weight = Scale.PPM + (Scale.PPM - condition[i])
            val loss = mulDiv(area[i], mulDiv(sharePpm.coerceIn(0L, Scale.PPM), weight, Scale.PPM), Scale.PPM).coerceAtMost(area[i])
            area[i] -= loss
            lost += loss
        }
        return lost
    }

    /** What it would cost to replace the stock at [costPerSqm], scaled by its condition. */
    fun replacementValue(costPerSqm: Money): Money {
        var raw = 0L
        for (i in 0 until BANDS) raw += mulDiv(costPerSqm.raw * area[i], condition[i], Scale.PPM)
        return Money(raw)
    }

    fun areas(): LongArray = area.copyOf()
    fun conditions(): LongArray = condition.copyOf()
    fun carries(): LongArray = carry.copyOf()

    fun restore(areas: LongArray, conditions: LongArray, carries: LongArray) {
        require(areas.size == BANDS && conditions.size == BANDS && carries.size == BANDS) { "wrong number of age bands" }
        for (i in 0 until BANDS) { area[i] = areas[i]; condition[i] = conditions[i]; carry[i] = carries[i] }
    }

    fun hashInto(h: CanonicalHasher) {
        for (i in 0 until BANDS) h.putLong(area[i]).putLong(condition[i]).putLong(carry[i])
    }

    companion object {
        const val BANDS = 5
        const val NEW_CONDITION = 950_000L
        const val MIN_CONDITION = 80_000L
        val BAND_YEARS = intArrayOf(10, 15, 25, 30, 0)
        val MID_AGE = intArrayOf(5, 17, 37, 65, 95)
        private val WEAR_PPM = longArrayOf(900L, 1_600L, 2_400L, 3_200L, 4_200L)
    }
}
