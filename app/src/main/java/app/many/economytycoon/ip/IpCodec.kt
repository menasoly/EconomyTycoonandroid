package app.many.economytycoon.ip

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick

/**
 * Binary save format of the whole module. It starts with a version byte; a newer layout adds fields at the end of a
 * record or new sections after the last one, and the reader of an older version rejects what it cannot understand
 * rather than guess. Derived data (forward citations) is rebuilt after reading.
 */
object IpCodec {
    const val VERSION = 1

    fun encode(state: IpState): ByteArray = ByteWriter().also { write(state, it) }.toByteArray()

    fun decode(bytes: ByteArray): IpState = read(ByteReader(bytes))

    fun write(s: IpState, out: ByteWriter) {
        out.writeByte(VERSION)
        out.writeVarLong(s.worldSeed)
        writeRegimes(s, out)
        val ids = s.ids.snapshot()
        out.writeVarInt(ids.size)
        for ((kind, next) in ids) { out.writeString(kind); out.writeVarLong(next) }
        out.writeVarInt(s.patentMap.size); for (p in s.patentMap.values) writePatent(p, out)
        out.writeVarInt(s.secretMap.size); for (x in s.secretMap.values) writeSecret(x, out)
        out.writeVarInt(s.licenseMap.size); for (l in s.licenseMap.values) writeLicense(l, out)
        out.writeVarInt(s.poolMap.size); for (p in s.poolMap.values) writePool(p, out)
        out.writeVarInt(s.disputeMap.size); for (d in s.disputeMap.values) writeDispute(d, out)
        out.writeVarInt(s.npeMap.size); for (n in s.npeMap.values) writeNpe(n, out)
        out.writeVarInt(s.designAroundMap.size); for (d in s.designAroundMap.values) writeDesign(d, out)
        out.writeVarInt(s.compulsoryMap.size); for (c in s.compulsoryMap.values) writeCompulsory(c, out)
        out.writeVarInt(s.clearanceSet.size)
        for (c in s.clearanceSet) { out.writeVarLong(c.company.value); out.writeVarLong(c.patent.value); out.writeVarLong(c.since.value) }
        out.writeVarInt(s.policyMap.size); for ((c, p) in s.policyMap) { out.writeVarLong(c.value); writePolicy(p, out) }
        out.writeVarInt(s.creditYearMap.size); for ((c, y) in s.creditYearMap) { out.writeVarLong(c.value); out.writeVarLong(y) }
    }

    fun read(r: ByteReader): IpState {
        val version = r.readByte()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported intellectual property save version $version")
        val s = IpState(r.readVarLong())
        readRegimes(s, r)
        val counters = java.util.TreeMap<String, Long>()
        repeat(r.readVarInt()) { counters[r.readString()] = r.readVarLong() }
        try { s.ids.restore(counters) } catch (e: IllegalArgumentException) { throw ByteReader.CorruptDataException("bad id counter: ${e.message}") }
        repeat(r.readVarInt()) { val p = readPatent(r); s.patentMap[p.id] = p }
        repeat(r.readVarInt()) { val x = readSecret(r); s.secretMap[x.id] = x }
        repeat(r.readVarInt()) { val l = readLicense(r); s.licenseMap[l.id] = l }
        repeat(r.readVarInt()) { val p = readPool(r); s.poolMap[p.id] = p }
        repeat(r.readVarInt()) { val d = readDispute(r); s.disputeMap[d.id] = d }
        repeat(r.readVarInt()) { val n = readNpe(r); s.npeMap[n.id] = n }
        repeat(r.readVarInt()) { val d = readDesign(r); s.designAroundMap[d.id] = d }
        repeat(r.readVarInt()) { val c = readCompulsory(r); s.compulsoryMap[c.id] = c }
        repeat(r.readVarInt()) { s.clearanceSet.add(Clearance(CompanyId(r.readVarLong()), PatentId(r.readVarLong()), Tick(r.readVarLong()))) }
        repeat(r.readVarInt()) { val c = CompanyId(r.readVarLong()); s.policyMap[c] = readPolicy(r) }
        repeat(r.readVarInt()) { val c = CompanyId(r.readVarLong()); s.creditYearMap[c] = r.readVarLong() }
        s.rebuildIndexes()
        return s
    }

