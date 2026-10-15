package app.many.economytycoon.watch

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId

/** The player's rules, with the memory of when each last fired so a standing condition does not shout every day. */
class RuleBook(private val maxRules: Int = MAX_RULES) {
    private val entries = java.util.TreeMap<Long, RuleEntry>()
    private val lastFired = java.util.TreeMap<Long, Long>()
    private var nextId = 1L

    val size: Int get() = entries.size
    fun all(): List<RuleEntry> = entries.values.toList()
    fun get(id: Long): RuleEntry? = entries[id]

    /** Adds a rule; null when the book is full. */
    fun add(rule: WatchRule, cooldownDays: Int = RuleEntry.DEFAULT_COOLDOWN): Long? {
        if (entries.size >= maxRules) return null
        val id = nextId++
        entries[id] = RuleEntry(id, rule, true, cooldownDays)
        return id
    }

    fun remove(id: Long): Boolean { lastFired.remove(id); return entries.remove(id) != null }

    fun setEnabled(id: Long, enabled: Boolean): Boolean {
        val e = entries[id] ?: return false
        entries[id] = e.copy(enabled = enabled)
        return true
    }

    fun replace(id: Long, rule: WatchRule): Boolean {
        val e = entries[id] ?: return false
        entries[id] = e.copy(rule = rule)
        lastFired.remove(id)
        return true
    }

    fun clear() { entries.clear(); lastFired.clear() }

    fun lastFiredDay(id: Long): Long? = lastFired[id]

    /** Whether a rule may fire on [day], given its cooldown. */
    fun ready(id: Long, day: Long): Boolean {
        val e = entries[id] ?: return false
        if (!e.enabled) return false
        val last = lastFired[id] ?: return true
        return day - last >= e.cooldownDays
    }

    /** Checks every ready rule and records the ones that fire. Hits come back in rule order. */
    fun evaluate(facts: WatchFacts, names: WatchNames, money: (Money) -> String = { app.many.economytycoon.session.Formatting.money(it) }): List<WatchHit> {
        val hits = ArrayList<WatchHit>()
        for (e in entries.values) {
            if (!ready(e.id, facts.day)) continue
            val hit = RuleEvaluator.check(e, facts, names, money) ?: continue
            lastFired[e.id] = facts.day
            hits += hit
        }
        return hits
    }

    fun toBytes(): ByteArray {
        val w = ByteWriter()
        w.writeVarInt(VERSION)
        w.writeVarLong(nextId)
        w.writeVarInt(entries.size)
        for (e in entries.values) {
            w.writeVarLong(e.id); w.writeBoolean(e.enabled); w.writeVarInt(e.cooldownDays)
            write(w, e.rule)
            w.writeVarLong(lastFired[e.id] ?: -1L)
        }
        return w.toByteArray()
    }

    private fun write(w: ByteWriter, r: WatchRule) {
        w.writeVarInt(r.kind.ordinal); w.writeVarInt(r.severity.ordinal)
        when (r) {
            is WatchRule.Price -> { w.writeVarLong(r.region.value); w.writeString(r.good.key); w.writeBoolean(r.above); w.writeMoney(r.level) }
            is WatchRule.Cash -> w.writeMoney(r.level)
            is WatchRule.Profit -> w.writeMoney(r.level)
            is WatchRule.Stock -> { w.writeVarLong(r.issuer.value); w.writeVarLong(r.movePpm) }
            is WatchRule.Loan -> w.writeVarInt(r.days)
            is WatchRule.Delivery -> w.writeVarInt(r.days)
            is WatchRule.Idle -> w.writeVarInt(r.count)
        }
    }

    companion object {
        const val VERSION = 1
        const val MAX_RULES = 50

        private fun read(r: ByteReader): WatchRule {
            val kind = WatchRule.Kind.values()[r.readVarInt()]
            val sev = Severity.values()[r.readVarInt()]
            return when (kind) {
                WatchRule.Kind.PRICE -> WatchRule.Price(RegionId(r.readVarLong()), GoodId(r.readString()), r.readBoolean(), r.readMoney(), sev)
                WatchRule.Kind.CASH -> WatchRule.Cash(r.readMoney(), sev)
                WatchRule.Kind.PROFIT -> WatchRule.Profit(r.readMoney(), sev)
                WatchRule.Kind.STOCK -> WatchRule.Stock(CompanyId(r.readVarLong()), r.readVarLong(), sev)
                WatchRule.Kind.LOAN -> WatchRule.Loan(r.readVarInt(), sev)
                WatchRule.Kind.DELIVERY -> WatchRule.Delivery(r.readVarInt(), sev)
                WatchRule.Kind.IDLE -> WatchRule.Idle(r.readVarInt(), sev)
            }
        }

        /** Reads a saved book; anything unreadable gives an empty one. */
        fun fromBytes(bytes: ByteArray?, maxRules: Int = MAX_RULES): RuleBook {
            val book = RuleBook(maxRules)
            if (bytes == null) return book
            try {
                val r = ByteReader(bytes)
                if (r.readVarInt() != VERSION) return RuleBook(maxRules)
                val next = r.readVarLong()
                val n = r.readVarInt()
                require(n in 0..maxRules) { "implausible rule count" }
                repeat(n) {
                    val id = r.readVarLong(); val enabled = r.readBoolean(); val cooldown = r.readVarInt()
                    val rule = read(r)
                    val fired = r.readVarLong()
                    book.entries[id] = RuleEntry(id, rule, enabled, cooldown)
                    if (fired >= 0L) book.lastFired[id] = fired
                }
                book.nextId = maxOf(next, (book.entries.keys.maxOrNull() ?: 0L) + 1L)
                return book
            } catch (e: ByteReader.CorruptDataException) {
                return RuleBook(maxRules)
            } catch (e: IllegalArgumentException) {
                return RuleBook(maxRules)
            } catch (e: IndexOutOfBoundsException) {
                return RuleBook(maxRules)
            }
        }
    }
}
