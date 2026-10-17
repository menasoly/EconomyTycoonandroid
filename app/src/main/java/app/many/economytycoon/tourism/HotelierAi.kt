package app.many.economytycoon.tourism

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.SettlementIntent

/** How boldly a computer-controlled hotelier prices and invests. Ratios are ppm. */
data class HotelierStance(
    /** The step by which the rack rate moves each time it is reviewed. */
    val priceStepPpm: Long,
    /** The most of its cash it will commit to investment in one round. */
    val cashShareToSpendPpm: Long,
    /** Upper limit on the overbooking allowance it is willing to run. */
    val maxOverbookPpm: Long,
    val usesDynamicPricing: Boolean,
    /** Longest payback it accepts for new rooms, in years. */
    val maxPaybackYears: Int,
    val maxProperties: Int,
) {
    init { require(maxPaybackYears > 0 && maxProperties > 0) { "payback and portfolio limits must be positive" } }

    companion object {
        val CAUTIOUS = HotelierStance(30_000L, 250_000L, 0L, false, 6, 3)
        val BALANCED = HotelierStance(50_000L, 400_000L, 80_000L, true, 9, 6)
        val AGGRESSIVE = HotelierStance(80_000L, 600_000L, 150_000L, true, 14, 12)
    }
}

/** What the hotelier sees of one of its properties. */
data class PropertyView(
    val property: PropertyId,
    val destination: DestinationId,
    val kind: PropertyKind,
    val stars: Int,
    val rooms: Int,
    val policy: RatePolicy,
    val rack: Money,
    /** The rack rate a property of this kind and stars normally carries here. */
    val reference: Money,
    val occupancy14Ppm: Long,
    val occupancy30Ppm: Long,
    /** Rate against the other properties in town; 0 when unknown. */
    val adrIndexPpm: Long,
    val ratingTenths: Int,
    val conditionPpm: Long,
    val overbookPpm: Long,
    /** Share of bookings that show up, from the recent nights. */
    val showPpm: Long,
    val walked14: Int,
    val listedPlatforms: Int,
    val unmetPpm: Long,
    val upgradeCost: Money?,
    val renovationCost: Money,
    val expansionCostPerRoom: Money,
)

/** A town where the hotelier could build. */
data class Opening(
    val destination: DestinationId,
    val kind: PropertyKind,
    val stars: Int,
    val rooms: Int,
    val cost: Money,
    val occupancyPpm: Long,
    val unmetPpm: Long,
    val expectedRate: Money,
)

data class HotelierView(
    val company: CompanyId,
    val tick: Long,
    val cash: Money,
    val stance: HotelierStance,
    val properties: List<PropertyView>,
    val openings: List<Opening>,
    val onlinePlatforms: List<IntermediaryId>,
)

sealed class HotelierMove(val cost: Money) {
    abstract val property: PropertyId?
    data class SetRack(override val property: PropertyId, val rack: Money) : HotelierMove(Money.ZERO)
    data class UseDynamic(override val property: PropertyId, val targetOccupancyPpm: Long) : HotelierMove(Money.ZERO)
    data class SetOverbook(override val property: PropertyId, val ppm: Long) : HotelierMove(Money.ZERO)
    data class ListOnPlatform(override val property: PropertyId, val platform: IntermediaryId) : HotelierMove(Money.ZERO)
    data class Renovate(override val property: PropertyId, val estimate: Money) : HotelierMove(estimate)
    data class Upgrade(override val property: PropertyId, val stars: Int, val estimate: Money) : HotelierMove(estimate)
    data class Expand(override val property: PropertyId, val rooms: Int, val estimate: Money) : HotelierMove(estimate)
    data class Build(val opening: Opening) : HotelierMove(opening.cost) { override val property: PropertyId? = null }
}

/** What happened when a move was carried out. */
data class MoveResult(val move: HotelierMove, val outcome: Outcome<Unit>, val intents: List<SettlementIntent>)

/** Heuristic play for rival hoteliers: price by occupancy, overbook sensibly, maintain, and expand where rooms run short. */
object HotelierAi {
    const val HIGH_OCCUPANCY_PPM = 880_000L
    const val LOW_OCCUPANCY_PPM = 550_000L
    const val MAX_RACK_OVER_REFERENCE_PPM = 1_800_000L
    const val MIN_RACK_OVER_REFERENCE_PPM = 550_000L
    const val WORN_PPM = 550_000L
    const val EXPAND_UNMET_PPM = 150_000L

