package app.many.economytycoon.futures

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FuturesId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Tick

/** Binary save format for futures positions. */
object FuturesCodec {
    const val VERSION = 1

    fun write(s: FuturesService, out: ByteWriter) {
        out.writeVarInt(VERSION)
        out.writeBoolean(s.clearingFunded)
        val all = s.all()
        out.writeVarInt(all.size)
        for (p in all) {
            out.writeVarLong(p.id.value); out.writeVarLong(p.company.value); out.writeString(p.good.key)
            out.writeVarInt(p.side.ordinal); out.writeVarLong(p.contracts); out.writeMoney(p.entry)
            out.writeVarLong(p.openedAt.value); out.writeVarLong(p.expiresAt.value)
            out.writeMoney(p.margin); out.writeMoney(p.mark); out.writeVarInt(p.status.ordinal)
            val called = p.calledAt; out.writeBoolean(called != null); if (called != null) out.writeVarLong(called.value)
            val closed = p.closedAt; out.writeBoolean(closed != null); if (closed != null) out.writeVarLong(closed.value)
            out.writeMoney(p.realised); out.writeMoney(p.feePaid)
        }
    }

    fun read(r: ByteReader, s: FuturesService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported futures save version $version")
        s.clearAll()
        s.clearingFunded = r.readBoolean()
        repeat(r.readVarInt()) {
            val id = FuturesId(r.readVarLong()); val company = CompanyId(r.readVarLong()); val good = GoodId(r.readString())
            val sideAt = r.readVarInt()
            val side = Side.values().getOrNull(sideAt) ?: throw ByteReader.CorruptDataException("unknown futures side $sideAt")
            val p = Position(id, company, good, side, r.readVarLong(), r.readMoney(), Tick(r.readVarLong()), Tick(r.readVarLong()))
            val margin = r.readMoney(); val mark = r.readMoney()
            val statusAt = r.readVarInt()
            val status = PositionStatus.values().getOrNull(statusAt) ?: throw ByteReader.CorruptDataException("unknown position status $statusAt")
            val called = if (r.readBoolean()) Tick(r.readVarLong()) else null
            val closed = if (r.readBoolean()) Tick(r.readVarLong()) else null
            p.restore(margin, mark, status, called, closed, r.readMoney(), r.readMoney())
            s.restore(p)
        }
    }
}
