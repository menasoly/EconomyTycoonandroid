package app.many.economytycoon.simulation

import app.many.economytycoon.companies.Company
import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rejection
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Holder
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.logistics.ShipRequest
import app.many.economytycoon.markets.MarketKey
import app.many.economytycoon.markets.Party
import app.many.economytycoon.markets.Side
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.workforce.CohortKey

/** What a successful command produced. [created] carries the id of a new entity, when there is one. */
data class CommandResult(val created: Long? = null, val message: String = "")

/**
 * Applies commands to the economy. Every command is checked for who may give it and then handed to the
 * engine that owns the rule; the executor itself holds no state.
 */
class CommandExecutor(private val e: Economy) {

    private fun ok(created: Long? = null, message: String = "") = Outcome.ok(CommandResult(created, message))
    private fun <T> fail(rule: String, message: String): Outcome<T> = Outcome.reject(rule, message)

    fun authorize(actor: Actor, company: Company): Rejection? = when (actor) {
        is Actor.System -> null
        is Actor.Ai -> if (actor.company == company.id) null else Rejection(Rules.NOT_OWNER, "the AI may only act for its own company")
        is Actor.Player -> if (company.founder == Holder.player(actor.id)) null else Rejection(Rules.NOT_OWNER, "you do not run ${company.name}")
    }

