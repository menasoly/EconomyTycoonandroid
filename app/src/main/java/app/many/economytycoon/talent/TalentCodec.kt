package app.many.economytycoon.talent

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.workforce.CohortKey

/**
 * Binary save format of the talent state. A version byte leads; every partition follows in a fixed order, each as a
 * count and its rows in key order, so equal states always give equal bytes.
 */
object TalentCodec {
    const val VERSION = 1

    fun encode(state: TalentState): ByteArray = ByteWriter().also { write(state, it) }.toByteArray()

    fun decode(bytes: ByteArray): TalentState = TalentState().also { read(ByteReader(bytes), it) }

    // ----- small pieces -------------------------------------------------------------------------------------

    private fun ByteWriter.tick(t: Tick) = writeVarLong(t.value)
    private fun ByteWriter.optTick(t: Tick?) { writeBoolean(t != null); if (t != null) tick(t) }
    private fun ByteWriter.optLong(v: Long?) { writeBoolean(v != null); if (v != null) writeVarLong(v) }
    private fun ByteWriter.key(k: CohortKey) { writeVarLong(k.company.value); writeVarLong(k.facility.value); writeString(k.role.key); writeVarLong(k.region.value) }
    private fun ByteWriter.optKey(k: CohortKey?) { writeBoolean(k != null); if (k != null) key(k) }
    private fun ByteWriter.dist(d: SkillDistribution) {
        for (c in d.counts()) writeVarLong(c)
        for (c in d.carries()) writeVarLong(c)
    }

    private fun ByteReader.tick() = Tick(readVarLong())
    private fun ByteReader.optTick(): Tick? = if (readBoolean()) tick() else null
    private fun ByteReader.optLong(): Long? = if (readBoolean()) readVarLong() else null
    private fun ByteReader.key() = CohortKey(CompanyId(readVarLong()), FacilityId(readVarLong()), RoleId(readString()), RegionId(readVarLong()))
    private fun ByteReader.optKey(): CohortKey? = if (readBoolean()) key() else null
    private fun ByteReader.dist(): SkillDistribution {
        val counts = LongArray(SkillDistribution.BUCKETS) { readVarLong() }
        val carry = LongArray(SkillDistribution.BUCKETS) { readVarLong() }
        return SkillDistribution.restore(counts, carry)
    }

    private fun <T> pick(all: Array<T>, index: Int, what: String): T = all.getOrNull(index) ?: throw ByteReader.CorruptDataException("unknown $what $index")

    // ----- write --------------------------------------------------------------------------------------------------------

