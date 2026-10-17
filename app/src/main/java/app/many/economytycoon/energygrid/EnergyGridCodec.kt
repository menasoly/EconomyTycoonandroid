package app.many.economytycoon.energygrid

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.world.ClimateZone

/**
 * Binary save format of the energy grid section. The layout starts with a version number; a reader refuses versions
 * it does not know. New fields are added at the end of a record under a new version, never in the middle.
 */
object EnergyGridCodec {
    const val VERSION = 1

    fun write(s: EnergyGridState, out: ByteWriter) {
        out.writeVarInt(VERSION)
        out.writeVarLong(s.lastTick); out.writeVarLong(s.lastBilledMonth)
        val c = s.counters
        out.writeVarLong(c.nextGrid); out.writeVarLong(c.nextUnit); out.writeVarLong(c.nextLoad); out.writeVarLong(c.nextPpa); out.writeVarLong(c.nextLink); out.writeVarLong(c.nextBlackout)
        out.writeVarInt(s.gridMap.size)
        for (g in s.gridMap.values) writeGrid(g, out)
        out.writeVarInt(s.linkMap.size)
        for (l in s.linkMap.values) {
            out.writeVarLong(l.id.value); out.writeVarLong(l.a.value); out.writeVarLong(l.b.value)
            out.writeVarLong(l.capacityKw); out.writeVarLong(l.lossPpm); out.writeMoney(l.wheelingPerMwh)
            out.writeBoolean(l.inService); out.writeVarLong(l.sentAbKwh); out.writeVarLong(l.sentBaKwh); out.writeVarLong(l.lostKwh); out.writeVarInt(l.congestedHours)
        }
        out.writeVarInt(s.ppaMap.size)
        for (p in s.ppaMap.values) writePpa(p, out)
        out.writeVarInt(s.plantMap.size)
        for ((f, u) in s.plantMap) { out.writeVarLong(f.value); out.writeVarLong(u.value) }
    }

    fun read(r: ByteReader, s: EnergyGridState) {
        try {
            readSection(r, s)
        } catch (e: IllegalArgumentException) {
            throw ByteReader.CorruptDataException("invalid energy grid data: ${e.message}")
        }
    }

    private fun readSection(r: ByteReader, s: EnergyGridState) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported energy grid save version $version")
        s.clear()
        s.lastTick = r.readVarLong(); s.lastBilledMonth = r.readVarLong()
        val c = s.counters
        c.nextGrid = r.readVarLong(); c.nextUnit = r.readVarLong(); c.nextLoad = r.readVarLong(); c.nextPpa = r.readVarLong(); c.nextLink = r.readVarLong(); c.nextBlackout = r.readVarLong()
        repeat(r.readVarInt()) { val g = readGrid(r); s.gridMap[g.id] = g }
        repeat(r.readVarInt()) {
            val l = Interconnector(LinkId(r.readVarLong()), GridId(r.readVarLong()), GridId(r.readVarLong()), r.readVarLong(), r.readVarLong(), r.readMoney())
            l.restore(l.capacityKw, r.readBoolean(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarInt())
            s.linkMap[l.id] = l
        }
        repeat(r.readVarInt()) { val p = readPpa(r); s.ppaMap[p.id] = p }
        repeat(r.readVarInt()) { s.plantMap[FacilityId(r.readVarLong())] = UnitId(r.readVarLong()) }
    }

    /** Convenience for tests and tools: the section as bytes. */
    fun toBytes(s: EnergyGridState): ByteArray = ByteWriter().also { write(s, it) }.toByteArray()

    fun fromBytes(bytes: ByteArray): EnergyGridState = EnergyGridState().also { read(ByteReader(bytes), it) }

