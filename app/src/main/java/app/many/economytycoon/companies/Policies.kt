package app.many.economytycoon.companies

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.mulDiv

enum class PriceStrategy {
    /** Ask the going market price plus the markup. */
    MARKET,
    /** Ask unit cost plus the markup, regardless of the market. */
    COST_PLUS,
    /** Ask the market price minus the markup to win volume. */
    UNDERCUT,
    /** Ask the market price plus the markup and the company's brand premium. */
    PREMIUM,
}

/** Standing instruction to sell surplus stock of a good from a facility. */
data class SellRule(
    val facility: FacilityId,
    val good: GoodId,
    val strategy: PriceStrategy = PriceStrategy.MARKET,
    /** Markup (or discount for UNDERCUT) in ppm of the base price. */
    val markupPpm: Long = 0,
    /** Stock to keep back from sale. */
    val reserve: Quantity = Quantity.ZERO,
    /** Largest quantity offered per order; null means everything above the reserve. */
    val maxPerOrder: Quantity? = null,
    /** Never ask less than unit cost times (1 + this), ppm; negative accepts a loss. */
    val minMarginPpm: Long = 0,
    val enabled: Boolean = true,
) {
    init {
        require(markupPpm in -900_000L..5_000_000L) { "markup out of range" }
        require(minMarginPpm >= -900_000L) { "minimum margin out of range" }
        require(reserve.raw >= 0) { "reserve cannot be negative" }
    }
}

/** Standing instruction to keep a facility stocked with an input. */
data class BuyRule(
    val facility: FacilityId,
    val good: GoodId,
    /** Stock level to restore on each review. */
    val target: Quantity,
    /** Highest acceptable price as a multiple of the market price, ppm (1,000,000 = market). */
    val maxPriceMultiplePpm: Long = 1_100_000L,
    val enabled: Boolean = true,
) {
    init {
        require(target.raw >= 0) { "target cannot be negative" }
        require(maxPriceMultiplePpm in 100_000L..10_000_000L) { "price multiple out of range" }
    }
}

data class DividendPolicy(
    /** Share of distributable earnings paid out each interval, ppm. */
    val payoutPpm: Long = 0,
    /** Ticks of operating cost kept as cash before any dividend. */
    val reserveTicks: Int = 30,
    val intervalTicks: Int = 90,
) {
    init {
        require(payoutPpm in 0L..1_000_000L) { "payout must be 0-100%" }
        require(reserveTicks >= 0 && intervalTicks > 0) { "bad dividend schedule" }
    }
}

class SellPlan(val facility: FacilityId, val good: GoodId, val quantity: Quantity, val limit: Price)
class BuyPlan(val facility: FacilityId, val good: GoodId, val quantity: Quantity, val limit: Price)

/** Pure decision functions behind the standing rules; the simulation feeds them state and books the orders. */
object RulePlanner {
    fun sellPlan(rule: SellRule, available: Quantity, unitCost: Money, marketPrice: Price, brandPremiumPpm: Long): SellPlan? {
        if (!rule.enabled) return null
        var qty = (available - rule.reserve)
        if (qty.raw <= 0) return null
        rule.maxPerOrder?.let { qty = Quantity.min(qty, it) }
        val market = marketPrice.perUnit.raw
        val price = when (rule.strategy) {
            PriceStrategy.MARKET -> mulDiv(market, 1_000_000L + rule.markupPpm, 1_000_000L)
            PriceStrategy.COST_PLUS -> mulDiv(unitCost.raw, 1_000_000L + rule.markupPpm, 1_000_000L)
            PriceStrategy.UNDERCUT -> mulDiv(market, 1_000_000L - rule.markupPpm.coerceAtLeast(0L), 1_000_000L)
            PriceStrategy.PREMIUM -> mulDiv(market, 1_000_000L + rule.markupPpm + brandPremiumPpm, 1_000_000L)
        }
        val floor = mulDiv(unitCost.raw, 1_000_000L + rule.minMarginPpm, 1_000_000L)
        val ask = maxOf(price, floor, 1L)
        return SellPlan(rule.facility, rule.good, qty, Price(Money(ask)))
    }

    fun buyPlan(rule: BuyRule, onHand: Quantity, onOrder: Quantity, marketPrice: Price): BuyPlan? {
        if (!rule.enabled) return null
        val need = rule.target - onHand - onOrder
        if (need.raw <= 0) return null
        val limit = mulDiv(marketPrice.perUnit.raw, rule.maxPriceMultiplePpm, 1_000_000L).coerceAtLeast(1L)
        return BuyPlan(rule.facility, rule.good, need, Price(Money(limit)))
    }

