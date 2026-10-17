package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money

enum class TourismSeverity { INFO, WARNING, CRITICAL }

/** A line for the news feed or the alert list. */
data class TourismNote(val destination: DestinationId?, val severity: TourismSeverity, val headline: String, val detail: String)

/**
 * Turns the events of a day into words. Names come from the state, so the wording is the same for the feed, the alert
 * list and the activity log.
 */
class TourismNews(private val state: TourismState, private val money: (Money) -> String) {
    private fun destName(id: DestinationId?): String = id?.let { state.destination(it)?.name } ?: "the region"
    private fun propName(id: PropertyId): String = state.property(id)?.name ?: "Property ${id.value}"

    fun note(e: TourismEvent): TourismNote? = when (e) {
        is TourismEvent.PropertyOpened -> {
            val p = state.property(e.property)
            TourismNote(p?.destination, TourismSeverity.INFO, "${propName(e.property)} opens", "${p?.totalRooms ?: 0} rooms are now on sale in ${destName(p?.destination)}.")
        }
        is TourismEvent.AttractionOpened -> {
            val a = state.attraction(e.attraction)
            TourismNote(a?.destination, TourismSeverity.INFO, "${a?.name ?: "An attraction"} opens", "Visitors to ${destName(a?.destination)} have something new to see.")
        }
        is TourismEvent.ShockStarted -> {
            val s = state.shock(e.shock)
            val where = destName(e.destination)
            TourismNote(
                e.destination, if (e.kind.isBad) TourismSeverity.WARNING else TourismSeverity.INFO,
                s?.label ?: "${e.kind.label} in $where",
                if (e.kind.isBad) "Expect fewer visitors and more cancellations until it passes." else "Expect more visitors than usual while it lasts.",
            )
        }
        is TourismEvent.ShockEnded -> null
        is TourismEvent.SoldOut -> null
        is TourismEvent.GuestsWalked -> {
            val p = state.property(e.property)
            TourismNote(p?.destination, TourismSeverity.WARNING, "${propName(e.property)} walked ${e.guests} guests", "More people turned up than there were rooms; they were sent elsewhere at the hotel's cost.")
        }
        is TourismEvent.DemandTurnedAway -> TourismNote(
            e.destination, TourismSeverity.INFO, "${destName(e.destination)} is full", "${e.roomNights} room-nights were asked for and could not be sold.",
        )
        is TourismEvent.FestivalBegan -> TourismNote(e.destination, TourismSeverity.INFO, "The festival begins in ${destName(e.destination)}", "Hotels and restaurants expect a surge.")
        is TourismEvent.AllotmentEnded -> TourismNote(
            state.allotment(e.allotment)?.let { state.property(it.property)?.destination }, TourismSeverity.INFO,
            "Allotment ${e.allotment.value} has run out", "It ended without being renewed.",
        )
        is TourismEvent.AllotmentShort -> TourismNote(
            state.allotment(e.allotment)?.let { state.property(it.property)?.destination }, TourismSeverity.INFO,
            "Operator under-sold allotment ${e.allotment.value}", "${e.rooms} committed rooms went unsold; the operator pays for them.",
        )
        is TourismEvent.RatingChanged -> {
            val p = state.property(e.property)
            val up = e.toTenths > e.fromTenths
            TourismNote(
                p?.destination, if (up) TourismSeverity.INFO else TourismSeverity.WARNING, "${propName(e.property)} is now rated ${e.toTenths / 10}.${e.toTenths % 10}",
                if (up) "Guests are happier than before." else "Guests are less happy than before.",
            )
        }
        is TourismEvent.RoutesFull -> TourismNote(
            e.destination, TourismSeverity.INFO, "Transport to ${destName(e.destination)} is full", "${e.turnedAway} travellers could not get a seat from ${state.origin(e.origin)?.name ?: "their home market"}.",
        )
    }

    fun notes(report: DayReport): List<TourismNote> =
        report.events.mapNotNull { note(it) }.sortedWith(compareByDescending<TourismNote> { it.severity.ordinal }.thenBy { it.destination?.value ?: -1L })

    /** One line for a settlement: how many properties traded and what they made. */
    fun summary(run: SettlementRun): TourismNote {
        val profit = Money.sum(run.statements.map { it.profit })
        return TourismNote(null, if (profit.isNegative) TourismSeverity.WARNING else TourismSeverity.INFO, "Tourism month ${run.month} settled", "Properties made ${money(profit)} in total.")
    }
}
