package app.many.economytycoon.ip

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick

enum class SecretStatus(val label: String, val live: Boolean) {
    ACTIVE("Secret", true),
    LEAKED("Leaked", false),
    REVERSE_ENGINEERED("Reverse engineered", false),
    SHARED("Independently discovered", false),
    PATENTED("Moved to a patent", false),
    ABANDONED("Dropped", false),
}

/**
 * Know-how a company keeps to itself instead of patenting. It costs no fees and never expires, but it is lost the
 * moment it leaks, is worked out from the product or is found independently by a rival.
 */
class TradeSecret(
    val id: SecretId,
    val owner: CompanyId,
    val tech: TechId,
    val field: TechField,
    val createdAt: Tick,
) {
    var status: SecretStatus = SecretStatus.ACTIVE
        internal set
    var endedAt: Tick? = null
        internal set
    /** How well the secret is guarded, ppm; builds with security spending and fades without upkeep. */
    var protectionPpm: Long = 0L
        internal set
    var securitySpent: Money = Money.ZERO
        internal set
    /** People who know it, as last reported by the host. */
    var knowers: Int = 0
        internal set
    /** How much of the secret the company's products give away, ppm of the daily hazard scale. */
    var exposurePpm: Long = 0L
        internal set
    var incidents: Int = 0
        internal set
    internal val knownToSet = java.util.TreeSet<CompanyId>()

    /** Rivals that have obtained the secret by espionage. */
    val knownTo: List<CompanyId> get() = knownToSet.toList()
    val isLive: Boolean get() = status.live
    val isExclusive: Boolean get() = status.live && knownToSet.isEmpty()

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(owner.value).putString(tech.key).putInt(field.ordinal).putLong(createdAt.value)
        h.putInt(status.ordinal).putLong(endedAt?.value ?: -1L).putLong(protectionPpm).putMoney(securitySpent)
        h.putInt(knowers).putLong(exposurePpm).putInt(incidents)
        h.putInt(knownToSet.size); for (c in knownToSet) h.putLong(c.value)
    }
}
