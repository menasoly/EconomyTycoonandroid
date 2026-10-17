package app.many.economytycoon.simulation

import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.Department
import app.many.economytycoon.companies.DividendPolicy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.contracts.ContractSide
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.ContractId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.DepositId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.LoanId
import app.many.economytycoon.core.MachineTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.OrderId
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.ProjectId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.VehicleId
import app.many.economytycoon.core.VehicleTypeId
import app.many.economytycoon.core.WarehouseId
import app.many.economytycoon.logistics.StockRef
import app.many.economytycoon.markets.Side
import app.many.economytycoon.production.SchedulePolicy

/** Who is giving a command. Players may only act for companies they founded. */
sealed class Actor {
    data class Player(val id: Long) : Actor()
    data class Ai(val company: CompanyId) : Actor()
    object System : Actor()
}

/**
 * Everything a player or an AI can ask the economy to do. Commands are validated when executed; a refused
 * command changes nothing and returns the rule it broke.
 */
sealed class Command {
    /** Company the command acts for; null only for [FoundCompany]. */
    abstract val company: CompanyId?

    // ---- company ----
    data class FoundCompany(val name: String, val country: CountryId, val hq: CityId?, val capital: Money, val kind: CompanyKind = CompanyKind.PLAYER) : Command() {
        override val company: CompanyId? get() = null
    }
    data class Rename(override val company: CompanyId, val name: String) : Command()
    data class SetHeadquarters(override val company: CompanyId, val city: CityId) : Command()
    data class Dissolve(override val company: CompanyId) : Command()

    // ---- land and deposits ----
    data class SurveyDeposit(override val company: CompanyId, val deposit: DepositId) : Command()
    data class ClaimDeposit(override val company: CompanyId, val deposit: DepositId) : Command()
    data class BuyParcel(override val company: CompanyId, val parcel: ParcelId) : Command()

    // ---- facilities ----
    data class Build(override val company: CompanyId, val type: FacilityTypeId, val region: RegionId, val city: CityId? = null, val parcel: ParcelId? = null, val deposit: DepositId? = null, val name: String = "") : Command()
    data class CancelConstruction(override val company: CompanyId, val project: ProjectId) : Command()
    data class DeliverMaterials(override val company: CompanyId, val project: ProjectId, val from: FacilityId, val good: GoodId, val quantity: Quantity) : Command()
    data class ExpandStorage(override val company: CompanyId, val facility: FacilityId) : Command()
    data class AddLine(override val company: CompanyId, val facility: FacilityId) : Command()
    data class SetRecipe(override val company: CompanyId, val facility: FacilityId, val line: LineId, val recipe: RecipeId?) : Command()
    data class SetLineEnabled(override val company: CompanyId, val facility: FacilityId, val line: LineId, val enabled: Boolean) : Command()
    data class SetLinePriority(override val company: CompanyId, val facility: FacilityId, val line: LineId, val priority: Int) : Command()
    data class SetSchedulePolicy(override val company: CompanyId, val facility: FacilityId, val policy: SchedulePolicy) : Command()
    data class SetQuality(override val company: CompanyId, val facility: FacilityId, val levelPpm: Long) : Command()
    data class InstallMachine(override val company: CompanyId, val facility: FacilityId, val line: LineId, val type: MachineTypeId) : Command()
    data class RemoveMachine(override val company: CompanyId, val facility: FacilityId, val line: LineId, val type: MachineTypeId) : Command()
    data class Maintain(override val company: CompanyId, val facility: FacilityId, val budget: Money) : Command()
    data class DisposeWaste(override val company: CompanyId, val facility: FacilityId) : Command()
    data class Shutdown(override val company: CompanyId, val facility: FacilityId) : Command()
    data class Reopen(override val company: CompanyId, val facility: FacilityId) : Command()
    data class Demolish(override val company: CompanyId, val facility: FacilityId) : Command()

    // ---- workforce ----
    data class SetStaffing(override val company: CompanyId, val facility: FacilityId, val role: RoleId, val target: Long, val wage: Money? = null) : Command()
    data class SetBenefits(override val company: CompanyId, val ppm: Long) : Command()
    data class SetTraining(override val company: CompanyId, val facility: FacilityId, val role: RoleId, val ppm: Long) : Command()

    // ---- trading ----
    data class PlaceOrder(override val company: CompanyId, val facility: FacilityId, val side: Side, val good: GoodId, val quantity: Quantity, val limit: Price?, val ttl: Int = 5) : Command()
    data class CancelOrder(override val company: CompanyId, val order: OrderId) : Command()
    data class SetSellRule(override val company: CompanyId, val rule: SellRule) : Command()
    data class RemoveSellRule(override val company: CompanyId, val facility: FacilityId, val good: GoodId) : Command()
    data class SetBuyRule(override val company: CompanyId, val rule: BuyRule) : Command()
    data class RemoveBuyRule(override val company: CompanyId, val facility: FacilityId, val good: GoodId) : Command()
    data class SetDividend(override val company: CompanyId, val policy: DividendPolicy) : Command()