    /** Dividend a company should pay now given its distributable room, cash and monthly operating cost. */
    fun dividend(policy: DividendPolicy, distributable: Money, cash: Money, operatingCostPerTick: Money): Money {
        if (policy.payoutPpm == 0L || distributable.raw <= 0) return Money.ZERO
        val reserve = Money(operatingCostPerTick.raw * policy.reserveTicks)
        val room = cash - reserve
        if (room.raw <= 0) return Money.ZERO
        val wanted = Money(mulDiv(distributable.raw, policy.payoutPpm, 1_000_000L))
        return Money.min(wanted, room)
    }
}

/** All standing rules of every company. */
class PolicyBook {
    private val sells = java.util.TreeMap<Triple<Long, Long, String>, SellRule>(compareBy({ it.first }, { it.second }, { it.third }))
    private val buys = java.util.TreeMap<Triple<Long, Long, String>, BuyRule>(compareBy({ it.first }, { it.second }, { it.third }))
    private val dividends = java.util.TreeMap<CompanyId, DividendPolicy>()

    fun setSell(company: CompanyId, rule: SellRule): Outcome<Unit> {
        if (sells.count { it.key.first == company.value } >= MAX_RULES && !sells.containsKey(Triple(company.value, rule.facility.value, rule.good.key))) {
            return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many selling rules")
        }
        sells[Triple(company.value, rule.facility.value, rule.good.key)] = rule
        return Outcome.ok(Unit)
    }

    fun setBuy(company: CompanyId, rule: BuyRule): Outcome<Unit> {
        if (buys.count { it.key.first == company.value } >= MAX_RULES && !buys.containsKey(Triple(company.value, rule.facility.value, rule.good.key))) {
            return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many buying rules")
        }
        buys[Triple(company.value, rule.facility.value, rule.good.key)] = rule
        return Outcome.ok(Unit)
    }

    fun removeSell(company: CompanyId, facility: FacilityId, good: GoodId): Boolean = sells.remove(Triple(company.value, facility.value, good.key)) != null
    fun removeBuy(company: CompanyId, facility: FacilityId, good: GoodId): Boolean = buys.remove(Triple(company.value, facility.value, good.key)) != null

    fun sellRules(company: CompanyId): List<SellRule> = sells.filterKeys { it.first == company.value }.values.toList()
    fun buyRules(company: CompanyId): List<BuyRule> = buys.filterKeys { it.first == company.value }.values.toList()
    fun sellRule(company: CompanyId, facility: FacilityId, good: GoodId): SellRule? = sells[Triple(company.value, facility.value, good.key)]
    fun buyRule(company: CompanyId, facility: FacilityId, good: GoodId): BuyRule? = buys[Triple(company.value, facility.value, good.key)]

    fun setDividend(company: CompanyId, policy: DividendPolicy) { dividends[company] = policy }
    fun dividend(company: CompanyId): DividendPolicy = dividends[company] ?: DividendPolicy()

    fun dropFacility(company: CompanyId, facility: FacilityId) {
        sells.keys.removeAll { it.first == company.value && it.second == facility.value }
        buys.keys.removeAll { it.first == company.value && it.second == facility.value }
    }

    fun remove(company: CompanyId) {
        sells.keys.removeAll { it.first == company.value }
        buys.keys.removeAll { it.first == company.value }
        dividends.remove(company)
    }

    internal fun allSells(): List<Pair<CompanyId, SellRule>> = sells.map { CompanyId(it.key.first) to it.value }
    internal fun allBuys(): List<Pair<CompanyId, BuyRule>> = buys.map { CompanyId(it.key.first) to it.value }
    internal fun allDividends(): List<Pair<CompanyId, DividendPolicy>> = dividends.map { it.key to it.value }
    internal fun clear() { sells.clear(); buys.clear(); dividends.clear() }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(sells.size)
        for ((k, r) in sells) h.putLong(k.first).putLong(k.second).putString(k.third).putInt(r.strategy.ordinal).putLong(r.markupPpm).putQuantity(r.reserve)
            .putQuantity(r.maxPerOrder ?: Quantity(-1)).putLong(r.minMarginPpm).putBoolean(r.enabled)
        h.putInt(buys.size)
        for ((k, r) in buys) h.putLong(k.first).putLong(k.second).putString(k.third).putQuantity(r.target).putLong(r.maxPriceMultiplePpm).putBoolean(r.enabled)
        h.putInt(dividends.size)
        for ((c, d) in dividends) h.putLong(c.value).putLong(d.payoutPpm).putInt(d.reserveTicks).putInt(d.intervalTicks)
    }

    companion object { const val MAX_RULES = 200 }
}
