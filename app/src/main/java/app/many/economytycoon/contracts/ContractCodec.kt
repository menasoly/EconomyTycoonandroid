package app.many.economytycoon.contracts

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.ContractId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Tick

/** Binary save format for the contract register. */
object ContractCodec {
    const val VERSION = 1

    fun write(s: ContractService, out: ByteWriter) {
        out.writeVarInt(VERSION)
        val all = s.all()
        out.writeVarInt(all.size)
        for (c in all) {
            out.writeVarLong(c.id.value); out.writeVarLong(c.proposer.value); out.writeVarLong(c.counterparty.value)
            out.writeVarInt(c.proposerSide.ordinal); out.writeVarLong(c.proposerFacility.value)
            out.writeString(c.terms.good.key); out.writeQuantity(c.terms.quantity); out.writeMoney(c.terms.unitPrice.perUnit)
            out.writeVarInt(c.terms.intervalTicks); out.writeVarInt(c.terms.deliveries)
            out.writeVarLong(c.proposedAt.value)
            out.writeVarInt(c.status.ordinal)
            optional(out, c.counterFacility?.value); optional(out, c.startedAt?.value); optional(out, c.nextDue?.value); optional(out, c.endedAt?.value)
            out.writeVarInt(c.delivered); out.writeVarInt(c.missed); out.writeVarInt(c.strikes)
            out.writeMoney(c.paid); out.writeMoney(c.penalties)
        }
    }

    fun read(r: ByteReader, s: ContractService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported contract save version $version")
        s.clearAll()
        repeat(r.readVarInt()) {
            val id = ContractId(r.readVarLong()); val proposer = CompanyId(r.readVarLong()); val counter = CompanyId(r.readVarLong())
            val side = enumAt(ContractSide.values(), r.readVarInt(), "contract side")
            val facility = FacilityId(r.readVarLong())
            val terms = try {
                ContractTerms(GoodId(r.readString()), r.readQuantity(), Price(r.readMoney()), r.readVarInt(), r.readVarInt())
            } catch (e: IllegalArgumentException) {
                throw ByteReader.CorruptDataException("invalid contract terms: ${e.message}")
            }
            val c = SupplyContract(id, proposer, counter, side, facility, terms, Tick(r.readVarLong()))
            val status = enumAt(ContractStatus.values(), r.readVarInt(), "contract status")
            val counterFacility = optional(r)?.let { FacilityId(it) }
            val started = optional(r)?.let { Tick(it) }
            val next = optional(r)?.let { Tick(it) }
            val ended = optional(r)?.let { Tick(it) }
            c.restore(status, counterFacility, started, next, ended, r.readVarInt(), r.readVarInt(), r.readVarInt(), r.readMoney(), r.readMoney())
            s.restore(c)
        }
    }

    private fun optional(w: ByteWriter, v: Long?) { w.writeBoolean(v != null); if (v != null) w.writeVarLong(v) }
    private fun optional(r: ByteReader): Long? = if (r.readBoolean()) r.readVarLong() else null

    private fun <T> enumAt(values: Array<T>, index: Int, what: String): T =
        values.getOrNull(index) ?: throw ByteReader.CorruptDataException("unknown $what $index")
}
