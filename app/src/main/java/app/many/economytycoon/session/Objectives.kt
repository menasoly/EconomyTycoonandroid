package app.many.economytycoon.session

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.simulation.Economy

/** A goal measured from the state of the game, so it needs no storage: loading a save re-evaluates it. */
class Objective(
    val id: String,
    val title: String,
    val description: String,
    val group: String,
    private val measure: (Economy, CompanyId) -> Pair<Long, Long>,
) {
    fun progress(e: Economy, company: CompanyId): ObjectiveProgress {
        val (value, target) = measure(e, company)
        return ObjectiveProgress(this, value, target)
    }
}

data class ObjectiveProgress(val objective: Objective, val value: Long, val target: Long) {
    val done: Boolean get() = value >= target
    val ppm: Long get() = if (target <= 0L) 1_000_000L else (value.coerceAtLeast(0L) * 1_000_000L / target).coerceAtMost(1_000_000L)
}

object Objectives {
    private fun count(value: Int, target: Int) = value.toLong() to target.toLong()

    val all: List<Objective> = listOf(
        Objective("first-facility", "Open for business", "Finish building your first facility.", "Getting started") { e, c ->
            count(e.production.facilitiesOf(c).size, 1)
        },
        Objective("first-hires", "Hire a team", "Employ ten people.", "Getting started") { e, c ->
            e.workforce.employedBy(c) to 10L
        },
        Objective("first-sale", "Make a sale", "Earn revenue over a month.", "Getting started") { e, c ->
            val owner = e.companies.require(c).owner
            val now = e.tick
            val income = e.finance.statements.income(owner, app.many.economytycoon.core.Tick((now.value - 30).coerceAtLeast(0L)), now)
            (if (income.revenue.raw > 0) 1L else 0L) to 1L
        },
        Objective("in-the-black", "In the black", "Make a profit over the last month.", "Getting started") { e, c ->
            val owner = e.companies.require(c).owner
            val now = e.tick
            val income = e.finance.statements.income(owner, app.many.economytycoon.core.Tick((now.value - 30).coerceAtLeast(0L)), now)
            (if (income.netIncome.raw > 0) 1L else 0L) to 1L
        },
        Objective("three-facilities", "Growing", "Run three facilities.", "Growth") { e, c -> count(e.production.facilitiesOf(c).size, 3) },
        Objective("ten-facilities", "An industrial group", "Run ten facilities.", "Growth") { e, c -> count(e.production.facilitiesOf(c).size, 10) },
        Objective("hundred-staff", "Major employer", "Employ a hundred people.", "Growth") { e, c -> e.workforce.employedBy(c) to 100L },
        Objective("two-industries", "Diversified", "Operate in two industries.", "Growth") { e, c ->
            count(e.companies.require(c).industries.size, 2)
        },
        Objective("net-worth-1m", "A million", "Reach a net worth of one million.", "Wealth") { e, c -> netWorthUnits(e, c) to 1_000_000L },
        Objective("net-worth-10m", "Ten million", "Reach a net worth of ten million.", "Wealth") { e, c -> netWorthUnits(e, c) to 10_000_000L },
        Objective("net-worth-100m", "Tycoon", "Reach a net worth of one hundred million.", "Wealth") { e, c -> netWorthUnits(e, c) to 100_000_000L },
        Objective("first-tech", "Researcher", "Unlock a technology.", "Technology") { e, c -> count(e.research.read(c).unlockedTechs().size, 1) },
        Objective("five-techs", "Innovator", "Unlock five technologies.", "Technology") { e, c -> count(e.research.read(c).unlockedTechs().size, 5) },
        Objective("top-rated", "Creditworthy", "Earn an A rating or better.", "Finance") { e, c ->
            val rating = e.finance.company(e.companies.require(c).owner)?.rating?.rating
            val rank = rating?.let { app.many.economytycoon.finance.CreditRating.values().size - it.ordinal } ?: 0
            rank.toLong() to (app.many.economytycoon.finance.CreditRating.values().size - app.many.economytycoon.finance.CreditRating.A.ordinal).toLong()
        },
        Objective("debt-free", "Debt free", "Hold no debt while running three facilities.", "Finance") { e, c ->
            val owner = e.companies.require(c).owner
            val free = e.finance.loans.debtOf(owner).raw == 0L && e.production.facilitiesOf(c).size >= 3
            (if (free) 1L else 0L) to 1L
        },
        Objective("market-leader", "Market leader", "Have the highest net worth of all companies.", "Competition") { e, c ->
            val mine = e.finance.statements.balance(e.companies.require(c).owner, e.tick).netWorth
            val best = e.companies.active().filter { it.id != c && it.kind != CompanyKind.STATE }.maxOfOrNull { e.finance.statements.balance(it.owner, e.tick).netWorth } ?: Money.ZERO
            (if (mine > best) 1L else 0L) to 1L
        },
        Objective("buyout", "Hostile or friendly", "Gain control of another company.", "Competition") { e, c ->
            count(e.companies.groups.stakesHeldBy(c).count { e.companies.groups.controllerOf(it.first) == c }, 1)
        },
    )

    private fun netWorthUnits(e: Economy, c: CompanyId): Long = e.finance.statements.balance(e.companies.require(c).owner, e.tick).netWorth.wholeUnits()

    fun byId(id: String): Objective? = all.firstOrNull { it.id == id }

    fun evaluate(e: Economy, company: CompanyId): List<ObjectiveProgress> = all.map { it.progress(e, company) }

    fun groups(): List<String> = all.map { it.group }.distinct()
}

/** Tells when an objective has just been completed so the interface can celebrate it once. */
class ObjectiveWatcher(private val e: Economy) {
    private val done = java.util.TreeMap<CompanyId, MutableSet<String>>()

    /** Objectives completed since the previous call for this company. */
    fun newlyCompleted(company: CompanyId): List<Objective> {
        if (e.companies.company(company) == null) return emptyList()
        val seen = done.getOrPut(company) { java.util.TreeSet() }
        val fresh = ArrayList<Objective>()
        for (p in Objectives.evaluate(e, company)) if (p.done && seen.add(p.objective.id)) fresh += p.objective
        return fresh
    }

    fun completed(company: CompanyId): Set<String> = done[company].orEmpty()
}
