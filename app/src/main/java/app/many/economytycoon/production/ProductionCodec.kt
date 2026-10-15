package app.many.economytycoon.production

import app.many.economytycoon.content.GoodAmount
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DepositId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.MachineId
import app.many.economytycoon.core.MachineTypeId
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.core.ProjectId
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick

/** Binary save format for facilities, lines, machines, stock and construction projects. */
object ProductionCodec {
    const val VERSION = 1

    fun write(s: ProductionService, out: ByteWriter) {
        out.writeVarInt(VERSION)
        val facilities = s.facilities()
        out.writeVarInt(facilities.size)
        for (f in facilities) writeFacility(f, out)
        val projects = s.projects()
        out.writeVarInt(projects.size)
        for (p in projects) writeProject(p, out)
    }

    private fun writeFacility(f: Facility, out: ByteWriter) {
        out.writeVarLong(f.id.value); out.writeString(f.type.key); out.writeVarLong(f.owner.value); out.writeVarLong(f.region.value)
        out.writeBoolean(f.city != null); f.city?.let { out.writeVarLong(it.value) }
        out.writeBoolean(f.parcel != null); f.parcel?.let { out.writeVarLong(it.value) }
        out.writeVarLong(f.built.value); out.writeString(f.name)
        out.writeVarInt(f.status.ordinal); out.writeVarLong(f.conditionPpm); out.writeVarLong(f.pollutionPpm)
        out.writeVarInt(f.policy.ordinal); out.writeVarLong(f.qualityAssurancePpm)
        out.writeBoolean(f.depositId != null); f.depositId?.let { out.writeVarLong(it.value) }
        out.writeVarLong(f.unmaintainedTicks)
        out.writeQuantity(f.inventory.capacity)
        val stock = f.inventory.entries().filter { it.second.quantity.raw != 0L || it.second.value.raw != 0L }
        out.writeVarInt(stock.size)
        for ((g, slot) in stock) {
            out.writeString(g.key); out.writeQuantity(slot.quantity); out.writeQuantity(slot.reserved); out.writeMoney(slot.value)
            out.writeVarLong(slot.spoilCarry)
        }
        out.writeVarInt(f.lines.size)
        for (l in f.lines) {
            out.writeVarLong(l.id.value); out.writeVarInt(l.priority)
            out.writeBoolean(l.recipe != null); l.recipe?.let { out.writeString(it.key) }
            out.writeBoolean(l.enabled); out.writeVarLong(l.batchesCompleted)
            out.writeString(l.blocked.code); out.writeVarLong(l.utilizationPpm)
            val b = l.batch
            out.writeBoolean(b != null)
            if (b != null) {
                out.writeString(b.recipe.key); out.writeVarLong(b.progressPpm); out.writeMoney(b.inputValue)
                out.writeVarLong(b.yieldPpm); out.writeMoney(b.conversionCost)
            }
            out.writeVarInt(l.machines.size)
            for (m in l.machines) {
                out.writeVarLong(m.id.value); out.writeString(m.type.key); out.writeVarLong(m.installed.value)
                out.writeVarLong(m.conditionPpm); out.writeVarLong(m.batchesRun); out.writeVarLong(m.brokenUntil); out.writeVarInt(m.breakdowns)
            }
        }
    }

    private fun writeProject(p: ConstructionProject, out: ByteWriter) {
        out.writeVarLong(p.id.value); out.writeVarInt(p.kind.ordinal); out.writeVarLong(p.owner.value); out.writeString(p.facilityType.key)
        out.writeVarLong(p.region.value)
        out.writeBoolean(p.city != null); p.city?.let { out.writeVarLong(it.value) }
        out.writeBoolean(p.parcel != null); p.parcel?.let { out.writeVarLong(it.value) }
        out.writeBoolean(p.deposit != null); p.deposit?.let { out.writeVarLong(it.value) }
        out.writeBoolean(p.target != null); p.target?.let { out.writeVarLong(it.value) }
        out.writeVarLong(p.started.value); out.writeVarInt(p.totalTicks); out.writeMoney(p.totalCost); out.writeString(p.name)
        out.writeVarInt(p.required.size)
        for (r in p.required) { out.writeString(r.good.key); out.writeQuantity(r.quantity) }
        out.writeVarInt(p.ticksDone); out.writeMoney(p.paid); out.writeVarInt(p.state.ordinal)
        out.writeVarInt(p.delivered.size)
        for ((g, q) in p.delivered) { out.writeString(g.key); out.writeQuantity(q) }
    }

