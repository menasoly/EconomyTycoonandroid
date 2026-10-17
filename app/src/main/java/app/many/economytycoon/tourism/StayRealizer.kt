package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

/** A night at one property and the people who spent it, by segment. */
internal class StayResult(val day: StayDay, val guestsBySegment: LongArray, val guestRevenue: Money, val operatorRevenue: Money)

/**
 * Turns the rooms booked for tonight into a night: some guests cancel or do not come, some are walked when the house
 * is overbooked, the rest stay and pay. Costs, staff, levy and satisfaction follow, and the property's reputation and
 * condition move.
 */
internal class StayRealizer(private val ctx: DayContext) {
    private val state = ctx.state

    private fun weightedRate(nb: NightBook, pick: (SegmentProfile) -> Long): Long {
        val total = nb.bySegment.sum().toLong()
        if (total <= 0L) return 0L
        var sum = 0L
        for (s in VisitorSegment.values()) sum += nb.bySegment[s.ordinal].toLong() * pick(s.profile)
        return sum / total
    }

    fun realize(p: Property, dest: Destination, eff: ShockEffects, experiencePpm: Long): StayResult {
        val t = ctx.tick.value
        val nb = p.bookMap.remove(t) ?: NightBook()
        p.bookMap.headMap(t).clear()
        val available = p.sellableTotal()

        val baseCancel = mulDiv(weightedRate(nb) { it.cancelPpm }, p.cancellation.cancelFactorPpm, 1_000_000L)
        val cancelRate = (baseCancel + eff.cancelExtraPpm).coerceIn(0L, 900_000L)
        val noShowRate = weightedRate(nb) { it.noShowPpm }.coerceIn(0L, 900_000L)

        var cancelled = 0
        var noShows = 0
        var occupied = 0
        var walked = 0
        var guestRevenue = 0L
        var operatorRevenue = 0L
        var walkCost = 0L
        var guestShowsAll = 0L
        var guestBookedAll = 0L
        for (c in RoomClass.values()) {
            val i = c.ordinal
            val b = nb.booked[i]
            if (b == 0) continue
            val op = nb.operatorRooms[i]
            val gb = b - op
            val cancelledC = mulDiv(gb.toLong(), cancelRate, 1_000_000L).toInt()
            val noShowC = mulDiv((gb - cancelledC).toLong(), noShowRate, 1_000_000L).toInt()
            val guestShows = gb - cancelledC - noShowC
            val shows = guestShows + op
            val occ = minOf(shows, p.sellable(c))
            val walkedC = shows - occ
            val walkedGuests = minOf(walkedC, guestShows)
            val walkedOp = walkedC - walkedGuests
            val guestAvg = if (gb > 0) (nb.revenueRaw[i] - nb.operatorRevenueRaw[i]) / gb else 0L
            val opAvg = if (op > 0) nb.operatorRevenueRaw[i] / op else 0L
            val forfeits = if (p.cancellation.chargesNoShow) (cancelledC + noShowC).toLong() * guestAvg else 0L
            guestRevenue += (guestShows - walkedGuests).toLong() * guestAvg + forfeits
            operatorRevenue += (op - walkedOp).toLong() * opAvg
            val walkRate = if (gb > 0) guestAvg else opAvg
            walkCost += mulDiv(walkedC.toLong() * walkRate, ctx.params.walkCostMultiplePpm, 1_000_000L)
            cancelled += cancelledC; noShows += noShowC; occupied += occ; walked += walkedC
            guestShowsAll += guestShows - walkedGuests; guestBookedAll += gb
        }

        val bookedTotal = nb.total
        val guestsBySegment = LongArray(VisitorSegment.COUNT)
        var guests = 0L
        if (bookedTotal > 0) {
            for (s in VisitorSegment.values()) {
                val g = nb.bySegment[s.ordinal].toLong() * s.profile.partyTenths * occupied / (10L * bookedTotal)
                guestsBySegment[s.ordinal] = g
                guests += g
            }
        }

        val ancillary = Money(mulDiv(AccommodationRules.ancillaryPerGuest(p.kind, p.stars).raw * guests, dest.priceLevelPpm, 1_000_000L))
        val operating = AccommodationRules.fixedCostPerDay(p.kind, p.stars, p.totalRooms) + AccommodationRules.variableCost(p.kind, p.stars, occupied)
        val needed = Staffing.needed(p.kind, p.stars, p.totalRooms, occupied)
        val supply = ctx.inputs.staff.available(p.owner, dest.id, needed, ctx.tick).coerceIn(0, needed)
        val had = (supply.toLong() * eff.staffPpm.coerceIn(0L, 1_000_000L) / 1_000_000L).toInt()
        val wages = Staffing.wageCost(had, ctx.inputs.wage(dest.id))
        val service = Staffing.serviceLevelPpm(needed, had)
        val levy = TourismTax.levy(guests, dest.levyPerGuestNight, Money(guestRevenue), dest.levyRevenuePpm)

        // Commission is owed on the share of guest bookings that came and stayed.
        var commissions = 0L
        if (guestBookedAll > 0L && nb.commissionRaw.isNotEmpty()) {
            for ((id, raw) in nb.commissionRaw) {
                val owed = mulDiv(raw, guestShowsAll, guestBookedAll)
                commissions += owed
                p.month.addCommission(IntermediaryId(id), Money(owed))
                state.intermediary(IntermediaryId(id))?.let { it.lifetimeCommission += Money(owed); it.monthCommission += Money(owed) }
            }
        }
        for ((id, rooms) in nb.roomsVia) state.intermediary(IntermediaryId(id))?.let { it.lifetimeRooms += rooms; it.monthRooms += rooms }

        var satisfaction = p.reviews.scorePpm
        val before = p.reviews.ratingTenths
        if (occupied > 0) {
            val paid = Money((guestRevenue + operatorRevenue) / occupied)
            val fair = Allocation.fairRate(p, RoomClass.STANDARD, dest.priceLevelPpm)
            val walkedShare = if (occupied + walked > 0) walked.toLong() * 1_000_000L / (occupied + walked) else 0L
            satisfaction = GuestSatisfaction.score(SatisfactionInputs(p.stars, p.conditionPpm, service, paid, fair, walkedShare, experiencePpm))
            p.reviews.record(satisfaction, guests.toInt())
        }
        val after = p.reviews.ratingTenths
        if (after != before) ctx.events += TourismEvent.RatingChanged(p.id, before, after, ctx.tick.value)
        if (walked > 0) ctx.events += TourismEvent.GuestsWalked(p.id, walked, ctx.tick.value)
        if (available > 0 && occupied >= available) ctx.events += TourismEvent.SoldOut(p.id, ctx.tick.value)

        val day = StayDay(
            tick = t, roomsAvailable = available, roomsSold = occupied, cancelled = cancelled, noShows = noShows, walked = walked,
            roomRevenue = Money(guestRevenue + operatorRevenue), ancillaryRevenue = ancillary, commissions = Money(commissions),
            walkCost = Money(walkCost), operatingCost = operating, wageCost = wages, levy = levy, guests = guests.toInt(),
            satisfactionPpm = satisfaction, staffNeeded = needed, staffHad = had,
        )
        p.pushDay(day)
        p.serviceLevelPpm = service
        p.lifetimeRevenue += day.totalRevenue
        p.lifetimeRoomNights += occupied
        p.lifetimeWalked += walked
        p.month.add(day, Money(guestRevenue), Money(operatorRevenue), dest.domesticSharePpm)
        wear(p, available, occupied)
        settleAllotments(p)
        return StayResult(day, guestsBySegment, Money(guestRevenue), Money(operatorRevenue))
    }