    // ---- small helpers ----

    internal fun <T> pick(all: Array<T>, index: Int, what: String): T = all.getOrNull(index) ?: throw ByteReader.CorruptDataException("unknown $what $index")

    private fun ByteWriter.tick(t: Tick?) = writeVarLong(t?.value ?: -1L)
    private fun ByteReader.tick(): Tick? = readVarLong().let { if (it < 0L) null else Tick(it) }
    private fun ByteReader.needTick(): Tick = Tick(readVarLong())
    private fun ByteWriter.owner(o: IpOwner) { writeVarInt(o.kind.ordinal); writeVarLong(o.id) }
    private fun ByteReader.owner(): IpOwner = IpOwner(pick(IpOwnerKind.values(), readVarInt(), "owner kind"), readVarLong())
    private fun ByteWriter.optOwner(o: IpOwner?) { writeBoolean(o != null); if (o != null) owner(o) }
    private fun ByteReader.optOwner(): IpOwner? = if (readBoolean()) owner() else null
    private fun ByteWriter.optMoney(m: Money?) { writeBoolean(m != null); if (m != null) writeMoney(m) }
    private fun ByteReader.optMoney(): Money? = if (readBoolean()) readMoney() else null
    private fun ByteWriter.optId(v: Long?) { writeBoolean(v != null); if (v != null) writeVarLong(v) }
    private fun ByteReader.optId(): Long? = if (readBoolean()) readVarLong() else null

    // ---- regimes and policies ----

    private fun writeRegimes(s: IpState, out: ByteWriter) {
        out.writeVarInt(s.regimes.default.ordinal)
        val assigned = s.regimes.assigned()
        out.writeVarInt(assigned.size)
        for ((c, k) in assigned) { out.writeVarLong(c.value); out.writeVarInt(k.ordinal) }
    }

    private fun readRegimes(s: IpState, r: ByteReader) {
        s.regimes.default = pick(RegimeKind.values(), r.readVarInt(), "regime")
        repeat(r.readVarInt()) { val c = CountryId(r.readVarLong()); s.regimes.assign(c, pick(RegimeKind.values(), r.readVarInt(), "regime")) }
    }

    internal fun writePolicy(p: IpPolicy, out: ByteWriter) {
        out.writeVarInt(p.stance.ordinal); out.writeVarLong(p.monitoringPpm); out.writeVarInt(p.defaultBreadth.ordinal); out.writeBoolean(p.autoMaintain)
        out.writeVarInt(p.defaultCountries.size); for (c in p.defaultCountries) out.writeVarLong(c.value)
    }

    internal fun readPolicy(r: ByteReader): IpPolicy {
        val stance = pick(EnforcementStance.values(), r.readVarInt(), "stance")
        val monitoring = r.readVarLong()
        val breadth = pick(BreadthChoice.values(), r.readVarInt(), "breadth")
        val auto = r.readBoolean()
        val countries = List(r.readVarInt()) { CountryId(r.readVarLong()) }
        if (monitoring !in 0L..1_000_000L) throw ByteReader.CorruptDataException("monitoring out of range")
        return IpPolicy(stance, monitoring, breadth, countries, auto)
    }

    // ---- patents ----

    private fun writePatent(p: Patent, out: ByteWriter) {
        out.writeVarLong(p.id.value); out.writeVarLong(p.inventor.value); out.writeString(p.tech.key); out.writeString(p.title)
        out.writeVarInt(p.field.ordinal); out.writeVarInt(p.industry?.ordinal ?: -1); out.tick(p.filedAt)
        out.writeVarLong(p.noveltyPpm); out.writeVarInt(p.claims); out.writeVarLong(p.claimedBreadthPpm)
        out.writeVarInt(p.cites.size); for (c in p.cites) out.writeVarLong(c.value)
        out.writeMoney(p.researchCost); out.writeBoolean(p.pharma); out.owner(p.owner)
        out.writeBoolean(p.standardEssential); out.writeBoolean(p.frandPledged); out.writeBoolean(p.autoMaintain); out.writeVarInt(p.transfers)
        out.optMoney(p.askingPrice); out.tick(p.listedAt); out.writeMoney(p.totalFees)
        out.writeVarInt(p.filingMap.size)
        for (f in p.filingMap.values) {
            out.writeVarLong(f.country.value); out.tick(f.filedAt); out.tick(f.examinationDue); out.writeVarLong(f.breadthPpm)
            out.writeVarInt(f.status.ordinal); out.tick(f.grantedAt); out.tick(f.expiresAt); out.tick(f.endedAt)
            out.writeMoney(f.feesPaid); out.writeVarInt(f.feeYearsPaid); out.tick(f.nextFeeDue); out.tick(f.graceUntil)
        }
    }

