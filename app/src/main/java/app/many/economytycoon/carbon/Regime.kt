package app.many.economytycoon.carbon

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv

/** How a country prices carbon. */
enum class RegimeKind(val label: String, val blurb: String) {
    NONE("No carbon policy", "Emissions are free. Nothing is reported to the regulator."),
    CARBON_TAX("Carbon tax", "Every tonne released is taxed at a rate the government sets and raises on a schedule."),
    CAP_AND_TRADE("Cap and trade", "The government caps total emissions and issues permits. Companies surrender one per tonne and trade what they do not need."),
    INTENSITY_STANDARD("Intensity standard", "Emissions per unit of output may not pass a benchmark that tightens each year. Every tonne over it pays a levy."),
}

/**
 * One phase of a regime. A step takes effect in [fromYear] and holds until the next step's year, so a schedule with a
 * wider [coverage], a lower [freeSharePpm] or a smaller [cap] in a later step is a phase-in. Which fields matter depends
 * on the regime kind:
 *  * cap and trade uses [cap], [capCutPpm], [freeSharePpm], [floor], [ceiling], [penalty] and [offsetLimitPpm];
 *  * a carbon tax uses [rate] and [rateEscalatorPpm];
 *  * an intensity standard uses [benchmarkGramsPerUnit], [benchmarkCutPpm], [rate] as the levy per excess tonne and [offsetLimitPpm].
 */
data class RegimeStep(
    val fromYear: Int,
    /** Industry families the step covers, sorted by their position in the enum. */
    val coverage: List<IndustryFamily>,
    /** A company in the country emitting less than this in a year, from covered facilities, is exempt. */
    val threshold: Tonnes = Tonnes.ZERO,
    val rate: Money = Money.ZERO,
    val rateEscalatorPpm: Long = 0L,
    val cap: Tonnes = Tonnes.ZERO,
    val capCutPpm: Long = 0L,
    val freeSharePpm: Long = 0L,
    val floor: Money = Money.ZERO,
    /** Price at which the government sells any number of extra permits; zero means there is no ceiling. */
    val ceiling: Money = Money.ZERO,
    /** Fine per tonne of shortfall. */
    val penalty: Money = Money.ZERO,
    /** Allowed emissions in grams per unit of output value. */
    val benchmarkGramsPerUnit: Long = 0L,
    val benchmarkCutPpm: Long = 0L,
    /** The most of an obligation that offsets may cover. */
    val offsetLimitPpm: Long = 100_000L,
    /** Whether electricity use also counts towards an obligation. */
    val coversIndirect: Boolean = false,
    /** Share of the cost of an abatement project the government pays back from the green fund. */
    val abatementSubsidyPpm: Long = 0L,
) {
    fun covers(family: IndustryFamily): Boolean = family in coverage

    /** Years the step has been in force by [year]. */
    fun age(year: Int): Int = (year - fromYear).coerceAtLeast(0)

    /** The cap in [year]: the step's cap cut by [capCutPpm] for every year since the step began. */
    fun capIn(year: Int): Tonnes {
        var c = cap.raw
        repeat(age(year)) { c = mulDiv(c, Rate.ONE_PPM - capCutPpm, Rate.ONE_PPM, RoundingMode.DOWN) }
        return Tonnes(c)
    }

    /** The tax or levy rate in [year]: the step's rate raised by [rateEscalatorPpm] for every year since the step began. */
    fun rateIn(year: Int): Money {
        var r = rate.raw
        repeat(age(year)) { r = mulDiv(r, Rate.ONE_PPM + rateEscalatorPpm, Rate.ONE_PPM, RoundingMode.HALF_EVEN) }
        return Money(r)
    }

    /** The benchmark in [year]: the step's figure cut by [benchmarkCutPpm] for every year since the step began. */
    fun benchmarkIn(year: Int): Long {
        var b = benchmarkGramsPerUnit
        repeat(age(year)) { b = mulDiv(b, Rate.ONE_PPM - benchmarkCutPpm, Rate.ONE_PPM, RoundingMode.DOWN) }
        return b
    }

    /** Permits handed out free in [year]. */
    fun freeIn(year: Int): Tonnes = capIn(year).times(Rate(freeSharePpm))

    /** Permits sold at auction in [year] before any reserve adjustment. */
    fun auctionIn(year: Int): Tonnes = capIn(year) - freeIn(year)
}

