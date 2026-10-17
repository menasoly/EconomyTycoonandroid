package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.finance.Chart

/*
 * Supply of permits: opening a year's supply for each cap-and-trade market, expiring old vintages, reviewing the stability
 * reserve, and handing out the free share. This runs on the first day of every year and whenever a regime is installed.
 */

/** Opens the supply of the current year in every permit market that has none yet. */
internal fun CarbonService.ensureYears(ctx: CarbonService.Ctx) {
    for (regime in state.regimes()) {
        if (!regime.tradesPermits) continue
        val step = regime.stepFor(ctx.year) ?: continue
        val market = state.marketFor(regime.country)
        if (market.supply(ctx.year) != null) continue
        openYear(ctx, regime, step, market)
    }
}

private fun CarbonService.openYear(ctx: CarbonService.Ctx, regime: CarbonRegime, step: RegimeStep, market: PermitMarket) {
    expireOldVintages(ctx, regime, market)
    val day = CarbonCalendar.dayOfYear(ctx.tick)
    val daysLeft = (CarbonCalendar.DAYS_PER_YEAR - day).toLong()
    fun prorated(t: Tonnes) = if (day == 0) t else t.scale(daysLeft, CarbonCalendar.DAYS_PER_YEAR.toLong())
    val passed = if (day > params.auctionDayOfQuarter) ((day - params.auctionDayOfQuarter - 1) / DAYS_PER_AUCTION_QUARTER + 1).coerceAtMost(AUCTIONS_PER_YEAR) else 0
    val auctionLeft = (AUCTIONS_PER_YEAR - passed).toLong()
    val fullCap = step.capIn(ctx.year)
    val supply = YearSupply(ctx.year, prorated(fullCap), prorated(step.freeIn(ctx.year)), prorated(step.auctionIn(ctx.year)).scale(auctionLeft, AUCTIONS_PER_YEAR.toLong()))
    supply.auctionsHeld = passed
    market.supplyMap[ctx.year] = supply
    while (market.supplyMap.size > params.keepYears) market.supplyMap.remove(market.supplyMap.firstKey())
    reviewReserve(ctx, regime, market, supply, fullCap)
    for (a in state.compliance.accountsIn(market.country)) a.freeThisYear = Tonnes.ZERO
    market.mutatePendingFree(market.pendingFree + supply.free)
    flushPendingFree(ctx, market, regime, step)
}

private fun CarbonService.expireOldVintages(ctx: CarbonService.Ctx, regime: CarbonRegime, market: PermitMarket) {
    val oldestValid = if (regime.bankingAllowed) ctx.year - regime.vintageLifeYears else ctx.year - 1
    val lots = market.holdings.expire(oldestValid)
    if (lots.isEmpty()) return
    var total = Tonnes.ZERO
    for (l in lots) {
        total += l.tonnes
        ctx.settle.writeDown(company(l.company), Chart.INTANGIBLES, l.cost, "permits expired")
    }
    market.addExpired(total)
    ctx.emit(CarbonEvent.PermitsExpired(ctx.tick, market.country, total))
}

private fun CarbonService.reviewReserve(ctx: CarbonService.Ctx, regime: CarbonRegime, market: PermitMarket, supply: YearSupply, cap: Tonnes) {
    if (market.reviewedYear >= ctx.year) return
    val circulation = market.holdings.totalAll()
    val d = StabilityReserve.review(regime.reserve, circulation, cap, market.reserve, supply.remaining)
    market.setCirculation(circulation)
    market.markReviewed(ctx.year)
    if (d.isIdle) return
    supply.withheld += d.withhold
    supply.released += d.release
    market.mutateReserve(market.reserve + d.withhold - d.release - d.cancel)
    market.addCancelled(d.cancel)
    ctx.emit(CarbonEvent.ReserveAdjusted(ctx.tick, market.country, ctx.year, d.withhold, d.release, d.cancel))
}

/** Hands out free permits that are waiting for a recipient, once there are companies to receive them. */
internal fun CarbonService.flushPendingFree(ctx: CarbonService.Ctx) {
    for (market in state.markets()) {
        if (!market.pendingFree.isPositive) continue
        val regime = state.regime(market.country) ?: continue
        val step = regime.stepFor(ctx.year) ?: continue
        flushPendingFree(ctx, market, regime, step)
    }
}

private fun CarbonService.flushPendingFree(ctx: CarbonService.Ctx, market: PermitMarket, regime: CarbonRegime, step: RegimeStep) {
    val pool = market.pendingFree
    if (!pool.isPositive) return
    val weights = allocationWeights(market.country, step, ctx.year)
    val shares = FreeAllocation.distribute(pool, weights)
    if (shares.isEmpty()) return
    var total = Tonnes.ZERO
    for ((c, t) in shares) {
        market.holdings.add(c, ctx.year, t, Money.ZERO)
        val account = state.compliance.open(c, market.country)
        account.freeThisYear += t
        total += t
    }
    market.mutatePendingFree(pool - total)
    market.supply(ctx.year)?.let { it.freeIssued += total }
    ctx.emit(CarbonEvent.PermitsAllocated(ctx.tick, market.country, ctx.year, total, shares.size))
}

/**
 * Who gets free permits and in what proportion: companies in proportion to what they covered last year, or, before
 * there is such a record, to what their covered facilities are measured to release. Only companies over the step's
 * threshold that are still trading qualify.
 */
internal fun CarbonService.allocationWeights(country: CountryId, step: RegimeStep, year: Int): Map<CompanyId, Long> {
    val weights = java.util.TreeMap<CompanyId, Long>()
    for (cy in state.inventory.companiesIn(country, year - 1)) {
        val covered = coveredOf(cy, step)
        if (covered >= step.threshold && covered.isPositive && env.isActive(cy.company)) weights.merge(cy.company, covered.raw, Long::plus)
    }
    if (weights.isNotEmpty()) return weights
    val regime = state.regime(country)
    for (f in state.inventory.facilities()) {
        if (f.country != country || regime?.covers(f.family, year) != true || !env.isActive(f.owner)) continue
        val annual = f.annualDirect
        if (annual.isPositive) weights.merge(f.owner, annual.raw, Long::plus)
    }
    weights.entries.removeAll { Tonnes(it.value) < step.threshold }
    return weights
}
