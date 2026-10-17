package app.many.economytycoon.tourism

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rejection
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.SettlementIntent

/**
 * What a company can ask of the tourism module. Each command names the company acting, so the executor can refuse a
 * request to touch someone else's property before the service sees it.
 */
sealed class TourismCommand {
    abstract val company: CompanyId

    data class BuildProperty(override val company: CompanyId, val destination: DestinationId, val kind: PropertyKind, val stars: Int, val rooms: List<Int>, val name: String) : TourismCommand()
    data class SetRates(override val company: CompanyId, val property: PropertyId, val policy: RatePolicy, val rack: Money) : TourismCommand()
    data class SetPricingBand(
        override val company: CompanyId, val property: PropertyId, val floorPpm: Long, val ceilingPpm: Long, val aggressionPpm: Long, val targetOccupancyPpm: Long,
    ) : TourismCommand()
    data class SetOverbooking(override val company: CompanyId, val property: PropertyId, val ppm: Long) : TourismCommand()
    data class SetCancellation(override val company: CompanyId, val property: PropertyId, val policy: CancellationPolicy) : TourismCommand()
    data class Listing(override val company: CompanyId, val property: PropertyId, val intermediary: IntermediaryId, val listed: Boolean) : TourismCommand()
    data class ExpandProperty(override val company: CompanyId, val property: PropertyId, val room: RoomClass, val count: Int) : TourismCommand()
    data class UpgradeProperty(override val company: CompanyId, val property: PropertyId, val stars: Int) : TourismCommand()
    data class RenovateProperty(override val company: CompanyId, val property: PropertyId) : TourismCommand()
    data class CloseProperty(override val company: CompanyId, val property: PropertyId) : TourismCommand()
    data class ReopenProperty(override val company: CompanyId, val property: PropertyId) : TourismCommand()
    data class BuildAttraction(
        override val company: CompanyId, val destination: DestinationId, val kind: AttractionKind, val name: String, val capacity: Int, val ticket: Money,
    ) : TourismCommand()
    data class SetTicketPrice(override val company: CompanyId, val attraction: AttractionId, val price: Money) : TourismCommand()
    data class SetAttractionCapacity(override val company: CompanyId, val attraction: AttractionId, val capacity: Int) : TourismCommand()
    data class SetAttractionOpen(override val company: CompanyId, val attraction: AttractionId, val open: Boolean) : TourismCommand()
    data class FoundOperator(override val company: CompanyId, val name: String, val marginPpm: Long) : TourismCommand()
    data class SetOperatorMargin(override val company: CompanyId, val operator: OperatorId, val marginPpm: Long) : TourismCommand()
    data class FoundIntermediary(
        override val company: CompanyId, val kind: IntermediaryKind, val name: String, val commissionPpm: Long, val reachPpm: Long, val listingFee: Money,
    ) : TourismCommand()
    data class SetCommission(override val company: CompanyId, val intermediary: IntermediaryId, val commissionPpm: Long) : TourismCommand()
    data class ProposeAllotment(
        override val company: CompanyId, val side: ContractSide, val operator: OperatorId, val property: PropertyId, val room: RoomClass, val roomsPerNight: Int,
        val netRate: Money, val startsAt: Long, val days: Int, val releaseDays: Int, val committedPpm: Long,
    ) : TourismCommand()
    data class AcceptAllotment(override val company: CompanyId, val allotment: AllotmentId) : TourismCommand()
    data class DeclineAllotment(override val company: CompanyId, val allotment: AllotmentId) : TourismCommand()
    data class TerminateAllotment(override val company: CompanyId, val allotment: AllotmentId) : TourismCommand()
    data class LaunchCampaign(override val company: CompanyId, val destination: DestinationId, val segment: VisitorSegment?, val spend: Money) : TourismCommand()
}

/** What came of a command: a plain sentence for the player, the ledger intents to post, and the rejection if any. */
data class TourismCommandResult(val message: String, val intents: List<SettlementIntent> = emptyList(), val rejection: Rejection? = null) {
    val ok: Boolean get() = rejection == null
}

/** Checks ownership and then hands the command to the service. It holds no state of its own. */
class TourismCommandExecutor(private val service: TourismService) {
    private val state get() = service.state

