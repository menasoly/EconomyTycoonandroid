package app.many.economytycoon.persistence

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.tourism.AllotmentId
import app.many.economytycoon.tourism.AttractionId
import app.many.economytycoon.tourism.AttractionKind
import app.many.economytycoon.tourism.CancellationPolicy
import app.many.economytycoon.tourism.ContractSide
import app.many.economytycoon.tourism.DestinationId
import app.many.economytycoon.tourism.IntermediaryId
import app.many.economytycoon.tourism.IntermediaryKind
import app.many.economytycoon.tourism.OperatorId
import app.many.economytycoon.tourism.PropertyId
import app.many.economytycoon.tourism.PropertyKind
import app.many.economytycoon.tourism.RatePolicy
import app.many.economytycoon.tourism.RoomClass
import app.many.economytycoon.tourism.TourismCommand
import app.many.economytycoon.tourism.VisitorSegment

/**
 * Binary form of a [TourismCommand] for the command log and replays. The company is written by the caller; each command
 * here is a sub-tag followed by its other fields. Sub-tags never change meaning, and an unknown one makes the log unreadable.
 */
object TourismCommandCodec {
    fun write(c: TourismCommand, w: ByteWriter) {
        when (c) {
            is TourismCommand.BuildProperty -> {
                w.writeVarInt(1); w.writeVarLong(c.destination.value); w.writeVarInt(c.kind.ordinal); w.writeVarInt(c.stars)
                w.writeVarInt(c.rooms.size); for (n in c.rooms) w.writeVarInt(n); w.writeString(c.name)
            }
            is TourismCommand.SetRates -> { w.writeVarInt(2); w.writeVarLong(c.property.value); w.writeVarInt(c.policy.ordinal); w.writeMoney(c.rack) }
            is TourismCommand.SetPricingBand -> {
                w.writeVarInt(3); w.writeVarLong(c.property.value); w.writeVarLong(c.floorPpm); w.writeVarLong(c.ceilingPpm)
                w.writeVarLong(c.aggressionPpm); w.writeVarLong(c.targetOccupancyPpm)
            }
            is TourismCommand.SetOverbooking -> { w.writeVarInt(4); w.writeVarLong(c.property.value); w.writeVarLong(c.ppm) }
            is TourismCommand.SetCancellation -> { w.writeVarInt(5); w.writeVarLong(c.property.value); w.writeVarInt(c.policy.ordinal) }
            is TourismCommand.Listing -> { w.writeVarInt(6); w.writeVarLong(c.property.value); w.writeVarLong(c.intermediary.value); w.writeBoolean(c.listed) }
            is TourismCommand.ExpandProperty -> { w.writeVarInt(7); w.writeVarLong(c.property.value); w.writeVarInt(c.room.ordinal); w.writeVarInt(c.count) }
            is TourismCommand.UpgradeProperty -> { w.writeVarInt(8); w.writeVarLong(c.property.value); w.writeVarInt(c.stars) }
            is TourismCommand.RenovateProperty -> { w.writeVarInt(9); w.writeVarLong(c.property.value) }
            is TourismCommand.CloseProperty -> { w.writeVarInt(10); w.writeVarLong(c.property.value) }
            is TourismCommand.ReopenProperty -> { w.writeVarInt(11); w.writeVarLong(c.property.value) }
            is TourismCommand.BuildAttraction -> {
                w.writeVarInt(12); w.writeVarLong(c.destination.value); w.writeVarInt(c.kind.ordinal); w.writeString(c.name)
                w.writeVarInt(c.capacity); w.writeMoney(c.ticket)
            }
            is TourismCommand.SetTicketPrice -> { w.writeVarInt(13); w.writeVarLong(c.attraction.value); w.writeMoney(c.price) }
            is TourismCommand.SetAttractionCapacity -> { w.writeVarInt(14); w.writeVarLong(c.attraction.value); w.writeVarInt(c.capacity) }
            is TourismCommand.SetAttractionOpen -> { w.writeVarInt(15); w.writeVarLong(c.attraction.value); w.writeBoolean(c.open) }
            is TourismCommand.FoundOperator -> { w.writeVarInt(16); w.writeString(c.name); w.writeVarLong(c.marginPpm) }
            is TourismCommand.SetOperatorMargin -> { w.writeVarInt(17); w.writeVarLong(c.operator.value); w.writeVarLong(c.marginPpm) }
            is TourismCommand.FoundIntermediary -> {
                w.writeVarInt(18); w.writeVarInt(c.kind.ordinal); w.writeString(c.name); w.writeVarLong(c.commissionPpm)
                w.writeVarLong(c.reachPpm); w.writeMoney(c.listingFee)
            }
            is TourismCommand.SetCommission -> { w.writeVarInt(19); w.writeVarLong(c.intermediary.value); w.writeVarLong(c.commissionPpm) }
            is TourismCommand.ProposeAllotment -> {
                w.writeVarInt(20); w.writeVarInt(c.side.ordinal); w.writeVarLong(c.operator.value); w.writeVarLong(c.property.value)
                w.writeVarInt(c.room.ordinal); w.writeVarInt(c.roomsPerNight); w.writeMoney(c.netRate); w.writeVarLong(c.startsAt)
                w.writeVarInt(c.days); w.writeVarInt(c.releaseDays); w.writeVarLong(c.committedPpm)
            }
            is TourismCommand.AcceptAllotment -> { w.writeVarInt(21); w.writeVarLong(c.allotment.value) }
            is TourismCommand.DeclineAllotment -> { w.writeVarInt(22); w.writeVarLong(c.allotment.value) }
            is TourismCommand.TerminateAllotment -> { w.writeVarInt(23); w.writeVarLong(c.allotment.value) }
            is TourismCommand.LaunchCampaign -> {
                w.writeVarInt(24); w.writeVarLong(c.destination.value); w.writeVarInt(c.segment?.ordinal ?: -1); w.writeMoney(c.spend)
            }
        }
    }

