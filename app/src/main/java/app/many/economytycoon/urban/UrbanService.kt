package app.many.economytycoon.urban

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick

/** What the rest of the game should apply to its own models after a month of urban life. */
data class CityOutputs(
    /** People the housing of the city can hold. */
    val housingCapacity: Long,
    /** Mean rent against what a typical household can pay, ppm: 1,000,000 is balanced. */
    val rentIndexPpm: Long,
    /** Growth boom strength, ppm above 1.0, which migration may treat as a pull. */
    val boomPpm: Long,
    val liveabilityIndex: Int,
)

/**
 * The owner of the urban partition. It creates city plans, executes commands, advances the daily and monthly cycles and
 * hands back notices and settlement intents. It never touches the ledger, the world or other modules: what it needs from
 * them arrives as [CityInputs] and what it produces leaves as [CityOutputs], notices and intents.
 */
class UrbanService(val state: UrbanState) {
    private val executor = UrbanExecutor(state)
    private val hooks = ArrayList<LobbyingHook>()
    private val lastScores = java.util.TreeMap<CityId, LiveabilityScore>()

    val params: UrbanParams get() = state.params

    /** Registers an outside influence on council votes. */
    fun addLobbyingHook(h: LobbyingHook) { hooks += h }

    /** Starts planning a city and settles its first month so that every surface and rent has a value. */
    fun addCity(city: CityId, country: CountryId, name: String, inputs: CityInputs, now: Tick, layout: List<ZoneUse>? = null): Outcome<CityPlan> {
        if (state.has(city)) return Outcome.reject(Rules.DUPLICATE, "that city already has a plan")
        if (name.isBlank()) return Outcome.reject(Rules.INVALID_ARGUMENT, "a city needs a name")
        if (inputs.population <= 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "a city needs people")
        val plan = CityFactory.create(state, city, country, name, inputs, layout)
        state.put(plan)
        val first = UrbanMonthly.run(state, plan, inputs, now)
        lastScores[city] = first.score
        return Outcome.ok(plan)
    }

    fun removeCity(city: CityId) { state.remove(city); lastScores.remove(city) }

    fun execute(cmd: UrbanCommand, now: Tick): Outcome<UrbanReceipt> = executor.execute(cmd, now)

    /** The money a company must have before [cmd] may be executed. */
    fun companyCost(cmd: UrbanCommand): Money = executor.companyCost(cmd)

    /**
     * Advances every city by one day. On the first day of a month the monthly cycle also runs, with the inputs [supplier]
     * gives for each city (cities it returns null for keep their last inputs). Cities are visited in id order.
     */
    fun step(now: Tick, monthStart: Boolean, supplier: (CityId) -> CityInputs?): UrbanStep {
        val notices = ArrayList<UrbanNotice>()
        val intents = ArrayList<app.many.economytycoon.finance.SettlementIntent>()
        for (plan in state.plans()) {
            notices += UrbanDaily.run(state, plan, hooks, now)
            if (monthStart) {
                val result = UrbanMonthly.run(state, plan, supplier(plan.city) ?: plan.inputs, now)
                lastScores[plan.city] = result.score
                notices += result.notices
                intents += result.intents
            }
        }
        return UrbanStep(notices, intents)
    }

    /** The liveability of a city as of the last monthly update, or null before the first one. */
    fun score(city: CityId): LiveabilityScore? = lastScores[city]

    /** Liveability recomputed from the saved state; use after loading a game. */
    fun refreshScores() {
        lastScores.clear()
        for (p in state.plans()) lastScores[p.city] = Liveability.score(p, UrbanAnalysis.analyse(p, params), params)
    }

    fun ranking(): List<RankEntry> {
        if (lastScores.size < state.size) refreshScores()
        return Liveability.rank(lastScores)
    }

    fun outputs(city: CityId, now: Tick): CityOutputs? {
        val plan = state.plan(city) ?: return null
        val housing = Housing.report(plan.districts, plan.inputs.population, plan.inputs.averageWage, params)
        val anchor = Housing.anchorRent(plan.inputs.averageWage, params)
        val rentIndex = if (anchor.raw <= 0L) Scale.PPM else housing.meanRentPerSqm.raw * Scale.PPM / anchor.raw
        val score = lastScores[city] ?: Liveability.score(plan, UrbanAnalysis.analyse(plan, params), params)
        return CityOutputs(housing.capacity, rentIndex, plan.events.boomPpm(now) - Scale.PPM, score.index)
    }

    /** The planner's insights about a city, most severe first. */
    fun advice(city: CityId, now: Tick): List<Insight> {
        val plan = state.plan(city) ?: return emptyList()
        val analysis = UrbanAnalysis.analyse(plan, params)
        val score = lastScores[city] ?: Liveability.score(plan, analysis, params)
        return PlanningAdvisor.advise(plan, analysis, score, params, now)
    }

    /** What a computer-controlled mayor would order for a city right now, without executing anything. */
    fun aiProposals(city: CityId, now: Tick): List<UrbanCommand> {
        val plan = state.plan(city) ?: return emptyList()
        val analysis = UrbanAnalysis.analyse(plan, params)
        val score = lastScores[city] ?: Liveability.score(plan, analysis, params)
        return MunicipalAi.decide(plan, analysis, score, params, now)
    }

    /** Lets the computer-controlled mayor act: proposes and executes. Returns each order with its outcome. */
    fun runAi(city: CityId, now: Tick): List<Pair<UrbanCommand, Outcome<UrbanReceipt>>> =
        aiProposals(city, now).map { it to execute(it, now) }

    fun stateHash() = state.stateHash()
}
