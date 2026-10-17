package app.many.economytycoon.activity

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.markets.Side
import app.many.economytycoon.simulation.Command

/** How names and amounts are written in a description. */
class DescribeNames(
    val company: (CompanyId) -> String,
    val facility: (FacilityId) -> String,
    val good: (GoodId) -> String,
    val money: (Money) -> String,
    val quantity: (Quantity) -> String,
    val city: (CityId) -> String,
    val region: (RegionId) -> String,
) {
    companion object {
        /** Plain figures and numbers, for places that cannot look names up. */
        val PLAIN = DescribeNames(
            company = { "company ${it.value}" }, facility = { "facility ${it.value}" }, good = { it.key.replace('_', ' ') },
            money = { it.toString() }, quantity = { it.toString() }, city = { "city ${it.value}" }, region = { "region ${it.value}" },
        )
    }
}

/** The part of the game a command belongs to. */
enum class ActivityArea(val label: String) {
    COMPANY("Company"),
    LAND("Land"),
    FACILITIES("Facilities"),
    PEOPLE("People"),
    TRADE("Trade"),
    FINANCE("Finance"),
    RESEARCH("Research"),
    LOGISTICS("Logistics"),
    CONTRACTS("Contracts"),
    EXCHANGE("Exchange"),
    PUBLIC("Government"),
    PROPERTY("Property"),
    CORPORATE("Takeovers"),
    MARKETING("Marketing"),
}

/** Writes any command as a sentence a player would recognise, and says which part of the game it belongs to. */
object CommandDescriber {
    private fun word(e: Enum<*>): String = e.name.lowercase().replace('_', ' ')
    private fun pct(ppm: Long): String = if (ppm % 10_000L == 0L) "${ppm / 10_000L}%" else "%.1f%%".format(ppm / 10_000.0)

