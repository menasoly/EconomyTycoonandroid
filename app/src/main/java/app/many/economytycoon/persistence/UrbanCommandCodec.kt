package app.many.economytycoon.persistence

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.urban.CapitalProjectId
import app.many.economytycoon.urban.ConcessionId
import app.many.economytycoon.urban.ConcessionKind
import app.many.economytycoon.urban.DensityRules
import app.many.economytycoon.urban.LinkId
import app.many.economytycoon.urban.PaymentModel
import app.many.economytycoon.urban.PetitionId
import app.many.economytycoon.urban.PetitionKind
import app.many.economytycoon.urban.ProjectKind
import app.many.economytycoon.urban.RoadClass
import app.many.economytycoon.urban.Service
import app.many.economytycoon.urban.TaxRates
import app.many.economytycoon.urban.TransitLineId
import app.many.economytycoon.urban.TransitMode
import app.many.economytycoon.urban.UrbanCommand
import app.many.economytycoon.urban.ZoneUse

/**
 * Binary form of an [UrbanCommand] for the command log and replays. The company is written by the caller; each command
 * here is a sub-tag, its city and its other fields (money as raw varints, enums by ordinal, optional ids behind a presence
 * byte). Sub-tags never change meaning, and an unknown one makes the log unreadable.
 */
object UrbanCommandCodec {
    fun write(c: UrbanCommand, w: ByteWriter) {
        when (c) {
            is UrbanCommand.SetTaxRates -> {
                w.writeVarInt(1); city(w, c); w.writeVarLong(c.rates.propertyPerYearPpm); w.writeVarLong(c.rates.incomePpm); w.writeMoney(c.rates.businessPerSqm)
            }
            is UrbanCommand.SetServiceLevel -> { w.writeVarInt(2); city(w, c); w.writeVarInt(c.service.ordinal); w.writeVarInt(c.level) }
            is UrbanCommand.SetDensityRules -> {
                w.writeVarInt(3); city(w, c); w.writeVarInt(c.district)
                w.writeVarInt(c.rules.maxFarMilli); w.writeVarInt(c.rules.maxFloors); w.writeVarLong(c.rules.lotCoveragePpm)
            }
            is UrbanCommand.SetTenantProtection -> { w.writeVarInt(4); city(w, c); w.writeVarInt(c.district); w.writeVarInt(c.permille) }
            is UrbanCommand.AddRoad -> { w.writeVarInt(5); city(w, c); w.writeVarInt(c.a); w.writeVarInt(c.b); w.writeVarInt(c.roadClass.ordinal); w.writeVarInt(c.lanes) }
            is UrbanCommand.PlanLine -> {
                w.writeVarInt(6); city(w, c); w.writeString(c.name); w.writeVarInt(c.mode.ordinal)
                w.writeVarInt(c.stops.size); for (s in c.stops) w.writeVarInt(s)
                w.writeVarInt(c.headwaySeconds); w.writeMoney(c.fare); w.writeBoolean(c.dedicatedLane)
            }
            is UrbanCommand.SetLineHeadway -> { w.writeVarInt(7); city(w, c); w.writeVarLong(c.line.value); w.writeVarInt(c.headwaySeconds) }
            is UrbanCommand.SetLineFare -> { w.writeVarInt(8); city(w, c); w.writeVarLong(c.line.value); w.writeMoney(c.fare) }
            is UrbanCommand.ToggleLine -> { w.writeVarInt(9); city(w, c); w.writeVarLong(c.line.value) }
            is UrbanCommand.StartProject -> {
                w.writeVarInt(10); city(w, c); w.writeVarInt(c.kind.ordinal); w.writeString(c.name)
                optInt(w, c.district); optLong(w, c.line?.value); optLong(w, c.road?.value); w.writeVarLong(c.size); w.writeBoolean(c.fixedPrice)
            }
            is UrbanCommand.CancelProject -> { w.writeVarInt(11); city(w, c); w.writeVarLong(c.project.value) }
            is UrbanCommand.Borrow -> { w.writeVarInt(12); city(w, c); w.writeMoney(c.amount) }
            is UrbanCommand.Repay -> { w.writeVarInt(13); city(w, c); w.writeMoney(c.amount) }
            is UrbanCommand.TenderConcession -> {
                w.writeVarInt(14); city(w, c); w.writeVarInt(c.kind.ordinal); w.writeString(c.name); optLong(w, c.road?.value); optLong(w, c.line?.value)
                w.writeVarInt(c.model.ordinal); w.writeVarInt(c.termMonths); w.writeVarInt(c.constructionMonths)
                w.writeMoney(c.capex); w.writeMoney(c.toll); w.writeMoney(c.availabilityMonthly); w.writeVarLong(c.cityShareBasePpm); w.writeVarLong(c.trafficGuarantee)
            }
            is UrbanCommand.TerminateConcession -> { w.writeVarInt(15); city(w, c); w.writeVarLong(c.concession.value) }
            is UrbanCommand.FilePetition -> {
                w.writeVarInt(16); city(w, c); w.writeVarInt(c.district); w.writeVarInt(c.kind.ordinal); w.writeVarInt(c.toZone.ordinal)
                w.writeVarInt(c.farMilli); w.writeVarInt(c.floors); optLong(w, c.applicant?.value)
            }
            is UrbanCommand.WithdrawPetition -> { w.writeVarInt(17); city(w, c); w.writeVarLong(c.petition.value); optLong(w, c.applicant?.value) }
            is UrbanCommand.LobbyPetition -> {
                w.writeVarInt(18); city(w, c); w.writeVarLong(c.petition.value); w.writeVarLong(c.company.value); optInt(w, c.seat); w.writeMoney(c.amount)
            }
            is UrbanCommand.BidConcession -> { w.writeVarInt(19); city(w, c); w.writeVarLong(c.concession.value); w.writeVarLong(c.company.value); w.writeVarLong(c.discountPpm) }
        }
    }

