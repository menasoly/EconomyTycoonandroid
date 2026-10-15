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
import app.many.economytycoon.core.OrderId
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.ProjectId
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.VehicleId
import app.many.economytycoon.core.VehicleTypeId
import app.many.economytycoon.core.WarehouseId
import app.many.economytycoon.logistics.StockKind
import app.many.economytycoon.logistics.StockRef
import app.many.economytycoon.markets.Side
import app.many.economytycoon.production.SchedulePolicy
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.LoggedCommand

/**
 * Binary form of commands and the log built from them. Every command type has a fixed tag that must never
 * change meaning; new commands take new tags, and an unknown tag makes the whole log unreadable rather
 * than silently skipping a command and replaying a different game.
 */
object CommandCodec {
    const val VERSION = 1

    // ---- log -----------------------------------------------------------------------------------

    fun writeLog(log: List<LoggedCommand>, w: ByteWriter) {
        w.writeVarInt(VERSION)
        w.writeVarInt(log.size)
        for (entry in log) writeLogged(entry, w)
    }

    fun readLog(r: ByteReader): List<LoggedCommand> {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported command log version $version")
        val n = r.readVarInt()
        if (n < 0 || n > MAX_ENTRIES) throw ByteReader.CorruptDataException("implausible command log size $n")
        return List(n) { readLogged(r) }
    }

    fun writeLogged(entry: LoggedCommand, w: ByteWriter) {
        w.writeVarLong(entry.sequence)
        w.writeVarLong(entry.tick)
        w.writeBoolean(entry.immediate)
        writeActor(entry.actor, w)
        writeCommand(entry.command, w)
    }

    fun readLogged(r: ByteReader): LoggedCommand {
        val sequence = r.readVarLong()
        val tick = r.readVarLong()
        val immediate = r.readBoolean()
        val actor = readActor(r)
        return LoggedCommand(sequence, tick, actor, readCommand(r), immediate)
    }

    fun writeActor(a: Actor, w: ByteWriter) {
        when (a) {
            is Actor.Player -> { w.writeByte(0); w.writeVarLong(a.id) }
            is Actor.Ai -> { w.writeByte(1); w.writeVarLong(a.company.value) }
            is Actor.System -> w.writeByte(2)
        }
    }

    fun readActor(r: ByteReader): Actor = when (val t = r.readByte()) {
        0 -> Actor.Player(r.readVarLong())
        1 -> Actor.Ai(CompanyId(r.readVarLong()))
        2 -> Actor.System
        else -> throw ByteReader.CorruptDataException("unknown actor tag $t")
    }

    // ---- commands ------------------------------------------------------------------------------

