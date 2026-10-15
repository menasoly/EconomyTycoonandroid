package app.many.economytycoon.markets

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.OrderId
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick

/** Binary save format for order books, price state and the index log. The recent-trade log is not saved. */
object MarketCodec {
    const val VERSION = 1

    fun write(m: MarketService, out: ByteWriter) {
        out.writeVarInt(VERSION)
        val states = m.states()
        out.writeVarInt(states.size)
        for (s in states) {
            out.writeVarLong(s.key.region.value); out.writeString(s.key.good.key)
            out.writeMoney(s.last.perUnit); out.writeMoney(s.previousClose.perUnit)
            out.writeVarLong(s.fastAverage); out.writeVarLong(s.slowAverage)
            out.writeQuantity(s.lastVolume); out.writeVarLong(s.volumeAverage); out.writeVarLong(s.lastTradeTick)
            out.writeQuantity(s.unmetDemand); out.writeQuantity(s.unsoldSupply)
            out.writeVarLong(s.shockPpm); out.writeVarLong(s.demandShiftPpm)
            val c = s.history.closesOldestFirst(); val v = s.history.volumesOldestFirst()
            out.writeVarInt(c.size)
            for (i in c.indices) { out.writeVarLong(c[i]); out.writeVarLong(v[i]) }
        }
        val orders = m.allOrders()
        out.writeVarInt(orders.size)
        for (o in orders) {
            out.writeVarLong(o.id.value); out.writeVarLong(o.key.region.value); out.writeString(o.key.good.key)
            out.writeVarInt(o.side.ordinal); out.writeVarInt(o.party.kind.ordinal); out.writeVarLong(o.party.id)
            out.writeBoolean(o.limit != null); o.limit?.let { out.writeMoney(it.perUnit) }
            out.writeQuantity(o.quantity); out.writeQuantity(o.remaining)
            out.writeVarLong(o.placed.value); out.writeVarLong(o.expires.value); out.writeVarLong(o.source)
            out.writeVarLong(m.sequenceOf(o.id))
        }
        out.writeVarLong(m.sequenceCounter)
        val log = m.indexLog.samples()
        out.writeVarInt(log.size)
        for (s in log) { out.writeVarLong(s.tick); out.writeVarLong(s.cpi); out.writeVarLong(s.ppi) }
    }

    fun read(r: ByteReader, target: MarketService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported market save version $version")
        target.clearAll()
        repeat(r.readVarInt()) {
            val key = MarketKey(RegionId(r.readVarLong()), GoodId(r.readString()))
            val s = MarketState(key, Price(r.readMoney()))
            val previous = Price(r.readMoney())
            s.restore(s.last, previous, r.readVarLong(), r.readVarLong(), r.readQuantity(), r.readVarLong(), r.readVarLong(), r.readQuantity(), r.readQuantity(), r.readVarLong(), r.readVarLong())
            val n = r.readVarInt()
            val closes = LongArray(n); val volumes = LongArray(n)
            for (i in 0 until n) { closes[i] = r.readVarLong(); volumes[i] = r.readVarLong() }
            s.history.restore(closes, volumes)
            target.restoreState(s)
        }
        repeat(r.readVarInt()) {
            val id = OrderId(r.readVarLong())
            val key = MarketKey(RegionId(r.readVarLong()), GoodId(r.readString()))
            val side = Side.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad order side") }
            val kind = PartyKind.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad party kind") }
            val party = Party(kind, r.readVarLong())
            val limit = if (r.readBoolean()) Price(r.readMoney()) else null
            val qty = r.readQuantity(); val remaining = r.readQuantity()
            val placed = Tick(r.readVarLong()); val expires = Tick(r.readVarLong()); val source = r.readVarLong()
            val order = Order(id, key, side, party, limit, qty, placed, expires, source)
            order.restoreRemaining(remaining)
            target.restoreOrder(order, r.readVarLong())
        }
        target.sequenceCounter = r.readVarLong()
        val samples = ArrayList<IndexLog.Sample>()
        repeat(r.readVarInt()) { samples += IndexLog.Sample(r.readVarLong(), r.readVarLong(), r.readVarLong()) }
        target.indexLog.restore(samples)
    }
}
