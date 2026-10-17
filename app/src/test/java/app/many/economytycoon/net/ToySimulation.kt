package app.many.economytycoon.net

import app.many.economytycoon.companies.Department
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.simulation.Command

/**
 * A tiny deterministic game with the same shape as the real one: companies owned by players, commands that
 * must be applied in order to give the same result, cash that evolves each tick and a hash of everything.
 * It lets the protocol tests run hundreds of ticks in milliseconds.
 */
class ToySimulation(override val fingerprint: String = "toy:1", private val income: Boolean = true) : SimulationPort {
    class Co(val id: Long, val owner: PlayerId, var name: String) {
        var cash = 10_000_000L
        var budget = 0L
        var revenue = 0L
        val goods = java.util.TreeMap<String, Long>()
        var alive = true
    }

    override var tick: Long = 0L
        private set
    private var next = 1L
    private var folded = 17L
    val companies = java.util.TreeMap<Long, Co>()
    private val queue = ArrayList<Pair<PlayerId, Command>>()
    var escrowCash = 0L
    val escrowGoods = java.util.TreeMap<String, Long>()
    var advances = 0
        private set

    override fun stateHash(): ByteArray = Hmac.sha256(canonical())

    private fun canonical(): ByteArray {
        val w = ByteWriter()
        w.writeVarLong(tick); w.writeVarLong(next); w.writeVarLong(folded); w.writeVarLong(escrowCash)
        for ((g, v) in escrowGoods) { w.writeString(g); w.writeVarLong(v) }
        for (c in companies.values) {
            w.writeVarLong(c.id); w.writeVarLong(c.owner.value); w.writeString(c.name); w.writeVarLong(c.cash); w.writeVarLong(c.budget)
            w.writeVarLong(c.revenue); w.writeBoolean(c.alive)
            for ((g, v) in c.goods) { w.writeString(g); w.writeVarLong(v) }
        }
        return w.toByteArray()
    }

    override fun enqueue(player: PlayerId, command: Command) { queue += player to command }

    override fun advance(): List<AppliedCommand> {
        advances++
        val applied = ArrayList<AppliedCommand>()
        for ((p, cmd) in queue) {
            val rule = apply(p, cmd)
            applied += AppliedCommand(p, rule == null, rule ?: "")
            folded = folded * 1_000_003L + cmd.hashCode() * 31L + p.value
        }
        queue.clear()
        if (income) for (c in companies.values) {
            val gain = (tick % 5 + 1) * 10_000L + c.id
            c.cash += gain - c.budget / 100
            c.revenue += gain
        }
        tick++
        return applied
    }

    private fun apply(p: PlayerId, cmd: Command): String? {
        val id = cmd.company?.value ?: return Rules.INVALID_ARGUMENT
        val co = companies[id] ?: return Rules.UNKNOWN_ENTITY
        if (co.owner != p) return Rules.NOT_OWNER
        return when (cmd) {
            is Command.Rename -> if (cmd.name.length < 3 || companies.values.any { it.name == cmd.name }) Rules.INVALID_ARGUMENT else { co.name = cmd.name; null }
            is Command.SetBudget -> if (cmd.perTick.raw < 0) Rules.INVALID_ARGUMENT else { co.budget = cmd.perTick.raw; null }
            else -> null
        }
    }

    override fun applyTransfers(transfers: List<Transfer>): List<Boolean> = transfers.map { t ->
        val a = t.asset
        fun held(c: Long): Long = when {
            c == 0L -> if (a.kind == AssetKind.CASH) escrowCash else escrowGoods[a.key] ?: 0L
            else -> companies[c]?.let { if (a.kind == AssetKind.CASH) it.cash else it.goods[a.key] ?: 0L } ?: -1L
        }
        if (a.amount <= 0L || held(t.from) < a.amount || held(t.to) < 0L || (t.from != 0L && companies[t.from]?.alive != true) || (t.to != 0L && companies[t.to]?.alive != true)) return@map false
        fun add(c: Long, d: Long) {
            if (c == 0L) { if (a.kind == AssetKind.CASH) escrowCash += d else escrowGoods.merge(a.key, d, Long::plus) }
            else companies[c]?.let { if (a.kind == AssetKind.CASH) it.cash += d else it.goods.merge(a.key, d, Long::plus) }
        }
        add(t.from, -a.amount); add(t.to, a.amount)
        true
    }