    private fun readPatent(r: ByteReader): Patent {
        val id = PatentId(r.readVarLong()); val inventor = CompanyId(r.readVarLong()); val tech = TechId(r.readString()); val title = r.readString()
        val field = pick(TechField.values(), r.readVarInt(), "field")
        val industryIdx = r.readVarInt()
        val industry = if (industryIdx < 0) null else pick(IndustryFamily.values(), industryIdx, "industry")
        val filedAt = r.needTick()
        val novelty = r.readVarLong(); val claims = r.readVarInt(); val breadth = r.readVarLong()
        val cites = List(r.readVarInt()) { PatentId(r.readVarLong()) }
        val cost = r.readMoney(); val pharma = r.readBoolean(); val owner = r.owner()
        val p = Patent(id, inventor, tech, title, field, industry, filedAt, novelty, claims, breadth, cites, cost, pharma, owner)
        p.standardEssential = r.readBoolean(); p.frandPledged = r.readBoolean(); p.autoMaintain = r.readBoolean(); p.transfers = r.readVarInt()
        p.askingPrice = r.optMoney(); p.listedAt = r.tick(); p.totalFees = r.readMoney()
        repeat(r.readVarInt()) {
            val country = CountryId(r.readVarLong()); val fAt = r.needTick(); val due = r.needTick(); val fb = r.readVarLong()
            val f = Filing(country, fAt, due, fb)
            f.status = pick(FilingStatus.values(), r.readVarInt(), "filing status")
            f.grantedAt = r.tick(); f.expiresAt = r.tick(); f.endedAt = r.tick()
            f.feesPaid = r.readMoney(); f.feeYearsPaid = r.readVarInt(); f.nextFeeDue = r.tick(); f.graceUntil = r.tick()
            p.filingMap[country] = f
        }
        return p
    }

    // ---- secrets ----

    private fun writeSecret(x: TradeSecret, out: ByteWriter) {
        out.writeVarLong(x.id.value); out.writeVarLong(x.owner.value); out.writeString(x.tech.key); out.writeVarInt(x.field.ordinal); out.tick(x.createdAt)
        out.writeVarInt(x.status.ordinal); out.tick(x.endedAt); out.writeVarLong(x.protectionPpm); out.writeMoney(x.securitySpent)
        out.writeVarInt(x.knowers); out.writeVarLong(x.exposurePpm); out.writeVarInt(x.incidents)
        out.writeVarInt(x.knownToSet.size); for (c in x.knownToSet) out.writeVarLong(c.value)
    }

    private fun readSecret(r: ByteReader): TradeSecret {
        val x = TradeSecret(SecretId(r.readVarLong()), CompanyId(r.readVarLong()), TechId(r.readString()), pick(TechField.values(), r.readVarInt(), "field"), r.needTick())
        x.status = pick(SecretStatus.values(), r.readVarInt(), "secret status")
        x.endedAt = r.tick(); x.protectionPpm = r.readVarLong(); x.securitySpent = r.readMoney()
        x.knowers = r.readVarInt(); x.exposurePpm = r.readVarLong(); x.incidents = r.readVarInt()
        repeat(r.readVarInt()) { x.knownToSet.add(CompanyId(r.readVarLong())) }
        return x
    }

    // ---- licences ----

