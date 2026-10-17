package app.many.economytycoon.simulation

import app.many.economytycoon.banking.BankAction
import app.many.economytycoon.banking.BankEvent
import app.many.economytycoon.banking.BankingIntents
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.OwnerKind
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.SettlementIntent

/**
 * Connects the banking sector to the economy. Games start with the simple lender in `finance` and no commercial banks;
 * once [establish] has run, every country has a few computer-run banks and the player may found one. Until then nothing
 * here does anything and nothing is saved or hashed.
 *
 * The banks keep their own books (pools of loans and deposits by sector) and describe only the money that really
 * changes hands as settlement intents. The existing loan book stays the lender for companies; the sector lends to
 * households and businesses that are not individually modelled, so no money is created twice. Customers that are not
 * modelled are the households account, so every flow moves money that already exists. The one exception is share capital
 * paid in by investors, which is a designated creation of money like founding capital.
 */
class BankingRunner(private val e: Economy) {
    private val service get() = e.banking

    /** True once the sector has been set up; before that nothing here does anything. */
    val active: Boolean get() = !service.isPristine

    private fun fail(rule: String, message: String): Outcome<CommandResult> = Outcome.reject(rule, message)

    private fun cash(company: CompanyId): Money = e.finance.ledger.cash(LedgerOwner.company(company))

    // ---- setting up ----------------------------------------------------------------------------

    /** Gives every country three to five computer-run banks, paid in by investors. The same world always gets the same banks. */
    fun establish(tick: Tick): Outcome<CommandResult> {
        if (active) return fail(Rules.WRONG_STATE, "the banking sector is already set up")
        var made = 0
        val intents = ArrayList<SettlementIntent>()
        for (c in e.world.countries().sortedBy { it.id }) {
            val count = RIVALS_MIN + (c.id.value % RIVALS_SPREAD).toInt()
            for (id in service.seedRivals(c.id, count, e.bankingInputs, tick, e.random)) {
                val capital = service.bank(id)?.shareCapital ?: continue
                BankingIntents.capitalInjection(id, null, capital, tick.value)?.let { intents += it }
                made++
            }
        }
        if (made == 0) return fail(Rules.WRONG_STATE, "there is no country to open banks in")
        settleNow(tick, intents)
        return Outcome.ok(CommandResult(made.toLong(), "$made banks opened"))
    }

    // ---- commands ------------------------------------------------------------------------------

    fun found(actor: Actor, cmd: Command.FoundBank, tick: Tick): Outcome<CommandResult> {
        if (actor !is Actor.Player) return fail(Rules.NOT_OWNER, "only a player founds a bank")
        if (!active) return fail(Rules.WRONG_STATE, "there is no banking sector in this game")
        if (e.world.countries().none { it.id == cmd.country }) return fail(Rules.UNKNOWN_ENTITY, "unknown country")
        if (cmd.capital.raw <= 0L) return fail(Rules.INVALID_ARGUMENT, "the capital must be positive")
        if (cash(cmd.company) < cmd.capital) return fail(Rules.INSUFFICIENT_FUNDS, "the company cannot pay in that much capital")
        val action = BankAction.FoundBank(cmd.name, cmd.country, cmd.company, cmd.capital, player = true)
        return when (val r = service.found(action, e.bankingInputs, tick, e.random)) {
            is Outcome.Rejected -> Outcome.Rejected(r.rejection)
            is Outcome.Ok -> {
                settleNow(tick, r.value.intents)
                Outcome.ok(CommandResult(created = r.value.bank.value, message = "bank opened"))
            }
        }
    }

    fun decide(actor: Actor, cmd: Command.BankDecision, tick: Tick): Outcome<CommandResult> {
        val action = cmd.action
        if (action is BankAction.FoundBank) return fail(Rules.INVALID_ARGUMENT, "use the found command to open a bank")
        val who = if (actor is Actor.Player) cmd.company else null
        if (action is BankAction.RaiseCapital && who != null && cash(who) < action.amount) {
            return fail(Rules.INSUFFICIENT_FUNDS, "the company cannot pay in that much capital")
        }
        return when (val r = service.apply(action, tick, e.bankingInputs, who)) {
            is Outcome.Rejected -> Outcome.Rejected(r.rejection)
            is Outcome.Ok -> {
                settleNow(tick, r.value.intents)
                Outcome.ok(CommandResult(message = r.value.message))
            }
        }
    }

    // ---- the tick ------------------------------------------------------------------------------

    /** Runs the day for the banks. Returns the day's events and the payments for the settlement batch. */
    fun step(tick: Tick): Pair<List<BankEvent>, List<SettlementIntent>> {
        if (!active) return emptyList<BankEvent>() to emptyList()
        val day = service.step(tick, e.bankingInputs, e.random)
        return day.events to normalise(day.intents)
    }

    /** Pays now what a command or setup step owes, outside the daily batch. */
    fun settleNow(tick: Tick, intents: List<SettlementIntent>) {
        if (intents.isEmpty()) return
        e.finance.settlement.settle(tick, normalise(intents))
    }

    /**
     * Moves the payments of customers who are not modelled onto the households account, so nothing is paid from or into
     * thin air, and gives a payment that repeats a key of the same tick (two decisions in one day) a key of its own.
     */
    fun normalise(intents: List<SettlementIntent>): List<SettlementIntent> {
        val seen = HashSet<String>()
        val out = ArrayList<SettlementIntent>(intents.size)
        for (i in intents) {
            val creates = i.reason == ReasonCode.CAPITAL
            val payer = if (!creates && isCustomer(i.payer)) e.finance.households else i.payer
            val payee = if (isCustomer(i.payee)) e.finance.households else i.payee
            var key = i.key
            var n = 1
            while (e.finance.ledger.hasKey(key) || !seen.add(key)) key = "${i.key}#${n++}"
            out += i.copy(key = key, payer = payer, payee = payee)
        }
        return out
    }

    private fun isCustomer(o: LedgerOwner) = o.kind == OwnerKind.SYSTEM || o.kind == OwnerKind.HOUSEHOLDS

    private companion object {
        const val RIVALS_MIN = 3
        const val RIVALS_SPREAD = 3
    }
}
