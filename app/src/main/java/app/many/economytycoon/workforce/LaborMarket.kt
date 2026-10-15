package app.many.economytycoon.workforce

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.mulDiv

data class MarketKey(val region: RegionId, val role: RoleId) : Comparable<MarketKey> {
    override fun compareTo(other: MarketKey): Int {
        val c = region.compareTo(other.region)
        return if (c != 0) c else role.compareTo(other.role)
    }
}

/** Supply, demand and the going wage for one role in one region. */
class RoleMarket {
    /** Jobseekers available this tick. Set by the simulation from the world's labor force. */
    var available: Long = 0
        internal set
    var demand: Long = 0
        internal set
    var wageIndexPpm: Long = 1_000_000
        internal set
    var hiredThisTick: Long = 0
        internal set
    var employed: Long = 0
        internal set
    var vacancies: Long = 0
        internal set

    val tightnessPpm: Long
        get() = if (available + hiredThisTick <= 0) 3_000_000L else mulDiv(demand, 1_000_000L, available + hiredThisTick + 1).coerceAtMost(4_000_000L)

    internal fun restore(index: Long, employed: Long) { wageIndexPpm = index; this.employed = employed }
}

/**
 * Labor markets per (region, role). Wages drift toward a level that reflects how tight the market is:
 * scarce workers earn more, a glut of jobseekers pushes wages down, never faster than a few percent a month.
 */
class LaborMarkets(private val catalog: RoleCatalog) {
    private val markets = java.util.TreeMap<MarketKey, RoleMarket>()
    private val regionIndex = java.util.TreeMap<RegionId, Long>()
    private val minimumWage = java.util.TreeMap<RegionId, Money>()

    fun market(key: MarketKey): RoleMarket = markets.getOrPut(key) { RoleMarket() }
    fun markets(): Map<MarketKey, RoleMarket> = markets

    fun setRegionIndex(region: RegionId, ppm: Long) { regionIndex[region] = ppm }
    fun regionIndex(region: RegionId): Long = regionIndex[region] ?: 1_000_000L
    fun setMinimumWage(region: RegionId, wage: Money) { minimumWage[region] = wage }
    fun minimumWage(region: RegionId): Money = minimumWage[region] ?: Money.ZERO

    fun setSupply(key: MarketKey, available: Long) { market(key).available = available.coerceAtLeast(0) }

    /** Going daily wage for a role, never below the legal minimum. */
    fun marketWage(key: MarketKey): Money {
        val role = catalog.role(key.role)
        val m = market(key)
        val base = Money(mulDiv(role.baseWage.raw, mulDiv(regionIndex(key.region), m.wageIndexPpm, 1_000_000L), 1_000_000L))
        return Money.max(base, minimumWage(key.region))
    }

    fun beginTick() {
        for (m in markets.values) { m.hiredThisTick = 0; m.demand = 0; m.vacancies = 0 }
    }

    /** Records that [count] workers were taken from the pool. Returns how many were actually available. */
    fun take(key: MarketKey, count: Long): Long {
        val m = market(key)
        val got = count.coerceIn(0L, m.available)
        m.available -= got
        m.hiredThisTick += got
        return got
    }

    fun release(key: MarketKey, count: Long) { market(key).available += count }

    /** Monthly wage adjustment toward the tightness-implied level, limited to maxStepPpm per call. */
    fun adjustWages(maxStepPpm: Long = 30_000L) {
        for (m in markets.values) {
            val t = m.tightnessPpm
            // Balanced at tightness 1.0; every 10% of excess demand adds 3% to the target index.
            val target = (1_000_000L + (t - 1_000_000L) * 3 / 10).coerceIn(700_000L, 1_800_000L)
            val delta = (target - m.wageIndexPpm).coerceIn(-maxStepPpm, maxStepPpm)
            m.wageIndexPpm += delta
        }
    }

    /** Average wage index of a region across roles weighted by employment. */
    fun regionalWageLevel(region: RegionId): Rate {
        var sum = 0L
        var weight = 0L
        for ((k, m) in markets) if (k.region == region) { sum += m.wageIndexPpm * (m.employed + 1); weight += m.employed + 1 }
        return Rate(if (weight == 0L) 1_000_000L else sum / weight)
    }
}
