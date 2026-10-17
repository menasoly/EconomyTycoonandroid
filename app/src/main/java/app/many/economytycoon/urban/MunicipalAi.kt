package app.many.economytycoon.urban

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** How a computer-controlled mayor leans. Derived from the council so that the same city always behaves the same way. */
data class MayorProfile(
    /** Appetite for growth, permille: high means upzoning and big projects. */
    val growth: Int,
    /** Caution with money, permille: high means taxes first, borrowing last. */
    val thrift: Int,
    /** Weight on green space and the environment, permille. */
    val green: Int,
) {
    companion object {
        fun of(plan: CityPlan): MayorProfile {
            val mean = plan.council.meanIdeology() // -500..500
            val names = plan.name.sumOf { it.code }
            return MayorProfile(
                growth = (500 + mean).coerceIn(0, 1000),
                thrift = (400 + (names % 400)).coerceIn(0, 1000),
                green = (700 - mean / 2).coerceIn(0, 1000),
            )
        }
    }
}

/**
 * The heuristic of a computer-controlled city government. Once a month it looks at the city and proposes at most
 * [MAX_COMMANDS] orders, most pressing first: keep the books balanced, answer anger, relieve housing and traffic, add
 * green space. It only reads; the caller executes the commands it likes. Everything is a fixed rule on integer state, so the
 * same plan always yields the same orders.
 */
object MunicipalAi {
    const val MAX_COMMANDS = 4

    fun decide(plan: CityPlan, analysis: CityAnalysis, score: LiveabilityScore, params: UrbanParams, now: Tick): List<UrbanCommand> {
        val profile = MayorProfile.of(plan)
        val out = ArrayList<UrbanCommand>()
        books(plan, profile, params, out)
        if (out.size < MAX_COMMANDS) answerAnger(plan, out)
        if (out.size < MAX_COMMANDS) housing(plan, profile, params, out)
        if (out.size < MAX_COMMANDS) traffic(plan, profile, params, out)
        if (out.size < MAX_COMMANDS) services(plan, analysis, params, out)
        if (out.size < MAX_COMMANDS) greenSpace(plan, profile, params, out)
        return out.take(MAX_COMMANDS)
    }

    private fun books(plan: CityPlan, p: MayorProfile, params: UrbanParams, out: MutableList<UrbanCommand>) {
        val b = plan.budget
        val monthly = b.totalServiceCost(plan.population)
        val rates = b.rates
        val lowCash = b.cash < monthly * 2L
        if (lowCash && b.consecutiveDeficitMonths >= 2 && rates.propertyPerYearPpm < TaxRates.MAX_PROPERTY_PPM - 1_000L) {
            out += UrbanCommand.SetTaxRates(plan.city, rates.copy(propertyPerYearPpm = rates.propertyPerYearPpm + 1_000L))
            return
        }
        if (lowCash && p.thrift < 600 && b.rating() > 450 && b.debt + monthly * 2L <= b.debtLimit(params) && b.debt.raw == 0L) {
            out += UrbanCommand.Borrow(plan.city, monthly * 2L)
            return
        }
        val rich = b.cash > monthly * 8L && b.consecutiveDeficitMonths == 0
        if (b.debt.isPositive && b.cash > monthly * 4L) {
            out += UrbanCommand.Repay(plan.city, Money.min(b.debt, b.cash - monthly * 3L))
            return
        }
        if (rich && rates.propertyPerYearPpm > 6_000L && p.thrift < 500) {
            out += UrbanCommand.SetTaxRates(plan.city, rates.copy(propertyPerYearPpm = rates.propertyPerYearPpm - 500L))
        }
    }

    private fun answerAnger(plan: CityPlan, out: MutableList<UrbanCommand>) {
        val angry = plan.districts.filter { it.backlashPpm > 300_000L && it.tenantProtectionPermille < 800 }.maxByOrNull { it.backlashPpm } ?: return
        out += UrbanCommand.SetTenantProtection(plan.city, angry.index, (angry.tenantProtectionPermille + 200).coerceAtMost(1000))
        if (plan.budget.level(Service.HOUSING_AID) < 700) out += UrbanCommand.SetServiceLevel(plan.city, Service.HOUSING_AID, plan.budget.level(Service.HOUSING_AID) + 150)
    }

