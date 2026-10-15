package app.many.economytycoon.intel

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.mulDiv

/** One production line's nominal movement of a good: made or used, per day, at full tilt. */
class LineFlow(
    val company: CompanyId,
    val facility: FacilityId,
    val good: GoodId,
    /** Quantity (raw thousandths) per day when the line runs without pause. */
    val perDay: Long,
    /** Share of the time the line has actually been running, in parts per million. */
    val utilizationPpm: Long,
    /** True for an output, false for an input. */
    val makes: Boolean,
) {
    val actualPerDay: Long get() = mulDiv(perDay, utilizationPpm.coerceIn(0L, 1_000_000L), 1_000_000L)
}

/** What one company contributes to a good. */
class Producer(
    val company: CompanyId,
    val name: String,
    val capacityPerDay: Long,
    val actualPerDay: Long,
    val facilities: Int,
    /** Share of all capacity, in parts per million. */
    val sharePpm: Long,
)

/** How tightly a market is held by few sellers. */
enum class Concentration(val label: String) {
    NONE("Nobody makes it"),
    MONOPOLY("One seller"),
    CONCENTRATED("Concentrated"),
    MODERATE("Moderately concentrated"),
    COMPETITIVE("Competitive"),
}

/** Whether more is made than the factories themselves take. */
enum class Balance(val label: String) {
    SURPLUS("More made than used"),
    BALANCED("Roughly in balance"),
    SHORT("Industry uses more than is made"),
    UNUSED("Only households buy it"),
    UNMADE("Not made anywhere"),
}

class GoodSector(
    val good: GoodId,
    /** Largest first. */
    val producers: List<Producer>,
    val capacityPerDay: Long,
    val actualPerDay: Long,
    /** What factories take in per day, at the rate they have been running. */
    val industryUsePerDay: Long,
    /** Average market price over its reference price, in parts per million; 1,000,000 means at reference. */
    val priceRatioPpm: Long,
) {
    /** Herfindahl index of capacity shares on the usual 0..10,000 scale. */
    val hhi: Long get() = producers.sumOf { (it.sharePpm / 100L) * (it.sharePpm / 100L) } / 10_000L

    val concentration: Concentration get() = when {
        producers.isEmpty() -> Concentration.NONE
        producers.size == 1 -> Concentration.MONOPOLY
        hhi >= 2_500L -> Concentration.CONCENTRATED
        hhi >= 1_500L -> Concentration.MODERATE
        else -> Concentration.COMPETITIVE
    }

    val balance: Balance get() = when {
        producers.isEmpty() -> Balance.UNMADE
        industryUsePerDay == 0L -> Balance.UNUSED
        actualPerDay * 100L >= industryUsePerDay * 115L -> Balance.SURPLUS
        actualPerDay * 100L <= industryUsePerDay * 85L -> Balance.SHORT
        else -> Balance.BALANCED
    }

    val leader: Producer? get() = producers.firstOrNull()

    fun shareOf(company: CompanyId): Long = producers.firstOrNull { it.company == company }?.sharePpm ?: 0L

    /** One-based place by capacity, or null for a company that does not make the good. */
    fun rankOf(company: CompanyId): Int? = producers.indexOfFirst { it.company == company }.takeIf { it >= 0 }?.plus(1)

    /** Few sellers and a price well above normal: room for another producer. */
    fun isOpening(minRatioPpm: Long = 1_150_000L, maxProducers: Int = 2): Boolean =
        producers.size <= maxProducers && priceRatioPpm >= minRatioPpm
}

object SectorMath {
    /** Groups line flows into one sector per good that is made or used. */
    fun build(flows: List<LineFlow>, names: (CompanyId) -> String, priceRatio: (GoodId) -> Long): List<GoodSector> {
        val byGood = java.util.TreeMap<GoodId, MutableList<LineFlow>>(compareBy { it.key })
        for (f in flows) byGood.getOrPut(f.good) { ArrayList() } += f
        return byGood.map { (good, list) ->
            val makers = list.filter { it.makes }
            val capacity = makers.sumOf { it.perDay }
            val perCompany = java.util.TreeMap<CompanyId, MutableList<LineFlow>>()
            for (m in makers) perCompany.getOrPut(m.company) { ArrayList() } += m
            val producers = perCompany.map { (c, ls) ->
                val cap = ls.sumOf { it.perDay }
                Producer(c, names(c), cap, ls.sumOf { it.actualPerDay }, ls.map { it.facility }.distinct().size, if (capacity <= 0L) 0L else mulDiv(cap, 1_000_000L, capacity))
            }.sortedWith(compareByDescending<Producer> { it.capacityPerDay }.thenBy { it.company })
            GoodSector(good, producers, capacity, makers.sumOf { it.actualPerDay }, list.filter { !it.makes }.sumOf { it.actualPerDay }, priceRatio(good))
        }
    }
}
