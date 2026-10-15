package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/**
 * How computer-run companies use the exchange. Mature companies with a solid balance sheet list themselves,
 * and those with spare cash buy shares that trade below what the business is worth and sell ones that have
 * run well ahead of it. Everything depends only on the state, so games replay identically.
 */
class AiInvestors(private val e: Economy) {
    private val listEvery = 45L
    private val tradeEvery = 7L
    private val buyBelowPpm = 880_000L
    private val sellAbovePpm = 1_250_000L
    private val spendShareOfCashPpm = 100_000L
    private val cashFloorMultiple = 6L

    fun run(tick: Tick) {
        val ai = e.companies.all().filter { it.kind == CompanyKind.AI && it.status == CompanyStatus.ACTIVE }.map { it.id }.sorted()
        for (id in ai) if ((tick.value + id.value) % listEvery == 0L) consider(id, tick)
        for (id in ai) if ((tick.value + id.value) % tradeEvery == 0L) invest(id, tick)
    }

    private fun consider(id: CompanyId, tick: Tick) {
        val c = e.companies.require(id)
        if (e.exchangeRunner.listingProblem(c, tick) != null) return
        val worth = e.finance.statements.balance(c.owner, tick).netWorth
        if (worth < e.exchange.params.minNetWorth * 2L) return
        e.exchangeRunner.list(id, FLOAT_PPM, tick)
    }

    private fun invest(id: CompanyId, tick: Tick) {
        val c = e.companies.require(id)
        // Sell first: anything far above its worth, so the cash can go into something cheaper.
        for (h in e.exchangeRunner.portfolio(id)) {
            val l = e.exchange.listing(h.issuer) ?: continue
            if (l.fairValue.raw > 0L && l.price.raw * 1_000_000L / l.fairValue.raw > sellAbovePpm) e.exchangeRunner.sell(id, h.issuer, h.shares, tick)
        }
        val cash = e.finance.ledger.cash(c.owner)
        val floor = Money.max(Money.units(10_000) * cashFloorMultiple, e.aiReserve.floor(id))
        if (cash < floor * 2L) return
        val best = e.exchange.listings()
            .filter { it.issuer != id && it.fairValue.raw > 0L }
            .filter { e.exchangeRunner.publicShares(it.issuer) > 0L }
            .filter { it.price.raw * 1_000_000L / it.fairValue.raw < buyBelowPpm }
            .minWithOrNull(compareBy({ it.price.raw * 1_000_000L / it.fairValue.raw }, { it.issuer }))
            ?: return
        val budget = Money(mulDiv((cash - floor).raw, spendShareOfCashPpm, 1_000_000L))
        val shares = (budget.raw / best.price.raw.coerceAtLeast(1L)).coerceAtMost(e.exchangeRunner.publicShares(best.issuer))
        if (shares <= 0L) return
        val r = e.exchangeRunner.buy(id, best.issuer, shares, tick)
        if (r is Outcome.Rejected) return
    }

    companion object { const val FLOAT_PPM = 250_000L }
}
