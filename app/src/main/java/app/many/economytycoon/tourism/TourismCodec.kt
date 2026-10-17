package app.many.economytycoon.tourism

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.world.ClimateZone
import app.many.economytycoon.world.Point

/**
 * Binary save format of the tourism section. It starts with a version number and a reader refuses versions it does not
 * know. New fields go at the end of a record under a new version, never in the middle.
 */
object TourismCodec {
    const val VERSION = 1

    fun write(s: TourismState, out: ByteWriter) {
        out.writeVarInt(VERSION)
        out.writeVarLong(s.lastTick); out.writeVarLong(s.lastSettledMonth)
        val c = s.counters
        out.writeVarLong(c.nextDestination); out.writeVarLong(c.nextOrigin); out.writeVarLong(c.nextProperty); out.writeVarLong(c.nextAttraction)
        out.writeVarLong(c.nextOperator); out.writeVarLong(c.nextAllotment); out.writeVarLong(c.nextIntermediary); out.writeVarLong(c.nextShock)
        out.writeVarInt(s.destinationMap.size); for (d in s.destinationMap.values) writeDestination(d, out)
        out.writeVarInt(s.originMap.size); for (o in s.originMap.values) writeOrigin(o, out)
        out.writeVarInt(s.intermediaryMap.size); for (i in s.intermediaryMap.values) writeIntermediary(i, out)
        out.writeVarInt(s.propertyMap.size); for (p in s.propertyMap.values) writeProperty(p, out)
        out.writeVarInt(s.attractionMap.size); for (a in s.attractionMap.values) writeAttraction(a, out)
        out.writeVarInt(s.operatorMap.size); for (o in s.operatorMap.values) writeOperator(o, out)
        out.writeVarInt(s.allotmentMap.size); for (a in s.allotmentMap.values) writeAllotment(a, out)
        out.writeVarInt(s.shockMap.size); for (x in s.shockMap.values) writeShock(x, out)
    }

    fun read(r: ByteReader, s: TourismState) {
        try {
            readSection(r, s)
        } catch (e: IllegalArgumentException) {
            throw ByteReader.CorruptDataException("invalid tourism data: ${e.message}")
        }
    }

