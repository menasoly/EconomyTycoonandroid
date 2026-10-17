package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.world.WeatherEvent

/** Facts about a day on the grids that news and alerts can pick up. */
sealed class GridEvent {
    abstract val grid: GridId

    data class UnitFailed(override val grid: GridId, val unit: UnitId, val until: Long) : GridEvent()
    data class MaintenanceStarted(override val grid: GridId, val unit: UnitId, val until: Long) : GridEvent()
    data class MaintenancePostponed(override val grid: GridId, val unit: UnitId, val deferrals: Int) : GridEvent()
    data class UnitBack(override val grid: GridId, val unit: UnitId) : GridEvent()
    data class UnitCommissioned(override val grid: GridId, val unit: UnitId) : GridEvent()
    data class UnitOrdered(override val grid: GridId, val unit: UnitId, val tech: Technology, val readyAt: Long) : GridEvent()
    data class UnitRetired(override val grid: GridId, val unit: UnitId) : GridEvent()
    data class BlackoutStarted(override val grid: GridId, val blackout: BlackoutId, val hour: Int, val shedKw: Long) : GridEvent()
    data class BlackoutEnded(override val grid: GridId, val blackout: BlackoutId, val hours: Int, val shedKwh: Long, val damage: Money) : GridEvent()
    data class PriceSpike(override val grid: GridId, val peak: Money, val average: Money) : GridEvent()
    data class CapacityAuction(override val grid: GridId, val clearedKw: Long, val shortfallKw: Long, val price: Money) : GridEvent()
    data class PpaShortfall(override val grid: GridId, val ppa: PpaId, val shortfallKwh: Long) : GridEvent()
    data class PpaLapsed(override val grid: GridId, val ppa: PpaId) : GridEvent()
}

/** Everything a simulated day produced, for the wiring to turn into news, alerts, damage and ledger entries. */
class DayReport(
    val tick: Tick,
    val summaries: Map<GridId, DaySummary>,
    val events: List<GridEvent>,
    val impacts: List<FacilityImpact>,
    /** Energy sent over each link today, before losses, in both directions added. */
    val linkFlowKwh: Map<LinkId, Long>,
    /** Payments for units the grid operators ordered today; the wiring hands them to the settlement engine. */
    val capexIntents: List<SettlementIntent> = emptyList(),
) {
    fun eventsOf(grid: GridId): List<GridEvent> = events.filter { it.grid == grid }
    inline fun <reified T : GridEvent> eventsOfType(): List<T> = events.filterIsInstance<T>()
}

/** What the world gives the grids each day. Fuel prices come from the markets, weather from the world. */
data class DayInputs(
    val fuel: FuelPrices = FuelPrices(),
    val weather: Map<GridId, List<WeatherEvent>> = emptyMap(),
    /** The price of a tonne of carbon dioxide in force where each grid lies; a grid not listed has none. */
    val carbonPrice: Map<GridId, Money> = emptyMap(),
) {
    /** The fuel prices a grid dispatches on today: the shared fuel prices with the carbon price of its country. */
    fun fuelFor(grid: GridId): FuelPrices = carbonPrice[grid]?.let { fuel.copy(carbonPerTonne = it) } ?: fuel
}
