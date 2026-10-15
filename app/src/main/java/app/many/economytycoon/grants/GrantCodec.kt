package app.many.economytycoon.grants

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.GrantId
import app.many.economytycoon.core.Tick

/** Binary save format for grant programmes and the applications made to them. */
object GrantCodec {
    const val VERSION = 1

    fun write(s: GrantService, out: ByteWriter) {
        out.writeVarInt(VERSION)
        val all = s.all()
        out.writeVarInt(all.size)
        for (g in all) {
            out.writeVarLong(g.id.value); out.writeVarLong(g.country.value); out.writeVarInt(g.kind.ordinal)
            out.writeVarInt(g.family?.ordinal ?: -1)
            out.writeMoney(g.rate); out.writeMoney(g.cap); out.writeMoney(g.budget)
            out.writeVarLong(g.announcedAt.value); out.writeVarLong(g.applyUntil.value); out.writeVarLong(g.endsAt.value)
            out.writeVarInt(g.status.ordinal)
            out.writeBoolean(g.endedAt != null); if (g.endedAt != null) out.writeVarLong(g.endedAt!!.value)
            out.writeMoney(g.paidTotal)
            val apps = g.applications
            out.writeVarInt(apps.size)
            for (a in apps) {
                out.writeVarLong(a.company.value); out.writeVarLong(a.baseline); out.writeVarLong(a.appliedAt.value)
                out.writeVarLong(a.gain); out.writeMoney(a.paid); out.writeBoolean(a.settled)
            }
        }
    }

    fun read(r: ByteReader, s: GrantService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported grant save version $version")
        s.clearAll()
        repeat(r.readVarInt()) {
            val id = GrantId(r.readVarLong())
            val country = CountryId(r.readVarLong())
            val kind = pick(GrantKind.values(), r.readVarInt(), "grant kind")
            val familyIndex = r.readVarInt()
            val family = if (familyIndex < 0) null else pick(IndustryFamily.values(), familyIndex, "industry family")
            val g = GrantProgram(id, country, kind, family, r.readMoney(), r.readMoney(), r.readMoney(), Tick(r.readVarLong()), Tick(r.readVarLong()), Tick(r.readVarLong()))
            val status = pick(GrantStatus.values(), r.readVarInt(), "grant status")
            val ended = if (r.readBoolean()) Tick(r.readVarLong()) else null
            val paid = r.readMoney()
            val apps = List(r.readVarInt()) {
                Application(CompanyId(r.readVarLong()), r.readVarLong(), Tick(r.readVarLong())).also { a ->
                    a.gain = r.readVarLong(); a.paid = r.readMoney(); a.settled = r.readBoolean()
                }
            }
            g.restore(status, ended, paid, apps)
            s.restore(g)
        }
    }

    private fun <T> pick(all: Array<T>, index: Int, what: String): T = all.getOrNull(index) ?: throw ByteReader.CorruptDataException("unknown $what $index")
}
