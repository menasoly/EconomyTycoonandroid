package app.many.economytycoon.banking

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.StateHash
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.SettlementIntent

/** What one tick of the banking sector produced. */
data class BankingStep(val events: List<BankEvent>, val intents: List<SettlementIntent>, val monthClosed: Boolean) {
    companion object { val EMPTY = BankingStep(emptyList(), emptyList(), false) }
}

/** A bank that has just been opened and the money its founders must put in. */
data class FoundedBank(val bank: BankId, val intents: List<SettlementIntent>)

/**
 * The banking sector of the game: every commercial bank, the deposit guarantee funds and the state of the interbank
 * markets. It is the only place that changes them. Money that has to move between banks and the rest of the economy is
 * returned as ledger intents for the caller to post; the bank's own books are always kept in balance here.
 */
class BankingService(val params: BankingParams = BankingParams()) {
    internal val book = BankingBook()
    private val planner = ResolutionPlanner(params)
    private val engine = ResolutionEngine(params, planner)
    private val runner = ResolutionRunner(book, params, planner, engine)
    private val liquidity = LiquidityManager(params)
    private val interbank = InterbankMarket(params)
    private val loanMarket = LoanMarket(params)
    private val ai = BankAiPlanner(params)
    private val daily = DailyRound(book, params, interbank, liquidity, planner, runner)
    private val monthly = MonthlyClose(book, params, loanMarket, planner, ai)
    private val actions = BankingActions(book, params)
    private val tester = StressTester(params)
    private val advisor = BankAdvisor()
    private val log = ArrayDeque<BankEvent>()

    // Queries ------------------------------------------------------------------------------------------------------------

    fun bankIds(): List<BankId> = book.ids()
    fun bank(id: BankId): BankSnapshot? = book.banks[id]?.snapshot()
    fun banks(): List<BankSnapshot> = book.banks.values.map { it.snapshot() }
    fun banksIn(country: CountryId): List<BankSnapshot> = book.banks.values.filter { it.country == country }.map { it.snapshot() }
    fun operatingIn(country: CountryId): List<BankSnapshot> = book.operating(country).map { it.snapshot() }
    fun banksOf(owner: CompanyId): List<BankSnapshot> = book.banks.values.filter { it.owner == owner && it.operator == BankOperator.PLAYER }.map { it.snapshot() }
    fun strategyOf(id: BankId): BankStrategy? = book.banks[id]?.strategy
    fun fund(country: CountryId): GuaranteeFund? = book.funds[country]
    fun fundCoverage(country: CountryId): Rate {
        val fund = book.funds[country] ?: return Rate.ZERO
        val covered = Money.sum(book.operating(country).map { GuaranteeScheme.coveredDeposits(it.snapshot(), params.deposits) })
        return GuaranteeScheme.coverage(fund, covered)
    }
    fun systemicStress(country: CountryId): Long = book.systemicStress(country)
    fun market(country: CountryId): MarketStatus? = book.market(country)
    fun lastStress(id: BankId): StressRecord? = book.stress[id]
    fun recentEvents(): List<BankEvent> = log.toList()
    fun eventsOf(id: BankId): List<BankEvent> = log.filter { it.subject == id }
    fun view(id: BankId, inputs: BankingInputs): BankView? = book.banks[id]?.let { BankView(it.snapshot(), inputs, params) }

    /** The confidence drivers of a bank today, for explaining a run to the player. */
    fun confidence(id: BankId, inputs: BankingInputs): ConfidenceDrivers? {
        val b = book.banks[id] ?: return null
        val snap = b.snapshot()
        val capital = CapitalEngine.position(snap, params.capital)
        val position = LiquidityEngine.position(snap, params.liquidity, params.deposits::insuredShare)
        return BankRuns.drivers(snap, capital, position, b.rumourPpm, b.supportPpm, book.systemicStress(b.country), fundCoverage(b.country), params.runs)
    }

    // Advice -----------------------------------------------------------------------------------------------------------------

    fun advice(id: BankId, inputs: BankingInputs, tick: Tick, limit: Int = Int.MAX_VALUE): List<BankInsight> {
        val view = view(id, inputs) ?: return emptyList()
        val record = book.stress[id]
        // The advisor looks at the latest test again on today's books, so its warnings never describe a bank that has changed.
        val scenario = record?.let { StressScenario.byId(it.scenarioId) }
        val result = scenario?.let { tester.run(view.bank, it, inputs.policyRate(view.bank.country), inputs.collateralIndex(view.bank.country)) }
        val context = AdvisorContext(tick.value, result, record?.tick, fundCoverage(view.bank.country), BankMath.YEAR_TICKS.toLong())
        return advisor.advise(view, context).filter { (book.hiddenAdvice[it.code] ?: Long.MIN_VALUE) <= tick.value }.take(limit)
    }

