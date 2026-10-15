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
        if (speed == GameSpeed.PAUSED || elapsedMillis <= 0L) return 0
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
        val saved = saves?.save(slot, simulation, label, SlotKind.MANUAL)
        if (saved != null) notify(SessionChange.SAVED)
        return saved
    }

    private fun autosave() {
        val manager = saves ?: return
        val due = tick.value % settings.autosaveEveryTicks == 0L
        if (due && manager.autosaveIfDue(simulation) != null) notify(SessionChange.SAVED)
    }

    companion object {
        const val MAX_OUTCOMES = 100

        /** Rebuilds a session around a loaded simulation. */
        fun resume(simulation: Simulation, playerId: Long, settings: GameSettings, saves: SaveManager?): GameSession =
            GameSession(simulation, playerId, settings, saves).also { it.notify(SessionChange.LOADED) }
    }
}
