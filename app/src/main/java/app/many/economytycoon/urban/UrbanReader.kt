package app.many.economytycoon.urban

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.Tick

/** Builds the read models from the service. Pure: reading never changes the state. */
object UrbanReader {
    fun view(service: UrbanService, now: Tick): UrbanView {
        val ranking = service.ranking()
        val cities = service.state.plans().map { cityView(service, it, now, ranking) }
        return UrbanView(now, cities, ranking)
    }

    fun cityView(service: UrbanService, city: CityId, now: Tick): CityView? {
        val plan = service.state.plan(city) ?: return null
        return cityView(service, plan, now, service.ranking())
    }

    private fun cityView(service: UrbanService, plan: CityPlan, now: Tick, ranking: List<RankEntry>): CityView {
        val params = service.params
        val analysis = UrbanAnalysis.analyse(plan, params)
        val score = service.score(plan.city) ?: Liveability.score(plan, analysis, params)
        val housing = Housing.report(plan.districts, plan.inputs.population, plan.inputs.averageWage, params)
        val names = plan.districts.map { it.name }
        val concessionOfLine = plan.concessions.live().mapNotNull { c -> c.line?.let { it to c.id } }.toMap()
        return CityView(
            city = plan.city, name = plan.name, population = plan.population, jobs = plan.jobs, floorArea = plan.floorArea,
            liveability = score.index,
            factors = LiveabilityFactor.values().map { FactorView(it, score.of(it)) },
            liveabilityHistory = plan.history(),
            rank = ranking.firstOrNull { it.city == plan.city }?.rank ?: 0,
            cityCount = ranking.size,
            vacancyPpm = housing.vacancyPpm, meanRentPerSqm = housing.meanRentPerSqm, rentBurdenPpm = housing.rentBurdenPpm,
            lowIncomeBurdenPpm = housing.lowIncomeBurdenPpm, unhoused = housing.unhoused,
            commuters = plan.traffic.commuters, averageCommuteSeconds = plan.traffic.averageCommuteSeconds,
            carSharePpm = plan.traffic.carSharePpm, transitSharePpm = plan.traffic.transitSharePpm, walkSharePpm = plan.traffic.walkSharePpm,
            congestionPpm = plan.traffic.congestionPpm,
            districts = plan.districts.map { districtView(plan, it, now) },
            lines = plan.network.lines().map { l ->
                LineView(
                    l.id, l.name, l.mode, l.stops.map { names[it] }, l.headwaySeconds, l.fare, l.status, l.loadRatioPpm, l.monthlyRiders,
                    l.monthlyOperatingCost(), l.dedicatedLane, l.lengthMeters, concessionOfLine[l.id],
                )
            },
            roads = plan.network.roads().map { r ->
                RoadView(r.id, names[r.a], names[r.b], r.roadClass, r.lanes, r.flowVph, r.capacityVph, r.volumeCapacityPpm, r.levelOfService, r.toll)
            },
            budget = budgetView(plan, params),
            council = councilView(plan, params, now),
            petitions = plan.petitions.all().sortedByDescending { it.filedAt }.map { petitionView(plan, it) },
            projects = plan.projects.all().map { p ->
                ProjectView(p.id, p.name, p.kind, p.phase, p.status, p.progressPpm(), p.budget, p.originalBudget, p.spent, p.overrunPpm, p.delayDays, p.daysLeft(), p.fixedPrice)
            },
            concessions = plan.concessions.all().map { c ->
                ConcessionView(
                    c.id, c.name, c.kind, c.model, c.status, c.concessionaire, c.termMonths, c.toll, c.availabilityMonthly, c.cityShareNowPpm,
                    c.bids.size, c.bids.maxOfOrNull { it.discountPpm } ?: 0L, c.bidsCloseAt, c.userChargesTotal, c.cityShareTotal,
                    c.availabilityPaidTotal, c.deductionsTotal,
                )
            },
            events = plan.events.recent(12).map { e ->
                EventView(e.id, e.kind, names.getOrNull(e.district), e.startedAt, e.endsAt, e.isActive(now), e.damageSqm)
            },
            insights = PlanningAdvisor.advise(plan, analysis, score, params, now),
        )
    }

    private fun districtView(plan: CityPlan, d: District, now: Tick) = DistrictView(
        d.index, d.name, d.zone, d.residents, d.jobs, d.floorArea, d.landSqm, d.farMilli, d.rules.effectiveFarMilli(), d.rules.maxFloors, d.headroom,
        d.pipelineArea, d.rentPerSqm, d.rentGrowthPpm(), d.vacancyPpm, d.landValuePerSqm, d.accessibilityPermille, d.stock.averageAgeYears,
        d.stock.averageConditionPpm, d.lowIncomeSharePpm, d.displacedTotal, d.backlashPpm, d.tenantProtectionPermille, d.heritage,
        d.averageCommuteSeconds, plan.petitions.openFor(d.index)?.id, d.lastRezonedAt.value >= 0L && now.value - d.lastRezonedAt.value < 180L,
    )

    private fun budgetView(plan: CityPlan, params: UrbanParams): BudgetView {
        val b = plan.budget
        val r = b.lastReport
        return BudgetView(
            b.rates, r?.revenue?.map { it.key to it.value } ?: emptyList(), r?.expenses?.map { it.key to it.value } ?: emptyList(),
            b.cash, b.debt, b.debtLimit(params), b.rating(), b.interestRatePpm(), r?.fundingRatioPpm ?: Scale.PPM, b.consecutiveDeficitMonths,
            Service.values().map { ServiceView(it, b.level(it), b.quality(it), b.serviceCost(it, plan.population)) }, r != null,
        )
    }

    private fun councilView(plan: CityPlan, params: UrbanParams, now: Tick): CouncilView {
        val c = plan.council
        val due = c.lastElection.value + params.monthsBetweenCouncilElections * Scale.MONTH_DAYS
        return CouncilView(
            c.seats.map { SeatView(it.index, it.name, it.ideology, plan.districts[it.ward].name, it.developerTies, it.integrity) },
            c.majority, c.supermajority, c.growthSeats(), c.moodAgainstGrowth, (due - now.value).coerceAtLeast(0L),
        )
    }

    private fun petitionView(plan: CityPlan, p: Petition) = PetitionView(
        p.id, plan.districts[p.district].name, p.district, p.kind, p.status, p.applicant, p.fromZone, p.toZone, p.requestedFarMilli, p.filedAt,
        p.hearingAt, p.voteAt, p.staffScore, p.opinion.supportPermille, p.opinion.oppositionPermille, p.yes, p.no,
        if (p.protestPetition) plan.council.supermajority else plan.council.majority, p.lobbied, p.protestPetition, p.note,
    )
}
