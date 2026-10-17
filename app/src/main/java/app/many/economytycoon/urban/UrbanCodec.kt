package app.many.economytycoon.urban

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick

/**
 * Binary save format of the urban partition. Counts and ids are varints and money is stored as raw integers. Derived
 * surfaces and the last budget report are not saved: they are recomputed. The format starts with a version byte; an
 * unknown version is refused.
 */
object UrbanCodec {
    const val VERSION = 1

    fun write(s: UrbanState, out: ByteWriter) {
        out.writeVarInt(VERSION)
        out.writeVarLong(s.random.worldSeed)
        out.writeVarInt(s.params.gridSize)
        out.writeVarInt(s.params.blockSize)
        for (v in s.ids.snapshot()) out.writeVarLong(v)
        val streams = s.random.snapshot()
        out.writeVarInt(streams.size)
        for ((k, v) in streams) { out.writeString(k); out.writeVarLong(v) }
        val plans = s.plans()
        out.writeVarInt(plans.size)
        for (p in plans) writePlan(p, out)
    }

    fun read(r: ByteReader, s: UrbanState) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported urban save version $version")
        val seed = r.readVarLong()
        if (seed != s.random.worldSeed) throw ByteReader.CorruptDataException("urban save belongs to another world")
        if (r.readVarInt() != s.params.gridSize || r.readVarInt() != s.params.blockSize) throw ByteReader.CorruptDataException("urban save has another grid")
        s.clear()
        s.ids.restore(LongArray(7) { r.readVarLong() })
        val streams = java.util.TreeMap<String, Long>()
        repeat(r.readVarInt()) { streams[r.readString()] = r.readVarLong() }
        s.random.restore(streams)
        repeat(r.readVarInt()) { s.put(readPlan(r)) }
    }

    // ----- writing --------------------------------------------------------------------------------------------

    private fun writePlan(p: CityPlan, out: ByteWriter) {
        out.writeVarLong(p.city.value); out.writeVarLong(p.country.value); out.writeString(p.name)
        out.writeVarInt(p.geometry.gridSize); out.writeVarInt(p.geometry.blockSize); out.writeVarLong(p.geometry.cellAreaSqm)
        out.writeVarInt(p.districts.size)
        for (d in p.districts) writeDistrict(d, out)
        writeCouncil(p.council, out)
        writeNetwork(p.network, out)
        writeBudget(p.budget, out)
        val petitions = p.petitions.all()
        out.writeVarInt(petitions.size)
        for (x in petitions) writePetition(x, out)
        val efforts = p.petitions.efforts()
        out.writeVarInt(efforts.size)
        for (e in efforts) {
            out.writeVarLong(e.petition.value); out.writeVarLong(e.company.value); out.writeVarInt(e.seat ?: -1)
            out.writeMoney(e.amount); out.writeVarInt(e.points); out.writeVarLong(e.at.value)
        }
        val projects = p.projects.all()
        out.writeVarInt(projects.size)
        for (x in projects) writeProject(x, out)
        val concessions = p.concessions.all()
        out.writeVarInt(concessions.size)
        for (x in concessions) writeConcession(x, out)
        val events = p.events.all()
        out.writeVarInt(events.size)
        for (e in events) {
            out.writeVarLong(e.id.value); out.writeVarInt(e.kind.ordinal); out.writeVarInt(e.district)
            out.writeVarLong(e.startedAt.value); out.writeVarLong(e.endsAt.value); out.writeVarInt(e.magnitudePermille)
            out.writeVarInt(e.lines.size); for (l in e.lines) out.writeVarLong(l.value)
            out.writeVarLong(e.damageSqm); out.writeBoolean(e.ended)
        }
        val i = p.inputs
        out.writeVarLong(i.population); out.writeMoney(i.averageWage); out.writeVarLong(i.lowIncomeSharePpm); out.writeVarLong(i.unemploymentPpm)
        out.writeVarLong(i.happinessPpm); out.writeBoolean(i.coastal); out.writeMoney(i.transferPerResident)
        val t = p.traffic
        out.writeVarLong(t.commuters); out.writeVarLong(t.averageCommuteSeconds); out.writeVarLong(t.carSharePpm)
        out.writeVarLong(t.transitSharePpm); out.writeVarLong(t.walkSharePpm); out.writeVarLong(t.congestionPpm)
        out.writeVarLong(p.lastMonthly.value)
        out.writeVarInt(p.liveabilityHistory.size)
        for (v in p.liveabilityHistory) out.writeVarInt(v)
        out.writeMoney(p.capitalThisMonth); out.writeMoney(p.feesThisMonth)
    }

    private fun writeLongs(a: LongArray, out: ByteWriter) { for (v in a) out.writeVarLong(v) }

    private fun writeDistrict(d: District, out: ByteWriter) {
        out.writeVarLong(d.id.value); out.writeVarLong(d.city.value); out.writeVarInt(d.index); out.writeString(d.name)
        out.writeVarInt(d.zone.ordinal); out.writeVarInt(d.rules.maxFarMilli); out.writeVarInt(d.rules.maxFloors); out.writeVarLong(d.rules.lotCoveragePpm)
        out.writeVarLong(d.landSqm)
        writeLongs(d.stock.areas(), out); writeLongs(d.stock.conditions(), out); writeLongs(d.stock.carries(), out)
        out.writeBoolean(d.heritage)
        out.writeVarLong(d.residents); out.writeVarLong(d.jobs); out.writeVarLong(d.workersLiving)
        out.writeMoney(d.rentPerSqm); out.writeVarLong(d.vacancyPpm); out.writeVarLong(d.lowIncomeSharePpm)
        out.writeVarLong(d.displacedTotal); out.writeVarLong(d.backlashPpm); out.writeVarInt(d.tenantProtectionPermille)
        out.writeMoney(d.landValuePerSqm); out.writeVarInt(d.accessibilityPermille); out.writeVarInt(d.trafficLoadPermille)
        out.writeVarLong(d.averageCommuteSeconds); out.writeVarInt(d.amenityBonus); out.writeVarLong(d.lastRezonedAt.value)
        out.writeVarLong(d.pipelineArea); out.writeVarLong(d.pipelineReadyAt.value)
        val rents = d.rentHistoryList()
        out.writeVarInt(rents.size)
        for (m in rents) out.writeMoney(m)
    }

    private fun writeNetwork(n: TransitNetwork, out: ByteWriter) {
        val roads = n.roads()
        out.writeVarInt(roads.size)
        for (r in roads) {
            out.writeVarLong(r.id.value); out.writeVarInt(r.a); out.writeVarInt(r.b); out.writeVarLong(r.lengthMeters)
            out.writeVarInt(r.roadClass.ordinal); out.writeVarInt(r.lanes); out.writeMoney(r.toll); out.writeVarLong(r.flowVph)
        }
        val lines = n.lines()
        out.writeVarInt(lines.size)
        for (l in lines) {
            out.writeVarLong(l.id.value); out.writeString(l.name); out.writeVarInt(l.mode.ordinal)
            out.writeVarInt(l.stops.size); for (s in l.stops) out.writeVarInt(s)
            out.writeVarInt(l.headwaySeconds); out.writeMoney(l.fare); out.writeBoolean(l.dedicatedLane); out.writeVarLong(l.lengthMeters)
            out.writeVarInt(l.status.ordinal); out.writeVarLong(l.peakLoad); out.writeVarLong(l.monthlyRiders); out.writeVarLong(l.lifetimeRiders)
        }
    }

    private fun writeBudget(b: MunicipalBudget, out: ByteWriter) {
        out.writeVarLong(b.rates.propertyPerYearPpm); out.writeVarLong(b.rates.incomePpm); out.writeMoney(b.rates.businessPerSqm)
        out.writeMoney(b.cash); out.writeMoney(b.debt); out.writeVarLong(b.annualBaseRatePpm); out.writeMoney(b.lastRevenue)
        out.writeVarInt(b.consecutiveDeficitMonths)
        for (v in b.levelsList()) out.writeVarInt(v)
        for (v in b.qualityList()) out.writeVarInt(v)
    }

    private fun writeCouncil(c: Council, out: ByteWriter) {
        out.writeVarInt(c.seats.size)
        for (s in c.seats) {
            out.writeVarInt(s.index); out.writeString(s.name); out.writeVarInt(s.ideology); out.writeVarInt(s.ward)
            out.writeVarInt(s.developerTies); out.writeVarInt(s.integrity)
            val pressure = s.pressure.entries.toList()
            out.writeVarInt(pressure.size)
            for ((k, v) in pressure) { out.writeVarLong(k.value); out.writeVarInt(v) }
        }
        out.writeVarInt(c.moodAgainstGrowth); out.writeVarLong(c.lastElection.value); out.writeVarInt(c.elections)
    }

    private fun writePetition(p: Petition, out: ByteWriter) {
        out.writeVarLong(p.id.value); out.writeVarInt(p.district); out.writeVarInt(p.kind.ordinal)
        out.writeVarLong(p.applicant?.value ?: -1L); out.writeVarInt(p.fromZone.ordinal); out.writeVarInt(p.toZone.ordinal)
        out.writeVarInt(p.requestedFarMilli); out.writeVarInt(p.requestedFloors); out.writeVarLong(p.filedAt.value); out.writeMoney(p.fee)
        out.writeVarInt(p.status.ordinal); out.writeVarLong(p.hearingAt.value); out.writeVarLong(p.voteAt.value)
        out.writeVarLong(p.enactAt?.value ?: -1L); out.writeVarLong(p.resolvedAt?.value ?: -1L)
        out.writeVarInt(p.staffScore); out.writeVarInt(p.opinion.supportPermille); out.writeVarInt(p.opinion.oppositionPermille)
        out.writeBoolean(p.protestPetition); out.writeMoney(p.lobbied); out.writeString(p.note)
        out.writeVarInt(p.votes.size); for (v in p.votes) out.writeBoolean(v)
    }

    private fun writeProject(p: CapitalProject, out: ByteWriter) {
        out.writeVarLong(p.id.value); out.writeVarInt(p.kind.ordinal); out.writeString(p.name); out.writeVarInt(p.district ?: -1)
        out.writeVarLong(p.line?.value ?: -1L); out.writeVarLong(p.road?.value ?: -1L); out.writeVarLong(p.size)
        out.writeMoney(p.originalBudget); out.writeVarLong(p.startedAt.value); out.writeBoolean(p.fixedPrice)
        out.writeMoney(p.budget); out.writeMoney(p.spent); out.writeVarInt(p.phase.ordinal); out.writeVarInt(p.phaseDay)
        out.writeVarInt(p.phaseLength); out.writeVarInt(p.delayDays); out.writeVarInt(p.stalledDays); out.writeVarInt(p.status.ordinal)
        out.writeVarLong(p.finishedAt?.value ?: -1L); out.writeMoney(p.phaseSpent)
    }

    private fun writeConcession(c: Concession, out: ByteWriter) {
        out.writeVarLong(c.id.value); out.writeVarInt(c.kind.ordinal); out.writeString(c.name)
        out.writeVarLong(c.road?.value ?: -1L); out.writeVarLong(c.line?.value ?: -1L); out.writeVarInt(c.model.ordinal)
        out.writeVarInt(c.termMonths); out.writeVarInt(c.constructionMonths); out.writeMoney(c.capex); out.writeMoney(c.toll)
        out.writeMoney(c.availabilityMonthly); out.writeVarLong(c.cityShareBasePpm); out.writeVarLong(c.trafficGuarantee)
        out.writeVarLong(c.openedForBidsAt.value); out.writeVarLong(c.bidsCloseAt.value); out.writeVarInt(c.status.ordinal)
        out.writeVarLong(c.concessionaire?.value ?: -1L); out.writeVarLong(c.winningDiscountPpm)
        out.writeVarLong(c.awardedAt?.value ?: -1L); out.writeVarLong(c.operatesFrom?.value ?: -1L); out.writeVarLong(c.endsAt?.value ?: -1L)
        out.writeMoney(c.capexPaid); out.writeMoney(c.userChargesTotal); out.writeMoney(c.cityShareTotal)
        out.writeMoney(c.availabilityPaidTotal); out.writeMoney(c.deductionsTotal); out.writeMoney(c.guaranteePaidTotal)
        out.writeVarLong(c.lastRenegotiation.value)
        val bids = c.bids
        out.writeVarInt(bids.size)
        for (b in bids) { out.writeVarLong(b.company.value); out.writeVarLong(b.discountPpm); out.writeVarLong(b.at.value) }
    }

    // ----- reading --------------------------------------------------------------------------------------------

    private fun <T> pick(all: Array<T>, index: Int, what: String): T = all.getOrNull(index) ?: throw ByteReader.CorruptDataException("unknown $what $index")
    private fun optTick(v: Long): Tick? = if (v < 0L) null else Tick(v)
    private fun readLongs(r: ByteReader, n: Int) = LongArray(n) { r.readVarLong() }

    private fun readPlan(r: ByteReader): CityPlan {
        val city = CityId(r.readVarLong()); val country = CountryId(r.readVarLong()); val name = r.readString()
        val geometry = try {
            CityGeometry(r.readVarInt(), r.readVarInt(), r.readVarLong())
        } catch (e: IllegalArgumentException) {
            throw ByteReader.CorruptDataException("bad city grid: ${e.message}")
        }
        val districts = List(r.readVarInt()) { readDistrict(r) }
        if (districts.size != geometry.districtCount) throw ByteReader.CorruptDataException("district count does not match the grid")
        val plan = CityPlan(city, country, name, geometry, districts, readCouncil(r))
        readNetwork(r, plan.network)
        readBudget(r, plan.budget)
        repeat(r.readVarInt()) { plan.petitions.restore(readPetition(r)) }
        plan.petitions.restoreEfforts(List(r.readVarInt()) {
            val petition = PetitionId(r.readVarLong()); val company = CompanyId(r.readVarLong()); val seat = r.readVarInt()
            LobbyEffort(petition, company, if (seat < 0) null else seat, r.readMoney(), r.readVarInt(), Tick(r.readVarLong()))
        })
        repeat(r.readVarInt()) { plan.projects.restore(readProject(r)) }
        repeat(r.readVarInt()) { plan.concessions.restore(readConcession(r)) }
        repeat(r.readVarInt()) {
            val id = UrbanEventId(r.readVarLong()); val kind = pick(UrbanEventKind.values(), r.readVarInt(), "event kind"); val district = r.readVarInt()
            val e = UrbanEvent(id, kind, district, Tick(r.readVarLong()), Tick(r.readVarLong()), r.readVarInt(), List(r.readVarInt()) { TransitLineId(r.readVarLong()) })
            e.damageSqm = r.readVarLong(); e.ended = r.readBoolean()
            plan.events.restore(e)
        }
        plan.inputs = CityInputs(
            r.readVarLong(), r.readMoney(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readBoolean(), r.readMoney(),
        )
        plan.traffic = TrafficSummary(r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong())
        plan.lastMonthly = Tick(r.readVarLong())
        repeat(r.readVarInt()) { plan.liveabilityHistory += r.readVarInt() }
        plan.capitalThisMonth = r.readMoney(); plan.feesThisMonth = r.readMoney()
        return plan
    }

    private fun readDistrict(r: ByteReader): District {
        val id = DistrictId(r.readVarLong()); val city = CityId(r.readVarLong()); val index = r.readVarInt(); val name = r.readString()
        val zone = pick(ZoneUse.values(), r.readVarInt(), "zone")
        val rules = try { DensityRules(r.readVarInt(), r.readVarInt(), r.readVarLong()) } catch (e: IllegalArgumentException) {
            throw ByteReader.CorruptDataException("bad density rules: ${e.message}")
        }
        val d = District(id, city, index, name, zone, rules, r.readVarLong())
        d.stock.restore(readLongs(r, BuildingStock.BANDS), readLongs(r, BuildingStock.BANDS), readLongs(r, BuildingStock.BANDS))
        d.heritage = r.readBoolean()
        d.residents = r.readVarLong(); d.jobs = r.readVarLong(); d.workersLiving = r.readVarLong()
        d.rentPerSqm = r.readMoney(); d.vacancyPpm = r.readVarLong(); d.lowIncomeSharePpm = r.readVarLong()
        d.displacedTotal = r.readVarLong(); d.backlashPpm = r.readVarLong(); d.tenantProtectionPermille = r.readVarInt()
        d.landValuePerSqm = r.readMoney(); d.accessibilityPermille = r.readVarInt(); d.trafficLoadPermille = r.readVarInt()
        d.averageCommuteSeconds = r.readVarLong(); d.amenityBonus = r.readVarInt(); d.lastRezonedAt = Tick(r.readVarLong())
        d.pipelineArea = r.readVarLong(); d.pipelineReadyAt = Tick(r.readVarLong())
        val n = r.readVarInt()
        if (n > District.HISTORY) throw ByteReader.CorruptDataException("too much rent history")
        d.restoreRentHistory(List(n) { r.readMoney() })
        return d
    }

    private fun readNetwork(r: ByteReader, n: TransitNetwork) {
        n.clear()
        repeat(r.readVarInt()) {
            val link = RoadLink(LinkId(r.readVarLong()), r.readVarInt(), r.readVarInt(), r.readVarLong(), pick(RoadClass.values(), r.readVarInt(), "road class"), r.readVarInt())
            link.toll = r.readMoney(); link.flowVph = r.readVarLong()
            n.restoreRoad(link)
        }
        repeat(r.readVarInt()) {
            val id = TransitLineId(r.readVarLong()); val name = r.readString(); val mode = pick(TransitMode.values(), r.readVarInt(), "transit mode")
            val stops = List(r.readVarInt()) { r.readVarInt() }
            if (stops.size < 2) throw ByteReader.CorruptDataException("a line needs two stops")
            val line = TransitLine(id, name, mode, stops, r.readVarInt(), r.readMoney(), r.readBoolean(), r.readVarLong())
            line.status = pick(LineStatus.values(), r.readVarInt(), "line status")
            line.peakLoad = r.readVarLong(); line.monthlyRiders = r.readVarLong(); line.lifetimeRiders = r.readVarLong()
            n.restoreLine(line)
        }
    }

    private fun readBudget(r: ByteReader, b: MunicipalBudget) {
        val rates = try { TaxRates(r.readVarLong(), r.readVarLong(), r.readMoney()) } catch (e: IllegalArgumentException) {
            throw ByteReader.CorruptDataException("bad tax rates: ${e.message}")
        }
        val cash = r.readMoney(); val debt = r.readMoney(); val base = r.readVarLong(); val revenue = r.readMoney(); val deficits = r.readVarInt()
        val count = Service.values().size
        b.restore(rates, cash, debt, base, revenue, deficits, List(count) { r.readVarInt() }, List(count) { r.readVarInt() })
    }

    private fun readCouncil(r: ByteReader): Council {
        val seats = List(r.readVarInt()) {
            val s = CouncilSeat(r.readVarInt(), r.readString(), r.readVarInt(), r.readVarInt(), r.readVarInt(), r.readVarInt())
            repeat(r.readVarInt()) { s.pressure[PetitionId(r.readVarLong())] = r.readVarInt() }
            s
        }
        if (seats.size % 2 == 0) throw ByteReader.CorruptDataException("a council has an odd number of seats")
        val c = Council(seats)
        c.restore(r.readVarInt(), Tick(r.readVarLong()), r.readVarInt())
        return c
    }

    private fun readPetition(r: ByteReader): Petition {
        val id = PetitionId(r.readVarLong()); val district = r.readVarInt(); val kind = pick(PetitionKind.values(), r.readVarInt(), "petition kind")
        val applicantRaw = r.readVarLong(); val from = pick(ZoneUse.values(), r.readVarInt(), "zone"); val to = pick(ZoneUse.values(), r.readVarInt(), "zone")
        val p = Petition(
            id, district, kind, if (applicantRaw < 0L) null else CompanyId(applicantRaw), from, to,
            r.readVarInt(), r.readVarInt(), Tick(r.readVarLong()), r.readMoney(),
        )
        p.status = pick(PetitionStatus.values(), r.readVarInt(), "petition status")
        p.hearingAt = Tick(r.readVarLong()); p.voteAt = Tick(r.readVarLong())
        p.enactAt = optTick(r.readVarLong()); p.resolvedAt = optTick(r.readVarLong())
        p.staffScore = r.readVarInt(); p.opinion = PublicOpinion(r.readVarInt(), r.readVarInt())
        p.protestPetition = r.readBoolean(); p.lobbied = r.readMoney(); p.note = r.readString()
        p.votes = List(r.readVarInt()) { r.readBoolean() }
        return p
    }

    private fun readProject(r: ByteReader): CapitalProject {
        val id = CapitalProjectId(r.readVarLong()); val kind = pick(ProjectKind.values(), r.readVarInt(), "project kind"); val name = r.readString()
        val district = r.readVarInt(); val line = r.readVarLong(); val road = r.readVarLong(); val size = r.readVarLong()
        val p = CapitalProject(
            id, kind, name, if (district < 0) null else district, if (line < 0L) null else TransitLineId(line), if (road < 0L) null else LinkId(road),
            size, r.readMoney(), Tick(r.readVarLong()), r.readBoolean(),
        )
        p.budget = r.readMoney(); p.spent = r.readMoney(); p.phase = pick(ProjectPhase.values(), r.readVarInt(), "project phase")
        p.phaseDay = r.readVarInt(); p.phaseLength = r.readVarInt(); p.delayDays = r.readVarInt(); p.stalledDays = r.readVarInt()
        p.status = pick(ProjectStatus.values(), r.readVarInt(), "project status")
        p.finishedAt = optTick(r.readVarLong()); p.phaseSpent = r.readMoney()
        return p
    }

    private fun readConcession(r: ByteReader): Concession {
        val id = ConcessionId(r.readVarLong()); val kind = pick(ConcessionKind.values(), r.readVarInt(), "concession kind"); val name = r.readString()
        val road = r.readVarLong(); val line = r.readVarLong(); val model = pick(PaymentModel.values(), r.readVarInt(), "payment model")
        val c = Concession(
            id, kind, name, if (road < 0L) null else LinkId(road), if (line < 0L) null else TransitLineId(line), model,
            r.readVarInt(), r.readVarInt(), r.readMoney(), r.readMoney(), r.readMoney(), r.readVarLong(), r.readVarLong(),
            Tick(r.readVarLong()), Tick(r.readVarLong()),
        )
        c.status = pick(ConcessionStatus.values(), r.readVarInt(), "concession status")
        val holder = r.readVarLong()
        c.concessionaire = if (holder < 0L) null else CompanyId(holder)
        c.winningDiscountPpm = r.readVarLong()
        c.awardedAt = optTick(r.readVarLong()); c.operatesFrom = optTick(r.readVarLong()); c.endsAt = optTick(r.readVarLong())
        c.capexPaid = r.readMoney(); c.userChargesTotal = r.readMoney(); c.cityShareTotal = r.readMoney()
        c.availabilityPaidTotal = r.readMoney(); c.deductionsTotal = r.readMoney(); c.guaranteePaidTotal = r.readMoney()
        c.lastRenegotiation = Tick(r.readVarLong())
        repeat(r.readVarInt()) { c.bidList += ConcessionBid(CompanyId(r.readVarLong()), r.readVarLong(), Tick(r.readVarLong())) }
        return c
    }
}
