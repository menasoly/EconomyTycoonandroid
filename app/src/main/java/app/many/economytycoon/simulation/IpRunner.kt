package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.ip.IpCommand
import app.many.economytycoon.ip.IpEvent
import app.many.economytycoon.ip.IpStepResult

/**
 * Connects the patent module to the economy. Games begin without a patent office; once [establish] has run, filings,
 * licences, disputes and secrets exist and the module takes its turn each day after the day's sales are recorded.
 * Until then nothing here does anything, nothing is saved or hashed and no random number is drawn, so a game without
 * the office plays exactly as it did before.
 *
 * Company commands are carried out and paid at once, like the other runners. What the daily step decides (royalties,
 * fees, damages, credits) is held until the settlement phase, where a company short of cash has its deferrable charges
 * deferred instead of overdrawn.
 */
class IpRunner(private val e: Economy) {
    private var pending: List<SettlementIntent> = emptyList()
    private val recent = ArrayDeque<IpEvent>()

    /** True once the patent office has been opened. */
    val active: Boolean get() = e.ipActive

    /** The module's events of the last few days, oldest first; alerts and the news read them. */
    fun recentEvents(): List<IpEvent> = recent.toList()

    fun establish(tick: Tick): Outcome<CommandResult> {
        if (active) return Outcome.reject(Rules.WRONG_STATE, "the patent office is already open")
        e.openIp()
        return Outcome.ok(CommandResult(message = "the patent office is open"))
    }

    // ---- the day -------------------------------------------------------------------------------

    /** Runs the module's day and holds its payments for settlement. Called once a tick after fulfilment. */
    fun step(tick: Tick): List<IpEvent> {
        if (!active) return emptyList()
        e.ipSales.prune(tick)
        val result = e.ip.step(tick, e.ipContext)
        pending = pending + result.intents()
        note(result)
        return result.events
    }

    /** Hands the day's payments to the settlement phase and starts over. */
    fun drain(): List<SettlementIntent> {
        val out = pending
        pending = emptyList()
        return out
    }

    private fun note(result: IpStepResult) {
        for (ev in result.events) recent.addLast(ev)
        while (recent.size > RECENT_CAP) recent.removeFirst()
    }

    // ---- commands ------------------------------------------------------------------------------

    fun execute(cmd: IpCommand, tick: Tick): Outcome<CommandResult> {
        if (!active) return Outcome.reject(Rules.WRONG_STATE, "there is no patent office yet")
        val r = e.ip.execute(tick, e.ipContext, cmd)
        pay(r.step)
        note(r.step)
        return when (val o = r.outcome) {
            is Outcome.Ok -> Outcome.ok(CommandResult(message = o.value))
            is Outcome.Rejected -> Outcome.Rejected(o.rejection)
        }
    }

    /** Pays a command's money at once, so the next command sees the cash that is left. */
    private fun pay(step: IpStepResult) {
        val intents = step.intents()
        if (intents.isNotEmpty()) e.finance.settlement.settle(e.tick, intents)
    }

    /** A company has ceased to exist: its rights are sold or ended and what it owed is settled. */
    fun forget(company: CompanyId, tick: Tick) {
        if (!active) return
        val r = e.ip.companyClosed(tick, e.ipContext, company)
        pay(r)
        note(r)
    }

    private companion object {
        const val RECENT_CAP = 200
    }
}