    /** Wear grows with use; routine upkeep repairs most of it, so only renovation brings a building back to new. */
    private fun wear(p: Property, available: Int, occupied: Int) {
        val occupancy = if (available <= 0) 0L else occupied.toLong() * 1_000_000L / available
        val wear = mulDiv(ctx.params.wearPerNightPpm, 200_000L + occupancy, 1_000_000L)
        val repaired = mulDiv(wear, ctx.params.upkeepShare, 1_000_000L)
        p.conditionPpm = (p.conditionPpm - (wear - repaired)).coerceIn(0L, 1_000_000L)
    }

    /** Books the night against each running allotment: what the operator sold, what it owes for the commitment. */
    private fun settleAllotments(p: Property) {
        val t = ctx.tick.value
        for (a in state.allotmentsOfProperty(p.id)) {
            if (a.status != AllotmentStatus.ACTIVE || !a.covers(t)) { a.forgetBefore(t); continue }
            val sold = a.soldOn(t)
            a.monthSold += sold
            a.lifetimeSold += sold
            a.monthSoldValue += a.netRate * sold.toLong()
            val short = AllotmentRules.shortfallRooms(a, sold)
            if (short > 0) {
                val value = a.netRate * short.toLong()
                a.monthShortfallRooms += short
                a.monthShortfallValue += value
                a.lifetimeShortfall += value
                ctx.events += TourismEvent.AllotmentShort(a.id, short, t)
            }
            a.monthReleased += (a.roomsPerNight - sold - a.committedRooms).coerceAtLeast(0)
            state.operator(a.operator)?.let { op ->
                val margin = AllotmentRules.packagePrice(a.netRate, op.marginPpm) - a.netRate
                op.monthRooms += sold
                op.lifetimeRooms += sold
                op.monthMargin += margin * sold.toLong()
                op.lifetimeMargin += margin * sold.toLong()
            }
            a.forgetBefore(t)
        }
    }
}
