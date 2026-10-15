package app.many.economytycoon.scenarios

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.CreditRating
import app.many.economytycoon.finance.Holder
import app.many.economytycoon.simulation.Economy

/** What a scenario can ask the player to reach. Every kind is measured from the state of the game. */
enum class GoalKind(val noun: String) {
    NET_WORTH("net worth"),
    CASH("cash in hand"),
    MONTHLY_PROFIT("profit over a month"),
    FACILITIES("facilities"),
    EMPLOYEES("employees"),
    TECHNOLOGIES("technologies"),
    INDUSTRIES("industries"),
    PARCELS("parcels of land"),
    RENT_INCOME("rent a month"),
    INVESTED("in bonds and futures"),
    COMPANIES("companies controlled"),
    CREDIT_RANK("credit rating steps"),
    MARKET_LEADER("market leadership"),
    DEBT_FREE("facilities without debt"),
}

/** One target: reach at least [target] of [kind]. Money targets are in whole currency units. */
data class Goal(val kind: GoalKind, val target: Long) {
    init { require(target > 0L) { "a goal needs a positive target" } }

    fun describe(): String = when (kind) {
        GoalKind.NET_WORTH -> "Reach a net worth of ${money(target)}"
        GoalKind.CASH -> "Hold ${money(target)} in cash"
        GoalKind.MONTHLY_PROFIT -> "Earn ${money(target)} profit in a month"
        GoalKind.FACILITIES -> "Run $target facilities"
        GoalKind.EMPLOYEES -> "Employ $target people"
        GoalKind.TECHNOLOGIES -> "Unlock $target technologies"
        GoalKind.INDUSTRIES -> "Operate in $target industries"
        GoalKind.PARCELS -> "Own $target parcels of land"
        GoalKind.RENT_INCOME -> "Collect ${money(target)} a month in rent"
        GoalKind.INVESTED -> "Have ${money(target)} in bonds and futures"
        GoalKind.COMPANIES -> "Control $target other companies"
        GoalKind.CREDIT_RANK -> "Earn a credit rating $target steps above the bottom"
        GoalKind.MARKET_LEADER -> "Become the richest company"
        GoalKind.DEBT_FREE -> "Stay free of debt while running at least $target facilities"
    }

    companion object {
        fun money(units: Long): String = when {
            units >= 1_000_000L && units % 1_000_000L == 0L -> "${units / 1_000_000L} million"
            units >= 1_000L && units % 1_000L == 0L -> "${units / 1_000L} thousand"
            else -> units.toString()
        }
    }
}

/** How far along a goal is: [value] now against its [goal] target. */
data class GoalProgress(val goal: Goal, val value: Long) {
    val done: Boolean get() = value >= goal.target
    val ppm: Long get() = (value.coerceAtLeast(0L) * 1_000_000L / goal.target).coerceAtMost(1_000_000L)
}

object GoalMeter {
    fun playerCompanies(e: Economy, playerId: Long): List<CompanyId> =
        e.companies.ownedBy(Holder.player(playerId)).filter { it.kind == CompanyKind.PLAYER && it.isActive }.sortedBy { it.id }.map { it.id }

    fun netWorthUnits(e: Economy, c: CompanyId): Long = e.finance.statements.balance(e.companies.require(c).owner, e.tick).netWorth.wholeUnits()

    /** The best value any of the player's companies has for [goal]. */
    fun measure(e: Economy, playerId: Long, goal: Goal): Long =
        playerCompanies(e, playerId).maxOfOrNull { valueFor(e, it, goal) } ?: 0L

    fun progress(e: Economy, playerId: Long, goals: List<Goal>): List<GoalProgress> = goals.map { GoalProgress(it, measure(e, playerId, it)) }

    fun valueFor(e: Economy, c: CompanyId, goal: Goal): Long {
        val owner = e.companies.require(c).owner
        return when (goal.kind) {
            GoalKind.NET_WORTH -> netWorthUnits(e, c)
            GoalKind.CASH -> e.finance.ledger.cash(owner).wholeUnits()
            GoalKind.MONTHLY_PROFIT -> {
                val now = e.tick
                e.finance.statements.income(owner, Tick((now.value - 30).coerceAtLeast(0L)), now).netIncome.wholeUnits()
            }
            GoalKind.FACILITIES -> e.production.facilitiesOf(c).size.toLong()
            GoalKind.EMPLOYEES -> e.workforce.employedBy(c)
            GoalKind.TECHNOLOGIES -> e.research.of(c).unlockedTechs().size.toLong()
            GoalKind.INDUSTRIES -> e.companies.require(c).industries.size.toLong()
            GoalKind.PARCELS -> e.world.land.ownedBy(c).size.toLong()
            GoalKind.RENT_INCOME -> e.propertyRunner.monthlyIncome(c).wholeUnits()
            GoalKind.INVESTED -> (e.bondRunner.worth(c, e.tick) + e.futuresRunner.worth(c)).wholeUnits()
            GoalKind.COMPANIES -> e.companies.groups.stakesHeldBy(c).count { e.companies.groups.controllerOf(it.first) == c }.toLong()
            GoalKind.CREDIT_RANK -> e.finance.company(owner)?.rating?.rating?.let { (CreditRating.values().size - it.ordinal).toLong() } ?: 0L
            GoalKind.MARKET_LEADER -> {
                val mine = e.finance.statements.balance(owner, e.tick).netWorth
                val best = e.companies.active().filter { it.id != c && it.kind != CompanyKind.STATE }
                    .maxOfOrNull { e.finance.statements.balance(it.owner, e.tick).netWorth } ?: Money.ZERO
                if (mine > best) 1L else 0L
            }
            GoalKind.DEBT_FREE -> if (e.finance.loans.debtOf(owner).raw == 0L) e.production.facilitiesOf(c).size.toLong() else 0L
        }
    }
}