    fun write(s: TalentState, out: ByteWriter) {
        out.writeVarInt(VERSION)
        val counters = s.counters.snapshot()
        out.writeVarInt(counters.size)
        for ((k, v) in counters) { out.writeString(k); out.writeVarLong(v) }

        out.writeVarInt(s.institutions.size)
        for (i in s.institutions.values) {
            out.writeVarLong(i.id.value); out.writeVarLong(i.country.value); out.writeVarLong(i.region.value); out.writeVarInt(i.kind.ordinal)
            out.optLong(i.owner?.value); out.tick(i.openedAt)
            out.writeString(i.name); out.writeVarLong(i.capacity); out.writeMoney(i.tuition); out.writeMoney(i.spend); out.writeVarLong(i.qualityPpm)
            out.writeVarInt(i.fields.size); for (f in i.fields) out.writeString(f.key)
            out.optTick(i.closedAt); out.writeVarLong(i.graduatesTotal); out.writeVarLong(i.droppedTotal); out.writeVarLong(i.unmetLastIntake)
            out.writeMoney(i.revenueYear); out.writeMoney(i.costYear)
            out.writeVarInt(i.intakes.size)
            for (n in i.intakes) { out.writeVarLong(n.startTick); out.writeString(n.skill.key); out.writeVarLong(n.initial); out.writeVarLong(n.completionPpm); out.writeVarLong(n.enrolled); out.writeVarLong(n.dropCarry) }
            out.writeVarInt(i.expansions.size)
            for (e in i.expansions) { out.tick(e.readyAt); out.writeVarLong(e.seats) }
        }
        out.writeVarInt(s.qualifications.size)
        for ((r, q) in s.qualifications) { out.writeVarLong(r.value); for (v in q.byKind) out.writeVarLong(v) }
        out.writeVarInt(s.leavers.size)
        for ((r, d) in s.leavers) { out.writeVarLong(r.value); out.dist(d) }

        out.writeVarInt(s.pools.size)
        for ((k, d) in s.pools) { out.writeVarLong(k.region.value); out.writeString(k.skill.key); out.dist(d) }
        out.writeVarInt(s.markets.size)
        for ((k, m) in s.markets) {
            out.writeVarLong(k.region.value); out.writeString(k.skill.key)
            out.writeVarLong(m.supply); out.writeVarLong(m.demandFlow); out.writeVarLong(m.employed); out.writeVarLong(m.tightnessPpm); out.writeVarLong(m.premiumPpm)
            out.writeVarLong(m.poolMeanPpm); out.writeVarLong(m.requiredPpm); out.writeVarInt(m.shortage.ordinal); out.writeVarLong(m.shortageSince); out.writeVarLong(m.inflowYear)
        }

        out.writeVarInt(s.cohortSkills.size)
        for (c in s.cohortSkills.values) {
            out.key(c.key)
            out.writeVarInt(c.dists.size); for ((sk, d) in c.dists) { out.writeString(sk.key); out.dist(d) }
            out.writeVarInt(c.certified.size); for ((sk, v) in c.certified) { out.writeString(sk.key); out.writeVarLong(v) }
        }

        out.writeVarInt(s.programmes.size)
        for (p in s.programmes.values) {
            out.writeVarLong(p.id.value); out.writeVarLong(p.company.value); out.key(p.cohort); out.writeString(p.skill.key); out.writeVarInt(p.kind.ordinal)
            out.writeVarLong(p.seats); out.tick(p.startTick); out.tick(p.endTick); out.writeMoney(p.costPerHead)
            out.writeBoolean(p.clause != null); if (p.clause != null) { out.writeVarInt(p.clause.bondDays); out.writeVarLong(p.clause.clawbackPpm) }
            out.optLong(p.provider?.value); out.optLong(p.course?.value); out.optLong(p.fee?.raw)
            out.writeVarInt(p.status.ordinal); out.writeMoney(p.paid); out.writeVarLong(p.gainDelivered); out.optTick(p.finishedAt)
            out.writeVarLong(p.bondedHeads); out.writeVarLong(p.leaversCarry); out.writeMoney(p.clawedBack)
        }

        out.writeVarInt(s.policies.size)
        for (p in s.policies.values) { out.writeVarLong(p.country.value); out.writeVarLong(p.opennessPpm); out.writeVarLong(p.quotaPpm); out.writeBoolean(p.skilledOnly) }
        out.writeVarInt(s.diaspora.size)
        for ((k, d) in s.diaspora) { out.writeVarLong(k.from.value); out.writeVarLong(k.to.value); out.writeString(k.skill.key); out.dist(d) }
        out.writeVarInt(s.ledgers.size)
        for ((c, l) in s.ledgers) { out.writeVarLong(c.value); out.writeVarLong(l.out); out.writeVarLong(l.skilledOut); out.writeVarLong(l.arrived); out.writeVarLong(l.returned); out.writeVarLong(l.visasUsed) }

        out.writeVarInt(s.people.size)
        for (p in s.people.values) {
            out.writeVarLong(p.id.value); out.writeVarLong(p.company.value); out.writeString(p.name); out.writeString(p.role.key); out.writeVarLong(p.region.value); out.tick(p.hiredAt)
            out.writeVarInt(p.ageYears); out.writeMoney(p.wage); out.writeVarLong(p.loyaltyPpm); out.writeVarLong(p.potentialPpm); out.optLong(p.position?.value); out.optTick(p.retiredAt)
            out.writeVarInt(p.levels.size); for ((sk, v) in p.levels) { out.writeString(sk.key); out.writeVarLong(v) }
        }
        out.writeVarInt(s.positions.size)
        for (p in s.positions.values) {
            out.writeVarLong(p.id.value); out.writeVarLong(p.company.value); out.writeString(p.role.key); out.writeVarLong(p.region.value); out.writeString(p.title)
            out.optLong(p.holder?.value); out.optTick(p.vacantSince); out.optTick(p.settlingUntil); out.writeVarLong(p.settlingPpm); out.optTick(p.searchUntil); out.writeMoney(p.searchSpent)
        }
        out.writeVarInt(s.successors.size)
        for ((pos, m) in s.successors) {
            out.writeVarLong(pos.value); out.writeVarInt(m.size)
            for (c in m.values) { out.writeVarLong(c.person.value); out.writeVarLong(c.readinessPpm); out.writeVarInt(c.development.ordinal); out.tick(c.namedAt) }
        }

        out.writeVarInt(s.offers.size)
        for (o in s.offers.values) {
            out.writeVarLong(o.id.value); out.writeVarLong(o.hunter.value); out.writeVarLong(o.victim.value)
            when (val t = o.target) {
                is PoachTarget.Group -> { out.writeByte(1); out.key(t.cohort); out.writeVarLong(t.count); out.writeVarLong(t.minLevelPpm) }
                is PoachTarget.Individual -> { out.writeByte(2); out.writeVarLong(t.person.value) }
            }
            out.key(o.destination); out.writeMoney(o.offeredWage); out.writeMoney(o.signingBonus); out.writeVarInt(o.stance.ordinal)
            out.tick(o.submittedAt); out.tick(o.resolveAt); out.writeVarLong(o.heads)
            out.writeVarInt(o.status.ordinal); out.writeVarLong(o.movedHeads); out.writeVarLong(o.keptHeads); out.optLong(o.counterWage?.raw); out.writeVarLong(o.oddsPpm)
            out.writeMoney(o.feePaid); out.optTick(o.resolvedAt)
        }
        out.writeVarInt(s.covenants.size)
        for (c in s.covenants.values) {
            out.writeVarLong(c.id.value); out.writeVarLong(c.company.value); out.optKey(c.cohort); out.optLong(c.person?.value); out.optLong(c.region?.value)
            out.writeVarLong(c.headsCovered); out.tick(c.signedAt); out.tick(c.until); out.writeMoney(c.penaltyPerHead); out.writeVarInt(c.breaches); out.writeBoolean(c.lapsed)
        }
        out.writeVarInt(s.counterPolicies.size)
        for (c in s.counterPolicies.values) { out.writeVarLong(c.company.value); out.writeBoolean(c.enabled); out.writeVarLong(c.maxMatchPpm) }
        out.writeVarInt(s.retention.size)
        for (r in s.retention.values) { out.key(r.cohort); out.tick(r.until); out.writeVarLong(r.strengthPpm) }
        out.writeVarInt(s.cooldowns.size)
        for ((k, t) in s.cooldowns) { out.writeVarLong(k.hunter.value); out.key(k.cohort); out.tick(t) }

        out.writeVarInt(s.brands.size)
        for ((k, b) in s.brands) { out.writeVarLong(k.company.value); out.writeVarLong(k.region.value); out.writeVarLong(b.awarenessPpm); out.writeVarLong(b.appealPpm) }
        out.writeVarInt(s.campaigns.size)
        for (c in s.campaigns.values) {
            out.writeVarLong(c.id.value); out.writeVarLong(c.company.value); out.writeVarLong(c.region.value); out.writeString(c.role.key); out.writeMoney(c.budget)
            out.tick(c.startTick); out.tick(c.endTick); out.writeVarInt(c.status.ordinal); out.writeVarLong(c.hired); out.writeVarLong(c.carryPpm)
        }
        out.writeVarInt(s.ladders.size)
        for (l in s.ladders.values) {
            out.writeVarLong(l.id.value); out.writeVarLong(l.company.value); out.writeString(l.name)
            out.writeVarInt(l.rungs.size); for (r in l.rungs) { out.key(r.cohort); out.writeVarLong(r.minTenureTicks); out.writeVarLong(r.minLevelPpm); out.writeVarLong(r.raisePpm) }
            out.writeBoolean(l.enabled); out.writeVarLong(l.promotions); out.tick(l.lastReview)
            for (c in l.carry) out.writeVarLong(c)
        }

        out.writeVarInt(s.providers.size)
        for (p in s.providers.values) {
            out.writeVarLong(p.id.value); out.writeVarLong(p.owner.value); out.writeVarLong(p.region.value); out.writeString(p.name); out.tick(p.openedAt)
            out.writeVarLong(p.qualityPpm); out.writeVarLong(p.teachingLevelPpm); out.writeVarInt(p.accreditation.ordinal); out.tick(p.accreditedUntil)
            out.writeVarLong(p.reputationPpm); out.writeBoolean(p.closed); out.writeMoney(p.revenue); out.writeMoney(p.costs)
            out.writeVarLong(p.graduates); out.writeVarLong(p.dropouts); out.writeVarLong(p.gainSumPpm)
            out.writeVarInt(p.courses.size)
            for (c in p.courses.values) {
                out.writeVarLong(c.id.value); out.writeString(c.name); out.writeString(c.skill.key); out.writeVarInt(c.kind.ordinal); out.writeVarLong(c.seats); out.writeMoney(c.tuition)
                out.writeBoolean(c.open); out.writeVarLong(c.enrolledTotal); out.writeVarLong(c.completedTotal)
                out.writeVarInt(c.intakes.size)
                for (i in c.intakes) { out.tick(i.start); out.tick(i.end); out.writeVarLong(i.startMean); out.dist(i.students) }
            }
        }
    }