    fun execute(actor: Actor, cmd: Command): Outcome<CommandResult> {
        val tick = e.tick
        if (cmd is Command.FoundCompany) return found(actor, cmd, tick)
        val id = cmd.company ?: return fail(Rules.INVALID_ARGUMENT, "command needs a company")
        val company = e.companies.company(id) ?: return fail(Rules.UNKNOWN_ENTITY, "unknown company")
        authorize(actor, company)?.let { return Outcome.Rejected(it) }
        if (company.status != CompanyStatus.ACTIVE && cmd !is Command.FileInsolvency && cmd !is Command.RespondOffer) {
            return fail(Rules.WRONG_STATE, "${company.name} is not trading")
        }
        return when (cmd) {
            is Command.FoundCompany -> fail(Rules.INVALID_ARGUMENT, "unreachable")
            is Command.Rename -> e.companies.rename(id, cmd.name).map { CommandResult() }
            is Command.SetHeadquarters -> {
                if (e.world.cityOrNull(cmd.city) == null) fail(Rules.UNKNOWN_ENTITY, "unknown city")
                else e.companies.setHeadquarters(id, cmd.city).map { CommandResult() }
            }
            is Command.Dissolve -> e.insolvency.dissolveVoluntarily(id, tick).map { e.retire(id); CommandResult(message = "dissolved") }

            is Command.SurveyDeposit -> survey(id, cmd)
            is Command.ClaimDeposit -> if (e.world.deposits.claim(cmd.deposit, id)) ok() else fail(Rules.WRONG_STATE, "deposit is unsurveyed, taken or exhausted")
            is Command.BuyParcel -> buyParcel(id, cmd)

            is Command.Build -> {
                cmd.parcel?.let { p ->
                    val parcel = e.world.land.get(p) ?: return fail(Rules.UNKNOWN_ENTITY, "unknown parcel")
                    if (parcel.owner != null && parcel.owner != id) return fail(Rules.NOT_OWNER, "parcel belongs to someone else")
                    if (e.property.encumbered(p)) return fail(Rules.WRONG_STATE, "the parcel is listed or let")
                }
                cmd.deposit?.let { d ->
                    val dep = e.world.deposits.get(d) ?: return fail(Rules.UNKNOWN_ENTITY, "unknown deposit")
                    if (dep.owner != id) return fail(Rules.NOT_OWNER, "claim the deposit first")
                }
                e.production.startConstruction(id, cmd.type, cmd.region, cmd.city, cmd.parcel, cmd.deposit, cmd.name, tick).map { CommandResult(it.id.value) }
            }
            is Command.CancelConstruction -> e.production.cancelConstruction(id, cmd.project).map { refund ->
                e.payer.credit(id, refund, "construction refund"); CommandResult()
            }
            is Command.DeliverMaterials -> deliver(id, cmd)
            is Command.ExpandStorage -> e.production.startStorageExpansion(id, cmd.facility, tick).map { CommandResult(it.id.value) }
            is Command.AddLine -> e.production.addLine(id, cmd.facility).map { CommandResult(it.id.value) }
            is Command.SetRecipe -> e.production.setRecipe(id, cmd.facility, cmd.line, cmd.recipe).map { CommandResult() }
            is Command.SetLineEnabled -> e.production.setLineEnabled(id, cmd.facility, cmd.line, cmd.enabled).map { CommandResult() }
            is Command.SetLinePriority -> e.production.setPriority(id, cmd.facility, cmd.line, cmd.priority).map { CommandResult() }
            is Command.SetSchedulePolicy -> e.production.setPolicy(id, cmd.facility, cmd.policy).map { CommandResult() }
            is Command.SetQuality -> e.production.setQualityAssurance(id, cmd.facility, app.many.economytycoon.core.Rate(cmd.levelPpm)).map { CommandResult() }
            is Command.InstallMachine -> e.production.installMachine(id, cmd.facility, cmd.line, cmd.type, tick).map { CommandResult(it.id.value) }
            is Command.RemoveMachine -> e.production.removeMachine(id, cmd.facility, cmd.line, cmd.type).map { v -> e.payer.credit(id, v, "machine resale"); CommandResult() }
            is Command.Maintain -> e.production.maintain(id, cmd.facility, cmd.budget).map { CommandResult(message = it.toString()) }
            is Command.DisposeWaste -> e.production.disposeWaste(id, cmd.facility).map { CommandResult(message = it.toString()) }
            is Command.Shutdown -> e.production.shutdown(id, cmd.facility).map { cancelFacilityOrders(id, cmd.facility.value); CommandResult() }
            is Command.Reopen -> e.production.reopen(id, cmd.facility).map { CommandResult() }
            is Command.Demolish -> demolish(id, cmd)

            is Command.SetStaffing -> staffing(id, cmd)
            is Command.SetBenefits -> { e.workforce.setBenefits(id, cmd.ppm); ok() }
            is Command.SetTraining -> {
                val f = e.production.facility(cmd.facility)
                if (f == null || f.owner != id) fail(Rules.NOT_OWNER, "not your facility")
                else { e.workforce.setTraining(CohortKey(id, f.id, cmd.role, f.region), cmd.ppm); ok() }
            }

            is Command.PlaceOrder -> placeOrder(id, cmd)
            is Command.CancelOrder -> cancelOrder(id, cmd)
            is Command.SetSellRule -> {
                val f = e.production.facility(cmd.rule.facility)
                if (f == null || f.owner != id) fail(Rules.NOT_OWNER, "not your facility")
                else e.companies.policies.setSell(id, cmd.rule).map { CommandResult() }
            }
            is Command.RemoveSellRule -> if (e.companies.policies.removeSell(id, cmd.facility, cmd.good)) ok() else fail(Rules.UNKNOWN_ENTITY, "no such rule")
            is Command.SetBuyRule -> {
                val f = e.production.facility(cmd.rule.facility)
                if (f == null || f.owner != id) fail(Rules.NOT_OWNER, "not your facility")
                else e.companies.policies.setBuy(id, cmd.rule).map { CommandResult() }
            }
            is Command.RemoveBuyRule -> if (e.companies.policies.removeBuy(id, cmd.facility, cmd.good)) ok() else fail(Rules.UNKNOWN_ENTITY, "no such rule")
            is Command.SetDividend -> { e.companies.policies.setDividend(id, cmd.policy); ok() }

            is Command.Borrow -> e.finance.borrow(tick, company.owner, cmd.amount, cmd.termTicks).map { CommandResult(it.id.value) }
            is Command.Repay -> repay(company, cmd)
            is Command.SetBudget -> { e.companies.budgets(id).set(cmd.department, cmd.perTick); ok() }

            is Command.StartResearch -> e.research.start(id, cmd.tech, tick).map { CommandResult() }
            is Command.QueueResearch -> e.research.enqueue(id, cmd.tech).map { CommandResult() }
            is Command.CancelResearch -> {
                val dq = e.research.dequeue(id, cmd.tech)
                if (dq) ok() else e.research.cancel(id, cmd.tech, tick).map { CommandResult() }
            }
            is Command.LicenseTech -> e.research.license(cmd.from, id, cmd.tech, tick).map { CommandResult(message = it.toString()) }

            is Command.Ship -> e.logistics.ship(
                ShipRequest(id, cmd.consignee ?: id, cmd.from, cmd.to, cmd.good, cmd.quantity, useFleet = cmd.useFleet), tick,
            ).map { CommandResult(it.id.value) }
            is Command.MoveLocal -> e.logistics.moveLocal(id, cmd.from, cmd.to, cmd.good, cmd.quantity).map { CommandResult(message = it.toString()) }
            is Command.BuyVehicle -> e.logistics.buyVehicle(id, cmd.type, cmd.city, tick).map { CommandResult(it.id.value) }
            is Command.SellVehicle -> e.logistics.sellVehicle(id, cmd.vehicle).map { v -> e.payer.credit(id, v, "vehicle resale"); CommandResult() }
            is Command.RepairVehicle -> e.logistics.repairVehicle(id, cmd.vehicle).map { CommandResult() }
            is Command.LeaseWarehouse -> e.logistics.leaseWarehouse(id, cmd.city, cmd.capacity, tick).map { CommandResult(it.id.value) }
            is Command.CloseWarehouse -> e.logistics.closeWarehouse(id, cmd.warehouse).map { CommandResult() }

            is Command.ProposeContract -> proposeContract(id, cmd, tick)
            is Command.AcceptContract -> acceptContract(id, cmd, tick)
            is Command.DeclineContract -> e.contracts.decline(cmd.contract, id, tick).map { CommandResult() }
            is Command.CancelContract -> cancelContract(id, cmd, tick)

            is Command.ListCompany -> e.exchangeRunner.list(id, cmd.floatPpm, tick).map { CommandResult(message = "raised ${it.raised}") }
            is Command.DelistCompany -> e.exchangeRunner.delist(id, tick).map { CommandResult(message = "bought out the public for $it") }
            is Command.BuyShares -> e.exchangeRunner.buy(id, cmd.issuer, cmd.shares, tick).map { CommandResult(message = "paid ${it.net}") }
            is Command.SellShares -> e.exchangeRunner.sell(id, cmd.issuer, cmd.shares, tick).map { CommandResult(message = "received ${it.net}") }
            is Command.BuybackShares -> e.exchangeRunner.buyBack(id, cmd.shares, tick).map { CommandResult(message = "paid ${it.gross}") }

            is Command.BidTender -> e.tenderRunner.bid(id, cmd.tender, cmd.facility, cmd.unitPrice, tick).map { CommandResult(it.sequence, message = "bid placed") }
            is Command.WithdrawBid -> e.tenderRunner.withdraw(id, cmd.tender).map { CommandResult() }
            is Command.ApplyGrant -> e.grantRunner.apply(id, cmd.program, tick).map { CommandResult(message = "applied, starting from ${it.baseline}") }
            is Command.WithdrawGrant -> e.grantRunner.withdraw(id, cmd.program).map { CommandResult() }
            is Command.CreateCampaign -> e.marketingRunner.create(id, cmd, tick)
            is Command.PauseCampaign -> e.marketingRunner.pause(id, cmd.campaign)
            is Command.ResumeCampaign -> e.marketingRunner.resume(id, cmd.campaign, tick)
            is Command.ExtendCampaign -> e.marketingRunner.extend(id, cmd.campaign, cmd.extraDays, tick)
            is Command.SetCampaignBudget -> e.marketingRunner.setBudget(id, cmd.campaign, cmd.dailyBudget, tick)
            is Command.RefreshCreative -> e.marketingRunner.refresh(id, cmd.campaign, cmd.tier, tick)
            is Command.CancelCampaign -> e.marketingRunner.cancel(id, cmd.campaign, tick)
            is Command.StartAbTest -> e.marketingRunner.startTest(id, cmd, tick)
            is Command.LaunchPromotion -> e.marketingRunner.launchPromotion(id, cmd, tick)
            is Command.CancelPromotion -> e.marketingRunner.cancelPromotion(id, cmd.promotion, tick)
            is Command.RespondDemand -> e.unionRunner.respond(id, cmd.demand, cmd.response, cmd.counterPpm, tick).map { CommandResult(message = when (it) { is app.many.economytycoon.unions.UnionEvent.Struck -> "the workers walk out"; is app.many.economytycoon.unions.UnionEvent.Agreed -> "agreed"; else -> "the union backs down" }) }
            is Command.ListParcel -> e.propertyRunner.list(id, cmd.parcel, cmd.ask, tick).map { CommandResult(message = "listed for ${cmd.ask}") }
            is Command.DelistParcel -> e.propertyRunner.delist(id, cmd.parcel).map { CommandResult() }
            is Command.BuyListed -> e.propertyRunner.buy(id, cmd.parcel, tick).map { CommandResult(message = "bought for ${it.price}") }
            is Command.OfferLease -> e.propertyRunner.offerLease(id, cmd.parcel, cmd.termDays, cmd.monthlyRent, tick).map { CommandResult(message = "offered at ${cmd.monthlyRent} a month") }
            is Command.WithdrawLease -> e.propertyRunner.withdrawOffer(id, cmd.parcel).map { CommandResult() }
            is Command.EndLease -> e.propertyRunner.endLease(id, cmd.parcel, tick).map { CommandResult(message = "paid $it to end the lease") }
            is Command.OpenFuture -> e.futuresRunner.open(id, cmd.good, cmd.side, cmd.contracts, cmd.tenor, tick).map { CommandResult(it.id.value, message = "opened at ${it.entry}") }
            is Command.CloseFuture -> e.futuresRunner.close(id, app.many.economytycoon.core.FuturesId(cmd.position), tick).map { CommandResult(message = "closed with a result of $it") }
            is Command.AddMargin -> e.futuresRunner.addMargin(id, app.many.economytycoon.core.FuturesId(cmd.position), cmd.amount, tick).map { CommandResult(message = "margin is now $it") }
            is Command.SubscribeBond -> e.bondRunner.subscribe(id, cmd.bond, cmd.units, tick).map { CommandResult(message = "subscribed for ${cmd.units} units") }
            is Command.TradeBond -> e.bondRunner.trade(id, cmd.bond, cmd.units, cmd.buy, tick).map { CommandResult(message = if (cmd.buy) "bonds bought" else "bonds sold") }
            is Command.LobbyLaw -> e.regulationRunner.lobby(id, cmd.law, cmd.support, cmd.amount, tick).map { CommandResult(message = if (cmd.support) "lobbying for the law" else "lobbying against the law") }

            is Command.BuyPolicy -> e.riskRunner.buy(id, cmd.facility, cmd.level, cmd.coveragePpm, cmd.deductible, tick).map { CommandResult(it.id.value, message = "premium ${it.premium}") }
            is Command.CancelPolicy -> e.riskRunner.cancel(id, cmd.policy, tick).map { CommandResult() }

            is Command.MakeOffer -> e.acquisitions.makeOffer(id, cmd.target, cmd.shares, cmd.pricePerShare, tick).map { CommandResult(it.id) }
            is Command.RespondOffer -> e.acquisitions.respond(id, cmd.offer, cmd.accept, tick).map { r ->
                if (r != null) e.afterTakeover(r)
                CommandResult()
            }
            is Command.WithdrawOffer -> e.acquisitions.withdraw(id, cmd.offer).map { CommandResult() }
            is Command.FileInsolvency -> e.insolvency.open(id, tick).map { CommandResult() }
        }
    }