    internal fun writeTerms(t: RoyaltyTerms, out: ByteWriter) {
        out.writeVarInt(t.basis.ordinal); out.writeMoney(t.perUnit); out.writeRate(t.percent); out.writeMoney(t.lumpSum)
        out.writeMoney(t.minimumPerPeriod); out.optMoney(t.capPerPeriod); out.writeMoney(t.upfront)
    }

    internal fun readTerms(r: ByteReader): RoyaltyTerms {
        val basis = pick(RoyaltyBasis.values(), r.readVarInt(), "royalty basis")
        val perUnit = r.readMoney(); val percent = r.readRate(); val lump = r.readMoney(); val min = r.readMoney(); val cap = r.optMoney(); val up = r.readMoney()
        try { return RoyaltyTerms(basis, perUnit, percent, lump, min, cap, up) } catch (e: IllegalArgumentException) { throw ByteReader.CorruptDataException("bad royalty terms: ${e.message}") }
    }

    private fun writeLicense(l: License, out: ByteWriter) {
        out.writeVarLong(l.id.value); out.owner(l.licensor); out.writeVarLong(l.licensee.value)
        out.writeVarInt(l.patents.size); for (p in l.patents) out.writeVarLong(p.value)
        out.writeVarInt(l.kind.ordinal)
        out.writeVarInt(l.scope.industries.size); for (i in l.scope.industries) out.writeVarInt(i.ordinal)
        out.writeVarInt(l.scope.countries.size); for (c in l.scope.countries) out.writeVarLong(c.value)
        writeTerms(l.terms, out)
        out.writeVarInt(l.termDays); out.writeBoolean(l.sublicensable); out.writeBoolean(l.frand); out.tick(l.offeredAt); out.tick(l.offerExpires)
        out.optId(l.poolId?.value); out.writeBoolean(l.compulsory)
        out.writeVarInt(l.status.ordinal); out.tick(l.startsAt); out.tick(l.endsAt); out.tick(l.endedAt); out.optId(l.crossWith?.value)
        out.writeMoney(l.paidTotal); out.tick(l.lastSettledAt); out.writeVarInt(l.strikes); out.writeMoney(l.arrears)
        out.writeQuantity(l.unitsReported); out.writeMoney(l.salesReported); out.writeVarInt(l.auditsThisYear); out.tick(l.lastAuditAt); out.writeMoney(l.recovered)
        out.writeMoney(l.dueTrue); out.writeMoney(l.dueReported); out.writeMoney(l.auditedTrue); out.writeMoney(l.auditedReported)
        out.writeMoney(l.lastDue); out.writeQuantity(l.lastUnits); out.writeMoney(l.lastSales)
    }

    private fun readLicense(r: ByteReader): License {
        val id = LicenseId(r.readVarLong()); val licensor = r.owner(); val licensee = CompanyId(r.readVarLong())
        val patents = List(r.readVarInt()) { PatentId(r.readVarLong()) }
        val kind = pick(LicenseKind.values(), r.readVarInt(), "licence kind")
        val industries = List(r.readVarInt()) { pick(IndustryFamily.values(), r.readVarInt(), "industry") }
        val countries = List(r.readVarInt()) { CountryId(r.readVarLong()) }
        val terms = readTerms(r)
        val termDays = r.readVarInt(); val sub = r.readBoolean(); val frand = r.readBoolean(); val offeredAt = r.needTick(); val expires = r.needTick()
        val pool = r.optId()?.let { PoolId(it) }; val compulsory = r.readBoolean()
        val l = License(id, licensor, licensee, patents, kind, LicenseScope(industries, countries), terms, termDays, sub, frand, offeredAt, expires, pool, compulsory)
        l.status = pick(LicenseStatus.values(), r.readVarInt(), "licence status")
        l.startsAt = r.tick(); l.endsAt = r.tick(); l.endedAt = r.tick(); l.crossWith = r.optId()?.let { LicenseId(it) }
        l.paidTotal = r.readMoney(); l.lastSettledAt = r.tick(); l.strikes = r.readVarInt(); l.arrears = r.readMoney()
        l.unitsReported = r.readQuantity(); l.salesReported = r.readMoney(); l.auditsThisYear = r.readVarInt(); l.lastAuditAt = r.tick(); l.recovered = r.readMoney()
        l.dueTrue = r.readMoney(); l.dueReported = r.readMoney(); l.auditedTrue = r.readMoney(); l.auditedReported = r.readMoney()
        l.lastDue = r.readMoney(); l.lastUnits = r.readQuantity(); l.lastSales = r.readMoney()
        return l
    }

