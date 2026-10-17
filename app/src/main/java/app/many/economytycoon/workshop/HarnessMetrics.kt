package app.many.economytycoon.workshop

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.simulation.Economy

/** How a headless run ended. */
enum class RunEnd(val label: String) {
    WON("Won"),
    LOST("Lost"),
    /** The run stopped at the requested number of days with the game still going. */
    HORIZON("Still going")
}

enum class LossCause(val label: String) {
    NO_COMPANY("The company is gone"),
    DEADLINE("Time ran out"),
    NET_WORTH("Net worth fell under the floor"),
    CASH("Cash fell under the floor"),
    FACILITIES("Too few facilities");
}

/** The company's position on one day. [goalValues] and [goalPpm] follow the order of the draft's goals. */
data class RunSample(
    val day: Int,
    val cash: Long,
    val netWorth: Long,
    val facilities: Int,
    val employees: Long,
    val goalValues: List<Long>,
    /** Progress on each goal, 0 to 1,000,000. */
    val goalPpm: List<Long>,
    /** Rival companies that have gone under so far. */
    val rivalsOut: Int,
)

/** What the stall probe saw on one day. */
data class StallReading(val stalled: Boolean, val cash: Long, val cheapestBuild: Long, val credit: Long, val profitableSites: Int, val openBuilds: Int) {
    fun describe(): String =
        "cash $cash and credit $credit cannot pay for the cheapest building ($cheapestBuild), and $profitableSites of the company's sites earn money"
}

/** Looks for the dead end where the company can no longer do anything that would make money. */
object SoftlockProbe {
    fun read(e: Economy, company: CompanyId): StallReading {
        val owner = e.companies.require(company).owner
        val cash = e.finance.ledger.cash(owner)
        val debt = e.finance.loans.debtOf(owner)
        val credit = Money.max(Money.ZERO, e.finance.creditTerms(owner, e.tick).limit - debt)
        val cheapest = e.content.allFacilityTypes
            .filter { it.requiredTech == null && it.extracts == null && it.maxLines > 0 }
            .minOfOrNull { it.buildCost } ?: Money.ZERO
        val sites = e.production.facilitiesOf(company)
        val profitable = sites.count { it.status == app.many.economytycoon.production.FacilityStatus.OPERATING && e.facilityStats.profitOf(it.id).raw > 0L }
        val builds = e.production.openProjectsOf(company).size
        val stalled = cash + credit < cheapest && profitable == 0 && builds == 0
        return StallReading(stalled, cash.wholeUnits(), cheapest.wholeUnits(), credit.wholeUnits(), profitable, builds)
    }
}

/** Everything one headless run produced. Plain numbers throughout, so two runs compare with ==. */
data class RunMetrics(
    val draftHash: String,
    val policy: String,
    val seed: Long,
    /** Days actually simulated; fewer than asked when the game ended early. */
    val days: Int,
    val samples: List<RunSample>,
    val end: RunEnd,
    val lossCause: LossCause?,
    /** The first day each goal (in draft order) was met, or null if it never was. */
    val goalFirstDay: List<Int?>,
    /** Whether each goal was met at some check and then lost again. */
    val goalRegressed: List<Boolean>,
    /** The first day every required goal was met at once. */
    val allDoneDay: Int?,
    val bankruptDay: Int?,
    val rivalsOut: Int,
    val softlockDay: Int?,
    val softlockNote: String?,
    val log: List<String>,
    val stateHash: String,
    val invariantProblems: List<String> = emptyList(),
) {
    val last: RunSample get() = samples.last()
    val won: Boolean get() = end == RunEnd.WON
    val cashCurve: List<Pair<Int, Long>> get() = samples.map { it.day to it.cash }
    val netWorthCurve: List<Pair<Int, Long>> get() = samples.map { it.day to it.netWorth }
    val peakNetWorth: Long get() = samples.maxOf { it.netWorth }

    /** Weighted progress over all goals at the end, 0 to 1,000,000. */
    fun weightedProgressPpm(draft: ScenarioDraft): Long = WeightedProgress.ppm(draft, last.goalPpm)
}

object WeightedProgress {
    /** Mean of the goals' progress, each weighted as the draft says. Zero when there are no goals. */
    fun ppm(draft: ScenarioDraft, goalPpm: List<Long>): Long {
        var sum = 0L
        var weights = 0L
        for ((i, g) in draft.goals.withIndex()) {
            val p = goalPpm.getOrNull(i) ?: continue
            sum += p * g.weight
            weights += g.weight
        }
        return if (weights == 0L) 0L else sum / weights
    }

    /** Mean progress over required goals only, each weighted as the draft says. */
    fun requiredPpm(draft: ScenarioDraft, goalPpm: List<Long>): Long {
        var sum = 0L
        var weights = 0L
        for ((i, g) in draft.goals.withIndex()) {
            if (g.bonus) continue
            val p = goalPpm.getOrNull(i) ?: continue
            sum += p * g.weight
            weights += g.weight
        }
        return if (weights == 0L) 0L else sum / weights
    }
}
