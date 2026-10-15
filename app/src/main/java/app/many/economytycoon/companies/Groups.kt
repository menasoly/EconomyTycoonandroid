package app.many.economytycoon.companies

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.BalanceSheet
import app.many.economytycoon.finance.EquityService
import app.many.economytycoon.finance.Holder
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.OwnerKind

/**
 * Ownership graph between companies. A stake is a share of a child's voting power held by a parent. The
 * graph is a forest: a company has at most one controlling parent and cycles are rejected.
 */
class CorporateGroups {
    private val stakes = java.util.TreeMap<Pair<CompanyId, CompanyId>, Long>(compareBy({ it.first }, { it.second }))
    private val controllers = java.util.TreeMap<CompanyId, CompanyId>()

    fun stake(parent: CompanyId, child: CompanyId): Rate = Rate(stakes[parent to child] ?: 0L)
    fun controllerOf(child: CompanyId): CompanyId? = controllers[child]
    fun stakesIn(child: CompanyId): List<Pair<CompanyId, Rate>> = stakes.filterKeys { it.second == child }.map { it.key.first to Rate(it.value) }
    fun stakesHeldBy(parent: CompanyId): List<Pair<CompanyId, Rate>> = stakes.filterKeys { it.first == parent }.map { it.key.second to Rate(it.value) }

    fun subsidiaries(parent: CompanyId): List<CompanyId> = controllers.filterValues { it == parent }.keys.toList()

    fun descendants(parent: CompanyId): List<CompanyId> {
        val out = ArrayList<CompanyId>()
        val queue = ArrayDeque(subsidiaries(parent))
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (c in out) continue
            out += c
            queue.addAll(subsidiaries(c))
        }
        return out.sorted()
    }

    fun ultimateParent(c: CompanyId): CompanyId {
        var cur = c
        val seen = HashSet<CompanyId>()
        while (true) {
            val p = controllers[cur] ?: return cur
            if (!seen.add(cur)) return cur
            cur = p
        }
    }

    fun group(root: CompanyId): List<CompanyId> = (listOf(root) + descendants(root)).sorted()

    /** Would making [parent] a controller of [child] create a loop? */
    fun wouldCycle(parent: CompanyId, child: CompanyId): Boolean = parent == child || parent in descendants(child)

    /** Records the stake and, if it crosses [controlThreshold], makes the parent the controller. */
    fun setStake(parent: CompanyId, child: CompanyId, share: Rate, controlThreshold: Rate = Rate.percent(50)): Outcome<Unit> {
        if (share.ppm < 0 || share.ppm > 1_000_000L) return Outcome.reject(Rules.INVALID_ARGUMENT, "stake must be 0-100%")
        if (share.ppm > 0 && wouldCycle(parent, child)) return Outcome.reject(Rules.OWNERSHIP_CYCLE, "that stake would make a company own itself")
        if (share.ppm == 0L) stakes.remove(parent to child) else stakes[parent to child] = share.ppm
        recomputeController(child, controlThreshold)
        return Outcome.ok(Unit)
    }

    private fun recomputeController(child: CompanyId, threshold: Rate) {
        val top = stakesIn(child).filter { it.second >= threshold }.maxByOrNull { it.second.ppm }
        if (top == null) controllers.remove(child) else controllers[child] = top.first
    }

    /** Rebuilds the graph from share registers. Holders of kind "owner" that are companies count as parents. */
    fun rebuild(equity: EquityService, companies: List<Company>, controlThreshold: Rate = Rate.percent(50)) {
        stakes.clear(); controllers.clear()
        val byOwner = companies.associateBy { it.owner }
        for (child in companies.sortedBy { it.id }) {
            val table = equity.table(child.owner) ?: continue
            for (holder in table.holders()) {
                if (holder.kind != "owner") continue
                val parent = byOwner[LedgerOwner(OwnerKind.COMPANY, holder.id)] ?: continue
                if (parent.id == child.id) continue
                val share = table.votingShare(holder)
                if (share.ppm > 0 && !wouldCycle(parent.id, child.id)) stakes[parent.id to child.id] = share.ppm
            }
            recomputeController(child.id, controlThreshold)
        }
    }

    fun remove(c: CompanyId) {
        stakes.keys.removeAll { it.first == c || it.second == c }
        controllers.remove(c)
        controllers.entries.removeAll { it.value == c }
    }

    internal fun entries() = stakes.map { Triple(it.key.first, it.key.second, it.value) }
    internal fun restore(list: List<Triple<CompanyId, CompanyId, Long>>, threshold: Rate = Rate.percent(50)) {
        stakes.clear(); controllers.clear()
        for ((p, c, s) in list) stakes[p to c] = s
        for (c in stakes.keys.map { it.second }.distinct()) recomputeController(c, threshold)
    }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(stakes.size)
        for ((k, v) in stakes) h.putLong(k.first.value).putLong(k.second.value).putLong(v)
    }
}

/** Proportional consolidation of balance sheets across a group. */
object Consolidation {
    class Member(val company: CompanyId, val sheet: BalanceSheet, val ownedPpm: Long)

    /**
     * Totals assets, liabilities and equity of the members weighted by the group's economic interest.
     * Returns (assets, liabilities, equity).
     */
    fun proportional(members: List<Member>): Triple<Money, Money, Money> {
        var a = 0L; var l = 0L; var e = 0L
        for (m in members) {
            a += mulDiv(m.sheet.totalAssets.raw, m.ownedPpm, 1_000_000L)
            l += mulDiv(m.sheet.totalLiabilities.raw, m.ownedPpm, 1_000_000L)
            e += mulDiv(m.sheet.totalEquity.raw, m.ownedPpm, 1_000_000L)
        }
        return Triple(Money(a), Money(l), Money(e))
    }

    /** Economic interest of the group root in every descendant, multiplying stakes down the chain. */
    fun interests(groups: CorporateGroups, root: CompanyId): Map<CompanyId, Long> {
        val out = java.util.TreeMap<CompanyId, Long>()
        out[root] = 1_000_000L
        fun walk(parent: CompanyId, share: Long, depth: Int) {
            if (depth > 12) return
            for ((child, stake) in groups.stakesHeldBy(parent)) {
                val s = mulDiv(share, stake.ppm, 1_000_000L)
                if (s > (out[child] ?: 0L)) out[child] = s
                walk(child, s, depth + 1)
            }
        }
        walk(root, 1_000_000L, 0)
        return out
    }
}

