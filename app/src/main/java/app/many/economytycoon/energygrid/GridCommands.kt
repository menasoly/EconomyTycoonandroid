package app.many.economytycoon.energygrid

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rejection
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.SettlementIntent

/**
 * What a company can ask of the grids. Each command names the company acting, so the executor can refuse a request
 * to touch someone else's plant or connection before the service sees it.
 */
sealed class GridCommand {
    abstract val company: CompanyId

    data class ConnectFacility(override val company: CompanyId, val grid: GridId, val facility: FacilityId, val customer: CustomerClass, val baseKw: Long) : GridCommand()
    data class DisconnectFacility(override val company: CompanyId, val facility: FacilityId) : GridCommand()
    data class Reclassify(override val company: CompanyId, val load: LoadId, val customer: CustomerClass) : GridCommand()
    data class SetDraw(override val company: CompanyId, val load: LoadId, val baseKw: Long) : GridCommand()
    data class OrderUnit(override val company: CompanyId, val grid: GridId, val tech: Technology, val capacityKw: Long) : GridCommand()
    data class RetireUnit(override val company: CompanyId, val unit: UnitId) : GridCommand()
    data class Mothball(override val company: CompanyId, val unit: UnitId) : GridCommand()
    data class Restart(override val company: CompanyId, val unit: UnitId) : GridCommand()
    data class OfferPpa(
        override val company: CompanyId, val grid: GridId, val buyer: CompanyId, val profile: PpaProfile, val volumeKw: Long, val sharePpm: Long,
        val unit: UnitId?, val strike: Money, val startsAt: Long, val days: Int, val penaltyPpm: Long,
    ) : GridCommand()
    data class AcceptPpa(override val company: CompanyId, val ppa: PpaId) : GridCommand()
    data class DeclinePpa(override val company: CompanyId, val ppa: PpaId) : GridCommand()
    data class TerminatePpa(override val company: CompanyId, val ppa: PpaId) : GridCommand()
}

/** What came of a command: a plain sentence for the player, the ledger intents to post, and the rejection if any. */
data class GridCommandResult(val message: String, val intents: List<SettlementIntent> = emptyList(), val rejection: Rejection? = null) {
    val ok: Boolean get() = rejection == null
}

/** Checks ownership and then hands the command to the service. It holds no state of its own. */
class GridCommandExecutor(private val service: EnergyGridService) {
    private val state get() = service.state

    fun execute(cmd: GridCommand, tick: Tick): GridCommandResult = when (cmd) {
        is GridCommand.ConnectFacility -> done(service.connectFacility(cmd.grid, cmd.customer, cmd.company, cmd.facility, cmd.baseKw)) { "Facility connected at ${it.baseKw / 1_000L} MW." }
        is GridCommand.DisconnectFacility -> ownedLoadOfFacility(cmd.company, cmd.facility)?.let { fail(it) }
            ?: done(service.disconnectFacility(cmd.facility)) { "Facility disconnected." }
        is GridCommand.Reclassify -> ownedLoad(cmd.company, cmd.load)?.let { fail(it) }
            ?: done(service.reclassify(cmd.load, cmd.customer)) { "Now billed as ${it.customer.label.lowercase()}." }
        is GridCommand.SetDraw -> ownedLoad(cmd.company, cmd.load)?.let { fail(it) }
            ?: done(service.setLoadKw(cmd.load, cmd.baseKw)) { "Expected draw set to ${it.baseKw / 1_000L} MW." }
        is GridCommand.OrderUnit -> orderUnit(cmd, tick)
        is GridCommand.RetireUnit -> ownedUnit(cmd.company, cmd.unit)?.let { fail(it) } ?: done(service.retireUnit(cmd.unit)) { "Unit retired." }
        is GridCommand.Mothball -> ownedUnit(cmd.company, cmd.unit)?.let { fail(it) } ?: done(service.mothball(cmd.unit)) { "Unit mothballed." }
        is GridCommand.Restart -> ownedUnit(cmd.company, cmd.unit)?.let { fail(it) } ?: done(service.restart(cmd.unit, tick)) { "Unit restarting." }
        is GridCommand.OfferPpa -> done(
            service.offerPpa(cmd.grid, cmd.company, cmd.buyer, cmd.profile, cmd.volumeKw, cmd.sharePpm, cmd.unit, cmd.strike, Tick(cmd.startsAt), cmd.days, cmd.penaltyPpm, tick),
        ) { "Agreement ${it.id.value} offered." }
        is GridCommand.AcceptPpa -> done(service.acceptPpa(cmd.ppa, cmd.company, tick)) { "Agreement ${it.id.value} accepted." }
        is GridCommand.DeclinePpa -> done(service.declinePpa(cmd.ppa, cmd.company, tick)) { "Agreement ${it.id.value} closed." }
        is GridCommand.TerminatePpa -> when (val r = service.terminatePpa(cmd.ppa, cmd.company, tick)) {
            is Outcome.Ok -> GridCommandResult("Agreement ended early.", listOfNotNull(r.value))
            is Outcome.Rejected -> fail(r.rejection)
        }
    }