    // ---- finance ----
    data class Borrow(override val company: CompanyId, val amount: Money, val termTicks: Int) : Command()
    data class Repay(override val company: CompanyId, val loan: LoanId?, val amount: Money) : Command()
    data class SetBudget(override val company: CompanyId, val department: Department, val perTick: Money) : Command()

    // ---- research ----
    data class StartResearch(override val company: CompanyId, val tech: TechId) : Command()
    data class QueueResearch(override val company: CompanyId, val tech: TechId) : Command()
    data class CancelResearch(override val company: CompanyId, val tech: TechId) : Command()
    data class LicenseTech(override val company: CompanyId, val from: CompanyId, val tech: TechId) : Command()

    // ---- logistics ----
    data class Ship(override val company: CompanyId, val from: StockRef, val to: StockRef, val good: GoodId, val quantity: Quantity, val consignee: CompanyId? = null, val useFleet: Boolean = false) : Command()
    data class MoveLocal(override val company: CompanyId, val from: StockRef, val to: StockRef, val good: GoodId, val quantity: Quantity) : Command()
    data class BuyVehicle(override val company: CompanyId, val type: VehicleTypeId, val city: CityId) : Command()
    data class SellVehicle(override val company: CompanyId, val vehicle: VehicleId) : Command()
    data class RepairVehicle(override val company: CompanyId, val vehicle: VehicleId) : Command()
    data class LeaseWarehouse(override val company: CompanyId, val city: CityId, val capacity: Quantity) : Command()
    data class CloseWarehouse(override val company: CompanyId, val warehouse: WarehouseId) : Command()

    // ---- supply contracts ----
    data class ProposeContract(
        override val company: CompanyId, val counterparty: CompanyId, val side: ContractSide, val facility: FacilityId,
        val good: GoodId, val quantity: Quantity, val unitPrice: Money, val intervalTicks: Int, val deliveries: Int,
    ) : Command()
    data class AcceptContract(override val company: CompanyId, val contract: ContractId, val facility: FacilityId) : Command()
    data class DeclineContract(override val company: CompanyId, val contract: ContractId) : Command()
    data class CancelContract(override val company: CompanyId, val contract: ContractId) : Command()

    // ---- stock exchange ----
    data class ListCompany(override val company: CompanyId, val floatPpm: Long) : Command()
    data class DelistCompany(override val company: CompanyId) : Command()
    data class BuyShares(override val company: CompanyId, val issuer: CompanyId, val shares: Long) : Command()
    data class SellShares(override val company: CompanyId, val issuer: CompanyId, val shares: Long) : Command()
    data class BuybackShares(override val company: CompanyId, val shares: Long) : Command()

    // ---- public tenders ----
    data class BidTender(override val company: CompanyId, val tender: app.many.economytycoon.core.TenderId, val facility: FacilityId, val unitPrice: Money) : Command()
    data class WithdrawBid(override val company: CompanyId, val tender: app.many.economytycoon.core.TenderId) : Command()

    // ---- grants ----
    data class ApplyGrant(override val company: CompanyId, val program: app.many.economytycoon.core.GrantId) : Command()
    data class WithdrawGrant(override val company: CompanyId, val program: app.many.economytycoon.core.GrantId) : Command()

    // ---- marketing ----
    data class CreateCampaign(
        override val company: CompanyId, val good: GoodId, val region: RegionId, val city: CityId?, val channel: app.many.economytycoon.marketing.Channel,
        val segment: app.many.economytycoon.marketing.TargetSegment, val dailyBudget: Money, val days: Int,
        val tier: app.many.economytycoon.marketing.CreativeTier, val startTick: Tick?,
    ) : Command()
    data class PauseCampaign(override val company: CompanyId, val campaign: app.many.economytycoon.marketing.CampaignId) : Command()
    data class ResumeCampaign(override val company: CompanyId, val campaign: app.many.economytycoon.marketing.CampaignId) : Command()
    data class ExtendCampaign(override val company: CompanyId, val campaign: app.many.economytycoon.marketing.CampaignId, val extraDays: Int) : Command()
    data class SetCampaignBudget(override val company: CompanyId, val campaign: app.many.economytycoon.marketing.CampaignId, val dailyBudget: Money) : Command()
    data class RefreshCreative(override val company: CompanyId, val campaign: app.many.economytycoon.marketing.CampaignId, val tier: app.many.economytycoon.marketing.CreativeTier) : Command()
    data class CancelCampaign(override val company: CompanyId, val campaign: app.many.economytycoon.marketing.CampaignId) : Command()
    data class StartAbTest(override val company: CompanyId, val campaign: app.many.economytycoon.marketing.CampaignId, val challenger: app.many.economytycoon.marketing.CreativeTier, val days: Int) : Command()
    data class LaunchPromotion(
        override val company: CompanyId, val good: GoodId, val region: RegionId, val days: Int, val discountPpm: Long,
        val listPrice: Money, val unitCost: Money, val elasticityPpm: Long, val clearance: Boolean,
    ) : Command()
    data class CancelPromotion(override val company: CompanyId, val promotion: app.many.economytycoon.marketing.PromotionId) : Command()