    // ---- handlers ------------------------------------------------------------------------------

    private fun proposeContract(id: CompanyId, cmd: Command.ProposeContract, tick: Tick): Outcome<CommandResult> {
        val other = e.companies.company(cmd.counterparty) ?: return fail(Rules.UNKNOWN_ENTITY, "unknown company")
        if (other.status != CompanyStatus.ACTIVE) return fail(Rules.WRONG_STATE, "${other.name} is not trading")
        val facility = e.production.facility(cmd.facility)
        if (facility == null || facility.owner != id) return fail(Rules.NOT_OWNER, "not your facility")
        if (facility.status == FacilityStatus.DEMOLISHED) return fail(Rules.WRONG_STATE, "that facility has been demolished")
        val good = e.content.goodOrNull(cmd.good) ?: return fail(Rules.UNKNOWN_ENTITY, "unknown good")
        val terms = app.many.economytycoon.contracts.ContractTerms(good.id, cmd.quantity, app.many.economytycoon.core.Price(cmd.unitPrice), cmd.intervalTicks, cmd.deliveries)
        e.contracts.checkTerms(terms, good.referencePrice.perUnit)?.let { return Outcome.Rejected(it) }
        return e.contracts.propose(id, cmd.counterparty, cmd.side, cmd.facility, terms, tick).map { CommandResult(it.id.value) }
    }

