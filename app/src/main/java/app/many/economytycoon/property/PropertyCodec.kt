package app.many.economytycoon.property

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.core.Tick

/** Binary save format for land listings, lettings and holding periods. */
object PropertyCodec {
    const val VERSION = 1

    fun write(s: PropertyService, out: ByteWriter) {
        out.writeVarInt(VERSION)
        out.writeVarLong(s.leaseSequence)
        val listings = s.listings()
        out.writeVarInt(listings.size)
        for (l in listings) { out.writeVarLong(l.parcel.value); out.writeVarLong(l.seller.value); out.writeMoney(l.ask); out.writeVarLong(l.listedAt.value); out.writeVarLong(l.expiresAt.value) }
        val offers = s.offers()
        out.writeVarInt(offers.size)
        for (o in offers) {
            out.writeVarLong(o.parcel.value); out.writeVarLong(o.landlord.value); out.writeVarInt(o.termDays); out.writeMoney(o.monthlyRent)
            out.writeVarLong(o.offeredAt.value); out.writeVarLong(o.expiresAt.value)
        }
        val leases = s.leases()
        out.writeVarInt(leases.size)
        for (l in leases) {
            out.writeVarLong(l.id); out.writeVarLong(l.parcel.value); out.writeVarLong(l.landlord.value); out.writeVarInt(l.tenant); out.writeMoney(l.monthlyRent)
            out.writeVarLong(l.startedAt.value); out.writeVarLong(l.endsAt.value); out.writeVarLong(l.nextRent.value); out.writeMoney(l.collected); out.writeVarInt(l.missed)
            val ended = l.endedAt; out.writeBoolean(ended != null); if (ended != null) out.writeVarLong(ended.value)
        }
        val held = s.heldParcels()
        out.writeVarInt(held.size)
        for ((p, t) in held) { out.writeVarLong(p.value); out.writeVarLong(t.value) }
    }

    fun read(r: ByteReader, s: PropertyService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported property save version $version")
        s.clearAll()
        s.leaseSequence = r.readVarLong()
        repeat(r.readVarInt()) { s.restore(Listing(ParcelId(r.readVarLong()), CompanyId(r.readVarLong()), r.readMoney(), Tick(r.readVarLong()), Tick(r.readVarLong()))) }
        repeat(r.readVarInt()) {
            s.restore(LeaseOffer(ParcelId(r.readVarLong()), CompanyId(r.readVarLong()), r.readVarInt(), r.readMoney(), Tick(r.readVarLong()), Tick(r.readVarLong())))
        }
        repeat(r.readVarInt()) {
            val l = Lease(r.readVarLong(), ParcelId(r.readVarLong()), CompanyId(r.readVarLong()), r.readVarInt(), r.readMoney(), Tick(r.readVarLong()), Tick(r.readVarLong()))
            val next = Tick(r.readVarLong()); val collected = r.readMoney(); val missed = r.readVarInt()
            val ended = if (r.readBoolean()) Tick(r.readVarLong()) else null
            l.restore(next, collected, missed, ended)
            s.restore(l)
        }
        repeat(r.readVarInt()) { s.restoreSince(ParcelId(r.readVarLong()), Tick(r.readVarLong())) }
    }
}
