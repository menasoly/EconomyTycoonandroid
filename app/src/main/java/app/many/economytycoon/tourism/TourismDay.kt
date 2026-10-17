package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.world.WeatherEvent

/** Everything the caller supplies for a day. The world side fills these in; tourism reads them and nothing else. */
class DayInputs(
    /** Active weather events in the region of each destination. */
    val weather: Map<DestinationId, List<WeatherEvent>> = emptyMap(),
    val transport: TransportCapacity = UnlimitedTransport,
    val staff: StaffSupply = FullStaffSupply,
    /** Daily pay of one member of staff; the default applies to destinations not listed. */
    val wagePerDay: Map<DestinationId, Money> = emptyMap(),
    val defaultWage: Money = Money.units(70),
    /** Residents of the destination's city, for the local spending comparison. */
    val residents: Map<DestinationId, Long> = emptyMap(),
    val residentSpendPerDay: Money = Money.units(25),
    /** Share of tourist spending that is spent again locally and share that leaks out of the area. */
    val respendPpm: Long = 450_000L,
    val leakagePpm: Long = 350_000L,
) {
    fun wage(d: DestinationId): Money = wagePerDay[d] ?: defaultWage
}

/** Things that happened, in the order they happened. They are returned, never published. */
sealed class TourismEvent {
    abstract val tick: Long

    data class PropertyOpened(val property: PropertyId, override val tick: Long) : TourismEvent()
    data class AttractionOpened(val attraction: AttractionId, override val tick: Long) : TourismEvent()
    data class ShockStarted(val shock: ShockId, val kind: ShockKind, val destination: DestinationId?, override val tick: Long) : TourismEvent()
    data class ShockEnded(val shock: ShockId, val kind: ShockKind, override val tick: Long) : TourismEvent()
    data class SoldOut(val property: PropertyId, override val tick: Long) : TourismEvent()
    data class GuestsWalked(val property: PropertyId, val guests: Int, override val tick: Long) : TourismEvent()
    data class DemandTurnedAway(val destination: DestinationId, val roomNights: Long, override val tick: Long) : TourismEvent()
    data class FestivalBegan(val destination: DestinationId, override val tick: Long) : TourismEvent()
    data class AllotmentEnded(val allotment: AllotmentId, override val tick: Long) : TourismEvent()
    data class AllotmentShort(val allotment: AllotmentId, val rooms: Int, override val tick: Long) : TourismEvent()
    data class RatingChanged(val property: PropertyId, val fromTenths: Int, val toTenths: Int, override val tick: Long) : TourismEvent()
    data class RoutesFull(val origin: OriginId, val destination: DestinationId, val turnedAway: Long, override val tick: Long) : TourismEvent()
}

/** What local business gets from the visitors of one destination on one day. */
class LocalSpendingReport(
    val destination: DestinationId,
    val guests: Long,
    val spend: SpendBreakdown,
    val uplifts: List<CategoryUplift>,
    val multiplierPpm: Long,
    val impact: Money,
) {
    fun upliftOf(c: SpendCategory): Long = uplifts.first { it.category == c }.upliftPpm
}

/** One destination's day in the report. */
class DestinationReport(
    val destination: DestinationId,
    val day: DestinationDay,
    val arrivalsBySegment: LongArray,
    val arrivalsByOrigin: Map<OriginId, Long>,
    val shocksActive: Int,
    val experiencePpm: Long,
)

/** The result of one day. */
class DayReport(
    val tick: Tick,
    val destinations: List<DestinationReport>,
    val events: List<TourismEvent>,
    val routes: List<RouteLoad>,
    val spending: List<LocalSpendingReport>,
) {
    fun destination(id: DestinationId): DestinationReport? = destinations.firstOrNull { it.destination == id }
    fun spendingOf(id: DestinationId): LocalSpendingReport? = spending.firstOrNull { it.destination == id }
    inline fun <reified T : TourismEvent> eventsOf(): List<T> = events.filterIsInstance<T>()
    val totalArrivals: Long get() = destinations.sumOf { it.day.arrivals }
}

/** What a month's settlement came to. */
class SettlementRun(val month: Long, val intents: List<SettlementIntent>, val statements: List<PropertyStatement>, val attractionStatements: List<AttractionStatement>) {
    val total: Money get() = Money.sum(intents.map { it.total })
    fun statementOf(p: PropertyId): PropertyStatement? = statements.firstOrNull { it.property == p }
}

/** The month of one property in money. */
data class PropertyStatement(
    val property: PropertyId,
    val guestRoomRevenue: Money,
    val operatorRoomRevenue: Money,
    val ancillaryRevenue: Money,
    val commissions: Money,
    val walkCost: Money,
    val operatingCost: Money,
    val wageCost: Money,
    val levy: Money,
    val vat: Money,
    val listingFees: Money,
    val roomsSold: Long,
    val roomsAvailable: Long,
) {
    val revenue: Money get() = guestRoomRevenue + operatorRoomRevenue + ancillaryRevenue
    val profit: Money get() = revenue - commissions - walkCost - operatingCost - wageCost - vat - listingFees
    val occupancyPpm: Long get() = if (roomsAvailable <= 0L) 0L else roomsSold * 1_000_000L / roomsAvailable
    val adr: Money get() = if (roomsSold <= 0L) Money.ZERO else Money((guestRoomRevenue + operatorRoomRevenue).raw / roomsSold)
    val revPar: Money get() = if (roomsAvailable <= 0L) Money.ZERO else Money((guestRoomRevenue + operatorRoomRevenue).raw / roomsAvailable)
}

/** The month of one attraction in money. */
data class AttractionStatement(val attraction: AttractionId, val revenue: Money, val cost: Money, val vat: Money, val visits: Long) {
    val profit: Money get() = revenue - cost - vat
}
