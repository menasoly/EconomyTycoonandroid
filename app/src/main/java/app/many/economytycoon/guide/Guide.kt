package app.many.economytycoon.guide

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.simulation.Economy

/** The order in which a first game is best learned. Each step builds on what the earlier ones set up. */
object Guide {
    private fun facilities(e: Economy, c: CompanyId) = e.production.facilitiesOf(c).filter { it.status != FacilityStatus.DEMOLISHED }

    private fun monthRevenue(e: Economy, c: CompanyId): Long {
        val owner = e.companies.require(c).owner
        val from = Tick((e.tick.value - 30).coerceAtLeast(0L))
        return e.finance.statements.income(owner, from, e.tick).revenue.raw
    }

    private fun monthProfit(e: Economy, c: CompanyId): Long {
        val owner = e.companies.require(c).owner
        val from = Tick((e.tick.value - 30).coerceAtLeast(0L))
        return e.finance.statements.income(owner, from, e.tick).netIncome.raw
    }

    val steps: List<GuideStep> = listOf(
        GuideStep(
            "first-day", "Let a day pass",
            "Nothing happens until the clock runs. Wages, rent and prices all move a day at a time.",
            "Press play at the top of the screen, then pause again when the date changes.",
            GuideArea.OVERVIEW,
        ) { e, _ -> e.tick.value >= 1L },
        GuideStep(
            "build-first", "Build a facility",
            "Facilities are where goods are made. A farm or mine needs no input goods, so it is the safest start.",
            "Open Facilities, choose Build, pick a type you can afford and confirm.",
            GuideArea.BUILD,
        ) { e, c -> facilities(e, c).isNotEmpty() },
        GuideStep(
            "set-recipe", "Give it work to do",
            "A facility only produces once a production line has a recipe.",
            "Open the facility, choose a line and pick a recipe. Check that its inputs are in stock.",
            GuideArea.BUILD,
        ) { e, c -> facilities(e, c).any { f -> f.lines.any { it.recipe != null } } },
        GuideStep(
            "hire-staff", "Hire people",
            "Lines need staff to run. Without them the facility stands idle however well stocked it is.",
            "Open People, choose a role for your facility and raise the number wanted.",
            GuideArea.PEOPLE,
        ) { e, c -> e.workforce.employedBy(c) >= 1L },
        GuideStep(
            "first-output", "Produce something",
            "Output in storage is the first sign that the chain works.",
            "Run the clock for a few days and open the facility to see stock rise.",
            GuideArea.BUILD,
        ) { e, c -> facilities(e, c).any { f -> f.lines.any { it.batchesCompleted > 0L } } },
        GuideStep(
            "first-sale", "Make a sale",
            "Revenue pays the wages. Goods that are not sold cost storage and tie up cash.",
            "Open Market, choose the good and sell it, or set an automation rule in the next step.",
            GuideArea.MARKET,
        ) { e, c -> monthRevenue(e, c) > 0L },
        GuideStep(
            "sell-rule", "Automate selling",
            "A rule sells your surplus every day, so you do not have to watch every stockroom.",
            "Open Automation, add a sell rule for a good you make and choose how you want to price it.",
            GuideArea.RULES,
        ) { e, c -> e.companies.policies.sellRules(c).isNotEmpty() },
        GuideStep(
            "second-facility", "Grow to two facilities",
            "A second facility can feed the first or sell to someone else, and it spreads your risk.",
            "Build again, ideally something that uses what your first facility makes.",
            GuideArea.BUILD,
        ) { e, c -> facilities(e, c).size >= 2 },
        GuideStep(
            "staff-up", "Employ ten people",
            "A bigger team lets you run more lines at once. Watch wages as they grow.",
            "Raise staffing on the People screen until ten are employed.",
            GuideArea.PEOPLE,
        ) { e, c -> e.workforce.employedBy(c) >= 10L },
        GuideStep(
            "research", "Start researching",
            "Technology unlocks better recipes and machines, and it takes days or weeks to arrive.",
            "Open Research, pick something affordable and start it.",
            GuideArea.RESEARCH,
        ) { e, c ->
            val r = e.research.peek(c)
            r != null && (r.unlockedTechs().isNotEmpty() || r.projects().isNotEmpty())
        },
        GuideStep(
            "turn-profit", "Turn a profit",
            "Profit over a month shows that income covers every cost, not just the wages.",
            "Look at Statements. If costs are high, check which line is idle or which good sells below cost.",
            GuideArea.FINANCE,
        ) { e, c -> monthProfit(e, c) > 0L },
        GuideStep(
            "borrow", "Take out a loan",
            "Borrowed money can pay for growth that your cash cannot, at the price of interest.",
            "Open Loans and borrow a modest amount you can repay out of monthly profit.",
            GuideArea.LOANS,
        ) { e, c -> e.finance.loans.forBorrower(e.companies.require(c).owner).isNotEmpty() },
    )

    fun byId(id: String): GuideStep? = steps.firstOrNull { it.id == id }

    fun statuses(e: Economy, company: CompanyId): List<StepStatus> {
        var currentFound = false
        return steps.map { step ->
            when {
                step.isDone(e, company) -> StepStatus(step, StepState.DONE)
                !currentFound -> { currentFound = true; StepStatus(step, StepState.CURRENT) }
                else -> StepStatus(step, StepState.LATER)
            }
        }
    }
}

/** How far a company has got through the guide. */
class GuideProgress(val statuses: List<StepStatus>) {
    val total: Int get() = statuses.size
    val doneCount: Int get() = statuses.count { it.state == StepState.DONE }
    val finished: Boolean get() = doneCount == total
    val current: GuideStep? get() = statuses.firstOrNull { it.state == StepState.CURRENT }?.step
    val ppm: Long get() = if (total == 0) 1_000_000L else doneCount * 1_000_000L / total

    companion object {
        fun of(e: Economy, company: CompanyId): GuideProgress = GuideProgress(Guide.statuses(e, company))
    }
}
