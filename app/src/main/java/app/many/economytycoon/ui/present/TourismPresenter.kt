package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.tourism.Allotment
import app.many.economytycoon.tourism.AllotmentStatus
import app.many.economytycoon.tourism.Attraction
import app.many.economytycoon.tourism.Destination
import app.many.economytycoon.tourism.Property
import app.many.economytycoon.tourism.PropertyStatus
import app.many.economytycoon.tourism.TourismAdvisor
import app.many.economytycoon.tourism.TourismAnalytics
import app.many.economytycoon.tourism.TourismInsight
import app.many.economytycoon.tourism.TourismState
import app.many.economytycoon.tourism.PropertyFacts
import app.many.economytycoon.tourism.VisitorSegment

data class DestinationRow(val id: Long, val name: String, val occupancy: String, val adr: String, val tone: Tone, val note: String)

data class DestinationListModel(val rows: List<DestinationRow>, val empty: String)

data class TourismInsightLine(val code: String, val title: String, val detail: String, val tone: Tone)

data class AppealRow(val segment: String, val value: String, val parts: List<Pair<String, String>>)

data class MonthBar(val label: String, val indexPpm: Long)

data class DestinationOverviewModel(
    val title: String,
    val tiles: List<Tile>,
    val facts: List<Fact>,
    val appeal: List<AppealRow>,
    val season: List<MonthBar>,
    val notices: List<String>,
)

data class PropertyRow(val id: Long, val name: String, val stars: String, val rooms: Int, val occupancy: String, val adr: String, val revPar: String, val rating: String, val tone: Tone, val status: String)

data class PropertyListModel(val rows: List<PropertyRow>, val tiles: List<Tile>, val empty: String)

data class NightLine(val day: String, val sold: String, val rate: String, val note: String?)

data class PropertyDetailModel(
    val title: String,
    val tiles: List<Tile>,
    val facts: List<Fact>,
    val nights: List<NightLine>,
    val insights: List<TourismInsightLine>,
)

data class AttractionRow(val id: Long, val name: String, val kind: String, val visits: String, val wait: String, val ticket: String, val tone: Tone)

data class AttractionListModel(val rows: List<AttractionRow>, val empty: String)

data class AllotmentRow(val id: Long, val parties: String, val terms: String, val progress: String, val status: String, val tone: Tone)

data class AllotmentListModel(val rows: List<AllotmentRow>, val tiles: List<Tile>, val empty: String)

/** Read-only views of tourism and hospitality. Money is formatted by the caller's function so settings stay in one place. */
object TourismPresenter {
    const val NO_DESTINATIONS = "There are no tourist destinations in this game yet."
    const val NO_PROPERTIES = "There are no hotels here yet."
    const val NO_ATTRACTIONS = "There are no attractions here yet."
    const val NO_ALLOTMENTS = "No tour operator contracts for this hotel."

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun stars(n: Int): String = "$n-star"

    fun rating(tenths: Int): String = "${tenths / 10}.${tenths % 10}"

    fun occupancyTone(ppm: Long): Tone = when {
        ppm >= 920_000L -> Tone.INFO
        ppm >= 650_000L -> Tone.GOOD
        ppm >= 450_000L -> Tone.WARN
        else -> Tone.BAD
    }

    fun ratingTone(tenths: Int): Tone = when {
        tenths >= 40 -> Tone.GOOD
        tenths >= 33 -> Tone.NEUTRAL
        tenths >= 25 -> Tone.WARN
        else -> Tone.BAD
    }

    fun insightTone(priority: Int): Tone = when {
        priority >= 70 -> Tone.BAD
        priority >= 45 -> Tone.WARN
        priority >= 30 -> Tone.INFO
        else -> Tone.NEUTRAL
    }

    fun insight(i: TourismInsight): TourismInsightLine = TourismInsightLine(i.code, i.title, i.detail, insightTone(i.priority))

    private fun statusLabel(p: Property): String = when (p.status) {
        PropertyStatus.UNDER_CONSTRUCTION -> "Building"
        PropertyStatus.OPEN -> "Open"
        PropertyStatus.CLOSED -> "Closed"
    }

    // ----- all destinations -----

    fun destinations(state: TourismState, money: (Money) -> String): DestinationListModel {
        val rows = state.destinations.map { d ->
            val occ = TourismAnalytics.occupancyPpm(d)
            DestinationRow(
                d.id.value, d.name, Formatting.percentPpm(occ), money(TourismAnalytics.adr(d)), occupancyTone(occ),
                "${state.openPropertiesIn(d.id).size} hotels, ${state.attractionsIn(d.id).size} attractions",
            )
        }
        return DestinationListModel(rows, NO_DESTINATIONS)
    }