    // ---- pools ----

    private fun writePool(p: PatentPool, out: ByteWriter) {
        out.writeVarLong(p.id.value); out.writeString(p.name); out.writeVarLong(p.administrator.value); out.writeVarInt(p.field.ordinal); out.tick(p.createdAt)
        writeTerms(p.terms, out); out.writeVarLong(p.adminFeePpm)
        out.writeBoolean(p.open); out.tick(p.dissolvedAt); out.writeMoney(p.collected); out.writeMoney(p.adminEarned)
        out.writeVarInt(p.memberMap.size)
        for (m in p.memberMap.values) {
            out.writeVarLong(m.company.value); out.tick(m.joinedAt); out.writeMoney(m.earned)
            out.writeVarInt(m.patentSet.size); for (id in m.patentSet) out.writeVarLong(id.value)
        }
    }

    private fun readPool(r: ByteReader): PatentPool {
        val p = PatentPool(PoolId(r.readVarLong()), r.readString(), CompanyId(r.readVarLong()), pick(TechField.values(), r.readVarInt(), "field"), r.needTick(), readTerms(r), r.readVarLong())
        p.open = r.readBoolean(); p.dissolvedAt = r.tick(); p.collected = r.readMoney(); p.adminEarned = r.readMoney()
        repeat(r.readVarInt()) {
            val m = PoolMember(CompanyId(r.readVarLong()), r.needTick())
            m.earned = r.readMoney()
            repeat(r.readVarInt()) { m.patentSet.add(PatentId(r.readVarLong())) }
            p.memberMap[m.company] = m
        }
        return p
    }

    // ---- disputes ----

    private fun writeDispute(d: Dispute, out: ByteWriter) {
        out.writeVarLong(d.id.value); out.writeVarInt(d.kind.ordinal); out.owner(d.claimant); out.owner(d.defendant)
        out.optId(d.patent?.value); out.optId(d.secret?.value); out.writeVarLong(d.country.value); out.tick(d.openedAt)
        out.writeVarInt(d.stage.ordinal); out.tick(d.stageSince); out.writeVarInt(d.result.ordinal); out.tick(d.closedAt)
        out.writeVarLong(d.claimantEvidencePpm); out.writeVarLong(d.defendantEvidencePpm); out.writeQuantity(d.infringingUnits); out.writeMoney(d.infringingSales)
        out.writeBoolean(d.willful); out.writeMoney(d.demand); out.writeMoney(d.claimantCosts); out.writeMoney(d.defendantCosts)
        out.writeMoney(d.claimantCostsPaid); out.writeMoney(d.defendantCostsPaid); out.writeMoney(d.award); out.writeBoolean(d.awardPaid)
        out.tick(d.injunctionUntil)
        val cr = d.courtRoyalty
        out.writeBoolean(cr != null); if (cr != null) writeTerms(cr, out)
        out.writeBoolean(d.patentRevoked); out.optOwner(d.appealedBy); out.tick(d.judgmentAt)
        out.writeVarInt(when (d.decidedForClaimant) { null -> -1; true -> 1; false -> 0 })
        out.optOwner(d.offerBy); out.writeMoney(d.offerAmount); out.tick(d.noticeAt)
    }

