package app.many.economytycoon.scenarios

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.world.WorldSize

/** Binary form of a scenario definition, for saves of games that were started from a scenario outside the catalog. */
object ScenarioDefCodec {
    const val VERSION = 1
    private const val MAX_LIST = 2_000

    fun write(d: ScenarioDef, out: ByteWriter) {
        out.writeVarInt(VERSION)
        out.writeString(d.id); out.writeString(d.title); out.writeString(d.tagline); out.writeString(d.story)
        out.writeMoney(d.capital); out.writeVarInt(d.aiCompanies); out.writeVarInt(d.worldSize.ordinal)
        out.writeVarLong(d.seed); out.writeVarInt(d.countryIndex)
        out.writeVarInt(d.goals.size)
        for (g in d.goals) { out.writeVarInt(g.kind.ordinal); out.writeVarLong(g.target) }
        out.writeVarInt(d.years); out.writeVarInt(d.goldDays); out.writeVarInt(d.silverDays)
        val ruin = d.ruinBelow
        out.writeBoolean(ruin != null); if (ruin != null) out.writeVarLong(ruin)
        out.writeVarInt(d.ruinAfterDay)
        out.writeVarInt(d.tips.size); for (t in d.tips) out.writeString(t)
        out.writeVarInt(d.events.size)
        for (e in d.events) { out.writeVarInt(e.day); out.writeVarInt(e.kind.ordinal); out.writeVarLong(e.amount); out.writeString(e.text) }
        out.writeVarInt(d.fails.size)
        for (f in d.fails) { out.writeVarInt(f.kind.ordinal); out.writeVarLong(f.threshold); out.writeVarInt(f.afterDay) }
        out.writeVarInt(d.starters.size)
        for (s in d.starters) { out.writeString(s.recipe); out.writeVarInt(s.regionIndex) }
    }

    fun read(r: ByteReader): ScenarioDef {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported scenario definition version $version")
        fun count(): Int = r.readVarInt().also { if (it !in 0..MAX_LIST) throw ByteReader.CorruptDataException("implausible list size $it") }
        fun <E : Enum<E>> pick(all: Array<E>, what: String): E {
            val at = r.readVarInt()
            return all.getOrNull(at) ?: throw ByteReader.CorruptDataException("unknown $what $at")
        }
        val id = r.readString(); val title = r.readString(); val tagline = r.readString(); val story = r.readString()
        val capital = r.readMoney(); val rivals = r.readVarInt(); val size = pick(WorldSize.values(), "world size")
        val seed = r.readVarLong(); val country = r.readVarInt()
        val goals = List(count()) { Goal(pick(GoalKind.values(), "goal kind"), r.readVarLong()) }
        val years = r.readVarInt(); val gold = r.readVarInt(); val silver = r.readVarInt()
        val ruin = if (r.readBoolean()) r.readVarLong() else null
        val ruinAfter = r.readVarInt()
        val tips = List(count()) { r.readString() }
        val events = List(count()) { DefEvent(r.readVarInt(), pick(DefEventKind.values(), "event kind"), r.readVarLong(), r.readString()) }
        val fails = List(count()) { DefFail(pick(DefFailKind.values(), "failure kind"), r.readVarLong(), r.readVarInt()) }
        val starters = List(count()) { DefSite(r.readString(), r.readVarInt()) }
        val def = ScenarioDef(
            id, title, tagline, story, capital, rivals, size, seed, country, goals, years, gold, silver, ruin, tips,
            ruinAfter, events, fails, starters,
        )
        val problems = def.problems()
        if (problems.isNotEmpty()) throw ByteReader.CorruptDataException("scenario definition is unsound: ${problems.first()}")
        return def
    }

    fun hashInto(d: ScenarioDef, h: CanonicalHasher) {
        h.putString(d.id).putString(d.title).putString(d.tagline).putString(d.story)
        h.putMoney(d.capital).putInt(d.aiCompanies).putInt(d.worldSize.ordinal).putLong(d.seed).putInt(d.countryIndex)
        h.putInt(d.goals.size); for (g in d.goals) h.putInt(g.kind.ordinal).putLong(g.target)
        h.putInt(d.years).putInt(d.goldDays).putInt(d.silverDays)
        h.putBoolean(d.ruinBelow != null).putLong(d.ruinBelow ?: 0L).putInt(d.ruinAfterDay)
        h.putInt(d.tips.size); for (t in d.tips) h.putString(t)
        h.putInt(d.events.size); for (e in d.events) h.putInt(e.day).putInt(e.kind.ordinal).putLong(e.amount).putString(e.text)
        h.putInt(d.fails.size); for (f in d.fails) h.putInt(f.kind.ordinal).putLong(f.threshold).putInt(f.afterDay)
        h.putInt(d.starters.size); for (s in d.starters) h.putString(s.recipe).putInt(s.regionIndex)
    }
}
