package app.many.economytycoon.session

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.persistence.SaveManager
import app.many.economytycoon.persistence.SaveSlot
import app.many.economytycoon.persistence.SlotKind
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.CommandOutcome
import app.many.economytycoon.simulation.CommandResult
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.simulation.TickSummary

/** Everything the screens read from, bundled so they share one instance of each report. */
class SessionReports(sim: Simulation) {
    val company = CompanyReports(sim.economy)
    val market = MarketReports(sim.economy)
    val world = WorldReports(sim.economy)
    val people = PeopleReports(sim.economy)
    val rivals = RivalReports(sim.economy, company)
    val alerts = AlertScanner(sim.economy)
    val logistics = LogisticsReports(sim.economy)
    val details = FacilityDetails(sim.economy, company, people)
    val planner = BuildPlanner(sim.economy)
}

/** Told after every tick and after anything else that changes what screens show. */
fun interface SessionListener {
    fun onChanged(session: GameSession, change: SessionChange)
}

enum class SessionChange { TICK, SPEED, SELECTION, COMMAND, SAVED, LOADED, SETTINGS, ADVICE }

/**
 * One running game as the interface sees it: the simulation, the player's identity, the clock and its speed,
 * the headlines, and saving. It owns no economic state itself. Time is driven from outside by [advanceBy],
 * which turns elapsed real time into whole ticks, so tests and the app use the same code path.
 */
