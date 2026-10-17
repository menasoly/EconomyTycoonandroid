package app.many.economytycoon.carbon

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick

/**
 * Binary save format of the carbon package. The layout is a version byte followed by the sections in a fixed order; every
 * collection is written in ascending key order so the bytes, and the hash taken over them, do not depend on how the state
 * was reached. A reader refuses a version it does not know.
 */
object CarbonCodec {
    const val VERSION = 1

    fun encode(s: CarbonState): ByteArray = ByteWriter().also { write(s, it) }.toByteArray()

    fun decode(bytes: ByteArray, into: CarbonState) = read(ByteReader(bytes), into)

    fun write(s: CarbonState, w: ByteWriter) {
        w.writeVarInt(VERSION)
        w.writeVarLong(s.lastTick)
        for (k in CarbonIdKind.values()) w.writeVarLong(s.counters.peek(k))
        writeRegimes(s, w)
        writeMarkets(s, w)
        writePairs(w, s.gridEntries())
        writePairs(w, s.fundEntries())
        writePairs(w, s.receiptEntries())
        writeInventory(s.inventory, w)
        writeCompliance(s.compliance, w)
        writeOffsets(s.offsets, w)
        writeAbatement(s.abatement, w)
        writeBorder(s.border, w)
        writeReports(s.reports, w)
    }

