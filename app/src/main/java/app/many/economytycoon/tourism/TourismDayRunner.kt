package app.many.economytycoon.tourism

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/**
 * The daily pipeline of the tourism module, in this order: lifecycle (buildings open, contracts end), shocks,
 * decay of marketing, booking for the whole horizon, the night itself at every property, attractions, then the
 * destination's books (history, reputation, domestic share, the monthly roll-up and price level).
 */
internal class TourismDayRunner(private val state: TourismState, private val params: TourismParams) {

    fun run(tick: Tick, inputs: DayInputs, random: RandomProvider): DayReport {
        lifecycle(tick, inputs)
        shocks(tick, random)
        decayMarketing()
        val ctx = DayContext(tick, inputs, state, params)
        ctx.events += pending
        pending.clear()
        val booking = BookingEngine(ctx)
        booking.run()

        val realizer = StayRealizer(ctx)
        val reports = ArrayList<DestinationReport>()
        val spending = ArrayList<LocalSpendingReport>()
        val routes = ArrayList<RouteLoad>()
        for (dest in ctx.destinations) {
            val flow = booking.todayFlows[dest.id]
            val cells = flow?.cells.orEmpty()
            routes += flow?.routes.orEmpty()
            for (r in flow?.routes.orEmpty()) if (r.turnedAway > 0L) ctx.events += TourismEvent.RoutesFull(r.origin, r.destination, r.turnedAway, tick.value)
            if (festivalStarts(dest, tick)) ctx.events += TourismEvent.FestivalBegan(dest.id, tick.value)
            val eff = ctx.shockEffects(dest.id, tick.value)
            val weather = ctx.weatherPenaltyPpm(dest.id, 0)
            val experience = experience(dest, weather)
            updateDomesticShare(dest, cells)

            var sold = 0L; var availableRooms = 0L; var revenue = Money.ZERO; var walked = 0L; var guests = 0L
            val bySegment = LongArray(VisitorSegment.COUNT)
            for (p in state.openPropertiesIn(dest.id)) {
                val r = realizer.realize(p, dest, eff, experience)
                sold += r.day.roomsSold; availableRooms += r.day.roomsAvailable; revenue += r.day.roomRevenue; walked += r.day.walked; guests += r.day.guests
                for (i in bySegment.indices) bySegment[i] += r.guestsBySegment[i]
            }
            val visits = runAttractions(dest, guests, weather, tick)
            updateReputation(dest, tick)

            val demand = dest.demandMap.remove(tick.value) ?: 0L
            val denied = dest.deniedMap.remove(tick.value) ?: 0L
            dest.deniedMap.headMap(tick.value).clear(); dest.demandMap.headMap(tick.value).clear()
            if (denied > 0L) ctx.events += TourismEvent.DemandTurnedAway(dest.id, denied, tick.value)
            val arrivals = VisitorFlows.total(cells)
            val day = DestinationDay(
                tick.value, arrivals, demand, sold, availableRooms, denied, walked, revenue, guests, visits, flow?.appealPpm ?: 0L,
            )
            dest.pushDay(day)
            dest.setUnmet(if (demand <= 0L) 0L else denied * 1_000_000L / demand)
            val byOrigin = java.util.TreeMap<OriginId, Long>()
            for (c in cells) byOrigin.merge(c.origin, c.persons, Long::plus)
            reports += DestinationReport(
                dest.id, day, LongArray(VisitorSegment.COUNT) { VisitorFlows.persons(cells, VisitorSegment.values()[it]) }, byOrigin,
                state.activeShocks(tick.value).count { it.touches(dest.id) }, experience,
            )
            spending += localSpending(dest, bySegment, guests, inputs)
            if (tick.value % Calendar.DAYS_PER_MONTH == Calendar.DAYS_PER_MONTH - 1L) monthEnd(dest, tick)
        }
        prune(tick)
        return DayReport(tick, reports, ctx.events.toList(), routes, spending)
    }

    // ----- lifecycle -------------------------------------------------------------------------

    private val pending = ArrayList<TourismEvent>()