class GameSession(
    val simulation: Simulation,
    val playerId: Long,
    settings: GameSettings = GameSettings(),
    private val saves: SaveManager? = null,
) {
    /** The player's preferences. They can change while the game runs, for example from the settings screen. */
    var settings: GameSettings = settings
        private set

    val reports = SessionReports(simulation)
    val news = NewsFeed(simulation.economy)
    val stats = StatsRecorder(reports.company)
    val objectives = ObjectiveWatcher(simulation.economy)

    var speed: GameSpeed = settings.defaultSpeed
        private set
    var selectedCompany: CompanyId? = null
        private set

    /**
     * True while a multiplayer layer owns the clock: [advanceBy] runs nothing (ticks come from [step], called once per
     * commit the host sends), and nothing is saved, so the simulation cannot be rolled back on one device.
     */
    var externallyClocked: Boolean = false

    /** Told about every scenario event as it happens, so progress outside the game (medals) can be kept. */
    var scenarioSink: ((app.many.economytycoon.scenarios.ScenarioEvent) -> Unit)? = null

    /** Suggestions for the selected company, and which the player has set aside. */
    val advisor = app.many.economytycoon.advisor.Advisor()
    private val advisorReader = AdvisorReader(simulation.economy, reports)

    fun adviceFacts(): app.many.economytycoon.advisor.AdvisorFacts? = selectedCompany?.let { advisorReader.read(it) }

    fun hideAdvice(code: String, days: Long = app.many.economytycoon.advisor.Advisor.DEFAULT_HIDE_DAYS) {
        advisor.hide(code, tick.value, days)
        notify(SessionChange.ADVICE)
    }

    fun showHiddenAdvice() {
        advisor.unhideAll()
        notify(SessionChange.ADVICE)
    }

    /** The electricity grids of the world; empty in a game that never set them up. */
    val energyGrids: app.many.economytycoon.energygrid.EnergyGridState get() = simulation.economy.energyGrids

    /** What the grid advisor looks at for one grid, or null when there is no such grid. */
    fun gridFacts(grid: app.many.economytycoon.energygrid.GridId): app.many.economytycoon.energygrid.GridFacts? =
        energyGrids.grid(grid)?.let { app.many.economytycoon.energygrid.GridFacts.of(energyGrids, it, tick.value, simulation.economy.energyGridService.peakDemandKw(grid)) }

    /** Today's news from the grids, worst first. */
    fun gridNews(): List<app.many.economytycoon.energygrid.GridNote> =
        lastSummary?.gridReport?.let { app.many.economytycoon.energygrid.GridNews(energyGrids, settings::money).notes(it) }.orEmpty()

    private val marketingReader = MarketingReader(simulation.economy)

    /** Reads campaigns, brands, results and plans for the marketing screen. */
    fun marketing(): MarketingReader = marketingReader

    /** A company's campaigns, brand standing, promotions and bad news. */
    fun marketingOverview(company: CompanyId): app.many.economytycoon.marketing.MarketingOverview = marketingReader.overview(company)

    /** The marketing advisor's suggestions for the selected company. */
    fun marketingInsights(): List<app.many.economytycoon.advisor.Insight> = selectedCompany?.let { marketingReader.insights(it) }.orEmpty()

    private val analysisReader = AnalysisReader(simulation.economy, reports, stats)

    /** Ratios, cost structure, runway, outlook and peer standing of the selected company. */
    fun analysis(): AnalysisView? = selectedCompany?.let { analysisReader.view(it) }

    /** How far the selected company has got through the first-steps guide. */
    fun guide(): app.many.economytycoon.guide.GuideProgress? = selectedCompany?.let { app.many.economytycoon.guide.GuideProgress.of(simulation.economy, it) }

    private val annual = app.many.economytycoon.reporting.AnnualReports(simulation.economy)

    /** Yearly results worked out from the ledger. */
    fun annualReports(): app.many.economytycoon.reporting.AnnualReports = annual

    private val sectorIntel = app.many.economytycoon.intel.SectorIntel(simulation.economy)

    /** Who makes which goods, read from the production lines of every company. */
    fun sectors(): app.many.economytycoon.intel.SectorIntel = sectorIntel

    /** A frozen copy of the game for the selected company, ready for a trial run on another thread. */
    fun outlookInput(): app.many.economytycoon.forecast.OutlookInput? = selectedCompany?.let {
        app.many.economytycoon.forecast.OutlookInput(app.many.economytycoon.forecast.Projector.capture(simulation), simulation.content, it, tick.value)
    }

    /** Names and figures as the player sees them, for describing commands. */
    fun describeNames(): app.many.economytycoon.activity.DescribeNames {
        val e = simulation.economy
        return app.many.economytycoon.activity.DescribeNames(
            company = { e.companies.company(it)?.name ?: "company ${it.value}" },
            facility = { id -> e.production.facility(id)?.let { f -> f.name.ifBlank { e.content.facilityTypeOrNull(f.type)?.name ?: f.type.key.replace('_', ' ') } } ?: "facility ${id.value}" },
            good = { e.content.goodOrNull(it)?.name ?: it.key.replace('_', ' ') },
            money = { settings.money(it) },
            quantity = { Formatting.quantity(it) },
            city = { id -> runCatching { e.world.city(id).name }.getOrDefault("city ${id.value}") },
            region = { id -> e.world.regions().firstOrNull { it.id == id }?.name ?: "region ${id.value}" },
        )
    }

    /** What the player has ordered for the selected company, newest first. */
    fun activity(limit: Int = 200): List<app.many.economytycoon.activity.ActivityEntry> =
        selectedCompany?.let { app.many.economytycoon.activity.ActivityLog.of(simulation.commandLog(), it, describeNames(), limit = limit) }.orEmpty()

    private val agendaBuilder = app.many.economytycoon.agenda.AgendaBuilder(simulation.economy)

    /** Dated payments, deliveries and deadlines for the selected company over the next [days] days. */
    fun agenda(days: Int = app.many.economytycoon.agenda.AgendaBuilder.DEFAULT_DAYS): app.many.economytycoon.agenda.Agenda? =
        selectedCompany?.let { agendaBuilder.build(it, tick.value, days) }

    private val tradeFinder = app.many.economytycoon.trade.TradeFinder(simulation.economy)

    /** Profitable buy-here-sell-there legs for a shipment of the given size, best first. */
    fun tradeLegs(quantity: app.many.economytycoon.core.Quantity = app.many.economytycoon.trade.TradeFinder.DEFAULT_QUANTITY): List<app.many.economytycoon.trade.TradeLeg> =
        tradeFinder.scan(quantity)

    /** Looks up text in the language chosen in the settings. */
    val translator: app.many.economytycoon.i18n.Translator get() = app.many.economytycoon.i18n.Translator(settings.language)

    /** The player's watchlist rules. The app replaces this with the saved book when a game starts. */
    var watch: app.many.economytycoon.watch.RuleBook = app.many.economytycoon.watch.RuleBook()

    /** Called after rules fire, so the book's cooldowns can be saved. */
    var watchSink: (() -> Unit)? = null

    private val watchLog = ArrayDeque<app.many.economytycoon.watch.WatchHit>()

    /** The most recent rule hits, newest first. */
    fun watchHits(): List<app.many.economytycoon.watch.WatchHit> = watchLog.toList().reversed()

    fun saveWatch() { watchSink?.invoke() }

    /** Saved facility set-ups and the builds waiting for one. The app replaces this with the saved library when a game starts. */
    var blueprints: app.many.economytycoon.blueprint.BlueprintController =
        app.many.economytycoon.blueprint.BlueprintController.open(app.many.economytycoon.persistence.MemorySaveStore()) { null }

    private val blueprintNoted = HashSet<Long>()

    /** Finished buildings whose blueprint was waiting for them; the player applies it from the Blueprints screen or the facility. */
    fun blueprintsReady(): List<app.many.economytycoon.blueprint.ReadyApply> = blueprints.readyPending()

    private fun checkBlueprints(tickValue: Long) {
        if (blueprints.pending.size == 0) return
        val at = app.many.economytycoon.core.Tick(tickValue)
        for (p in blueprints.expirePending()) {
            blueprintNoted.remove(p.id)
            news.post(at, NewsKind.COMPANY, "Blueprint for ${p.facilityName} was dropped: the building never appeared.")
        }
        for (r in blueprints.readyPending()) {
            if (blueprintNoted.add(r.pending.id)) news.post(at, NewsKind.COMPANY, "${r.pending.facilityName} is built. Open its facility screen to apply the blueprint.")
        }
    }

    private fun checkWatch(tickValue: Long) {
        val company = selectedCompany ?: return
        if (watch.size == 0) return
        val e = simulation.economy
        val hits = watch.evaluate(app.many.economytycoon.watch.EconomyWatchFacts(e, company), app.many.economytycoon.watch.EconomyWatchNames(e), settings::money)
        if (hits.isEmpty()) return
        for (h in hits) {
            watchLog.addLast(h)
            while (watchLog.size > WATCH_LOG_SIZE) watchLog.removeFirst()
            news.post(app.many.economytycoon.core.Tick(tickValue), NewsKind.COMPANY, "Watch: " + h.message)
        }
        saveWatch()
        if (settings.pauseOnCritical && speed != GameSpeed.PAUSED && hits.any { it.severity == app.many.economytycoon.watch.Severity.CRITICAL }) setSpeed(GameSpeed.PAUSED)
    }

    /** Which routine jobs the player has handed to managers. The app replaces this with the saved choice. */
    var managers: app.many.economytycoon.automation.ManagerSettings = app.many.economytycoon.automation.ManagerSettings()
        private set

    /** Called when the manager settings change, so they can be saved. */
    var managerSink: (() -> Unit)? = null

    private val automation = app.many.economytycoon.automation.AutomationRunner(simulation.economy)

    fun applyManagers(settings: app.many.economytycoon.automation.ManagerSettings) {
        managers = settings
        managerSink?.invoke()
        notify(SessionChange.COMMAND)
    }

    /** Loads saved manager settings without treating it as a change the player made. */
    fun restoreManagers(settings: app.many.economytycoon.automation.ManagerSettings) { managers = settings }

    /** What the managers have done lately, newest first. */
    fun managerLog(): List<app.many.economytycoon.automation.ManagerAction> = automation.recent()

    private fun runManagers(tickValue: Long) {
        if (externallyClocked || !managers.anyOn) return
        for (company in playerCompanies()) {
            for (p in automation.plan(company, managers, tickValue)) {
                issue(p.command)
                automation.record(tickValue, p)
                news.post(app.many.economytycoon.core.Tick(tickValue), NewsKind.COMPANY, "Manager: " + p.note)
            }
        }
    }

    /** The player's progress through the step-by-step tutorial. */
    val tutorial = app.many.economytycoon.tutorial.TutorialTracker()

    /** Records that the player opened a screen, which some tutorial steps ask for. */
    fun noteVisit(route: String) { tutorial.noteVisit(route) }

    /** The tutorial as it stands now, brought up to date with the game. */
    fun tutorialView(): app.many.economytycoon.tutorial.TutorialView? {
        val company = selectedCompany ?: return null
        val facts = app.many.economytycoon.tutorial.TutorialFactsReader.read(simulation.economy, company, tutorial.visitedScreens)
        tutorial.update(facts)
        return tutorial.view(facts)
    }

    private val profitAnalyzer = app.many.economytycoon.facilityprofit.ProfitAnalyzer(simulation.economy)

    /** What each of the selected company's facilities earns at today's prices, and where lines could earn more. */
    fun facilityProfit(): app.many.economytycoon.facilityprofit.PortfolioPerformance? = selectedCompany?.let { profitAnalyzer.portfolio(it) }

    private val cashForecaster = app.many.economytycoon.cashflow.CashForecaster(simulation.economy)

    /** The selected company's cash day by day under three scenarios. */
    fun cashOutlook(horizon: Int = app.many.economytycoon.cashflow.CashForecaster.DEFAULT_HORIZON): app.many.economytycoon.cashflow.CashOutlook? =
        selectedCompany?.let { cashForecaster.outlook(it, horizon) }

    private val valuationReader = app.many.economytycoon.valuation.ValuationReader(simulation.economy)

    /** What the selected company is worth under the given assumptions, from several angles. */
    fun valuation(assumptions: app.many.economytycoon.valuation.ValuationAssumptions = app.many.economytycoon.valuation.ValuationAssumptions.DEFAULT): app.many.economytycoon.valuation.ValuationSummary? =
        selectedCompany?.let { valuationReader.summary(it, assumptions) }

    fun valuationInput(): app.many.economytycoon.valuation.ValuationInput? = selectedCompany?.let { valuationReader.input(it) }

    private val chainPlanner by lazy { app.many.economytycoon.planner.ChainPlanner(simulation.content) }

    /** Goods some recipe can make, by name. */
    fun plannableGoods(): List<app.many.economytycoon.content.GoodDef> =
        simulation.content.allGoods.filter { chainPlanner.recipeFor(it.id) != null }.sortedBy { it.name }

    /** What it would take to make [perDay] units a day of [good], buying the goods in [buy] instead of making them. */
    fun planChain(good: app.many.economytycoon.core.GoodId, perDay: app.many.economytycoon.core.Quantity, buy: Set<app.many.economytycoon.core.GoodId> = emptySet()): app.many.economytycoon.planner.ChainPlan? =
        chainPlanner.plan(good, perDay, buy)

    private val exports = Exports(this)

    /** Spreadsheet files of the selected company's figures. */
    fun exports(): Exports = exports

    /** The selected company's credit score taken apart, or null before its first financial close. */
    fun credit(): app.many.economytycoon.credit.CreditExplanation? {
        val id = selectedCompany ?: return null
        val owner = simulation.economy.companies.company(id)?.owner ?: return null
        val finance = simulation.economy.finance
        val inputs = finance.creditInputs(owner, tick) ?: return null
        val held = finance.company(owner)?.rating?.rating ?: return null
        return app.many.economytycoon.credit.CreditExplainer.explain(inputs, held)
    }

    private val groupReader = GroupReader(simulation.economy, reports)

    /** The selected company and its subsidiaries added up by ownership share, or null when it owns none. */
    fun group(): app.many.economytycoon.consolidation.GroupStatements? =
        selectedCompany?.takeIf { groupReader.hasGroup(it) }?.let { groupReader.read(it) }

    /** Counts lifetime totals and awards achievements once a profile is attached to it. */
    val achievements = AchievementTracker(simulation.economy, playerId)

    /** Asked to write the profile out when it has changed enough to matter. */
    var profileSink: (() -> Unit)? = null

    private val listeners = ArrayList<SessionListener>()
    private val outcomes = ArrayDeque<CommandOutcome>()
    private var carryMillis = 0L
    private var lastSummary: TickSummary? = null

    val tick: Tick get() = simulation.tick
    val economy get() = simulation.economy
    val lastTickSummary: TickSummary? get() = lastSummary

    init {
        selectedCompany = playerCompanies().firstOrNull()
    }

    fun applySettings(next: GameSettings) {
        settings = next
        notify(SessionChange.SETTINGS)
    }

    fun addListener(l: SessionListener) { listeners += l }
    fun removeListener(l: SessionListener) { listeners -= l }
    private fun notify(change: SessionChange) { for (l in listeners.toList()) l.onChanged(this, change) }

    // ---- the player's companies ----------------------------------------------------------------

    fun playerCompanies(): List<CompanyId> =
        economy.companies.ownedBy(app.many.economytycoon.finance.Holder.player(playerId))
            .filter { it.kind == CompanyKind.PLAYER && it.isActive }.sortedBy { it.id }.map { it.id }

    fun select(company: CompanyId): Boolean {
        if (company !in playerCompanies()) return false
        selectedCompany = company
        notify(SessionChange.SELECTION)
        return true
    }

    /** True once the player has no company left to run and none can be founded for free. */
    fun isGameOver(): Boolean = playerCompanies().isEmpty()

    // ---- clock ---------------------------------------------------------------------------------

    fun setSpeed(next: GameSpeed) {
        if (next == speed) return
        speed = next
        carryMillis = 0L
        notify(SessionChange.SPEED)
    }

    fun togglePause() = setSpeed(if (speed == GameSpeed.PAUSED) settings.defaultSpeed.takeIf { it != GameSpeed.PAUSED } ?: GameSpeed.NORMAL else GameSpeed.PAUSED)

    /** Runs exactly one tick now, whatever the speed. */
    fun step(): TickSummary {
        val summary = simulation.advance()
        lastSummary = summary
        for (o in summary.commandOutcomes) {
            outcomes.addLast(o)
            while (outcomes.size > MAX_OUTCOMES) outcomes.removeFirst()
        }
        news.record(summary, simulation.pipeline.events.history)
        stats.sample(tick.value, playerCompanies())
        for (ev in summary.scenarioEvents) {
            news.post(summary.tick, NewsKind.COMPANY, ScenarioNews.headline(economy, ev))
            scenarioSink?.invoke(ev)
        }
        val earned = achievements.onTick(summary, forceCheck = summary.scenarioEvents.isNotEmpty())
        for (a in earned) news.post(summary.tick, NewsKind.COMPANY, "Achievement unlocked: ${a.title} (${a.tier.label})")
        if (earned.isNotEmpty() || achievements.shouldSave()) { profileSink?.invoke(); achievements.markSaved() }
        for (company in playerCompanies()) for (o in objectives.newlyCompleted(company)) news.post(summary.tick, NewsKind.COMPANY, "Goal reached: ${o.title}")
        checkWatch(summary.tick.value)
        checkBlueprints(summary.tick.value)
        runManagers(summary.tick.value)
        if (settings.autosave) autosave()
        if (settings.pauseOnCritical && speed != GameSpeed.PAUSED && criticalAlerts().isNotEmpty()) setSpeed(GameSpeed.PAUSED)
        notify(SessionChange.TICK)
        return summary
    }

    /**
     * Converts real elapsed time into ticks at the current speed. At most [maxTicks] run per call so a long
     * stall (the app in the background) does not freeze the screen catching up. Returns the ticks run.
     */
    fun advanceBy(elapsedMillis: Long, maxTicks: Int = 12): Int {
        if (externallyClocked || speed == GameSpeed.PAUSED || elapsedMillis <= 0L) return 0
        carryMillis += elapsedMillis
        val millisPerTick = 1_000L / speed.ticksPerSecond
        var ran = 0
        while (carryMillis >= millisPerTick && ran < maxTicks) {
            carryMillis -= millisPerTick
            step(); ran++
            if (speed == GameSpeed.PAUSED) break
        }
        if (ran == maxTicks) carryMillis = 0L
        return ran
    }

    // ---- commands ------------------------------------------------------------------------------

    /** Queues a command for the selected company's player; it is applied at the start of the next tick. */
    fun issue(command: Command): Long {
        val ticket = simulation.submit(Actor.Player(playerId), command)
        notify(SessionChange.COMMAND)
        return ticket.sequence
    }

    /** Applies a command at once, outside the clock; used by setup screens. */
    fun issueNow(command: Command): Outcome<CommandResult> {
        val r = simulation.execute(Actor.Player(playerId), command)
        if (selectedCompany == null) selectedCompany = playerCompanies().firstOrNull()
        notify(SessionChange.COMMAND)
        return r
    }

    fun outcomeOf(sequence: Long): CommandOutcome? = outcomes.firstOrNull { it.sequence == sequence }
    fun recentOutcomes(): List<CommandOutcome> = outcomes.toList().reversed()
    fun pendingCommands(): Int = simulation.pendingCommands()

    // ---- alerts --------------------------------------------------------------------------------

    fun alerts(): List<Alert> = selectedCompany?.let { reports.alerts.scan(it) }.orEmpty()
    fun criticalAlerts(): List<Alert> = alerts().filter { it.severity == Severity.CRITICAL }

    // ---- saving --------------------------------------------------------------------------------

    fun save(slot: String, label: String): SaveSlot? {
        if (externallyClocked) return null
        val saved = saves?.save(slot, simulation, label, SlotKind.MANUAL)
        if (saved != null) notify(SessionChange.SAVED)
        return saved
    }

    private fun autosave() {
        val manager = saves ?: return
        if (externallyClocked) return
        val due = tick.value % settings.autosaveEveryTicks == 0L
        if (due && manager.autosaveIfDue(simulation) != null) notify(SessionChange.SAVED)
    }

    companion object {
        const val MAX_OUTCOMES = 100
        const val WATCH_LOG_SIZE = 50

        /** Rebuilds a session around a loaded simulation. */
        fun resume(simulation: Simulation, playerId: Long, settings: GameSettings, saves: SaveManager?): GameSession =
            GameSession(simulation, playerId, settings, saves).also { it.notify(SessionChange.LOADED) }
    }
}