    fun describe(cmd: Command, n: DescribeNames = DescribeNames.PLAIN): String = when (cmd) {
        is Command.FoundCompany -> "Founded ${cmd.name} with ${n.money(cmd.capital)}"
        is Command.Rename -> "Renamed ${n.company(cmd.company)} to ${cmd.name}"
        is Command.SetHeadquarters -> "Moved the headquarters to ${n.city(cmd.city)}"
        is Command.Dissolve -> "Dissolved ${n.company(cmd.company)}"
        is Command.SurveyDeposit -> "Surveyed deposit ${cmd.deposit.value}"
        is Command.ClaimDeposit -> "Claimed deposit ${cmd.deposit.value}"
        is Command.BuyParcel -> "Bought parcel ${cmd.parcel.value}"
        is Command.Build -> "Started building ${cmd.type.key.replace('_', ' ')} in ${n.region(cmd.region)}" + if (cmd.name.isNotBlank()) " called ${cmd.name}" else ""
        is Command.CancelConstruction -> "Cancelled construction project ${cmd.project.value}"
        is Command.DeliverMaterials -> "Delivered ${n.quantity(cmd.quantity)} of ${n.good(cmd.good)} to construction project ${cmd.project.value}"
        is Command.ExpandStorage -> "Expanded storage at ${n.facility(cmd.facility)}"
        is Command.AddLine -> "Added a production line to ${n.facility(cmd.facility)}"
        is Command.SetRecipe -> if (cmd.recipe == null) "Cleared line ${cmd.line.value} of ${n.facility(cmd.facility)}" else "Set line ${cmd.line.value} of ${n.facility(cmd.facility)} to ${cmd.recipe.key.replace('_', ' ')}"
        is Command.SetLineEnabled -> "${if (cmd.enabled) "Switched on" else "Switched off"} line ${cmd.line.value} of ${n.facility(cmd.facility)}"
        is Command.SetLinePriority -> "Gave line ${cmd.line.value} of ${n.facility(cmd.facility)} priority ${cmd.priority}"
        is Command.SetSchedulePolicy -> "Set ${n.facility(cmd.facility)} to schedule by ${word(cmd.policy)}"
        is Command.SetQuality -> "Set quality assurance at ${n.facility(cmd.facility)} to ${pct(cmd.levelPpm)}"
        is Command.InstallMachine -> "Installed ${cmd.type.key.replace('_', ' ')} on line ${cmd.line.value} of ${n.facility(cmd.facility)}"
        is Command.RemoveMachine -> "Removed ${cmd.type.key.replace('_', ' ')} from line ${cmd.line.value} of ${n.facility(cmd.facility)}"
        is Command.Maintain -> "Paid ${n.money(cmd.budget)} for maintenance at ${n.facility(cmd.facility)}"
        is Command.DisposeWaste -> "Disposed of waste at ${n.facility(cmd.facility)}"
        is Command.Shutdown -> "Shut down ${n.facility(cmd.facility)}"
        is Command.Reopen -> "Reopened ${n.facility(cmd.facility)}"
        is Command.Demolish -> "Demolished ${n.facility(cmd.facility)}"
        is Command.SetStaffing -> "Wanted ${cmd.target} ${cmd.role.key.replace('_', ' ')} at ${n.facility(cmd.facility)}" + (cmd.wage?.let { " paying ${n.money(it)}" } ?: "")
        is Command.SetBenefits -> "Set benefits to ${pct(cmd.ppm)} of wages"
        is Command.SetTraining -> "Set training for ${cmd.role.key.replace('_', ' ')} at ${n.facility(cmd.facility)} to ${pct(cmd.ppm)}"
        is Command.PlaceOrder -> "${if (cmd.side == Side.BUY) "Bid for" else "Offered"} ${n.quantity(cmd.quantity)} of ${n.good(cmd.good)}" + (cmd.limit?.let { " at ${n.money(it.perUnit)}" } ?: " at market") + " for ${n.facility(cmd.facility)}"
        is Command.CancelOrder -> "Cancelled order ${cmd.order.value}"
        is Command.SetSellRule -> "Set a rule to sell ${n.good(cmd.rule.good)} from ${n.facility(cmd.rule.facility)}"
        is Command.RemoveSellRule -> "Removed the rule selling ${n.good(cmd.good)} from ${n.facility(cmd.facility)}"
        is Command.SetBuyRule -> "Set a rule to buy ${n.good(cmd.rule.good)} for ${n.facility(cmd.rule.facility)}"
        is Command.RemoveBuyRule -> "Removed the rule buying ${n.good(cmd.good)} for ${n.facility(cmd.facility)}"
        is Command.SetDividend -> "Set the dividend policy to pay out ${pct(cmd.policy.payoutPpm)} of profit"
        is Command.Borrow -> "Borrowed ${n.money(cmd.amount)} for ${cmd.termTicks} days"
        is Command.Repay -> "Repaid ${n.money(cmd.amount)}" + (cmd.loan?.let { " on loan ${it.value}" } ?: "")
        is Command.SetBudget -> "Set the ${word(cmd.department)} budget to ${n.money(cmd.perTick)} a day"
        is Command.StartResearch -> "Started researching ${cmd.tech.key.replace('_', ' ')}"
        is Command.QueueResearch -> "Queued research into ${cmd.tech.key.replace('_', ' ')}"
        is Command.CancelResearch -> "Cancelled research into ${cmd.tech.key.replace('_', ' ')}"
        is Command.LicenseTech -> "Licensed ${cmd.tech.key.replace('_', ' ')} from ${n.company(cmd.from)}"
        is Command.Ship -> "Shipped ${n.quantity(cmd.quantity)} of ${n.good(cmd.good)}" + if (cmd.useFleet) " with the company fleet" else ""
        is Command.MoveLocal -> "Moved ${n.quantity(cmd.quantity)} of ${n.good(cmd.good)} between stores"
        is Command.BuyVehicle -> "Bought a ${cmd.type.key.replace('_', ' ')} in ${n.city(cmd.city)}"
        is Command.SellVehicle -> "Sold vehicle ${cmd.vehicle.value}"
        is Command.RepairVehicle -> "Repaired vehicle ${cmd.vehicle.value}"
        is Command.LeaseWarehouse -> "Leased a warehouse for ${n.quantity(cmd.capacity)} in ${n.city(cmd.city)}"
        is Command.CloseWarehouse -> "Closed warehouse ${cmd.warehouse.value}"
        is Command.ProposeContract -> "Proposed to ${if (cmd.side == app.many.economytycoon.contracts.ContractSide.BUYER) "buy" else "sell"} ${n.quantity(cmd.quantity)} of ${n.good(cmd.good)} every ${cmd.intervalTicks} days with ${n.company(cmd.counterparty)}"
        is Command.AcceptContract -> "Accepted contract ${cmd.contract.value}"
        is Command.DeclineContract -> "Declined contract ${cmd.contract.value}"
        is Command.CancelContract -> "Cancelled contract ${cmd.contract.value}"
        is Command.ListCompany -> "Listed the company on the exchange, floating ${pct(cmd.floatPpm)}"
        is Command.DelistCompany -> "Delisted the company"
        is Command.BuyShares -> "Bought ${cmd.shares} shares of ${n.company(cmd.issuer)}"
        is Command.SellShares -> "Sold ${cmd.shares} shares of ${n.company(cmd.issuer)}"
        is Command.BuybackShares -> "Bought back ${cmd.shares} of the company's own shares"
        is Command.BidTender -> "Bid ${n.money(cmd.unitPrice)} a unit on tender ${cmd.tender.value}"
        is Command.WithdrawBid -> "Withdrew the bid on tender ${cmd.tender.value}"
        is Command.ApplyGrant -> "Applied for grant programme ${cmd.program.value}"
        is Command.WithdrawGrant -> "Withdrew from grant programme ${cmd.program.value}"
        is Command.CreateCampaign -> "Booked a ${cmd.channel.label.lowercase()} campaign for ${n.good(cmd.good)} at ${n.money(cmd.dailyBudget)} a day for ${cmd.days} days"
        is Command.PauseCampaign -> "Paused campaign ${cmd.campaign.value}"
        is Command.ResumeCampaign -> "Resumed campaign ${cmd.campaign.value}"
        is Command.ExtendCampaign -> "Extended campaign ${cmd.campaign.value} by ${cmd.extraDays} days"
        is Command.SetCampaignBudget -> "Set the budget of campaign ${cmd.campaign.value} to ${n.money(cmd.dailyBudget)} a day"
        is Command.RefreshCreative -> "Commissioned a new creative for campaign ${cmd.campaign.value}"
        is Command.CancelCampaign -> "Cancelled campaign ${cmd.campaign.value}"
        is Command.StartAbTest -> "Started a creative test on campaign ${cmd.campaign.value}"
        is Command.LaunchPromotion -> "Launched a ${pct(cmd.discountPpm)} price promotion on ${n.good(cmd.good)} for ${cmd.days} days"
        is Command.CancelPromotion -> "Cancelled promotion ${cmd.promotion.value}"
        is Command.OpenFuture -> "Opened a ${word(cmd.side)} position of ${cmd.contracts} futures in ${n.good(cmd.good)} for ${cmd.tenor} days"
        is Command.CloseFuture -> "Closed futures position ${cmd.position}"
        is Command.AddMargin -> "Added ${n.money(cmd.amount)} margin to futures position ${cmd.position}"
        is Command.ListParcel -> "Put parcel ${cmd.parcel.value} up for sale at ${n.money(cmd.ask)}"
        is Command.DelistParcel -> "Took parcel ${cmd.parcel.value} off the market"
        is Command.BuyListed -> "Bought listed parcel ${cmd.parcel.value}"
        is Command.OfferLease -> "Offered parcel ${cmd.parcel.value} for lease at ${n.money(cmd.monthlyRent)} a month"
        is Command.WithdrawLease -> "Withdrew the lease offer on parcel ${cmd.parcel.value}"
        is Command.EndLease -> "Ended the lease on parcel ${cmd.parcel.value}"
        is Command.RespondDemand -> when (cmd.response) {
            app.many.economytycoon.unions.ResponseKind.ACCEPT -> "Accepted union demand ${cmd.demand}"
            app.many.economytycoon.unions.ResponseKind.REFUSE -> "Refused union demand ${cmd.demand}"
            app.many.economytycoon.unions.ResponseKind.COUNTER -> "Countered union demand ${cmd.demand} at ${pct(cmd.counterPpm)}"
        }
        is Command.SubscribeBond -> "Subscribed to ${cmd.units} units of bond ${cmd.bond.value}"
        is Command.TradeBond -> "${if (cmd.buy) "Bought" else "Sold"} ${cmd.units} units of bond ${cmd.bond.value}"
        is Command.LobbyLaw -> "Spent ${n.money(cmd.amount)} lobbying ${if (cmd.support) "for" else "against"} law ${cmd.law.value}"
        is Command.BuyPolicy -> "Insured ${n.facility(cmd.facility)} with ${word(cmd.level)} cover"
        is Command.CancelPolicy -> "Cancelled insurance policy ${cmd.policy.value}"
        is Command.MakeOffer -> "Offered ${n.money(cmd.pricePerShare)} a share for ${cmd.shares} shares of ${n.company(cmd.target)}"
        is Command.RespondOffer -> "${if (cmd.accept) "Accepted" else "Rejected"} takeover offer ${cmd.offer}"
        is Command.WithdrawOffer -> "Withdrew takeover offer ${cmd.offer}"
        is Command.FileInsolvency -> "Filed for insolvency"
    }