    private fun writeGrid(g: Grid, out: ByteWriter) {
        out.writeVarLong(g.id.value); out.writeVarLong(g.region.value); out.writeVarLong(g.country.value); out.writeString(g.name); out.writeVarInt(g.climate.ordinal)
        val p = g.params
        out.writeMoney(p.priceCap); out.writeMoney(p.priceFloor); out.writeVarLong(p.scarcityThresholdPpm); out.writeMoney(p.valueOfLostLoad)
        out.writeVarLong(p.targetReservePpm); out.writeVarLong(p.growthPpm); out.writeVarInt(p.buildableMask); out.writeBoolean(p.operatorPlans); out.writeBoolean(p.compensateCap)
        val t = g.tariff
        out.writeVarLong(t.passThroughPpm); out.writeMoney(t.retailMarginPerMwh); out.writeMoney(t.networkChargePerMwh); out.writeMoney(t.fixedChargePerKwMonth)
        out.writeVarLong(t.interruptibleDiscountPpm); out.writeBoolean(t.householdCapPerMwh != null); t.householdCapPerMwh?.let { out.writeMoney(it) }
        out.writeVarLong(g.windCarryPpm); out.writeMoney(g.capacityLevy); out.writeMoney(g.capShortfall); out.writeVarLong(g.lastPlanTick)
        out.writeVarLong(g.monthDemandKwh); out.writeVarLong(g.lifetimeUnservedKwh)

        out.writeVarInt(g.unitMap.size)
        for (u in g.unitMap.values) {
            out.writeVarLong(u.id.value); out.writeVarInt(u.tech.ordinal); out.writeString(u.name); out.writeBoolean(u.owner != null); u.owner?.let { out.writeVarLong(it.value) }
            out.writeVarInt(u.status.ordinal); out.writeVarLong(u.statusUntil); out.writeVarLong(u.commissioned); out.writeVarLong(u.lastMaintenance)
            out.writeVarInt(u.deferrals); out.writeVarLong(u.outputKw); out.writeVarLong(u.efficiencyPpm); out.writeVarLong(u.socKwh)
            out.writeVarLong(u.soldKwh); out.writeMoney(u.poolRevenue); out.writeVarLong(u.lifetimeKwh); out.writeVarInt(u.outageCount); out.writeVarLong(u.capacityKw)
        }
        out.writeVarInt(g.loadMap.size)
        for (l in g.loadMap.values) {
            out.writeVarLong(l.id.value); out.writeVarInt(l.kind.ordinal); out.writeVarInt(l.customer.ordinal)
            out.writeBoolean(l.company != null); l.company?.let { out.writeVarLong(it.value) }
            out.writeBoolean(l.facility != null); l.facility?.let { out.writeVarLong(it.value) }
            out.writeVarLong(l.baseKw); out.writeBoolean(l.connected); out.writeVarLong(l.monthServedKwh); out.writeVarLong(l.monthShedKwh)
            out.writeVarLong(l.lifetimeShedKwh); out.writeVarInt(l.shedHoursToday)
        }
        out.writeVarInt(g.historyList.size)
        for (d in g.historyList) writeDay(d, out)
        out.writeVarInt(g.lastDay.size)
        for (h in g.lastDay) writeHour(h, out)
        out.writeVarInt(g.blackoutList.size)
        for (b in g.blackoutList) {
            out.writeVarLong(b.id.value); out.writeVarLong(b.startTick); out.writeVarInt(b.startHour); out.writeVarInt(b.hours)
            out.writeVarLong(b.peakShedKw); out.writeVarLong(b.shedKwh); out.writeBoolean(b.open); out.writeVarLong(b.endTick); out.writeMoney(b.damage)
            out.writeVarInt(b.affectedLoads.size); for (l in b.affectedLoads) out.writeVarLong(l.value)
        }
        out.writeVarInt(g.outageList.size)
        for (o in g.outageList) { out.writeVarLong(o.unit.value); out.writeVarInt(o.kind.ordinal); out.writeVarLong(o.start); out.writeVarLong(o.until) }
        val round = g.capacityRound
        out.writeBoolean(round != null)
        if (round != null) {
            out.writeVarLong(round.heldAt); out.writeVarLong(round.requirementKw); out.writeMoney(round.clearingPrice); out.writeVarLong(round.shortfallKw)
            out.writeVarInt(round.awards.size); for (a in round.awards) { out.writeVarLong(a.unit.value); out.writeVarLong(a.kw) }
        }
    }

