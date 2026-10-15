package app.many.economytycoon.companies

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.IndustryId
import app.many.economytycoon.core.mulDiv

/** How the outside world sees a company, each from 0 to 1,000,000 ppm. */
class Reputation {
    var customers: Long = 500_000
        internal set
    var employees: Long = 500_000
        internal set
    var regulators: Long = 600_000
        internal set
    var investors: Long = 500_000
        internal set

    val overall: Long get() = (customers + employees + regulators + investors) / 4

    /** Nudges one dimension toward [target] by [weightPpm] of the gap. */
    fun nudge(dimension: Dimension, target: Long, weightPpm: Long) {
        val t = target.coerceIn(0L, 1_000_000L)
        when (dimension) {
            Dimension.CUSTOMERS -> customers = move(customers, t, weightPpm)
            Dimension.EMPLOYEES -> employees = move(employees, t, weightPpm)
            Dimension.REGULATORS -> regulators = move(regulators, t, weightPpm)
            Dimension.INVESTORS -> investors = move(investors, t, weightPpm)
        }
    }

    /** One-off hit or boost, e.g. a recall or a successful launch. */
    fun shock(dimension: Dimension, deltaPpm: Long) {
        fun c(v: Long) = (v + deltaPpm).coerceIn(0L, 1_000_000L)
        when (dimension) {
            Dimension.CUSTOMERS -> customers = c(customers)
            Dimension.EMPLOYEES -> employees = c(employees)
            Dimension.REGULATORS -> regulators = c(regulators)
            Dimension.INVESTORS -> investors = c(investors)
        }
    }

    private fun move(current: Long, target: Long, weight: Long) = current + mulDiv(target - current, weight, 1_000_000L)

    enum class Dimension { CUSTOMERS, EMPLOYEES, REGULATORS, INVESTORS }

    internal fun restore(c: Long, e: Long, r: Long, i: Long) { customers = c; employees = e; regulators = r; investors = i }

    fun hashInto(h: CanonicalHasher) { h.putLong(customers).putLong(employees).putLong(regulators).putLong(investors) }
}

/**
 * Brand strength of one company in one industry. Awareness is bought with marketing and fades without
 * it; loyalty follows delivered quality and fair prices and changes slowly.
 */
class Brand {
    var awarenessPpm: Long = 0
        internal set
    var loyaltyPpm: Long = 300_000
        internal set

    /** Pricing power: how far above the going rate customers will still buy, ppm. */
    val premiumPpm: Long get() = mulDiv(awarenessPpm, loyaltyPpm, 1_000_000L) / 5

    /**
     * Advances one tick. [marketingEffectPpm] is the department effect (0..max), [qualityPpm] the delivered
     * quality against expectations (1,000,000 = as expected), [priceFairnessPpm] above 1,000,000 means the
     * company charges more than the market and loses loyalty.
     */
    fun advance(marketingEffectPpm: Long, qualityPpm: Long, priceFairnessPpm: Long) {
        awarenessPpm = (awarenessPpm + mulDiv(1_000_000L - awarenessPpm, marketingEffectPpm, 40_000_000L) - awarenessPpm / 400).coerceIn(0L, 1_000_000L)
        val loyaltyTarget = (qualityPpm - (priceFairnessPpm - 1_000_000L).coerceAtLeast(0L) / 2).coerceIn(0L, 1_000_000L)
        loyaltyPpm += (loyaltyTarget - loyaltyPpm) / 60
        loyaltyPpm = loyaltyPpm.coerceIn(0L, 1_000_000L)
    }

    internal fun restore(awareness: Long, loyalty: Long) { awarenessPpm = awareness; loyaltyPpm = loyalty }

    fun hashInto(h: CanonicalHasher) { h.putLong(awarenessPpm).putLong(loyaltyPpm) }
}

/** Reputation and brands for every company. */
class BrandBook {
    private val reputations = java.util.TreeMap<CompanyId, Reputation>()
    private val brands = java.util.TreeMap<Pair<CompanyId, IndustryId>, Brand>(compareBy({ it.first }, { it.second }))

    fun reputation(c: CompanyId): Reputation = reputations.getOrPut(c) { Reputation() }
    fun brand(c: CompanyId, industry: IndustryId): Brand = brands.getOrPut(c to industry) { Brand() }
    fun brandsOf(c: CompanyId): List<Pair<IndustryId, Brand>> = brands.filterKeys { it.first == c }.map { it.key.second to it.value }
    fun brandIfAny(c: CompanyId, industry: IndustryId): Brand? = brands[c to industry]
    fun remove(c: CompanyId) { reputations.remove(c); brands.keys.removeAll { it.first == c } }
    fun allReputations(): List<Pair<CompanyId, Reputation>> = reputations.map { it.key to it.value }
    fun allBrands(): List<Triple<CompanyId, IndustryId, Brand>> = brands.map { Triple(it.key.first, it.key.second, it.value) }
    internal fun clear() { reputations.clear(); brands.clear() }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(reputations.size)
        for ((c, r) in reputations) { h.putLong(c.value); r.hashInto(h) }
        h.putInt(brands.size)
        for ((k, b) in brands) { h.putLong(k.first.value).putString(k.second.key); b.hashInto(h) }
    }
}
