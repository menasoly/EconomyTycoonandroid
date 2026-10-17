package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

/** The standard measures of a hotel's trading over a window of nights. */
data class PropertyKpis(
    val nights: Int,
    val roomsAvailable: Long,
    val roomsSold: Long,
    val roomRevenue: Money,
    val totalRevenue: Money,
    val gop: Money,
    val walked: Long,
    val commissions: Money,
    val satisfactionPpm: Long,
) {
    val occupancyPpm: Long get() = if (roomsAvailable <= 0L) 0L else roomsSold * 1_000_000L / roomsAvailable
    val adr: Money get() = if (roomsSold <= 0L) Money.ZERO else Money(roomRevenue.raw / roomsSold)
    val revPar: Money get() = if (roomsAvailable <= 0L) Money.ZERO else Money(roomRevenue.raw / roomsAvailable)
    val goppar: Money get() = if (roomsAvailable <= 0L) Money.ZERO else Money(gop.raw / roomsAvailable)
    val commissionShareOfRevenuePpm: Long get() = if (roomRevenue.raw <= 0L) 0L else mulDiv(commissions.raw, 1_000_000L, roomRevenue.raw)
}

/** Occupancy, rate and revenue indices of a property against the other properties of its destination, 1,000,000 = par. */
data class MarketIndex(val occupancyPpm: Long, val adrPpm: Long) {
    /** Revenue per available room index: the product of the other two. */
    val revParPpm: Long get() = mulDiv(occupancyPpm, adrPpm, 1_000_000L)
}

/** One month's demand level against the year, per month of the calendar. */
data class SeasonPoint(val month: Int, val indexPpm: Long)

object TourismAnalytics {
    fun kpis(p: Property, window: Int = 30): PropertyKpis {
        val days = p.days.takeLast(window)
        return PropertyKpis(
            nights = days.size,
            roomsAvailable = days.sumOf { it.roomsAvailable.toLong() },
            roomsSold = days.sumOf { it.roomsSold.toLong() },
            roomRevenue = Money.sum(days.map { it.roomRevenue }),
            totalRevenue = Money.sum(days.map { it.totalRevenue }),
            gop = Money.sum(days.map { it.gop }),
            walked = days.sumOf { it.walked.toLong() },
            commissions = Money.sum(days.map { it.commissions }),
            satisfactionPpm = if (days.isEmpty()) p.reviews.scorePpm else days.sumOf { it.satisfactionPpm } / days.size,
        )
    }

    /** The combined figures of a set of properties over the same window. */
    fun setKpis(props: List<Property>, window: Int = 30): PropertyKpis {
        val all = props.map { kpis(it, window) }
        return PropertyKpis(
            all.maxOfOrNull { it.nights } ?: 0, all.sumOf { it.roomsAvailable }, all.sumOf { it.roomsSold }, Money.sum(all.map { it.roomRevenue }),
            Money.sum(all.map { it.totalRevenue }), Money.sum(all.map { it.gop }), all.sumOf { it.walked }, Money.sum(all.map { it.commissions }),
            if (all.isEmpty()) 0L else all.sumOf { it.satisfactionPpm } / all.size,
        )
    }

    /** How a property trades against its competitive set (the other open properties in the destination). */
    fun marketIndex(p: Property, others: List<Property>, window: Int = 30): MarketIndex? {
        val own = kpis(p, window)
        val set = setKpis(others.filter { it.id != p.id }, window)
        if (own.roomsAvailable <= 0L || set.roomsAvailable <= 0L || set.roomsSold <= 0L || own.roomsSold <= 0L) return null
        val occ = mulDiv(own.occupancyPpm, 1_000_000L, set.occupancyPpm.coerceAtLeast(1L))
        val adr = mulDiv(own.adr.raw, 1_000_000L, set.adr.raw.coerceAtLeast(1L))
        return MarketIndex(occ, adr)
    }

    /** Share of the room-nights people asked for in the window that could not be sold. */
    fun denialRatePpm(d: Destination, window: Int = 30): Long {
        val days = d.days.takeLast(window)
        val demand = days.sumOf { it.roomNightDemand }
        if (demand <= 0L) return 0L
        return days.sumOf { it.deniedRoomNights } * 1_000_000L / demand
    }

    /** Destination occupancy over the window. */
    fun occupancyPpm(d: Destination, window: Int = 30): Long {
        val days = d.days.takeLast(window)
        val avail = days.sumOf { it.roomsAvailable }
        return if (avail <= 0L) 0L else days.sumOf { it.roomsSold } * 1_000_000L / avail
    }