    private fun readDispute(r: ByteReader): Dispute {
        val id = DisputeId(r.readVarLong()); val kind = pick(DisputeKind.values(), r.readVarInt(), "dispute kind")
        val claimant = r.owner(); val defendant = r.owner()
        val patent = r.optId()?.let { PatentId(it) }; val secret = r.optId()?.let { SecretId(it) }
        val d = Dispute(id, kind, claimant, defendant, patent, secret, CountryId(r.readVarLong()), r.needTick())
        d.stage = pick(DisputeStage.values(), r.readVarInt(), "dispute stage"); d.stageSince = r.needTick()
        d.result = pick(DisputeResult.values(), r.readVarInt(), "dispute result"); d.closedAt = r.tick()
        d.claimantEvidencePpm = r.readVarLong(); d.defendantEvidencePpm = r.readVarLong(); d.infringingUnits = r.readQuantity(); d.infringingSales = r.readMoney()
        d.willful = r.readBoolean(); d.demand = r.readMoney(); d.claimantCosts = r.readMoney(); d.defendantCosts = r.readMoney()
        d.claimantCostsPaid = r.readMoney(); d.defendantCostsPaid = r.readMoney(); d.award = r.readMoney(); d.awardPaid = r.readBoolean()
        d.injunctionUntil = r.tick()
        d.courtRoyalty = if (r.readBoolean()) readTerms(r) else null
        d.patentRevoked = r.readBoolean(); d.appealedBy = r.optOwner(); d.judgmentAt = r.tick()
        d.decidedForClaimant = when (r.readVarInt()) { -1 -> null; 1 -> true; 0 -> false; else -> throw ByteReader.CorruptDataException("bad judgment flag") }
        d.offerBy = r.optOwner(); d.offerAmount = r.readMoney(); d.noticeAt = r.tick()
        return d
    }

    // ---- scripted holders and programmes ----

    private fun writeNpe(n: Npe, out: ByteWriter) {
        out.writeVarLong(n.id.value); out.writeString(n.name); out.writeVarInt(n.strategy.ordinal); out.writeVarLong(n.aggressionPpm); out.writeVarLong(n.settleFloorPpm)
        out.writeMoney(n.chest); out.writeVarInt(n.demandsSent); out.writeMoney(n.earned); out.writeMoney(n.spent); out.writeVarLong(n.lastActiveMonth)
    }

    private fun readNpe(r: ByteReader): Npe {
        val n = Npe(NpeId(r.readVarLong()), r.readString(), pick(NpeStrategy.values(), r.readVarInt(), "strategy"), r.readVarLong(), r.readVarLong())
        n.chest = r.readMoney(); n.demandsSent = r.readVarInt(); n.earned = r.readMoney(); n.spent = r.readMoney(); n.lastActiveMonth = r.readVarLong()
        return n
    }

    private fun writeDesign(d: DesignAround, out: ByteWriter) {
        out.writeVarLong(d.id.value); out.writeVarLong(d.company.value); out.writeVarLong(d.patent.value); out.tick(d.startedAt); out.tick(d.completesAt)
        out.writeMoney(d.cost); out.writeVarLong(d.successPpm); out.optId(d.dispute?.value); out.writeVarInt(d.status.ordinal); out.tick(d.endedAt)
    }

    private fun readDesign(r: ByteReader): DesignAround {
        val d = DesignAround(
            DesignAroundId(r.readVarLong()), CompanyId(r.readVarLong()), PatentId(r.readVarLong()), r.needTick(), r.needTick(),
            r.readMoney(), r.readVarLong(), r.optId()?.let { DisputeId(it) },
        )
        d.status = pick(DesignAroundStatus.values(), r.readVarInt(), "redesign status"); d.endedAt = r.tick()
        return d
    }

    private fun writeCompulsory(c: CompulsoryRequest, out: ByteWriter) {
        out.writeVarLong(c.id.value); out.writeVarLong(c.applicant.value); out.writeVarLong(c.patent.value); out.writeVarLong(c.country.value)
        out.writeVarInt(c.ground.ordinal); out.tick(c.filedAt); out.tick(c.decideAt); out.writeVarInt(c.status.ordinal); out.optId(c.licenseId?.value); out.writeString(c.reason)
    }

    private fun readCompulsory(r: ByteReader): CompulsoryRequest {
        val c = CompulsoryRequest(
            CompulsoryId(r.readVarLong()), CompanyId(r.readVarLong()), PatentId(r.readVarLong()), CountryId(r.readVarLong()),
            pick(CompulsoryGround.values(), r.readVarInt(), "ground"), r.needTick(), r.needTick(),
        )
        c.status = pick(CompulsoryStatus.values(), r.readVarInt(), "request status"); c.licenseId = r.optId()?.let { LicenseId(it) }; c.reason = r.readString()
        return c
    }
}