    private fun readGrid(r: ByteReader): Grid {
        val id = GridId(r.readVarLong())
        val region = RegionId(r.readVarLong())
        val country = CountryId(r.readVarLong())
        val name = r.readString()
        val climate = pick(ClimateZone.values(), r.readVarInt(), "climate zone")
        val params = GridParams(
            r.readMoney(), r.readMoney(), r.readVarLong(), r.readMoney(), r.readVarLong(), r.readVarLong(), r.readVarInt(), r.readBoolean(), r.readBoolean(),
        )
        val g = Grid(id, region, country, name, climate, params)
        val passThrough = r.readVarLong(); val margin = r.readMoney(); val network = r.readMoney(); val fixed = r.readMoney(); val discount = r.readVarLong()
        val cap = if (r.readBoolean()) r.readMoney() else null
        g.tariff = TariffSchedule(passThrough, margin, network, fixed, discount, cap)
        g.windCarryPpm = r.readVarLong(); g.capacityLevy = r.readMoney(); g.capShortfall = r.readMoney(); g.lastPlanTick = r.readVarLong()
        g.monthDemandKwh = r.readVarLong(); g.lifetimeUnservedKwh = r.readVarLong()

        repeat(r.readVarInt()) {
            val uid = UnitId(r.readVarLong())
            val tech = pick(Technology.values(), r.readVarInt(), "technology")
            val uname = r.readString()
            val owner = if (r.readBoolean()) CompanyId(r.readVarLong()) else null
            val status = pick(UnitStatus.values(), r.readVarInt(), "unit status")
            val until = r.readVarLong(); val commissioned = r.readVarLong(); val lastMaintenance = r.readVarLong()
            val deferrals = r.readVarInt(); val output = r.readVarLong(); val efficiency = r.readVarLong(); val soc = r.readVarLong()
            val sold = r.readVarLong(); val revenue = r.readMoney(); val lifetime = r.readVarLong(); val outages = r.readVarInt(); val capacity = r.readVarLong()
            val u = GenUnit(uid, id, tech, uname, capacity, owner)
            u.restore(status, until, commissioned, lastMaintenance, deferrals, output, efficiency, soc, sold, revenue, lifetime, outages, capacity)
            g.unitMap[uid] = u
        }
        repeat(r.readVarInt()) {
            val lid = LoadId(r.readVarLong())
            val kind = pick(LoadKind.values(), r.readVarInt(), "load kind")
            val customer = pick(CustomerClass.values(), r.readVarInt(), "customer class")
            val company = if (r.readBoolean()) CompanyId(r.readVarLong()) else null
            val facility = if (r.readBoolean()) FacilityId(r.readVarLong()) else null
            val l = GridLoad(lid, id, kind, customer, company, facility)
            l.restore(r.readVarLong(), r.readBoolean(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarInt())
            g.loadMap[lid] = l
        }
        repeat(r.readVarInt()) { g.historyList += readDay(r) }
        g.lastDay = List(r.readVarInt()) { readHour(r) }
        repeat(r.readVarInt()) {
            val b = BlackoutRecord(BlackoutId(r.readVarLong()), id, r.readVarLong(), r.readVarInt())
            val hours = r.readVarInt(); val peak = r.readVarLong(); val shed = r.readVarLong(); val open = r.readBoolean(); val end = r.readVarLong(); val damage = r.readMoney()
            val affected = List(r.readVarInt()) { LoadId(r.readVarLong()) }
            b.restore(hours, peak, shed, open, end, damage, affected)
            g.blackoutList += b
        }
        repeat(r.readVarInt()) {
            val unit = UnitId(r.readVarLong())
            val kind = pick(OutageKind.values(), r.readVarInt(), "outage kind")
            g.outageList += OutageEvent(id, unit, kind, r.readVarLong(), r.readVarLong())
        }
        if (r.readBoolean()) {
            val held = r.readVarLong(); val requirement = r.readVarLong(); val price = r.readMoney(); val shortfall = r.readVarLong()
            val awards = List(r.readVarInt()) { CapacityAward(UnitId(r.readVarLong()), r.readVarLong()) }
            g.capacityRound = CapacityRound(held, requirement, awards, price, shortfall)
        }
        return g
    }

    private fun writeDay(d: DaySummary, out: ByteWriter) {
        out.writeVarLong(d.tick); out.writeMoney(d.averagePrice); out.writeMoney(d.peakPrice); out.writeMoney(d.lowPrice)
        out.writeVarLong(d.demandKwh); out.writeVarLong(d.unservedKwh); out.writeVarLong(d.curtailedKwh); out.writeVarLong(d.peakDemandKw)
        for (g in d.generationKwh) out.writeVarLong(g)
        out.writeVarLong(d.importKwh); out.writeVarLong(d.exportKwh); out.writeVarInt(d.shedHours); out.writeVarInt(d.scarcityHours)
        out.writeVarLong(d.carbonKg); out.writeVarLong(d.lowestReservePpm); out.writeMoney(d.lostLoadValue)
    }

    private fun readDay(r: ByteReader): DaySummary {
        val tick = r.readVarLong(); val avg = r.readMoney(); val peak = r.readMoney(); val low = r.readMoney()
        val demand = r.readVarLong(); val unserved = r.readVarLong(); val curtailed = r.readVarLong(); val peakKw = r.readVarLong()
        val gen = List(Technology.values().size) { r.readVarLong() }
        return DaySummary(tick, avg, peak, low, demand, unserved, curtailed, peakKw, gen, r.readVarLong(), r.readVarLong(), r.readVarInt(), r.readVarInt(), r.readVarLong(), r.readVarLong(), r.readMoney())
    }

    private fun writeHour(h: HourProfile, out: ByteWriter) {
        out.writeVarInt(h.hour); out.writeVarLong(h.demandKw); out.writeMoney(h.price); out.writeVarLong(h.unservedKw); out.writeVarLong(h.curtailedKw)
        out.writeVarLong(h.importKw); out.writeVarLong(h.exportKw); out.writeVarLong(h.storageChargeKw); out.writeVarLong(h.storageDischargeKw)
        out.writeVarLong(h.reservePpm); for (o in h.outputKw) out.writeVarLong(o); out.writeVarLong(h.setter)
    }

    private fun readHour(r: ByteReader): HourProfile {
        val hour = r.readVarInt(); val demand = r.readVarLong(); val price = r.readMoney(); val unserved = r.readVarLong(); val curtailed = r.readVarLong()
        val imp = r.readVarLong(); val exp = r.readVarLong(); val charge = r.readVarLong(); val discharge = r.readVarLong(); val reserve = r.readVarLong()
        val out = List(Technology.values().size) { r.readVarLong() }
        return HourProfile(hour, demand, price, unserved, curtailed, imp, exp, charge, discharge, reserve, out, r.readVarLong())
    }

    private fun writePpa(p: Ppa, out: ByteWriter) {
        out.writeVarLong(p.id.value); out.writeVarLong(p.grid.value); out.writeVarLong(p.seller.value); out.writeVarLong(p.buyer.value)
        out.writeVarInt(p.profile.ordinal); out.writeVarLong(p.volumeKw); out.writeVarLong(p.sharePpm)
        out.writeBoolean(p.unit != null); p.unit?.let { out.writeVarLong(it.value) }
        out.writeMoney(p.strikePerMwh); out.writeVarLong(p.startsAt); out.writeVarLong(p.endsAt); out.writeVarLong(p.penaltyPpm); out.writeVarLong(p.offeredAt)
        out.writeVarInt(p.status.ordinal); out.writeVarLong(p.closedAt); out.writeVarLong(p.deliveredKwh); out.writeVarLong(p.shortfallKwh)
        out.writeMoney(p.marketValue); out.writeMoney(p.shortfallMarketValue); out.writeVarLong(p.totalDeliveredKwh); out.writeVarLong(p.totalShortfallKwh)
        out.writeMoney(p.netPaidBySeller); out.writeVarInt(p.daysDelivering)
    }

    private fun readPpa(r: ByteReader): Ppa {
        val id = PpaId(r.readVarLong()); val grid = GridId(r.readVarLong()); val seller = CompanyId(r.readVarLong()); val buyer = CompanyId(r.readVarLong())
        val profile = pick(PpaProfile.values(), r.readVarInt(), "ppa profile"); val volume = r.readVarLong(); val share = r.readVarLong()
        val unit = if (r.readBoolean()) UnitId(r.readVarLong()) else null
        val p = Ppa(id, grid, seller, buyer, profile, volume, share, unit, r.readMoney(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong())
        val status = pick(PpaStatus.values(), r.readVarInt(), "ppa status")
        p.restore(status, r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readMoney(), r.readMoney(), r.readVarLong(), r.readVarLong(), r.readMoney(), r.readVarInt())
        return p
    }

    private fun <T> pick(all: Array<T>, index: Int, what: String): T = all.getOrNull(index) ?: throw ByteReader.CorruptDataException("unknown $what $index")
}