    fun writeCommand(c: Command, w: ByteWriter) {
        when (c) {
            is Command.FoundCompany -> { tag(w, 1); w.writeString(c.name); w.writeVarLong(c.country.value); city(w, c.hq); w.writeMoney(c.capital); w.writeVarInt(c.kind.ordinal) }
            is Command.Rename -> { tag(w, 2); co(w, c.company); w.writeString(c.name) }
            is Command.SetHeadquarters -> { tag(w, 3); co(w, c.company); w.writeVarLong(c.city.value) }
            is Command.Dissolve -> { tag(w, 4); co(w, c.company) }
            is Command.SurveyDeposit -> { tag(w, 5); co(w, c.company); w.writeVarLong(c.deposit.value) }
            is Command.ClaimDeposit -> { tag(w, 6); co(w, c.company); w.writeVarLong(c.deposit.value) }
            is Command.BuyParcel -> { tag(w, 7); co(w, c.company); w.writeVarLong(c.parcel.value) }
            is Command.Build -> {
                tag(w, 8); co(w, c.company); w.writeString(c.type.key); w.writeVarLong(c.region.value)
                city(w, c.city); optLong(w, c.parcel?.value); optLong(w, c.deposit?.value); w.writeString(c.name)
            }
            is Command.CancelConstruction -> { tag(w, 9); co(w, c.company); w.writeVarLong(c.project.value) }
            is Command.DeliverMaterials -> { tag(w, 10); co(w, c.company); w.writeVarLong(c.project.value); w.writeVarLong(c.from.value); w.writeString(c.good.key); w.writeQuantity(c.quantity) }
            is Command.ExpandStorage -> { tag(w, 11); co(w, c.company); w.writeVarLong(c.facility.value) }
            is Command.AddLine -> { tag(w, 12); co(w, c.company); w.writeVarLong(c.facility.value) }
            is Command.SetRecipe -> { tag(w, 13); co(w, c.company); w.writeVarLong(c.facility.value); w.writeVarLong(c.line.value); optString(w, c.recipe?.key) }
            is Command.SetLineEnabled -> { tag(w, 14); co(w, c.company); w.writeVarLong(c.facility.value); w.writeVarLong(c.line.value); w.writeBoolean(c.enabled) }
            is Command.SetLinePriority -> { tag(w, 15); co(w, c.company); w.writeVarLong(c.facility.value); w.writeVarLong(c.line.value); w.writeVarInt(c.priority) }
            is Command.SetSchedulePolicy -> { tag(w, 16); co(w, c.company); w.writeVarLong(c.facility.value); w.writeVarInt(c.policy.ordinal) }
            is Command.SetQuality -> { tag(w, 17); co(w, c.company); w.writeVarLong(c.facility.value); w.writeVarLong(c.levelPpm) }
            is Command.InstallMachine -> { tag(w, 18); co(w, c.company); w.writeVarLong(c.facility.value); w.writeVarLong(c.line.value); w.writeString(c.type.key) }
            is Command.RemoveMachine -> { tag(w, 19); co(w, c.company); w.writeVarLong(c.facility.value); w.writeVarLong(c.line.value); w.writeString(c.type.key) }
            is Command.Maintain -> { tag(w, 20); co(w, c.company); w.writeVarLong(c.facility.value); w.writeMoney(c.budget) }
            is Command.DisposeWaste -> { tag(w, 21); co(w, c.company); w.writeVarLong(c.facility.value) }
            is Command.Shutdown -> { tag(w, 22); co(w, c.company); w.writeVarLong(c.facility.value) }
            is Command.Reopen -> { tag(w, 23); co(w, c.company); w.writeVarLong(c.facility.value) }
            is Command.Demolish -> { tag(w, 24); co(w, c.company); w.writeVarLong(c.facility.value) }
            is Command.SetStaffing -> { tag(w, 25); co(w, c.company); w.writeVarLong(c.facility.value); w.writeString(c.role.key); w.writeVarLong(c.target); optLong(w, c.wage?.raw) }
            is Command.SetBenefits -> { tag(w, 26); co(w, c.company); w.writeVarLong(c.ppm) }
            is Command.SetTraining -> { tag(w, 27); co(w, c.company); w.writeVarLong(c.facility.value); w.writeString(c.role.key); w.writeVarLong(c.ppm) }
            is Command.PlaceOrder -> {
                tag(w, 28); co(w, c.company); w.writeVarLong(c.facility.value); w.writeVarInt(c.side.ordinal); w.writeString(c.good.key)
                w.writeQuantity(c.quantity); optLong(w, c.limit?.perUnit?.raw); w.writeVarInt(c.ttl)
            }
            is Command.CancelOrder -> { tag(w, 29); co(w, c.company); w.writeVarLong(c.order.value) }
            is Command.SetSellRule -> { tag(w, 30); co(w, c.company); writeSell(c.rule, w) }
            is Command.RemoveSellRule -> { tag(w, 31); co(w, c.company); w.writeVarLong(c.facility.value); w.writeString(c.good.key) }
            is Command.SetBuyRule -> { tag(w, 32); co(w, c.company); writeBuy(c.rule, w) }
            is Command.RemoveBuyRule -> { tag(w, 33); co(w, c.company); w.writeVarLong(c.facility.value); w.writeString(c.good.key) }
            is Command.SetDividend -> { tag(w, 34); co(w, c.company); w.writeVarLong(c.policy.payoutPpm); w.writeVarInt(c.policy.reserveTicks); w.writeVarInt(c.policy.intervalTicks) }
            is Command.Borrow -> { tag(w, 35); co(w, c.company); w.writeMoney(c.amount); w.writeVarInt(c.termTicks) }
            is Command.Repay -> { tag(w, 36); co(w, c.company); optLong(w, c.loan?.value); w.writeMoney(c.amount) }
            is Command.SetBudget -> { tag(w, 37); co(w, c.company); w.writeVarInt(c.department.ordinal); w.writeMoney(c.perTick) }
            is Command.StartResearch -> { tag(w, 38); co(w, c.company); w.writeString(c.tech.key) }
            is Command.QueueResearch -> { tag(w, 39); co(w, c.company); w.writeString(c.tech.key) }
            is Command.CancelResearch -> { tag(w, 40); co(w, c.company); w.writeString(c.tech.key) }
            is Command.LicenseTech -> { tag(w, 41); co(w, c.company); w.writeVarLong(c.from.value); w.writeString(c.tech.key) }
            is Command.Ship -> {
                tag(w, 42); co(w, c.company); writeRef(c.from, w); writeRef(c.to, w); w.writeString(c.good.key); w.writeQuantity(c.quantity)
                optLong(w, c.consignee?.value); w.writeBoolean(c.useFleet)
            }
            is Command.MoveLocal -> { tag(w, 43); co(w, c.company); writeRef(c.from, w); writeRef(c.to, w); w.writeString(c.good.key); w.writeQuantity(c.quantity) }
            is Command.BuyVehicle -> { tag(w, 44); co(w, c.company); w.writeString(c.type.key); w.writeVarLong(c.city.value) }
            is Command.SellVehicle -> { tag(w, 45); co(w, c.company); w.writeVarLong(c.vehicle.value) }
            is Command.RepairVehicle -> { tag(w, 46); co(w, c.company); w.writeVarLong(c.vehicle.value) }
            is Command.LeaseWarehouse -> { tag(w, 47); co(w, c.company); w.writeVarLong(c.city.value); w.writeQuantity(c.capacity) }
            is Command.CloseWarehouse -> { tag(w, 48); co(w, c.company); w.writeVarLong(c.warehouse.value) }
            is Command.MakeOffer -> { tag(w, 49); co(w, c.company); w.writeVarLong(c.target.value); w.writeVarLong(c.shares); w.writeMoney(c.pricePerShare) }
            is Command.RespondOffer -> { tag(w, 50); co(w, c.company); w.writeVarLong(c.offer); w.writeBoolean(c.accept) }
            is Command.WithdrawOffer -> { tag(w, 51); co(w, c.company); w.writeVarLong(c.offer) }
            is Command.FileInsolvency -> { tag(w, 52); co(w, c.company) }
            is Command.BidTender -> { tag(w, 64); co(w, c.company); w.writeVarLong(c.tender.value); w.writeVarLong(c.facility.value); w.writeMoney(c.unitPrice) }
            is Command.WithdrawBid -> { tag(w, 65); co(w, c.company); w.writeVarLong(c.tender.value) }
            is Command.ApplyGrant -> { tag(w, 79); co(w, c.company); w.writeVarLong(c.program.value) }
            is Command.WithdrawGrant -> { tag(w, 80); co(w, c.company); w.writeVarLong(c.program.value) }
            is Command.ListParcel -> { tag(w, 73); co(w, c.company); w.writeVarLong(c.parcel.value); w.writeMoney(c.ask) }
            is Command.DelistParcel -> { tag(w, 74); co(w, c.company); w.writeVarLong(c.parcel.value) }
            is Command.BuyListed -> { tag(w, 75); co(w, c.company); w.writeVarLong(c.parcel.value) }
            is Command.OfferLease -> { tag(w, 76); co(w, c.company); w.writeVarLong(c.parcel.value); w.writeVarInt(c.termDays); w.writeMoney(c.monthlyRent) }
            is Command.WithdrawLease -> { tag(w, 77); co(w, c.company); w.writeVarLong(c.parcel.value) }
            is Command.EndLease -> { tag(w, 78); co(w, c.company); w.writeVarLong(c.parcel.value) }
            is Command.OpenFuture -> { tag(w, 70); co(w, c.company); w.writeString(c.good.key); w.writeVarInt(c.side.ordinal); w.writeVarLong(c.contracts); w.writeVarInt(c.tenor) }
            is Command.CloseFuture -> { tag(w, 71); co(w, c.company); w.writeVarLong(c.position) }
            is Command.AddMargin -> { tag(w, 72); co(w, c.company); w.writeVarLong(c.position); w.writeMoney(c.amount) }
            is Command.RespondDemand -> { tag(w, 69); co(w, c.company); w.writeVarLong(c.demand); w.writeVarInt(c.response.ordinal); w.writeVarLong(c.counterPpm) }
            is Command.SubscribeBond -> { tag(w, 67); co(w, c.company); w.writeVarLong(c.bond.value); w.writeVarLong(c.units) }
            is Command.TradeBond -> { tag(w, 68); co(w, c.company); w.writeVarLong(c.bond.value); w.writeVarLong(c.units); w.writeBoolean(c.buy) }
            is Command.LobbyLaw -> { tag(w, 66); co(w, c.company); w.writeVarLong(c.law.value); w.writeBoolean(c.support); w.writeMoney(c.amount) }
            is Command.BuyPolicy -> { tag(w, 62); co(w, c.company); w.writeVarLong(c.facility.value); w.writeVarInt(c.level.ordinal); w.writeVarLong(c.coveragePpm); w.writeVarInt(c.deductible.ordinal) }
            is Command.CancelPolicy -> { tag(w, 63); co(w, c.company); w.writeVarLong(c.policy.value) }
            is Command.ListCompany -> { tag(w, 57); co(w, c.company); w.writeVarLong(c.floatPpm) }
            is Command.DelistCompany -> { tag(w, 58); co(w, c.company) }
            is Command.BuyShares -> { tag(w, 59); co(w, c.company); w.writeVarLong(c.issuer.value); w.writeVarLong(c.shares) }
            is Command.SellShares -> { tag(w, 60); co(w, c.company); w.writeVarLong(c.issuer.value); w.writeVarLong(c.shares) }
            is Command.BuybackShares -> { tag(w, 61); co(w, c.company); w.writeVarLong(c.shares) }
            is Command.ProposeContract -> {
                tag(w, 53); co(w, c.company); w.writeVarLong(c.counterparty.value); w.writeVarInt(c.side.ordinal); w.writeVarLong(c.facility.value)
                w.writeString(c.good.key); w.writeQuantity(c.quantity); w.writeMoney(c.unitPrice); w.writeVarInt(c.intervalTicks); w.writeVarInt(c.deliveries)
            }
            is Command.AcceptContract -> { tag(w, 54); co(w, c.company); w.writeVarLong(c.contract.value); w.writeVarLong(c.facility.value) }
            is Command.DeclineContract -> { tag(w, 55); co(w, c.company); w.writeVarLong(c.contract.value) }
            is Command.CancelContract -> { tag(w, 56); co(w, c.company); w.writeVarLong(c.contract.value) }
        }
    }