    override fun seat(player: PlayerId, name: String): Outcome<Long> {
        if (companies.values.any { it.name == name }) return Outcome.reject(Rules.NAME_TAKEN, "taken")
        val c = Co(next++, player, name)
        companies[c.id] = c
        return Outcome.ok(c.id)
    }

    override fun companiesOf(player: PlayerId): List<Long> = companies.values.filter { it.owner == player && it.alive }.map { it.id }

    override fun available(company: Long, asset: TradeAsset): Long {
        val c = companies[company] ?: return 0L
        return when (asset.kind) { AssetKind.CASH -> c.cash.coerceAtLeast(0L); AssetKind.GOODS -> c.goods[asset.key] ?: 0L }
    }

    override fun supports(kind: AssetKind) = true

    fun giveGoods(company: Long, good: String, amount: Long) { companies[company]?.goods?.merge(good, amount, Long::plus) }

    override fun snapshot(): ByteArray {
        val w = ByteWriter()
        w.writeVarLong(tick); w.writeVarLong(next); w.writeVarLong(folded); w.writeVarLong(escrowCash)
        w.writeVarInt(escrowGoods.size); for ((g, v) in escrowGoods) { w.writeString(g); w.writeVarLong(v) }
        w.writeVarInt(companies.size)
        for (c in companies.values) {
            w.writeVarLong(c.id); w.writeVarLong(c.owner.value); w.writeString(c.name); w.writeVarLong(c.cash); w.writeVarLong(c.budget)
            w.writeVarLong(c.revenue); w.writeBoolean(c.alive)
            w.writeVarInt(c.goods.size); for ((g, v) in c.goods) { w.writeString(g); w.writeVarLong(v) }
        }
        // pad so snapshots span several chunks in transfer tests
        w.writeBytes(ByteArray(PADDING) { (it % 251).toByte() })
        return w.toByteArray()
    }

    override fun restore(bytes: ByteArray): Outcome<Unit> = try {
        val r = ByteReader(bytes)
        val t = r.readVarLong(); val n = r.readVarLong(); val f = r.readVarLong(); val e = r.readVarLong()
        val eg = java.util.TreeMap<String, Long>(); repeat(r.readVarInt()) { eg[r.readString()] = r.readVarLong() }
        val cs = java.util.TreeMap<Long, Co>()
        repeat(r.readVarInt()) {
            val c = Co(r.readVarLong(), PlayerId(r.readVarLong()), r.readString())
            c.cash = r.readVarLong(); c.budget = r.readVarLong(); c.revenue = r.readVarLong(); c.alive = r.readBoolean()
            repeat(r.readVarInt()) { c.goods[r.readString()] = r.readVarLong() }
            cs[c.id] = c
        }
        r.readBytes()
        tick = t; next = n; folded = f; escrowCash = e
        escrowGoods.clear(); escrowGoods.putAll(eg); companies.clear(); companies.putAll(cs); queue.clear()
        Outcome.ok(Unit)
    } catch (ex: ByteReader.CorruptDataException) {
        Outcome.reject(Rules.INVALID_ARGUMENT, ex.message ?: "damaged")
    }

    override fun statements(fromTick: Long): List<CompanyStatement> = companies.values.map {
        val worth = it.cash + it.goods.values.sum() + 0L
        CompanyStatement(it.id, it.name, it.owner, Money(it.revenue), Money(it.revenue - it.budget), Money(worth), Money(it.cash), Money.ZERO, Money(worth), it.alive)
    }

    /** Sum of all cash including escrow, for conservation checks. */
    fun totalCash(): Long = companies.values.sumOf { it.cash } + escrowCash
    fun totalGoods(good: String): Long = companies.values.sumOf { it.goods[good] ?: 0L } + (escrowGoods[good] ?: 0L)

    companion object {
        const val PADDING = 40_000
        fun rename(company: Long, name: String) = Command.Rename(app.many.economytycoon.core.CompanyId(company), name)
        fun budget(company: Long, perTick: Long) = Command.SetBudget(app.many.economytycoon.core.CompanyId(company), Department.MARKETING, Money.units(perTick))
    }
}