    fun execute(cmd: TourismCommand, tick: Tick): TourismCommandResult = when (cmd) {
        is TourismCommand.BuildProperty -> build(cmd, tick)
        is TourismCommand.SetRates -> owned(cmd.company, cmd.property) ?: done(service.setPricing(cmd.property, cmd.policy, cmd.rack)) { "Rates set." }
        is TourismCommand.SetPricingBand -> owned(cmd.company, cmd.property)
            ?: done(service.setPricingBand(cmd.property, cmd.floorPpm, cmd.ceilingPpm, cmd.aggressionPpm, cmd.targetOccupancyPpm)) { "Pricing band set." }
        is TourismCommand.SetOverbooking -> owned(cmd.company, cmd.property) ?: done(service.setOverbooking(cmd.property, cmd.ppm)) { "Overbooking set to ${cmd.ppm / 10_000L}%." }
        is TourismCommand.SetCancellation -> owned(cmd.company, cmd.property) ?: done(service.setCancellation(cmd.property, cmd.policy)) { "Cancellation terms are now ${cmd.policy.label.lowercase()}." }
        is TourismCommand.Listing -> owned(cmd.company, cmd.property) ?: done(
            if (cmd.listed) service.listWith(cmd.property, cmd.intermediary) else service.delist(cmd.property, cmd.intermediary),
        ) { if (cmd.listed) "Listed." else "Removed from the listing." }
        is TourismCommand.ExpandProperty -> owned(cmd.company, cmd.property) ?: spend(service.expandRooms(cmd.property, cmd.room, cmd.count, tick)) { "${cmd.count} rooms added." }
        is TourismCommand.UpgradeProperty -> owned(cmd.company, cmd.property) ?: spend(service.upgradeStars(cmd.property, cmd.stars, tick)) { "Upgraded to ${cmd.stars} stars." }
        is TourismCommand.RenovateProperty -> owned(cmd.company, cmd.property) ?: spend(service.renovate(cmd.property, tick)) { "Renovated." }
        is TourismCommand.CloseProperty -> owned(cmd.company, cmd.property) ?: done(service.closeProperty(cmd.property)) { "Closed." }
        is TourismCommand.ReopenProperty -> owned(cmd.company, cmd.property) ?: done(service.reopenProperty(cmd.property, tick)) { "Reopened." }
        is TourismCommand.BuildAttraction -> buildAttraction(cmd, tick)
        is TourismCommand.SetTicketPrice -> ownedAttraction(cmd.company, cmd.attraction) ?: done(service.setTicketPrice(cmd.attraction, cmd.price)) { "Ticket price set." }
        is TourismCommand.SetAttractionCapacity -> ownedAttraction(cmd.company, cmd.attraction) ?: done(service.setAttractionCapacity(cmd.attraction, cmd.capacity)) { "Capacity set to ${cmd.capacity}." }
        is TourismCommand.SetAttractionOpen -> ownedAttraction(cmd.company, cmd.attraction) ?: done(service.setAttractionOpen(cmd.attraction, cmd.open)) { if (cmd.open) "Opened." else "Closed." }
        is TourismCommand.FoundOperator -> done(service.addOperator(cmd.company, cmd.name, marginPpm = cmd.marginPpm)) { "Tour operator ${it.name} founded." }
        is TourismCommand.SetOperatorMargin -> ownedOperator(cmd.company, cmd.operator) ?: done(service.setOperatorMargin(cmd.operator, cmd.marginPpm)) { "Margin set." }
        is TourismCommand.FoundIntermediary -> done(service.addIntermediary(cmd.kind, cmd.company, cmd.name, cmd.commissionPpm, cmd.reachPpm, cmd.listingFee)) { "${it.name} opened." }
        is TourismCommand.SetCommission -> ownedIntermediary(cmd.company, cmd.intermediary) ?: done(service.setCommission(cmd.intermediary, cmd.commissionPpm)) { "Commission set." }
        is TourismCommand.ProposeAllotment -> done(
            service.proposeAllotment(
                cmd.side, cmd.company, cmd.operator, cmd.property, cmd.room, cmd.roomsPerNight, cmd.netRate, Tick(cmd.startsAt), cmd.days, cmd.releaseDays, cmd.committedPpm, tick,
            ),
        ) { if (it.status == AllotmentStatus.PROPOSED) "Allotment ${it.id.value} offered." else "Allotment ${it.id.value} is ${it.status.label.lowercase()}." }
        is TourismCommand.AcceptAllotment -> done(service.acceptAllotment(cmd.allotment, cmd.company, tick)) { "Allotment ${it.id.value} accepted." }
        is TourismCommand.DeclineAllotment -> done(service.declineAllotment(cmd.allotment, cmd.company)) { "Allotment ${it.id.value} closed." }
        is TourismCommand.TerminateAllotment -> when (val r = service.terminateAllotment(cmd.allotment, cmd.company, tick)) {
            is Outcome.Ok -> TourismCommandResult("Allotment ended early.", listOfNotNull(r.value.payment))
            is Outcome.Rejected -> fail(r.rejection)
        }
        is TourismCommand.LaunchCampaign -> spend(service.launchCampaign(cmd.destination, cmd.company, cmd.segment, cmd.spend, tick)) { "Campaign launched." }
    }