    // ----- read ---------------------------------------------------------------------------------------------------------

    fun read(r: ByteReader, s: TalentState) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported talent save version $version")
        s.clear()
        val counters = java.util.TreeMap<String, Long>()
        repeat(r.readVarInt()) { val k = r.readString(); counters[k] = r.readVarLong() }
        s.counters.restore(counters)

        repeat(r.readVarInt()) {
            val id = InstitutionId(r.readVarLong()); val country = CountryId(r.readVarLong()); val region = RegionId(r.readVarLong())
            val kind = pick(InstitutionKind.values(), r.readVarInt(), "institution kind")
            val owner = r.optLong()?.let { CompanyId(it) }
            val i = Institution(id, country, region, kind, owner, r.tick())
            i.name = r.readString(); i.capacity = r.readVarLong(); i.tuition = r.readMoney(); i.spend = r.readMoney(); i.qualityPpm = r.readVarLong()
            i.fields = List(r.readVarInt()) { SkillId(r.readString()) }
            i.closedAt = r.optTick(); i.graduatesTotal = r.readVarLong(); i.droppedTotal = r.readVarLong(); i.unmetLastIntake = r.readVarLong()
            i.revenueYear = r.readMoney(); i.costYear = r.readMoney()
            repeat(r.readVarInt()) {
                val n = Intake(r.readVarLong(), SkillId(r.readString()), r.readVarLong(), r.readVarLong())
                n.enrolled = r.readVarLong(); n.dropCarry = r.readVarLong(); i.intakes.add(n)
            }
            repeat(r.readVarInt()) { i.expansions.add(Expansion(r.tick(), r.readVarLong())) }
            s.institutions[id] = i
        }
        repeat(r.readVarInt()) { val region = RegionId(r.readVarLong()); val q = QualificationStock(); for (k in q.byKind.indices) q.byKind[k] = r.readVarLong(); s.qualifications[region] = q }
        repeat(r.readVarInt()) { val region = RegionId(r.readVarLong()); s.leavers[region] = r.dist() }

