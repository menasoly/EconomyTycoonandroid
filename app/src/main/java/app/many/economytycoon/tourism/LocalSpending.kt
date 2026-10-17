package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

enum class SpendCategory(val label: String, val residentSharePpm: Long) {
    RESTAURANTS("Restaurants", 300_000L),
    SHOPS("Shops", 400_000L),
    TRANSPORT("Local transport", 120_000L),
    ENTERTAINMENT("Entertainment", 180_000L),
}

/** Money visitors spend in the destination outside their room, by category. */
class SpendBreakdown(private val amounts: Map<SpendCategory, Money>) {
    operator fun get(c: SpendCategory): Money = amounts[c] ?: Money.ZERO
    val total: Money get() = Money.sum(amounts.values)
    fun toMap(): Map<SpendCategory, Money> = amounts
}

/** The effect of tourists on one category of local business. */
data class CategoryUplift(
    val category: SpendCategory,
    val touristSpend: Money,
    val residentSpend: Money,
    /** Extra demand tourists bring as a share of what residents spend, ppm. */
    val upliftPpm: Long,
)

object LocalSpending {
    /** How the spending of each segment divides: restaurants, shops, transport, entertainment. */
    fun shares(segment: VisitorSegment): LongArray = when (segment) {
        VisitorSegment.BUDGET -> longArrayOf(400_000L, 250_000L, 200_000L, 150_000L)
        VisitorSegment.FAMILY -> longArrayOf(350_000L, 250_000L, 100_000L, 300_000L)
        VisitorSegment.BUSINESS -> longArrayOf(450_000L, 150_000L, 250_000L, 150_000L)
        VisitorSegment.LUXURY -> longArrayOf(300_000L, 450_000L, 100_000L, 150_000L)
    }

    /** What one visitor of [segment] spends in a day at the destination's price level. */
    fun perGuestDay(segment: VisitorSegment, priceLevelPpm: Long): Money =
        Money(mulDiv(segment.profile.dailySpend.raw, priceLevelPpm.coerceAtLeast(0L), 1_000_000L))

    /** Spending of the guests present, by segment (persons per segment in segment order), split into categories. */
    fun breakdown(guestsBySegment: LongArray, priceLevelPpm: Long): SpendBreakdown {
        val totals = LongArray(SpendCategory.values().size)
        for (s in VisitorSegment.values()) {
            val guests = guestsBySegment.getOrElse(s.ordinal) { 0L }
            if (guests <= 0L) continue
            val spend = perGuestDay(s, priceLevelPpm).raw * guests
            val parts = app.many.economytycoon.core.splitLargestRemainder(spend, shares(s))
            for (i in totals.indices) totals[i] += parts[i]
        }
        return SpendBreakdown(SpendCategory.values().associateWith { Money(totals[it.ordinal]) })
    }

    /** What residents spend in a day in the category: population times per-person discretionary spending times its share. */
    fun residentSpend(category: SpendCategory, population: Long, perCapitaPerDay: Money): Money =
        Money(mulDiv(perCapitaPerDay.raw * population, category.residentSharePpm, 1_000_000L))

    /** Extra demand from tourists relative to residents; zero when there are no residents to compare with and no tourists. */
    fun upliftPpm(touristSpend: Money, residentSpend: Money): Long {
        if (touristSpend.raw <= 0L) return 0L
        if (residentSpend.raw <= 0L) return MAX_UPLIFT_PPM
        return mulDiv(touristSpend.raw, 1_000_000L, residentSpend.raw).coerceAtMost(MAX_UPLIFT_PPM)
    }

    const val MAX_UPLIFT_PPM = 5_000_000L

    fun uplifts(spend: SpendBreakdown, population: Long, perCapitaPerDay: Money): List<CategoryUplift> =
        SpendCategory.values().map { c ->
            val resident = residentSpend(c, population, perCapitaPerDay)
            CategoryUplift(c, spend[c], resident, upliftPpm(spend[c], resident))
        }

    /**
     * The local multiplier: each unit of tourist spending is re-spent locally at [respendPpm] after [leakagePpm] has
     * leaked out of the area, giving 1 / (1 - respend x (1 - leakage)). Returned in ppm (1,000,000 = no multiplier).
     */
    fun multiplierPpm(respendPpm: Long, leakagePpm: Long): Long {
        val kept = mulDiv(respendPpm.coerceIn(0L, 900_000L), 1_000_000L - leakagePpm.coerceIn(0L, 1_000_000L), 1_000_000L)
        return mulDiv(1_000_000L, 1_000_000L, 1_000_000L - kept)
    }

    /** Money tourists bring into the economy in total, including the respending round. */
    fun economicImpact(spend: Money, multiplierPpm: Long): Money = Money(mulDiv(spend.raw, multiplierPpm, 1_000_000L))
}
