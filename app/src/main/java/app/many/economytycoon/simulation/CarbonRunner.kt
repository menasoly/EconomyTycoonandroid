package app.many.economytycoon.simulation

import app.many.economytycoon.carbon.BorderPolicy
import app.many.economytycoon.carbon.CarbonEvent
import app.many.economytycoon.carbon.CarbonRegime
import app.many.economytycoon.carbon.CarbonSettlement
import app.many.economytycoon.carbon.EmissionCalculator
import app.many.economytycoon.carbon.FacilityActivity
import app.many.economytycoon.carbon.ProductFootprints
import app.many.economytycoon.carbon.ProductionActivities
import app.many.economytycoon.carbon.RegimePresets
import app.many.economytycoon.carbon.RegimeKind
import app.many.economytycoon.carbon.StandardFactors
import app.many.economytycoon.carbon.Tonnes
import app.many.economytycoon.carbon.TradeFlow
import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.energygrid.Grid
import app.many.economytycoon.energygrid.Technology
import app.many.economytycoon.production.ProductionReport

/**
 * Connects the carbon package to the economy. Each day it turns what the facilities burned and made (and what the
 * power plants on the electricity grids generated) into emissions, runs the package, and holds the payments it decided
 * until the settlement phase. Company commands are carried out at once and paid at once, like the other runners.
 *
 * Nothing here does anything while no government has a carbon policy: the state stays empty, nothing is saved or
 * hashed and no random number is drawn, so a game without carbon plays exactly as it did before.
 */
class CarbonRunner(private val e: Economy) {
    private val state get() = e.carbonState
    private val service get() = e.carbon

    private val countryOfRegion: Map<RegionId, CountryId> by lazy { e.world.regions().associate { it.id to it.country } }
    private val activities by lazy { ProductionActivities(e.content, StandardFactors.table, { countryOfRegion[it] }) }
    private val footprints by lazy { ProductFootprints(e.content, EmissionCalculator(StandardFactors.table, e.content)) }

    private var pending: CarbonSettlement = CarbonSettlement.EMPTY

    /** True once a policy exists or a day has been run. */
    val active: Boolean get() = !state.isPristine

    // ---- the day -------------------------------------------------------------------------------

    fun run(tick: Tick, report: ProductionReport): List<CarbonEvent> {
        if (!active) return emptyList()
        if (tick.value % Calendar.DAYS_PER_MONTH == 0L) refreshGridIntensity()
        var list = activities.activities(report.facilities) { e.production.facility(it) }
        val plants = e.energyGridRunner.plantFuelToday()
        if (plants.isNotEmpty()) list = list.map { a -> plants[a.facility]?.let { (good, burned) -> withPlantBurn(a, good, burned) } ?: a }
        val result = service.advance(tick, list)
        pending += result.settlement
        return result.events
    }

    /**
     * A power plant that stands on an electricity grid has its own lines switched off, so the production report shows no
     * fuel. The fuel it burned (worked out from the energy it generated) is added to what the report shows, which makes a
     * plant on a grid release exactly what the same plant would release running its own burn recipe.
     */
    private fun withPlantBurn(a: FacilityActivity, good: GoodId, burned: Quantity): FacilityActivity {
        val consumed = java.util.TreeMap(a.consumed)
        consumed[good] = (consumed[good] ?: Quantity.ZERO) + burned
        return a.copy(consumed = consumed)
    }

    /**
     * The national grid intensity of every country with electricity grids, from what its grids generated over the last month.
     * The grams are those of the fuel the plants burned, as the facility accounts count them. One unit of electricity is a
     * megawatt-hour, so grams per kilowatt-hour become grams per unit.
     */
    private fun refreshGridIntensity() {
        if (!e.energyGridRunner.active) return
        for ((country, grids) in e.energyGrids.grids.groupBy { it.country }.toSortedMap()) {
            val generation = LongArray(Technology.values().size)
            for (g in grids) for (d in g.history.takeLast(Calendar.DAYS_PER_MONTH)) for (i in generation.indices) generation[i] += d.generationKwh[i]
            val total = generation.sum()
            if (total <= 0L) continue
            var grams = 0L
            for (tech in Technology.values()) {
                val kwh = generation[tech.ordinal]
                if (kwh <= 0L) continue
                val (good, burned) = EnergyGridRunner.fuelBurned(tech, kwh, tech.profile.efficiencyPpm) ?: continue
                grams += mulDiv(burned.raw, StandardFactors.table.fuels[good]?.gramsPerUnit ?: 0L, Quantity.SCALE)
            }
            service.noteGridIntensity(country, mulDiv(grams, KWH_PER_UNIT, total))
        }
    }