    fun read(r: ByteReader, company: CompanyId): TourismCommand {
        fun <T> at(all: Array<T>, i: Int, what: String): T = all.getOrNull(i) ?: throw ByteReader.CorruptDataException("unknown tourism $what $i")
        return when (val t = r.readVarInt()) {
            1 -> TourismCommand.BuildProperty(
                company, DestinationId(r.readVarLong()), at(PropertyKind.values(), r.readVarInt(), "property kind"), r.readVarInt(),
                r.readVarInt().also { if (it < 0 || it > MAX_ROOM_CLASSES) throw ByteReader.CorruptDataException("implausible room list") }.let { n -> List(n) { r.readVarInt() } },
                r.readString(),
            )
            2 -> TourismCommand.SetRates(company, PropertyId(r.readVarLong()), at(RatePolicy.values(), r.readVarInt(), "rate policy"), r.readMoney())
            3 -> TourismCommand.SetPricingBand(company, PropertyId(r.readVarLong()), r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarLong())
            4 -> TourismCommand.SetOverbooking(company, PropertyId(r.readVarLong()), r.readVarLong())
            5 -> TourismCommand.SetCancellation(company, PropertyId(r.readVarLong()), at(CancellationPolicy.values(), r.readVarInt(), "cancellation policy"))
            6 -> TourismCommand.Listing(company, PropertyId(r.readVarLong()), IntermediaryId(r.readVarLong()), r.readBoolean())
            7 -> TourismCommand.ExpandProperty(company, PropertyId(r.readVarLong()), at(RoomClass.values(), r.readVarInt(), "room class"), r.readVarInt())
            8 -> TourismCommand.UpgradeProperty(company, PropertyId(r.readVarLong()), r.readVarInt())
            9 -> TourismCommand.RenovateProperty(company, PropertyId(r.readVarLong()))
            10 -> TourismCommand.CloseProperty(company, PropertyId(r.readVarLong()))
            11 -> TourismCommand.ReopenProperty(company, PropertyId(r.readVarLong()))
            12 -> TourismCommand.BuildAttraction(
                company, DestinationId(r.readVarLong()), at(AttractionKind.values(), r.readVarInt(), "attraction kind"), r.readString(), r.readVarInt(), r.readMoney(),
            )
            13 -> TourismCommand.SetTicketPrice(company, AttractionId(r.readVarLong()), r.readMoney())
            14 -> TourismCommand.SetAttractionCapacity(company, AttractionId(r.readVarLong()), r.readVarInt())
            15 -> TourismCommand.SetAttractionOpen(company, AttractionId(r.readVarLong()), r.readBoolean())
            16 -> TourismCommand.FoundOperator(company, r.readString(), r.readVarLong())
            17 -> TourismCommand.SetOperatorMargin(company, OperatorId(r.readVarLong()), r.readVarLong())
            18 -> TourismCommand.FoundIntermediary(
                company, at(IntermediaryKind.values(), r.readVarInt(), "intermediary kind"), r.readString(), r.readVarLong(), r.readVarLong(), r.readMoney(),
            )
            19 -> TourismCommand.SetCommission(company, IntermediaryId(r.readVarLong()), r.readVarLong())
            20 -> TourismCommand.ProposeAllotment(
                company, at(ContractSide.values(), r.readVarInt(), "contract side"), OperatorId(r.readVarLong()), PropertyId(r.readVarLong()),
                at(RoomClass.values(), r.readVarInt(), "room class"), r.readVarInt(), r.readMoney(), r.readVarLong(), r.readVarInt(), r.readVarInt(), r.readVarLong(),
            )
            21 -> TourismCommand.AcceptAllotment(company, AllotmentId(r.readVarLong()))
            22 -> TourismCommand.DeclineAllotment(company, AllotmentId(r.readVarLong()))
            23 -> TourismCommand.TerminateAllotment(company, AllotmentId(r.readVarLong()))
            24 -> TourismCommand.LaunchCampaign(
                company, DestinationId(r.readVarLong()), r.readVarInt().let { i -> if (i < 0) null else at(VisitorSegment.values(), i, "visitor segment") }, r.readMoney(),
            )
            else -> throw ByteReader.CorruptDataException("unknown tourism command tag $t")
        }
    }

    private const val MAX_ROOM_CLASSES = 16
}