    private fun lifecycle(tick: Tick, inputs: DayInputs) {
        val t = tick.value
        for (p in state.propertyMap.values) {
            if (p.status == PropertyStatus.UNDER_CONSTRUCTION && t >= p.opensAt) {
                p.status = PropertyStatus.OPEN
                p.openedAt = t
                pending += TourismEvent.PropertyOpened(p.id, t)
            }
        }
        for (a in state.attractionMap.values) if (a.opensAt == t && a.open) pending += TourismEvent.AttractionOpened(a.id, t)
        for (a in state.allotmentMap.values) {
            if (a.status == AllotmentStatus.ACTIVE && t >= a.endsAt.value) {
                a.status = AllotmentStatus.ENDED
                a.endedAt = t
                pending += TourismEvent.AllotmentEnded(a.id, t)
            } else if (a.status == AllotmentStatus.PROPOSED && t >= a.endsAt.value) {
                a.status = AllotmentStatus.DECLINED
            }
        }
        for (s in state.shockMap.values) {
            if (s.start.value == t) pending += TourismEvent.ShockStarted(s.id, s.kind, s.destination, t)
            if (s.end.value == t) pending += TourismEvent.ShockEnded(s.id, s.kind, t)
        }
    }

    private fun shocks(tick: Tick, random: RandomProvider) {
        for (dest in state.destinationMap.values) {
            val active = state.shockMap.values.filter { it.touches(dest.id) && it.destination == dest.id && it.activeAt(tick) }.map { it.kind }.toSet()
            val rolled = ShockGenerator.roll(tick, dest.climate, active, random.stream("tourism.shock", dest.id.value))
            for ((kind, days, severity) in rolled) {
                if (state.activeShocks(tick.value).count { it.destination == dest.id } >= params.maxActiveShocksPerDestination) break
                val s = Shock(state.counters.shock(), kind, dest.id, null, tick, days, severity, "${kind.label} in ${dest.name}")
                state.shockMap[s.id] = s
                pending += TourismEvent.ShockStarted(s.id, kind, dest.id, tick.value)
            }
        }
    }

    private fun decayMarketing() {
        for (d in state.destinationMap.values) {
            for (i in d.marketingPpm.indices) d.marketingPpm[i] -= mulDiv(d.marketingPpm[i], params.marketingDecayPpm, 1_000_000L)
        }
    }

    private fun festivalStarts(d: Destination, tick: Tick): Boolean {
        if (d.festivalMonth == 0) return false
        val date = Calendar.date(tick)
        return date.month == d.festivalMonth && date.day == 1
    }

    // ----- the destination's day -----------------------------------------------------------

    /** How the stay felt apart from the room: the weather and how the attractions treated their visitors yesterday. */
    private fun experience(dest: Destination, weatherPenaltyPpm: Long): Long {
        val atts = state.attractionsIn(dest.id).filter { it.lastDay != null && (it.lastDay?.visits ?: 0L) > 0L }
        val queue = if (atts.isEmpty()) 800_000L else {
            val visits = atts.sumOf { it.lastDay?.visits ?: 0L }
            atts.sumOf { it.queueSatisfactionPpm * (it.lastDay?.visits ?: 0L) } / visits.coerceAtLeast(1L)
        }
        return ((1_000_000L - weatherPenaltyPpm) * 4L + queue * 6L) / 10L
    }

    private fun updateDomesticShare(dest: Destination, cells: List<FlowCell>) {
        val total = VisitorFlows.total(cells)
        if (total <= 0L) return
        val domestic = cells.filter { c -> state.origin(c.origin)?.country == dest.country }.sumOf { it.persons }
        dest.setDomesticShare(domestic * 1_000_000L / total)
    }