    /** Average daily rate across a destination's rooms sold over the window. */
    fun adr(d: Destination, window: Int = 30): Money {
        val days = d.days.takeLast(window)
        val sold = days.sumOf { it.roomsSold }
        return if (sold <= 0L) Money.ZERO else Money(Money.sum(days.map { it.roomRevenue }).raw / sold)
    }

    /**
     * Demand against the year by calendar month, from the destination's monthly record: each month's occupancy divided
     * by the mean of the months known. Months not seen yet are left out.
     */
    fun seasonality(d: Destination): List<SeasonPoint> {
        val byMonth = java.util.TreeMap<Int, MutableList<Long>>()
        for (m in d.months) if (m.roomsAvailable > 0L) byMonth.getOrPut((m.month % 12).toInt() + 1) { ArrayList() } += m.occupancyPpm
        if (byMonth.isEmpty()) return emptyList()
        val means = byMonth.mapValues { it.value.sum() / it.value.size }
        val overall = means.values.sum() / means.size
        if (overall <= 0L) return means.map { SeasonPoint(it.key, 0L) }
        return means.map { SeasonPoint(it.key, mulDiv(it.value, 1_000_000L, overall)) }
    }

    /** Peak month index over the trough month index, ppm; 1,000,000 for a flat year. */
    fun seasonalityRangePpm(points: List<SeasonPoint>): Long {
        val positive = points.filter { it.indexPpm > 0L }
        if (positive.size < 2) return 1_000_000L
        return mulDiv(positive.maxOf { it.indexPpm }, 1_000_000L, positive.minOf { it.indexPpm })
    }

    /** Share of room revenue earned by the busiest third of the nights in the window, ppm. */
    fun peakConcentrationPpm(p: Property, window: Int = 90): Long {
        val rev = p.days.takeLast(window).map { it.roomRevenue.raw }.sortedDescending()
        if (rev.isEmpty() || rev.sum() <= 0L) return 0L
        val top = rev.take((rev.size + 2) / 3).sum()
        return mulDiv(top, 1_000_000L, rev.sum())
    }

    /** The breakdown of where a destination's appeal for a segment comes from, for the screen. */
    fun appealBreakdown(d: Destination, segment: VisitorSegment, tick: Long, state: TourismState): Appeal {
        val atts = state.attractionsIn(d.id).filter { it.open && tick >= it.opensAt }
        val (culture, nature, nightlife) = AttractionModel.boosts(atts)
        val drivers = d.drivers.copy(
            culturePpm = Attractiveness.boosted(d.drivers.culturePpm, culture),
            naturePpm = Attractiveness.boosted(d.drivers.naturePpm, nature),
            nightlifePpm = Attractiveness.boosted(d.drivers.nightlifePpm, nightlife),
        )
        val shocks = ShockEffects.combine(state.shocks.filter { it.origin == null && it.touches(d.id) && it.activeAt(app.many.economytycoon.core.Tick(tick)) }.map { ShockEffects.of(it) })
        val ctx = AppealContext(
            season = app.many.economytycoon.core.Calendar.season(app.many.economytycoon.core.Tick(tick)), drivers = drivers,
            eventPpm = maxOf(shocks.eventPpm, ShockGenerator.festivalPpm(d.festivalMonth, d.festivalDays, app.many.economytycoon.core.Tick(tick))),
            safetyDropPpm = shocks.safetyDropPpm,
            taxPriceAddPpm = TourismTax.priceFeelPpm(d, Money(mulDiv(DayContext.REFERENCE_NIGHT.raw, d.priceLevelPpm, 1_000_000L))),
        )
        return Attractiveness.appeal(d, segment, ctx)
    }

    /** The segment shares of everything the origin markets send out, ppm in segment order; even shares with no markets. */
    fun marketMix(state: TourismState): LongArray {
        val totals = LongArray(VisitorSegment.COUNT)
        for (o in state.origins) for (s in VisitorSegment.values()) totals[s.ordinal] += mulDiv(o.outboundPerDay, o.mixPpm[s.ordinal], 1_000_000L)
        val sum = totals.sum()
        if (sum <= 0L) return LongArray(VisitorSegment.COUNT) { 1_000_000L / VisitorSegment.COUNT }
        val shares = LongArray(VisitorSegment.COUNT) { mulDiv(totals[it], 1_000_000L, sum) }
        shares[0] += 1_000_000L - shares.sum()
        return shares
    }
}