    private fun acceptContract(id: CompanyId, cmd: Command.AcceptContract, tick: Tick): Outcome<CommandResult> {
        val facility = e.production.facility(cmd.facility)
        if (facility == null || facility.owner != id) return fail(Rules.NOT_OWNER, "not your facility")
        if (facility.status == FacilityStatus.DEMOLISHED) return fail(Rules.WRONG_STATE, "that facility has been demolished")
        return e.contracts.accept(cmd.contract, id, cmd.facility, tick).map { CommandResult(it.id.value) }
    }

    private fun cancelContract(id: CompanyId, cmd: Command.CancelContract, tick: Tick): Outcome<CommandResult> {
        val c = e.contracts.get(cmd.contract) ?: return fail(Rules.UNKNOWN_ENTITY, "unknown contract")
        val wasActive = c.isActive
        return e.contracts.cancel(cmd.contract, id, tick).map {
            if (wasActive) {
                val fee = e.contracts.cancelFee(c)
                val paid = e.contractRunner.penalise(c, id, c.other(id), fee, tick, "contract cancelled")
                e.contracts.addPenalty(c, paid)
            }
            CommandResult()
        }
    }

    private fun found(actor: Actor, cmd: Command.FoundCompany, tick: Tick): Outcome<CommandResult> {
        if (e.world.countries().none { it.id == cmd.country }) return fail(Rules.UNKNOWN_ENTITY, "unknown country")
        val founder = when (actor) {
            is Actor.Player -> Holder.player(actor.id)
            is Actor.Ai -> Holder("ai", actor.company.value)
            is Actor.System -> Holder("system", 0)
        }
        if (actor is Actor.Player && cmd.kind != CompanyKind.PLAYER) return fail(Rules.REGULATION, "players can only found player companies")
        val gov = e.governmentOf(cmd.country)
        val result = e.companies.found(cmd.name, cmd.kind, cmd.country, cmd.hq, founder, cmd.capital, gov, tick)
        return result.map { c ->
            e.fundNewCompany(c, cmd.capital)
            CommandResult(c.id.value)
        }
    }

