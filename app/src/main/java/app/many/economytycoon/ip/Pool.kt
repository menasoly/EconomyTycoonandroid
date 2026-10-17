package app.many.economytycoon.ip

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick

/** One company's seat in a pool and the patents it put in. */
class PoolMember(val company: CompanyId, val joinedAt: Tick) {
    internal val patentSet = java.util.TreeSet<PatentId>()
    var earned: Money = Money.ZERO
        internal set
    val patents: List<PatentId> get() = patentSet.toList()
}

/**
 * Companies that put the patents every maker of a product needs into one shop and license them together at one
 * price. Pool terms must be FRAND; the administrator keeps a small cut and the rest is shared by the weight of the
 * patents each member contributed.
 */
class PatentPool(
    val id: PoolId,
    val name: String,
    val administrator: CompanyId,
    val field: TechField,
    val createdAt: Tick,
    /** The standard bundle price for outside licensees. */
    val terms: RoyaltyTerms,
    /** Share of royalties the administrator keeps, ppm. */
    val adminFeePpm: Long,
) {
    var open: Boolean = true
        internal set
    var dissolvedAt: Tick? = null
        internal set
    var collected: Money = Money.ZERO
        internal set
    var adminEarned: Money = Money.ZERO
        internal set
    internal val memberMap = java.util.TreeMap<CompanyId, PoolMember>()

    val members: List<PoolMember> get() = memberMap.values.toList()
    fun member(c: CompanyId): PoolMember? = memberMap[c]
    fun allPatents(): List<PatentId> = memberMap.values.flatMap { it.patentSet }.sorted()
    fun contains(patent: PatentId): Boolean = memberMap.values.any { patent in it.patentSet }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putString(name).putLong(administrator.value).putInt(field.ordinal).putLong(createdAt.value)
        terms.hashInto(h)
        h.putLong(adminFeePpm).putBoolean(open).putLong(dissolvedAt?.value ?: -1L).putMoney(collected).putMoney(adminEarned)
        h.putInt(memberMap.size)
        for (m in memberMap.values) {
            h.putLong(m.company.value).putLong(m.joinedAt.value).putMoney(m.earned).putInt(m.patentSet.size)
            for (p in m.patentSet) h.putLong(p.value)
        }
    }
}