    private fun runAttractions(dest: Destination, guests: Long, weather: Long, tick: Tick): Long {
        val present = guests + mulDiv(guests, params.dayTripperPpm, 1_000_000L)
        var visits = 0L
        for (a in state.attractionsIn(dest.id)) {
            val day = AttractionModel.runDay(a, AttractionDayInputs(present, weather), tick.value)
            a.pushDay(day)
            if (!a.open || tick.value < a.opensAt) continue
            a.queueSatisfactionPpm = (a.queueSatisfactionPpm + (day.satisfactionPpm - a.queueSatisfactionPpm) / 10L).coerceIn(0L, 1_000_000L)
            a.monthRevenue += day.revenue; a.monthCost += day.cost; a.monthVisits += day.visits
            a.lifetimeVisits += day.visits; a.lifetimeRevenue += day.revenue
            if (day.visits > 0L) a.reviews.record(day.satisfactionPpm, day.visits.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            visits += day.visits
        }
        return visits
    }

    private fun updateReputation(dest: Destination, tick: Tick) {
        val ratings = ArrayList<Pair<Long, Long>>()
        for (p in state.openPropertiesIn(dest.id)) {
            val sold = p.lastDay?.roomsSold?.toLong() ?: 0L
            if (sold > 0L) ratings += p.reviews.scorePpm to sold
        }
        for (a in state.attractionsIn(dest.id)) {
            val v = a.lastDay?.visits ?: 0L
            if (v > 0L) ratings += a.reviews.scorePpm to (v / 4L).coerceAtLeast(1L)
        }
        dest.setReputation(GuestSatisfaction.destinationReputation(dest.reputationPpm, ratings))
    }

    private fun localSpending(dest: Destination, bySegment: LongArray, guests: Long, inputs: DayInputs): LocalSpendingReport {
        val present = LongArray(bySegment.size) { bySegment[it] + mulDiv(bySegment[it], params.dayTripperPpm, 1_000_000L) }
        val spend = LocalSpending.breakdown(present, dest.priceLevelPpm)
        val population = inputs.residents[dest.id] ?: 0L
        val uplifts = LocalSpending.uplifts(spend, population, inputs.residentSpendPerDay)
        val multiplier = LocalSpending.multiplierPpm(inputs.respendPpm, inputs.leakagePpm)
        return LocalSpendingReport(dest.id, guests, spend, uplifts, multiplier, LocalSpending.economicImpact(spend.total, multiplier))
    }

    // ----- month end ----------------------------------------------------------------------------

    private fun monthEnd(dest: Destination, tick: Tick) {
        val month = tick.value / Calendar.DAYS_PER_MONTH
        val days = dest.days.takeLast(Calendar.DAYS_PER_MONTH)
        dest.pushMonth(
            DestinationMonth(
                month, days.sumOf { it.arrivals }, days.sumOf { it.roomsSold }, days.sumOf { it.roomsAvailable },
                days.sumOf { it.deniedRoomNights }, Money.sum(days.map { it.roomRevenue }),
            ),
        )
        updatePriceLevel(dest, days)
    }

    /**
     * Pulls the price level toward what rooms actually cost compared with what they normally cost in the place: a
     * destination whose hotels charge a premium becomes dearer in the eyes of visitors, one that discounts gets cheaper.
     */
    private fun updatePriceLevel(dest: Destination, days: List<DestinationDay>) {
        val sold = days.sumOf { it.roomsSold }
        if (sold <= 0L) return
        val revenue = Money.sum(days.map { it.roomRevenue })
        var reference = 0L
        for (p in state.openPropertiesIn(dest.id)) {
            val rooms = p.days.takeLast(Calendar.DAYS_PER_MONTH).sumOf { it.roomsSold.toLong() }
            reference += AccommodationRules.referenceRack(p.kind, p.stars).raw * rooms
        }
        if (reference <= 0L) return
        val expected = mulDiv(mulDiv(reference, dest.basePriceLevelPpm, 1_000_000L), MIX_UPLIFT_PPM, 1_000_000L)
        val index = mulDiv(revenue.raw, 1_000_000L, expected)
        val pull = mulDiv(index - 1_000_000L, params.priceLevelPassThroughPpm, 1_000_000L)
        val level = mulDiv(dest.basePriceLevelPpm, 1_000_000L + pull, 1_000_000L)
        dest.setPriceLevel(dest.basePriceLevelPpm, level.coerceIn(dest.basePriceLevelPpm / 2L, dest.basePriceLevelPpm * 2L))
    }

    private fun prune(tick: Tick) {
        val oldAllot = tick.value - params.keepEndedAllotmentDays
        state.allotmentMap.values.removeAll { !it.status.live && it.status != AllotmentStatus.PROPOSED && (if (it.endedAt >= 0L) it.endedAt else it.endsAt.value) < oldAllot }
        val oldShock = tick.value - params.keepShockDays
        state.shockMap.values.removeAll { it.end.value < oldShock }
    }

    companion object {
        /** Rooms are sold in a mix of classes, so average revenue per room runs above the standard rack rate. */
        const val MIX_UPLIFT_PPM = 1_120_000L
    }
}