    fun readCommand(r: ByteReader): Command = try {
        decode(r)
    } catch (e: IllegalArgumentException) {
        throw ByteReader.CorruptDataException("invalid command: ${e.message}")
    }

    private fun decode(r: ByteReader): Command {
        val t = r.readVarInt()
        if (t == 1) {
            val name = r.readString(); val country = CountryId(r.readVarLong()); val hq = readCity(r); val capital = r.readMoney()
            val kind = CompanyKind.values().getOrNull(r.readVarInt()) ?: throw ByteReader.CorruptDataException("bad company kind")
            return Command.FoundCompany(name, country, hq, capital, kind)
        }
        val company = CompanyId(r.readVarLong())
        return when (t) {
            2 -> Command.Rename(company, r.readString())
            3 -> Command.SetHeadquarters(company, CityId(r.readVarLong()))
            4 -> Command.Dissolve(company)
            5 -> Command.SurveyDeposit(company, DepositId(r.readVarLong()))
            6 -> Command.ClaimDeposit(company, DepositId(r.readVarLong()))
            7 -> Command.BuyParcel(company, ParcelId(r.readVarLong()))
            8 -> {
                val type = FacilityTypeId(r.readString()); val region = RegionId(r.readVarLong()); val city = readCity(r)
                val parcel = readOptLong(r)?.let { ParcelId(it) }; val deposit = readOptLong(r)?.let { DepositId(it) }
                Command.Build(company, type, region, city, parcel, deposit, r.readString())
            }
            9 -> Command.CancelConstruction(company, ProjectId(r.readVarLong()))
            10 -> Command.DeliverMaterials(company, ProjectId(r.readVarLong()), FacilityId(r.readVarLong()), GoodId(r.readString()), r.readQuantity())
            11 -> Command.ExpandStorage(company, FacilityId(r.readVarLong()))
            12 -> Command.AddLine(company, FacilityId(r.readVarLong()))
            13 -> Command.SetRecipe(company, FacilityId(r.readVarLong()), LineId(r.readVarLong()), readOptString(r)?.let { RecipeId(it) })
            14 -> Command.SetLineEnabled(company, FacilityId(r.readVarLong()), LineId(r.readVarLong()), r.readBoolean())
            15 -> Command.SetLinePriority(company, FacilityId(r.readVarLong()), LineId(r.readVarLong()), r.readVarInt())
            16 -> Command.SetSchedulePolicy(company, FacilityId(r.readVarLong()), enumAt(SchedulePolicy.values(), r.readVarInt(), "schedule policy"))
            17 -> Command.SetQuality(company, FacilityId(r.readVarLong()), r.readVarLong())
            18 -> Command.InstallMachine(company, FacilityId(r.readVarLong()), LineId(r.readVarLong()), MachineTypeId(r.readString()))
            19 -> Command.RemoveMachine(company, FacilityId(r.readVarLong()), LineId(r.readVarLong()), MachineTypeId(r.readString()))
            20 -> Command.Maintain(company, FacilityId(r.readVarLong()), r.readMoney())
            21 -> Command.DisposeWaste(company, FacilityId(r.readVarLong()))
            22 -> Command.Shutdown(company, FacilityId(r.readVarLong()))
            23 -> Command.Reopen(company, FacilityId(r.readVarLong()))
            24 -> Command.Demolish(company, FacilityId(r.readVarLong()))
            25 -> Command.SetStaffing(company, FacilityId(r.readVarLong()), RoleId(r.readString()), r.readVarLong(), readOptLong(r)?.let { app.many.economytycoon.core.Money(it) })
            26 -> Command.SetBenefits(company, r.readVarLong())
            27 -> Command.SetTraining(company, FacilityId(r.readVarLong()), RoleId(r.readString()), r.readVarLong())
            28 -> Command.PlaceOrder(
                company, FacilityId(r.readVarLong()), enumAt(Side.values(), r.readVarInt(), "side"), GoodId(r.readString()),
                r.readQuantity(), readOptLong(r)?.let { Price(app.many.economytycoon.core.Money(it)) }, r.readVarInt(),
            )
            29 -> Command.CancelOrder(company, OrderId(r.readVarLong()))
            30 -> Command.SetSellRule(company, readSell(r))
            31 -> Command.RemoveSellRule(company, FacilityId(r.readVarLong()), GoodId(r.readString()))
            32 -> Command.SetBuyRule(company, readBuy(r))
            33 -> Command.RemoveBuyRule(company, FacilityId(r.readVarLong()), GoodId(r.readString()))
            34 -> Command.SetDividend(company, DividendPolicy(r.readVarLong(), r.readVarInt(), r.readVarInt()))
            35 -> Command.Borrow(company, r.readMoney(), r.readVarInt())
            36 -> Command.Repay(company, readOptLong(r)?.let { LoanId(it) }, r.readMoney())
            37 -> Command.SetBudget(company, enumAt(Department.values(), r.readVarInt(), "department"), r.readMoney())
            38 -> Command.StartResearch(company, TechId(r.readString()))
            39 -> Command.QueueResearch(company, TechId(r.readString()))
            40 -> Command.CancelResearch(company, TechId(r.readString()))
            41 -> Command.LicenseTech(company, CompanyId(r.readVarLong()), TechId(r.readString()))
            42 -> Command.Ship(company, readRef(r), readRef(r), GoodId(r.readString()), r.readQuantity(), readOptLong(r)?.let { CompanyId(it) }, r.readBoolean())
            43 -> Command.MoveLocal(company, readRef(r), readRef(r), GoodId(r.readString()), r.readQuantity())
            44 -> Command.BuyVehicle(company, VehicleTypeId(r.readString()), CityId(r.readVarLong()))
            45 -> Command.SellVehicle(company, VehicleId(r.readVarLong()))
            46 -> Command.RepairVehicle(company, VehicleId(r.readVarLong()))
            47 -> Command.LeaseWarehouse(company, CityId(r.readVarLong()), r.readQuantity())
            48 -> Command.CloseWarehouse(company, WarehouseId(r.readVarLong()))
            49 -> Command.MakeOffer(company, CompanyId(r.readVarLong()), r.readVarLong(), r.readMoney())
            50 -> Command.RespondOffer(company, r.readVarLong(), r.readBoolean())
            51 -> Command.WithdrawOffer(company, r.readVarLong())
            52 -> Command.FileInsolvency(company)
            64 -> Command.BidTender(company, app.many.economytycoon.core.TenderId(r.readVarLong()), app.many.economytycoon.core.FacilityId(r.readVarLong()), r.readMoney())
            65 -> Command.WithdrawBid(company, app.many.economytycoon.core.TenderId(r.readVarLong()))
            79 -> Command.ApplyGrant(company, app.many.economytycoon.core.GrantId(r.readVarLong()))
            80 -> Command.WithdrawGrant(company, app.many.economytycoon.core.GrantId(r.readVarLong()))
            73 -> Command.ListParcel(company, ParcelId(r.readVarLong()), r.readMoney())
            74 -> Command.DelistParcel(company, ParcelId(r.readVarLong()))
            75 -> Command.BuyListed(company, ParcelId(r.readVarLong()))
            76 -> Command.OfferLease(company, ParcelId(r.readVarLong()), r.readVarInt(), r.readMoney())
            77 -> Command.WithdrawLease(company, ParcelId(r.readVarLong()))
            78 -> Command.EndLease(company, ParcelId(r.readVarLong()))
            70 -> Command.OpenFuture(company, GoodId(r.readString()), enumAt(app.many.economytycoon.futures.Side.values(), r.readVarInt(), "side"), r.readVarLong(), r.readVarInt())
            71 -> Command.CloseFuture(company, r.readVarLong())
            72 -> Command.AddMargin(company, r.readVarLong(), r.readMoney())
            69 -> Command.RespondDemand(company, r.readVarLong(), enumAt(app.many.economytycoon.unions.ResponseKind.values(), r.readVarInt(), "response"), r.readVarLong())
            67 -> Command.SubscribeBond(company, app.many.economytycoon.core.BondId(r.readVarLong()), r.readVarLong())
            68 -> Command.TradeBond(company, app.many.economytycoon.core.BondId(r.readVarLong()), r.readVarLong(), r.readBoolean())
            66 -> Command.LobbyLaw(company, app.many.economytycoon.core.RegulationId(r.readVarLong()), r.readBoolean(), r.readMoney())
            62 -> Command.BuyPolicy(
                company, app.many.economytycoon.core.FacilityId(r.readVarLong()), enumAt(app.many.economytycoon.risk.CoverLevel.values(), r.readVarInt(), "cover level"),
                r.readVarLong(), enumAt(app.many.economytycoon.risk.Deductible.values(), r.readVarInt(), "deductible"),
            )
            63 -> Command.CancelPolicy(company, app.many.economytycoon.core.PolicyId(r.readVarLong()))
            57 -> Command.ListCompany(company, r.readVarLong())
            58 -> Command.DelistCompany(company)
            59 -> Command.BuyShares(company, CompanyId(r.readVarLong()), r.readVarLong())
            60 -> Command.SellShares(company, CompanyId(r.readVarLong()), r.readVarLong())
            61 -> Command.BuybackShares(company, r.readVarLong())
            53 -> Command.ProposeContract(
                company, CompanyId(r.readVarLong()), enumAt(app.many.economytycoon.contracts.ContractSide.values(), r.readVarInt(), "contract side"),
                FacilityId(r.readVarLong()), GoodId(r.readString()), r.readQuantity(), r.readMoney(), r.readVarInt(), r.readVarInt(),
            )
            54 -> Command.AcceptContract(company, app.many.economytycoon.core.ContractId(r.readVarLong()), FacilityId(r.readVarLong()))
            55 -> Command.DeclineContract(company, app.many.economytycoon.core.ContractId(r.readVarLong()))
            56 -> Command.CancelContract(company, app.many.economytycoon.core.ContractId(r.readVarLong()))
            else -> throw ByteReader.CorruptDataException("unknown command tag $t")
        }
    }

