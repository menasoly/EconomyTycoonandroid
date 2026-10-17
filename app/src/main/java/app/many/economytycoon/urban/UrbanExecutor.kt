package app.many.economytycoon.urban

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick

/** Validates and applies urban commands. Reached only through [UrbanService]. */
internal class UrbanExecutor(private val state: UrbanState) {
    private val params get() = state.params

    fun execute(cmd: UrbanCommand, now: Tick): Outcome<UrbanReceipt> {
        val plan = state.plan(cmd.city) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no urban plan for that city")
        return when (cmd) {
            is UrbanCommand.SetTaxRates -> ok("Tax rates set.") { plan.budget.setRates(cmd.rates) }
            is UrbanCommand.SetServiceLevel -> plan.budget.setLevel(cmd.service, cmd.level).map { UrbanReceipt("${cmd.service.label} level set to ${cmd.level}.") }
            is UrbanCommand.SetDensityRules -> district(plan, cmd.district) { d ->
                d.rules = cmd.rules
                UrbanReceipt("Density limits of ${d.name} changed.")
            }
            is UrbanCommand.SetTenantProtection -> if (cmd.permille !in 0..1000) {
                Outcome.reject(Rules.INVALID_ARGUMENT, "protection is between 0 and 1000")
            } else district(plan, cmd.district) { d ->
                d.tenantProtectionPermille = cmd.permille
                UrbanReceipt("Tenant protection in ${d.name} set to ${cmd.permille}.")
            }
            is UrbanCommand.AddRoad -> plan.network.addRoad(state.ids.link(), cmd.a, cmd.b, cmd.roadClass, cmd.lanes).map { UrbanReceipt("Road built.", it.id.value) }
            is UrbanCommand.PlanLine -> plan.network.planLine(state.ids.line(), cmd.name, cmd.mode, cmd.stops, cmd.headwaySeconds, cmd.fare, cmd.dedicatedLane)
                .map { UrbanReceipt("${it.name} planned.", it.id.value) }
            is UrbanCommand.SetLineHeadway -> plan.network.setHeadway(cmd.line, cmd.headwaySeconds).map { UrbanReceipt("Headway of ${it.name} set to ${cmd.headwaySeconds} seconds.") }
            is UrbanCommand.SetLineFare -> plan.network.setFare(cmd.line, cmd.fare).map { UrbanReceipt("Fare on ${it.name} changed.") }
            is UrbanCommand.ToggleLine -> toggleLine(plan, cmd)
            is UrbanCommand.StartProject -> startProject(plan, cmd, now)
            is UrbanCommand.CancelProject -> plan.projects.cancel(cmd.project, now).map {
                val p = plan.projects.get(cmd.project)
                if (p?.kind == ProjectKind.TRANSIT_LINE) p.line?.let { id -> plan.network.line(id)?.status = LineStatus.PLANNED }
                UrbanReceipt("Project cancelled after spending ${p?.spent}.")
            }
            is UrbanCommand.Borrow -> plan.budget.borrow(cmd.amount, params).map { UrbanReceipt("Borrowed ${cmd.amount}.") }
            is UrbanCommand.Repay -> plan.budget.repay(cmd.amount).map { UrbanReceipt("Repaid ${cmd.amount}.") }
            is UrbanCommand.TenderConcession -> tender(plan, cmd, now)
            is UrbanCommand.TerminateConcession -> terminate(plan, cmd, now)
            is UrbanCommand.FilePetition -> file(plan, cmd, now)
            is UrbanCommand.WithdrawPetition -> plan.petitions.withdraw(cmd.petition, cmd.applicant, now).map { UrbanReceipt("Petition withdrawn.") }
            is UrbanCommand.LobbyPetition -> lobby(plan, cmd, now)
            is UrbanCommand.BidConcession -> plan.concessions.bid(cmd.concession, cmd.company, cmd.discountPpm, now).map { UrbanReceipt("Bid placed.") }
        }
    }