    private fun readSection(r: ByteReader, s: TourismState) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported tourism save version $version")
        s.clear()
        s.lastTick = r.readVarLong(); s.lastSettledMonth = r.readVarLong()
        val c = s.counters
        c.nextDestination = r.readVarLong(); c.nextOrigin = r.readVarLong(); c.nextProperty = r.readVarLong(); c.nextAttraction = r.readVarLong()
        c.nextOperator = r.readVarLong(); c.nextAllotment = r.readVarLong(); c.nextIntermediary = r.readVarLong(); c.nextShock = r.readVarLong()
        repeat(r.readVarInt()) { val d = readDestination(r); s.destinationMap[d.id] = d }
        repeat(r.readVarInt()) { val o = readOrigin(r); s.originMap[o.id] = o }
        repeat(r.readVarInt()) { val i = readIntermediary(r); s.intermediaryMap[i.id] = i }
        repeat(r.readVarInt()) { val p = readProperty(r); s.propertyMap[p.id] = p }
        repeat(r.readVarInt()) { val a = readAttraction(r); s.attractionMap[a.id] = a }
        repeat(r.readVarInt()) { val o = readOperator(r); s.operatorMap[o.id] = o }
        repeat(r.readVarInt()) { val a = readAllotment(r); s.allotmentMap[a.id] = a }
        repeat(r.readVarInt()) { val x = readShock(r); s.shockMap[x.id] = x }
    }

    /** Convenience for tests and tools: the section as bytes. */
    fun toBytes(s: TourismState): ByteArray = ByteWriter().also { write(s, it) }.toByteArray()

    fun fromBytes(bytes: ByteArray): TourismState = TourismState().also { read(ByteReader(bytes), it) }

    private fun <T> pick(all: Array<T>, index: Int, what: String): T = all.getOrNull(index) ?: throw ByteReader.CorruptDataException("unknown $what $index")

    private fun optCompany(r: ByteReader): CompanyId? = r.readVarLong().let { if (it < 0L) null else CompanyId(it) }
    private fun writeOpt(out: ByteWriter, v: Long?) = out.writeVarLong(v ?: -1L)

    // ----- destinations ------------------------------------------------------------------------

    private fun writeDestination(d: Destination, out: ByteWriter) {
        out.writeVarLong(d.id.value); out.writeString(d.name); out.writeVarLong(d.city.value); out.writeVarLong(d.region.value); out.writeVarLong(d.country.value)
        out.writeVarInt(d.climate.ordinal); out.writeVarInt(d.location.x); out.writeVarInt(d.location.y); out.writeBoolean(d.coastal)
        val dr = d.drivers
        out.writeVarLong(dr.infrastructurePpm); out.writeVarLong(dr.safetyPpm); out.writeVarLong(dr.culturePpm); out.writeVarLong(dr.naturePpm); out.writeVarLong(dr.nightlifePpm)
        out.writeVarLong(d.basePriceLevelPpm); out.writeVarLong(d.priceLevelPpm); out.writeVarLong(d.reputationPpm)
        for (m in d.marketingPpm) out.writeVarLong(m)
        out.writeMoney(d.levyPerGuestNight); out.writeVarLong(d.levyRevenuePpm); out.writeVarLong(d.vatPpm)
        out.writeVarInt(d.festivalMonth); out.writeVarInt(d.festivalDays); out.writeVarLong(d.domesticSharePpm); out.writeVarLong(d.unmetPpm)
        out.writeVarInt(d.demandMap.size); for ((k, v) in d.demandMap) { out.writeVarLong(k); out.writeVarLong(v) }
        out.writeVarInt(d.deniedMap.size); for ((k, v) in d.deniedMap) { out.writeVarLong(k); out.writeVarLong(v) }
        out.writeVarInt(d.dayList.size)
        for (x in d.dayList) {
            out.writeVarLong(x.tick); out.writeVarLong(x.arrivals); out.writeVarLong(x.roomNightDemand); out.writeVarLong(x.roomsSold); out.writeVarLong(x.roomsAvailable)
            out.writeVarLong(x.deniedRoomNights); out.writeVarLong(x.walkedGuests); out.writeMoney(x.roomRevenue); out.writeVarLong(x.guestsPresent)
            out.writeVarLong(x.attractionVisits); out.writeVarLong(x.appealPpm)
        }
        out.writeVarInt(d.monthList.size)
        for (m in d.monthList) {
            out.writeVarLong(m.month); out.writeVarLong(m.arrivals); out.writeVarLong(m.roomsSold); out.writeVarLong(m.roomsAvailable)
            out.writeVarLong(m.deniedRoomNights); out.writeMoney(m.roomRevenue)
        }
    }

    private fun readDestination(r: ByteReader): Destination {
        val id = DestinationId(r.readVarLong()); val name = r.readString()
        val d = Destination(
            id, name, CityId(r.readVarLong()), RegionId(r.readVarLong()), CountryId(r.readVarLong()),
            pick(ClimateZone.values(), r.readVarInt(), "climate zone"), Point(r.readVarInt(), r.readVarInt()), r.readBoolean(),
        )
        d.drivers = DestinationDrivers(r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong())
        d.setPriceLevel(r.readVarLong(), r.readVarLong())
        d.reputationPpm = r.readVarLong()
        for (i in d.marketingPpm.indices) d.marketingPpm[i] = r.readVarLong()
        d.setTaxes(r.readMoney(), r.readVarLong(), r.readVarLong())
        d.setFestival(r.readVarInt(), r.readVarInt())
        d.domesticSharePpm = r.readVarLong(); d.unmetPpm = r.readVarLong()
        repeat(r.readVarInt()) { d.demandMap[r.readVarLong()] = r.readVarLong() }
        repeat(r.readVarInt()) { d.deniedMap[r.readVarLong()] = r.readVarLong() }
        repeat(r.readVarInt()) {
            d.dayList += DestinationDay(
                r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(),
                r.readMoney(), r.readVarLong(), r.readVarLong(), r.readVarLong(),
            )
        }
        repeat(r.readVarInt()) { d.monthList += DestinationMonth(r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readMoney()) }
        return d
    }

    // ----- markets and intermediaries ----------------------------------------------------------

    private fun writeOrigin(o: OriginMarket, out: ByteWriter) {
        out.writeVarLong(o.id.value); out.writeString(o.name); writeOpt(out, o.country?.value)
        out.writeVarInt(o.location.x); out.writeVarInt(o.location.y); out.writeVarLong(o.outboundPerDay); out.writeVarLong(o.incomePpm)
        for (m in o.mixPpm) out.writeVarLong(m)
    }

    private fun readOrigin(r: ByteReader): OriginMarket {
        val id = OriginId(r.readVarLong()); val name = r.readString()
        val country = r.readVarLong().let { if (it < 0L) null else CountryId(it) }
        val o = OriginMarket(id, name, country, Point(r.readVarInt(), r.readVarInt()))
        val outbound = r.readVarLong(); val income = r.readVarLong()
        val mix = LongArray(VisitorSegment.COUNT) { r.readVarLong() }
        o.configure(outbound, income, mix)
        return o
    }

    private fun writeIntermediary(i: Intermediary, out: ByteWriter) {
        out.writeVarLong(i.id.value); out.writeVarInt(i.kind.ordinal); writeOpt(out, i.owner?.value); out.writeString(i.name)
        out.writeVarLong(i.commissionPpm); out.writeVarLong(i.reachPpm); out.writeMoney(i.listingFeePerMonth)
        out.writeMoney(i.lifetimeCommission); out.writeVarLong(i.lifetimeRooms); out.writeMoney(i.monthCommission); out.writeVarLong(i.monthRooms)
    }

    private fun readIntermediary(r: ByteReader): Intermediary {
        val i = Intermediary(IntermediaryId(r.readVarLong()), pick(IntermediaryKind.values(), r.readVarInt(), "intermediary kind"), optCompany(r), r.readString())
        i.commissionPpm = r.readVarLong(); i.reachPpm = r.readVarLong(); i.listingFeePerMonth = r.readMoney()
        i.lifetimeCommission = r.readMoney(); i.lifetimeRooms = r.readVarLong(); i.monthCommission = r.readMoney(); i.monthRooms = r.readVarLong()
        return i
    }

    // ----- properties --------------------------------------------------------------------------

    private fun writeReviews(b: ReviewBook, out: ByteWriter) {
        out.writeVarLong(b.scorePpm); out.writeVarLong(b.volume); out.writeVarLong(b.reputationPpm)
        out.writeVarInt(b.recentList.size); for (v in b.recentList) out.writeVarLong(v)
    }

    private fun readReviews(r: ByteReader, b: ReviewBook) {
        val score = r.readVarLong(); val volume = r.readVarLong(); val rep = r.readVarLong()
        b.restore(score, volume, rep, List(r.readVarInt()) { r.readVarLong() })
    }

    private fun writeNight(nb: NightBook, out: ByteWriter) {
        for (v in nb.booked) out.writeVarInt(v)
        for (v in nb.revenueRaw) out.writeVarLong(v)
        for (v in nb.bySegment) out.writeVarInt(v)
        for (v in nb.byChannel) out.writeVarInt(v)
        for (v in nb.operatorRooms) out.writeVarInt(v)
        for (v in nb.operatorRevenueRaw) out.writeVarLong(v)
        out.writeVarInt(nb.commissionRaw.size); for ((k, v) in nb.commissionRaw) { out.writeVarLong(k); out.writeVarLong(v) }
        out.writeVarInt(nb.roomsVia.size); for ((k, v) in nb.roomsVia) { out.writeVarLong(k); out.writeVarInt(v) }
    }

    private fun readNight(r: ByteReader): NightBook {
        val nb = NightBook()
        for (i in nb.booked.indices) nb.booked[i] = r.readVarInt()
        for (i in nb.revenueRaw.indices) nb.revenueRaw[i] = r.readVarLong()
        for (i in nb.bySegment.indices) nb.bySegment[i] = r.readVarInt()
        for (i in nb.byChannel.indices) nb.byChannel[i] = r.readVarInt()
        for (i in nb.operatorRooms.indices) nb.operatorRooms[i] = r.readVarInt()
        for (i in nb.operatorRevenueRaw.indices) nb.operatorRevenueRaw[i] = r.readVarLong()
        repeat(r.readVarInt()) { nb.commissionRaw[r.readVarLong()] = r.readVarLong() }
        repeat(r.readVarInt()) { nb.roomsVia[r.readVarLong()] = r.readVarInt() }
        return nb
    }

    private fun writeProperty(p: Property, out: ByteWriter) {
        out.writeVarLong(p.id.value); out.writeVarLong(p.destination.value); writeOpt(out, p.owner?.value); writeOpt(out, p.facility?.value)
        out.writeVarInt(p.kind.ordinal); out.writeString(p.name); out.writeVarInt(p.stars)
        for (v in p.rooms) out.writeVarInt(v)
        val st = p.settings
        out.writeVarInt(st.policy.ordinal); out.writeMoney(st.rackRate); out.writeVarLong(st.floorPpm); out.writeVarLong(st.ceilingPpm)
        out.writeVarLong(st.aggressionPpm); out.writeVarLong(st.targetOccupancyPpm)
        out.writeVarLong(p.overbookPpm); out.writeVarInt(p.cancellation.ordinal); out.writeVarLong(p.conditionPpm)
        out.writeVarInt(p.status.ordinal); out.writeVarLong(p.opensAt); out.writeVarLong(p.openedAt); out.writeVarLong(p.serviceLevelPpm)
        out.writeMoney(p.lifetimeRevenue); out.writeVarLong(p.lifetimeRoomNights); out.writeVarLong(p.lifetimeWalked); out.writeMoney(p.invested)
        writeReviews(p.reviews, out)
        out.writeVarInt(p.listed.size); for (i in p.listed) out.writeVarLong(i.value)
        out.writeVarInt(p.bookMap.size); for ((k, v) in p.bookMap) { out.writeVarLong(k); writeNight(v, out) }
        out.writeVarInt(p.dayList.size)
        for (d in p.dayList) {
            out.writeVarLong(d.tick); out.writeVarInt(d.roomsAvailable); out.writeVarInt(d.roomsSold); out.writeVarInt(d.cancelled); out.writeVarInt(d.noShows)
            out.writeVarInt(d.walked); out.writeMoney(d.roomRevenue); out.writeMoney(d.ancillaryRevenue); out.writeMoney(d.commissions)
            out.writeMoney(d.walkCost); out.writeMoney(d.operatingCost); out.writeMoney(d.wageCost); out.writeMoney(d.levy)
            out.writeVarInt(d.guests); out.writeVarLong(d.satisfactionPpm); out.writeVarInt(d.staffNeeded); out.writeVarInt(d.staffHad)
        }
        val m = p.month
        out.writeMoney(m.guestRoomRevenue); out.writeMoney(m.operatorRoomRevenue); out.writeMoney(m.ancillaryRevenue); out.writeMoney(m.domesticRevenue)
        out.writeMoney(m.commissions); out.writeMoney(m.walkCost); out.writeMoney(m.operatingCost); out.writeMoney(m.wageCost); out.writeMoney(m.levy)
        out.writeVarLong(m.roomsSold); out.writeVarLong(m.roomsAvailable)
        out.writeVarInt(m.commissionBy.size); for ((k, v) in m.commissionBy) { out.writeVarLong(k.value); out.writeMoney(v) }
    }

    private fun readProperty(r: ByteReader): Property {
        val id = PropertyId(r.readVarLong()); val dest = DestinationId(r.readVarLong())
        val owner = optCompany(r); val facility = r.readVarLong().let { if (it < 0L) null else FacilityId(it) }
        val kind = pick(PropertyKind.values(), r.readVarInt(), "property kind")
        val p = Property(id, dest, owner, facility, kind, r.readString())
        p.stars = r.readVarInt()
        for (i in p.rooms.indices) p.rooms[i] = r.readVarInt()
        p.settings = RateSettings(pick(RatePolicy.values(), r.readVarInt(), "rate policy"), r.readMoney(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong())
        p.overbookPpm = r.readVarLong(); p.cancellation = pick(CancellationPolicy.values(), r.readVarInt(), "cancellation policy"); p.conditionPpm = r.readVarLong()
        p.status = pick(PropertyStatus.values(), r.readVarInt(), "property status"); p.opensAt = r.readVarLong(); p.openedAt = r.readVarLong(); p.serviceLevelPpm = r.readVarLong()
        p.lifetimeRevenue = r.readMoney(); p.lifetimeRoomNights = r.readVarLong(); p.lifetimeWalked = r.readVarLong(); p.invested = r.readMoney()
        readReviews(r, p.reviews)
        repeat(r.readVarInt()) { p.listed += IntermediaryId(r.readVarLong()) }
        repeat(r.readVarInt()) { p.bookMap[r.readVarLong()] = readNight(r) }
        repeat(r.readVarInt()) {
            p.dayList += StayDay(
                r.readVarLong(), r.readVarInt(), r.readVarInt(), r.readVarInt(), r.readVarInt(), r.readVarInt(), r.readMoney(), r.readMoney(), r.readMoney(),
                r.readMoney(), r.readMoney(), r.readMoney(), r.readMoney(), r.readVarInt(), r.readVarLong(), r.readVarInt(), r.readVarInt(),
            )
        }
        val m = p.month
        m.guestRoomRevenue = r.readMoney(); m.operatorRoomRevenue = r.readMoney(); m.ancillaryRevenue = r.readMoney(); m.domesticRevenue = r.readMoney()
        m.commissions = r.readMoney(); m.walkCost = r.readMoney(); m.operatingCost = r.readMoney(); m.wageCost = r.readMoney(); m.levy = r.readMoney()
        m.roomsSold = r.readVarLong(); m.roomsAvailable = r.readVarLong()
        repeat(r.readVarInt()) { m.commissionBy[IntermediaryId(r.readVarLong())] = r.readMoney() }
        return p
    }

    // ----- attractions, operators, allotments, shocks -----------------------------------------

    private fun writeAttraction(a: Attraction, out: ByteWriter) {
        out.writeVarLong(a.id.value); out.writeVarLong(a.destination.value); writeOpt(out, a.owner?.value); writeOpt(out, a.facility?.value)
        out.writeVarInt(a.kind.ordinal); out.writeString(a.name)
        out.writeVarInt(a.capacityPerDay); out.writeMoney(a.ticketPrice); out.writeVarLong(a.qualityPpm); out.writeBoolean(a.open)
        out.writeVarLong(a.opensAt); out.writeVarLong(a.queueSatisfactionPpm); out.writeVarLong(a.lifetimeVisits); out.writeMoney(a.lifetimeRevenue)
        out.writeMoney(a.invested); out.writeMoney(a.monthRevenue); out.writeMoney(a.monthCost); out.writeVarLong(a.monthVisits)
        writeReviews(a.reviews, out)
        out.writeVarInt(a.dayList.size)
        for (d in a.dayList) {
            out.writeVarLong(d.tick); out.writeVarLong(d.demand); out.writeVarLong(d.visits); out.writeVarLong(d.turnedAway); out.writeVarInt(d.waitMinutes)
            out.writeMoney(d.revenue); out.writeMoney(d.cost); out.writeVarLong(d.satisfactionPpm)
        }
    }

    private fun readAttraction(r: ByteReader): Attraction {
        val id = AttractionId(r.readVarLong()); val dest = DestinationId(r.readVarLong())
        val owner = optCompany(r); val facility = r.readVarLong().let { if (it < 0L) null else FacilityId(it) }
        val a = Attraction(id, dest, owner, facility, pick(AttractionKind.values(), r.readVarInt(), "attraction kind"), r.readString())
        a.capacityPerDay = r.readVarInt(); a.ticketPrice = r.readMoney(); a.qualityPpm = r.readVarLong(); a.open = r.readBoolean()
        a.opensAt = r.readVarLong(); a.queueSatisfactionPpm = r.readVarLong(); a.lifetimeVisits = r.readVarLong(); a.lifetimeRevenue = r.readMoney()
        a.invested = r.readMoney(); a.monthRevenue = r.readMoney(); a.monthCost = r.readMoney(); a.monthVisits = r.readVarLong()
        readReviews(r, a.reviews)
        repeat(r.readVarInt()) {
            a.dayList += AttractionDay(r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarInt(), r.readMoney(), r.readMoney(), r.readVarLong())
        }
        return a
    }

    private fun writeOperator(o: TourOperator, out: ByteWriter) {
        out.writeVarLong(o.id.value); writeOpt(out, o.owner?.value); out.writeString(o.name); out.writeVarLong(o.strengthPpm); out.writeVarLong(o.marginPpm)
        out.writeVarLong(o.lifetimeRooms); out.writeMoney(o.lifetimeMargin); out.writeVarLong(o.monthRooms); out.writeMoney(o.monthMargin)
    }

    private fun readOperator(r: ByteReader): TourOperator {
        val o = TourOperator(OperatorId(r.readVarLong()), optCompany(r), r.readString())
        o.strengthPpm = r.readVarLong(); o.marginPpm = r.readVarLong()
        o.lifetimeRooms = r.readVarLong(); o.lifetimeMargin = r.readMoney(); o.monthRooms = r.readVarLong(); o.monthMargin = r.readMoney()
        return o
    }

    private fun writeAllotment(a: Allotment, out: ByteWriter) {
        out.writeVarLong(a.id.value); out.writeVarLong(a.operator.value); out.writeVarLong(a.property.value); out.writeVarInt(a.room.ordinal)
        out.writeVarInt(a.roomsPerNight); out.writeMoney(a.netRate); out.writeVarLong(a.startsAt.value); out.writeVarLong(a.endsAt.value)
        out.writeVarInt(a.releaseDays); out.writeVarLong(a.committedPpm); out.writeVarInt(a.proposedBy.ordinal); out.writeVarLong(a.proposedAt.value)
        out.writeVarInt(a.status.ordinal); out.writeVarLong(a.endedAt)
        out.writeVarInt(a.soldMap.size); for ((k, v) in a.soldMap) { out.writeVarLong(k); out.writeVarInt(v) }
        out.writeVarLong(a.monthSold); out.writeMoney(a.monthSoldValue); out.writeVarLong(a.monthShortfallRooms); out.writeMoney(a.monthShortfallValue)
        out.writeVarLong(a.monthReleased); out.writeVarLong(a.lifetimeSold); out.writeMoney(a.lifetimeShortfall)
        out.writeVarInt(a.terminatedBy?.ordinal ?: -1); out.writeMoney(a.terminationFee)
    }

    private fun readAllotment(r: ByteReader): Allotment {
        val id = AllotmentId(r.readVarLong()); val op = OperatorId(r.readVarLong()); val prop = PropertyId(r.readVarLong())
        val room = pick(RoomClass.values(), r.readVarInt(), "room class")
        val a = Allotment(
            id, op, prop, room, r.readVarInt(), r.readMoney(), Tick(r.readVarLong()), Tick(r.readVarLong()), r.readVarInt(), r.readVarLong(),
            pick(ContractSide.values(), r.readVarInt(), "contract side"), Tick(r.readVarLong()),
        )
        a.status = pick(AllotmentStatus.values(), r.readVarInt(), "allotment status"); a.endedAt = r.readVarLong()
        repeat(r.readVarInt()) { a.soldMap[r.readVarLong()] = r.readVarInt() }
        a.monthSold = r.readVarLong(); a.monthSoldValue = r.readMoney(); a.monthShortfallRooms = r.readVarLong(); a.monthShortfallValue = r.readMoney()
        a.monthReleased = r.readVarLong(); a.lifetimeSold = r.readVarLong(); a.lifetimeShortfall = r.readMoney()
        a.terminatedBy = r.readVarInt().let { if (it < 0) null else pick(ContractSide.values(), it, "contract side") }
        a.terminationFee = r.readMoney()
        return a
    }

    private fun writeShock(x: Shock, out: ByteWriter) {
        out.writeVarLong(x.id.value); out.writeVarInt(x.kind.ordinal); writeOpt(out, x.destination?.value); writeOpt(out, x.origin?.value)
        out.writeVarLong(x.start.value); out.writeVarInt(x.durationDays); out.writeVarLong(x.severityPpm); out.writeString(x.label)
    }

    private fun readShock(r: ByteReader): Shock {
        val id = ShockId(r.readVarLong()); val kind = pick(ShockKind.values(), r.readVarInt(), "shock kind")
        val dest = r.readVarLong().let { if (it < 0L) null else DestinationId(it) }
        val origin = r.readVarLong().let { if (it < 0L) null else OriginId(it) }
        return Shock(id, kind, dest, origin, Tick(r.readVarLong()), r.readVarInt(), r.readVarLong(), r.readString())
    }
}
