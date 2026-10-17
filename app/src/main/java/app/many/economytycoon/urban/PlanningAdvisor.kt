package app.many.economytycoon.urban

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

enum class InsightSeverity { INFO, NOTICE, WARNING, CRITICAL }

enum class InsightCategory(val label: String) {
    HOUSING("Housing"), EQUITY("Equity"), TRANSPORT("Transport"), BUDGET("Budget"), ZONING("Zoning"),
    POLITICS("Politics"), PROJECTS("Projects"), ENVIRONMENT("Environment"), OVERALL("Overall"),
}

/** A piece of advice about a city: what is wrong (or right), why it matters and what could be done. */
data class Insight(
    val city: CityId,
    val category: InsightCategory,
    val severity: InsightSeverity,
    /** The district concerned, or null for the whole city. */
    val district: Int?,
    val title: String,
    val detail: String,
    val suggestion: String?,
)

/**
 * Reads a city and says what a planner should worry about. Pure and deterministic: the same plan always gives the same
 * list in the same order (most severe first, then by category, district and title).
 */
object PlanningAdvisor {
    fun advise(plan: CityPlan, analysis: CityAnalysis, score: LiveabilityScore, params: UrbanParams, now: Tick): List<Insight> {
        val out = ArrayList<Insight>()
        housing(plan, params, out)
        equity(plan, out)
        transport(plan, out)
        budget(plan, params, out)
        zoning(plan, analysis, out)
        politics(plan, params, out)
        projects(plan, out)
        environment(plan, analysis, out)
        overall(plan, score, out)
        return out.sortedWith(compareBy<Insight>({ -it.severity.ordinal }, { it.category.ordinal }, { it.district ?: -1 }, { it.title }))
    }

    /** A floor-area ratio in thousandths as text, e.g. 2400 becomes "2.40". */
    fun ratioText(milli: Int): String = "${milli / 1000}.${(milli % 1000 / 10).toString().padStart(2, '0')}"

    private fun add(out: MutableList<Insight>, plan: CityPlan, c: InsightCategory, s: InsightSeverity, d: Int?, title: String, detail: String, tip: String?) {
        out += Insight(plan.city, c, s, d, title, detail, tip)
    }

    private fun housing(plan: CityPlan, params: UrbanParams, out: MutableList<Insight>) {
        val report = Housing.report(plan.districts, plan.inputs.population, plan.inputs.averageWage, params)
        if (report.unhoused > 0L) {
            add(out, plan, InsightCategory.HOUSING, InsightSeverity.CRITICAL, null, "${report.unhoused} people have no home",
                "Housing holds ${report.capacity} people but the city has ${plan.inputs.population}.",
                "Raise the floor-area limits where rents are highest, or build public housing.")
        } else if (report.vacancyPpm < 25_000L) {
            val tightest = plan.districts.filter { it.residentialArea > 0L }.maxByOrNull { it.rentPerSqm.raw }
            add(out, plan, InsightCategory.HOUSING, InsightSeverity.WARNING, tightest?.index, "Housing is very tight",
                "Only ${report.vacancyPpm / 10_000L}% of homes are empty, so rents keep rising.",
                tightest?.let { "Allow more floor area in ${it.name}, where rents are highest." })
        }
        for (d in plan.districts) {
            if (d.residentialArea > 0L && d.vacancyPpm > 150_000L) {
                add(out, plan, InsightCategory.HOUSING, InsightSeverity.INFO, d.index, "${d.name} has too many empty homes",
                    "${d.vacancyPpm / 10_000L}% of its homes stand empty; builders will stay away until demand catches up.", null)
            }
            if (d.zone != ZoneUse.GREEN && d.zone != ZoneUse.CIVIC && d.headroom == 0L && d.floorArea > 0L && d.vacancyPpm < params.targetVacancyPpm) {
                add(out, plan, InsightCategory.ZONING, InsightSeverity.NOTICE, d.index, "${d.name} is built to its limit",
                    "Floor area has reached ${ratioText(d.farMilli)} times the land and homes are scarce.", "A density variance would let builders add more.")
            }
        }
    }

