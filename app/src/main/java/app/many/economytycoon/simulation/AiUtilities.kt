package app.many.economytycoon.simulation

import app.many.economytycoon.companies.Company
import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.energygrid.GridAnalytics
import app.many.economytycoon.energygrid.PpaProfile
import app.many.economytycoon.energygrid.PpaStatus
import app.many.economytycoon.energygrid.UnitOrder
import app.many.economytycoon.energygrid.UtilityAi
import app.many.economytycoon.energygrid.UtilityMove
import app.many.economytycoon.energygrid.UtilityStance

/**
 * Computer-run companies that own generating plant plan their fleet once a month: what to build, mothball, restart or
 * retire, and whether to sell part of a unit's output at a fixed price. Companies that buy power look at the offers made
 * to them. Orders are paid for at once; a wish to sell power is given to the largest buyer on the grid.
 */
class AiUtilities(private val e: Economy) {
    class Report(var moves: Int = 0, var orders: Int = 0, var offers: Int = 0, var accepted: Int = 0)

    fun run(tick: Tick): Report {
        val report = Report()
        if (!e.energyGridRunner.active || tick.value % PERIOD != 0L) return report
        val ai = e.companies.all().filter { it.kind == CompanyKind.AI && it.status == CompanyStatus.ACTIVE }.sortedBy { it.id }
        answerOffers(ai, tick, report)
        for (c in ai) for (g in e.energyGrids.grids) {
            if (g.units.none { it.owner == c.id }) continue
            val view = UtilityAi.viewOf(
                e.energyGrids, g, c.id, tick.value, e.finance.ledger.cash(c.owner), stanceOf(c), e.energyGridRunner.currentFuelPrices(), e.energyGridService.peakDemandKw(g.id),
            )
            for (r in UtilityAi.execute(e.energyGridService, view, UtilityAi.decide(view))) {
                report.moves++
                val outcome = r.outcome
                when {
                    outcome is Outcome.Ok<*> && outcome.value is UnitOrder -> {
                        e.energyGridRunner.payNow(listOf((outcome.value as UnitOrder).payment), tick)
                        report.orders++
                    }
                    r.move is UtilityMove.SeekPpa -> if (offer(c, g.id, r.move as UtilityMove.SeekPpa, tick)) report.offers++
                }
            }
        }
        return report
    }

    private fun stanceOf(c: Company): UtilityStance {
        val p = e.competitors.personality(c.id) ?: return UtilityStance.BALANCED
        val appetite = (p.expansionAppetite + p.riskTolerance) / 2L
        return when {
            appetite < 350_000L -> UtilityStance.CAUTIOUS
            appetite > 650_000L -> UtilityStance.BOLD
            else -> UtilityStance.BALANCED
        }
    }

    /** The largest company with a connection on the grid, other than the seller, is asked to buy. */
    private fun offer(seller: Company, grid: app.many.economytycoon.energygrid.GridId, wish: UtilityMove.SeekPpa, tick: Tick): Boolean {
        val g = e.energyGrids.grid(grid) ?: return false
        val buyer = g.loads.filter { it.connected && it.company != null && it.company != seller.id }
            .groupBy { it.company }.entries
            .sortedWith(compareByDescending<Map.Entry<app.many.economytycoon.core.CompanyId?, List<app.many.economytycoon.energygrid.GridLoad>>> { en -> en.value.sumOf { it.baseKw } }.thenBy { it.key?.value ?: 0L })
            .firstNotNullOfOrNull { it.key } ?: return false
        val already = e.energyGrids.ppasOfCompany(seller.id).any { it.status == PpaStatus.OFFERED && it.buyer == buyer && it.unit == null }
        if (already) return false
        val r = e.energyGridService.offerPpa(
            grid, seller.id, buyer, PpaProfile.BASELOAD, wish.volumeKw, 0L, null, wish.strike, Tick(tick.value + 1L), wish.days, PENALTY_PPM, tick,
        )
        return r is Outcome.Ok
    }

    /** A computer-run buyer takes an offer whose fixed price is not much above what the pool has been paying. */
    private fun answerOffers(ai: List<Company>, tick: Tick, report: Report) {
        val mine = ai.map { it.id }.toSet()
        for (p in e.energyGrids.ppas) {
            if (p.status != PpaStatus.OFFERED || p.buyer !in mine) continue
            val g = e.energyGrids.grid(p.grid) ?: continue
            val average = GridAnalytics.windowAveragePrice(g.history, 30)
            val ceiling = Money(mulDiv(average.raw, ACCEPT_CEILING_PPM, 1_000_000L))
            if (p.strikePerMwh <= ceiling) {
                if (e.energyGridService.acceptPpa(p.id, p.buyer, tick) is Outcome.Ok) report.accepted++
            } else {
                e.energyGridService.declinePpa(p.id, p.buyer, tick)
            }
        }
    }

    private companion object {
        const val PERIOD = 30L
        const val PENALTY_PPM = 100_000L
        const val ACCEPT_CEILING_PPM = 1_150_000L
    }
}