    private fun survey(id: CompanyId, cmd: Command.SurveyDeposit): Outcome<CommandResult> {
        val d = e.world.deposits.get(cmd.deposit) ?: return fail(Rules.UNKNOWN_ENTITY, "unknown deposit")
        if (d.surveyed) return fail(Rules.WRONG_STATE, "already surveyed")
        val cost = Money.units(5_000)
        if (!e.payer.charge(id, cost, "survey")) return fail(Rules.INSUFFICIENT_FUNDS, "cannot afford the survey")
        e.world.deposits.survey(cmd.deposit)
        return ok()
    }

    private fun buyParcel(id: CompanyId, cmd: Command.BuyParcel): Outcome<CommandResult> {
        val parcel = e.world.land.get(cmd.parcel) ?: return fail(Rules.UNKNOWN_ENTITY, "unknown parcel")
        if (parcel.owner != null) return fail(Rules.WRONG_STATE, "parcel is not for sale")
        val price = e.propertyRunner.appraise(parcel)
        if (price.raw <= 0L) return fail(Rules.WRONG_STATE, "parcel is not for sale")
        if (!e.payer.charge(id, price, "land purchase")) return fail(Rules.INSUFFICIENT_FUNDS, "cannot afford the land")
        e.world.land.assign(cmd.parcel, id, price)
        e.property.noteAcquired(cmd.parcel, e.tick)
        return ok()
    }

    private fun deliver(id: CompanyId, cmd: Command.DeliverMaterials): Outcome<CommandResult> {
        val f = e.production.facility(cmd.from)
        if (f == null || f.owner != id) return fail(Rules.NOT_OWNER, "not your facility")
        if (f.inventory.available(cmd.good) < cmd.quantity) return fail(Rules.INSUFFICIENT_STOCK, "not enough ${cmd.good.key} on hand")
        return e.production.deliverMaterials(id, cmd.project, cmd.good, cmd.quantity).flatMap { taken ->
            f.inventory.remove(cmd.good, taken).map { CommandResult(message = taken.toString()) }
        }
    }

