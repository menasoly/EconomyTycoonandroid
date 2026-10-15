package app.many.economytycoon.risk

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.EventId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.PolicyId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick

/** Binary save format for policies, disasters and claims. */
object RiskCodec {
    const val VERSION = 1

    fun write(s: RiskService, out: ByteWriter) {
        out.writeVarInt(VERSION)
        out.writeBoolean(s.reserveSeeded)
        val policies = s.allPolicies()
        out.writeVarInt(policies.size)
        for (p in policies) {
            out.writeVarLong(p.id.value); out.writeVarLong(p.holder.value); out.writeVarLong(p.facility.value); out.writeVarLong(p.region.value)
            out.writeVarInt(p.level.ordinal); out.writeVarLong(p.coveragePpm); out.writeVarInt(p.deductible.ordinal); out.writeVarLong(p.boughtAt.value)
            out.writeVarInt(p.status.ordinal); out.writeMoney(p.insuredValue); out.writeMoney(p.premium); out.writeVarLong(p.paidThrough.value)
            out.writeMoney(p.premiumsPaid); out.writeMoney(p.claimsPaid); out.writeVarInt(p.claimCount)
            optional(out, p.lastClaim?.value); out.writeVarLong(p.surchargePpm); optional(out, p.endedAt?.value)
        }
        val disasters = s.disasters()
        out.writeVarInt(disasters.size)
        for (d in disasters) {
            out.writeVarLong(d.id.value); out.writeVarInt(d.peril.ordinal); out.writeVarLong(d.region.value); out.writeVarLong(d.tick.value)
            out.writeVarLong(d.severityPpm); out.writeVarInt(d.facilitiesHit); out.writeMoney(d.totalLoss); out.writeMoney(d.insuredLoss)
        }
        val claims = s.claims()
        out.writeVarInt(claims.size)
        for (c in claims) {
            out.writeVarLong(c.disaster.value); out.writeVarInt(c.peril.ordinal); optional(out, c.policy?.value); out.writeVarLong(c.holder.value)
            out.writeVarLong(c.facility.value); out.writeVarLong(c.tick.value); out.writeMoney(c.buildingLoss); out.writeMoney(c.stockLoss)
            out.writeMoney(c.payout); out.writeMoney(c.shortfall)
        }
    }

    fun read(r: ByteReader, s: RiskService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported risk save version $version")
        s.clearAll()
        s.reserveSeeded = r.readBoolean()
        repeat(r.readVarInt()) {
            val id = PolicyId(r.readVarLong()); val holder = CompanyId(r.readVarLong()); val facility = FacilityId(r.readVarLong()); val region = RegionId(r.readVarLong())
            val level = enumAt(CoverLevel.values(), r.readVarInt(), "cover level")
            val coverage = r.readVarLong()
            val deductible = enumAt(Deductible.values(), r.readVarInt(), "deductible")
            val p = Policy(id, holder, facility, region, level, coverage, deductible, Tick(r.readVarLong()))
            val status = enumAt(PolicyStatus.values(), r.readVarInt(), "policy status")
            p.restore(
                status, r.readMoney(), r.readMoney(), Tick(r.readVarLong()), r.readMoney(), r.readMoney(), r.readVarInt(),
                optional(r)?.let { Tick(it) }, r.readVarLong(), optional(r)?.let { Tick(it) },
            )
            s.restore(p)
        }
        repeat(r.readVarInt()) {
            s.restoreDisaster(
                Disaster(
                    EventId(r.readVarLong()), enumAt(Peril.values(), r.readVarInt(), "peril"), RegionId(r.readVarLong()), Tick(r.readVarLong()),
                    r.readVarLong(), r.readVarInt(), r.readMoney(), r.readMoney(),
                ),
            )
        }
        repeat(r.readVarInt()) {
            s.restoreClaim(
                Claim(
                    EventId(r.readVarLong()), enumAt(Peril.values(), r.readVarInt(), "peril"), optional(r)?.let { PolicyId(it) }, CompanyId(r.readVarLong()),
                    FacilityId(r.readVarLong()), Tick(r.readVarLong()), r.readMoney(), r.readMoney(), r.readMoney(), r.readMoney(),
                ),
            )
        }
    }

    private fun optional(w: ByteWriter, v: Long?) { w.writeBoolean(v != null); if (v != null) w.writeVarLong(v) }
    private fun optional(r: ByteReader): Long? = if (r.readBoolean()) r.readVarLong() else null

    private fun <T> enumAt(values: Array<T>, index: Int, what: String): T =
        values.getOrNull(index) ?: throw ByteReader.CorruptDataException("unknown $what $index")
}
