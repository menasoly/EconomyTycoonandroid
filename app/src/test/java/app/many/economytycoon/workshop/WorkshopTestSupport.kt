package app.many.economytycoon.workshop

import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.world.WorldSize

/** Drafts and runs shared by the workshop tests. */
object Samples {
    val content get() = SimTestSupport.content

    /** A draft that uses every field, valid against the shipped content. */
    fun full(): ScenarioDraft = ScenarioDraft(
        id = "full-house",
        title = "Full House",
        tagline = "Everything switched on.",
        story = "A draft that uses \"every\" field, including a\nsecond line and a back\\slash.",
        seed = 4242L,
        world = WorldSettings(WorldSize.SMALL, rivals = 2, countryIndex = 0),
        company = CompanySetup("Full House Ltd", 400_000L, listOf(StarterSite("grow_wheat", 0), StarterSite("raise_poultry", 1))),
        rules = RuleOverrides.of(RuleKey.SPENDING_PROPENSITY to 20_000L, RuleKey.HOUSEHOLD_CASH to 200L),
        events = listOf(
            ScriptedEvent(90, EventKind.RIVAL_GRANT, 50_000L, "Rivals get a loan."),
            ScriptedEvent(30, EventKind.GRANT, 25_000L, "Seed money"),
            ScriptedEvent(60, EventKind.ANNOUNCE, 0L, "Rain is coming."),
            ScriptedEvent(120, EventKind.LEVY, 10_000L),
        ),
        goals = listOf(
            DraftGoal(GoalKind.NET_WORTH, 900_000L, weight = 3),
            DraftGoal(GoalKind.FACILITIES, 4, weight = 2),
            DraftGoal(GoalKind.CASH, 300_000L, weight = 1, bonus = true),
        ),
        fails = listOf(FailCondition(FailKind.NET_WORTH_BELOW, 50_000L, afterDay = 30), FailCondition(FailKind.FACILITIES_BELOW, 1, afterDay = 100)),
        timing = Timing(years = 4, goldDays = 700, silverDays = 1_100),
        tags = listOf(DifficultyTag.HARSH, DifficultyTag.SPRINT),
        hints = listOf("Staff every site.", "Watch the wages."),
        packs = listOf("base:1.0", "agriculture:1.0"),
    ).normalised()

    /** The smallest sound draft. */
    fun minimal(id: String = "tiny"): ScenarioDraft = DraftDefaults.blank(id, "Tiny Test")

    /** A small world and a short deadline, cheap to play. */
    fun cheap(id: String = "cheap-run"): ScenarioDraft = ScenarioDraft(
        id = id, title = "Cheap Run", seed = 7L,
        world = WorldSettings(WorldSize.SMALL, rivals = 2, countryIndex = 0),
        company = CompanySetup("Cheap Co", 300_000L, listOf(StarterSite("grow_wheat", 0))),
        goals = listOf(DraftGoal(GoalKind.NET_WORTH, 400_000L, weight = 2), DraftGoal(GoalKind.EMPLOYEES, 30)),
        timing = Timing(years = 2, goldDays = 300, silverDays = 500),
        tags = listOf(DifficultyTag.STANDARD),
    ).normalised()

    /** A made-up run, for tests of the analysis that should not need a simulation. */
    fun run(
        draft: ScenarioDraft,
        policy: String,
        seed: Long = 1L,
        days: Int = 60,
        start: List<Long>? = null,
        end: List<Long>,
        outcome: RunEnd = RunEnd.HORIZON,
        wonOn: Int? = null,
        netWorth: Long = 300_000L,
        cash: Long = 100_000L,
        softlock: Int? = null,
        bankrupt: Int? = null,
        regressed: List<Boolean>? = null,
        firstDay: List<Int?>? = null,
        invariants: List<String> = emptyList(),
    ): RunMetrics {
        val n = draft.goals.size
        val s0 = start ?: List(n) { 0L }
        val first = RunSample(0, 300_000L, 300_000L, 1, 5, List(n) { 0L }, s0, 0)
        val last = RunSample(days, cash, netWorth, 2, 12, List(n) { 0L }, end, 0)
        return RunMetrics(
            draftHash = draft.stateHash(), policy = policy, seed = seed, days = days, samples = listOf(first, last), end = outcome,
            lossCause = if (outcome == RunEnd.LOST) LossCause.NET_WORTH else null,
            goalFirstDay = firstDay ?: List(n) { null }, goalRegressed = regressed ?: List(n) { false },
            allDoneDay = if (outcome == RunEnd.WON) (wonOn ?: days) else null, bankruptDay = bankrupt, rivalsOut = 0,
            softlockDay = softlock, softlockNote = softlock?.let { "stuck for test" }, log = emptyList(), stateHash = "h-$policy-$seed-$days",
            invariantProblems = invariants,
        )
    }
}

/** A bare simulation for the draft, with the player's company founded, for tests that look inside the economy. */
object Simulation0 {
    fun sim(d: ScenarioDraft): app.many.economytycoon.simulation.Simulation =
        app.many.economytycoon.simulation.Simulation(DraftConverter.config(d), Samples.content).also { HarnessSetup.found(it, d) }
}

fun companyOf(d: ScenarioDraft): app.many.economytycoon.core.CompanyId {
    val sim = Simulation0.sim(d)
    return sim.economy.companies.ownedBy(app.many.economytycoon.finance.Holder.player(HarnessSetup.PLAYER_ID)).minOf { it.id }
}