    private fun housing(plan: CityPlan, p: MayorProfile, params: UrbanParams, out: MutableList<UrbanCommand>) {
        val report = Housing.report(plan.districts, plan.inputs.population, plan.inputs.averageWage, params)
        if (report.vacancyPpm >= 30_000L && report.unhoused == 0L) return
        if (p.growth < 350 || plan.petitions.open().size >= params.maxOpenPetitionsPerCity / 2) return
        val candidate = plan.districts
            .filter { it.residentialArea > 0L && it.headroom < it.maxFloorArea / 10L && it.backlashPpm < 300_000L && plan.petitions.openFor(it.index) == null }
            .filter { PetitionRules.neighbourCompatibility(it.zone, it.index, plan.geometry, plan.districts) >= 0 }
            .maxWithOrNull(compareBy<District>({ it.rentPerSqm.raw }, { -it.index }))
            ?: return
        val farMilli = candidate.rules.maxFarMilli + candidate.rules.maxFarMilli / 4
        val floors = candidate.rules.maxFloors + candidate.rules.maxFloors / 4 + 1
        out += UrbanCommand.FilePetition(plan.city, candidate.index, PetitionKind.VARIANCE, candidate.zone, farMilli, floors, null)
    }

    private fun traffic(plan: CityPlan, p: MayorProfile, params: UrbanParams, out: MutableList<UrbanCommand>) {
        for (l in plan.network.activeLines()) {
            if (l.loadRatioPpm > 900_000L && l.headwaySeconds > 150) out += UrbanCommand.SetLineHeadway(plan.city, l.id, (l.headwaySeconds * 4 / 5).coerceAtLeast(120))
            else if (l.loadRatioPpm in 1L..100_000L && l.headwaySeconds < 900 && l.monthlyRiders > 0L) out += UrbanCommand.SetLineHeadway(plan.city, l.id, l.headwaySeconds * 5 / 4)
            if (out.size >= MAX_COMMANDS) return
        }
        if (plan.traffic.congestionPpm < 250_000L || plan.projects.running().any { it.kind == ProjectKind.ROAD_UPGRADE }) return
        val worst = plan.network.roads().maxByOrNull { it.volumeCapacityPpm } ?: return
        if (worst.levelOfService < LevelOfService.E || worst.roadClass == RoadClass.HIGHWAY && worst.lanes >= 6) return
        val estimate = ProjectEstimates.estimate(ProjectKind.ROAD_UPGRADE, 0L, params, worst.lanes)
        if (plan.budget.cash < estimate) {
            if (plan.concessions.onRoad(worst.id) == null && p.growth > 400 && plan.concessions.tendering().isEmpty()) {
                out += UrbanCommand.TenderConcession(
                    plan.city, ConcessionKind.TOLL_ROAD, "${plan.districts[worst.a].name} bypass", worst.id, null, PaymentModel.TOLL, 300, 0,
                    Money.ZERO, Money.units(3), Money.ZERO, 250_000L, 0L,
                )
            }
            return
        }
        out += UrbanCommand.StartProject(plan.city, ProjectKind.ROAD_UPGRADE, "Widen ${plan.districts[worst.a].name} to ${plan.districts[worst.b].name}", null, null, worst.id, 0L, true)
    }

    private fun services(plan: CityPlan, analysis: CityAnalysis, params: UrbanParams, out: MutableList<UrbanCommand>) {
        val b = plan.budget
        val funded = (b.lastReport?.fundingRatioPpm ?: Scale.PPM) >= 950_000L
        if (!funded) {
            val dearest = Service.values().filter { b.level(it) > 300 }.maxByOrNull { b.serviceCost(it, plan.population).raw } ?: return
            out += UrbanCommand.SetServiceLevel(plan.city, dearest, b.level(dearest) - 100)
            return
        }
        if (b.cash < b.totalServiceCost(plan.population) * 4L) return
        val crime = plan.districts.maxOfOrNull { analysis.factors[it.index].crime } ?: 0
        if (crime > 350 && b.level(Service.POLICE) < 800) out += UrbanCommand.SetServiceLevel(plan.city, Service.POLICE, b.level(Service.POLICE) + 100)
        val dirt = plan.districts.maxOfOrNull { analysis.factors[it.index].pollution } ?: 0
        if (dirt > 350 && b.level(Service.SANITATION) < 800) out += UrbanCommand.SetServiceLevel(plan.city, Service.SANITATION, b.level(Service.SANITATION) + 100)
    }

    private fun greenSpace(plan: CityPlan, p: MayorProfile, params: UrbanParams, out: MutableList<UrbanCommand>) {
        if (plan.projects.runningOfKind(ProjectKind.PARK).isNotEmpty()) return
        val green = plan.districts.filter { it.zone == ZoneUse.GREEN }.sumOf { it.landSqm }
        if (plan.population == 0L || green / plan.population >= 12L || p.green < 400) return
        val target = plan.districts.filter { it.zone != ZoneUse.GREEN && it.residents > 0L && it.amenityBonus < 300 }
            .maxWithOrNull(compareBy<District>({ it.residents }, { -it.index })) ?: return
        val size = target.landSqm / 4L
        val cost = ProjectEstimates.estimate(ProjectKind.PARK, size, params)
        if (plan.budget.cash < cost * 3L) return
        out += UrbanCommand.StartProject(plan.city, ProjectKind.PARK, "${target.name} park", target.index, null, null, size, false)
    }
}
