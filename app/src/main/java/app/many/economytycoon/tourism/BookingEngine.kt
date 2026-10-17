package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.core.splitLargestRemainder

/** The visitors of one destination today, after the transport limit, and the routes that brought them. */
internal class DestFlow(val cells: List<FlowCell>, val routes: List<RouteLoad>, val appealPpm: Long)

/**
 * Takes the day's booking requests. For every night in the booking horizon it works out how many people want to come
 * (the flow model), how many rooms of each class that asks for today (the booking curve), through which channel, and
 * which property takes each request at the price it quotes now. Requests nobody can take are recorded as denied.
 */
internal class BookingEngine(private val ctx: DayContext) {
    companion object {
        /** Share of guests turned away from a room class who ask for the next class up instead. */
        const val TRADE_UP_PPM = 350_000L
    }

    val todayFlows = java.util.TreeMap<DestinationId, DestFlow>()
    private val state = ctx.state

    fun run() {
        for (lead in 0 until Seasonality.HORIZON) bookLead(lead)
    }

    private fun bookLead(lead: Int) {
        val date = ctx.tick.value + lead
        val at = Tick(date)
        if (ctx.destinations.isEmpty()) return
        val effects = ctx.destinations.associate { it.id to ctx.shockEffects(it.id, date) }
        val candidates = ctx.destinations.map { d ->
            val eff = effects.getValue(d.id)
            val appealCtx = ctx.appealContext(d, date, lead, eff)
            val values = LongArray(VisitorSegment.COUNT) { Attractiveness.appeal(d, VisitorSegment.values()[it], appealCtx).valuePpm }
            FlowCandidate(d.id, d.country, d.location, values, ctx.arrivalsMultipliers(d, date, eff))
        }
        val flows = VisitorFlows.allocate(ctx.origins, candidates, at) { ctx.originShockPpm(it, date) }
        for ((i, d) in ctx.destinations.withIndex()) {
            val eff = effects.getValue(d.id)
            val transportPpm = mulDiv(eff.transportPpm, ctx.weatherTransportPpm(d.id, lead), 1_000_000L)
            val (cells, routes) = TransportCoupling.limit(flows.getValue(d.id), d.id, ctx.inputs.transport, at, transportPpm)
            if (lead == 0) todayFlows[d.id] = DestFlow(cells, routes, candidates[i].valuePpm.sum() / VisitorSegment.COUNT)
            bookNight(d, date, lead, cells)
        }
    }

    /** Roomnights per stay-date that [persons] arrivals of a segment ask for. */
    private fun roomNights(persons: Long, s: VisitorSegment): Long {
        val p = s.profile
        return mulDiv(persons, p.nightsTenths.toLong() * (1_000_000L - p.informalPpm), p.partyTenths.toLong() * 1_000_000L)
    }

    private inner class Night(val dest: Destination, val date: Long, val lead: Int, val mix: LongArray) {
        val props: List<Property> = state.openPropertiesIn(dest.id)
        private val quotes = java.util.TreeMap<Long, RateQuote>()
        private val allots: Map<PropertyId, List<Allotment>> = props.associate { p -> p.id to state.liveAllotmentsOfProperty(p.id) }

        fun held(p: Property, c: RoomClass): Int =
            allots.getValue(p.id).filter { it.room == c }.sumOf { it.heldOn(date, lead) }

        fun booked(p: Property, c: RoomClass): Int = p.book(date)?.booked?.get(c.ordinal) ?: 0

        fun free(p: Property, c: RoomClass): Int = (p.sellLimit(c) - booked(p, c) - held(p, c)).coerceAtLeast(0)

        fun quote(p: Property, c: RoomClass): RateQuote = quotes.getOrPut(p.id.value * RoomClass.COUNT + c.ordinal) {
            val baseline = DemandForecast.baselineOccupancyPpm(p, ctx.tick.value, date, mix)
            RevenueManagement.quote(p, c, lead, booked(p, c), p.sellable(c), baseline, mix)
        }

        fun place(p: Property, c: RoomClass, s: VisitorSegment, n: Int, channel: Channel, via: Intermediary?) {
            if (n <= 0) return
            val nb = p.nightBook(date)
            val rate = quote(p, c).rate.raw
            nb.booked[c.ordinal] += n
            nb.revenueRaw[c.ordinal] += rate * n
            nb.bySegment[s.ordinal] += n
            nb.byChannel[channel.ordinal] += n
            if (via != null) {
                nb.addCommission(via.id, mulDiv(rate * n, via.commissionPpm, 1_000_000L))
                nb.addVia(via.id, n)
            }
        }

        fun placeOperator(p: Property, a: Allotment, s: VisitorSegment, n: Int) {
            if (n <= 0) return
            val nb = p.nightBook(date)
            val net = a.netRate.raw
            nb.booked[a.room.ordinal] += n
            nb.revenueRaw[a.room.ordinal] += net * n
            nb.operatorRooms[a.room.ordinal] += n
            nb.operatorRevenueRaw[a.room.ordinal] += net * n
            nb.bySegment[s.ordinal] += n
            nb.byChannel[Channel.OPERATOR.ordinal] += n
            a.sell(date, n)
        }
    }

