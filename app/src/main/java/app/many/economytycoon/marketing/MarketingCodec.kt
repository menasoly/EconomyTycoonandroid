package app.many.economytycoon.marketing

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick

/** Binary save format for the marketing section: campaigns, brand stocks, promotions, split tests and the damage log. */
object MarketingCodec {
    const val VERSION = 1

    fun write(s: MarketingState, out: ByteWriter) {
        out.writeVarInt(VERSION)
        val counters = s.ids.snapshot()
        out.writeVarInt(counters.size)
        for (n in counters) out.writeVarLong(n)

        out.writeVarInt(s.campaignMap.size)
        for (c in s.campaignMap.values) writeCampaign(c, out)

        out.writeVarInt(s.stockMap.size)
        for ((k, st) in s.stockMap) {
            out.writeVarLong(k.company.value); out.writeString(k.good.key); out.writeVarLong(k.region.value)
            out.writeVarLong(st.awarenessPpm); out.writeVarLong(st.loyaltyPpm); out.writeVarLong(st.taintPpm)
            val ad = st.adstockSnapshot()
            out.writeVarInt(ad.size)
            for (a in ad) out.writeVarLong(a)
        }

        out.writeVarInt(s.promotionMap.size)
        for (p in s.promotionMap.values) {
            out.writeVarLong(p.id.value); out.writeVarLong(p.company.value); out.writeString(p.good.key); out.writeVarLong(p.region.value)
            out.writeVarLong(p.startTick.value); out.writeVarInt(p.days); out.writeVarLong(p.discountPpm)
            out.writeMoney(p.listPrice); out.writeMoney(p.unitCost); out.writeVarLong(p.expectedUpliftPpm)
            out.writeVarInt(p.status.ordinal); writeOptionalTick(out, p.endedAt)
        }

        out.writeVarInt(s.testMap.size)
        for (t in s.testMap.values) {
            out.writeVarLong(t.id.value); out.writeVarLong(t.campaign.value); out.writeVarLong(t.qualityA); out.writeVarLong(t.qualityB)
            out.writeVarLong(t.startTick.value); out.writeVarInt(t.days); out.writeVarInt(t.outcome.ordinal)
            out.writeVarLong(t.impressions); out.writeVarLong(t.responsesA); out.writeVarLong(t.responsesB); out.writeVarLong(t.zHundredths)
            writeOptionalTick(out, t.decidedAt)
        }

        out.writeVarInt(s.damageMap.size)
        for (d in s.damageMap.values) {
            out.writeVarLong(d.id.value); out.writeVarLong(d.company.value)
            out.writeBoolean(d.good != null); d.good?.let { out.writeString(it.key) }
            out.writeBoolean(d.region != null); d.region?.let { out.writeVarLong(it.value) }
            out.writeVarInt(d.kind.ordinal); out.writeVarLong(d.severityPpm); out.writeVarLong(d.tick.value); out.writeVarLong(d.awarenessLostPpm)
        }
    }

    fun read(r: ByteReader, s: MarketingState) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported marketing save version $version")
        s.clear()
        val counterCount = r.readVarInt()
        val counters = LongArray(counterCount) { r.readVarLong() }
        if (counterCount != 4) throw ByteReader.CorruptDataException("bad marketing id counters")
        s.ids.restore(counters)

        repeat(r.readVarInt()) { val c = readCampaign(r); s.campaignMap[c.id] = c }

        repeat(r.readVarInt()) {
            val key = BrandKey(CompanyId(r.readVarLong()), GoodId(r.readString()), RegionId(r.readVarLong()))
            val awareness = r.readVarLong(); val loyalty = r.readVarLong(); val taint = r.readVarLong()
            val ad = LongArray(r.readVarInt().also { n -> if (n < 0 || n > MAX_CHANNELS) throw ByteReader.CorruptDataException("bad adstock size $n") }) { r.readVarLong() }
            s.stockMap[key] = BrandStock().also { it.restore(awareness, loyalty, taint, ad) }
        }

        repeat(r.readVarInt()) {
            val p = Promotion(
                PromotionId(r.readVarLong()), CompanyId(r.readVarLong()), GoodId(r.readString()), RegionId(r.readVarLong()),
                Tick(r.readVarLong()), r.readVarInt(), r.readVarLong(), r.readMoney(), r.readMoney(), r.readVarLong(),
            )
            p.restore(pick(PromotionStatus.values(), r.readVarInt(), "promotion status"), readOptionalTick(r))
            s.promotionMap[p.id] = p
        }