/** How the market stability reserve takes permits out of the market when there are too many and puts them back when there are too few. */
data class ReserveRule(
    /** When the permits in circulation pass this share of the annual cap, a share of them is withheld from auctions. */
    val upperPpm: Long = 1_200_000L,
    /** When they fall below this share of the annual cap, permits are released from the reserve. */
    val lowerPpm: Long = 400_000L,
    /** Share of the permits in circulation moved into the reserve in a year of surplus. */
    val intakePpm: Long = 240_000L,
    /** Share of the annual cap released from the reserve in a year of scarcity. */
    val releasePpm: Long = 100_000L,
    /** A quarterly auction price above this multiple of the trailing average triggers an extra release. */
    val priceTriggerPpm: Long = 2_000_000L,
    /** Share of the annual cap released on a price trigger. */
    val priceReleasePpm: Long = 40_000L,
    /** Reserve above this share of the annual cap is cancelled for good. */
    val maxStockPpm: Long = 1_500_000L,
)

/**
 * The rules one country runs. A regime is plain immutable data: a government changing its policy swaps in a new one,
 * and the market, the accounts and the schedule keep their history.
 */
data class CarbonRegime(
    val country: CountryId,
    val kind: RegimeKind,
    val steps: List<RegimeStep> = emptyList(),
    val bankingAllowed: Boolean = true,
    /** Years a permit stays valid after the year it is issued for. */
    val vintageLifeYears: Int = 3,
    /** Share of a company's next-year permits it may surrender early. */
    val borrowLimitPpm: Long = 100_000L,
    /** Extra obligation, as a share of what was borrowed, added to the next year. */
    val borrowInterestPpm: Long = 80_000L,
    /** The most one company may hold, as a share of the annual cap. */
    val holdingLimitPpm: Long = 400_000L,
    /** Days after a year ends in which its permits must be surrendered. */
    val surrenderWindowDays: Int = 90,
    /** Whether a shortfall must still be surrendered the next year, on top of the fine. */
    val makeGood: Boolean = true,
    /** How much the fine rises for each consecutive year of non-compliance. */
    val repeatPenaltyStepPpm: Long = 250_000L,
    val eligibleOffsets: List<OffsetKind> = listOf(OffsetKind.METHANE_CAPTURE, OffsetKind.REFORESTATION, OffsetKind.CLEAN_COOKING),
    val reserve: ReserveRule = ReserveRule(),
    /** Share of receipts (auctions, taxes, fines) kept in the green fund that pays abatement subsidies. */
    val greenFundSharePpm: Long = 300_000L,
) {
    val isRegulated: Boolean get() = kind != RegimeKind.NONE && steps.isNotEmpty()
    val tradesPermits: Boolean get() = kind == RegimeKind.CAP_AND_TRADE

    /** The step in force in [year], or null before the first step begins or when there are none. */
    fun stepFor(year: Int): RegimeStep? = steps.lastOrNull { it.fromYear <= year }

    /** True when carbon is priced for [family] in [year]. */
    fun covers(family: IndustryFamily, year: Int): Boolean = isRegulated && (stepFor(year)?.covers(family) ?: false)

    /**
     * What a tonne costs to release in [year]: the tax, the levy or the market price of a permit. Zero where no
     * policy is in force.
     */
    fun priceIn(year: Int, permitPrice: Money): Money {
        val step = stepFor(year) ?: return Money.ZERO
        return when (kind) {
            RegimeKind.NONE -> Money.ZERO
            RegimeKind.CARBON_TAX, RegimeKind.INTENSITY_STANDARD -> step.rateIn(year)
            RegimeKind.CAP_AND_TRADE -> permitPrice
        }
    }

    /** A rough view of the price in [yearsAhead] years for investment appraisal: the tightening of the cap or the escalator. */
    fun expectedPrice(year: Int, current: Money, yearsAhead: Int): Money {
        if (yearsAhead <= 0) return current
        val step = stepFor(year + yearsAhead)
        return when (kind) {
            RegimeKind.NONE -> Money.ZERO
            RegimeKind.CARBON_TAX, RegimeKind.INTENSITY_STANDARD -> step?.rateIn(year + yearsAhead) ?: current
            RegimeKind.CAP_AND_TRADE -> {
                var p = current.raw
                val drift = (step?.capCutPpm ?: 0L) + DRIFT_PREMIUM_PPM
                repeat(yearsAhead) { p = mulDiv(p, Rate.ONE_PPM + drift, Rate.ONE_PPM, RoundingMode.HALF_EVEN) }
                val ceiling = step?.ceiling ?: Money.ZERO
                if (ceiling.isPositive) Money.min(Money(p), ceiling) else Money(p)
            }
        }
    }

    /** Checks the rules hang together. A regime that fails here is never installed. */
    fun validate(): Outcome<CarbonRegime> {
        fun bad(msg: String) = Outcome.reject(Rules.INVALID_ARGUMENT, msg)
        if (kind == RegimeKind.NONE) return Outcome.ok(this)
        if (steps.isEmpty()) return bad("a carbon policy needs at least one step")
        for ((i, s) in steps.withIndex()) {
            if (i > 0 && s.fromYear <= steps[i - 1].fromYear) return bad("steps must begin in increasing years")
            if (s.coverage != s.coverage.distinct().sortedBy { it.ordinal }) return bad("coverage must be sorted and without repeats")
            if (s.rate.isNegative || s.cap.isNegative || s.floor.isNegative || s.ceiling.isNegative || s.penalty.isNegative || s.threshold.isNegative) return bad("amounts cannot be negative")
            if (s.capCutPpm !in 0L..Rate.ONE_PPM || s.benchmarkCutPpm !in 0L..Rate.ONE_PPM || s.freeSharePpm !in 0L..Rate.ONE_PPM || s.offsetLimitPpm !in 0L..Rate.ONE_PPM || s.abatementSubsidyPpm !in 0L..Rate.ONE_PPM) return bad("shares must be between 0 and 100%")
            if (s.rateEscalatorPpm < 0L || s.rateEscalatorPpm > Rate.ONE_PPM) return bad("the escalator must be between 0 and 100%")
            if (s.ceiling.isPositive && s.ceiling < s.floor) return bad("the ceiling cannot sit below the floor")
            when (kind) {
                RegimeKind.CAP_AND_TRADE -> if (!s.cap.isPositive) return bad("a cap-and-trade step needs a cap")
                RegimeKind.CARBON_TAX -> if (!s.rate.isPositive) return bad("a carbon tax step needs a rate")
                RegimeKind.INTENSITY_STANDARD -> if (s.benchmarkGramsPerUnit <= 0L || !s.rate.isPositive) return bad("an intensity step needs a benchmark and a levy")
                RegimeKind.NONE -> Unit
            }
        }
        if (vintageLifeYears < 1) return bad("permits must stay valid for at least a year")
        if (surrenderWindowDays !in 1..(CarbonCalendar.DAYS_PER_YEAR - 1)) return bad("the surrender window must fit inside a year")
        if (borrowLimitPpm !in 0L..Rate.ONE_PPM || borrowInterestPpm < 0L || holdingLimitPpm !in 1L..Rate.ONE_PPM * 10) return bad("borrowing and holding limits are out of range")
        if (repeatPenaltyStepPpm < 0L || greenFundSharePpm !in 0L..Rate.ONE_PPM) return bad("penalty step or fund share out of range")
        return Outcome.ok(this)
    }

    companion object {
        /** Yearly rise assumed on top of the cap cut when appraising investments, for the scarcity premium. */
        const val DRIFT_PREMIUM_PPM = 20_000L

        fun none(country: CountryId) = CarbonRegime(country, RegimeKind.NONE)
    }
}