    /** What the command will cost, if it costs anything, so the caller can check funds before executing it. */
    fun cost(cmd: TourismCommand): Money = when (cmd) {
        is TourismCommand.BuildProperty -> AccommodationRules.buildCost(cmd.kind, cmd.stars, cmd.rooms.sum())
        is TourismCommand.ExpandProperty -> state.property(cmd.property)?.let {
            Money(mulDiv(AccommodationRules.buildCost(it.kind, it.stars, cmd.count).raw, service.params.expansionCostPpm, 1_000_000L))
        } ?: Money.ZERO
        is TourismCommand.UpgradeProperty -> state.property(cmd.property)?.let { AccommodationRules.upgradeCost(it.kind, it.stars, cmd.stars, it.totalRooms) } ?: Money.ZERO
        is TourismCommand.RenovateProperty -> state.property(cmd.property)?.let {
            Money(mulDiv(mulDiv(AccommodationRules.buildCost(it.kind, it.stars, it.totalRooms).raw, 1_000_000L - it.conditionPpm, 1_000_000L), service.params.renovationShare, 1_000_000L))
        } ?: Money.ZERO
        is TourismCommand.BuildAttraction -> Money(mulDiv(cmd.kind.buildCost.raw, cmd.capacity.toLong(), cmd.kind.referenceCapacity.toLong()))
        is TourismCommand.LaunchCampaign -> cmd.spend
        else -> Money.ZERO
    }

    private fun build(cmd: TourismCommand.BuildProperty, tick: Tick): TourismCommandResult {
        if (cmd.rooms.size != RoomClass.COUNT) return fail(Rejection(Rules.INVALID_QUANTITY, "give a room count for each class"))
        return when (val r = service.addProperty(cmd.destination, cmd.company, null, cmd.kind, cmd.stars, cmd.rooms.toIntArray(), tick, cmd.name)) {
            is Outcome.Ok -> TourismCommandResult("${cmd.name} is being built, ready on day ${r.value.property.opensAt}.", listOfNotNull(r.value.payment))
            is Outcome.Rejected -> fail(r.rejection)
        }
    }

    private fun buildAttraction(cmd: TourismCommand.BuildAttraction, tick: Tick): TourismCommandResult =
        when (val r = service.addAttraction(cmd.destination, cmd.company, null, cmd.kind, cmd.name, tick, cmd.capacity, cmd.ticket)) {
            is Outcome.Ok -> TourismCommandResult("${cmd.name} is being built, ready on day ${r.value.attraction.opensAt}.", listOfNotNull(r.value.payment))
            is Outcome.Rejected -> fail(r.rejection)
        }

    private fun <T> done(outcome: Outcome<T>, message: (T) -> String): TourismCommandResult = when (outcome) {
        is Outcome.Ok -> TourismCommandResult(message(outcome.value))
        is Outcome.Rejected -> fail(outcome.rejection)
    }

    private fun spend(outcome: Outcome<Capex>, message: (Capex) -> String): TourismCommandResult = when (outcome) {
        is Outcome.Ok -> TourismCommandResult(message(outcome.value), listOfNotNull(outcome.value.payment))
        is Outcome.Rejected -> fail(outcome.rejection)
    }

    private fun fail(r: Rejection) = TourismCommandResult(r.message, rejection = r)

    private fun owned(company: CompanyId, property: PropertyId): TourismCommandResult? {
        val p = state.property(property) ?: return fail(Rejection(Rules.UNKNOWN_ENTITY, "unknown property"))
        return if (p.owner == company) null else fail(Rejection(Rules.NOT_OWNER, "that property belongs to someone else"))
    }