    /** What a company must be able to pay before the command is accepted. */
    fun companyCost(cmd: UrbanCommand): Money = when (cmd) {
        is UrbanCommand.FilePetition -> {
            val plan = state.plan(cmd.city)
            val d = plan?.districts?.getOrNull(cmd.district)
            if (cmd.applicant == null || d == null) Money.ZERO else Zoning.rezoneCost(d.landSqm, d.zone, cmd.toZone, params)
        }
        is UrbanCommand.LobbyPetition -> cmd.amount
        else -> Money.ZERO
    }

    private fun ok(message: String, action: () -> Unit): Outcome<UrbanReceipt> { action(); return Outcome.ok(UrbanReceipt(message)) }

    private fun district(plan: CityPlan, index: Int, action: (District) -> UrbanReceipt): Outcome<UrbanReceipt> {
        val d = plan.districts.getOrNull(index) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such district")
        return Outcome.ok(action(d))
    }

    private fun toggleLine(plan: CityPlan, cmd: UrbanCommand.ToggleLine): Outcome<UrbanReceipt> {
        val line = plan.network.line(cmd.line) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such line")
        return when (line.status) {
            LineStatus.ACTIVE -> plan.network.setStatus(cmd.line, LineStatus.SUSPENDED).map { UrbanReceipt("${line.name} suspended.") }
            LineStatus.SUSPENDED -> plan.network.setStatus(cmd.line, LineStatus.ACTIVE).map { UrbanReceipt("${line.name} back in service.") }
            else -> Outcome.reject(Rules.WRONG_STATE, "that line is not in service")
        }
    }