    fun read(r: ByteReader, target: ProductionService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported production save version $version")
        target.clear()
        repeat(r.readVarInt()) { target.restoreFacility(readFacility(r, target)) }
        repeat(r.readVarInt()) { target.restoreProject(readProject(r)) }
    }

    private fun readFacility(r: ByteReader, s: ProductionService): Facility {
        val id = FacilityId(r.readVarLong()); val type = FacilityTypeId(r.readString()); val owner = CompanyId(r.readVarLong()); val region = RegionId(r.readVarLong())
        val city = if (r.readBoolean()) CityId(r.readVarLong()) else null
        val parcel = if (r.readBoolean()) ParcelId(r.readVarLong()) else null
        val built = Tick(r.readVarLong()); val name = r.readString()
        val status = FacilityStatus.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad facility status") }
        val condition = r.readVarLong(); val pollution = r.readVarLong()
        val policy = SchedulePolicy.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad schedule policy") }
        val qa = r.readVarLong()
        val deposit = if (r.readBoolean()) DepositId(r.readVarLong()) else null
        val unmaintained = r.readVarLong()
        val inventory = s.newInventory(r.readQuantity())
        repeat(r.readVarInt()) {
            val g = GoodId(r.readString()); val q = r.readQuantity(); val res = r.readQuantity(); val v = r.readMoney(); val carry = r.readVarLong()
            inventory.restoreSlot(g, q, res, v, carry)
        }
        val f = Facility(id, type, owner, region, city, parcel, built, inventory)
        f.name = name
        f.restoreState(status, condition, pollution, policy, qa, deposit, unmaintained)
        repeat(r.readVarInt()) {
            val line = ProductionLine(LineId(r.readVarLong()), r.readVarInt())
            val recipe = if (r.readBoolean()) RecipeId(r.readString()) else null
            val enabled = r.readBoolean(); val completed = r.readVarLong()
            val blocked = Blocked.parse(r.readString()); val util = r.readVarLong()
            val batch = if (r.readBoolean()) ActiveBatch(RecipeId(r.readString()), r.readVarLong(), r.readMoney(), r.readVarLong(), r.readMoney()) else null
            line.restore(recipe, enabled, completed, blocked, util, batch)
            repeat(r.readVarInt()) {
                val m = Machine(MachineId(r.readVarLong()), MachineTypeId(r.readString()), Tick(r.readVarLong()))
                m.restore(r.readVarLong(), r.readVarLong(), r.readVarLong(), r.readVarInt())
                line.machines.add(m)
            }
            f.lines.add(line)
        }
        return f
    }

    private fun readProject(r: ByteReader): ConstructionProject {
        val id = ProjectId(r.readVarLong())
        val kind = ProjectKind.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad project kind") }
        val owner = CompanyId(r.readVarLong()); val type = FacilityTypeId(r.readString()); val region = RegionId(r.readVarLong())
        val city = if (r.readBoolean()) CityId(r.readVarLong()) else null
        val parcel = if (r.readBoolean()) ParcelId(r.readVarLong()) else null
        val deposit = if (r.readBoolean()) DepositId(r.readVarLong()) else null
        val target = if (r.readBoolean()) FacilityId(r.readVarLong()) else null
        val started = Tick(r.readVarLong()); val totalTicks = r.readVarInt(); val cost = r.readMoney(); val name = r.readString()
        val required = ArrayList<GoodAmount>()
        repeat(r.readVarInt()) { required += GoodAmount(GoodId(r.readString()), r.readQuantity()) }
        val p = ConstructionProject(id, kind, owner, type, region, city, parcel, deposit, target, started, totalTicks, cost, required, name)
        val done = r.readVarInt(); val paid = r.readMoney()
        val state = ProjectState.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad project state") }
        p.restore(done, paid, state)
        repeat(r.readVarInt()) { p.delivered[GoodId(r.readString())] = r.readQuantity() }
        return p
    }
}