    // ---- pieces --------------------------------------------------------------------------------

    private fun tag(w: ByteWriter, t: Int) = w.writeVarInt(t)
    private fun co(w: ByteWriter, c: CompanyId) = w.writeVarLong(c.value)
    private fun city(w: ByteWriter, c: CityId?) = optLong(w, c?.value)
    private fun readCity(r: ByteReader): CityId? = readOptLong(r)?.let { CityId(it) }

    private fun optLong(w: ByteWriter, v: Long?) { w.writeBoolean(v != null); if (v != null) w.writeVarLong(v) }
    private fun readOptLong(r: ByteReader): Long? = if (r.readBoolean()) r.readVarLong() else null
    private fun optString(w: ByteWriter, v: String?) { w.writeBoolean(v != null); if (v != null) w.writeString(v) }
    private fun readOptString(r: ByteReader): String? = if (r.readBoolean()) r.readString() else null

    private fun <T> enumAt(values: Array<T>, i: Int, what: String): T =
        values.getOrNull(i) ?: throw ByteReader.CorruptDataException("bad $what ordinal $i")

    private fun writeRef(ref: StockRef, w: ByteWriter) { w.writeVarInt(ref.kind.ordinal); w.writeVarLong(ref.id) }
    private fun readRef(r: ByteReader) = StockRef(enumAt(StockKind.values(), r.readVarInt(), "stock kind"), r.readVarLong())

