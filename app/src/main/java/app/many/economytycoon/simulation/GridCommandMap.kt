package app.many.economytycoon.simulation

import app.many.economytycoon.energygrid.CustomerClass
import app.many.economytycoon.energygrid.GridCommand
import app.many.economytycoon.energygrid.GridId
import app.many.economytycoon.energygrid.LoadId
import app.many.economytycoon.energygrid.PpaId
import app.many.economytycoon.energygrid.PpaProfile
import app.many.economytycoon.energygrid.Technology
import app.many.economytycoon.energygrid.UnitId

/**
 * Turns the commands of the game's command set that concern the electricity grids into the grid module's own commands.
 * Ids and enums travel in commands as plain numbers; an ordinal that names nothing gives null, and the executor refuses it.
 */
object GridCommandMap {
    fun isGridCommand(cmd: Command): Boolean = when (cmd) {
        is Command.ConnectFacilityToGrid, is Command.DisconnectFacilityFromGrid, is Command.ReclassifyGridLoad, is Command.SetGridLoadDraw,
        is Command.OrderGridUnit, is Command.RetireGridUnit, is Command.MothballGridUnit, is Command.RestartGridUnit,
        is Command.OfferPowerAgreement, is Command.AcceptPowerAgreement, is Command.DeclinePowerAgreement, is Command.TerminatePowerAgreement -> true
        else -> false
    }

    fun of(cmd: Command): GridCommand? = when (cmd) {
        is Command.ConnectFacilityToGrid -> CustomerClass.values().getOrNull(cmd.customer)?.let { GridCommand.ConnectFacility(cmd.company, GridId(cmd.grid), cmd.facility, it, cmd.baseKw) }
        is Command.DisconnectFacilityFromGrid -> GridCommand.DisconnectFacility(cmd.company, cmd.facility)
        is Command.ReclassifyGridLoad -> CustomerClass.values().getOrNull(cmd.customer)?.let { GridCommand.Reclassify(cmd.company, LoadId(cmd.load), it) }
        is Command.SetGridLoadDraw -> GridCommand.SetDraw(cmd.company, LoadId(cmd.load), cmd.baseKw)
        is Command.OrderGridUnit -> Technology.values().getOrNull(cmd.tech)?.let { GridCommand.OrderUnit(cmd.company, GridId(cmd.grid), it, cmd.capacityKw) }
        is Command.RetireGridUnit -> GridCommand.RetireUnit(cmd.company, UnitId(cmd.unit))
        is Command.MothballGridUnit -> GridCommand.Mothball(cmd.company, UnitId(cmd.unit))
        is Command.RestartGridUnit -> GridCommand.Restart(cmd.company, UnitId(cmd.unit))
        is Command.OfferPowerAgreement -> PpaProfile.values().getOrNull(cmd.profile)?.let {
            GridCommand.OfferPpa(cmd.company, GridId(cmd.grid), cmd.buyer, it, cmd.volumeKw, cmd.sharePpm, cmd.unit?.let { u -> UnitId(u) }, cmd.strike, cmd.startsAt, cmd.days, cmd.penaltyPpm)
        }
        is Command.AcceptPowerAgreement -> GridCommand.AcceptPpa(cmd.company, PpaId(cmd.ppa))
        is Command.DeclinePowerAgreement -> GridCommand.DeclinePpa(cmd.company, PpaId(cmd.ppa))
        is Command.TerminatePowerAgreement -> GridCommand.TerminatePpa(cmd.company, PpaId(cmd.ppa))
        else -> null
    }
}
