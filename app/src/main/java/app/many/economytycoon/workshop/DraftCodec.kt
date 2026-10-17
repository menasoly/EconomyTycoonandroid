package app.many.economytycoon.workshop

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.world.WorldSize

/**
 * Binary form of a [ScenarioDraft]. A version byte leads; a later version only adds fields after the ones listed here
 * and an older reader stops at the length of the block it knows, so a draft written by a newer build still opens.
 */
object DraftCodec {
    const val VERSION = 1

    fun toBytes(d: ScenarioDraft): ByteArray = ByteWriter().also { write(d, it) }.toByteArray()

    fun fromBytes(bytes: ByteArray): ScenarioDraft {
        val r = ByteReader(bytes)
        val d = read(r)
        if (r.remaining != 0) throw ByteReader.CorruptDataException("${r.remaining} stray bytes after the draft")
        return d
    }

    fun write(d: ScenarioDraft, out: ByteWriter) {
        out.writeVarInt(VERSION)
        val body = ByteWriter()
        writeBody(d.normalised(), body)
        out.writeBytes(body.toByteArray())
    }

    fun read(r: ByteReader): ScenarioDraft {
        val version = r.readVarInt()
        if (version < 1) throw ByteReader.CorruptDataException("bad draft version $version")
        // A newer build may append fields to the block; the block length lets this reader skip them.
        return readBody(ByteReader(r.readBytes()))
    }

    private fun writeBody(d: ScenarioDraft, w: ByteWriter) {
        w.writeString(d.id); w.writeString(d.title); w.writeString(d.tagline); w.writeString(d.story)
        w.writeVarLong(d.seed)
        w.writeVarInt(d.world.size.ordinal); w.writeVarInt(d.world.rivals); w.writeVarInt(d.world.countryIndex)
        w.writeString(d.company.name); w.writeVarLong(d.company.capital)
        w.writeVarInt(d.company.starters.size)
        for (s in d.company.starters) { w.writeString(s.recipe); w.writeVarInt(s.regionIndex) }
        w.writeVarInt(d.rules.size)
        for ((k, v) in d.rules.entries()) { w.writeVarInt(k.ordinal); w.writeVarLong(v) }
        w.writeVarInt(d.events.size)
        for (e in d.events) { w.writeVarInt(e.day); w.writeVarInt(e.kind.ordinal); w.writeVarLong(e.amount); w.writeString(e.text) }
        w.writeVarInt(d.goals.size)
        for (g in d.goals) { w.writeVarInt(g.kind.ordinal); w.writeVarLong(g.target); w.writeVarInt(g.weight); w.writeBoolean(g.bonus) }
        w.writeVarInt(d.fails.size)
        for (f in d.fails) { w.writeVarInt(f.kind.ordinal); w.writeVarLong(f.threshold); w.writeVarInt(f.afterDay) }
        w.writeVarInt(d.timing.years); w.writeVarInt(d.timing.goldDays); w.writeVarInt(d.timing.silverDays)
        w.writeVarInt(d.tags.size)
        for (t in d.tags) w.writeVarInt(t.ordinal)
        w.writeVarInt(d.hints.size)
        for (h in d.hints) w.writeString(h)
        w.writeVarInt(d.packs.size)
        for (p in d.packs) w.writeString(p)
    }

    private fun <E : Enum<E>> pick(values: Array<E>, at: Int, what: String): E =
        values.getOrNull(at) ?: throw ByteReader.CorruptDataException("unknown $what $at")

    private fun count(r: ByteReader, max: Int, what: String): Int {
        val n = r.readVarInt()
        if (n < 0 || n > max) throw ByteReader.CorruptDataException("bad $what count $n")
        return n
    }

    private fun readBody(r: ByteReader): ScenarioDraft {
        val id = r.readString(); val title = r.readString(); val tagline = r.readString(); val story = r.readString()
        val seed = r.readVarLong()
        val world = WorldSettings(pick(WorldSize.values(), r.readVarInt(), "world size"), r.readVarInt(), r.readVarInt())
        val name = r.readString(); val capital = r.readVarLong()
        val starters = List(count(r, DraftLimits.MAX_STARTERS, "starter")) { StarterSite(r.readString(), r.readVarInt()) }
        var rules = RuleOverrides()
        repeat(count(r, RuleKey.values().size, "rule")) { rules = rules.with(pick(RuleKey.values(), r.readVarInt(), "rule key"), r.readVarLong()) }
        val events = List(count(r, DraftLimits.MAX_EVENTS, "event")) {
            ScriptedEvent(r.readVarInt(), pick(EventKind.values(), r.readVarInt(), "event kind"), r.readVarLong(), r.readString())
        }
        val goals = List(count(r, DraftLimits.MAX_GOALS, "goal")) {
            DraftGoal(pick(GoalKind.values(), r.readVarInt(), "goal kind"), r.readVarLong(), r.readVarInt(), r.readBoolean())
        }
        val fails = List(count(r, DraftLimits.MAX_FAILS, "failure")) { FailCondition(pick(FailKind.values(), r.readVarInt(), "failure kind"), r.readVarLong(), r.readVarInt()) }
        val timing = Timing(r.readVarInt(), r.readVarInt(), r.readVarInt())
        val tags = List(count(r, DraftLimits.MAX_TAGS, "tag")) { pick(DifficultyTag.values(), r.readVarInt(), "tag") }
        val hints = List(count(r, DraftLimits.MAX_HINTS, "hint")) { r.readString() }
        val packs = List(count(r, DraftLimits.MAX_PACKS, "pack")) { r.readString() }
        return ScenarioDraft(id, title, tagline, story, seed, world, CompanySetup(name, capital, starters), rules, events, goals, fails, timing, tags, hints, packs)
    }
}