    // ----- one destination -----

    fun overview(state: TourismState, d: Destination, tick: Long, money: (Money) -> String): DestinationOverviewModel {
        val occ = TourismAnalytics.occupancyPpm(d)
        val denial = TourismAnalytics.denialRatePpm(d)
        val last = d.days.lastOrNull()
        val tiles = listOf(
            Tile("Occupancy", Formatting.percentPpm(occ), occupancyTone(occ), "Last 30 days"),
            Tile("Average rate", money(TourismAnalytics.adr(d)), note = "Per room night"),
            Tile("Arrivals", Formatting.grouped(last?.arrivals ?: 0L), Tone.NEUTRAL, "Yesterday"),
            Tile("Turned away", Formatting.percentPpm(denial), if (denial >= 150_000L) Tone.WARN else Tone.NEUTRAL, "Share of demand"),
            Tile("Reputation", Formatting.percentPpm(d.reputationPpm), Tones.health(d.reputationPpm), "Past guests"),
            Tile("Prices", Formatting.ratioTimes(d.priceLevelPpm), if (d.priceLevelPpm > 1_300_000L) Tone.WARN else Tone.NEUTRAL, "Against the world"),
        )
        val facts = buildList {
            add(Fact("Climate", d.climate.name.lowercase().replace('_', ' ')))
            add(Fact("Hotels", state.propertiesIn(d.id).size.toString()))
            add(Fact("Rooms", Formatting.grouped(state.openPropertiesIn(d.id).sumOf { it.totalRooms }.toLong())))
            add(Fact("Attractions", state.attractionsIn(d.id).size.toString()))
            add(Fact("Infrastructure", Formatting.percentPpm(d.drivers.infrastructurePpm)))
            add(Fact("Safety", Formatting.percentPpm(d.drivers.safetyPpm), Tones.health(d.drivers.safetyPpm)))
            if (d.levyPerGuestNight.raw > 0L) add(Fact("Tourism levy", money(d.levyPerGuestNight) + " a night"))
            if (d.vatPpm > 0L) add(Fact("Room tax", Formatting.percentPpm(d.vatPpm)))
        }
        val appeal = VisitorSegment.values().map { s ->
            val a = TourismAnalytics.appealBreakdown(d, s, tick, state)
            AppealRow(s.label, Formatting.percentPpm(a.valuePpm), a.parts.map { it.label to Formatting.percentPpm(it.valuePpm) })
        }
        val season = TourismAnalytics.seasonality(d).map { MonthBar(MONTHS[(it.month - 1).coerceIn(0, 11)], it.indexPpm) }
        val notices = state.activeShocks(tick).filter { it.touches(d.id) }.map { it.label }
        return DestinationOverviewModel(d.name, tiles, facts, appeal, season, notices)
    }

    // ----- hotels -----

    fun properties(state: TourismState, d: Destination, money: (Money) -> String): PropertyListModel {
        val props = state.propertiesIn(d.id)
        val rows = props.map { p ->
            val k = TourismAnalytics.kpis(p)
            PropertyRow(
                p.id.value, p.name, stars(p.stars), p.totalRooms, Formatting.percentPpm(k.occupancyPpm), money(k.adr), money(k.revPar),
                rating(p.reviews.ratingTenths), occupancyTone(k.occupancyPpm), statusLabel(p),
            )
        }
        val open = props.filter { it.isOpen }
        val total = TourismAnalytics.setKpis(open)
        val tiles = if (open.isEmpty()) emptyList() else listOf(
            Tile("Occupancy", Formatting.percentPpm(total.occupancyPpm), occupancyTone(total.occupancyPpm)),
            Tile("Rate", money(total.adr)),
            Tile("Per room", money(total.revPar), note = "Revenue per available room"),
            Tile("Profit per room", money(total.goppar), Tones.bySign(total.gop.raw)),
        )
        return PropertyListModel(rows, tiles, NO_PROPERTIES)
    }

