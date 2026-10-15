package app.many.economytycoon.unions

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.UnionId

/** Binary save format for unions and their wage demands. */
object UnionCodec {
    const val VERSION = 1

    private fun optional(out: ByteWriter, v: Long?) { out.writeBoolean(v != null); if (v != null) out.writeVarLong(v) }
    private fun optional(r: ByteReader): Long? = if (r.readBoolean()) r.readVarLong() else null

    fun write(s: UnionService, out: ByteWriter) {
        out.writeVarInt(VERSION)
        out.writeVarLong(s.demandSequence)
        val unions = s.all()
        out.writeVarInt(unions.size)
        for (u in unions) {
            out.writeVarLong(u.id.value); out.writeVarLong(u.company.value); out.writeVarLong(u.region.value); out.writeVarLong(u.formedAt.value)
            out.writeVarLong(u.strengthPpm); out.writeVarLong(u.members); out.writeVarLong(u.lastRound.value)
            optional(out, u.strikeUntil?.value); out.writeVarInt(u.strikes); out.writeVarInt(u.raisesWon); optional(out, u.disbandedAt?.value)
        }
        val demands = s.demands()
        out.writeVarInt(demands.size)
        for (d in demands) {
            out.writeVarLong(d.id); out.writeVarLong(d.union.value); out.writeVarLong(d.raisePpm); out.writeVarLong(d.issuedAt.value); out.writeVarLong(d.deadline.value)
            out.writeVarInt(d.status.ordinal); out.writeVarLong(d.agreedPpm); out.writeVarLong(d.counterPpm); out.writeVarInt(d.strikeDays); optional(out, d.resolvedAt?.value)
        }
    }

    fun read(r: ByteReader, s: UnionService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported union save version $version")
        s.clearAll()
        s.demandSequence = r.readVarLong()
        repeat(r.readVarInt()) {
            val u = Union(UnionId(r.readVarLong()), CompanyId(r.readVarLong()), RegionId(r.readVarLong()), Tick(r.readVarLong()))
            val strength = r.readVarLong(); val members = r.readVarLong(); val last = Tick(r.readVarLong())
            val strike = optional(r)?.let { Tick(it) }; val strikes = r.readVarInt(); val raises = r.readVarInt(); val gone = optional(r)?.let { Tick(it) }
            u.restore(strength, members, last, strike, strikes, raises, gone)
            s.restore(u)
        }
        repeat(r.readVarInt()) {
            val d = Demand(r.readVarLong(), UnionId(r.readVarLong()), r.readVarLong(), Tick(r.readVarLong()), Tick(r.readVarLong()))
            val ordinal = r.readVarInt()
            val status = DemandStatus.values().getOrNull(ordinal) ?: throw ByteReader.CorruptDataException("unknown demand status $ordinal")
            d.restore(status, r.readVarLong(), r.readVarLong(), r.readVarInt(), optional(r)?.let { Tick(it) })
            s.restore(d)
        }
    }
}
