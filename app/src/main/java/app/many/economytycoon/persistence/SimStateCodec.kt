package app.many.economytycoon.persistence

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.simulation.EconomicEvent
import app.many.economytycoon.simulation.EventQueue
import app.many.economytycoon.simulation.FacilityStats
import app.many.economytycoon.simulation.Grid
import app.many.economytycoon.simulation.QueuedCommand
import app.many.economytycoon.simulation.ScheduledEvent
import app.many.economytycoon.simulation.SimConfig
import app.many.economytycoon.world.WorldSize

/** Binary form of the simulation-level state that no single engine owns. */
object SimStateCodec {
    const val VERSION = 1

    // ---- configuration -------------------------------------------------------------------------

    fun writeConfig(c: SimConfig, w: ByteWriter) {
        w.writeVarInt(VERSION)
        w.writeFixedLong(c.seed)
        w.writeVarInt(c.worldSize.ordinal)
        w.writeVarInt(c.aiCompanies)
        w.writeVarLong(c.householdCashPerCapita)
        w.writeVarLong(c.bankCapitalMultiple)
        w.writeMoney(c.governmentTreasury)
        w.writeVarLong(c.spendingPropensityPpm)
        w.writeMoney(c.aiStartingCapital)
        w.writeMoney(c.playerStartingCapital)
        w.writeVarInt(c.maxCommandsPerTick)
        w.writeMoney(c.tradeBoardCapital)
        w.writeVarInt(c.importAlso.size)
        for (g in c.importAlso) w.writeString(g)
        w.writeVarLong(c.importMarkupPpm)
        w.writeVarLong(c.consumerImportMarkupPpm)
        w.writeMoney(c.benefitPerTick)
        w.writeMoney(c.publicSpendingPerCapita)
        w.writeVarLong(c.importPeoplePerUnit)
    }