        repeat(r.readVarInt()) { val k = PoolKey(RegionId(r.readVarLong()), SkillId(r.readString())); s.pools[k] = r.dist() }
        repeat(r.readVarInt()) {
            val k = PoolKey(RegionId(r.readVarLong()), SkillId(r.readString()))
            val m = SkillMarket()
            m.restore(r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(),
                pick(ShortageKind.values(), r.readVarInt(), "shortage kind"), r.readVarLong(), r.readVarLong())
            s.markets[k] = m
        }

        repeat(r.readVarInt()) {
            val set = CohortSkillSet(r.key())
            repeat(r.readVarInt()) { val sk = SkillId(r.readString()); set.dists[sk] = r.dist() }
            repeat(r.readVarInt()) { val sk = SkillId(r.readString()); set.certified[sk] = r.readVarLong() }
            s.cohortSkills[set.key] = set
        }

        repeat(r.readVarInt()) {
            val id = ProgrammeId(r.readVarLong()); val company = CompanyId(r.readVarLong()); val cohort = r.key(); val skill = SkillId(r.readString())
            val kind = pick(ProgrammeKind.values(), r.readVarInt(), "programme kind")
            val seats = r.readVarLong(); val start = r.tick(); val end = r.tick(); val cost = r.readMoney()
            val clause = if (r.readBoolean()) RetentionClause(r.readVarInt(), r.readVarLong()) else null
            val provider = r.optLong()?.let { ProviderId(it) }; val course = r.optLong()?.let { CourseId(it) }; val fee = r.optLong()?.let { Money(it) }
            val p = TrainingProgramme(id, company, cohort, skill, kind, seats, start, end, cost, clause, provider, course, fee)
            p.restore(pick(ProgrammeStatus.values(), r.readVarInt(), "programme status"), r.readMoney(), r.readVarLong(), r.optTick(), r.readVarLong(), r.readVarLong(), r.readMoney())
            s.programmes[id] = p
        }