    fun area(cmd: Command): ActivityArea = when (cmd) {
        is Command.FoundCompany, is Command.Rename, is Command.SetHeadquarters, is Command.Dissolve -> ActivityArea.COMPANY
        is Command.SurveyDeposit, is Command.ClaimDeposit, is Command.BuyParcel -> ActivityArea.LAND
        is Command.Build, is Command.CancelConstruction, is Command.DeliverMaterials, is Command.ExpandStorage, is Command.AddLine,
        is Command.SetRecipe, is Command.SetLineEnabled, is Command.SetLinePriority, is Command.SetSchedulePolicy, is Command.SetQuality,
        is Command.InstallMachine, is Command.RemoveMachine, is Command.Maintain, is Command.DisposeWaste, is Command.Shutdown,
        is Command.Reopen, is Command.Demolish, is Command.BuyPolicy, is Command.CancelPolicy -> ActivityArea.FACILITIES
        is Command.SetStaffing, is Command.SetBenefits, is Command.SetTraining, is Command.RespondDemand -> ActivityArea.PEOPLE
        is Command.PlaceOrder, is Command.CancelOrder, is Command.SetSellRule, is Command.RemoveSellRule, is Command.SetBuyRule,
        is Command.RemoveBuyRule, is Command.OpenFuture, is Command.CloseFuture, is Command.AddMargin -> ActivityArea.TRADE
        is Command.SetDividend, is Command.Borrow, is Command.Repay, is Command.SetBudget, is Command.SubscribeBond, is Command.TradeBond -> ActivityArea.FINANCE
        is Command.StartResearch, is Command.QueueResearch, is Command.CancelResearch, is Command.LicenseTech -> ActivityArea.RESEARCH
        is Command.Ship, is Command.MoveLocal, is Command.BuyVehicle, is Command.SellVehicle, is Command.RepairVehicle,
        is Command.LeaseWarehouse, is Command.CloseWarehouse -> ActivityArea.LOGISTICS
        is Command.ProposeContract, is Command.AcceptContract, is Command.DeclineContract, is Command.CancelContract -> ActivityArea.CONTRACTS
        is Command.ListCompany, is Command.DelistCompany, is Command.BuyShares, is Command.SellShares, is Command.BuybackShares -> ActivityArea.EXCHANGE
        is Command.BidTender, is Command.WithdrawBid, is Command.ApplyGrant, is Command.WithdrawGrant, is Command.LobbyLaw -> ActivityArea.PUBLIC
        is Command.ListParcel, is Command.DelistParcel, is Command.BuyListed, is Command.OfferLease, is Command.WithdrawLease, is Command.EndLease -> ActivityArea.PROPERTY
        is Command.MakeOffer, is Command.RespondOffer, is Command.WithdrawOffer, is Command.FileInsolvency -> ActivityArea.CORPORATE
        is Command.CreateCampaign, is Command.PauseCampaign, is Command.ResumeCampaign, is Command.ExtendCampaign, is Command.SetCampaignBudget,
        is Command.RefreshCreative, is Command.CancelCampaign, is Command.StartAbTest, is Command.LaunchPromotion, is Command.CancelPromotion -> ActivityArea.MARKETING
    }

    /** True for commands that spend or move money straight away, so a review of the log can pick them out. */
    fun movesMoney(cmd: Command): Boolean = when (cmd) {
        is Command.FoundCompany, is Command.BuyParcel, is Command.Build, is Command.Maintain, is Command.Borrow, is Command.Repay,
        is Command.BuyShares, is Command.SellShares, is Command.BuybackShares, is Command.BuyVehicle, is Command.BuyListed,
        is Command.LicenseTech, is Command.SubscribeBond, is Command.TradeBond, is Command.LobbyLaw, is Command.AddMargin,
        is Command.BuyPolicy, is Command.LeaseWarehouse, is Command.MakeOffer, is Command.DeliverMaterials, is Command.CreateCampaign,
        is Command.ExtendCampaign, is Command.SetCampaignBudget, is Command.RefreshCreative, is Command.StartAbTest -> true
        else -> false
    }
}
