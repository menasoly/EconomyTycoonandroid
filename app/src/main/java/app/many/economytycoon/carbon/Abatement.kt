package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** Tuning of the abatement catalogue. Shares are of the facility's build cost or of its emissions, in ppm. */
data class AbatementParams(
    val retrofitCapexSharePpm: Long = 150_000L,
    val retrofitReductionPpm: Long = 100_000L,
    /** The share of the theoretical fuel and electricity saving that shows up in the books. */
    val retrofitSavingsRealisationPpm: Long = 600_000L,
    val retrofitLifeYears: Int = 15,
    val retrofitBuildDays: Int = 60,
    val switchCapexSharePpm: Long = 90_000L,
    /** Cut in the switched fuel's release when coal is replaced, and when an oil product is. */
    val switchFromCoalPpm: Long = 550_000L,
    val switchFromOilPpm: Long = 350_000L,
    /** The cleaner fuel costs this much more than the fuel it replaces. */
    val switchFuelPremiumPpm: Long = 80_000L,
    val switchLifeYears: Int = 20,
    val switchBuildDays: Int = 90,
    val captureCapexSharePpm: Long = 280_000L,
    val captureRatePpm: Long = 850_000L,
    /** Running cost of capturing, transporting and storing a tonne. */
    val capturePerTonne: Money = Money.units(12),
    val captureLifeYears: Int = 25,
    val captureBuildDays: Int = 240,
    /** A facility releasing less than this a year offers no abatement. */
    val minAnnual: Tonnes = Tonnes.of(500),
    /** A fuel must be at least this share of a facility's fuel emissions to be worth switching. */
    val minSwitchShareOfFuelPpm: Long = 250_000L,
)