/** Ready-made regimes, used by the world setup, by scenarios and by tests. */
object RegimePresets {
    /** Heavy industry and power: the sectors a first-phase carbon policy catches. */
    val HEAVY: List<IndustryFamily> = listOf(
        IndustryFamily.ENERGY, IndustryFamily.OIL_AND_GAS, IndustryFamily.CHEMICALS, IndustryFamily.METALS, IndustryFamily.CONSTRUCTION,
    )

    /** Everything that makes physical goods. */
    val BROAD: List<IndustryFamily> = (HEAVY + listOf(
        IndustryFamily.MINING, IndustryFamily.AGRICULTURE, IndustryFamily.FOOD, IndustryFamily.TEXTILES, IndustryFamily.AUTOMOTIVE,
        IndustryFamily.AEROSPACE, IndustryFamily.ELECTRONICS, IndustryFamily.SEMICONDUCTORS, IndustryFamily.PHARMACEUTICALS,
        IndustryFamily.CONSUMER_GOODS, IndustryFamily.FURNITURE, IndustryFamily.ROBOTICS,
    )).distinct().sortedBy { it.ordinal }

    fun sorted(families: Collection<IndustryFamily>): List<IndustryFamily> = families.distinct().sortedBy { it.ordinal }

    fun carbonTax(
        country: CountryId, fromYear: Int, rate: Money, escalatorPpm: Long = 60_000L,
        coverage: List<IndustryFamily> = BROAD, threshold: Tonnes = Tonnes.of(500),
    ) = CarbonRegime(
        country, RegimeKind.CARBON_TAX,
        listOf(RegimeStep(fromYear, sorted(coverage), threshold = threshold, rate = rate, rateEscalatorPpm = escalatorPpm, abatementSubsidyPpm = 100_000L)),
        bankingAllowed = false, borrowLimitPpm = 0L,
    )

