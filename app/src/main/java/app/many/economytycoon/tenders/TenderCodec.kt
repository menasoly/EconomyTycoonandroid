package app.many.economytycoon.tenders

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.TenderId
import app.many.economytycoon.core.Tick

/** Binary save format for tenders, their bids and the failure record. */
object TenderCodec {
    const val VERSION = 1

    fun write(s: TenderService, out: ByteWriter) {
        out.writeVarInt(VERSION)
        out.writeVarLong(s.bidSequence)
        val all = s.all()
        out.writeVarInt(all.size)
        for (t in all) {
            out.writeVarLong(t.id.value); out.writeVarLong(t.country.value); out.writeString(t.good.key); out.writeQuantity(t.quantity); out.writeMoney(t.ceiling)
            out.writeVarLong(t.issuedAt.value); out.writeVarLong(t.closesAt.value); out.writeVarLong(t.deliverBy.value)
            out.writeVarInt(t.status.ordinal); optional(out, t.winner?.value); optional(out, t.winnerFacility?.value)
            out.writeMoney(t.awardedPrice); optional(out, t.endedAt?.value); out.writeMoney(t.penalty)
            val bids = t.bids
            out.writeVarInt(bids.size)
            for (b in bids) { out.writeVarLong(b.company.value); out.writeVarLong(b.facility.value); out.writeMoney(b.unitPrice); out.writeVarLong(b.placedAt.value); out.writeVarLong(b.sequence) }
        }
        val log = s.failureLog()
        out.writeVarInt(log.size)
        for ((c, ticks) in log) { out.writeVarLong(c.value); out.writeVarInt(ticks.size); for (x in ticks) out.writeVarLong(x) }
    }

    fun read(r: ByteReader, s: TenderService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported tender save version $version")
        s.clearAll()
        s.bidSequence = r.readVarLong()
        repeat(r.readVarInt()) {
            val t = Tender(
                TenderId(r.readVarLong()), CountryId(r.readVarLong()), GoodId(r.readString()), r.readQuantity(), r.readMoney(),
                Tick(r.readVarLong()), Tick(r.readVarLong()), Tick(r.readVarLong()),
            )
            val status = values(TenderStatus.values(), r.readVarInt(), "tender status")
            val winner = optional(r)?.let { CompanyId(it) }
            val facility = optional(r)?.let { FacilityId(it) }
            val price = r.readMoney()
            val ended = optional(r)?.let { Tick(it) }
            val penalty = r.readMoney()
            val bids = List(r.readVarInt()) { Bid(CompanyId(r.readVarLong()), FacilityId(r.readVarLong()), r.readMoney(), Tick(r.readVarLong()), r.readVarLong()) }
            t.restore(status, winner, facility, price, ended, penalty, bids)
            s.restore(t)
        }
        repeat(r.readVarInt()) {
            val c = CompanyId(r.readVarLong())
            s.restoreFailures(c, List(r.readVarInt()) { r.readVarLong() })
        }
    }

    private fun optional(w: ByteWriter, v: Long?) { w.writeBoolean(v != null); if (v != null) w.writeVarLong(v) }
    private fun optional(r: ByteReader): Long? = if (r.readBoolean()) r.readVarLong() else null
    private fun <T> values(all: Array<T>, index: Int, what: String): T = all.getOrNull(index) ?: throw ByteReader.CorruptDataException("unknown $what $index")
}
