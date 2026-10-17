package app.many.economytycoon.blueprint

import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.MachineTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.production.SchedulePolicy

/** What reading a stored or shared blueprint gave: the blueprint, and what had to be converted on the way. */
sealed class Decoded {
    data class Ok(val blueprint: Blueprint, val schema: Int, val notes: List<String>) : Decoded()
    data class Failed(val message: String) : Decoded()
}

/**
 * Binary form of one blueprint, shared by the library save and the copy-paste text. The first value is the schema
 * version. Newer schemas only ever add data; older ones are read through [BlueprintMigrations].
 */
object BlueprintCodec {
    const val SCHEMA = 2

    fun encode(bp: Blueprint): ByteArray = ByteWriter().also { write(bp, it) }.toByteArray()

    fun write(bp: Blueprint, w: ByteWriter) {
        w.writeVarInt(SCHEMA)
        w.writeString(bp.name); w.writeString(bp.note); w.writeString(bp.contentTag); w.writeVarLong(bp.createdDay)
        writeSpec(bp.spec.normalized(), w)
    }

    fun writeSpec(s: BlueprintSpec, w: ByteWriter) {
        w.writeString(s.facilityType.key)
        w.writeVarInt(s.policy.ordinal); w.writeVarLong(s.qualityPpm); w.writeVarInt(s.storageExpansions)
        w.writeVarInt(s.lines.size)
        for (l in s.lines) {
            w.writeVarInt(l.priority); w.writeBoolean(l.enabled); w.writeBoolean(l.recipe != null)
            l.recipe?.let { w.writeString(it.key) }
            w.writeVarInt(l.machines.size)
            for (m in l.machines) w.writeString(m.key)
        }
        w.writeVarInt(s.staff.size)
        for (t in s.staff) {
            w.writeString(t.role.key); w.writeVarLong(t.target); w.writeBoolean(t.wage != null)
            t.wage?.let { w.writeMoney(it) }
            w.writeVarLong(t.trainingPpm)
        }
        w.writeVarInt(s.sells.size)
        for (x in s.sells) {
            w.writeString(x.good.key); w.writeVarInt(x.strategy.ordinal); w.writeVarLong(x.markupPpm); w.writeQuantity(x.reserve)
            w.writeBoolean(x.maxPerOrder != null); x.maxPerOrder?.let { w.writeQuantity(it) }
            w.writeVarLong(x.minMarginPpm); w.writeBoolean(x.enabled)
        }
        w.writeVarInt(s.buys.size)
        for (b in s.buys) { w.writeString(b.good.key); w.writeQuantity(b.target); w.writeVarLong(b.maxPriceMultiplePpm); w.writeBoolean(b.enabled) }
    }

    /** Reads one blueprint, migrating older schemas. Never throws for bad input. */
    fun decode(bytes: ByteArray): Decoded {
        val r = ByteReader(bytes)
        return decode(r)
    }

    fun decode(r: ByteReader): Decoded = try {
        val schema = r.readVarInt()
        when {
            schema < 1 -> Decoded.Failed("Not a blueprint (schema $schema).")
            schema > SCHEMA -> Decoded.Failed("This blueprint was made by a newer version of the game (format $schema, this game reads up to $SCHEMA).")
            else -> {
                val notes = ArrayList<String>()
                val name = r.readString(); val note = r.readString(); val tag = r.readString(); val day = r.readVarLong()
                val spec = readSpec(r, schema, notes)
                Decoded.Ok(Blueprint.of(spec, name, note, tag, day), schema, notes)
            }
        }
    } catch (e: ByteReader.CorruptDataException) {
        Decoded.Failed("The blueprint data is damaged: ${e.message}.")
    } catch (e: IllegalArgumentException) {
        Decoded.Failed("The blueprint data is not valid: ${e.message}.")
    }

    internal fun readSpec(r: ByteReader, schema: Int, notes: MutableList<String>): BlueprintSpec {
        val type = FacilityTypeId(r.readString())
        val policy = pick(SchedulePolicy.values(), r.readVarInt(), "schedule policy")
        val rawQuality = r.readVarLong()
        val quality = BlueprintMigrations.quality(schema, rawQuality, notes)
        val expansions = if (schema >= 2) r.readVarInt() else 0
        val lines = List(count(r.readVarInt(), BlueprintLimits.MAX_LINES, "lines")) {
            val priority = r.readVarInt(); val enabled = r.readBoolean()
            val recipe = if (r.readBoolean()) RecipeId(r.readString()) else null
            val machines = List(count(r.readVarInt(), BlueprintLimits.MAX_MACHINES_PER_LINE, "machines")) { MachineTypeId(r.readString()) }
            LineSpec(priority, enabled, recipe, machines)
        }
        val staff = List(count(r.readVarInt(), BlueprintLimits.MAX_STAFF_ROLES, "staff")) {
            val role = RoleId(r.readString()); val target = r.readVarLong()
            val wage = if (r.readBoolean()) r.readMoney() else null
            val training = if (schema >= 2) r.readVarLong() else 0L
            StaffSpec(role, target, wage, training)
        }
        val sells = List(count(r.readVarInt(), BlueprintLimits.MAX_RULES, "sell rules")) {
            val good = GoodId(r.readString()); val strategy = pick(PriceStrategy.values(), r.readVarInt(), "price strategy")
            val markup = r.readVarLong(); val reserve = r.readQuantity()
            val cap = if (r.readBoolean()) r.readQuantity() else null
            val minMargin = if (schema >= 2) r.readVarLong() else 0L
            SellSpec(good, strategy, markup, reserve, cap, minMargin, r.readBoolean())
        }
        val buys = List(count(r.readVarInt(), BlueprintLimits.MAX_RULES, "buy rules")) {
            BuySpec(GoodId(r.readString()), r.readQuantity(), r.readVarLong(), r.readBoolean())
        }
        BlueprintMigrations.addedNotes(schema, staff.isNotEmpty(), sells.isNotEmpty(), notes)
        return BlueprintSpec(type, lines, policy, quality, staff, sells, buys, expansions).normalized()
    }

    private fun count(n: Int, max: Int, what: String): Int {
        if (n < 0 || n > max) throw ByteReader.CorruptDataException("implausible number of $what ($n)")
        return n
    }

    private fun <T> pick(all: Array<T>, index: Int, what: String): T =
        all.getOrNull(index) ?: throw ByteReader.CorruptDataException("unknown $what $index")
}

/** Conversions from older blueprint schemas, each producing a note the player can read. */
object BlueprintMigrations {
    /** Schema 1 stored the quality level as a whole percent; schema 2 uses parts per million. */
    fun quality(schema: Int, raw: Long, notes: MutableList<String>): Long {
        if (schema >= 2) return raw
        val ppm = raw.coerceIn(0L, 100L) * 10_000L
        notes += "Quality assurance was saved as a whole percent ($raw%) and has been converted."
        return ppm
    }

    fun addedNotes(schema: Int, hasStaff: Boolean, hasSells: Boolean, notes: MutableList<String>) {
        if (schema >= 2) return
        if (hasStaff) notes += "Older blueprints carry no training levels; staff training is left at none."
        if (hasSells) notes += "Older selling rules carry no minimum margin; it is set to break-even."
        notes += "Older blueprints carry no storage expansions; none will be built."
    }
}