    fun hideAdvice(code: String, tick: Tick, days: Long = 30L) { book.hiddenAdvice[code] = tick.value + days.coerceAtLeast(1L) }
    fun showAdvice(code: String) { book.hiddenAdvice.remove(code) }
    fun hiddenAdvice(): Map<String, Long> = book.hiddenAdvice.toMap()

    // Founding ---------------------------------------------------------------------------------------------------------------

    /** Opens a new bank. The founding capital is paid in by the owner, which is returned as an intent. */
    fun found(action: BankAction.FoundBank, inputs: BankingInputs, tick: Tick, random: RandomProvider): Outcome<FoundedBank> {
        val name = action.name.trim()
        if (name.isEmpty() || name.length > 40) return Outcome.reject(Rules.INVALID_ARGUMENT, "a bank needs a name of up to 40 characters")
        if (action.capital < Money.units(params.minFoundingCapitalUnits)) return Outcome.reject(Rules.INVALID_ARGUMENT, "founding a bank takes at least ${params.minFoundingCapitalUnits} of capital")
        if (book.banks.values.any { it.name.equals(name, ignoreCase = true) }) return Outcome.reject(Rules.NAME_TAKEN, "another bank already has that name")
        if (book.banks.values.count { it.country == action.country && it.status.isOperating } >= params.maxBanksPerCountry) return Outcome.reject(Rules.LIMIT_EXCEEDED, "the market in this country is full")
        if (action.player) {
            val owner = action.owner ?: return Outcome.reject(Rules.INVALID_ARGUMENT, "a player bank needs an owning company")
            val mine = book.banks.values.count { it.operator == BankOperator.PLAYER && it.owner == owner && it.status.isOperating }
            if (mine >= params.maxPlayerBanks) return Outcome.reject(Rules.LIMIT_EXCEEDED, "a company may run at most ${params.maxPlayerBanks} banks")
        }
        val id = BankId(book.nextBankId++)
        val operator = if (action.player) BankOperator.PLAYER else BankOperator.AI
        val strategy = if (action.player) null else BankStrategies.roll(random.stream("banking.found", id.value))
        val bank = BankFactory.open(id, name, action.country, operator, action.owner, strategy, OpeningSpec(action.capital), inputs, params, tick)
        book.banks[id] = bank
        book.fundOf(action.country)
        remember(listOf(BankEvent.Founded(id, action.country, tick.value)))
        return Outcome.ok(FoundedBank(id, listOfNotNull(BankingIntents.capitalInjection(id, action.owner, action.capital, tick.value))))
    }

    /** Fills a country with computer-controlled banks of different character. */
    fun seedRivals(country: CountryId, count: Int, inputs: BankingInputs, tick: Tick, random: RandomProvider): List<BankId> {
        val made = ArrayList<BankId>()
        val names = RIVAL_NAMES
        for (i in 0 until count.coerceIn(0, params.maxBanksPerCountry)) {
            val stream = random.stream("banking.seed", country.value * 1000L + i)
            val capital = Money.units(2_000_000L + stream.nextInRange(0L, 18_000_000L))
            val base = names[(stream.nextInt(names.size) + i) % names.size]
            var name = base
            var n = 2
            while (book.banks.values.any { it.name == name }) name = "$base $n".also { n++ }
            val result = found(BankAction.FoundBank(name, country, null, capital, false), inputs, tick, random)
            result.getOrNull()?.let { made.add(it.bank) }
        }
        return made
    }

    // Actions -------------------------------------------------------------------------------------------------------------------

    /** Applies a decision of bank management. [actor] is the company that runs the bank, or null for the computer players. */
    fun apply(action: BankAction, tick: Tick, inputs: BankingInputs, actor: CompanyId?): Outcome<ActionResult> {
        val result = actions.apply(action, tick, inputs, actor)
        result.getOrNull()?.let { remember(it.events) }
        return result
    }

