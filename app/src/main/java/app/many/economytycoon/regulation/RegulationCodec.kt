package app.many.economytycoon.regulation

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.RegulationId
import app.many.economytycoon.core.Tick

/** Binary save format for governments, laws and fines. */
object RegulationCodec {
    const val VERSION = 1

    fun write(s: RegulationService, out: ByteWriter) {
        out.writeVarInt(VERSION)
        val govs = s.governments()
        out.writeVarInt(govs.size)
        for (g in govs) {
            out.writeVarLong(g.country.value); out.writeVarInt(g.stance.ordinal); out.writeVarLong(g.nextElectionFirst.value); out.writeVarLong(g.baselineWage)
            out.writeVarLong(g.approvalPpm); out.writeVarLong(g.nextElection.value); out.writeVarInt(g.terms)
        }
        val laws = s.laws()
        out.writeVarInt(laws.size)
        for (l in laws) {
            out.writeVarLong(l.id.value); out.writeVarLong(l.country.value); out.writeVarInt(l.lever.ordinal); out.writeVarLong(l.from); out.writeVarLong(l.to)
            out.writeVarLong(l.proposedAt.value); out.writeVarLong(l.voteAt.value); out.writeVarInt(l.proposedBy.ordinal)
            out.writeVarInt(l.status.ordinal); optional(out, l.effectiveAt?.value); optional(out, l.endedAt?.value); out.writeVarLong(l.supportScorePpm)
            pool(out, l.backers); pool(out, l.opponents)
        }
        val fines = s.fines()
        out.writeVarInt(fines.size)
        for (f in fines) {
            out.writeVarLong(f.company.value); out.writeVarLong(f.facility.value); out.writeVarLong(f.country.value); out.writeVarLong(f.tick.value)
            out.writeVarLong(f.pollutionPpm); out.writeVarLong(f.limitPpm); out.writeMoney(f.amount); out.writeBoolean(f.closed)
        }
    }

    fun read(r: ByteReader, s: RegulationService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported regulation save version $version")
        s.clearAll()
        repeat(r.readVarInt()) {
            val country = CountryId(r.readVarLong())
            val stance = at(Stance.values(), r.readVarInt(), "stance")
            val g = Government(country, stance, Tick(r.readVarLong()), r.readVarLong())
            g.restore(stance, r.readVarLong(), Tick(r.readVarLong()), r.readVarInt())
            s.restore(g)
        }
        repeat(r.readVarInt()) {
            val id = RegulationId(r.readVarLong()); val country = CountryId(r.readVarLong())
            val lever = at(Lever.values(), r.readVarInt(), "lever")
            val from = r.readVarLong(); val to = r.readVarLong()
            val proposedAt = Tick(r.readVarLong()); val voteAt = Tick(r.readVarLong())
            val by = at(Stance.values(), r.readVarInt(), "stance")
            val law = Law(id, country, lever, from, to, proposedAt, voteAt, by)
            val status = at(LawStatus.values(), r.readVarInt(), "law status")
            law.restore(status, optional(r)?.let { Tick(it) }, optional(r)?.let { Tick(it) }, r.readVarLong(), pool(r), pool(r))
            s.restore(law)
        }
        repeat(r.readVarInt()) {
            s.restoreFine(Fine(CompanyId(r.readVarLong()), FacilityId(r.readVarLong()), CountryId(r.readVarLong()), Tick(r.readVarLong()), r.readVarLong(), r.readVarLong(), r.readMoney(), r.readBoolean()))
        }
    }

    private fun pool(w: ByteWriter, m: Map<CompanyId, app.many.economytycoon.core.Money>) {
        w.writeVarInt(m.size)
        for ((c, v) in m) { w.writeVarLong(c.value); w.writeMoney(v) }
    }

    private fun pool(r: ByteReader): Map<CompanyId, app.many.economytycoon.core.Money> {
        val out = java.util.TreeMap<CompanyId, app.many.economytycoon.core.Money>()
        repeat(r.readVarInt()) { out[CompanyId(r.readVarLong())] = r.readMoney() }
        return out
    }

    private fun optional(w: ByteWriter, v: Long?) { w.writeBoolean(v != null); if (v != null) w.writeVarLong(v) }
    private fun optional(r: ByteReader): Long? = if (r.readBoolean()) r.readVarLong() else null
    private fun <T> at(all: Array<T>, index: Int, what: String): T = all.getOrNull(index) ?: throw ByteReader.CorruptDataException("unknown $what $index")
}
