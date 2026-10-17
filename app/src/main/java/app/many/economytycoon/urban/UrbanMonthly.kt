package app.many.economytycoon.urban

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.SettlementIntent

internal class MonthlyResult(
    val report: BudgetReport,
    val score: LiveabilityScore,
    val notices: List<UrbanNotice>,
    val intents: List<SettlementIntent>,
)

/**
 * The monthly cycle of a city. The order matters and is fixed: events, surfaces and land value, housing and rents,
 * development, gentrification, traffic, ageing, finances, politics, liveability.
 */
internal object UrbanMonthly {
    fun run(state: UrbanState, plan: CityPlan, inputs: CityInputs, now: Tick): MonthlyResult {
        val params = state.params
        val notices = ArrayList<UrbanNotice>()
        plan.inputs = inputs
        runEvents(state, plan, now, notices)

        val analysis = UrbanAnalysis.analyse(plan, params)
        for (d in plan.districts) d.landValuePerSqm = analysis.landValue.districtAverage(plan.geometry, d.index)

        settleHousing(plan, analysis, params)

        val boom = plan.events.boomPpm(now)
        for (d in plan.districts) {
            Development.step(d, params, now, boom, frozen = plan.events.developmentFrozen(d.index, now))
        }

        val aid = plan.budget.quality(Service.HOUSING_AID)
        for (d in plan.districts) {
            Gentrification.step(d, aid)
            d.recordRent()
        }

        val traffic = TrafficAssignment.assign(plan.network, plan.districts, params)
        applyTraffic(plan, traffic)

        for (d in plan.districts) {
            d.stock.ageOneMonth()
            d.stock.maintain((1_000L + (params.targetVacancyPpm - d.vacancyPpm) / 100L).coerceIn(500L, 2_500L))
        }

        val settlements = ArrayList<ConcessionSettlement>()
        val report = settleFinances(plan, traffic, params, now, settlements, notices)

        plan.council.updateMood(plan.districts.sumOf { it.backlashPpm } / plan.districts.size)
        if (now.value - plan.council.lastElection.value >= params.monthsBetweenCouncilElections * Scale.MONTH_DAYS) {
            plan.council.holdElection(now, state.stream("election", plan.city))
            notices += UrbanNotice(plan.city, now, NoticeKind.ELECTION, "${plan.name} elected a new council; ${plan.council.growthSeats()} of ${plan.council.seats.size} members favour growth.")
        }

        val score = Liveability.score(plan, UrbanAnalysis.analyse(plan, params), params)
        plan.liveabilityHistory += score.index
        while (plan.liveabilityHistory.size > CityPlan.HISTORY) plan.liveabilityHistory.removeAt(0)
        plan.lastMonthly = now
        plan.events.prune(now, 720L)

        val intents = settlements.flatMap { UrbanLedger.concessionMonth(plan.country, it, now) }
        return MonthlyResult(report, score, notices, intents)
    }

    private fun runEvents(state: UrbanState, plan: CityPlan, now: Tick, notices: MutableList<UrbanNotice>) {
        for (e in plan.events.all()) {
            if (!e.ended && now >= e.endsAt) UrbanEffects.eventEnded(plan, e)
        }
        val climate = EventClimate(
            plan.inputs.coastal, plan.budget.quality(Service.FIRE), plan.budget.quality(Service.SANITATION),
            plan.districts.any { it.zone == ZoneUse.INDUSTRIAL && it.floorArea > 0L }, plan.network.activeLines().isNotEmpty(),
        )
        val rolled = EventGenerator.roll(now, plan.districts, climate, plan.events, state.stream("events", plan.city), { state.ids.event() }, plan.network.lines())
        val backlashes = EventGenerator.backlashes(now, plan.districts, plan.events) { state.ids.event() }
        for (e in rolled + backlashes) {
            plan.events.add(e)
            UrbanEffects.eventStarted(plan, e)
            val name = plan.districts.getOrNull(e.district)?.name
            notices += UrbanNotice(plan.city, now, NoticeKind.EVENT, EventGenerator.describe(e, name), e.id.value)
        }
    }

