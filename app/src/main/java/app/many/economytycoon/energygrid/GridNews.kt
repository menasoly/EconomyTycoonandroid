package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money

enum class NoteSeverity { INFO, WARNING, CRITICAL }

/** A line for the news feed or the alert list. */
data class GridNote(val grid: GridId, val severity: NoteSeverity, val headline: String, val detail: String)

/**
 * Turns the facts of a day into words. Names of grids and units come from the caller, so the wording is the same for
 * the feed, the alert list and the activity log.
 */
class GridNews(private val state: EnergyGridState, private val money: (Money) -> String) {
    private fun gridName(id: GridId) = state.grid(id)?.name ?: "Grid ${id.value}"
    private fun unitName(grid: GridId, id: UnitId): String {
        val u = state.grid(grid)?.unit(id) ?: return "Unit ${id.value}"
        return "${u.tech.label} plant ${u.name}"
    }

    fun note(e: GridEvent): GridNote? {
        val g = gridName(e.grid)
        return when (e) {
            is GridEvent.UnitFailed -> GridNote(e.grid, NoteSeverity.WARNING, "${unitName(e.grid, e.unit)} has tripped", "$g loses the plant until day ${e.until}.")
            is GridEvent.MaintenanceStarted -> null
            is GridEvent.MaintenancePostponed -> GridNote(e.grid, NoteSeverity.INFO, "Maintenance put off at ${unitName(e.grid, e.unit)}", "$g is too tight to release it; it has been postponed ${e.deferrals} times.")
            is GridEvent.UnitBack -> null
            is GridEvent.UnitCommissioned -> GridNote(e.grid, NoteSeverity.INFO, "${unitName(e.grid, e.unit)} is online", "New capacity joins the grid in $g.")
            is GridEvent.UnitOrdered -> GridNote(e.grid, NoteSeverity.INFO, "A ${e.tech.label.lowercase()} plant is on order in $g", "It is due on day ${e.readyAt}.")
            is GridEvent.UnitRetired -> GridNote(e.grid, NoteSeverity.INFO, "${unitName(e.grid, e.unit)} has closed", "$g has one unit fewer.")
            is GridEvent.BlackoutStarted -> GridNote(e.grid, NoteSeverity.CRITICAL, "Blackout in $g", "Supply fell short from hour ${e.hour}; ${e.shedKw / 1_000L} MW is cut off.")
            is GridEvent.BlackoutEnded -> GridNote(
                e.grid, NoteSeverity.CRITICAL, "Power is back in $g after ${e.hours} hours", "${e.shedKwh / 1_000L} MWh went unserved and the damage came to ${money(e.damage)}.",
            )
            is GridEvent.PriceSpike -> GridNote(e.grid, NoteSeverity.WARNING, "Power prices spike in $g", "The peak reached ${money(e.peak)} per MWh against a day average of ${money(e.average)}.")
            is GridEvent.CapacityAuction ->
                if (e.shortfallKw > 0L) GridNote(e.grid, NoteSeverity.WARNING, "Capacity auction falls short in $g", "${e.shortfallKw / 1_000L} MW of the requirement found no offer; the price cleared at ${money(e.price)}.")
                else GridNote(e.grid, NoteSeverity.INFO, "Capacity auction held in $g", "${e.clearedKw / 1_000L} MW cleared at ${money(e.price)} per MW a day.")
            is GridEvent.PpaShortfall -> GridNote(e.grid, NoteSeverity.WARNING, "Agreement ${e.ppa.value} is under-delivering", "${e.shortfallKwh / 1_000L} MWh short today; penalties apply.")
            is GridEvent.PpaLapsed -> GridNote(e.grid, NoteSeverity.INFO, "Agreement ${e.ppa.value} has run out", "It ended without being renewed.")
        }
    }

    fun notes(report: DayReport): List<GridNote> = report.events.mapNotNull { note(it) }.sortedWith(compareByDescending<GridNote> { it.severity.ordinal }.thenBy { it.grid })
}
