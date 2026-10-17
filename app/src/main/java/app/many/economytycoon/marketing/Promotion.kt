package app.many.economytycoon.marketing

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

enum class PromotionStatus(val label: String) { SCHEDULED("Booked"), RUNNING("On sale"), ENDED("Ended"), CANCELLED("Cancelled") }

/**
 * A temporary price cut on one product in one region. The prices are those at the day it was launched, so the margin
 * the company accepted can be reviewed later.
 */
class Promotion(
    val id: PromotionId,
    val company: CompanyId,
    val good: GoodId,
    val region: RegionId,
    val startTick: Tick,
    val days: Int,
    val discountPpm: Long,
    val listPrice: Money,
    val unitCost: Money,
    val expectedUpliftPpm: Long,
) {
    var status: PromotionStatus = PromotionStatus.SCHEDULED
        internal set
    var endedAt: Tick? = null
        internal set

    val endTick: Tick get() = startTick + days.toLong()
    val salePrice: Money get() = listPrice.times(Rate(Ppm.ONE - discountPpm))

    fun runsOn(tick: Tick): Boolean = status != PromotionStatus.CANCELLED && tick >= startTick && tick < endTick

    internal fun restore(status: PromotionStatus, endedAt: Tick?) { this.status = status; this.endedAt = endedAt }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(company.value).putString(good.key).putLong(region.value).putLong(startTick.value).putInt(days)
            .putLong(discountPpm).putMoney(listPrice).putMoney(unitCost).putLong(expectedUpliftPpm).putInt(status.ordinal)
            .putLong(endedAt?.value ?: -1L)
    }
}

/** What a price cut is expected to do, before it is launched. */
data class PromotionCheck(
    val salePrice: Money,
    val marginBeforePpm: Long,
    val marginAfterPpm: Long,
    /** Extra volume, in ppm of normal, needed just to earn the same gross profit as before. */
    val breakEvenUpliftPpm: Long,
    val expectedUpliftPpm: Long,
    /** Gross profit per day against normal, as a fraction of normal gross profit (can be negative). */
    val profitChangePpm: Long,
    val belowFloor: Boolean,
    val belowCost: Boolean,
) {
    val pays: Boolean get() = expectedUpliftPpm >= breakEvenUpliftPpm && !belowCost
    val allowed: Boolean get() = !belowFloor
}

object PromotionPlanner {
    /** Margin as a share of price, ppm; negative when the price is under cost. */
    fun marginPpm(price: Money, unitCost: Money): Long = if (price.raw <= 0L) -Ppm.ONE else mulDiv((price - unitCost).raw, Ppm.ONE, price.raw)

    /**
     * Extra volume a discount brings. Shoppers respond by the good's price elasticity, and more of them notice the
     * offer when the brand is well known in the region.
     */
    fun expectedUpliftPpm(elasticityPpm: Long, discountPpm: Long, awarenessPpm: Long): Long {
        val base = Ppm.mul(elasticityPpm, discountPpm)
        return Ppm.mul(base, 700_000L + Ppm.mul(600_000L, Ppm.clamp(awarenessPpm)))
    }

    fun check(
        listPrice: Money, unitCost: Money, discountPpm: Long, elasticityPpm: Long, awarenessPpm: Long, params: MarketingParams,
    ): PromotionCheck {
        val d = discountPpm.coerceIn(0L, Ppm.ONE)
        val sale = listPrice.times(Rate(Ppm.ONE - d))
        val before = marginPpm(listPrice, unitCost)
        val after = marginPpm(sale, unitCost)
        val unitProfitBefore = (listPrice - unitCost).raw
        val unitProfitAfter = (sale - unitCost).raw
        val uplift = expectedUpliftPpm(elasticityPpm, d, awarenessPpm)
        val breakEven = when {
            unitProfitBefore <= 0L -> 0L
            unitProfitAfter <= 0L -> Long.MAX_VALUE / 4
            else -> mulDiv(unitProfitBefore, Ppm.ONE, unitProfitAfter) - Ppm.ONE
        }
        val profitChange = if (unitProfitBefore <= 0L) 0L
        else mulDiv(unitProfitAfter * (Ppm.ONE + uplift) / Ppm.ONE - unitProfitBefore, Ppm.ONE, unitProfitBefore)
        return PromotionCheck(sale, before, after, breakEven, uplift, profitChange, after < params.minMarginAfterDiscountPpm, unitProfitAfter <= 0L)
    }

    /**
     * Demand multiplier of the promotion on [tick]: the lift while it runs and a dip afterwards as shoppers who
     * bought early do not need more. Returns one million when the promotion has no effect that day.
     */
    fun multiplierPpm(p: Promotion, tick: Tick, params: MarketingParams): Long {
        if (p.status == PromotionStatus.CANCELLED) return Ppm.ONE
        if (p.runsOn(tick)) return Ppm.ONE + p.expectedUpliftPpm
        val dipDays = (p.days / 2).coerceAtLeast(1)
        if (tick >= p.endTick && tick < p.endTick + dipDays.toLong()) {
            val pulled = Ppm.mul(p.expectedUpliftPpm, params.pullForwardPpm)
            val perDay = pulled * p.days.toLong() / dipDays.toLong()
            return (Ppm.ONE - perDay).coerceAtLeast(500_000L)
        }
        return Ppm.ONE
    }
}