    /** Places residents, sets vacancy and moves rents. */
    private fun settleHousing(plan: CityPlan, analysis: CityAnalysis, params: UrbanParams) {
        val ds = plan.districts
        val n = ds.size
        val caps = LongArray(n) { Zoning.capacity(ds[it].zone, ds[it].floorArea, params) }
        val totalCap = caps.sum()
        var rentWeighted = 0L
        for (d in ds) rentWeighted += d.rentPerSqm.raw * caps[d.index]
        val meanRent = if (totalCap == 0L) 1L else (rentWeighted / totalCap).coerceAtLeast(1L)
        val weights = LongArray(n) { i ->
            if (caps[i] == 0L) 0L else Housing.attractiveness(analysis.factors[i], mulDiv(ds[i].rentPerSqm.raw, Scale.PPM, meanRent))
        }
        val alloc = Housing.allocate(plan.inputs.population, weights, caps)
        var landWeighted = 0L
        var landTotal = 0L
        for (d in ds) { landWeighted += d.landValuePerSqm.raw * d.landSqm; landTotal += d.landSqm }
        val meanLand = if (landTotal == 0L) 1L else (landWeighted / landTotal).coerceAtLeast(1L)
        val anchor = Housing.anchorRent(plan.inputs.averageWage, params)
        for (d in ds) {
            val i = d.index
            d.residents = alloc.placed[i]
            d.workersLiving = mulDiv(d.residents, params.participationPpm, Scale.PPM)
            d.jobs = Zoning.jobs(d.zone, d.floorArea, params)
            d.vacancyPpm = if (caps[i] == 0L) params.targetVacancyPpm else Housing.vacancy(caps[i], d.residents)
            val ratio = mulDiv(d.landValuePerSqm.raw, Scale.PPM, meanLand)
            d.rentPerSqm = Housing.stepRent(d.rentPerSqm, Housing.targetRent(anchor, ratio, d.vacancyPpm, params), params)
        }
    }

    private fun applyTraffic(plan: CityPlan, r: TrafficReport) {
        plan.traffic = TrafficSummary.of(r)
        for (d in plan.districts) {
            d.accessibilityPermille = r.accessibilityPermille[d.index]
            d.averageCommuteSeconds = r.districtCommuteSeconds[d.index]
            val worst = plan.network.roadsAt(d.index).maxOfOrNull { it.volumeCapacityPpm } ?: 0L
            d.trafficLoadPermille = (worst / 1000L).toInt().coerceIn(0, 1000)
        }
        for (l in plan.network.lines()) {
            val daily = r.lineDailyRiders[l.id] ?: 0L
            l.monthlyRiders = daily * Scale.MONTH_DAYS
            l.lifetimeRiders += l.monthlyRiders
        }
    }

    private fun settleFinances(
        plan: CityPlan, traffic: TrafficReport, params: UrbanParams, now: Tick, settlements: MutableList<ConcessionSettlement>,
        notices: MutableList<UrbanNotice>,
    ): BudgetReport {
        val operated = plan.concessions.operating().mapNotNull { it.line }.toSet()
        var fares = Money.ZERO
        var transitCost = Money.ZERO
        for (l in plan.network.lines()) {
            if (l.id in operated || !l.isActive) continue
            fares += l.fare * l.monthlyRiders
            transitCost += l.monthlyOperatingCost()
        }
        var share = Money.ZERO
        var payments = Money.ZERO
        val availability = plan.events.availabilityPermille(now)
        for (c in plan.concessions.live()) {
            val vehicles = c.road?.let { traffic.tollVehicles[it] } ?: 0L
            val riderCharges = c.line?.let { id -> plan.network.line(id)?.let { it.fare * it.monthlyRiders } } ?: Money.ZERO
            val s = plan.concessions.settleMonth(c.id, vehicles, availability, riderCharges) ?: continue
            settlements += s
            share += s.cityShare
            payments += s.cityPays
        }
        val inputs = BudgetInputs(
            population = plan.population,
            monthlyWages = plan.monthlyWages(params),
            assessedValue = plan.assessedValue(params),
            businessFloorArea = plan.businessFloorArea(),
            transferPerResident = plan.inputs.transferPerResident,
            fares = fares,
            tollShare = share,
            fees = plan.feesThisMonth,
            transitOperatingCost = transitCost,
            availabilityPayments = payments,
        )
        val report = plan.budget.settleMonth(inputs, plan.capitalThisMonth, params)
        plan.capitalThisMonth = Money.ZERO
        plan.feesThisMonth = Money.ZERO
        if (report.fundingRatioPpm < 900_000L) {
            notices += UrbanNotice(plan.city, now, NoticeKind.SERVICE_CUT, "${plan.name} could not fund its services in full and cut them to ${report.fundingRatioPpm / 10_000L}% of the planned level.")
        }
        if (plan.budget.consecutiveDeficitMonths == 6) {
            notices += UrbanNotice(plan.city, now, NoticeKind.BUDGET_CRISIS, "${plan.name} has run a deficit for six months in a row.")
        }
        return report
    }
}