    /** A rumour about a bank starts to spread. [intensity] is in ppm of lost confidence. */
    fun rumour(id: BankId, intensity: Long, tick: Tick): Outcome<Unit> {
        val bank = book.banks[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown bank")
        if (!bank.status.isOperating) return Outcome.reject(Rules.WRONG_STATE, "${bank.name} no longer operates")
        if (intensity <= 0L || intensity > Rate.ONE_PPM) return Outcome.reject(Rules.INVALID_ARGUMENT, "the rumour must have a strength between 0 and 100%")
        bank.setRumour(maxOf(bank.rumourPpm, intensity))
        remember(listOf(BankEvent.Rumour(id, intensity, tick.value)))
        return Outcome.ok(Unit)
    }

    /** Stress test of every operating bank in a country, published as a report. Does not change any bank. */
    fun stressReport(country: CountryId, scenario: StressScenario, inputs: BankingInputs, tick: Tick): StressReport =
        tester.report(tick.value, operatingIn(country), scenario) { inputs.policyRate(it.country) }

    // The tick ----------------------------------------------------------------------------------------------------------------------

    /** Runs the daily round and, every month, the monthly close. */
    fun step(tick: Tick, inputs: BankingInputs, random: RandomProvider): BankingStep {
        val countries = book.countries()
        if (countries.isEmpty()) return BankingStep.EMPTY
        val out = Outputs()
        for (c in countries) daily.run(c, inputs, tick, out)
        val monthEnd = tick.value > 0L && tick.value % BankMath.MONTH_TICKS == 0L
        if (monthEnd) for (c in countries) closeMonth(c, inputs, tick, random, out)
        remember(out.events)
        return BankingStep(out.events.toList(), out.intents.toList(), monthEnd)
    }

    private fun closeMonth(country: CountryId, inputs: BankingInputs, tick: Tick, random: RandomProvider, out: Outputs) {
        val banks = book.banks.values.filter { it.country == country }
        val result = monthly.run(country, banks, inputs, tick, random, out)
        val collateral = inputs.collateralIndex(country)
        for ((id, trigger) in result.resolutions) book.banks[id]?.let { runner.execute(it, trigger, tick, collateral, out) }
        for (id in result.planningDue) {
            val bank = book.banks[id] ?: continue
            if (!bank.status.isOperating) continue
            val strategy = bank.strategy ?: continue
            val wanted = ai.plan(BankView(bank.snapshot(), inputs, params), strategy, random.stream("banking.ai", id.value))
            var done = 0
            for (action in wanted) {
                val applied = actions.apply(action, tick, inputs, null)
                applied.getOrNull()?.let { out.events.addAll(it.events); out.intents.addAll(it.intents); done++ }
            }
            if (done > 0) out.add(BankEvent.Strategy(id, "adjusted $done settings", tick.value))
        }
        for (bank in book.banks.values.filter { it.country == country && it.status == BankStatus.SUPERVISED }) {
            if (engine.release(bank, tick)) out.add(BankEvent.Released(bank.id, tick.value))
        }
        collectLevies(country, tick, out)
        if (tick.value - book.lastStressTest >= BankMath.YEAR_TICKS) publishStress(country, inputs, tick, out)
    }

    private fun collectLevies(country: CountryId, tick: Tick, out: Outputs) {
        val fund = book.fundOf(country)
        if (!GuaranteeScheme.isLevyDay(fund, tick.value, params.resolution)) return
        val banks = book.operating(country)
        val levies = GuaranteeScheme.levies(fund, banks.map { it.snapshot() }, params.resolution, params.deposits, tick.value - fund.lastLevy)
        val total = GuaranteeScheme.collect(fund, banks, levies, tick.value)
        for (levy in levies) out.add(BankingIntents.guaranteeLevy(levy.bank, country, levy.amount, tick.value))
        if (total.isPositive) out.add(BankEvent.LevyCollected(country, total, tick.value))
    }

    private fun publishStress(country: CountryId, inputs: BankingInputs, tick: Tick, out: Outputs) {
        val live = book.operating(country)
        if (live.isEmpty()) return
        val scenario = StressScenario.ADVERSE
        val report = tester.report(tick.value, live.map { it.snapshot() }, scenario) { inputs.policyRate(it.country) }
        for (r in report.results) book.stress[r.bank] = StressRecord.of(tick.value, r)
        out.add(BankEvent.StressTestPublished(country, scenario.name, report.results.count { !it.passed }, report.results.size, tick.value))
        if (book.countries().lastOrNull() == country) book.lastStressTest = tick.value
    }

    private fun remember(events: List<BankEvent>) {
        for (e in events) log.addLast(e)
        while (log.size > params.eventLog) log.removeFirst()
    }

    // State ----------------------------------------------------------------------------------------------------------------------------

    fun hashInto(h: CanonicalHasher) = book.hashInto(h)

    fun stateHash(): StateHash = CanonicalHasher().also { book.hashInto(it) }.finish()

    internal fun clearAll() { book.clear(); log.clear() }

    private companion object {
        val RIVAL_NAMES = listOf(
            "Meridian Trust", "Harbour & Crown", "Northgate Savings", "Alder Commercial", "Granite Mutual", "Lakeshore Bank",
            "Sterling Union", "Cobalt Credit", "Ridgeway Bank", "Evergreen Finance", "Copperfield Bank", "Atlas Provincial",
        )
    }
}