    fun propertyDetail(state: TourismState, p: Property, tick: Long, money: (Money) -> String, advisor: TourismAdvisor = TourismAdvisor()): PropertyDetailModel {
        val k = TourismAnalytics.kpis(p)
        val peers = state.openPropertiesIn(p.destination)
        val index = TourismAnalytics.marketIndex(p, peers)
        val tiles = listOf(
            Tile("Occupancy", Formatting.percentPpm(k.occupancyPpm), occupancyTone(k.occupancyPpm), "Last 30 days"),
            Tile("Rate", money(k.adr), note = "Average daily"),
            Tile("Per room", money(k.revPar), note = "Revenue per available room"),
            Tile("Rating", rating(p.reviews.ratingTenths), ratingTone(p.reviews.ratingTenths), "${p.reviews.volume} reviews"),
            Tile("Profit", money(k.gop), Tones.bySign(k.gop.raw), "Before rent and tax"),
            Tile("Guests walked", k.walked.toString(), if (k.walked > 0L) Tone.WARN else Tone.NEUTRAL),
        )
        val facts = buildList {
            add(Fact("Class", "${stars(p.stars)} ${p.kind.label.lowercase()}"))
            add(Fact("Rooms", p.totalRooms.toString()))
            add(Fact("Rack rate", money(p.settings.rackRate)))
            add(Fact("Pricing", p.settings.policy.label))
            add(Fact("Cancellation", p.cancellation.label))
            add(Fact("Overbooking", Formatting.percentPpm(p.overbookPpm)))
            add(Fact("Condition", Formatting.percentPpm(p.conditionPpm), Tones.health(p.conditionPpm)))
            add(Fact("Staffing", Formatting.percentPpm(p.serviceLevelPpm), Tones.health(p.serviceLevelPpm)))
            if (index != null) {
                add(Fact("Occupancy against others", Formatting.ratioTimes(index.occupancyPpm)))
                add(Fact("Rate against others", Formatting.ratioTimes(index.adrPpm)))
            }
        }
        val nights = p.days.takeLast(14).reversed().map { n ->
            val note = when {
                n.walked > 0 -> "${n.walked} walked"
                n.roomsSold >= n.roomsAvailable && n.roomsAvailable > 0 -> "Sold out"
                else -> null
            }
            NightLine(Formatting.date(app.many.economytycoon.core.Tick(n.tick)), "${n.roomsSold}/${n.roomsAvailable}", money(n.adr), note)
        }
        val insights = advisor.advise(PropertyFacts.of(state, p, tick)).map { insight(it) }
        return PropertyDetailModel(p.name, tiles, facts, nights, insights)
    }

    // ----- attractions -----

    fun attractions(state: TourismState, d: Destination, money: (Money) -> String): AttractionListModel {
        val rows = state.attractionsIn(d.id).map { a -> attractionRow(a, money) }
        return AttractionListModel(rows, NO_ATTRACTIONS)
    }

    private fun attractionRow(a: Attraction, money: (Money) -> String): AttractionRow {
        val day = a.lastDay
        val wait = day?.waitMinutes ?: 0
        return AttractionRow(
            a.id.value, a.name, a.kind.label, Formatting.grouped(day?.visits ?: 0L) + " yesterday", if (wait <= 0) "No queue" else "$wait min wait", money(a.ticketPrice),
            when {
                !a.open -> Tone.MUTED
                wait >= 60 -> Tone.BAD
                wait >= 25 -> Tone.WARN
                else -> Tone.GOOD
            },
        )
    }

    // ----- tour operator contracts -----

    fun allotments(state: TourismState, p: Property, tick: Long, money: (Money) -> String): AllotmentListModel {
        val all = state.allotmentsOfProperty(p.id)
        val rows = all.map { a ->
            AllotmentRow(
                a.id.value, (state.operator(a.operator)?.name ?: "Operator ${a.operator.value}") + " at " + p.name,
                "${a.roomsPerNight} ${a.room.label.lowercase()} rooms a night at ${money(a.netRate)}, release ${a.releaseDays} days out, ${Formatting.percentPpm(a.committedPpm)} committed",
                progress(a, tick), a.status.label, statusTone(a.status),
            )
        }
        val live = all.filter { it.status == AllotmentStatus.ACTIVE }
        val tiles = listOf(
            Tile("Live contracts", live.size.toString()),
            Tile("Rooms sold", Formatting.grouped(live.sumOf { it.lifetimeSold }), note = "Lifetime"),
            Tile("Shortfall paid", money(Money.sum(live.map { it.lifetimeShortfall })), note = "By the operators"),
        )
        return AllotmentListModel(rows, tiles, NO_ALLOTMENTS)
    }

    private fun progress(a: Allotment, tick: Long): String = when {
        tick < a.startsAt.value -> "Starts in ${Formatting.plural(a.startsAt.value - tick, "day")}"
        tick >= a.endsAt.value -> "Ended"
        else -> "Day ${tick - a.startsAt.value + 1} of ${a.nights}"
    }

    private fun statusTone(s: AllotmentStatus): Tone = when (s) {
        AllotmentStatus.ACTIVE -> Tone.GOOD
        AllotmentStatus.PROPOSED -> Tone.INFO
        AllotmentStatus.ENDED -> Tone.MUTED
        AllotmentStatus.CANCELLED, AllotmentStatus.DECLINED -> Tone.WARN
    }
}
