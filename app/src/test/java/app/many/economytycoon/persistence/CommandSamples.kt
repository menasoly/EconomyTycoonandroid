package app.many.economytycoon.persistence

import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.Department
import app.many.economytycoon.companies.DividendPolicy
import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CityId
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
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.VehicleId
import app.many.economytycoon.core.VehicleTypeId
import app.many.economytycoon.core.WarehouseId
import app.many.economytycoon.logistics.StockRef
import app.many.economytycoon.markets.Side
import app.many.economytycoon.production.SchedulePolicy
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.LoggedCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** One of every command, with fields filled in, for tests that must cover them all. */
object CommandSamples {
    private val c = CompanyId(7)
    private val f = FacilityId(3)

    fun all(): List<Command> = listOf(
        Command.FoundCompany("Test Works", CountryId(2), CityId(5), Money.units(250_000), CompanyKind.PLAYER),
        Command.FoundCompany("No Seat", CountryId(2), null, Money.units(50_000), CompanyKind.AI),
        Command.Rename(c, "New Name"), Command.SetHeadquarters(c, CityId(9)), Command.Dissolve(c),
        Command.SurveyDeposit(c, DepositId(4)), Command.ClaimDeposit(c, DepositId(4)), Command.BuyParcel(c, ParcelId(11)),
        Command.Build(c, FacilityTypeId("grain_farm"), RegionId(1), CityId(2), ParcelId(3), DepositId(4), "North field"),
        Command.Build(c, FacilityTypeId("grain_farm"), RegionId(1)),
        Command.CancelConstruction(c, ProjectId(8)),
        Command.DeliverMaterials(c, ProjectId(8), f, GoodId("concrete"), Quantity.units(40)),
        Command.ExpandStorage(c, f), Command.AddLine(c, f),
        Command.SetRecipe(c, f, LineId(2), RecipeId("grow_wheat")), Command.SetRecipe(c, f, LineId(2), null),
        Command.SetLineEnabled(c, f, LineId(2), false), Command.SetLinePriority(c, f, LineId(2), 4),
        Command.SetSchedulePolicy(c, f, SchedulePolicy.values().last()), Command.SetQuality(c, f, 250_000),
        Command.InstallMachine(c, f, LineId(1), MachineTypeId("tractor")), Command.RemoveMachine(c, f, LineId(1), MachineTypeId("tractor")),
        Command.Maintain(c, f, Money.units(120)), Command.DisposeWaste(c, f), Command.Shutdown(c, f), Command.Reopen(c, f), Command.Demolish(c, f),
        Command.SetStaffing(c, f, RoleId("farmhand"), 12, Money.units(60)), Command.SetStaffing(c, f, RoleId("farmhand"), 12),
        Command.SetBenefits(c, 50_000), Command.SetTraining(c, f, RoleId("farmhand"), 100_000),
        Command.PlaceOrder(c, f, Side.SELL, GoodId("wheat"), Quantity.units(10), Price(Money.units(21)), 7),
        Command.PlaceOrder(c, f, Side.BUY, GoodId("wheat"), Quantity.units(10), null),
        Command.CancelOrder(c, OrderId(55)),
        Command.SetSellRule(c, SellRule(f, GoodId("wheat"), PriceStrategy.PREMIUM, 40_000, Quantity.units(5), Quantity.units(50), -100_000, false)),
        Command.SetSellRule(c, SellRule(f, GoodId("wheat"))),
        Command.RemoveSellRule(c, f, GoodId("wheat")),
        Command.SetBuyRule(c, BuyRule(f, GoodId("seeds"), Quantity.units(30), 1_300_000, true)), Command.RemoveBuyRule(c, f, GoodId("seeds")),
        Command.SetDividend(c, DividendPolicy(400_000, 20, 60)),
        Command.Borrow(c, Money.units(10_000), 180), Command.Repay(c, LoanId(2), Money.units(500)), Command.Repay(c, null, Money.units(500)),
        Command.SetBudget(c, Department.values().last(), Money.units(80)),
        Command.StartResearch(c, TechId("deep_mining")), Command.QueueResearch(c, TechId("deep_mining")), Command.CancelResearch(c, TechId("deep_mining")),
        Command.LicenseTech(c, CompanyId(9), TechId("deep_mining")),
        Command.Ship(c, StockRef.facility(f), StockRef.warehouse(WarehouseId(4)), GoodId("wheat"), Quantity.units(9), CompanyId(3), true),
        Command.Ship(c, StockRef.facility(f), StockRef.warehouse(WarehouseId(4)), GoodId("wheat"), Quantity.units(9)),
        Command.MoveLocal(c, StockRef.facility(f), StockRef.facility(FacilityId(5)), GoodId("wheat"), Quantity.units(2)),
        Command.BuyVehicle(c, VehicleTypeId("truck"), CityId(1)), Command.SellVehicle(c, VehicleId(6)), Command.RepairVehicle(c, VehicleId(6)),
        Command.LeaseWarehouse(c, CityId(1), Quantity.units(5_000)), Command.CloseWarehouse(c, WarehouseId(2)),
        Command.MakeOffer(c, CompanyId(8), 1_000, Money.units(12)), Command.RespondOffer(c, 3, true), Command.WithdrawOffer(c, 3),
        Command.FileInsolvency(c),
        Command.ListCompany(c, 250_000L), Command.DelistCompany(c),
        Command.BuyShares(c, CompanyId(9), 1_250), Command.SellShares(c, CompanyId(9), 40), Command.BuybackShares(c, 300),
        Command.ProposeContract(c, CompanyId(9), app.many.economytycoon.contracts.ContractSide.BUYER, f, GoodId("wheat"), Quantity.units(25), Money.units(19), 7, 12),
        Command.ProposeContract(c, CompanyId(9), app.many.economytycoon.contracts.ContractSide.SELLER, f, GoodId("steel"), Quantity.units(1), Money.units(400), 1, 1),
        Command.AcceptContract(c, app.many.economytycoon.core.ContractId(6), f),
        Command.DeclineContract(c, app.many.economytycoon.core.ContractId(6)),
        Command.CancelContract(c, app.many.economytycoon.core.ContractId(6)),
        Command.BuyPolicy(c, f, app.many.economytycoon.risk.CoverLevel.FULL, 750_000L, app.many.economytycoon.risk.Deductible.HIGH),
        Command.CancelPolicy(c, app.many.economytycoon.core.PolicyId(4)),
        Command.BidTender(c, app.many.economytycoon.core.TenderId(8), f, Money.units(17)),
        Command.WithdrawBid(c, app.many.economytycoon.core.TenderId(8)),
        Command.ApplyGrant(c, app.many.economytycoon.core.GrantId(5)),
        Command.WithdrawGrant(c, app.many.economytycoon.core.GrantId(6)),
        Command.SubscribeBond(c, app.many.economytycoon.core.BondId(3), 25),
        Command.TradeBond(c, app.many.economytycoon.core.BondId(3), 10, true),
        Command.TradeBond(c, app.many.economytycoon.core.BondId(3), 10, false),
        Command.RespondDemand(c, 4, app.many.economytycoon.unions.ResponseKind.ACCEPT, 0L),
        Command.RespondDemand(c, 4, app.many.economytycoon.unions.ResponseKind.COUNTER, 35_000L),
        Command.RespondDemand(c, 4, app.many.economytycoon.unions.ResponseKind.REFUSE, 0L),
        Command.OpenFuture(c, app.many.economytycoon.core.GoodId("wheat"), app.many.economytycoon.futures.Side.LONG, 5, 60),
        Command.OpenFuture(c, app.many.economytycoon.core.GoodId("iron_ore"), app.many.economytycoon.futures.Side.SHORT, 1, 30),
        Command.CloseFuture(c, 9),
        Command.AddMargin(c, 9, Money.units(250)),
        Command.ListParcel(c, app.many.economytycoon.core.ParcelId(12), Money.units(80_000)),
        Command.DelistParcel(c, app.many.economytycoon.core.ParcelId(12)),
        Command.BuyListed(c, app.many.economytycoon.core.ParcelId(12)),
        Command.OfferLease(c, app.many.economytycoon.core.ParcelId(12), 180, Money.units(450)),
        Command.WithdrawLease(c, app.many.economytycoon.core.ParcelId(12)),
        Command.EndLease(c, app.many.economytycoon.core.ParcelId(12)),
        Command.LobbyLaw(c, app.many.economytycoon.core.RegulationId(5), true, Money.units(2_500)),
        Command.LobbyLaw(c, app.many.economytycoon.core.RegulationId(5), false, Money.units(10)),
        Command.CreateCampaign(c, GoodId("beer"), RegionId(1), null, app.many.economytycoon.marketing.Channel.RADIO, app.many.economytycoon.marketing.TargetSegment.EVERYONE, Money.units(500), 30, app.many.economytycoon.marketing.CreativeTier.PROFESSIONAL, null),
        Command.CreateCampaign(c, GoodId("bread"), RegionId(2), CityId(10), app.many.economytycoon.marketing.Channel.IN_STORE, app.many.economytycoon.marketing.TargetSegment.BUDGET, Money.units(90), 12, app.many.economytycoon.marketing.CreativeTier.BASIC, Tick(40)),
        Command.PauseCampaign(c, app.many.economytycoon.marketing.CampaignId(3)),
        Command.ResumeCampaign(c, app.many.economytycoon.marketing.CampaignId(3)),
        Command.ExtendCampaign(c, app.many.economytycoon.marketing.CampaignId(3), 14),
        Command.SetCampaignBudget(c, app.many.economytycoon.marketing.CampaignId(3), Money.units(750)),
        Command.RefreshCreative(c, app.many.economytycoon.marketing.CampaignId(3), app.many.economytycoon.marketing.CreativeTier.STUDIO),
        Command.CancelCampaign(c, app.many.economytycoon.marketing.CampaignId(3)),
        Command.StartAbTest(c, app.many.economytycoon.marketing.CampaignId(3), app.many.economytycoon.marketing.CreativeTier.STUDIO, 10),
        Command.LaunchPromotion(c, GoodId("beer"), RegionId(1), 7, 150_000L, Money.units(100), Money.units(60), 2_000_000L, false),
        Command.LaunchPromotion(c, GoodId("beer"), RegionId(1), 7, 300_000L, Money.units(100), Money.units(60), 2_000_000L, true),
        Command.CancelPromotion(c, app.many.economytycoon.marketing.PromotionId(2)),
        Command.EstablishGrids,
        Command.ConnectFacilityToGrid(c, 2L, f, 1, 40_000L),
        Command.DisconnectFacilityFromGrid(c, f),
        Command.ReclassifyGridLoad(c, 5L, 2),
        Command.SetGridLoadDraw(c, 5L, 12_000L),
        Command.OrderGridUnit(c, 2L, 3, 80_000L),
        Command.RetireGridUnit(c, 7L),
        Command.MothballGridUnit(c, 7L),
        Command.RestartGridUnit(c, 7L),
        Command.OfferPowerAgreement(c, 2L, CompanyId(9), 0, 20_000L, 500_000L, null, Money.units(40), 12L, 180, 100_000L),
        Command.OfferPowerAgreement(c, 2L, CompanyId(9), 1, 15_000L, 250_000L, 7L, Money.units(55), 30L, 90, 50_000L),
        Command.AcceptPowerAgreement(c, 4L),
        Command.DeclinePowerAgreement(c, 4L),
        Command.TerminatePowerAgreement(c, 4L),
        Command.EscrowTransfer(3L, 5L, 0L, Money.units(250), 1),
    )
}
