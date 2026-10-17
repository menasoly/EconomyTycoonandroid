package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money

/**
 * What a company can ask of the carbon package. The simulation turns its own command types into these and the AI
 * returns them directly; both go through the same checks in [CarbonService.execute].
 */
sealed class CarbonCommand {
    abstract val company: CompanyId

    /** A sealed bid for the next auction in [country]: up to [quantity] at no more than [limit] a tonne. */
    data class BidAtAuction(override val company: CompanyId, val country: CountryId, val quantity: Tonnes, val limit: Money) : CarbonCommand()

    data class CancelBid(override val company: CompanyId, val country: CountryId, val bid: BidId) : CarbonCommand()

    /** A limit order in the secondary market. */
    data class PlaceOrder(override val company: CompanyId, val country: CountryId, val side: PermitSide, val quantity: Tonnes, val limit: Money) : CarbonCommand()

    data class CancelOrder(override val company: CompanyId, val country: CountryId, val order: PermitOrderId) : CarbonCommand()

    /** Hands permits in for the year that is waiting to be settled. */
    data class Surrender(override val company: CompanyId, val country: CountryId, val quantity: Tonnes) : CarbonCommand()

    data class BuyOffsets(override val company: CompanyId, val kind: OffsetKind, val quantity: Tonnes) : CarbonCommand()

    /** Retires credits, either against the obligation waiting to be settled or voluntarily for the record. */
    data class RetireOffsets(override val company: CompanyId, val country: CountryId, val kind: OffsetKind, val quantity: Tonnes, val forCompliance: Boolean) : CarbonCommand()

    data class FundOffsetProject(override val company: CompanyId, val kind: OffsetKind, val host: CountryId, val annual: Tonnes) : CarbonCommand()

    data class StartAbatement(override val company: CompanyId, val facility: FacilityId, val kind: AbatementKind) : CarbonCommand()

    data class CancelAbatement(override val company: CompanyId, val project: AbatementId) : CarbonCommand()

    /** Sets what the package may do for the company at the compliance deadline. */
    data class SetPolicy(override val company: CompanyId, val country: CountryId, val autoOffsets: Boolean, val autoBorrow: Boolean) : CarbonCommand()
}

/** The result of a command that was accepted: the money to move and a sentence for the player. */
class CarbonEffect(
    val settlement: CarbonSettlement,
    val message: String,
    val trades: List<PermitTrade> = emptyList(),
) {
    companion object {
        fun of(message: String, settlement: CarbonSettlement = CarbonSettlement.EMPTY) = CarbonEffect(settlement, message)
    }
}

/** What a day of the package produced: events for the news and the intents for the ledger. */
class CarbonStepResult(val events: List<CarbonEvent>, val settlement: CarbonSettlement) {
    companion object {
        val EMPTY = CarbonStepResult(emptyList(), CarbonSettlement.EMPTY)
    }
}
