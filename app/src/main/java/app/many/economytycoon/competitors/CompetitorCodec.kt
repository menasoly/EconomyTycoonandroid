package app.many.economytycoon.competitors

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Tick

/** Binary save format for AI personalities and memory. */
object CompetitorCodec {
    const val VERSION = 1

    fun write(s: CompetitorService, out: ByteWriter) {
        out.writeVarInt(VERSION)
        val ids = s.companies()
        out.writeVarInt(ids.size)
        for (id in ids) {
            val p = s.personality(id)!!; val m = s.memory(id)!!
            out.writeVarLong(id.value)
            out.writeVarInt(p.archetype.ordinal)
            out.writeVarLong(p.aggression); out.writeVarLong(p.riskTolerance); out.writeVarLong(p.qualityFocus); out.writeVarLong(p.costFocus)
            out.writeVarLong(p.expansionAppetite); out.writeVarLong(p.rdShare); out.writeVarLong(p.payout)
            out.writeVarInt(p.thinkInterval); out.writeVarInt(p.cashBufferTicks); out.writeVarInt(p.maxFacilities)
            out.writeVarLong(m.lastThink.value); out.writeVarLong(m.lastExpansion.value); out.writeVarLong(m.lastBid.value)
            out.writeVarInt(m.lossStreak.size)
            for ((f, n) in m.lossStreak) { out.writeVarLong(f.value); out.writeVarInt(n) }
        }
    }

    fun read(r: ByteReader, s: CompetitorService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported competitor save version $version")
        s.clearAll()
        repeat(r.readVarInt()) {
            val id = CompanyId(r.readVarLong())
            val a = Archetype.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad archetype") }
            val p = AiPersonality(
                a, r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong(),
                r.readVarInt(), r.readVarInt(), r.readVarInt(),
            )
            val m = AiMemory()
            m.lastThink = Tick(r.readVarLong()); m.lastExpansion = Tick(r.readVarLong()); m.lastBid = Tick(r.readVarLong())
            repeat(r.readVarInt()) { m.lossStreak[FacilityId(r.readVarLong())] = r.readVarInt() }
            s.restore(id, p, m)
        }
    }
}
