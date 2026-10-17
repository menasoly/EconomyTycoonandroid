package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.SettlementIntent

/**
 * Runs one simulated day over every grid, in a fixed order so the result depends only on the state, the inputs and the
 * seed: weather, outages and maintenance, demand, the storage plan from a first dispatch without trade, then 24 hourly
 * dispatches with trade, shedding, blackout records and agreement accruals, and last the capacity auction and the
 * operator's investment round.
 */
internal class GridDayRunner(
    private val service: EnergyGridService,
    private val state: EnergyGridState,
    private val limits: GridLimits,
) {
    private val events = ArrayList<GridEvent>()
    private val impacts = ArrayList<FacilityImpact>()
    private val capex = ArrayList<SettlementIntent>()
    private val linkFlow = java.util.TreeMap<LinkId, Long>()

    /** Everything one grid needs through the day. */
    private class GridDay(val grid: Grid, val weather: GridWeather) {
        val producers = ArrayList<GenUnit>()
        val stores = ArrayList<GenUnit>()
        val draws = ArrayList<Pair<GridLoad, LongArray>>()
        val demand = LongArray(GridUnits.HOURS_PER_DAY)
        val prev = java.util.TreeMap<UnitId, Long>()
        val cost = java.util.TreeMap<UnitId, Money>()
        val soc = java.util.TreeMap<UnitId, Long>()
        val plans = java.util.TreeMap<UnitId, StoragePlan>()
        val unitKwh = java.util.TreeMap<UnitId, Long>()
        val unitRevenue = java.util.TreeMap<UnitId, Money>()
        val hours = ArrayList<HourProfile>()
        val hourlyOutput = ArrayList<Map<UnitId, Long>>()
        val shedKwhByLoad = java.util.TreeMap<LoadId, Long>()
    }

    fun run(tick: Tick, inputs: DayInputs, random: RandomProvider): DayReport {
        val t = tick.value
        val days = ArrayList<GridDay>()
        for (g in state.gridMap.values) {
            val w = GridWeatherModel.forDay(g.climate, tick, inputs.weather[g.id].orEmpty(), random.stream("energy.weather", g.id.value), g.windCarryPpm)
            g.windCarryPpm = w.windDayPpm
            lifecycle(g, w, t, random.stream("energy.outage", g.id.value))
            days += prepare(g, w, tick, inputs.fuelFor(g.id))
        }
        lapseOffers(t)
        for (d in days) planStorage(d)
        val summaries = java.util.TreeMap<GridId, DaySummary>()
        runHours(days, t)
        for (d in days) summaries[d.grid.id] = finish(d, t)
        accruePpas(days, t)
        for (d in days) govern(d.grid, tick, inputs.fuelFor(d.grid.id))
        for (g in state.gridMap.values) g.trimRecords()
        return DayReport(tick, summaries, events.toList(), impacts.toList(), linkFlow.toMap(), capex.toList())
    }

    // ----- units: construction, failures, maintenance -----

    private fun lifecycle(g: Grid, w: GridWeather, t: Long, rnd: DeterministicRandom) {
        val params = OutageParams()
        val peak = service.peakDemandKw(g.id)
        for (u in g.unitMap.values) {
            when (u.status) {
                UnitStatus.BUILDING -> if (t >= u.statusUntil) {
                    u.status = UnitStatus.AVAILABLE
                    u.statusUntil = 0L
                    u.commissioned = t
                    u.lastMaintenance = t
                    events += GridEvent.UnitCommissioned(g.id, u.id)
                }
                UnitStatus.MAINTENANCE, UnitStatus.FORCED_OUTAGE -> if (t >= u.statusUntil) {
                    if (u.status == UnitStatus.MAINTENANCE) {
                        u.lastMaintenance = t
                        u.deferrals = 0
                        u.efficiencyPpm = u.tech.profile.efficiencyPpm
                    }
                    u.status = UnitStatus.AVAILABLE
                    u.statusUntil = 0L
                    events += GridEvent.UnitBack(g.id, u.id)
                }
                UnitStatus.AVAILABLE -> availableUnit(g, u, w, t, rnd, peak, params)
                UnitStatus.MOTHBALLED, UnitStatus.RETIRED -> {}
            }
        }
    }

    private fun availableUnit(g: Grid, u: GenUnit, w: GridWeather, t: Long, rnd: DeterministicRandom, peak: Long, params: OutageParams) {
        if (u.tech.isThermal) {
            val floor = u.tech.profile.efficiencyPpm * 850_000L / Rate.ONE_PPM
            u.efficiencyPpm = maxOf(floor, u.efficiencyPpm - mulDiv(u.efficiencyPpm, params.wearPpmPerDay, Rate.ONE_PPM, RoundingMode.UP))
        }
        val chance = OutageModel.forcedChancePpm(u.tech, u.ageAt(t), w.outageRiskPpm, params)
        val days = OutageModel.rollForced(chance, u.tech, rnd)
        if (days > 0) {
            u.status = UnitStatus.FORCED_OUTAGE
            u.statusUntil = t + days
            u.outputKw = 0L
            u.outageCount++
            g.outageList += OutageEvent(g.id, u.id, OutageKind.FORCED, t, u.statusUntil)
            events += GridEvent.UnitFailed(g.id, u.id, u.statusUntil)
            return
        }
        if (!OutageModel.maintenanceDue(u.tech, u.lastMaintenance, t)) return
        val spare = g.unitMap.values.filter { it.status == UnitStatus.AVAILABLE && it.id != u.id }.sumOf { CapacityMarket.firmKw(it.tech, it.capacityKw) } - peak
        if (OutageModel.mayStartMaintenance(spare, peak, u.deferrals, params)) {
            u.status = UnitStatus.MAINTENANCE
            u.statusUntil = t + u.tech.profile.maintenanceTicks
            u.outputKw = 0L
            g.outageList += OutageEvent(g.id, u.id, OutageKind.MAINTENANCE, t, u.statusUntil)
            events += GridEvent.MaintenanceStarted(g.id, u.id, u.statusUntil)
        } else {
            u.deferrals++
            u.lastMaintenance = t - u.tech.profile.maintenanceEveryTicks + params.deferTicks
            g.outageList += OutageEvent(g.id, u.id, OutageKind.DEFERRED, t, t + params.deferTicks)
            events += GridEvent.MaintenancePostponed(g.id, u.id, u.deferrals)
        }
    }

    private fun lapseOffers(t: Long) {
        for (p in state.ppaMap.values) {
            if (p.status == PpaStatus.OFFERED && (t > p.startsAt || t - p.offeredAt > limits.offerLapseDays)) {
                p.status = PpaStatus.DECLINED
                p.closedAt = t
                events += GridEvent.PpaLapsed(p.grid, p.id)
            }
        }
    }

    // ----- the day -----

    private fun prepare(g: Grid, w: GridWeather, tick: Tick, fuel: FuelPrices): GridDay {
        val d = GridDay(g, w)
        for (u in g.unitMap.values) {
            if (u.status != UnitStatus.AVAILABLE) continue
            if (u.tech.isStorage) { d.stores += u; d.soc[u.id] = u.socKwh } else d.producers += u
            d.prev[u.id] = u.outputKw
            d.cost[u.id] = u.marginalCost(fuel)
        }
        for (l in g.loadMap.values) {
            if (!l.connected || l.baseKw <= 0L) continue
            val kw = LoadShape.dayKw(l.baseKw, l.customer, tick, w)
            d.draws += l to kw
            for (h in kw.indices) d.demand[h] += kw[h]
        }
        return d
    }

    private fun unitsFor(d: GridDay, h: Int, prev: Map<UnitId, Long>): List<DispatchUnit> = d.producers.map { u ->
        DispatchUnit(
            u.id, u.tech, d.weather.availableKw(u.tech, u.capacityKw, h), d.cost.getValue(u.id), prev[u.id] ?: 0L,
            u.tech.rampUpKw(u.capacityKw), u.tech.rampDownKw(u.capacityKw), u.tech.minStableKw(u.capacityKw),
        )
    }

    /** Runs the day's dispatch without trade and with the storage plans chosen so far, returning each hour's result. */
    private fun trialDay(d: GridDay, plans: Collection<StoragePlan>): List<DispatchResult> {
        val prev = java.util.TreeMap(d.prev)
        val out = ArrayList<DispatchResult>(GridUnits.HOURS_PER_DAY)
        for (h in 0 until GridUnits.HOURS_PER_DAY) {
            val charge = plans.sumOf { it.chargeKw[h] }
            val discharge = plans.sumOf { it.dischargeKw[h] }
            val r = DispatchEngine.dispatch(DispatchInput(d.demand[h], unitsFor(d, h, prev), discharge, charge, 0L, 0L, d.grid.params.dispatch()))
            out += r
            for (u in r.units) prev[u.id] = u.outputKw
        }
        return out
    }

    /**
     * Plans each store on the prices a first dispatch gives without it, then checks the plan against a dispatch that
     * includes it: charging lifts the price and discharging lowers it, so a plan that looked good on the forecast can
     * lose money. The store tries full, half and quarter power and keeps the most profitable, or stays idle.
     */
    private fun planStorage(d: GridDay) {
        if (d.stores.isEmpty()) return
        for (s in d.stores) d.plans[s.id] = StoragePlan.idle(s.id)
        for (s in d.stores) {
            val (buy, sell) = StoragePlanner.cautious(trialDay(d, d.plans.values).map { it.price })
            val hours = s.tech.profile.storageHours
            var best = StoragePlan.idle(s.id)
            var bestProfit = 0L
            for (share in STEP_SHARES) {
                val step = s.capacityKw * share / 100L
                if (step <= 0L) continue
                val level = StoragePlanner.levelOf(s.socKwh, step, hours)
                val candidate = StoragePlanner.plan(s.id, step, hours, level, s.tech.profile.roundTripPpm, d.cost.getValue(s.id), buy, sell)
                if (candidate.isIdle) continue
                val trial = trialDay(d, d.plans.filterKeys { it != s.id }.values + candidate)
                var profit = 0L
                for (h in 0 until GridUnits.HOURS_PER_DAY) {
                    profit += GridUnits.cost(trial[h].price - d.cost.getValue(s.id), candidate.dischargeKw[h]).raw - GridUnits.cost(trial[h].price, candidate.chargeKw[h]).raw
                }
                if (profit > bestProfit) { best = candidate; bestProfit = profit }
            }
            d.plans[s.id] = best
        }
    }

    private fun runHours(days: List<GridDay>, t: Long) {
        val links = state.linkMap.values.map { it.view() }
        for (h in 0 until GridUnits.HOURS_PER_DAY) {
            val inputs = java.util.TreeMap<GridId, DispatchInput>()
            for (d in days) {
                var charge = 0L
                var discharge = 0L
                for (s in d.stores) d.plans[s.id]?.let { charge += it.chargeKw[h]; discharge += it.dischargeKw[h] }
                inputs[d.grid.id] = DispatchInput(d.demand[h], unitsFor(d, h, d.prev), discharge, charge, 0L, 0L, d.grid.params.dispatch())
            }
            val traded = TradeDispatcher.run(inputs, links)
            for (tr in traded.transfers) recordTransfer(tr, t)
            for (d in days) {
                var result = traded.results.getValue(d.grid.id)
                val extra = emergencyStorage(d, h, result)
                if (extra.isNotEmpty()) {
                    result = DispatchEngine.dispatch(result.input.copy(storageDischargeKw = result.input.storageDischargeKw + extra.values.sum()))
                }
                record(d, h, t, result, extra)
            }
        }
    }

    private fun recordTransfer(tr: Transfer, t: Long) {
        val link = state.link(tr.link) ?: return
        if (tr.from == link.a) link.sentAbKwh += tr.sentKw else link.sentBaKwh += tr.sentKw
        link.lostKwh += tr.sentKw - tr.receivedKw
        if (tr.sentKw >= link.capacityKw) link.congestedHours++
        linkFlow[tr.link] = (linkFlow[tr.link] ?: 0L) + tr.sentKw
    }

    /** Idle stores with energy left discharge into a shortfall. Returns the delivered kilowatts by store. */
    private fun emergencyStorage(d: GridDay, h: Int, r: DispatchResult): Map<UnitId, Long> {
        if (r.unservedKw <= 0L) return emptyMap()
        val out = java.util.TreeMap<UnitId, Long>()
        var need = r.unservedKw
        for (s in d.stores) {
            if (need <= 0L) break
            val plan = d.plans[s.id]
            if (plan != null && (plan.chargeKw[h] > 0L || plan.dischargeKw[h] > 0L)) continue
            if ((d.soc[s.id] ?: 0L) < s.capacityKw) continue
            val delivered = mulDiv(s.capacityKw, s.tech.profile.roundTripPpm, Rate.ONE_PPM, RoundingMode.DOWN)
            out[s.id] = delivered
            need -= delivered
        }
        return out
    }

    private fun record(d: GridDay, h: Int, t: Long, r: DispatchResult, emergency: Map<UnitId, Long>) {
        val g = d.grid
        val price = r.price
        val unitOut = java.util.TreeMap<UnitId, Long>()
        for (u in r.units) {
            unitOut[u.id] = u.outputKw
            d.prev[u.id] = u.outputKw
            d.unitKwh.merge(u.id, u.outputKw, Long::plus)
            d.unitRevenue.merge(u.id, GridUnits.cost(price, u.outputKw), Money::plus)
        }
        var charge = 0L
        var discharge = 0L
        for (s in d.stores) {
            val plan = d.plans[s.id]
            val power = s.capacityKw
            val soc = d.soc.getValue(s.id)
            val extra = emergency[s.id] ?: 0L
            val delivered = (plan?.dischargeKw?.get(h) ?: 0L) + extra
            val charging = plan?.chargeKw?.get(h) ?: 0L
            if (charging > 0L) {
                d.soc[s.id] = minOf(s.storageCapacityKwh, soc + charging)
                d.unitRevenue.merge(s.id, -GridUnits.cost(price, charging), Money::plus)
                charge += charging
            }
            if (delivered > 0L) {
                val drawn = (plan?.drawKw?.get(h) ?: 0L) + (if (extra > 0L) power else 0L)
                d.soc[s.id] = maxOf(0L, d.soc.getValue(s.id) - drawn)
                d.unitKwh.merge(s.id, delivered, Long::plus)
                d.unitRevenue.merge(s.id, GridUnits.cost(price, delivered), Money::plus)
                unitOut[s.id] = delivered
                discharge += delivered
            }
        }
        val byTech = r.outputByTech()
        byTech[Technology.STORAGE.ordinal] += discharge
        val shed = r.unservedKw
        shedLoads(d, h, t, shed, price)
        d.hourlyOutput += unitOut
        d.hours += HourProfile(
            h, r.demandKw, price, shed, r.curtailedKw, r.input.importKw, r.input.exportKw, charge, discharge, r.reservePpm, byTech.toList(), r.setter?.value ?: -1L,
        )
    }

    private fun shedLoads(d: GridDay, h: Int, t: Long, shedKw: Long, price: Money) {
        val g = d.grid
        val draws = d.draws.map { (l, kw) -> LoadDraw(l.id, l.customer, kw[h]) }
        val cuts = LoadShedder.shed(draws, shedKw).associate { it.load to it.kw }
        for ((l, kw) in d.draws) {
            val cut = cuts[l.id] ?: 0L
            l.monthServedKwh += kw[h] - cut
            if (cut > 0L) {
                l.monthShedKwh += cut
                d.shedKwhByLoad.merge(l.id, cut, Long::plus)
                l.lifetimeShedKwh += cut
                l.shedHoursToday++
            }
        }
        val open = g.openBlackout()
        if (shedKw > 0L) {
            val record = open ?: BlackoutRecord(state.counters.blackout(), g.id, t, h).also {
                g.blackoutList += it
                events += GridEvent.BlackoutStarted(g.id, it.id, h, shedKw)
            }
            record.addHour(shedKw, cuts.keys, GridUnits.cost(g.params.valueOfLostLoad, shedKw))
        } else if (open != null) {
            open.close(t)
            events += GridEvent.BlackoutEnded(g.id, open.id, open.hours, open.shedKwh, open.damage)
        }
    }

    private fun finish(d: GridDay, t: Long): DaySummary {
        val g = d.grid
        for (u in d.producers) {
            u.outputKw = d.prev[u.id] ?: 0L
            val kwh = d.unitKwh[u.id] ?: 0L
            u.soldKwh += kwh
            u.lifetimeKwh += kwh
            u.poolRevenue = u.poolRevenue + (d.unitRevenue[u.id] ?: Money.ZERO)
        }
        for (s in d.stores) {
            s.socKwh = d.soc.getValue(s.id)
            val kwh = d.unitKwh[s.id] ?: 0L
            s.soldKwh += kwh
            s.lifetimeKwh += kwh
            s.poolRevenue = s.poolRevenue + (d.unitRevenue[s.id] ?: Money.ZERO)
        }
        val summary = DaySummary.of(t, d.hours, g.params.valueOfLostLoad)
        g.historyList += summary
        g.lastDay = d.hours.toList()
        g.monthDemandKwh += summary.servedKwh
        g.lifetimeUnservedKwh += summary.unservedKwh
        if (summary.peakPrice.raw * 2 >= g.params.priceCap.raw) events += GridEvent.PriceSpike(g.id, summary.peakPrice, summary.averagePrice)
        for ((l, _) in d.draws) {
            if (l.shedHoursToday <= 0) continue
            val facility = l.facility
            val company = l.company
            if (l.kind == LoadKind.FACILITY && facility != null && company != null) {
                impacts += FacilityImpact(
                    g.id, l.id, facility, company, l.shedHoursToday, d.shedKwhByLoad[l.id] ?: 0L,
                    BlackoutDamage.conditionLossPpm(l.customer, l.shedHoursToday),
                )
            }
            l.shedHoursToday = 0
        }
        return summary
    }

    // ----- agreements -----

    private fun accruePpas(days: List<GridDay>, t: Long) {
        val byGrid = days.associateBy { it.grid.id }
        val shortfallToday = java.util.TreeMap<PpaId, Long>()
        val delivering = java.util.TreeSet<PpaId>()
        for (h in 0 until GridUnits.HOURS_PER_DAY) {
            val remaining = LinkedHashMap<Pair<GridId, CompanyId>, Long>()
            for (p in state.ppaMap.values) {
                if (!p.runsAt(t)) continue
                val d = byGrid[p.grid] ?: continue
                val out = d.hourlyOutput[h]
                val price = d.hours[h].price
                when (p.profile) {
                    PpaProfile.BASELOAD -> {
                        val key = p.grid to p.seller
                        val left = remaining.getOrPut(key) { d.grid.unitMap.values.filter { it.owner == p.seller }.sumOf { out[it.id] ?: 0L } }
                        val delivered = minOf(p.volumeKw, left)
                        remaining[key] = left - delivered
                        p.accrueHour(delivered, p.volumeKw - delivered, price)
                        if (delivered > 0L) delivering += p.id
                        if (delivered < p.volumeKw) shortfallToday.merge(p.id, (p.volumeKw - delivered), Long::plus)
                    }
                    PpaProfile.AS_PRODUCED -> {
                        val produced = p.unit?.let { out[it] } ?: 0L
                        val delivered = mulDiv(produced, p.sharePpm, Rate.ONE_PPM, RoundingMode.DOWN)
                        p.accrueHour(delivered, 0L, price)
                        if (delivered > 0L) delivering += p.id
                    }
                }
            }
        }
        for (id in delivering) state.ppaMap.getValue(id).daysDelivering++
        for ((id, kwh) in shortfallToday) events += GridEvent.PpaShortfall(state.ppaMap.getValue(id).grid, id, kwh)
    }

    // ----- capacity auction and investment -----

    private fun govern(g: Grid, tick: Tick, fuel: FuelPrices) {
        val t = tick.value
        val round = g.capacityRound
        if (round == null || t >= round.validUntil) holdAuction(g, t, fuel)
        if (g.params.operatorPlans && (g.lastPlanTick < 0L || t - g.lastPlanTick >= PLAN_EVERY)) invest(g, tick, fuel)
    }

    private fun holdAuction(g: Grid, t: Long, fuel: FuelPrices) {
        val average = GridAnalytics.windowAveragePrice(g.historyList, 30)
        val peak = service.peakDemandKw(g.id)
        val bids = g.unitMap.values.filter { it.isLive && it.status != UnitStatus.MOTHBALLED }.mapNotNull { u ->
            val firm = CapacityMarket.firmKw(u.tech, u.capacityKw)
            if (firm <= 0L) null else CapacityBid(u.id, firm, CapacityMarket.bidPerMwDay(u.tech, u.marginalCost(fuel), average))
        }
        val result = CapacityMarket.auction(t, CapacityMarket.requirementKw(peak, g.params.targetReservePpm), bids)
        g.capacityRound = result
        val recent = g.historyList.takeLast(30)
        val dailyServed = if (recent.isEmpty()) 0L else recent.sumOf { it.servedKwh } / recent.size
        val year = Calendar.DAYS_PER_YEAR.toLong()
        g.capacityLevy = GridUnits.priceOf(result.dailyPayment() * year, dailyServed * year)
        events += GridEvent.CapacityAuction(g.id, result.clearedKw, result.shortfallKw, result.clearingPrice)
    }

    private fun invest(g: Grid, tick: Tick, fuel: FuelPrices) {
        g.lastPlanTick = tick.value
        val counted = g.unitMap.values.filter { it.isLive && it.status != UnitStatus.MOTHBALLED }
        val firmByTech = LongArray(Technology.values().size)
        for (u in g.unitMap.values.filter { (it.isLive && it.status != UnitStatus.MOTHBALLED) || it.status == UnitStatus.BUILDING }) {
            firmByTech[u.tech.ordinal] += CapacityMarket.firmKw(u.tech, u.capacityKw)
        }
        val candidates = counted.filter { it.owner == null && it.status == UnitStatus.AVAILABLE }.map {
            RetireCandidate(it.id, it.tech, it.ageAt(tick.value), CapacityMarket.firmKw(it.tech, it.capacityKw), it.marginalCost(fuel))
        }
        val view = PlanningView(
            service.peakDemandKw(g.id), g.params.growthPpm, counted.sumOf { CapacityMarket.firmKw(it.tech, it.capacityKw) }, g.firmUnderConstructionKw(),
            firmByTech.toList(), candidates, g.params.targetReservePpm, g.params.buildable(), fuel,
        )
        val plan = InvestmentPlanner.plan(view)
        for (b in plan.builds) {
            val order = service.orderUnit(g.id, b.tech, b.capacityKw, null, tick).getOrNull() ?: continue
            capex += order.payment
            events += GridEvent.UnitOrdered(g.id, order.unit.id, b.tech, order.unit.statusUntil)
        }
        for (id in plan.retire) {
            if (service.retireUnit(id).isOk) events += GridEvent.UnitRetired(g.id, id)
        }
    }

    companion object {
        /** Days between investment rounds. */
        const val PLAN_EVERY = 90L

        /** Shares of a store's power, in percent, that it tries when planning its day. */
        val STEP_SHARES = listOf(100L, 50L, 25L)
    }
}
