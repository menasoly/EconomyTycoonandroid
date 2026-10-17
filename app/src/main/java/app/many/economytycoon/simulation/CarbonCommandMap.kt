package app.many.economytycoon.simulation

import app.many.economytycoon.carbon.CarbonCommand

/** Turns the game's carbon commands into the carbon package's own commands and back; the field names match one to one. */
object CarbonCommandMap {
    fun isCarbonCommand(cmd: Command): Boolean = of(cmd) != null

    fun of(cmd: Command): CarbonCommand? = when (cmd) {
        is Command.BidCarbonAuction -> CarbonCommand.BidAtAuction(cmd.company, cmd.country, cmd.quantity, cmd.limit)
        is Command.CancelCarbonBid -> CarbonCommand.CancelBid(cmd.company, cmd.country, cmd.bid)
        is Command.PlacePermitOrder -> CarbonCommand.PlaceOrder(cmd.company, cmd.country, cmd.side, cmd.quantity, cmd.limit)
        is Command.CancelPermitOrder -> CarbonCommand.CancelOrder(cmd.company, cmd.country, cmd.order)
        is Command.SurrenderPermits -> CarbonCommand.Surrender(cmd.company, cmd.country, cmd.quantity)
        is Command.BuyCarbonOffsets -> CarbonCommand.BuyOffsets(cmd.company, cmd.kind, cmd.quantity)
        is Command.RetireCarbonOffsets -> CarbonCommand.RetireOffsets(cmd.company, cmd.country, cmd.kind, cmd.quantity, cmd.forCompliance)
        is Command.FundOffsetProject -> CarbonCommand.FundOffsetProject(cmd.company, cmd.kind, cmd.host, cmd.annual)
        is Command.StartAbatement -> CarbonCommand.StartAbatement(cmd.company, cmd.facility, cmd.kind)
        is Command.CancelAbatement -> CarbonCommand.CancelAbatement(cmd.company, cmd.project)
        is Command.SetCarbonPolicy -> CarbonCommand.SetPolicy(cmd.company, cmd.country, cmd.autoOffsets, cmd.autoBorrow)
        else -> null
    }

    fun toCommand(c: CarbonCommand): Command = when (c) {
        is CarbonCommand.BidAtAuction -> Command.BidCarbonAuction(c.company, c.country, c.quantity, c.limit)
        is CarbonCommand.CancelBid -> Command.CancelCarbonBid(c.company, c.country, c.bid)
        is CarbonCommand.PlaceOrder -> Command.PlacePermitOrder(c.company, c.country, c.side, c.quantity, c.limit)
        is CarbonCommand.CancelOrder -> Command.CancelPermitOrder(c.company, c.country, c.order)
        is CarbonCommand.Surrender -> Command.SurrenderPermits(c.company, c.country, c.quantity)
        is CarbonCommand.BuyOffsets -> Command.BuyCarbonOffsets(c.company, c.kind, c.quantity)
        is CarbonCommand.RetireOffsets -> Command.RetireCarbonOffsets(c.company, c.country, c.kind, c.quantity, c.forCompliance)
        is CarbonCommand.FundOffsetProject -> Command.FundOffsetProject(c.company, c.kind, c.host, c.annual)
        is CarbonCommand.StartAbatement -> Command.StartAbatement(c.company, c.facility, c.kind)
        is CarbonCommand.CancelAbatement -> Command.CancelAbatement(c.company, c.project)
        is CarbonCommand.SetPolicy -> Command.SetCarbonPolicy(c.company, c.country, c.autoOffsets, c.autoBorrow)
    }
}