    // ---- electricity grids ----
    /** Sets up one grid for every region of a world that has none; only the system gives it. */
    object EstablishGrids : Command() {
        override val company: CompanyId? get() = null
        override fun toString() = "EstablishGrids"
    }
    data class ConnectFacilityToGrid(override val company: CompanyId, val grid: Long, val facility: FacilityId, val customer: Int, val baseKw: Long) : Command()
    data class DisconnectFacilityFromGrid(override val company: CompanyId, val facility: FacilityId) : Command()
    data class ReclassifyGridLoad(override val company: CompanyId, val load: Long, val customer: Int) : Command()
    data class SetGridLoadDraw(override val company: CompanyId, val load: Long, val baseKw: Long) : Command()
    data class OrderGridUnit(override val company: CompanyId, val grid: Long, val tech: Int, val capacityKw: Long) : Command()
    data class RetireGridUnit(override val company: CompanyId, val unit: Long) : Command()
    data class MothballGridUnit(override val company: CompanyId, val unit: Long) : Command()
    data class RestartGridUnit(override val company: CompanyId, val unit: Long) : Command()
    data class OfferPowerAgreement(
        override val company: CompanyId, val grid: Long, val buyer: CompanyId, val profile: Int, val volumeKw: Long, val sharePpm: Long,
        val unit: Long?, val strike: Money, val startsAt: Long, val days: Int, val penaltyPpm: Long,
    ) : Command()
    data class AcceptPowerAgreement(override val company: CompanyId, val ppa: Long) : Command()
    data class DeclinePowerAgreement(override val company: CompanyId, val ppa: Long) : Command()
    data class TerminatePowerAgreement(override val company: CompanyId, val ppa: Long) : Command()

    // ---- labour relations ----
    data class OpenFuture(override val company: CompanyId, val good: GoodId, val side: app.many.economytycoon.futures.Side, val contracts: Long, val tenor: Int) : Command()
    data class CloseFuture(override val company: CompanyId, val position: Long) : Command()
    data class AddMargin(override val company: CompanyId, val position: Long, val amount: Money) : Command()
    data class ListParcel(override val company: CompanyId, val parcel: ParcelId, val ask: Money) : Command()
    data class DelistParcel(override val company: CompanyId, val parcel: ParcelId) : Command()
    data class BuyListed(override val company: CompanyId, val parcel: ParcelId) : Command()
    data class OfferLease(override val company: CompanyId, val parcel: ParcelId, val termDays: Int, val monthlyRent: Money) : Command()
    data class WithdrawLease(override val company: CompanyId, val parcel: ParcelId) : Command()
    data class EndLease(override val company: CompanyId, val parcel: ParcelId) : Command()
    data class RespondDemand(override val company: CompanyId, val demand: Long, val response: app.many.economytycoon.unions.ResponseKind, val counterPpm: Long = 0L) : Command()

    // ---- government bonds ----
    data class SubscribeBond(override val company: CompanyId, val bond: app.many.economytycoon.core.BondId, val units: Long) : Command()
    data class TradeBond(override val company: CompanyId, val bond: app.many.economytycoon.core.BondId, val units: Long, val buy: Boolean) : Command()

    // ---- regulation ----
    data class LobbyLaw(override val company: CompanyId, val law: app.many.economytycoon.core.RegulationId, val support: Boolean, val amount: Money) : Command()

    // ---- insurance ----
    data class BuyPolicy(
        override val company: CompanyId, val facility: FacilityId, val level: app.many.economytycoon.risk.CoverLevel,
        val coveragePpm: Long, val deductible: app.many.economytycoon.risk.Deductible,
    ) : Command()
    data class CancelPolicy(override val company: CompanyId, val policy: app.many.economytycoon.core.PolicyId) : Command()

    // ---- corporate ----
    data class MakeOffer(override val company: CompanyId, val target: CompanyId, val shares: Long, val pricePerShare: Money) : Command()
    data class RespondOffer(override val company: CompanyId, val offer: Long, val accept: Boolean) : Command()
    data class WithdrawOffer(override val company: CompanyId, val offer: Long) : Command()
    data class FileInsolvency(override val company: CompanyId) : Command()

    // ---- multiplayer ----
    /**
     * Moves cash between two companies, or between a company and the multiplayer escrow account (company 0), as part of a
     * player trade. [from] and [to] are company numbers. Only the system gives it; the host applies it to every copy of the game.
     */
    data class EscrowTransfer(val trade: Long, val from: Long, val to: Long, val amount: Money, val index: Int) : Command() {
        override val company: CompanyId? get() = null
    }
}

/** A command with the order and tick it was accepted, for replay. */
data class LoggedCommand(val sequence: Long, val tick: Long, val actor: Actor, val command: Command, val immediate: Boolean = false)
