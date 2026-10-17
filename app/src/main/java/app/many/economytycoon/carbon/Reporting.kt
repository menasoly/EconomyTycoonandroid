package app.many.economytycoon.carbon

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv

/** The yearly emissions statement a company files for one country. */
data class AnnualStatement(
    val company: CompanyId,
    val country: CountryId,
    val year: Int,
    val direct: Tonnes,
    val electricity: Tonnes,
    val biogenic: Tonnes,
    val covered: Tonnes,
    val avoided: Tonnes,
    val outputValue: Money,
    /** Grams released per unit of output value; zero when nothing was made. */
    val intensityGrams: Long,
    val dominantFamily: IndustryFamily?,
    val regime: RegimeKind,
    val status: ComplianceStatus,
    val obligation: Tonnes,
    val surrendered: Tonnes,
    val offsetsUsed: Tonnes,
    val shortfall: Tonnes,
    /** Penalty, tax or levy paid for the year. */
    val charges: Money,
    val freePermits: Tonnes,
    /** Change in footprint on the year before, in ppm, or null for a first statement. */
    val changePpm: Long?,
) {
    val footprint: Tonnes get() = direct + electricity
}

/** How a company's emissions per unit of output compare with others in its industry. */
data class IntensityBenchmark(val family: IndustryFamily, val medianGrams: Long, val bestGrams: Long, val peers: Int) {
    /** Where a company's intensity sits against the median, in ppm (1,000,000 is level). */
    fun ratioPpm(grams: Long): Long = if (medianGrams <= 0L) Rate.ONE_PPM else mulDiv(grams, Rate.ONE_PPM, medianGrams, RoundingMode.HALF_EVEN)
}

object Benchmarks {
    /** Benchmarks for every industry family with at least [minPeers] reporting companies in a country-year. */
    fun compute(years: List<CompanyYear>, minPeers: Int = 2): List<IntensityBenchmark> {
        val byFamily = java.util.TreeMap<IndustryFamily, MutableList<Long>>()
        for (y in years) {
            val family = y.dominantFamily() ?: continue
            if (y.outputValue.raw <= 0L) continue
            byFamily.getOrPut(family) { ArrayList() } += y.intensityGramsPerUnit
        }
        return byFamily.filter { it.value.size >= minPeers }.map { (family, list) ->
            val sorted = list.sorted()
            val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
            IntensityBenchmark(family, median, sorted.first(), sorted.size)
        }
    }

    fun forFamily(list: List<IntensityBenchmark>, family: IndustryFamily?): IntensityBenchmark? = list.firstOrNull { it.family == family }
}

/** Letter grades from the carbon rating, best first. [spreadBp] is the change to a company's borrowing spread, in basis points. */
enum class CarbonGrade(val label: String, val minScore: Int, val spreadBp: Int) {
    AAA("AAA", 900, -40),
    AA("AA", 800, -25),
    A("A", 700, -10),
    BBB("BBB", 600, 0),
    BB("BB", 500, 30),
    B("B", 400, 80),
    CCC("CCC", 250, 160),
    D("D", 0, 300);

    companion object {
        fun of(score: Int): CarbonGrade = values().first { score >= it.minScore }
    }
}

/** One scored part of a rating. */
data class RatingFactor(val label: String, val points: Int, val maximum: Int, val note: String)

data class CarbonRating(val company: CompanyId, val year: Int, val score: Int, val grade: CarbonGrade, val factors: List<RatingFactor>) {
    /** Change to a loan's spread, in ppm of the principal per year, for the finance module to apply. */
    val spreadAdjustmentPpm: Long get() = grade.spreadBp.toLong() * 100L
}

/** Scores a company's carbon performance from its statement, its peers and its record. */
object RatingModel {
    const val INTENSITY_MAX = 400
    const val TREND_MAX = 150
    const val COMPLIANCE_MAX = 250
    const val OFFSET_MAX = 100
    const val DISCLOSURE_MAX = 100

    fun rate(statement: AnnualStatement, benchmark: IntensityBenchmark?, violations: Int, priorStatements: Int): CarbonRating {
        val factors = ArrayList<RatingFactor>()
        factors += intensity(statement, benchmark)
        factors += trend(statement)
        factors += compliance(violations)
        factors += offsets(statement)
        factors += disclosure(priorStatements)
        val score = factors.sumOf { it.points }.coerceIn(0, 1000)
        return CarbonRating(statement.company, statement.year, score, CarbonGrade.of(score), factors)
    }

    /** Linear score between [best] (full marks) and [worst] (none) for a value in ppm. */
    private fun linear(value: Long, best: Long, worst: Long, max: Int): Int {
        if (value <= best) return max
        if (value >= worst) return 0
        return (max.toLong() - mulDiv(value - best, max.toLong(), worst - best, RoundingMode.HALF_EVEN)).toInt()
    }