    /**
     * What a tonne is worth to the grid's merit order, relative to the grid module's own figures: the module counts the
     * weight of carbon dioxide a real plant releases per megawatt-hour, while the economy's fuel factors give a lighter
     * figure for the same plant. Scaling the price by the ratio for coal makes the dispatch weigh a plant's carbon at what
     * its owner is actually charged for it.
     */
    private val dispatchScalePpm: Long by lazy {
        val tech = Technology.COAL
        val kwh = 1_000_000L
        val (good, burned) = EnergyGridRunner.fuelBurned(tech, kwh, tech.profile.efficiencyPpm) ?: return@lazy Rate.ONE_PPM
        val accounted = mulDiv(burned.raw, StandardFactors.table.fuels[good]?.gramsPerUnit ?: 0L, Quantity.SCALE)
        val nominal = mulDiv(kwh, tech.profile.carbonKgPerMwh, 1L)
        if (nominal <= 0L) Rate.ONE_PPM else mulDiv(accounted, Rate.ONE_PPM, nominal).coerceIn(0L, Rate.ONE_PPM)
    }

    /** The price of a tonne in each grid's country, for the grids' dispatch; empty where carbon is not priced. */
    fun gridCarbonPrices(grids: List<Grid>, tick: Tick): Map<app.many.economytycoon.energygrid.GridId, Money> {
        if (!active || state.regimes().isEmpty()) return emptyMap()
        val year = app.many.economytycoon.carbon.CarbonCalendar.yearOf(tick)
        val out = java.util.TreeMap<app.many.economytycoon.energygrid.GridId, Money>()
        for (g in grids) {
            val price = Money(mulDiv(service.carbonPrice(g.country, year).raw, dispatchScalePpm, Rate.ONE_PPM))
            if (price.isPositive) out[g.id] = price
        }
        return out
    }

    // ---- trade ---------------------------------------------------------------------------------

    /** Charges the border adjustment on the day's cross-border trade; the money is paid with the day's other settlements. */
    fun assessTrade(tick: Tick, flows: List<TradeFlow>) {
        if (!active || flows.isEmpty() || state.border.policies().isEmpty()) return
        pending += service.assessTrade(tick, flows, footprints).settlement
    }

    // ---- commands ------------------------------------------------------------------------------

    fun execute(cmd: Command, tick: Tick): Outcome<CommandResult> {
        val carbon = CarbonCommandMap.of(cmd) ?: return Outcome.reject(Rules.INVALID_ARGUMENT, "that is not a carbon command")
        if (!active) return Outcome.reject(Rules.WRONG_STATE, "no government has a carbon policy yet")
        return service.execute(carbon, tick).map { effect ->
            pay(effect.settlement, tick)
            CommandResult(message = effect.message)
        }
    }

    /** Pays a command's money at once, so the next command sees the cash that is left. */
    private fun pay(settlement: CarbonSettlement, tick: Tick) {
        if (settlement.payments.isNotEmpty()) e.finance.settlement.settle(tick, settlement.payments)
        for (draft in settlement.adjustments) e.finance.ledger.post(draft)
    }

    // ---- settlement ----------------------------------------------------------------------------

    /** Hands the day's payments to the settlement phase and starts over. */
    fun drain(): CarbonSettlement {
        val out = pending
        pending = CarbonSettlement.EMPTY
        return out
    }

    // ---- policies at the start of a game ---------------------------------------------------------

    /**
     * Gives every country the carbon policy that fits its environmental rules: none, a tax, an intensity standard or a
     * market, all beginning a couple of years in so a new company has time to settle. The strictest countries also
     * charge the border adjustment. Everything follows from the world, so the same world gets the same policies.
     */
    fun establish(tick: Tick, delayYears: Int = START_DELAY_YEARS): Outcome<CommandResult> {
        if (active) return Outcome.reject(Rules.WRONG_STATE, "the carbon policies are already in force")
        val from = app.many.economytycoon.carbon.CarbonCalendar.yearOf(tick) + delayYears.coerceIn(0, MAX_DELAY_YEARS)
        var made = 0
        for (c in e.world.countries().sortedBy { it.id }) {
            val cap = Tonnes.of((e.world.populationOf(c.id) / PEOPLE_PER_CAP_TONNE).coerceAtLeast(MIN_CAP_TONNES))
            val regime: CarbonRegime = RegimePresets.forStrictness(c.id, c.environmentalStrictnessPpm, from, cap)
            if (regime.kind == RegimeKind.NONE) continue
            val installed = service.installRegime(regime, tick)
            if (installed is Outcome.Rejected) return Outcome.Rejected(installed.rejection)
            if (regime.kind == RegimeKind.CAP_AND_TRADE) service.setBorderPolicy(BorderPolicy(c.id, startYear = from + 1))
            made++
        }
        return Outcome.ok(CommandResult(made.toLong(), "$made carbon policies in force"))
    }

    private companion object {
        const val KWH_PER_UNIT = 1_000L
        const val START_DELAY_YEARS = 2
        const val MAX_DELAY_YEARS = 20
        const val PEOPLE_PER_CAP_TONNE = 20L
        const val MIN_CAP_TONNES = 20_000L
    }
}
