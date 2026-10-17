package app.many.economytycoon.simulation

import app.many.economytycoon.companies.Company
import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.tourism.HotelierAi
import app.many.economytycoon.tourism.HotelierStance

/**
 * Computer-run companies review their hotels once a month, on the fifth day: rates, overbooking, listings, renovation,
 * upgrades, extensions, and now and then a new hotel in a town with unmet demand. A move that costs money is only made when
 * the company has the cash for it; its payment is posted at once, so the next move sees what is left. Nothing happens in a
 * game without tourism, and a company with no hotels and no cash to open one does nothing.
 */
class AiHoteliers(private val e: Economy) {
    class Report(var moves: Int = 0, var applied: Int = 0)

    fun run(tick: Tick): Report {
        val report = Report()
        if (!e.tourismRunner.active || tick.value % PERIOD != OFFSET) return report
        for (c in e.companies.all().filter { it.kind == CompanyKind.AI && it.status == CompanyStatus.ACTIVE }.sortedBy { it.id }) {
            val stance = stanceOf(c)
            val view = HotelierAi.viewOf(e.tourism, c.id, e.finance.ledger.cash(c.owner), stance, tick.value)
            for (move in HotelierAi.decide(view)) {
                report.moves++
                val cash = e.finance.ledger.cash(c.owner)
                if (move.cost.raw > 0L && cash < move.cost) continue
                for (r in HotelierAi.execute(e.tourismService, view, listOf(move), tick)) {
                    if (r.outcome is Outcome.Ok<*>) {
                        report.applied++
                        e.tourismRunner.pay(r.intents, tick)
                    }
                }
            }
        }
        return report
    }

    private fun stanceOf(c: Company): HotelierStance {
        val p = e.competitors.personality(c.id) ?: return HotelierStance.BALANCED
        val appetite = (p.expansionAppetite + p.riskTolerance + p.aggression) / 3L
        return when {
            appetite < 350_000L -> HotelierStance.CAUTIOUS
            appetite > 650_000L -> HotelierStance.AGGRESSIVE
            else -> HotelierStance.BALANCED
        }
    }

    private companion object {
        const val PERIOD = 30L
        const val OFFSET = 5L
    }
}
