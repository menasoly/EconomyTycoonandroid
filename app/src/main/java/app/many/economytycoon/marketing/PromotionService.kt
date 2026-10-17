package app.many.economytycoon.marketing

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick

/** A price cut a company wants to run. Prices are today's list price and what one unit costs to make or buy. */
data class PromotionRequest(
    val company: CompanyId,
    val good: GoodId,
    val region: RegionId,
    val startTick: Tick? = null,
    val days: Int,
    val discountPpm: Long,
    val listPrice: Money,
    val unitCost: Money,
    /** How strongly buyers of this good react to price (from the good's demand profile). */
    val elasticityPpm: Long,
    /** A clearance sale may go below cost; any other promotion may not. */
    val clearance: Boolean = false,
)

data class PromotionLaunch(val promotion: Promotion, val check: PromotionCheck)

/** Books and cancels price promotions, refusing those that would sell below the margin floor. */
class PromotionService(private val state: MarketingState, private val params: MarketingParams = MarketingParams()) {

    /** Reviews a plan without booking it, for the screen that shows the player what a discount would do. */
    fun preview(request: PromotionRequest): PromotionCheck {
        val awareness = state.stock(BrandKey(request.company, request.good, request.region))?.awarenessPpm ?: 0L
        return PromotionPlanner.check(request.listPrice, request.unitCost, request.discountPpm, request.elasticityPpm, awareness, params)
    }

    fun launch(request: PromotionRequest, tick: Tick): Outcome<PromotionLaunch> {
        if (request.days < params.minPromotionDays || request.days > params.maxPromotionDays) {
            return Outcome.reject(Rules.INVALID_ARGUMENT, "a promotion runs between ${params.minPromotionDays} and ${params.maxPromotionDays} days")
        }
        if (request.discountPpm <= 0L || request.discountPpm > params.maxDiscountPpm) {
            return Outcome.reject(Rules.INVALID_PRICE, "the discount must be above nothing and at most ${params.maxDiscountPpm / 10_000L}%")
        }
        if (request.listPrice.raw <= 0L || request.unitCost.raw < 0L) return Outcome.reject(Rules.INVALID_PRICE, "prices must be positive")
        val start = request.startTick ?: tick
        if (start < tick) return Outcome.reject(Rules.INVALID_ARGUMENT, "a promotion cannot start in the past")
        val busy = state.promotionsFor(request.company, request.good, request.region).any {
            it.status != PromotionStatus.CANCELLED &&
                (it.status != PromotionStatus.ENDED || (it.endedAt?.let { e -> start - e } ?: Long.MAX_VALUE) < params.promotionCooldownDays.toLong())
        }
        if (busy) return Outcome.reject(Rules.RATE_LIMITED, "that product was on offer recently; shoppers need time to forget it")
        val check = preview(request)
        if (check.belowCost && !request.clearance) return Outcome.reject(Rules.INVALID_PRICE, "that price is below what a unit costs")
        if (!check.allowed && !request.clearance) {
            return Outcome.reject(Rules.INVALID_PRICE, "margin after the discount would be under ${params.minMarginAfterDiscountPpm / 10_000L}%")
        }
        val p = Promotion(
            state.ids.promotion(), request.company, request.good, request.region, start, request.days, request.discountPpm,
            request.listPrice, request.unitCost, check.expectedUpliftPpm,
        )
        if (start <= tick) p.status = PromotionStatus.RUNNING
        state.promotionMap[p.id] = p
        return Outcome.ok(PromotionLaunch(p, check))
    }

    fun cancel(id: PromotionId, tick: Tick): Outcome<Promotion> {
        val p = state.promotion(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown promotion")
        if (p.status == PromotionStatus.ENDED || p.status == PromotionStatus.CANCELLED) return Outcome.reject(Rules.WRONG_STATE, "that promotion is over")
        p.status = PromotionStatus.CANCELLED
        p.endedAt = tick
        return Outcome.ok(p)
    }

    /** Combined demand multiplier of every promotion touching this product on [tick]. */
    fun multiplierPpm(company: CompanyId, good: GoodId, region: RegionId, tick: Tick): Long {
        var m = Ppm.ONE
        for (p in state.promotionsFor(company, good, region)) m = Ppm.mul(m, PromotionPlanner.multiplierPpm(p, tick, params))
        return m
    }

    /** Price a buyer pays on [tick] for a good whose list price is [listPrice]. */
    fun priceOn(company: CompanyId, good: GoodId, region: RegionId, tick: Tick, listPrice: Money): Money {
        val running = state.promotionsFor(company, good, region).filter { it.runsOn(tick) }
        val deepest = running.maxOfOrNull { it.discountPpm } ?: return listPrice
        return listPrice.times(Rate(Ppm.ONE - deepest))
    }
}