        repeat(r.readVarInt()) { val c = CountryId(r.readVarLong()); val p = CountryPolicy(c); p.restore(r.readVarLong(), r.readVarLong(), r.readBoolean()); s.policies[c] = p }
        repeat(r.readVarInt()) { val k = DiasporaKey(CountryId(r.readVarLong()), CountryId(r.readVarLong()), SkillId(r.readString())); s.diaspora[k] = r.dist() }
        repeat(r.readVarInt()) { val c = CountryId(r.readVarLong()); val l = MigrationLedger(); l.restore(r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong()); s.ledgers[c] = l }

        repeat(r.readVarInt()) {
            val id = PersonId(r.readVarLong()); val p = Person(id, CompanyId(r.readVarLong()), r.readString(), RoleId(r.readString()), RegionId(r.readVarLong()), r.tick())
            val age = r.readVarInt(); val wage = r.readMoney(); val loyalty = r.readVarLong(); val potential = r.readVarLong()
            val position = r.optLong()?.let { PositionId(it) }; val retired = r.optTick()
            val levels = java.util.TreeMap<SkillId, Long>()
            repeat(r.readVarInt()) { val sk = SkillId(r.readString()); levels[sk] = r.readVarLong() }
            p.restore(age, wage, loyalty, potential, position, retired, levels)
            s.people[id] = p
        }
        repeat(r.readVarInt()) {
            val p = KeyPosition(PositionId(r.readVarLong()), CompanyId(r.readVarLong()), RoleId(r.readString()), RegionId(r.readVarLong()), r.readString())
            p.restore(r.optLong()?.let { PersonId(it) }, r.optTick(), r.optTick(), r.readVarLong(), r.optTick(), r.readMoney())
            s.positions[p.id] = p
        }
        repeat(r.readVarInt()) {
            val pos = PositionId(r.readVarLong()); val m = java.util.TreeMap<PersonId, Successor>()
            repeat(r.readVarInt()) {
                val who = PersonId(r.readVarLong())
                m[who] = Successor(who, r.readVarLong(), pick(DevelopmentKind.values(), r.readVarInt(), "development kind"), r.tick())
            }
            s.successors[pos] = m
        }