    private fun demolish(id: CompanyId, cmd: Command.Demolish): Outcome<CommandResult> {
        val f = e.production.facility(cmd.facility)
        if (f != null) cancelFacilityOrders(id, f.id.value)
        return e.production.demolish(id, cmd.facility).map { d ->
            e.payer.credit(id, d.refund, "demolition refund")
            e.companies.policies.dropFacility(id, cmd.facility)
            CommandResult(message = "written off ${d.writtenOffStock}")
        }
    }

    private fun staffing(id: CompanyId, cmd: Command.SetStaffing): Outcome<CommandResult> {
        val f = e.production.facility(cmd.facility)
        if (f == null || f.owner != id || f.status == FacilityStatus.DEMOLISHED) return fail(Rules.NOT_OWNER, "not your facility")
        val key = CohortKey(id, f.id, cmd.role, f.region)
        if (e.catalog.roleOrNull(cmd.role) == null) return fail(Rules.UNKNOWN_ENTITY, "unknown role ${cmd.role.key}")
        val r = e.workforce.setTarget(key, cmd.target)
        if (r is Outcome.Rejected) return Outcome.Rejected(r.rejection)
        if (cmd.wage != null) {
            val w = e.workforce.setWage(key, cmd.wage)
            if (w is Outcome.Rejected) return Outcome.Rejected(w.rejection)
        }
        return ok()
    }

    private fun placeOrder(id: CompanyId, cmd: Command.PlaceOrder): Outcome<CommandResult> {
        val f = e.production.facility(cmd.facility)
        if (f == null || f.owner != id || f.status == FacilityStatus.DEMOLISHED) return fail(Rules.NOT_OWNER, "not your facility")
        val key = MarketKey(f.region, cmd.good)
        val party = Party.company(id)
        if (cmd.side == Side.SELL) {
            val reserved = f.inventory.reserve(cmd.good, cmd.quantity)
            if (reserved is Outcome.Rejected) return Outcome.Rejected(reserved.rejection)
            val placed = e.markets.placeOrder(party, key, Side.SELL, cmd.limit, cmd.quantity, cmd.ttl, f.id.value, e.tick)
            if (placed is Outcome.Rejected) { f.inventory.release(cmd.good, cmd.quantity); return Outcome.Rejected(placed.rejection) }
            return ok(placed.getOrThrow().id.value)
        }
        return e.markets.placeOrder(party, key, Side.BUY, cmd.limit, cmd.quantity, cmd.ttl, f.id.value, e.tick).map { CommandResult(it.id.value) }
    }

    private fun cancelOrder(id: CompanyId, cmd: Command.CancelOrder): Outcome<CommandResult> {
        val r = e.markets.cancelOrder(Party.company(id), cmd.order)
        return r.map { o -> e.releaseReservation(o); CommandResult() }
    }

    private fun repay(company: Company, cmd: Command.Repay): Outcome<CommandResult> {
        if (cmd.amount.raw <= 0L) return fail(Rules.INVALID_ARGUMENT, "amount must be positive")
        if (cmd.loan != null) {
            val loan = e.finance.loans.get(cmd.loan) ?: return fail(Rules.UNKNOWN_ENTITY, "unknown loan")
            if (loan.borrower != company.owner) return fail(Rules.NOT_OWNER, "not your loan")
            return e.finance.loans.prepay(e.tick, cmd.loan, cmd.amount).map { CommandResult(message = it.toString()) }
        }
        var left = cmd.amount
        var paid = Money.ZERO
        for (loan in e.finance.loans.activeFor(company.owner).sortedBy { it.id }) {
            if (left.raw <= 0L) break
            val r = e.finance.loans.prepay(e.tick, loan.id, Money.min(left, loan.outstanding))
            if (r is Outcome.Ok) { paid += r.value; left -= r.value }
        }
        return if (paid.raw > 0L) ok(message = paid.toString()) else fail(Rules.INSUFFICIENT_FUNDS, "nothing could be repaid")
    }

    private fun cancelFacilityOrders(company: CompanyId, facility: Long) {
        val mine = e.markets.openOrders(Party.company(company)).filter { it.source == facility }
        for (o in mine) { e.markets.cancelOrder(Party.company(company), o.id); e.releaseReservation(o) }
    }
}