    private fun bookNight(dest: Destination, date: Long, lead: Int, cells: List<FlowCell>) {
        val persons = LongArray(VisitorSegment.COUNT) { VisitorFlows.persons(cells, VisitorSegment.values()[it]) }
        val requests = LongArray(VisitorSegment.COUNT) { i ->
            val s = VisitorSegment.values()[i]
            mulDiv(roomNights(persons[i], s), Seasonality.bookingShare(s, lead), 1_000_000L)
        }
        val totalRequests = requests.sum()
        if (totalRequests <= 0L) return
        val night = Night(dest, date, lead, if (persons.sum() > 0L) persons else longArrayOf(1L, 1L, 1L, 1L))
        var denied = 0L
        for (s in VisitorSegment.values()) {
            val req = requests[s.ordinal]
            if (req <= 0L) continue
            val byClass = splitLargestRemainder(req, s.profile.classMixPpm)
            val channelMix = Channel.mix(s)
            var tradeUp = 0L
            for (c in RoomClass.values()) {
                val n = byClass[c.ordinal] + tradeUp
                if (n <= 0L) { tradeUp = 0L; continue }
                val byChannel = splitLargestRemainder(n, channelMix)
                var toDirect = 0L
                toDirect += fillOperators(night, s, c, byChannel[Channel.OPERATOR.ordinal])
                toDirect += viaIntermediaries(night, s, c, Channel.AGENCY, byChannel[Channel.AGENCY.ordinal])
                toDirect += viaIntermediaries(night, s, c, Channel.ONLINE, byChannel[Channel.ONLINE.ordinal])
                val unmet = direct(night, s, c, byChannel[Channel.DIRECT.ordinal] + toDirect)
                // Some guests who find their class full take the next one up.
                tradeUp = if (c.ordinal < RoomClass.COUNT - 1) mulDiv(unmet, TRADE_UP_PPM, 1_000_000L) else 0L
                denied += unmet - tradeUp
            }
        }
        dest.demandMap.merge(date, totalRequests, Long::plus)
        if (denied > 0L) dest.deniedMap.merge(date, denied, Long::plus)
    }

    /** Operators sell what their allotments hold, the cheapest first. Returns requests that found no allotment room. */
    private fun fillOperators(night: Night, s: VisitorSegment, c: RoomClass, requests: Long): Long {
        if (requests <= 0L) return 0L
        data class Source(val op: TourOperator, val p: Property, val a: Allotment)
        val sources = ArrayList<Source>()
        for (p in night.props) for (a in state.liveAllotmentsOfProperty(p.id)) {
            if (a.room != c || a.openOn(night.date) <= 0) continue
            val op = state.operator(a.operator) ?: continue
            sources += Source(op, p, a)
        }
        if (sources.isEmpty()) return requests
        val ops = sources.map { it.op }.distinctBy { it.id }.sortedBy { it.id }
        val shares = AllotmentRules.splitByStrength(requests, ops.map { it.strengthPpm })
        var unfilled = 0L
        for ((i, op) in ops.withIndex()) {
            var want = shares[i]
            val mine = sources.filter { it.op.id == op.id }.sortedWith(compareBy({ it.a.netRate.raw }, { it.a.id }))
            for (src in mine) {
                if (want <= 0L) break
                val physical = (src.p.sellLimit(c) - night.booked(src.p, c)).coerceAtLeast(0)
                val take = minOf(want, src.a.openOn(night.date).toLong(), physical.toLong()).toInt()
                if (take > 0) {
                    night.placeOperator(src.p, src.a, s, take)
                    want -= take
                }
            }
            unfilled += want
        }
        return unfilled
    }

    /** Agencies and platforms send guests to the properties listed with them, by reach and ranking. */
    private fun viaIntermediaries(night: Night, s: VisitorSegment, c: RoomClass, channel: Channel, requests: Long): Long {
        if (requests <= 0L) return 0L
        val ints = state.intermediariesOfChannel(channel).filter { i -> night.props.any { i.id in it.listed && night.free(it, c) > 0 } }
        if (ints.isEmpty()) return requests
        val parts = splitLargestRemainder(requests, LongArray(ints.size) { ints[it].reachPpm })
        var leftover = 0L
        for ((k, i) in ints.withIndex()) {
            val listed = night.props.filter { i.id in it.listed && night.free(it, c) > 0 }
            val weights = LongArray(listed.size) { j ->
                val p = listed[j]
                val fair = Allocation.fairRate(p, c, night.dest.priceLevelPpm)
                mulDiv(Allocation.propertyWeight(p, s, night.quote(p, c).rate, fair), i.rankingPpm(p.reviews.scorePpm), 1_000_000L)
            }
            val caps = IntArray(listed.size) { night.free(listed[it], c) }
            val given = Allocation.waterFill(parts[k], weights, caps)
            for ((j, p) in listed.withIndex()) night.place(p, c, s, given[j], channel, i)
            leftover += parts[k] - given.sum()
        }
        return leftover
    }

    /** Guests who book directly choose among every property with room. Returns the requests turned away. */
    private fun direct(night: Night, s: VisitorSegment, c: RoomClass, requests: Long): Long {
        if (requests <= 0L) return 0L
        val open = night.props.filter { night.free(it, c) > 0 }
        if (open.isEmpty()) return requests
        val weights = LongArray(open.size) { j ->
            val p = open[j]
            Allocation.propertyWeight(p, s, night.quote(p, c).rate, Allocation.fairRate(p, c, night.dest.priceLevelPpm))
        }
        val caps = IntArray(open.size) { night.free(open[it], c) }
        val given = Allocation.waterFill(requests, weights, caps)
        for ((j, p) in open.withIndex()) night.place(p, c, s, given[j], Channel.DIRECT, null)
        return requests - given.sum()
    }
}