        repeat(r.readVarInt()) {
            val t = AbTest(AbTestId(r.readVarLong()), CampaignId(r.readVarLong()), r.readVarLong(), r.readVarLong(), Tick(r.readVarLong()), r.readVarInt())
            val outcome = pick(AbOutcome.values(), r.readVarInt(), "test outcome")
            t.restore(outcome, r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), readOptionalTick(r))
            s.testMap[t.id] = t
        }

        repeat(r.readVarInt()) {
            val id = DamageId(r.readVarLong()); val company = CompanyId(r.readVarLong())
            val good = if (r.readBoolean()) GoodId(r.readString()) else null
            val region = if (r.readBoolean()) RegionId(r.readVarLong()) else null
            val record = DamageRecord(id, company, good, region, pick(DamageKind.values(), r.readVarInt(), "damage kind"), r.readVarLong(), Tick(r.readVarLong()), r.readVarLong())
            s.damageMap[id] = record
        }
    }

    private fun writeCampaign(c: Campaign, out: ByteWriter) {
        out.writeVarLong(c.id.value); out.writeVarLong(c.company.value); out.writeString(c.good.key); out.writeVarLong(c.region.value)
        out.writeBoolean(c.city != null); c.city?.let { out.writeVarLong(it.value) }
        out.writeVarInt(c.channel.ordinal); out.writeVarInt(c.segment.ordinal)
        out.writeVarLong(c.createdAt.value); out.writeVarLong(c.startTick.value)
        out.writeMoney(c.dailyBudget)
        out.writeVarLong(c.creative.qualityPpm); out.writeVarLong(c.creative.freshnessPpm); out.writeVarInt(c.creative.version)
        writeOptionalTick(out, c.creative.lastRefreshed)
        out.writeVarInt(c.status.ordinal); out.writeVarInt(c.daysRemaining); out.writeVarInt(c.daysBooked); out.writeVarInt(c.daysRun)
        out.writeVarInt(c.pausedDays)
        out.writeMoney(c.prepaid); out.writeMoney(c.spentTotal); out.writeMoney(c.refundedTotal); out.writeMoney(c.feesTotal)
        out.writeVarInt(c.missedPayments); out.writeVarLong(c.impressions); out.writeVarLong(c.lastReachPpm)
        writeOptionalTick(out, c.endedAt); writeOptionalTick(out, c.lastPaidTick)
    }

    private fun readCampaign(r: ByteReader): Campaign {
        val id = CampaignId(r.readVarLong()); val company = CompanyId(r.readVarLong()); val good = GoodId(r.readString())
        val region = RegionId(r.readVarLong())
        val city = if (r.readBoolean()) CityId(r.readVarLong()) else null
        val channel = pick(Channel.values(), r.readVarInt(), "channel")
        val segment = pick(TargetSegment.values(), r.readVarInt(), "segment")
        val created = Tick(r.readVarLong()); val start = Tick(r.readVarLong())
        val budget = r.readMoney()
        val creative = Creative(r.readVarLong(), r.readVarLong()).also { cr ->
            val version = r.readVarInt()
            cr.restore(cr.qualityPpm, cr.freshnessPpm, version, readOptionalTick(r))
        }
        val status = pick(CampaignStatus.values(), r.readVarInt(), "campaign status")
        val remaining = r.readVarInt(); val booked = r.readVarInt(); val run = r.readVarInt(); val paused = r.readVarInt()
        val c = Campaign(id, company, good, region, city, channel, segment, created, start, budget, booked, creative)
        c.restore(
            status, remaining, booked, run, paused, r.readMoney(), r.readMoney(), r.readMoney(), r.readMoney(),
            r.readVarInt(), r.readVarLong(), r.readVarLong(), readOptionalTick(r), readOptionalTick(r),
        )
        return c
    }

    private fun writeOptionalTick(out: ByteWriter, t: Tick?) { out.writeBoolean(t != null); t?.let { out.writeVarLong(it.value) } }
    private fun readOptionalTick(r: ByteReader): Tick? = if (r.readBoolean()) Tick(r.readVarLong()) else null

    private fun <T> pick(all: Array<T>, index: Int, what: String): T = all.getOrNull(index) ?: throw ByteReader.CorruptDataException("unknown $what $index")

    private const val MAX_CHANNELS = 64
}