    fun read(r: ByteReader): UrbanCommand {
        val t = r.readVarInt()
        val city = CityId(r.readVarLong())
        return when (t) {
            1 -> UrbanCommand.SetTaxRates(city, TaxRates(r.readVarLong(), r.readVarLong(), r.readMoney()))
            2 -> UrbanCommand.SetServiceLevel(city, at(Service.values(), r.readVarInt(), "service"), r.readVarInt())
            3 -> UrbanCommand.SetDensityRules(city, r.readVarInt(), DensityRules(r.readVarInt(), r.readVarInt(), r.readVarLong()))
            4 -> UrbanCommand.SetTenantProtection(city, r.readVarInt(), r.readVarInt())
            5 -> UrbanCommand.AddRoad(city, r.readVarInt(), r.readVarInt(), at(RoadClass.values(), r.readVarInt(), "road class"), r.readVarInt())
            6 -> {
                val name = r.readString(); val mode = at(TransitMode.values(), r.readVarInt(), "transit mode")
                val n = r.readVarInt()
                if (n < 0 || n > MAX_STOPS) throw ByteReader.CorruptDataException("implausible number of stops $n")
                val stops = List(n) { r.readVarInt() }
                UrbanCommand.PlanLine(city, name, mode, stops, r.readVarInt(), r.readMoney(), r.readBoolean())
            }
            7 -> UrbanCommand.SetLineHeadway(city, TransitLineId(r.readVarLong()), r.readVarInt())
            8 -> UrbanCommand.SetLineFare(city, TransitLineId(r.readVarLong()), r.readMoney())
            9 -> UrbanCommand.ToggleLine(city, TransitLineId(r.readVarLong()))
            10 -> {
                val kind = at(ProjectKind.values(), r.readVarInt(), "project kind"); val name = r.readString()
                val district = readOptInt(r); val line = readOptLong(r)?.let { TransitLineId(it) }; val road = readOptLong(r)?.let { LinkId(it) }
                UrbanCommand.StartProject(city, kind, name, district, line, road, r.readVarLong(), r.readBoolean())
            }
            11 -> UrbanCommand.CancelProject(city, CapitalProjectId(r.readVarLong()))
            12 -> UrbanCommand.Borrow(city, r.readMoney())
            13 -> UrbanCommand.Repay(city, r.readMoney())
            14 -> {
                val kind = at(ConcessionKind.values(), r.readVarInt(), "concession kind"); val name = r.readString()
                val road = readOptLong(r)?.let { LinkId(it) }; val line = readOptLong(r)?.let { TransitLineId(it) }
                UrbanCommand.TenderConcession(
                    city, kind, name, road, line, at(PaymentModel.values(), r.readVarInt(), "payment model"), r.readVarInt(), r.readVarInt(),
                    r.readMoney(), r.readMoney(), r.readMoney(), r.readVarLong(), r.readVarLong(),
                )
            }
            15 -> UrbanCommand.TerminateConcession(city, ConcessionId(r.readVarLong()))
            16 -> UrbanCommand.FilePetition(
                city, r.readVarInt(), at(PetitionKind.values(), r.readVarInt(), "petition kind"), at(ZoneUse.values(), r.readVarInt(), "zone"),
                r.readVarInt(), r.readVarInt(), readOptLong(r)?.let { CompanyId(it) },
            )
            17 -> UrbanCommand.WithdrawPetition(city, PetitionId(r.readVarLong()), readOptLong(r)?.let { CompanyId(it) })
            18 -> UrbanCommand.LobbyPetition(city, PetitionId(r.readVarLong()), CompanyId(r.readVarLong()), readOptInt(r), r.readMoney())
            19 -> UrbanCommand.BidConcession(city, ConcessionId(r.readVarLong()), CompanyId(r.readVarLong()), r.readVarLong())
            else -> throw ByteReader.CorruptDataException("unknown urban command tag $t")
        }
    }

    private const val MAX_STOPS = 64

    private fun city(w: ByteWriter, c: UrbanCommand) = w.writeVarLong(c.city.value)
    private fun optLong(w: ByteWriter, v: Long?) { w.writeBoolean(v != null); if (v != null) w.writeVarLong(v) }
    private fun optInt(w: ByteWriter, v: Int?) { w.writeBoolean(v != null); if (v != null) w.writeVarInt(v) }
    private fun readOptLong(r: ByteReader): Long? = if (r.readBoolean()) r.readVarLong() else null
    private fun readOptInt(r: ByteReader): Int? = if (r.readBoolean()) r.readVarInt() else null
    private fun <T> at(values: Array<T>, i: Int, what: String): T = values.getOrNull(i) ?: throw ByteReader.CorruptDataException("bad $what ordinal $i")
}