    private fun ownedAttraction(company: CompanyId, id: AttractionId): TourismCommandResult? {
        val a = state.attraction(id) ?: return fail(Rejection(Rules.UNKNOWN_ENTITY, "unknown attraction"))
        return if (a.owner == company) null else fail(Rejection(Rules.NOT_OWNER, "that attraction belongs to someone else"))
    }

    private fun ownedOperator(company: CompanyId, id: OperatorId): TourismCommandResult? {
        val o = state.operator(id) ?: return fail(Rejection(Rules.UNKNOWN_ENTITY, "unknown operator"))
        return if (o.owner == company) null else fail(Rejection(Rules.NOT_OWNER, "that operator belongs to someone else"))
    }

    private fun ownedIntermediary(company: CompanyId, id: IntermediaryId): TourismCommandResult? {
        val i = state.intermediary(id) ?: return fail(Rejection(Rules.UNKNOWN_ENTITY, "unknown intermediary"))
        return if (i.owner == company) null else fail(Rejection(Rules.NOT_OWNER, "that business belongs to someone else"))
    }

    companion object {
        /** One line for the activity log. */
        fun describe(cmd: TourismCommand): String = when (cmd) {
            is TourismCommand.BuildProperty -> "Build ${cmd.stars}-star ${cmd.kind.label.lowercase()} ${cmd.name} with ${cmd.rooms.sum()} rooms"
            is TourismCommand.SetRates -> "Set rack rate of property ${cmd.property.value} to ${cmd.rack.wholeUnits()} (${cmd.policy.label.lowercase()})"
            is TourismCommand.SetPricingBand -> "Set the pricing band of property ${cmd.property.value}"
            is TourismCommand.SetOverbooking -> "Set overbooking of property ${cmd.property.value} to ${cmd.ppm / 10_000L}%"
            is TourismCommand.SetCancellation -> "Set cancellation terms of property ${cmd.property.value} to ${cmd.policy.label.lowercase()}"
            is TourismCommand.Listing -> (if (cmd.listed) "List" else "Delist") + " property ${cmd.property.value} with intermediary ${cmd.intermediary.value}"
            is TourismCommand.ExpandProperty -> "Add ${cmd.count} ${cmd.room.label.lowercase()} rooms to property ${cmd.property.value}"
            is TourismCommand.UpgradeProperty -> "Upgrade property ${cmd.property.value} to ${cmd.stars} stars"
            is TourismCommand.RenovateProperty -> "Renovate property ${cmd.property.value}"
            is TourismCommand.CloseProperty -> "Close property ${cmd.property.value}"
            is TourismCommand.ReopenProperty -> "Reopen property ${cmd.property.value}"
            is TourismCommand.BuildAttraction -> "Build ${cmd.kind.label.lowercase()} ${cmd.name}"
            is TourismCommand.SetTicketPrice -> "Set ticket price of attraction ${cmd.attraction.value} to ${cmd.price.wholeUnits()}"
            is TourismCommand.SetAttractionCapacity -> "Set capacity of attraction ${cmd.attraction.value} to ${cmd.capacity}"
            is TourismCommand.SetAttractionOpen -> (if (cmd.open) "Open" else "Close") + " attraction ${cmd.attraction.value}"
            is TourismCommand.FoundOperator -> "Found tour operator ${cmd.name}"
            is TourismCommand.SetOperatorMargin -> "Set margin of operator ${cmd.operator.value} to ${cmd.marginPpm / 10_000L}%"
            is TourismCommand.FoundIntermediary -> "Open ${cmd.kind.label.lowercase()} ${cmd.name} at ${cmd.commissionPpm / 10_000L}% commission"
            is TourismCommand.SetCommission -> "Set commission of intermediary ${cmd.intermediary.value} to ${cmd.commissionPpm / 10_000L}%"
            is TourismCommand.ProposeAllotment -> "Offer allotment of ${cmd.roomsPerNight} ${cmd.room.label.lowercase()} rooms for ${cmd.days} days at ${cmd.netRate.wholeUnits()}"
            is TourismCommand.AcceptAllotment -> "Accept allotment ${cmd.allotment.value}"
            is TourismCommand.DeclineAllotment -> "Decline allotment ${cmd.allotment.value}"
            is TourismCommand.TerminateAllotment -> "End allotment ${cmd.allotment.value} early"
            is TourismCommand.LaunchCampaign -> "Spend ${cmd.spend.wholeUnits()} on a campaign in destination ${cmd.destination.value}"
        }
    }
}