    fun viewOf(state: TourismState, company: CompanyId, cash: Money, stance: HotelierStance, tick: Long): HotelierView {
        val params = TourismParams()
        val props = state.propertiesOf(company).filter { it.isOpen }.map { p ->
            val dest = state.destination(p.destination)
            val level = dest?.priceLevelPpm ?: 1_000_000L
            val index = TourismAnalytics.marketIndex(p, state.openPropertiesIn(p.destination))
            val last = p.days.takeLast(14)
            val gone = last.sumOf { (it.cancelled + it.noShows).toLong() }
            val kept = last.sumOf { it.roomsSold.toLong() }
            PropertyView(
                p.id, p.destination, p.kind, p.stars, p.totalRooms, p.settings.policy, p.settings.rackRate,
                Money(mulDiv(AccommodationRules.referenceRack(p.kind, p.stars).raw, level, 1_000_000L)),
                TourismAnalytics.kpis(p, 14).occupancyPpm, TourismAnalytics.kpis(p, 30).occupancyPpm, index?.adrPpm ?: 0L, p.reviews.ratingTenths,
                p.conditionPpm, p.overbookPpm, if (gone + kept <= 0L) 900_000L else mulDiv(kept, 1_000_000L, gone + kept),
                last.sumOf { it.walked }, p.listedOn.size, dest?.unmetPpm ?: 0L,
                if (p.stars < p.kind.maxStars) AccommodationRules.upgradeCost(p.kind, p.stars, p.stars + 1, p.totalRooms) else null,
                Money(mulDiv(AccommodationRules.buildCost(p.kind, p.stars, p.totalRooms).raw, 1_000_000L - p.conditionPpm, 1_000_000L) * params.renovationShare / 1_000_000L),
                Money(mulDiv(AccommodationRules.buildCost(p.kind, p.stars, 1).raw, params.expansionCostPpm, 1_000_000L)),
            )
        }
        val openings = state.destinations.mapNotNull { d ->
            val peers = state.openPropertiesIn(d.id)
            if (peers.isEmpty()) return@mapNotNull null
            val occ = TourismAnalytics.occupancyPpm(d, 30)
            val stars = 3
            val rooms = 60
            Opening(
                d.id, PropertyKind.HOTEL, stars, rooms, AccommodationRules.buildCost(PropertyKind.HOTEL, stars, rooms), occ, d.unmetPpm,
                Money(mulDiv(AccommodationRules.referenceRack(PropertyKind.HOTEL, stars).raw, d.priceLevelPpm, 1_000_000L)),
            )
        }
        return HotelierView(company, tick, cash, stance, props, openings, state.intermediariesOfChannel(Channel.ONLINE).map { it.id })
    }

    /** Years for new rooms to earn back their cost at the occupancy and rate expected. */
    fun paybackYears(cost: Money, rooms: Int, rate: Money, occupancyPpm: Long): Long {
        val perYear = mulDiv(rate.raw * rooms.toLong() * 360L, occupancyPpm.coerceAtLeast(100_000L), 1_000_000L) * 30L / 100L
        if (perYear <= 0L) return Long.MAX_VALUE
        return (cost.raw + perYear - 1L) / perYear
    }