/** One way to cut a facility's emissions, with what it costs and what it saves. Money is per the units named. */
data class AbatementOption(
    val facility: FacilityId,
    val kind: AbatementKind,
    val capex: Money,
    /** Running cost per year; negative when the measure saves money. */
    val opexPerYear: Money,
    /** Cut in the facility's direct emissions, ppm of the whole. */
    val directReductionPpm: Long,
    /** Cut applied to the sources the measure acts on; see [AbatementEffect]. */
    val effectReductionPpm: Long,
    val fuelKey: String?,
    /** Direct tonnes kept out of the air per year at today's smoothed rate. */
    val annualAbatement: Tonnes,
    /** Electricity-related tonnes also saved per year; not counted against a regulated obligation. */
    val annualIndirectAbatement: Tonnes,
    val buildDays: Int,
    val lifeYears: Int,
) {
    init { require(lifeYears > 0 && buildDays > 0) { "durations must be positive" } }

    val effect: AbatementEffect get() = AbatementEffect(kind, effectReductionPpm, fuelKey)

    /** Capital spread over the measure's life plus the yearly running cost. */
    val annualisedCost: Money get() = Money(capex.raw / lifeYears) + opexPerYear

    /** Marginal abatement cost: money per tonne kept out of the air. Negative when the measure pays for itself. */
    val costPerTonne: Money
        get() = if (annualAbatement.raw <= 0L) Money(Long.MAX_VALUE / 4) else Money(mulDiv(annualisedCost.raw, Tonnes.SCALE, annualAbatement.raw, RoundingMode.HALF_EVEN))

    /** What a year of the measure is worth when each tonne avoided saves [price]. */
    fun annualBenefit(price: Money): Money = annualAbatement.cost(price) - annualisedCost

    fun worthwhileAt(price: Money): Boolean = annualAbatement.raw > 0L && costPerTonne < price

    /** Whole years for the carbon price alone to repay the capital, or null if it never does at [price]. */
    fun paybackYears(price: Money): Int? {
        val perYear = annualAbatement.cost(price) - opexPerYear
        if (perYear.raw <= 0L) return null
        return ((capex.raw + perYear.raw - 1) / perYear.raw).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}

/**
 * Works out the abatement options of a facility from how much it releases and from what: fuels, process and electricity.
 * The figures are the facility's smoothed daily rates, so an option is always appraised against what the plant really does.
 */
class AbatementCatalog(val params: AbatementParams = AbatementParams()) {

    /** Options open to a facility given its [record], the [buildCost] of its type and the kinds already installed. */
    fun optionsFor(record: FacilityRecord, buildCost: Money): List<AbatementOption> {
        if (record.annualDirect < params.minAnnual) return emptyList()
        val installed = record.effects.map { it.kind }.toSet()
        val out = ArrayList<AbatementOption>()
        retrofit(record, buildCost)?.let { if (AbatementKind.EFFICIENCY_RETROFIT !in installed) out += it }
        fuelSwitch(record, buildCost)?.let { if (AbatementKind.FUEL_SWITCH !in installed) out += it }
        capture(record, buildCost)?.let { if (AbatementKind.CARBON_CAPTURE !in installed) out += it }
        return out
    }

    private fun share(n: Long, ppm: Long) = Money(mulDiv(n, ppm, Rate.ONE_PPM, RoundingMode.HALF_EVEN))

    private fun retrofit(r: FacilityRecord, buildCost: Money): AbatementOption? {
        val p = params
        val directBase = r.annual(SourceKind.FUEL) + r.annual(SourceKind.OVERHEAD)
        val direct = directBase.times(Rate(p.retrofitReductionPpm))
        if (!direct.isPositive) return null
        val indirect = r.annual(SourceKind.ELECTRICITY).times(Rate(p.retrofitReductionPpm))
        val saving = share(share(r.annualEnergySpend.raw, p.retrofitReductionPpm).raw, p.retrofitSavingsRealisationPpm)
        return AbatementOption(
            r.facility, AbatementKind.EFFICIENCY_RETROFIT, share(buildCost.raw, p.retrofitCapexSharePpm), -saving,
            ratioPpm(direct, r.annualDirect), p.retrofitReductionPpm, null, direct, indirect, p.retrofitBuildDays, p.retrofitLifeYears,
        )
    }

    private fun fuelSwitch(r: FacilityRecord, buildCost: Money): AbatementOption? {
        val p = params
        val (fuel, fuelDaily) = r.dominantFuel() ?: return null
        val cut = when (fuel) {
            "coal" -> p.switchFromCoalPpm
            "diesel", "gasoline", "kerosene", "propane" -> p.switchFromOilPpm
            else -> return null
        }
        val fuelTotal = r.annual(SourceKind.FUEL)
        if (!fuelTotal.isPositive) return null
        val fuelAnnual = Tonnes(Math.multiplyExact(fuelDaily, CarbonCalendar.DAYS_PER_YEAR.toLong()))
        val shareOfFuel = ratioPpm(fuelAnnual, fuelTotal)
        if (shareOfFuel < p.minSwitchShareOfFuelPpm) return null
        val abated = fuelAnnual.times(Rate(cut))
        if (!abated.isPositive) return null
        val spendOnFuel = share(r.annualEnergySpend.raw, shareOfFuel)
        return AbatementOption(
            r.facility, AbatementKind.FUEL_SWITCH, share(buildCost.raw, p.switchCapexSharePpm), share(spendOnFuel.raw, p.switchFuelPremiumPpm),
            ratioPpm(abated, r.annualDirect), cut, fuel, abated, Tonnes.ZERO, p.switchBuildDays, p.switchLifeYears,
        )
    }

    private fun capture(r: FacilityRecord, buildCost: Money): AbatementOption? {
        val p = params
        val capturable = r.annual(SourceKind.FUEL) + r.annual(SourceKind.PROCESS) + r.annual(SourceKind.OVERHEAD)
        val abated = capturable.times(Rate(p.captureRatePpm))
        if (!abated.isPositive) return null
        return AbatementOption(
            r.facility, AbatementKind.CARBON_CAPTURE, share(buildCost.raw, p.captureCapexSharePpm), abated.cost(p.capturePerTonne),
            ratioPpm(abated, r.annualDirect), p.captureRatePpm, null, abated, Tonnes.ZERO, p.captureBuildDays, p.captureLifeYears,
        )
    }

    private fun ratioPpm(part: Tonnes, whole: Tonnes): Long =
        if (whole.raw <= 0L) 0L else mulDiv(part.raw, Rate.ONE_PPM, whole.raw, RoundingMode.HALF_EVEN).coerceIn(0L, Rate.ONE_PPM)
}

/** One step of a marginal abatement cost curve. */
data class MacPoint(val option: AbatementOption, val cumulative: Tonnes, val cost: Money)

/**
 * The marginal abatement cost curve of a set of options: cheapest measure first, each with the tonnes it adds. Reading it
 * across at a carbon price shows how much is worth abating, and reading it up at a target shows what the last tonne costs.
 */
class MacCurve(options: List<AbatementOption>) {
    val points: List<MacPoint>

    init {
        val ordered = options.filter { it.annualAbatement.isPositive }.sortedWith(
            compareBy<AbatementOption>({ it.costPerTonne }, { it.kind.ordinal }, { it.facility }),
        )
        var running = Tonnes.ZERO
        points = ordered.map { o -> running += o.annualAbatement; MacPoint(o, running, o.costPerTonne) }
    }

    val totalAbatement: Tonnes get() = points.lastOrNull()?.cumulative ?: Tonnes.ZERO

    /** Options that cost less than [price] a tonne, cheapest first. */
    fun worthwhileAt(price: Money): List<AbatementOption> = points.map { it.option }.filter { it.costPerTonne < price }

    /** Tonnes a year that are cheaper to abate than to pay [price] for. */
    fun abatableAt(price: Money): Tonnes = Tonnes.sum(worthwhileAt(price).map { it.annualAbatement })

    /** Options, cheapest first, until [target] tonnes a year are covered. Empty when the target is zero. */
    fun cheapestFor(target: Tonnes): List<AbatementOption> {
        val out = ArrayList<AbatementOption>()
        var covered = Tonnes.ZERO
        for (pt in points) {
            if (covered >= target) break
            out += pt.option
            covered += pt.option.annualAbatement
        }
        return out
    }

    /** The cost per tonne of the dearest option needed to reach [target], or null if the curve cannot reach it. */
    fun marginalCostFor(target: Tonnes): Money? =
        if (!target.isPositive) null else points.firstOrNull { it.cumulative >= target }?.cost

    /** Yearly cost of reaching [target] with the cheapest measures; savings from negative-cost measures count. */
    fun annualCostFor(target: Tonnes): Money = Money.sum(cheapestFor(target).map { it.annualisedCost })
}

enum class AbatementStatus(val label: String) {
    BUILDING("Under construction"),
    OPERATING("Operating"),
    CANCELLED("Cancelled"),
    RETIRED("Retired"),
}

/** A company's investment in cutting one facility's emissions. */
class AbatementProject(
    val id: AbatementId,
    val company: CompanyId,
    val facility: FacilityId,
    val kind: AbatementKind,
    val capex: Money,
    val opexPerYear: Money,
    val effect: AbatementEffect,
    val annualAbatement: Tonnes,
    val startedAt: Tick,
    val completesAt: Tick,
    val endsAt: Tick,
) {
    var status: AbatementStatus = AbatementStatus.BUILDING
        internal set
    /** Subsidy the government paid towards the project. */
    var subsidy: Money = Money.ZERO
        internal set
    var opexPaid: Money = Money.ZERO
        internal set

    val isLive: Boolean get() = status == AbatementStatus.BUILDING || status == AbatementStatus.OPERATING

    internal fun restore(status: AbatementStatus, subsidy: Money, opexPaid: Money) {
        this.status = status; this.subsidy = subsidy; this.opexPaid = opexPaid
    }
    internal fun addSubsidy(m: Money) { subsidy += m }
    internal fun addOpex(m: Money) { opexPaid += m }
}

/** The register of abatement projects. */
class AbatementBook {
    private val projectMap = java.util.TreeMap<AbatementId, AbatementProject>()

    fun projects(): List<AbatementProject> = projectMap.values.toList()
    fun project(id: AbatementId): AbatementProject? = projectMap[id]
    fun projectsOf(company: CompanyId): List<AbatementProject> = projectMap.values.filter { it.company == company }
    fun projectsAt(facility: FacilityId): List<AbatementProject> = projectMap.values.filter { it.facility == facility }
    fun liveAt(facility: FacilityId): List<AbatementProject> = projectsAt(facility).filter { it.isLive }

    internal fun put(p: AbatementProject) { projectMap[p.id] = p }
    internal fun clear() { projectMap.clear() }
}
