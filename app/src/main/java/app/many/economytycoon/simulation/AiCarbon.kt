package app.many.economytycoon.simulation

import app.many.economytycoon.carbon.CarbonAi
import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.LedgerOwner

/**
 * Computer-run companies manage their carbon position every fortnight, in step with the permit auctions: they abate
 * where it pays, bid for the permits they will need, sell spares and keep credits ready for the deadline. Their orders
 * go through the same executor, and so the same checks, as a player's. Nothing happens in a game with no carbon policy.
 */
class AiCarbon(private val e: Economy, private val executor: CommandExecutor) {
    private val ai by lazy { CarbonAi(e.carbon, e.carbonViews) }

    fun run(tick: Tick) {
        if (e.carbonState.isPristine || tick.value % PERIOD != 0L) return
        val companies = e.companies.all().filter { it.kind == CompanyKind.AI && it.status == CompanyStatus.ACTIVE }.sortedBy { it.id }
        for (c in companies) {
            val rng = e.random.stream("carbon.ai", c.id.value)
            val cash = e.finance.ledger.cash(LedgerOwner.company(c.id))
            for (cmd in ai.decide(c.id, cash, tick, rng)) executor.execute(Actor.Ai(c.id), CarbonCommandMap.toCommand(cmd))
        }
    }

    private companion object {
        const val PERIOD = 15L
    }
}
