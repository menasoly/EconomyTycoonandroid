package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv

/**
 * A country's carbon border adjustment. Imports that carry more carbon than the importing country lets its own producers
 * release for free pay the difference between the two countries' carbon prices on the carbon embedded in the goods;
 * exporters from a country that prices carbon may get that cost back when they sell to a country that does not.
 *
 *  * [goods]: the goods covered; empty means every good whose embedded carbon reaches [minIntensityGrams].
 *  * [phaseInYears]: the charge rises in equal steps over this many years from [startYear].
 */
data class BorderPolicy(
    val country: CountryId,
    val enabled: Boolean = true,
    val goods: List<GoodId> = emptyList(),
    val minIntensityGrams: Long = 20_000L,
    val startYear: Int,
    val phaseInYears: Int = 3,
    val exportRebatePpm: Long = 0L,
) {
    init {
        require(phaseInYears >= 1) { "the phase-in lasts at least a year" }
        require(exportRebatePpm in 0L..Rate.ONE_PPM) { "the rebate is a share" }
        require(minIntensityGrams >= 0L) { "the intensity threshold cannot be negative" }
    }

    /** Share of the full charge applied in [year], in ppm. */
    fun phasePpm(year: Int): Long {
        if (!enabled || year < startYear) return 0L
        val step = (year - startYear + 1).coerceAtMost(phaseInYears)
        return mulDiv(step.toLong(), Rate.ONE_PPM, phaseInYears.toLong(), RoundingMode.HALF_EVEN)
    }

    fun covers(good: GoodId, embeddedGramsPerUnit: Long): Boolean =
        if (goods.isNotEmpty()) good in goods else embeddedGramsPerUnit >= minIntensityGrams
}

/** One consignment crossing a border. */
data class TradeFlow(
    val good: GoodId,
    val quantity: Quantity,
    val from: CountryId,
    val to: CountryId,
    val importer: CompanyId?,
    val exporter: CompanyId?,
)

/** What the border adjustment makes of a consignment. */
data class BorderAssessment(
    val embedded: Tonnes,
    val originPrice: Money,
    val destinationPrice: Money,
    val levy: Money,
    val rebate: Money,
    val phasePpm: Long,
) {
    companion object {
        fun none(embedded: Tonnes, originPrice: Money, destinationPrice: Money) =
            BorderAssessment(embedded, originPrice, destinationPrice, Money.ZERO, Money.ZERO, 0L)
    }
}

object BorderAdjuster {
    /**
     * Assesses [flow]. [embeddedGramsPerUnit] is the good's embedded carbon, [originPrice] and [destinationPrice] are the
     * carbon prices actually paid in the two countries, and [importPolicy] and [exportPolicy] are the policies of the
     * destination and the origin if they have one. Neither a levy nor a rebate arises on trade inside a country.
     */
    fun assess(
        flow: TradeFlow, embeddedGramsPerUnit: Long, originPrice: Money, destinationPrice: Money,
        importPolicy: BorderPolicy?, exportPolicy: BorderPolicy?, year: Int,
    ): BorderAssessment {
        val embedded = Tonnes.ofGrams(mulDiv(flow.quantity.raw, embeddedGramsPerUnit, Quantity.SCALE, RoundingMode.HALF_EVEN))
        if (flow.from == flow.to || !embedded.isPositive) return BorderAssessment.none(embedded, originPrice, destinationPrice)
        var levy = Money.ZERO
        var rebate = Money.ZERO
        var phase = 0L
        if (importPolicy != null && importPolicy.covers(flow.good, embeddedGramsPerUnit)) {
            phase = importPolicy.phasePpm(year)
            val gap = destinationPrice - originPrice
            if (gap.isPositive && phase > 0L) levy = embedded.cost(gap).times(Rate(phase))
        }
        if (exportPolicy != null && exportPolicy.exportRebatePpm > 0L && exportPolicy.covers(flow.good, embeddedGramsPerUnit)) {
            val exportPhase = exportPolicy.phasePpm(year)
            val gap = originPrice - destinationPrice
            if (gap.isPositive && exportPhase > 0L) {
                rebate = embedded.cost(gap).times(Rate(exportPhase)).times(Rate(exportPolicy.exportRebatePpm))
                phase = maxOf(phase, exportPhase)
            }
        }
        return BorderAssessment(embedded, originPrice, destinationPrice, levy, rebate, phase)
    }
}

/** The border policies in force and the money they have moved. */
class BorderBook {
    private val policyMap = java.util.TreeMap<CountryId, BorderPolicy>()
    private val levyMap = java.util.TreeMap<CountryId, Long>()
    private val rebateMap = java.util.TreeMap<CountryId, Long>()

    fun policies(): List<BorderPolicy> = policyMap.values.toList()
    fun policy(country: CountryId): BorderPolicy? = policyMap[country]
    fun levied(country: CountryId): Money = Money(levyMap[country] ?: 0L)
    fun rebated(country: CountryId): Money = Money(rebateMap[country] ?: 0L)

    internal fun set(p: BorderPolicy) { policyMap[p.country] = p }
    internal fun remove(country: CountryId) { policyMap.remove(country) }
    internal fun noteLevy(country: CountryId, amount: Money) { levyMap.merge(country, amount.raw, Long::plus) }
    internal fun noteRebate(country: CountryId, amount: Money) { rebateMap.merge(country, amount.raw, Long::plus) }
    internal fun putTotals(country: CountryId, levied: Long, rebated: Long) { levyMap[country] = levied; rebateMap[country] = rebated }
    internal fun totalEntries(): List<Triple<CountryId, Long, Long>> =
        (levyMap.keys + rebateMap.keys).toSortedSet().map { Triple(it, levyMap[it] ?: 0L, rebateMap[it] ?: 0L) }
    internal fun clear() { policyMap.clear(); levyMap.clear(); rebateMap.clear() }
}
