package app.many.economytycoon.workshop

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.TransactionDraft
import app.many.economytycoon.scenarios.Goal
import app.many.economytycoon.scenarios.GoalMeter
import app.many.economytycoon.simulation.Economy
import app.many.economytycoon.simulation.Invariants
import app.many.economytycoon.simulation.Simulation

/** How a headless run is conducted. */
data class HarnessSettings(
    /** Days to play at most. */
    val days: Int = 90,
    /** Days between samples of the company's position. */
    val sampleEvery: Int = 10,
    /** Days between checks of goals, failure conditions and the stall probe. The game itself checks every five. */
    val checkEvery: Int = 5,
    /** Consecutive days of stall before it counts as a softlock. */
    val stallDays: Int = 30,
    /** Run the ledger invariant check after every tick. Slower. */
    val checkInvariants: Boolean = false,
) {
    init {
        require(days >= 1) { "a run needs at least a day" }
        require(sampleEvery >= 1 && checkEvery >= 1 && stallDays >= 1) { "intervals must be positive" }
    }
}

/**
 * Plays a draft on the real simulation with no human: builds the world the draft describes, founds the player's
 * company, applies the scripted events on their days and lets a policy decide what the company does. Nothing is
 * registered anywhere; the game's scenario service never sees the draft. The goals and failure conditions of the
 * draft are checked here with the same meters the game uses.
 */
class WorkshopHarness(private val content: ContentRepository) {

    fun run(draft: ScenarioDraft, kind: PolicyKind, settings: HarnessSettings = HarnessSettings(), seed: Long = draft.seed): Outcome<RunMetrics> =
        run(draft, kind.create(draft, content), settings, seed)

    fun run(draft: ScenarioDraft, policy: PlayerPolicy, settings: HarnessSettings = HarnessSettings(), seed: Long = draft.seed): Outcome<RunMetrics> {
        val d = draft.normalised()
        val report = DraftValidator.validate(d, content)
        report.errors.firstOrNull()?.let { return Outcome.reject(Rules.INVALID_ARGUMENT, "the draft cannot be played: ${it.path}: ${it.message}") }
        val sim = Simulation(DraftConverter.config(d).copy(seed = seed), content)
        if (settings.checkInvariants) sim.pipeline.checkInvariants = true
        val founded = when (val f = HarnessSetup.found(sim, d)) {
            is Outcome.Ok -> f.value
            is Outcome.Rejected -> return Outcome.Rejected(f.rejection)
        }
        HarnessSetup.placeStarters(sim, d, founded)
        val ctx = PolicyContext(sim, founded.company, d)
        policy.attach(ctx)
        return Outcome.ok(Play(sim, d, ctx, policy, settings, seed).play())
    }

