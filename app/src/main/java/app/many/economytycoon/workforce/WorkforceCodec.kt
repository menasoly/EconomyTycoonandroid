package app.many.economytycoon.workforce

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CohortId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Tick

/** Binary save format for the workforce engine. */
object WorkforceCodec {
    const val VERSION = 1

    fun write(w: WorkforceService, out: ByteWriter) {
        out.writeVarInt(VERSION)
        out.writeVarLong(w.lastPay().value)
        val cohorts = w.all()
        out.writeVarInt(cohorts.size)
        for (c in cohorts) {
            out.writeVarLong(c.id.value)
            out.writeVarLong(c.key.company.value); out.writeVarLong(c.key.facility.value); out.writeString(c.key.role.key); out.writeVarLong(c.key.region.value)
            out.writeMoney(c.wage)
            out.writeVarLong(c.headcount); out.writeVarLong(c.target); out.writeVarLong(c.skillPpm); out.writeVarLong(c.moralePpm)
            out.writeVarLong(c.burnoutPpm); out.writeVarLong(c.tenureTicks); out.writeVarLong(c.trainingPpm); out.writeVarLong(c.strikeUntil)
            out.writeBoolean(c.unionized); out.writeVarLong(c.loadPpm); out.writeMoney(c.accruedWages)
            out.writeVarInt(c.pending.size)
            for (p in c.pending) { out.writeVarLong(p.arrival.value); out.writeVarLong(p.count) }
            out.writeVarInt(c.carry.size)
            for (x in c.carry) out.writeVarLong(x)
        }
        writeMap(w.exportUnpaid(), out) { o, v -> o.writeVarInt(v) }
        writeMap(w.exportBenefits(), out) { o, v -> o.writeVarLong(v) }
        writeMap(w.exportReputation(), out) { o, v -> o.writeVarLong(v) }
        writeMap(w.exportSeverance(), out) { o, v -> o.writeMoney(v) }
        val contracts = w.exportContracts()
        out.writeVarInt(contracts.size)
        for ((company, list) in contracts) {
            out.writeVarLong(company.value)
            out.writeVarInt(list.size)
            for (k in list) {
                out.writeBoolean(k.role != null); k.role?.let { out.writeString(it.key) }
                out.writeMoney(k.wage); out.writeVarLong(k.start.value); out.writeVarLong(k.end.value)
                out.writeVarInt(k.severanceDaysPerYear); out.writeBoolean(k.collective)
            }
        }
        val shock = w.exportShock()
        out.writeVarInt(shock.size)
        for ((k, v) in shock) {
            out.writeVarLong(k.company.value); out.writeVarLong(k.facility.value); out.writeString(k.role.key); out.writeVarLong(k.region.value); out.writeVarLong(v)
        }
        // Labor markets.
        val markets = w.markets.markets()
        out.writeVarInt(markets.size)
        for ((k, m) in markets) {
            out.writeVarLong(k.region.value); out.writeString(k.role.key)
            out.writeVarLong(m.available); out.writeVarLong(m.wageIndexPpm); out.writeVarLong(m.employed)
        }
    }

    fun read(r: ByteReader, target: WorkforceService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported workforce save version $version")
        val lastPay = Tick(r.readVarLong())
        val cohorts = ArrayList<Cohort>()
        repeat(r.readVarInt()) {
            val id = CohortId(r.readVarLong())
            val key = CohortKey(CompanyId(r.readVarLong()), FacilityId(r.readVarLong()), RoleId(r.readString()), RegionId(r.readVarLong()))
            val c = Cohort(id, key, r.readMoney())
            val head = r.readVarLong(); val tgt = r.readVarLong(); val skill = r.readVarLong(); val morale = r.readVarLong()
            val burn = r.readVarLong(); val tenure = r.readVarLong(); val training = r.readVarLong(); val strike = r.readVarLong()
            val union = r.readBoolean(); val load = r.readVarLong(); val accrued = r.readMoney()
            val pending = ArrayList<PendingHire>()
            repeat(r.readVarInt()) { pending.add(PendingHire(Tick(r.readVarLong()), r.readVarLong())) }
            val carries = LongArray(r.readVarInt()) { r.readVarLong() }
            c.restore(head, tgt, skill, morale, burn, tenure, training, strike, union, load, accrued, pending, carries)
            cohorts.add(c)
        }
        val unpaid = readMap(r) { it.readVarInt() }
        val benefits = readMap(r) { it.readVarLong() }
        val reputation = readMap(r) { it.readVarLong() }
        val severance = readMap(r) { it.readMoney() }
        val contracts = java.util.TreeMap<CompanyId, List<LaborContract>>()
        repeat(r.readVarInt()) {
            val company = CompanyId(r.readVarLong())
            val list = ArrayList<LaborContract>()
            repeat(r.readVarInt()) {
                val role = if (r.readBoolean()) RoleId(r.readString()) else null
                list.add(LaborContract(company, role, r.readMoney(), Tick(r.readVarLong()), Tick(r.readVarLong()), r.readVarInt(), r.readBoolean()))
            }
            contracts[company] = list
        }
        val shock = java.util.TreeMap<CohortKey, Long>()
        repeat(r.readVarInt()) {
            val key = CohortKey(CompanyId(r.readVarLong()), FacilityId(r.readVarLong()), RoleId(r.readString()), RegionId(r.readVarLong()))
            shock[key] = r.readVarLong()
        }
        target.restoreState(cohorts, unpaid, benefits, reputation, contracts, severance, shock, lastPay)
        repeat(r.readVarInt()) {
            val key = MarketKey(RegionId(r.readVarLong()), RoleId(r.readString()))
            val m = target.markets.market(key)
            m.available = r.readVarLong()
            m.restore(r.readVarLong(), r.readVarLong())
        }
    }

    private fun <V> writeMap(m: Map<CompanyId, V>, out: ByteWriter, value: (ByteWriter, V) -> Unit) {
        out.writeVarInt(m.size)
        for ((k, v) in m) { out.writeVarLong(k.value); value(out, v) }
    }

    private fun <V> readMap(r: ByteReader, value: (ByteReader) -> V): Map<CompanyId, V> {
        val out = java.util.TreeMap<CompanyId, V>()
        repeat(r.readVarInt()) { out[CompanyId(r.readVarLong())] = value(r) }
        return out
    }
}