    fun read(r: ByteReader, s: CarbonState) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported carbon save version $version")
        s.clear()
        s.lastTick = r.readVarLong()
        for (k in CarbonIdKind.values()) s.counters.restore(k, r.readVarLong())
        readRegimes(s, r)
        readMarkets(s, r)
        readPairs(r) { c, v -> s.setGrid(c, v) }
        readPairs(r) { c, v -> s.putFund(c, v) }
        readPairs(r) { c, v -> s.putReceipt(c, v) }
        readInventory(s.inventory, r)
        readCompliance(s.compliance, r)
        readOffsets(s.offsets, r)
        readAbatement(s.abatement, r)
        readBorder(s.border, r)
        readReports(s.reports, r)
    }

    // ---- small helpers ---------------------------------------------------------------------------

    private fun writePairs(w: ByteWriter, list: List<Pair<CountryId, Long>>) {
        w.writeVarInt(list.size)
        for ((c, v) in list) { w.writeVarLong(c.value); w.writeVarLong(v) }
    }

    private inline fun readPairs(r: ByteReader, put: (CountryId, Long) -> Unit) {
        repeat(r.readVarInt()) { put(CountryId(r.readVarLong()), r.readVarLong()) }
    }

    private fun w(w: ByteWriter, t: Tonnes) = w.writeVarLong(t.raw)
    private fun t(r: ByteReader) = Tonnes(r.readVarLong())

    private fun optLong(w: ByteWriter, v: Long?) { w.writeBoolean(v != null); if (v != null) w.writeVarLong(v) }
    private fun optLong(r: ByteReader): Long? = if (r.readBoolean()) r.readVarLong() else null

    private fun optString(w: ByteWriter, v: String?) { w.writeBoolean(v != null); if (v != null) w.writeString(v) }
    private fun optString(r: ByteReader): String? = if (r.readBoolean()) r.readString() else null

    private fun <T> at(all: Array<T>, index: Int, what: String): T =
        all.getOrNull(index) ?: throw ByteReader.CorruptDataException("unknown $what $index")

    // ---- regimes ---------------------------------------------------------------------------------

    private fun writeRegimes(s: CarbonState, out: ByteWriter) {
        val regimes = s.regimes()
        out.writeVarInt(regimes.size)
        for (g in regimes) {
            out.writeVarLong(g.country.value); out.writeVarInt(g.kind.ordinal)
            out.writeVarInt(g.steps.size)
            for (st in g.steps) {
                out.writeVarInt(st.fromYear)
                out.writeVarInt(st.coverage.size); for (f in st.coverage) out.writeVarInt(f.ordinal)
                w(out, st.threshold); out.writeMoney(st.rate); out.writeVarLong(st.rateEscalatorPpm)
                w(out, st.cap); out.writeVarLong(st.capCutPpm); out.writeVarLong(st.freeSharePpm)
                out.writeMoney(st.floor); out.writeMoney(st.ceiling); out.writeMoney(st.penalty)
                out.writeVarLong(st.benchmarkGramsPerUnit); out.writeVarLong(st.benchmarkCutPpm); out.writeVarLong(st.offsetLimitPpm)
                out.writeBoolean(st.coversIndirect); out.writeVarLong(st.abatementSubsidyPpm)
            }
            out.writeBoolean(g.bankingAllowed); out.writeVarInt(g.vintageLifeYears); out.writeVarLong(g.borrowLimitPpm); out.writeVarLong(g.borrowInterestPpm)
            out.writeVarLong(g.holdingLimitPpm); out.writeVarInt(g.surrenderWindowDays); out.writeBoolean(g.makeGood); out.writeVarLong(g.repeatPenaltyStepPpm)
            out.writeVarInt(g.eligibleOffsets.size); for (k in g.eligibleOffsets) out.writeVarInt(k.ordinal)
            val rs = g.reserve
            out.writeVarLong(rs.upperPpm); out.writeVarLong(rs.lowerPpm); out.writeVarLong(rs.intakePpm); out.writeVarLong(rs.releasePpm)
            out.writeVarLong(rs.priceTriggerPpm); out.writeVarLong(rs.priceReleasePpm); out.writeVarLong(rs.maxStockPpm)
            out.writeVarLong(g.greenFundSharePpm)
        }
    }

    private fun readRegimes(s: CarbonState, r: ByteReader) {
        repeat(r.readVarInt()) {
            val country = CountryId(r.readVarLong())
            val kind = at(RegimeKind.values(), r.readVarInt(), "regime kind")
            val steps = List(r.readVarInt()) {
                val from = r.readVarInt()
                val coverage = List(r.readVarInt()) { at(IndustryFamily.values(), r.readVarInt(), "industry family") }
                RegimeStep(
                    fromYear = from, coverage = coverage, threshold = t(r), rate = r.readMoney(), rateEscalatorPpm = r.readVarLong(),
                    cap = t(r), capCutPpm = r.readVarLong(), freeSharePpm = r.readVarLong(),
                    floor = r.readMoney(), ceiling = r.readMoney(), penalty = r.readMoney(),
                    benchmarkGramsPerUnit = r.readVarLong(), benchmarkCutPpm = r.readVarLong(), offsetLimitPpm = r.readVarLong(),
                    coversIndirect = r.readBoolean(), abatementSubsidyPpm = r.readVarLong(),
                )
            }
            val banking = r.readBoolean(); val life = r.readVarInt(); val borrowLimit = r.readVarLong(); val interest = r.readVarLong()
            val holding = r.readVarLong(); val window = r.readVarInt(); val makeGood = r.readBoolean(); val repeat = r.readVarLong()
            val eligible = List(r.readVarInt()) { at(OffsetKind.values(), r.readVarInt(), "offset kind") }
            val reserve = ReserveRule(r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong())
            val fund = r.readVarLong()
            s.installRegime(CarbonRegime(country, kind, steps, banking, life, borrowLimit, interest, holding, window, makeGood, repeat, eligible, reserve, fund))
        }
    }

    // ---- permit markets --------------------------------------------------------------------------

    private fun writeMarkets(s: CarbonState, out: ByteWriter) {
        val markets = s.markets()
        out.writeVarInt(markets.size)
        for (m in markets) {
            out.writeVarLong(m.country.value)
            val holders = m.holdings.companies()
            out.writeVarInt(holders.size)
            for (c in holders) {
                out.writeVarLong(c.value)
                val lots = m.holdings.lotsOf(c)
                out.writeVarInt(lots.size)
                for (l in lots) { out.writeVarInt(l.vintage); w(out, l.tonnes); out.writeMoney(l.cost) }
            }
            val bids = m.bids()
            out.writeVarInt(bids.size)
            for (b in bids) { out.writeVarLong(b.id.value); out.writeVarLong(b.company.value); w(out, b.quantity); out.writeMoney(b.limit); out.writeVarLong(b.placedAt.value) }
            val orders = m.orders()
            out.writeVarInt(orders.size)
            for (o in orders) {
                out.writeVarLong(o.id.value); out.writeVarLong(o.company.value); out.writeVarInt(o.side.ordinal); out.writeMoney(o.limit)
                w(out, o.quantity); w(out, o.remaining); out.writeVarLong(o.placedAt.value)
            }
            val supplies = m.supplies()
            out.writeVarInt(supplies.size)
            for (y in supplies) {
                out.writeVarInt(y.year); w(out, y.cap); w(out, y.free); w(out, y.auctionPlanned)
                w(out, y.withheld); w(out, y.released); w(out, y.offered); w(out, y.sold); w(out, y.ceilingSold); w(out, y.unsold); w(out, y.freeIssued)
                out.writeVarInt(y.auctionsHeld)
            }
            val auctions = m.auctions()
            out.writeVarInt(auctions.size)
            for (a in auctions) {
                out.writeVarLong(a.tick.value); out.writeVarInt(a.year); out.writeVarInt(a.quarter); w(out, a.supply); w(out, a.sold)
                w(out, a.ceilingSold); w(out, a.unsold); out.writeMoney(a.price); out.writeVarInt(a.bidders); out.writeVarLong(a.coverPpm)
            }
            val trades = m.trades()
            out.writeVarInt(trades.size)
            for (x in trades) { out.writeVarLong(x.tick.value); out.writeVarLong(x.buyer.value); out.writeVarLong(x.seller.value); w(out, x.tonnes); out.writeMoney(x.price) }
            val prices = m.prices()
            out.writeVarInt(prices.size); for (p in prices) out.writeMoney(p)
            w(out, m.reserve); out.writeMoney(m.lastTradePrice); out.writeMoney(m.lastAuctionPrice); w(out, m.pendingFree); w(out, m.cancelled)
            w(out, m.expired); w(out, m.ceilingReleased); w(out, m.lastCirculation); w(out, m.tradedVolume); out.writeVarInt(m.reviewedYear)
        }
    }

    private fun readMarkets(s: CarbonState, r: ByteReader) {
        repeat(r.readVarInt()) {
            val m = PermitMarket(CountryId(r.readVarLong()))
            repeat(r.readVarInt()) {
                val company = CompanyId(r.readVarLong())
                repeat(r.readVarInt()) { m.holdings.putLot(company, r.readVarInt(), t(r), r.readMoney()) }
            }
            repeat(r.readVarInt()) {
                val b = AuctionBid(BidId(r.readVarLong()), CompanyId(r.readVarLong()), t(r), r.readMoney(), Tick(r.readVarLong()))
                m.bidMap[b.id] = b
            }
            repeat(r.readVarInt()) {
                val o = PermitOrder(PermitOrderId(r.readVarLong()), CompanyId(r.readVarLong()), at(PermitSide.values(), r.readVarInt(), "order side"), r.readMoney(), Tonnes(0), Tick(0))
                // quantity and placement are read below; rebuild with the real values
                val quantity = t(r); val remaining = t(r); val placed = Tick(r.readVarLong())
                val rebuilt = PermitOrder(o.id, o.company, o.side, o.limit, quantity, placed)
                rebuilt.remaining = remaining
                m.orderMap[rebuilt.id] = rebuilt
            }
            repeat(r.readVarInt()) {
                val y = YearSupply(r.readVarInt(), t(r), t(r), t(r))
                y.restore(t(r), t(r), t(r), t(r), t(r), t(r), t(r), r.readVarInt())
                m.supplyMap[y.year] = y
            }
            repeat(r.readVarInt()) {
                m.auctionLog += AuctionResult(Tick(r.readVarLong()), r.readVarInt(), r.readVarInt(), t(r), t(r), t(r), t(r), r.readMoney(), r.readVarInt(), r.readVarLong())
            }
            repeat(r.readVarInt()) { m.tradeLog += PermitTrade(Tick(r.readVarLong()), CompanyId(r.readVarLong()), CompanyId(r.readVarLong()), t(r), r.readMoney()) }
            repeat(r.readVarInt()) { m.quarterlyPrices += r.readMoney() }
            m.restoreTotals(t(r), r.readMoney(), r.readMoney(), t(r), t(r), t(r), t(r), t(r), t(r))
            m.markReviewed(r.readVarInt())
            s.putMarket(m)
        }
    }

    // ---- inventory -------------------------------------------------------------------------------

    private fun writeInventory(b: InventoryBook, out: ByteWriter) {
        val years = b.companyYears()
        out.writeVarInt(years.size)
        for (y in years) {
            out.writeVarLong(y.company.value); out.writeVarLong(y.country.value); out.writeVarInt(y.year)
            w(out, y.direct); w(out, y.covered); w(out, y.indirect); w(out, y.biogenic); w(out, y.avoided)
            out.writeMoney(y.outputValue); out.writeMoney(y.coveredOutput); out.writeVarInt(y.days); out.writeBoolean(y.closed)
            val src = y.sources(); out.writeVarInt(src.size); for ((k, v) in src) { out.writeString(k); out.writeVarLong(v.raw) }
            val fam = y.families(); out.writeVarInt(fam.size); for ((k, v) in fam) { out.writeVarInt(k.ordinal); out.writeVarLong(v.raw) }
            val fac = y.facilities(); out.writeVarInt(fac.size); for ((k, v) in fac) { out.writeVarLong(k.value); out.writeVarLong(v.raw) }
        }
        val nat = b.nationalYears()
        out.writeVarInt(nat.size)
        for (n in nat) {
            out.writeVarLong(n.country.value); out.writeVarInt(n.year)
            w(out, n.direct); w(out, n.indirect); w(out, n.biogenic); w(out, n.avoided); w(out, n.covered); out.writeQuantity(n.electricityProduced)
            val fam = n.families(); out.writeVarInt(fam.size); for ((k, v) in fam) { out.writeVarInt(k.ordinal); out.writeVarLong(v.raw) }
        }
        val facs = b.facilities()
        out.writeVarInt(facs.size)
        for (f in facs) {
            out.writeVarLong(f.facility.value); out.writeVarLong(f.owner.value); out.writeVarLong(f.country.value); out.writeString(f.type.key); out.writeVarInt(f.family.ordinal)
            out.writeVarLong(f.lastSeen.value); out.writeVarInt(f.samples)
            val kinds = SourceKind.values().filter { f.daily(it).raw != 0L }
            out.writeVarInt(kinds.size); for (k in kinds) { out.writeVarInt(k.ordinal); out.writeVarLong(f.daily(k).raw) }
            val fuels = f.fuelKg.entries.toList(); out.writeVarInt(fuels.size); for (e in fuels) { out.writeString(e.key); out.writeVarLong(e.value) }
            out.writeMoney(f.dailyEnergySpend); out.writeMoney(f.dailyOutputValue)
            val effects = f.effects; out.writeVarInt(effects.size)
            for (e in effects) { out.writeVarInt(e.kind.ordinal); out.writeVarLong(e.reductionPpm); optString(out, e.fuelKey) }
        }
    }

    private fun readInventory(b: InventoryBook, r: ByteReader) {
        repeat(r.readVarInt()) {
            val y = CompanyYear(CompanyId(r.readVarLong()), CountryId(r.readVarLong()), r.readVarInt())
            val direct = t(r); val covered = t(r); val indirect = t(r); val biogenic = t(r); val avoided = t(r)
            val output = r.readMoney(); val coveredOutput = r.readMoney(); val days = r.readVarInt(); val closed = r.readBoolean()
            val src = java.util.TreeMap<String, Long>(); repeat(r.readVarInt()) { src[r.readString()] = r.readVarLong() }
            val fam = java.util.TreeMap<IndustryFamily, Long>(); repeat(r.readVarInt()) { fam[at(IndustryFamily.values(), r.readVarInt(), "industry family")] = r.readVarLong() }
            val fac = java.util.TreeMap<FacilityId, Long>(); repeat(r.readVarInt()) { fac[FacilityId(r.readVarLong())] = r.readVarLong() }
            y.restore(direct, covered, indirect, biogenic, avoided, output, coveredOutput, days, closed, src, fam, fac)
            b.putCompanyYear(y)
        }
        repeat(r.readVarInt()) {
            val n = NationalYear(CountryId(r.readVarLong()), r.readVarInt())
            val direct = t(r); val indirect = t(r); val biogenic = t(r); val avoided = t(r); val covered = t(r); val produced = r.readQuantity()
            val fam = java.util.TreeMap<IndustryFamily, Long>(); repeat(r.readVarInt()) { fam[at(IndustryFamily.values(), r.readVarInt(), "industry family")] = r.readVarLong() }
            n.restore(direct, indirect, biogenic, avoided, covered, produced, fam)
            b.putNationalYear(n)
        }
        repeat(r.readVarInt()) {
            val f = FacilityRecord(FacilityId(r.readVarLong()), CompanyId(r.readVarLong()), CountryId(r.readVarLong()), FacilityTypeId(r.readString()), at(IndustryFamily.values(), r.readVarInt(), "industry family"))
            val seen = Tick(r.readVarLong()); val samples = r.readVarInt()
            val kinds = HashMap<SourceKind, Long>(); repeat(r.readVarInt()) { kinds[at(SourceKind.values(), r.readVarInt(), "source kind")] = r.readVarLong() }
            val fuels = java.util.TreeMap<String, Long>(); repeat(r.readVarInt()) { fuels[r.readString()] = r.readVarLong() }
            val spend = r.readMoney(); val out = r.readMoney()
            val effects = List(r.readVarInt()) { AbatementEffect(at(AbatementKind.values(), r.readVarInt(), "abatement kind"), r.readVarLong(), optString(r)) }
            f.restore(seen, samples, kinds, fuels, spend, out, effects)
            b.putFacility(f)
        }
    }

    // ---- compliance ------------------------------------------------------------------------------

    private fun writeCompliance(b: ComplianceBook, out: ByteWriter) {
        val accounts = b.accounts()
        out.writeVarInt(accounts.size)
        for (a in accounts) {
            out.writeVarLong(a.key.company.value); out.writeVarLong(a.key.country.value)
            val p = a.pending
            out.writeBoolean(p != null)
            if (p != null) { out.writeVarInt(p.year); w(out, p.verified); w(out, p.obligation); out.writeVarLong(p.deadline.value); w(out, p.surrendered); w(out, p.offsets); w(out, p.borrowed) }
            w(out, a.carry); out.writeVarInt(a.violations); out.writeMoney(a.taxAssessed); w(out, a.freeThisYear)
            out.writeBoolean(a.policy.autoOffsets); out.writeBoolean(a.policy.autoBorrow)
            val months = a.monitoring(); out.writeVarInt(months.size); for ((m, v) in months) { out.writeVarInt(m); out.writeVarLong(v.raw) }
            val records = a.history(); out.writeVarInt(records.size)
            for (x in records) {
                out.writeVarInt(x.year); out.writeVarInt(x.regime.ordinal); out.writeVarInt(x.status.ordinal)
                w(out, x.verified); w(out, x.obligation); w(out, x.surrendered); w(out, x.offsets); w(out, x.borrowed); w(out, x.shortfall)
                out.writeMoney(x.penalty); out.writeMoney(x.charge); w(out, x.freeReceived)
            }
        }
    }

    private fun readCompliance(b: ComplianceBook, r: ByteReader) {
        repeat(r.readVarInt()) {
            val a = ComplianceAccount(AccountKey(CompanyId(r.readVarLong()), CountryId(r.readVarLong())))
            val pending = if (r.readBoolean()) {
                PendingYear(r.readVarInt(), t(r), t(r), Tick(r.readVarLong())).also { it.restore(t(r), t(r), t(r)) }
            } else null
            val carry = t(r); val violations = r.readVarInt(); val tax = r.readMoney(); val free = t(r)
            val policy = CompliancePolicy(r.readBoolean(), r.readBoolean())
            val monthly = java.util.TreeMap<Int, Long>(); repeat(r.readVarInt()) { monthly[r.readVarInt()] = r.readVarLong() }
            val records = List(r.readVarInt()) {
                ComplianceRecord(
                    year = r.readVarInt(), regime = at(RegimeKind.values(), r.readVarInt(), "regime kind"), status = at(ComplianceStatus.values(), r.readVarInt(), "compliance status"),
                    verified = t(r), obligation = t(r), surrendered = t(r), offsets = t(r), borrowed = t(r), shortfall = t(r),
                    penalty = r.readMoney(), charge = r.readMoney(), freeReceived = t(r),
                )
            }
            a.restore(pending, carry, violations, tax, free, policy, monthly, records)
            b.put(a)
        }
    }

    // ---- offsets ---------------------------------------------------------------------------------

    private fun writeOffsets(b: OffsetBook, out: ByteWriter) {
        val projects = b.projects()
        out.writeVarInt(projects.size)
        for (p in projects) {
            out.writeVarLong(p.id.value); out.writeVarInt(p.kind.ordinal); optLong(out, p.owner?.value); out.writeVarLong(p.host.value)
            w(out, p.annual); out.writeMoney(p.capex); out.writeVarLong(p.startedAt.value)
            out.writeVarInt(p.status.ordinal); out.writeVarLong(p.validationAt.value); out.writeVarLong(p.nextIssuance.value); out.writeVarLong(p.endsAt.value)
            w(out, p.issued); w(out, p.buffer); w(out, p.bufferLost); out.writeVarInt(p.periods); out.writeVarInt(p.shortPeriods); out.writeMoney(p.basisLeft)
        }
        val lots = b.holdingEntries()
        out.writeVarInt(lots.size)
        for ((c, k, lot) in lots) { out.writeVarLong(c.value); out.writeVarInt(k.ordinal); w(out, lot.tonnes); out.writeMoney(lot.cost) }
        val retired = b.retiredEntries()
        out.writeVarInt(retired.size)
        for ((key, raw) in retired) { out.writeVarLong(key.company.value); out.writeVarLong(key.country.value); out.writeVarInt(key.year); out.writeVarLong(raw) }
        for (k in OffsetKind.values()) {
            w(out, b.exchange.stock(k)); out.writeMoney(b.exchange.spot(k)); w(out, b.exchange.boughtThisMonth(k)); w(out, b.exchange.addedThisMonth(k))
        }
    }

    private fun readOffsets(b: OffsetBook, r: ByteReader) {
        repeat(r.readVarInt()) {
            val id = OffsetProjectId(r.readVarLong()); val kind = at(OffsetKind.values(), r.readVarInt(), "offset kind")
            val owner = optLong(r)?.let { CompanyId(it) }; val host = CountryId(r.readVarLong())
            val p = OffsetProject(id, kind, owner, host, t(r), r.readMoney(), Tick(r.readVarLong()))
            p.restore(at(OffsetStatus.values(), r.readVarInt(), "offset status"), Tick(r.readVarLong()), Tick(r.readVarLong()), Tick(r.readVarLong()), t(r), t(r), t(r), r.readVarInt(), r.readVarInt(), r.readMoney())
            b.put(p)
        }
        repeat(r.readVarInt()) {
            val c = CompanyId(r.readVarLong()); val k = at(OffsetKind.values(), r.readVarInt(), "offset kind")
            b.putLot(c, k, OffsetLot(t(r), r.readMoney()))
        }
        repeat(r.readVarInt()) { b.putRetired(CompanyYearKey(CompanyId(r.readVarLong()), CountryId(r.readVarLong()), r.readVarInt()), r.readVarLong()) }
        for (k in OffsetKind.values()) b.exchange.restore(k, t(r), r.readMoney(), t(r), t(r))
    }

    // ---- abatement -------------------------------------------------------------------------------

    private fun writeAbatement(b: AbatementBook, out: ByteWriter) {
        val projects = b.projects()
        out.writeVarInt(projects.size)
        for (p in projects) {
            out.writeVarLong(p.id.value); out.writeVarLong(p.company.value); out.writeVarLong(p.facility.value); out.writeVarInt(p.kind.ordinal)
            out.writeMoney(p.capex); out.writeMoney(p.opexPerYear)
            out.writeVarInt(p.effect.kind.ordinal); out.writeVarLong(p.effect.reductionPpm); optString(out, p.effect.fuelKey)
            w(out, p.annualAbatement); out.writeVarLong(p.startedAt.value); out.writeVarLong(p.completesAt.value); out.writeVarLong(p.endsAt.value)
            out.writeVarInt(p.status.ordinal); out.writeMoney(p.subsidy); out.writeMoney(p.opexPaid)
        }
    }

    private fun readAbatement(b: AbatementBook, r: ByteReader) {
        repeat(r.readVarInt()) {
            val id = AbatementId(r.readVarLong()); val company = CompanyId(r.readVarLong()); val facility = FacilityId(r.readVarLong())
            val kind = at(AbatementKind.values(), r.readVarInt(), "abatement kind")
            val capex = r.readMoney(); val opex = r.readMoney()
            val effect = AbatementEffect(at(AbatementKind.values(), r.readVarInt(), "abatement kind"), r.readVarLong(), optString(r))
            val p = AbatementProject(id, company, facility, kind, capex, opex, effect, t(r), Tick(r.readVarLong()), Tick(r.readVarLong()), Tick(r.readVarLong()))
            p.restore(at(AbatementStatus.values(), r.readVarInt(), "abatement status"), r.readMoney(), r.readMoney())
            b.put(p)
        }
    }

    // ---- border ----------------------------------------------------------------------------------

    private fun writeBorder(b: BorderBook, out: ByteWriter) {
        val policies = b.policies()
        out.writeVarInt(policies.size)
        for (p in policies) {
            out.writeVarLong(p.country.value); out.writeBoolean(p.enabled)
            out.writeVarInt(p.goods.size); for (g in p.goods) out.writeString(g.key)
            out.writeVarLong(p.minIntensityGrams); out.writeVarInt(p.startYear); out.writeVarInt(p.phaseInYears); out.writeVarLong(p.exportRebatePpm)
        }
        val totals = b.totalEntries()
        out.writeVarInt(totals.size)
        for ((c, levied, rebated) in totals) { out.writeVarLong(c.value); out.writeVarLong(levied); out.writeVarLong(rebated) }
    }

    private fun readBorder(b: BorderBook, r: ByteReader) {
        repeat(r.readVarInt()) {
            val country = CountryId(r.readVarLong()); val enabled = r.readBoolean()
            val goods = List(r.readVarInt()) { GoodId(r.readString()) }
            b.set(BorderPolicy(country, enabled, goods, r.readVarLong(), r.readVarInt(), r.readVarInt(), r.readVarLong()))
        }
        repeat(r.readVarInt()) { b.putTotals(CountryId(r.readVarLong()), r.readVarLong(), r.readVarLong()) }
    }

    // ---- reports ---------------------------------------------------------------------------------

    private fun writeReports(b: ReportBook, out: ByteWriter) {
        val statements = b.statements()
        out.writeVarInt(statements.size)
        for (s in statements) {
            out.writeVarLong(s.company.value); out.writeVarLong(s.country.value); out.writeVarInt(s.year)
            w(out, s.direct); w(out, s.electricity); w(out, s.biogenic); w(out, s.covered); w(out, s.avoided)
            out.writeMoney(s.outputValue); out.writeVarLong(s.intensityGrams); out.writeVarInt(s.dominantFamily?.ordinal ?: -1)
            out.writeVarInt(s.regime.ordinal); out.writeVarInt(s.status.ordinal)
            w(out, s.obligation); w(out, s.surrendered); w(out, s.offsetsUsed); w(out, s.shortfall); out.writeMoney(s.charges); w(out, s.freePermits)
            optLong(out, s.changePpm)
        }
        val ratings = b.ratings()
        out.writeVarInt(ratings.size)
        for (x in ratings) {
            out.writeVarLong(x.company.value); out.writeVarInt(x.year); out.writeVarInt(x.score); out.writeVarInt(x.grade.ordinal)
            out.writeVarInt(x.factors.size)
            for (f in x.factors) { out.writeString(f.label); out.writeVarInt(f.points); out.writeVarInt(f.maximum); out.writeString(f.note) }
        }
    }

    private fun readReports(b: ReportBook, r: ByteReader) {
        repeat(r.readVarInt()) {
            val company = CompanyId(r.readVarLong()); val country = CountryId(r.readVarLong()); val year = r.readVarInt()
            val direct = t(r); val electricity = t(r); val biogenic = t(r); val covered = t(r); val avoided = t(r)
            val output = r.readMoney(); val intensity = r.readVarLong()
            val familyIndex = r.readVarInt()
            val family = if (familyIndex < 0) null else at(IndustryFamily.values(), familyIndex, "industry family")
            val regime = at(RegimeKind.values(), r.readVarInt(), "regime kind"); val status = at(ComplianceStatus.values(), r.readVarInt(), "compliance status")
            b.file(
                AnnualStatement(
                    company, country, year, direct, electricity, biogenic, covered, avoided, output, intensity, family, regime, status,
                    t(r), t(r), t(r), t(r), r.readMoney(), t(r), optLong(r),
                ),
            )
        }
        repeat(r.readVarInt()) {
            val company = CompanyId(r.readVarLong()); val year = r.readVarInt(); val score = r.readVarInt(); val grade = at(CarbonGrade.values(), r.readVarInt(), "carbon grade")
            val factors = List(r.readVarInt()) { RatingFactor(r.readString(), r.readVarInt(), r.readVarInt(), r.readString()) }
            b.rate(CarbonRating(company, year, score, grade, factors))
        }
    }
}