    fun decide(view: HotelierView): List<HotelierMove> {
        val moves = ArrayList<HotelierMove>()
        var budget = Money(mulDiv(view.cash.raw.coerceAtLeast(0L), view.stance.cashShareToSpendPpm, 1_000_000L))
        val st = view.stance

        for (p in view.properties) {
            // Prices follow occupancy.
            val rack = p.rack
            val high = p.occupancy14Ppm >= HIGH_OCCUPANCY_PPM
            val low = p.occupancy14Ppm <= LOW_OCCUPANCY_PPM || (p.adrIndexPpm > 1_150_000L && p.occupancy14Ppm < 650_000L)
            if (high && !low) {
                val ceiling = mulDiv(p.reference.raw, MAX_RACK_OVER_REFERENCE_PPM, 1_000_000L)
                val up = Money(minOf(mulDiv(rack.raw, 1_000_000L + st.priceStepPpm, 1_000_000L), ceiling))
                if (up > rack) moves += HotelierMove.SetRack(p.property, up)
            } else if (low) {
                val floor = mulDiv(p.reference.raw, MIN_RACK_OVER_REFERENCE_PPM, 1_000_000L)
                val down = Money(maxOf(mulDiv(rack.raw, 1_000_000L - st.priceStepPpm, 1_000_000L), floor))
                if (down < rack) moves += HotelierMove.SetRack(p.property, down)
            }
            if (st.usesDynamicPricing && p.policy == RatePolicy.FIXED) moves += HotelierMove.UseDynamic(p.property, 780_000L)

            // Overbooking follows how many bookings fall through, and backs off after guests were walked.
            if (st.maxOverbookPpm > 0L) {
                var wanted = minOf(st.maxOverbookPpm, RevenueManagement.suggestedOverbookPpm(p.rooms, p.showPpm))
                if (p.walked14 >= TourismAdvisor.WALKS_TO_WARN) wanted /= 2L
                if (Math.abs(wanted - p.overbookPpm) >= 10_000L) moves += HotelierMove.SetOverbook(p.property, wanted)
            } else if (p.overbookPpm > 0L) {
                moves += HotelierMove.SetOverbook(p.property, 0L)
            }

            if (p.listedPlatforms == 0 && p.occupancy30Ppm < 650_000L && view.onlinePlatforms.isNotEmpty()) {
                moves += HotelierMove.ListOnPlatform(p.property, view.onlinePlatforms.first())
            }

            // Maintenance before growth.
            if (p.conditionPpm < WORN_PPM && p.renovationCost <= budget) {
                moves += HotelierMove.Renovate(p.property, p.renovationCost)
                budget -= p.renovationCost
            }
        }

        // Growth: raise the stars of a full, well-liked house, then add rooms where visitors are turned away.
        for (p in view.properties.sortedByDescending { it.occupancy30Ppm }) {
            val up = p.upgradeCost
            if (up != null && p.ratingTenths >= 42 && p.occupancy30Ppm >= 820_000L && p.adrIndexPpm >= 1_000_000L && up <= budget &&
                paybackYears(up, p.rooms, p.reference, p.occupancy30Ppm) <= st.maxPaybackYears
            ) {
                moves += HotelierMove.Upgrade(p.property, p.stars + 1, up)
                budget -= up
                continue
            }
            if (p.occupancy30Ppm >= 850_000L && p.unmetPpm >= EXPAND_UNMET_PPM) {
                val rooms = (p.rooms / 10).coerceIn(5, 50)
                val cost = p.expansionCostPerRoom * rooms.toLong()
                if (cost <= budget && paybackYears(cost, rooms, p.rack, p.occupancy30Ppm) <= st.maxPaybackYears) {
                    moves += HotelierMove.Expand(p.property, rooms, cost)
                    budget -= cost
                }
            }
        }

        // A new house in the town with the most unmet demand, once at most per round.
        if (view.properties.size < st.maxProperties) {
            val best = view.openings
                .filter { it.unmetPpm >= EXPAND_UNMET_PPM && it.occupancyPpm >= 750_000L && it.cost <= budget }
                .filter { paybackYears(it.cost, it.rooms, it.expectedRate, it.occupancyPpm) <= st.maxPaybackYears }
                .maxWithOrNull(compareBy<Opening>({ it.unmetPpm }, { it.occupancyPpm }).thenByDescending { it.destination })
            if (best != null) moves += HotelierMove.Build(best)
        }
        return moves
    }

    /** Carries out the moves in order through the service. Money for investments comes back as intents; nothing is posted. */
    fun execute(service: TourismService, view: HotelierView, moves: List<HotelierMove>, tick: Tick): List<MoveResult> {
        val results = ArrayList<MoveResult>()
        for (m in moves) results += run(service, view, m, tick)
        return results
    }

    private fun capex(o: Outcome<Capex>): Pair<Outcome<Unit>, List<SettlementIntent>> = when (o) {
        is Outcome.Ok -> Outcome.ok(Unit) to listOfNotNull(o.value.payment)
        is Outcome.Rejected -> o to emptyList()
    }

    private fun <T> plain(o: Outcome<T>): Pair<Outcome<Unit>, List<SettlementIntent>> = when (o) {
        is Outcome.Ok -> Outcome.ok(Unit) to emptyList()
        is Outcome.Rejected -> o to emptyList()
    }

    private fun run(service: TourismService, view: HotelierView, m: HotelierMove, tick: Tick): MoveResult {
        val (outcome, intents) = when (m) {
            is HotelierMove.SetRack -> plain(service.setRackRate(m.property, m.rack))
            is HotelierMove.UseDynamic -> plain(service.setPolicy(m.property, RatePolicy.DYNAMIC, m.targetOccupancyPpm))
            is HotelierMove.SetOverbook -> plain(service.setOverbooking(m.property, m.ppm))
            is HotelierMove.ListOnPlatform -> plain(service.listWith(m.property, m.platform))
            is HotelierMove.Renovate -> capex(service.renovate(m.property, tick))
            is HotelierMove.Upgrade -> capex(service.upgradeStars(m.property, m.stars, tick))
            is HotelierMove.Expand -> capex(service.expandRooms(m.property, RoomClass.STANDARD, m.rooms, tick))
            is HotelierMove.Build -> {
                val o = m.opening
                val rooms = intArrayOf(o.rooms * 7 / 10, o.rooms * 2 / 10, o.rooms - o.rooms * 7 / 10 - o.rooms * 2 / 10)
                val name = "${o.kind.label} ${service.state.counters.nextProperty}"
                when (val r = service.addProperty(o.destination, view.company, null, o.kind, o.stars, rooms, tick, name)) {
                    is Outcome.Ok -> Outcome.ok(Unit) to listOfNotNull(r.value.payment)
                    is Outcome.Rejected -> r to emptyList()
                }
            }
        }
        return MoveResult(m, outcome, intents)
    }
}
