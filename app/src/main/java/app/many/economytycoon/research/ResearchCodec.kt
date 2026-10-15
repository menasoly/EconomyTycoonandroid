package app.many.economytycoon.research

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick

/** Binary save format for research progress. */
object ResearchCodec {
    const val VERSION = 1

    fun write(s: ResearchService, out: ByteWriter) {
        out.writeVarInt(VERSION)
        val ids = s.companies()
        out.writeVarInt(ids.size)
        for (id in ids) {
            val st = s.of(id)
            out.writeVarLong(id.value)
            out.writeVarInt(st.unlocked.size)
            for ((t, at) in st.unlocked) { out.writeString(t.key); out.writeVarLong(at.value) }
            out.writeVarInt(st.active.size)
            for (p in st.active) { out.writeString(p.tech.key); out.writeVarLong(p.started.value); out.writeVarLong(p.costPoints); out.writeVarLong(p.invested) }
            out.writeVarInt(st.queue.size)
            for (t in st.queue) out.writeString(t.key)
        }
    }

    fun read(r: ByteReader, s: ResearchService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported research save version $version")
        s.clearAll()
        repeat(r.readVarInt()) {
            val st = CompanyResearch(CompanyId(r.readVarLong()))
            repeat(r.readVarInt()) { st.unlocked[TechId(r.readString())] = Tick(r.readVarLong()) }
            repeat(r.readVarInt()) {
                val p = ResearchProject(TechId(r.readString()), Tick(r.readVarLong()), r.readVarLong())
                p.restore(r.readVarLong())
                st.active.add(p)
            }
            repeat(r.readVarInt()) { st.queue.add(TechId(r.readString())) }
            s.restore(st)
        }
    }
}