    private fun equity(plan: CityPlan, out: MutableList<Insight>) {
        for (d in plan.districts) {
            if (d.backlashPpm >= EventGenerator.BACKLASH_THRESHOLD_PPM) {
                add(out, plan, InsightCategory.EQUITY, InsightSeverity.CRITICAL, d.index, "Protests over displacement in ${d.name}",
                    "Anger has reached ${d.backlashPpm / 10_000L}% after ${d.displacedTotal} people were pushed out.",
                    "Raise tenant protection or housing aid, and slow the rise in rents by building elsewhere.")
            } else if (Gentrification.pressurePpm(d) > 80_000L) {
                add(out, plan, InsightCategory.EQUITY, InsightSeverity.WARNING, d.index, "${d.name} is gentrifying",
                    "Rents rose ${d.rentGrowthPpm() / 10_000L}% over the year and the low-income share is ${d.lowIncomeSharePpm / 10_000L}%.",
                    "Tenant protection slows displacement at the price of slower building.")
            }
        }
    }

    private fun transport(plan: CityPlan, out: MutableList<Insight>) {
        val t = plan.traffic
        if (t.congestionPpm > 300_000L) {
            add(out, plan, InsightCategory.TRANSPORT, InsightSeverity.WARNING, null, "Roads are congested",
                "Traffic runs ${t.congestionPpm / 10_000L}% slower than free flow and the average commute is ${t.averageCommuteSeconds / 60} minutes.",
                "A metro or rail line, or wider roads on the worst links, would take cars off the road.")
        }
        val worst = plan.network.roads().maxByOrNull { it.volumeCapacityPpm }
        if (worst != null && worst.levelOfService >= LevelOfService.E) {
            val a = plan.districts[worst.a].name
            val b = plan.districts[worst.b].name
            add(out, plan, InsightCategory.TRANSPORT, InsightSeverity.WARNING, null, "The road between $a and $b is jammed",
                "It carries ${worst.flowVph} vehicles an hour on a capacity of ${worst.capacityVph}.", "Upgrade the road or give drivers another way to travel.")
        }
        for (l in plan.network.activeLines()) {
            if (l.loadRatioPpm > 900_000L) {
                add(out, plan, InsightCategory.TRANSPORT, InsightSeverity.CRITICAL, null, "${l.name} is overcrowded",
                    "Its fullest stretch runs at ${l.loadRatioPpm / 10_000L}% of capacity.", "Shorten the headway or build a parallel line.")
            } else if (l.loadRatioPpm in 1L..120_000L && l.monthlyRiders > 0L) {
                add(out, plan, InsightCategory.TRANSPORT, InsightSeverity.INFO, null, "${l.name} is little used",
                    "At its busiest it fills only ${l.loadRatioPpm / 10_000L}% of its seats.", "Lengthen the headway to save running costs.")
            }
        }
        val unserved = plan.districts.filter { d -> d.residents > 0L && plan.network.linesServing(d.index).isEmpty() }
        if (unserved.isNotEmpty() && plan.population > 0L) {
            val people = unserved.sumOf { it.residents }
            if (people * 4L > plan.population) {
                add(out, plan, InsightCategory.TRANSPORT, InsightSeverity.NOTICE, null, "A quarter of residents have no public transport",
                    "${unserved.size} districts with $people people are not on any line.", "Plan a line through ${unserved.first().name}.")
            }
        }
    }

    private fun budget(plan: CityPlan, params: UrbanParams, out: MutableList<Insight>) {
        val b = plan.budget
        val monthly = b.totalServiceCost(plan.population)
        if (b.cash < monthly) {
            add(out, plan, InsightCategory.BUDGET, InsightSeverity.WARNING, null, "The city is short of cash",
                "Cash covers less than a month of services.", "Raise taxes, cut services or borrow while credit is good.")
        }
        if (b.consecutiveDeficitMonths >= 3) {
            add(out, plan, InsightCategory.BUDGET, InsightSeverity.WARNING, null, "Deficit for ${b.consecutiveDeficitMonths} months",
                "Spending has exceeded revenue every month since ${b.consecutiveDeficitMonths} months ago.", "Credit rating is ${b.rating() / 10}.")
        }
        if (b.rating() < 300) {
            add(out, plan, InsightCategory.BUDGET, InsightSeverity.CRITICAL, null, "Credit is poor",
                "Debt is ${b.debt} against a limit of ${b.debtLimit(params)}; lenders charge ${b.interestRatePpm() / 10_000L}% a year.", "Pay down debt before starting new projects.")
        }
        val funding = b.lastReport?.fundingRatioPpm ?: Scale.PPM
        if (funding < 900_000L) {
            add(out, plan, InsightCategory.BUDGET, InsightSeverity.CRITICAL, null, "Services are underfunded",
                "Only ${funding / 10_000L}% of the planned service spending could be paid.", "Raise revenue or lower the service levels you promise.")
        }
    }