    private inner class Play(val sim: Simulation, val d: ScenarioDraft, val ctx: PolicyContext, val policy: PlayerPolicy, val s: HarnessSettings, val seed: Long) {
        val e: Economy get() = sim.economy
        val company: CompanyId get() = ctx.company
        val goals: List<Goal> = d.goals.map { Goal(it.kind, it.target) }
        val requiredIdx: List<Int> = d.goals.indices.filter { !d.goals[it].bonus }
        val samples = ArrayList<RunSample>()
        val log = ArrayList<String>()
        val problems = ArrayList<String>()
        val firstDay = arrayOfNulls<Int>(goals.size)
        val regressed = BooleanArray(goals.size)
        val wasDone = BooleanArray(goals.size)
        var allDone: Int? = null
        var bankrupt: Int? = null
        var stallSince: Int? = null
        var softlock: Int? = null
        var softlockNote: String? = null
        var end = RunEnd.HORIZON
        var cause: LossCause? = null
        var nextEvent = 0

        fun play(): RunMetrics {
            applyEvents(0)
            samples += sample(0, measure())
            var day = 0
            while (day < s.days) {
                applyEvents(day)
                policy.beforeTick(ctx)
                val summary = sim.advance()
                day++
                if (summary.problems.isNotEmpty() && problems.size < 20) problems += summary.problems.map { "t${summary.tick.value}: $it" }
                val checking = day % s.checkEvery == 0 || day == s.days
                val values = if (checking || day % s.sampleEvery == 0) measure() else null
                if (day % s.sampleEvery == 0 || day == s.days) samples += sample(day, values ?: measure())
                if (checking && values != null && decide(day, values)) break
            }
            return finish(day)
        }

        fun measure(): List<Long> =
            if (e.companies.company(company)?.isActive == true) goals.map { GoalMeter.valueFor(e, company, it) } else goals.map { 0L }

        fun sample(day: Int, values: List<Long>): RunSample {
            val active = e.companies.company(company)?.isActive == true
            val owner = e.companies.require(company).owner
            return RunSample(
                day = day,
                cash = if (active) e.finance.ledger.cash(owner).wholeUnits() else 0L,
                netWorth = if (active) GoalMeter.netWorthUnits(e, company) else 0L,
                facilities = if (active) e.production.facilitiesOf(company).size else 0,
                employees = if (active) e.workforce.employedBy(company) else 0L,
                goalValues = values,
                goalPpm = goals.mapIndexed { i, g -> (values[i].coerceAtLeast(0L) * 1_000_000L / g.target).coerceAtMost(1_000_000L) },
                rivalsOut = e.companies.all().count { it.kind == CompanyKind.AI && it.status != CompanyStatus.ACTIVE },
            )
        }

        /** Returns true when the run is over. */
        fun decide(day: Int, values: List<Long>): Boolean {
            val active = e.companies.company(company)?.isActive == true
            if (!active) {
                bankrupt = bankrupt ?: day
                return lose(day, LossCause.NO_COMPANY, "the company is no longer active")
            }
            for (i in goals.indices) {
                val done = values[i] >= goals[i].target
                if (done && firstDay[i] == null) { firstDay[i] = day; log += "day $day: goal ${i + 1} met (${goals[i].describe()})" }
                if (!done && wasDone[i]) regressed[i] = true
                wasDone[i] = done
            }
            if (requiredIdx.isNotEmpty() && requiredIdx.all { values[it] >= goals[it].target } && allDone == null) {
                allDone = day
                end = RunEnd.WON
                log += "day $day: every required goal is met"
                return true
            }
            val worth = GoalMeter.netWorthUnits(e, company)
            val cash = e.finance.ledger.cash(e.companies.require(company).owner).wholeUnits()
            val sites = e.production.facilitiesOf(company).size
            for ((i, f) in d.fails.withIndex()) {
                if (day < f.afterDay) continue
                val hit = when (f.kind) {
                    FailKind.NET_WORTH_BELOW -> (worth < f.threshold) to LossCause.NET_WORTH
                    FailKind.CASH_BELOW -> (cash < f.threshold) to LossCause.CASH
                    FailKind.FACILITIES_BELOW -> (sites.toLong() < f.threshold) to LossCause.FACILITIES
                }
                if (hit.first) return lose(day, hit.second, "failure condition ${i + 1} (${f.kind.label.lowercase()} ${f.threshold})")
            }
            stallWatch(day)
            if (day >= d.deadlineDays) return lose(day, LossCause.DEADLINE, "the deadline")
            return false
        }

        fun stallWatch(day: Int) {
            val reading = SoftlockProbe.read(e, company)
            if (!reading.stalled) { stallSince = null; return }
            val since = stallSince ?: day.also { stallSince = it }
            if (softlock == null && day - since >= s.stallDays) {
                softlock = since
                softlockNote = reading.describe()
                log += "day $since: the company is stuck; ${reading.describe()}"
            }
        }

        fun lose(day: Int, why: LossCause, detail: String): Boolean {
            end = RunEnd.LOST
            cause = why
            log += "day $day: lost: $detail"
            return true
        }

        fun applyEvents(day: Int) {
            while (nextEvent < d.events.size && d.events[nextEvent].day <= day) {
                val ev = d.events[nextEvent]
                nextEvent++
                if (ev.day < day) continue
                applyEvent(ev, nextEvent - 1)
            }
        }

        fun applyEvent(ev: ScriptedEvent, index: Int) {
            val owner = e.companies.require(company).owner
            val units = Money.units(ev.amount)
            when (ev.kind) {
                EventKind.GRANT -> if (e.companies.company(company)?.isActive == true) {
                    e.finance.mint(e.tick, owner, units, "workshop:grant:$index")
                    log += "day ${ev.day}: grant of ${ev.amount}" + note(ev)
                }
                EventKind.LEVY -> if (e.companies.company(company)?.isActive == true) {
                    val take = Money.min(units, e.finance.ledger.cash(owner))
                    if (take.raw > 0L) {
                        val gov = e.governmentOfCompany(company)
                        e.finance.ledger.post(
                            TransactionDraft(
                                "workshop:levy:$index", e.tick, ReasonCode.FEE,
                                listOf(Entry(AccountRef(owner, Chart.CASH), -take), Entry(AccountRef(gov, Chart.CASH), take)),
                                "scripted levy",
                            ),
                        )
                    }
                    log += "day ${ev.day}: levy of ${take.wholeUnits()}" + note(ev)
                }
                EventKind.RIVAL_GRANT -> {
                    val rivals = e.companies.ofKind(CompanyKind.AI).sortedBy { it.id }
                    for (r in rivals) e.finance.mint(e.tick, r.owner, units, "workshop:rivals:$index:${r.id.value}")
                    log += "day ${ev.day}: ${rivals.size} rivals receive ${ev.amount} each" + note(ev)
                }
                EventKind.ANNOUNCE -> log += "day ${ev.day}: ${ev.text}"
            }
        }

        fun note(ev: ScriptedEvent) = if (ev.text.isBlank()) "" else " (${ev.text})"

        fun finish(day: Int): RunMetrics {
            if (samples.last().day != day) samples += sample(day, measure())
            if (settings().checkInvariants) problems += Invariants.check(e).take(10)
            return RunMetrics(
                draftHash = d.stateHash(), policy = policy.name, seed = seed, days = day, samples = samples, end = end, lossCause = cause,
                goalFirstDay = firstDay.toList(), goalRegressed = regressed.toList(), allDoneDay = allDone, bankruptDay = bankrupt,
                rivalsOut = samples.last().rivalsOut, softlockDay = softlock, softlockNote = softlockNote, log = log, stateHash = sim.hash(),
                invariantProblems = problems,
            )
        }

        fun settings() = s
    }
}
