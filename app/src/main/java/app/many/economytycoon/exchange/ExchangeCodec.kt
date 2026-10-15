package app.many.economytycoon.exchange

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Holder

/** Binary save format for the stock exchange. */
object ExchangeCodec {
    const val VERSION = 1

    fun write(s: ExchangeService, out: ByteWriter) {
        out.writeVarInt(VERSION)
        out.writeVarLong(s.index); out.writeVarLong(s.indexPrevious)
        val idx = s.indexHistory()
        out.writeVarInt(idx.size); for (c in idx) out.writeVarLong(c)
        val all = s.listings()
        out.writeVarInt(all.size)
        for (l in all) {
            out.writeVarLong(l.issuer.value); out.writeVarLong(l.listedAt.value)
            out.writeMoney(l.price); out.writeMoney(l.previousClose); out.writeMoney(l.fairValue)
            out.writeVarLong(l.pressurePpm + OFFSET); out.writeVarLong(l.volumeToday); out.writeMoney(l.turnoverToday); out.writeVarLong(l.volumeTotal)
            out.writeVarInt(l.closes.size); for (c in l.closes) out.writeVarLong(c)
        }
        val pos = s.positions()
        out.writeVarInt(pos.size)
        for ((k, v) in pos) {
            out.writeString(k.holder.kind); out.writeVarLong(k.holder.id); out.writeVarLong(k.issuer.value); out.writeMoney(v)
        }
    }

    fun read(r: ByteReader, s: ExchangeService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported exchange save version $version")
        s.clearAll()
        val index = r.readVarLong(); val previous = r.readVarLong()
        val closes = List(r.readVarInt()) { r.readVarLong() }
        s.restoreIndex(index, previous, closes)
        repeat(r.readVarInt()) {
            val l = Listing(CompanyId(r.readVarLong()), Tick(r.readVarLong()), Money.ZERO)
            val price = r.readMoney(); val prev = r.readMoney(); val fair = r.readMoney()
            val pressure = r.readVarLong() - OFFSET
            val volumeToday = r.readVarLong(); val turnover = r.readMoney(); val total = r.readVarLong()
            val history = List(r.readVarInt()) { r.readVarLong() }
            l.restore(price, prev, fair, pressure, volumeToday, turnover, total, history)
            s.restoreListing(l)
        }
        repeat(r.readVarInt()) {
            val holder = Holder(r.readString(), r.readVarLong())
            s.restoreBasis(PositionKey(holder, CompanyId(r.readVarLong())), r.readMoney())
        }
    }

    /** Pressure can be negative; varints are not signed, so it is shifted into the positive range. */
    private const val OFFSET = 1_000_000_000L
}
