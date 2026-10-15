package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.futures.Side

/**
 * Computer-run companies use futures in two ways. Those sitting on a lot of a good sell it forward to lock in
 * today's price; a few others take a punt on where a price is heading. Each company looks once a month.
 */
class AiHedging(private val e: Economy, private val executor: CommandExecutor) {
    class Report(var hedges: Int = 0, var punts: Int = 0)

    private val lookEvery = 30L
    private val cashFloor = Money.units(120_000)
    private val puntOneIn = 8
    private val hedgeShareOfStockPpm = 500_000L

    fun run(tick: Tick): Report {
        val report = Report()
        val params = e.futures.params
        val ai = e.companies.all().filter { it.kind == CompanyKind.AI && it.status == CompanyStatus.ACTIVE }.sortedBy { it.id }
        for (c in ai) {
            if ((tick.value + c.id.value) % lookEvery != 0L) continue
            if (e.futures.openOf(c.id).size >= params.maxOpenPerCompany / 2) continue
            val cash = e.finance.ledger.cash(c.owner)
            val floor = Money.max(cashFloor, e.aiReserve.floor(c.id))
            if (cash < floor) continue
            val held = HashMap<GoodId, Long>()
            for (f in e.production.facilitiesOf(c.id)) for ((g, slot) in f.inventory.entries()) held.merge(g, slot.quantity.raw, Long::plus)
            val hedged = e.futures.openOf(c.id).filter { it.side == Side.SHORT }.map { it.good }.toSet()
            val best = held.filter { it.key !in hedged }.entries.sortedWith(compareBy({ -it.value }, { it.key.key })).firstOrNull()
            val random = e.random.stream("futures", c.id.value)
            if (best != null && best.value >= params.contractSize.raw * 2) {
                val contracts = minOf(params.maxContracts / 4, best.value * hedgeShareOfStockPpm / 1_000_000L / params.contractSize.raw).coerceAtLeast(1L)
                if (act(c.id, best.key, Side.SHORT, contracts, 60, cash, floor)) report.hedges++
            } else if (random.nextInt(puntOneIn) == 0) {
                val goods = e.content.allGoods
                val good = goods[random.nextInt(goods.size)].id
                val side = if (random.nextInt(2) == 0) Side.LONG else Side.SHORT
                if (act(c.id, good, side, 1L + random.nextLong(3L), 30, cash, floor)) report.punts++
            }
        }
        return report
    }

    private fun act(company: app.many.economytycoon.core.CompanyId, good: GoodId, side: Side, contracts: Long, tenor: Int, cash: Money, floor: Money): Boolean {
        val q = e.futuresRunner.quote(good, side, contracts, tenor)
        if (q.cashNeeded.raw * 4 > (cash - floor).raw) return false
        return executor.execute(Actor.Ai(company), Command.OpenFuture(company, good, side, contracts, tenor)) is Outcome.Ok
    }
}
