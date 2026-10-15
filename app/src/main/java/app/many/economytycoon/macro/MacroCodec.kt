package app.many.economytycoon.macro

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick

/** Binary save format for national accounts, central banks, cycles and budgets. */
object MacroCodec {
    const val VERSION = 1

    fun write(s: MacroService, out: ByteWriter) {
        out.writeVarInt(VERSION)
        val ids = s.countries()
        out.writeVarInt(ids.size)
        for (id in ids) {
            val c = s.of(id)
            out.writeVarLong(id.value)
            out.writeVarLong(c.bank.ratePpm); out.writeVarInt(c.bank.mode.ordinal); out.writeVarLong(c.bank.manualValue)
            out.writeVarLong(c.cycle.confidencePpm); out.writeVarLong(c.cycle.previousValue); out.writeVarInt(c.cycle.phase.ordinal); out.writeVarLong(c.cycle.shockValue)
            val open = c.gdp.openEntries()
            out.writeVarInt(open.size)
            for ((k, v) in open) { out.writeVarInt(k.ordinal); out.writeVarLong(v) }
            val past = c.gdp.past()
            out.writeVarInt(past.size)
            for (m in past) out.writeMoney(m)
            out.writeMoney(c.fiscal.debt); out.writeVarLong(c.fiscal.revenueRaw); out.writeVarLong(c.fiscal.spendingRaw)
            out.writeMoney(c.fiscal.lastBalance); out.writeMoney(c.fiscal.lastInterest)
            out.writeMoney(c.trendGdp)
            out.writeVarInt(c.snapshots.size)
            for (sn in c.snapshots) {
                out.writeVarLong(sn.tick.value); out.writeMoney(sn.gdp)
                out.writeBoolean(sn.growthPpm != null); sn.growthPpm?.let { out.writeVarLong(it) }
                out.writeVarLong(sn.outputGapPpm); out.writeVarLong(sn.inflationPpm); out.writeVarLong(sn.unemploymentPpm)
                out.writeVarLong(sn.policyRatePpm); out.writeVarInt(sn.phase.ordinal); out.writeVarLong(sn.confidencePpm)
                out.writeVarLong(sn.demandShiftPpm); out.writeVarLong(sn.debtToGdpPpm); out.writeMoney(sn.budgetBalance)
            }
        }
    }

    fun read(r: ByteReader, s: MacroService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported macro save version $version")
        s.clearAll()
        repeat(r.readVarInt()) {
            val id = CountryId(r.readVarLong())
            val c = CountryMacro(id, s.params)
            val rate = r.readVarLong()
            val mode = RateMode.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad rate mode") }
            c.bank.restore(rate, mode, r.readVarLong())
            val conf = r.readVarLong(); val prev = r.readVarLong()
            val phase = CyclePhase.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad cycle phase") }
            c.cycle.restore(conf, prev, phase, r.readVarLong())
            val open = HashMap<GdpKind, Long>()
            repeat(r.readVarInt()) {
                val k = GdpKind.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad gdp kind") }
                open[k] = r.readVarLong()
            }
            val past = ArrayList<Money>()
            repeat(r.readVarInt()) { past += r.readMoney() }
            c.gdp.restore(open, past)
            val debt = r.readMoney(); val rev = r.readVarLong(); val spend = r.readVarLong()
            c.fiscal.restore(debt, rev, spend, r.readMoney(), r.readMoney())
            c.trendGdp = r.readMoney()
            repeat(r.readVarInt()) {
                val tick = Tick(r.readVarLong()); val gdp = r.readMoney()
                val growth = if (r.readBoolean()) r.readVarLong() else null
                val gap = r.readVarLong(); val infl = r.readVarLong(); val unemp = r.readVarLong(); val pol = r.readVarLong()
                val ph = CyclePhase.values().getOrElse(r.readVarInt()) { throw ByteReader.CorruptDataException("bad cycle phase") }
                val conf2 = r.readVarLong(); val shift = r.readVarLong(); val dtg = r.readVarLong(); val bal = r.readMoney()
                c.snapshots += MacroSnapshot(tick, id, gdp, growth, gap, infl, unemp, pol, ph, conf2, shift, dtg, bal)
            }
            s.restore(c)
        }
    }
}
