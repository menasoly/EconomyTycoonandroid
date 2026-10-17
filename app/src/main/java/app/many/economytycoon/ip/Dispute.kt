package app.many.economytycoon.ip

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick

enum class DisputeKind(val label: String) {
    INFRINGEMENT("Patent infringement"),
    MISAPPROPRIATION("Trade secret theft"),
    INVALIDITY("Challenge to a patent"),
}

enum class DisputeStage(val label: String, val open: Boolean) {
    DETECTED("Spotted", true),
    NOTICE("Demand sent", true),
    FILED("Suit filed", true),
    DISCOVERY("Discovery", true),
    TRIAL("At trial", true),
    JUDGMENT("Judgment given", true),
    APPEAL("On appeal", true),
    CLOSED("Closed", false),
}

enum class DisputeResult(val label: String) {
    PENDING("Undecided"),
    CLAIMANT_WON("Claimant won"),
    DEFENDANT_WON("Defendant won"),
    SETTLED("Settled"),
    LICENSED("Licensed"),
    DESIGNED_AROUND("Designed around"),
    WITHDRAWN("Withdrawn"),
    LAPSED("Fell away"),
}

/**
 * A fight over a patent or a secret. The claimant is whoever says its right was violated (or, for a challenge, whoever
 * wants a patent struck down); the defendant is the other side. Evidence strengths and the costs run up on each side
 * decide how it ends.
 */
class Dispute(
    val id: DisputeId,
    val kind: DisputeKind,
    val claimant: IpOwner,
    val defendant: IpOwner,
    val patent: PatentId?,
    val secret: SecretId?,
    val country: CountryId,
    val openedAt: Tick,
) {
    var stage: DisputeStage = DisputeStage.DETECTED
        internal set
    var stageSince: Tick = openedAt
        internal set
    var result: DisputeResult = DisputeResult.PENDING
        internal set
    var closedAt: Tick? = null
        internal set
    var claimantEvidencePpm: Long = 0L
        internal set
    var defendantEvidencePpm: Long = 0L
        internal set
    var infringingUnits: Quantity = Quantity.ZERO
        internal set
    var infringingSales: Money = Money.ZERO
        internal set
    var willful: Boolean = false
        internal set
    var demand: Money = Money.ZERO
        internal set
    var claimantCosts: Money = Money.ZERO
        internal set
    var defendantCosts: Money = Money.ZERO
        internal set
    var claimantCostsPaid: Money = Money.ZERO
        internal set
    var defendantCostsPaid: Money = Money.ZERO
        internal set
    var award: Money = Money.ZERO
        internal set
    var awardPaid: Boolean = false
        internal set
    var injunctionUntil: Tick? = null
        internal set
    var courtRoyalty: RoyaltyTerms? = null
        internal set
    var patentRevoked: Boolean = false
        internal set
    var appealedBy: IpOwner? = null
        internal set
    var judgmentAt: Tick? = null
        internal set
    var decidedForClaimant: Boolean? = null
        internal set
    var offerBy: IpOwner? = null
        internal set
    var offerAmount: Money = Money.ZERO
        internal set
    var noticeAt: Tick? = null
        internal set

    val isOpen: Boolean get() = stage.open
    fun involves(owner: IpOwner): Boolean = claimant == owner || defendant == owner
    fun injunctionAt(tick: Tick): Boolean = injunctionUntil?.let { tick < it } ?: false

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putInt(kind.ordinal).putInt(claimant.kind.ordinal).putLong(claimant.id).putInt(defendant.kind.ordinal).putLong(defendant.id)
        h.putLong(patent?.value ?: -1L).putLong(secret?.value ?: -1L).putLong(country.value).putLong(openedAt.value)
        h.putInt(stage.ordinal).putLong(stageSince.value).putInt(result.ordinal).putLong(closedAt?.value ?: -1L)
        h.putLong(claimantEvidencePpm).putLong(defendantEvidencePpm).putQuantity(infringingUnits).putMoney(infringingSales)
        h.putBoolean(willful).putMoney(demand).putMoney(claimantCosts).putMoney(defendantCosts).putMoney(claimantCostsPaid).putMoney(defendantCostsPaid)
        h.putMoney(award).putBoolean(awardPaid).putLong(injunctionUntil?.value ?: -1L)
        val r = courtRoyalty
        h.putBoolean(r != null); r?.hashInto(h)
        h.putBoolean(patentRevoked).putInt(appealedBy?.kind?.ordinal ?: -1).putLong(appealedBy?.id ?: -1L).putLong(judgmentAt?.value ?: -1L)
        h.putInt(when (decidedForClaimant) { null -> -1; true -> 1; false -> 0 })
        h.putInt(offerBy?.kind?.ordinal ?: -1).putLong(offerBy?.id ?: -1L).putMoney(offerAmount).putLong(noticeAt?.value ?: -1L)
    }
}