    private fun zoning(plan: CityPlan, analysis: CityAnalysis, out: MutableList<Insight>) {
        val g = plan.geometry
        val clashes = ArrayList<Pair<Int, Int>>()
        for (d in plan.districts) for (n in g.neighbours(d.index)) {
            if (n > d.index && Zoning.compatibility(d.zone, plan.districts[n].zone) <= -300) clashes += d.index to n
        }
        for ((a, b) in clashes.take(3)) {
            add(out, plan, InsightCategory.ZONING, InsightSeverity.NOTICE, a, "${plan.districts[a].name} and ${plan.districts[b].name} clash",
                "${plan.districts[a].zone.label} sits next to ${plan.districts[b].zone.label}, which drags down land values on both sides.",
                "Rezone one of them or put a park between.")
        }
        val green = plan.districts.filter { it.zone == ZoneUse.GREEN }.sumOf { it.landSqm }
        if (plan.population > 0L && green / plan.population < 6L) {
            add(out, plan, InsightCategory.ZONING, InsightSeverity.NOTICE, null, "Little green space",
                "There are ${green / plan.population} square metres of parkland per resident.", "Build a park or rezone a quiet district as green space.")
        }
    }

    private fun politics(plan: CityPlan, params: UrbanParams, out: MutableList<Insight>) {
        val c = plan.council
        val housingTight = Housing.report(plan.districts, plan.inputs.population, plan.inputs.averageWage, params).vacancyPpm < 30_000L
        if (housingTight && c.growthSeats() < c.majority) {
            add(out, plan, InsightCategory.POLITICS, InsightSeverity.NOTICE, null, "The council leans against growth",
                "Only ${c.growthSeats()} of ${c.seats.size} members favour building, while housing is short.", "Expect upzoning petitions to fail without public support.")
        }
        for (p in plan.petitions.open()) {
            if (p.status == PetitionStatus.HEARING && p.staffScore < -300) {
                add(out, plan, InsightCategory.POLITICS, InsightSeverity.INFO, p.district, "Planners oppose the petition for ${plan.districts[p.district].name}",
                    "Staff recommend against it (score ${p.staffScore}).", null)
            }
        }
        if (c.moodAgainstGrowth > 600) {
            add(out, plan, InsightCategory.POLITICS, InsightSeverity.WARNING, null, "Voters are angry about growth",
                "Mood against growth stands at ${c.moodAgainstGrowth / 10}%.", "Protect tenants before pushing more development.")
        }
    }

    private fun projects(plan: CityPlan, out: MutableList<Insight>) {
        for (p in plan.projects.running()) {
            if (p.status == ProjectStatus.STALLED) {
                add(out, plan, InsightCategory.PROJECTS, InsightSeverity.CRITICAL, p.district, "${p.name} has stopped", "Stalled for ${p.stalledDays} days for lack of money.", "Free up cash or cancel it.")
            } else if (p.overrunPpm > 200_000L) {
                add(out, plan, InsightCategory.PROJECTS, InsightSeverity.WARNING, p.district, "${p.name} is ${p.overrunPpm / 10_000L}% over budget", "It was ${p.originalBudget} and is now ${p.budget}.", null)
            }
        }
    }

    private fun environment(plan: CityPlan, analysis: CityAnalysis, out: MutableList<Insight>) {
        for (d in plan.districts) {
            if (d.residents == 0L) continue
            val f = analysis.factors[d.index]
            if (f.pollution > 400) {
                add(out, plan, InsightCategory.ENVIRONMENT, InsightSeverity.WARNING, d.index, "Air in ${d.name} is bad",
                    "Pollution reaches ${f.pollution / 10}% of the worst level.", "Fund sanitation or move industry away from homes.")
            }
            if (f.crime > 450) {
                add(out, plan, InsightCategory.ENVIRONMENT, InsightSeverity.NOTICE, d.index, "Crime is high in ${d.name}", "Crime exposure is ${f.crime / 10}%.", "Raise police funding.")
            }
        }
    }

    private fun overall(plan: CityPlan, score: LiveabilityScore, out: MutableList<Insight>) {
        val weakest = score.weakest()
        add(out, plan, InsightCategory.OVERALL, InsightSeverity.INFO, null, "Weakest point: ${weakest.label.lowercase()}",
            "It scores ${score.of(weakest) / 10} out of 100; liveability overall is ${score.index / 10}.", null)
    }
}
