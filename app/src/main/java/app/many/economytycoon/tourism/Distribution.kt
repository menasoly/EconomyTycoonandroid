package app.many.economytycoon.tourism

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

enum class IntermediaryKind(val label: String, val channel: Channel) {
    AGENCY("Travel agency", Channel.AGENCY),
    ONLINE_PLATFORM("Online platform", Channel.ONLINE),
}

/**
 * A travel agency or an online booking platform. It sends guests to the properties listed with it and keeps a
 * commission on what they pay. [reachPpm] is how much of the channel's demand it commands against its rivals.
 */
class Intermediary(
    val id: IntermediaryId,
    val kind: IntermediaryKind,
    val owner: CompanyId?,
    val name: String,
) {
    var commissionPpm: Long = 150_000L
        internal set
    var reachPpm: Long = 1_000_000L
        internal set
    var listingFeePerMonth: Money = Money.ZERO
        internal set
    var lifetimeCommission: Money = Money.ZERO
        internal set
    var lifetimeRooms: Long = 0L
        internal set
    var monthCommission: Money = Money.ZERO
        internal set
    var monthRooms: Long = 0L
        internal set

    val channel: Channel get() = kind.channel

    /**
     * How strongly the platform shows a property: online platforms rank by review score, agencies lean toward the
     * properties that pay them the most. 1,000,000 is a neutral listing.
     */
    fun rankingPpm(reviewScorePpm: Long): Long = when (kind) {
        IntermediaryKind.ONLINE_PLATFORM -> 400_000L + mulDiv(reviewScorePpm.coerceIn(0L, 1_000_000L), 1_200_000L, 1_000_000L)
        IntermediaryKind.AGENCY -> 800_000L + mulDiv(commissionPpm, 1_500_000L, 1_000_000L) + reviewScorePpm.coerceIn(0L, 1_000_000L) / 5L
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putInt(kind.ordinal).putLong(owner?.value ?: -1L).putString(name)
        h.putLong(commissionPpm).putLong(reachPpm).putMoney(listingFeePerMonth).putMoney(lifetimeCommission).putLong(lifetimeRooms)
        h.putMoney(monthCommission).putLong(monthRooms)
    }

    companion object {
        const val MAX_COMMISSION_PPM = 350_000L
    }
}