    fun capAndTrade(
        country: CountryId, fromYear: Int, cap: Tonnes, freeSharePpm: Long = 500_000L, capCutPpm: Long = 20_000L,
        floor: Money = Money.units(5), ceiling: Money = Money.units(120), penalty: Money = Money.units(150),
        coverage: List<IndustryFamily> = HEAVY, threshold: Tonnes = Tonnes.of(500),
    ) = CarbonRegime(
        country, RegimeKind.CAP_AND_TRADE,
        listOf(RegimeStep(fromYear, sorted(coverage), threshold = threshold, cap = cap, capCutPpm = capCutPpm, freeSharePpm = freeSharePpm, floor = floor, ceiling = ceiling, penalty = penalty, abatementSubsidyPpm = 100_000L)),
    )

    /** Three phases: a narrow, generous start; a wider middle; a broad, tight end. */
    fun phasedCapAndTrade(country: CountryId, fromYear: Int, cap: Tonnes): CarbonRegime {
        val first = RegimeStep(fromYear, sorted(HEAVY), threshold = Tonnes.of(1_000), cap = cap, capCutPpm = 20_000L, freeSharePpm = 900_000L,
            floor = Money.units(5), ceiling = Money.units(100), penalty = Money.units(120), offsetLimitPpm = 150_000L, abatementSubsidyPpm = 150_000L)
        val second = first.copy(fromYear = fromYear + 3, coverage = sorted(HEAVY + IndustryFamily.MINING + IndustryFamily.FOOD), cap = cap.scale(1_150_000L, 1_000_000L),
            freeSharePpm = 600_000L, threshold = Tonnes.of(750), penalty = Money.units(150), abatementSubsidyPpm = 100_000L)
        val third = second.copy(fromYear = fromYear + 6, coverage = BROAD, cap = cap.scale(1_300_000L, 1_000_000L), capCutPpm = 40_000L,
            freeSharePpm = 300_000L, threshold = Tonnes.of(500), penalty = Money.units(200), offsetLimitPpm = 80_000L, abatementSubsidyPpm = 50_000L, coversIndirect = true)
        return CarbonRegime(country, RegimeKind.CAP_AND_TRADE, listOf(first, second, third))
    }

    fun intensityStandard(
        country: CountryId, fromYear: Int, benchmarkGramsPerUnit: Long, levy: Money = Money.units(40), cutPpm: Long = 30_000L,
        coverage: List<IndustryFamily> = BROAD, threshold: Tonnes = Tonnes.of(500),
    ) = CarbonRegime(
        country, RegimeKind.INTENSITY_STANDARD,
        listOf(RegimeStep(fromYear, sorted(coverage), threshold = threshold, rate = levy, benchmarkGramsPerUnit = benchmarkGramsPerUnit, benchmarkCutPpm = cutPpm, offsetLimitPpm = 200_000L)),
        bankingAllowed = false, borrowLimitPpm = 0L,
    )

    /**
     * A policy that fits how strict a country's environmental rules are (0..1,000,000): lax countries price nothing,
     * middling ones tax, strict ones run a standard or a market.
     */
    fun forStrictness(country: CountryId, strictnessPpm: Long, fromYear: Int, capScale: Tonnes = Tonnes.of(2_000_000)): CarbonRegime = when {
        strictnessPpm < 250_000L -> CarbonRegime.none(country)
        strictnessPpm < 500_000L -> carbonTax(country, fromYear, Money.units(12))
        strictnessPpm < 750_000L -> intensityStandard(country, fromYear, benchmarkGramsPerUnit = 4_000L)
        else -> phasedCapAndTrade(country, fromYear, capScale)
    }
}