    private fun orderUnit(cmd: GridCommand.OrderUnit, tick: Tick): GridCommandResult {
        if (cmd.tech !in (state.grid(cmd.grid)?.params?.buildable() ?: emptySet())) {
            return fail(Rejection(Rules.NOT_UNLOCKED, "${cmd.tech.label} cannot be built on this grid"))
        }
        return when (val r = service.orderUnit(cmd.grid, cmd.tech, cmd.capacityKw, cmd.company, tick)) {
            is Outcome.Ok -> GridCommandResult("${cmd.tech.label} unit ordered, ready on day ${r.value.unit.statusUntil}.", listOf(r.value.payment))
            is Outcome.Rejected -> fail(r.rejection)
        }
    }

    private fun <T> done(outcome: Outcome<T>, message: (T) -> String): GridCommandResult = when (outcome) {
        is Outcome.Ok -> GridCommandResult(message(outcome.value))
        is Outcome.Rejected -> fail(outcome.rejection)
    }

    private fun fail(r: Rejection) = GridCommandResult(r.message, rejection = r)

    private fun ownedUnit(company: CompanyId, unit: UnitId): Rejection? {
        val u = state.unit(unit) ?: return Rejection(Rules.UNKNOWN_ENTITY, "unknown unit")
        return if (u.owner == company) null else Rejection(Rules.NOT_OWNER, "that unit belongs to someone else")
    }

    private fun ownedLoad(company: CompanyId, load: LoadId): Rejection? {
        val l = state.grids.firstNotNullOfOrNull { it.load(load) } ?: return Rejection(Rules.UNKNOWN_ENTITY, "unknown connection")
        return if (l.company == company) null else Rejection(Rules.NOT_OWNER, "that connection belongs to someone else")
    }

    private fun ownedLoadOfFacility(company: CompanyId, facility: FacilityId): Rejection? {
        val l = service.loadOfFacility(facility) ?: return Rejection(Rules.UNKNOWN_ENTITY, "that facility is not connected")
        return if (l.company == company) null else Rejection(Rules.NOT_OWNER, "that connection belongs to someone else")
    }

    companion object {
        /** One line for the activity log. */
        fun describe(cmd: GridCommand): String = when (cmd) {
            is GridCommand.ConnectFacility -> "Connect facility ${cmd.facility.value} to grid ${cmd.grid.value} as ${cmd.customer.label.lowercase()}"
            is GridCommand.DisconnectFacility -> "Disconnect facility ${cmd.facility.value} from the grid"
            is GridCommand.Reclassify -> "Bill connection ${cmd.load.value} as ${cmd.customer.label.lowercase()}"
            is GridCommand.SetDraw -> "Set the draw of connection ${cmd.load.value} to ${cmd.baseKw / 1_000L} MW"
            is GridCommand.OrderUnit -> "Order a ${cmd.capacityKw / 1_000L} MW ${cmd.tech.label.lowercase()} unit on grid ${cmd.grid.value}"
            is GridCommand.RetireUnit -> "Retire unit ${cmd.unit.value}"
            is GridCommand.Mothball -> "Mothball unit ${cmd.unit.value}"
            is GridCommand.Restart -> "Restart unit ${cmd.unit.value}"
            is GridCommand.OfferPpa -> "Offer company ${cmd.buyer.value} a ${cmd.days} day power agreement at ${cmd.strike.wholeUnits()} per MWh"
            is GridCommand.AcceptPpa -> "Accept power agreement ${cmd.ppa.value}"
            is GridCommand.DeclinePpa -> "Decline power agreement ${cmd.ppa.value}"
            is GridCommand.TerminatePpa -> "End power agreement ${cmd.ppa.value} early"
        }
    }
}