    fun readConfig(r: ByteReader): SimConfig {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported simulation config version $version")
        val seed = r.readFixedLong()
        val size = WorldSize.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("unknown world size") }
        val ai = r.readVarInt()
        val cash = r.readVarLong()
        val bank = r.readVarLong()
        val treasury = r.readMoney()
        val propensity = r.readVarLong()
        val aiCapital = r.readMoney()
        val playerCapital = r.readMoney()
        val maxCommands = r.readVarInt()
        val boardCapital = r.readMoney()
        val imports = List(boundedCount(r, 500)) { r.readString() }
        return SimConfig(
            seed = seed, worldSize = size, aiCompanies = ai, householdCashPerCapita = cash, bankCapitalMultiple = bank,
            governmentTreasury = treasury, spendingPropensityPpm = propensity, aiStartingCapital = aiCapital,
            playerStartingCapital = playerCapital, maxCommandsPerTick = maxCommands, tradeBoardCapital = boardCapital,
            importAlso = imports, importMarkupPpm = r.readVarLong(), consumerImportMarkupPpm = r.readVarLong(),
            benefitPerTick = r.readMoney(), publicSpendingPerCapita = r.readMoney(), importPeoplePerUnit = r.readVarLong(),
        )
    }

    // ---- events --------------------------------------------------------------------------------

    fun writeEvents(q: EventQueue, w: ByteWriter) {
        w.writeVarInt(VERSION)
        w.writeVarLong(q.nextSequence())
        val pending = q.pendingEvents()
        w.writeVarInt(pending.size)
        for (s in pending) { w.writeVarLong(s.sequence); w.writeVarLong(s.tick.value); writeEvent(s.event, w) }
        val shifts = q.shiftEntries()
        w.writeVarInt(shifts.size)
        for ((g, v) in shifts) { w.writeString(g.key); w.writeVarLong(v) }
    }

    fun readEvents(r: ByteReader, q: EventQueue) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported event queue version $version")
        val next = r.readVarLong()
        val pending = List(boundedCount(r, 100_000)) { ScheduledEvent(r.readVarLong(), Tick(r.readVarLong()), readEvent(r)) }
        val shifts = List(boundedCount(r, 100_000)) { GoodId(r.readString()) to r.readVarLong() }
        q.restore(next, pending)
        q.restoreShifts(shifts)
    }

    fun writeEvent(e: EconomicEvent, w: ByteWriter) {
        when (e) {
            is EconomicEvent.SupplyShock -> { w.writeByte(1); w.writeString(e.good.key); w.writeVarLong(e.ppm); w.writeString(e.title) }
            is EconomicEvent.DemandShift -> { w.writeByte(2); w.writeString(e.good.key); w.writeVarLong(e.ppm); w.writeString(e.title) }
            is EconomicEvent.ConfidenceShock -> { w.writeByte(3); w.writeVarLong(e.country.value); w.writeVarLong(e.ppm); w.writeString(e.title) }
            is EconomicEvent.DemandRestored -> { w.writeByte(4); w.writeString(e.good.key); w.writeString(e.title) }
        }
    }

    fun readEvent(r: ByteReader): EconomicEvent = when (val t = r.readByte()) {
        1 -> EconomicEvent.SupplyShock(GoodId(r.readString()), r.readVarLong(), r.readString())
        2 -> EconomicEvent.DemandShift(GoodId(r.readString()), r.readVarLong(), r.readString())
        3 -> EconomicEvent.ConfidenceShock(CountryId(r.readVarLong()), r.readVarLong(), r.readString())
        4 -> EconomicEvent.DemandRestored(GoodId(r.readString()), r.readString())
        else -> throw ByteReader.CorruptDataException("unknown event tag $t")
    }

    // ---- grid and facility statistics ----------------------------------------------------------

    fun writeGrid(g: Grid, w: ByteWriter) {
        w.writeVarInt(VERSION)
        val base = g.baselines()
        w.writeVarInt(base.size)
        for ((region, units) in base) { w.writeVarLong(region.value); w.writeVarLong(units) }
        val list = g.pendingProduction()
        w.writeVarInt(list.size)
        for ((region, company, units) in list) { w.writeVarLong(region.value); w.writeVarLong(company.value); w.writeVarLong(units) }
    }

    fun readGrid(r: ByteReader, g: Grid) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported grid version $version")
        g.restoreBaselines(List(boundedCount(r, 1_000_000)) { RegionId(r.readVarLong()) to r.readVarLong() })
        g.restoreProduction(List(boundedCount(r, 1_000_000)) { Triple(RegionId(r.readVarLong()), CompanyId(r.readVarLong()), r.readVarLong()) })
    }

    fun writeFacilityStats(s: FacilityStats, w: ByteWriter) {
        w.writeVarInt(VERSION)
        val list = s.entries()
        w.writeVarInt(list.size)
        for ((f, v) in list) { w.writeVarLong(f.value); w.writeVarLong(v) }
    }

    fun readFacilityStats(r: ByteReader, s: FacilityStats) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported facility statistics version $version")
        s.restore(List(boundedCount(r, 5_000_000)) { FacilityId(r.readVarLong()) to r.readVarLong() })
    }

    // ---- counters and queue --------------------------------------------------------------------

    fun writeCounters(counters: Map<String, Long>, w: ByteWriter) {
        w.writeVarInt(counters.size)
        for ((k, v) in counters) { w.writeString(k); w.writeVarLong(v) }
    }

    fun readCounters(r: ByteReader): Map<String, Long> {
        val out = java.util.TreeMap<String, Long>()
        repeat(boundedCount(r, 100_000)) { out[r.readString()] = r.readVarLong() }
        return out
    }

    fun writeQueue(queue: List<QueuedCommand>, w: ByteWriter) {
        w.writeVarInt(queue.size)
        for (q in queue) { w.writeVarLong(q.sequence); CommandCodec.writeActor(q.actor, w); CommandCodec.writeCommand(q.command, w) }
    }

    fun readQueue(r: ByteReader): List<QueuedCommand> =
        List(boundedCount(r, 1_000_000)) { QueuedCommand(r.readVarLong(), CommandCodec.readActor(r), CommandCodec.readCommand(r)) }

    private fun boundedCount(r: ByteReader, max: Int): Int {
        val n = r.readVarInt()
        if (n < 0 || n > max) throw ByteReader.CorruptDataException("implausible element count $n")
        return n
    }
}