    private fun intensity(s: AnnualStatement, b: IntensityBenchmark?): RatingFactor {
        if (s.outputValue.raw <= 0L || b == null) return RatingFactor("Intensity", INTENSITY_MAX / 2, INTENSITY_MAX, "No peers to compare with")
        val ratio = b.ratioPpm(s.intensityGrams)
        val points = linear(ratio, 500_000L, 2_000_000L, INTENSITY_MAX)
        return RatingFactor("Intensity", points, INTENSITY_MAX, "${ratio / 10_000}% of the industry median")
    }

    private fun trend(s: AnnualStatement): RatingFactor {
        val change = s.changePpm ?: return RatingFactor("Trend", TREND_MAX / 2, TREND_MAX, "First statement")
        val points = linear(change, -100_000L, 100_000L, TREND_MAX)
        return RatingFactor("Trend", points, TREND_MAX, "${change / 10_000}% on last year")
    }

    private fun compliance(violations: Int): RatingFactor {
        val lost = violations.coerceAtLeast(0) * 125
        val points = (COMPLIANCE_MAX - lost).coerceAtLeast(0)
        val note = if (violations == 0) "No shortfalls" else "$violations year${if (violations == 1) "" else "s"} in a row with a shortfall"
        return RatingFactor("Compliance", points, COMPLIANCE_MAX, note)
    }

    private fun offsets(s: AnnualStatement): RatingFactor {
        val footprint = s.footprint
        if (!footprint.isPositive || !s.offsetsUsed.isPositive) return RatingFactor("Offsets", OFFSET_MAX, OFFSET_MAX, "No reliance on offsets")
        val share = mulDiv(s.offsetsUsed.raw, Rate.ONE_PPM, footprint.raw, RoundingMode.HALF_EVEN)
        return RatingFactor("Offsets", linear(share, 0L, 500_000L, OFFSET_MAX), OFFSET_MAX, "${share / 10_000}% of the footprint is offset")
    }

    private fun disclosure(prior: Int): RatingFactor {
        val points = if (prior >= 1) DISCLOSURE_MAX else DISCLOSURE_MAX / 2
        return RatingFactor("Disclosure", points, DISCLOSURE_MAX, if (prior >= 1) "Reports every year" else "First year of reporting")
    }
}

/** A country's emissions for a year, for the national report. */
data class NationalSummary(
    val country: CountryId,
    val year: Int,
    val direct: Tonnes,
    val electricity: Tonnes,
    val biogenic: Tonnes,
    val avoided: Tonnes,
    val covered: Tonnes,
    val power: Tonnes,
    /** Share of direct emissions that fall under the country's carbon policy, in ppm. */
    val coveragePpm: Long,
    /** Change on the previous year's direct total, in ppm, or null if there is none. */
    val changePpm: Long?,
    /** Change since the earliest year held, in ppm, or null if only one year is held. */
    val sinceBaselinePpm: Long?,
    val topFamilies: List<Pair<IndustryFamily, Tonnes>>,
)

object NationalReports {
    fun summarise(book: InventoryBook, country: CountryId, year: Int, topN: Int = 5): NationalSummary? {
        val now = book.national(country, year) ?: return null
        val history = book.nationalHistory(country)
        val previous = history.lastOrNull { it.year < year }
        val first = history.firstOrNull()
        fun change(a: Tonnes, b: Tonnes?): Long? = if (b == null || b.raw <= 0L) null else mulDiv(a.raw - b.raw, Rate.ONE_PPM, b.raw, RoundingMode.HALF_EVEN)
        val coverage = if (now.direct.raw <= 0L) 0L else mulDiv(now.covered.raw, Rate.ONE_PPM, now.direct.raw, RoundingMode.HALF_EVEN)
        val top = now.families().entries.sortedWith(compareByDescending<Map.Entry<IndustryFamily, Tonnes>> { it.value }.thenBy { it.key }).take(topN).map { it.key to it.value }
        return NationalSummary(
            country, year, now.direct, now.indirect, now.biogenic, now.avoided, now.covered, now.power, coverage,
            change(now.direct, previous?.direct), if (first == null || first.year == year) null else change(now.direct, first.direct), top,
        )
    }
}

/** Statements and ratings on file. */
class ReportBook {
    private val statementMap = java.util.TreeMap<CompanyYearKey, AnnualStatement>()
    private val ratingMap = java.util.TreeMap<CompanyId, CarbonRating>()

    fun statements(): List<AnnualStatement> = statementMap.values.toList()
    fun statementsOf(company: CompanyId): List<AnnualStatement> = statementMap.values.filter { it.company == company }.sortedWith(compareBy({ it.year }, { it.country }))
    fun statement(company: CompanyId, country: CountryId, year: Int): AnnualStatement? = statementMap[CompanyYearKey(company, country, year)]
    fun rating(company: CompanyId): CarbonRating? = ratingMap[company]
    fun ratings(): List<CarbonRating> = ratingMap.values.toList()

    internal fun file(s: AnnualStatement) { statementMap[CompanyYearKey(s.company, s.country, s.year)] = s }
    internal fun rate(r: CarbonRating) { ratingMap[r.company] = r }
    internal fun prune(currentYear: Int, keepYears: Int) { statementMap.keys.removeAll { it.year < currentYear - keepYears } }
    internal fun clear() { statementMap.clear(); ratingMap.clear() }
}