    private fun writeSell(s: SellRule, w: ByteWriter) {
        w.writeVarLong(s.facility.value); w.writeString(s.good.key); w.writeVarInt(s.strategy.ordinal); w.writeVarLong(s.markupPpm)
        w.writeQuantity(s.reserve); w.writeBoolean(s.maxPerOrder != null); s.maxPerOrder?.let { w.writeQuantity(it) }
        w.writeVarLong(s.minMarginPpm); w.writeBoolean(s.enabled)
    }

    private fun readSell(r: ByteReader): SellRule {
        val facility = FacilityId(r.readVarLong()); val good = GoodId(r.readString())
        val strategy = enumAt(PriceStrategy.values(), r.readVarInt(), "price strategy")
        val markup = r.readVarLong(); val reserve = r.readQuantity()
        val max = if (r.readBoolean()) r.readQuantity() else null
        return try {
            SellRule(facility, good, strategy, markup, reserve, max, r.readVarLong(), r.readBoolean())
        } catch (e: IllegalArgumentException) {
            throw ByteReader.CorruptDataException("invalid sell rule: ${e.message}")
        }
    }

    private fun writeBuy(b: BuyRule, w: ByteWriter) {
        w.writeVarLong(b.facility.value); w.writeString(b.good.key); w.writeQuantity(b.target); w.writeVarLong(b.maxPriceMultiplePpm); w.writeBoolean(b.enabled)
    }

    private fun readBuy(r: ByteReader): BuyRule = try {
        BuyRule(FacilityId(r.readVarLong()), GoodId(r.readString()), r.readQuantity(), r.readVarLong(), r.readBoolean())
    } catch (e: IllegalArgumentException) {
        throw ByteReader.CorruptDataException("invalid buy rule: ${e.message}")
    }

    const val MAX_ENTRIES = 5_000_000
}