    private fun startProject(plan: CityPlan, cmd: UrbanCommand.StartProject, now: Tick): Outcome<UrbanReceipt> {
        val d = cmd.district?.let { plan.districts.getOrNull(it) }
        if (cmd.district != null && d == null) return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such district")
        val needsDistrict = cmd.kind in setOf(ProjectKind.PARK, ProjectKind.SCHOOL, ProjectKind.HOSPITAL, ProjectKind.CIVIC_CENTRE, ProjectKind.HOUSING_PROGRAM)
        if (needsDistrict && d == null) return Outcome.reject(Rules.INVALID_ARGUMENT, "${cmd.kind.label.lowercase()} needs a district")
        var size = cmd.size
        val budget: Money
        when (cmd.kind) {
            ProjectKind.TRANSIT_LINE -> {
                val line = cmd.line?.let { plan.network.line(it) } ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such line")
                if (line.status != LineStatus.PLANNED) return Outcome.reject(Rules.WRONG_STATE, "only a planned line can be built")
                if (plan.concessions.all().any { it.line == line.id && (it.isLive || it.status == ConcessionStatus.TENDER) }) {
                    return Outcome.reject(Rules.DUPLICATE, "that line is part of a concession")
                }
                budget = line.buildCost()
            }
            ProjectKind.ROAD_UPGRADE -> {
                val road = cmd.road?.let { plan.network.road(it) } ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such road")
                budget = ProjectEstimates.estimate(cmd.kind, 0L, params, road.lanes)
            }
            ProjectKind.PARK -> {
                if (size <= 0L) size = d!!.landSqm / 4L
                budget = ProjectEstimates.estimate(cmd.kind, size, params)
            }
            ProjectKind.CIVIC_CENTRE -> {
                if (size <= 0L) size = 20_000L
                budget = ProjectEstimates.estimate(cmd.kind, size, params)
            }
            ProjectKind.HOUSING_PROGRAM -> {
                if (size <= 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "state how much floor area to build")
                if (size > d!!.headroom) return Outcome.reject(Rules.LIMIT_EXCEEDED, "the density limit of ${d.name} leaves room for only ${d.headroom} square metres")
                budget = ProjectEstimates.estimate(cmd.kind, size, params)
            }
            else -> budget = ProjectEstimates.estimate(cmd.kind, size, params)
        }
        val upfront = Money(budget.raw * (ProjectPhase.PLANNING.share + ProjectPhase.DESIGN.share) / Scale.PPM)
        if (plan.budget.cash < upfront) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "the city cannot pay for the first phases")
        val result = plan.projects.start(state.ids.project(), cmd.kind, cmd.name, cmd.district, cmd.line, cmd.road, size, budget, now, cmd.fixedPrice)
        return result.map {
            if (cmd.kind == ProjectKind.TRANSIT_LINE) cmd.line?.let { id -> plan.network.line(id)?.status = LineStatus.UNDER_CONSTRUCTION }
            UrbanReceipt("${it.name} started with a budget of $budget.", it.id.value)
        }
    }

    private fun tender(plan: CityPlan, cmd: UrbanCommand.TenderConcession, now: Tick): Outcome<UrbanReceipt> {
        if (cmd.road != null && plan.network.road(cmd.road) == null) return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such road")
        if (cmd.line != null && plan.network.line(cmd.line) == null) return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such line")
        if (cmd.line != null && plan.projects.running().any { it.line == cmd.line }) return Outcome.reject(Rules.DUPLICATE, "that line is already being built by the city")
        return plan.concessions.tender(
            state.ids.concession(), cmd.kind, cmd.name, cmd.road, cmd.line, cmd.model, cmd.termMonths, cmd.constructionMonths, cmd.capex,
            cmd.toll, cmd.availabilityMonthly, cmd.cityShareBasePpm, cmd.trafficGuarantee, now,
        ).map { UrbanReceipt("${it.name} put out to tender.", it.id.value) }
    }

    private fun terminate(plan: CityPlan, cmd: UrbanCommand.TerminateConcession, now: Tick): Outcome<UrbanReceipt> {
        val c = plan.concessions.get(cmd.concession) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such concession")
        val company = c.concessionaire
        return plan.concessions.terminate(cmd.concession, now).map { owed ->
            c.road?.let { plan.network.setToll(it, Money.ZERO) }
            plan.budget.spendCapital(Money.min(owed, plan.budget.cash))
            val intents = if (company == null) emptyList() else UrbanLedger.termination(plan.country, c.id, company, owed, now)
            UrbanReceipt("${c.name} terminated; compensation of $owed is due.", c.id.value, intents)
        }
    }

    private fun file(plan: CityPlan, cmd: UrbanCommand.FilePetition, now: Tick): Outcome<UrbanReceipt> {
        val d = plan.districts.getOrNull(cmd.district) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such district")
        val fee = if (cmd.applicant == null) Money.ZERO else Zoning.rezoneCost(d.landSqm, d.zone, cmd.toZone, params)
        val id = state.ids.petition()
        return plan.petitions.file(id, d, cmd.kind, cmd.applicant, cmd.toZone, cmd.farMilli, cmd.floors, fee, now, params).map { p ->
            plan.feesThisMonth += fee
            val intents = cmd.applicant?.let { UrbanLedger.petitionFee(it, plan.country, p.id, fee, now) } ?: emptyList()
            UrbanReceipt("Petition filed for ${d.name}; the hearing is on day ${p.hearingAt.value}.", p.id.value, intents)
        }
    }

    private fun lobby(plan: CityPlan, cmd: UrbanCommand.LobbyPetition, now: Tick): Outcome<UrbanReceipt> =
        plan.petitions.lobby(cmd.petition, cmd.company, cmd.seat, cmd.amount, plan.council, params, now).map { e ->
            val sequence = plan.petitions.effortsFor(cmd.petition).size
            UrbanReceipt("Lobbying worth ${e.points} points arranged.", cmd.petition.value, UrbanLedger.lobbying(cmd.company, cmd.petition, sequence, e.amount, now))
        }
}
