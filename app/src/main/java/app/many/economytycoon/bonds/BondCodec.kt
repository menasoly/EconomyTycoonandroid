package app.many.economytycoon.bonds

import app.many.economytycoon.core.BondId
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Tick

/** Binary save format for government bonds and who holds them. */
object BondCodec {
    const val VERSION = 1

    fun write(s: BondService, out: ByteWriter) {
        out.writeVarInt(VERSION)
        out.writeBoolean(s.dealerFunded)
        val all = s.all()
        out.writeVarInt(all.size)
        for (b in all) {
            out.writeVarLong(b.id.value); out.writeVarLong(b.country.value); out.writeVarInt(b.tenorDays); out.writeVarLong(b.couponPpm)
            out.writeMoney(b.faceEach); out.writeVarLong(b.unitsOffered); out.writeVarLong(b.issuedAt.value); out.writeVarLong(b.subscriptionEnds.value)
            out.writeVarInt(b.status.ordinal); out.writeVarLong(b.unitsSold)
            val ended = b.endedAt
            out.writeBoolean(ended != null); if (ended != null) out.writeVarLong(ended.value)
            out.writeMoney(b.couponsPaid)
            val holders = b.holderIds()
            out.writeVarInt(holders.size)
            for (c in holders) { out.writeVarLong(c.value); out.writeVarLong(b.heldBy(c)); out.writeMoney(b.costOf(c)) }
        }
    }

    fun read(r: ByteReader, s: BondService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported bond save version $version")
        s.clearAll()
        s.dealerFunded = r.readBoolean()
        repeat(r.readVarInt()) {
            val b = Bond(
                BondId(r.readVarLong()), CountryId(r.readVarLong()), r.readVarInt(), r.readVarLong(), r.readMoney(), r.readVarLong(),
                Tick(r.readVarLong()), Tick(r.readVarLong()),
            )
            val ordinal = r.readVarInt()
            val status = BondStatus.values().getOrNull(ordinal) ?: throw ByteReader.CorruptDataException("unknown bond status $ordinal")
            val sold = r.readVarLong()
            val ended = if (r.readBoolean()) Tick(r.readVarLong()) else null
            val coupons = r.readMoney()
            val holders = HashMap<CompanyId, Holding>()
            repeat(r.readVarInt()) { holders[CompanyId(r.readVarLong())] = Holding(r.readVarLong(), r.readMoney()) }
            b.restore(status, sold, ended, coupons, holders)
            s.restore(b)
        }
    }
}
