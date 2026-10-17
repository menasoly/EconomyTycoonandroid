package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.markets.ConsumerDemand
import app.many.economytycoon.markets.WorldPopulationView
import app.many.economytycoon.marketing.AiCompanyView
import app.many.economytycoon.marketing.AiPosition
import app.many.economytycoon.marketing.BrandKey
import app.many.economytycoon.marketing.MarketContext
import app.many.economytycoon.marketing.MarketKey
import app.many.economytycoon.production.FacilityStatus

/**
 * Reads the economy into the products a company advertises, as the marketing planners want to see them: for each good it
 * offers on its standing sell rules in a region, what the whole market earns a day, this company's even share of it, and
 * the margin left after what the stock cost. Only goods households buy count, since only those respond to advertising.
 */
class MarketingPositions(private val e: Economy) {
    private val population = WorldPopulationView(e.world)

    /** How many companies have an enabled sell rule for each product and region. */
    fun sellerCounts(): Map<MarketKey, Int> {
        val out = java.util.TreeMap<MarketKey, Int>()
        for (c in e.companies.all()) {
            if (c.status != CompanyStatus.ACTIVE) continue
            val seen = HashSet<MarketKey>()
            for (rule in e.companies.policies.sellRules(c.id)) {
                if (!rule.enabled) continue
                val f = e.production.facility(rule.facility) ?: continue
                if (f.owner != c.id || f.status == FacilityStatus.DEMOLISHED) continue
                val key = MarketKey(rule.good, f.region)
                if (seen.add(key)) out.merge(key, 1, Int::plus)
            }
        }
        return out
    }

    fun positions(company: CompanyId, tick: Tick, sellers: Map<MarketKey, Int> = sellerCounts()): List<AiPosition> {
        val out = java.util.TreeMap<MarketKey, AiPosition>()
        val season = Calendar.season(tick)
        for (rule in e.companies.policies.sellRules(company)) {
            if (!rule.enabled) continue
            val f = e.production.facility(rule.facility) ?: continue
            if (f.owner != company || f.status == FacilityStatus.DEMOLISHED) continue
            val key = MarketKey(rule.good, f.region)
            if (out.containsKey(key)) continue
            val profile = e.content.demandProfile(rule.good) ?: continue
            val audience = e.marketingRunner.audiences.audience(f.region) ?: continue
            if (audience.inhabitants <= 0L) continue
            val price = e.markets.price(app.many.economytycoon.markets.MarketKey(f.region, rule.good)).perUnit
            if (price.raw <= 0L) continue
            val cost = f.inventory.unitCost(rule.good)
            val margin = if (cost.raw <= 0L) DEFAULT_MARGIN_PPM else mulDiv((price - cost).raw, 1_000_000L, price.raw)
            if (margin < MIN_MARGIN_PPM) continue
            val quantity = ConsumerDemand.baseQuantity(profile, population, f.region, season)
            val market = price.valueOf(Quantity(quantity))
            val share = Money(market.raw / (sellers[key] ?: 1).coerceAtLeast(1))
            if (share.raw <= 0L) continue
            val taint = e.marketing.state.stock(BrandKey(company, rule.good, f.region))?.taintPpm ?: 0L
            out[key] = AiPosition(MarketContext(rule.good, f.region, audience, share, margin.coerceAtMost(MAX_MARGIN_PPM), taintPpm = taint), share)
        }
        return out.values.toList()
    }

    /** The view the marketing planner works from for one company. */
    fun view(company: CompanyId, tick: Tick, sellers: Map<MarketKey, Int> = sellerCounts()): AiCompanyView? {
        val c = e.companies.company(company) ?: return null
        val from = Tick((tick.value - 30L).coerceAtLeast(0L))
        val income = e.finance.statements.income(c.owner, from, tick)
        return AiCompanyView(company, e.finance.ledger.cash(c.owner), income.revenue, positions(company, tick, sellers))
    }

    private companion object {
        const val DEFAULT_MARGIN_PPM = 300_000L
        const val MIN_MARGIN_PPM = 50_000L
        const val MAX_MARGIN_PPM = 900_000L
    }
}