        repeat(r.readVarInt()) {
            val id = OfferId(r.readVarLong()); val hunter = CompanyId(r.readVarLong()); val victim = CompanyId(r.readVarLong())
            val target = when (val tag = r.readByte()) {
                1 -> PoachTarget.Group(r.key(), r.readVarLong(), r.readVarLong())
                2 -> PoachTarget.Individual(PersonId(r.readVarLong()))
                else -> throw ByteReader.CorruptDataException("unknown poach target $tag")
            }
            val o = HeadhuntOffer(id, hunter, victim, target, r.key(), r.readMoney(), r.readMoney(), pick(CovenantStance.values(), r.readVarInt(), "covenant stance"), r.tick(), r.tick(), r.readVarLong())
            o.restore(pick(OfferStatus.values(), r.readVarInt(), "offer status"), r.readVarLong(), r.readVarLong(), r.optLong()?.let { Money(it) }, r.readVarLong(), r.readMoney(), r.optTick())
            s.offers[id] = o
        }
        repeat(r.readVarInt()) {
            val c = NonCompete(CovenantId(r.readVarLong()), CompanyId(r.readVarLong()), r.optKey(), r.optLong()?.let { PersonId(it) }, r.optLong()?.let { RegionId(it) },
                r.readVarLong(), r.tick(), r.tick(), r.readMoney())
            c.restore(r.readVarInt(), r.readBoolean())
            s.covenants[c.id] = c
        }
        repeat(r.readVarInt()) { val c = CounterPolicy(CompanyId(r.readVarLong())); c.restore(r.readBoolean(), r.readVarLong()); s.counterPolicies[c.company] = c }
        repeat(r.readVarInt()) { val g = RetentionGrant(r.key(), r.tick(), r.readVarLong()); s.retention[g.cohort] = g }
        repeat(r.readVarInt()) { val k = CooldownKey(CompanyId(r.readVarLong()), r.key()); s.cooldowns[k] = r.tick() }

        repeat(r.readVarInt()) { val k = BrandKey(CompanyId(r.readVarLong()), RegionId(r.readVarLong())); val b = EmployerBrand(); b.restore(r.readVarLong(), r.readVarLong()); s.brands[k] = b }
        repeat(r.readVarInt()) {
            val c = RecruitCampaign(RecruitCampaignId(r.readVarLong()), CompanyId(r.readVarLong()), RegionId(r.readVarLong()), RoleId(r.readString()), r.readMoney(), r.tick(), r.tick())
            c.restore(pick(CampaignStatus.values(), r.readVarInt(), "campaign status"), r.readVarLong(), r.readVarLong())
            s.campaigns[c.id] = c
        }
        repeat(r.readVarInt()) {
            val id = LadderId(r.readVarLong()); val company = CompanyId(r.readVarLong()); val name = r.readString()
            val rungs = List(r.readVarInt()) { Rung(r.key(), r.readVarLong(), r.readVarLong(), r.readVarLong()) }
            val l = CareerLadder(id, company, name, rungs)
            val enabled = r.readBoolean(); val promotions = r.readVarLong(); val last = r.tick()
            l.restore(enabled, promotions, last, List(rungs.size) { r.readVarLong() })
            s.ladders[id] = l
        }

        repeat(r.readVarInt()) {
            val p = TrainingProvider(ProviderId(r.readVarLong()), CompanyId(r.readVarLong()), RegionId(r.readVarLong()), r.readString(), r.tick())
            p.restore(r.readVarLong(), r.readVarLong(), pick(Accreditation.values(), r.readVarInt(), "accreditation"), r.tick(), r.readVarLong(), r.readBoolean(),
                r.readMoney(), r.readMoney(), r.readVarLong(), r.readVarLong(), r.readVarLong())
            repeat(r.readVarInt()) {
                val c = Course(CourseId(r.readVarLong()), p.id, r.readString(), SkillId(r.readString()), pick(CourseKind.values(), r.readVarInt(), "course kind"), r.readVarLong(), r.readMoney())
                c.restore(r.readBoolean(), r.readVarLong(), r.readVarLong())
                repeat(r.readVarInt()) { val st = r.tick(); val en = r.tick(); val mean = r.readVarLong(); c.intakes.add(CourseIntake(st, en, r.dist(), mean)) }
                p.courses[c.id] = c
            }
            s.providers[p.id] = p
        }
    }
}
