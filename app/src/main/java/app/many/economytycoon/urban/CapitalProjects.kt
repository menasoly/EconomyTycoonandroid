package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

enum class ProjectKind(val label: String, val complexityPermille: Int) {
    TRANSIT_LINE("Transit line", 800),
    ROAD_UPGRADE("Road upgrade", 350),
    PARK("Park", 150),
    SCHOOL("School", 300),
    HOSPITAL("Hospital", 550),
    CIVIC_CENTRE("Civic centre", 450),
    HOUSING_PROGRAM("Public housing", 400),
    UTILITIES("Utilities", 500),
}

/** The phases every project goes through. [share] is the part of the budget each phase spends, ppm. */
enum class ProjectPhase(val label: String, val share: Long, val baseDays: Int) {
    PLANNING("Planning and environmental review", 50_000L, 60),
    DESIGN("Design", 100_000L, 90),
    PROCUREMENT("Procurement", 50_000L, 45),
    CONSTRUCTION("Construction", 700_000L, 360),
    COMMISSIONING("Testing and commissioning", 100_000L, 30);

    val next: ProjectPhase? get() = values().getOrNull(ordinal + 1)
}

enum class ProjectStatus { ACTIVE, STALLED, COMPLETED, CANCELLED }

/** A municipal project moving through its phases, drawing money day by day. */
class CapitalProject(
    val id: CapitalProjectId,
    val kind: ProjectKind,
    val name: String,
    /** District the project is in; null for network-wide works. */
    val district: Int?,
    val line: TransitLineId?,
    val road: LinkId?,
    /** Floor area of a public housing programme or land of a park, depending on the kind; zero otherwise. */
    val size: Long,
    val originalBudget: Money,
    val startedAt: Tick,
    /** A fixed-price contract halves the chance and size of overruns. */
    val fixedPrice: Boolean,
) {
    var budget: Money = originalBudget
        internal set
    var spent: Money = Money.ZERO
        internal set
    var phase: ProjectPhase = ProjectPhase.PLANNING
        internal set
    var phaseDay: Int = 0
        internal set
    var phaseLength: Int = ProjectPhase.PLANNING.baseDays
        internal set
    var delayDays: Int = 0
        internal set
    var stalledDays: Int = 0
        internal set
    var status: ProjectStatus = ProjectStatus.ACTIVE
        internal set
    var finishedAt: Tick? = null
        internal set
    /** Money already spent in the current phase. */
    var phaseSpent: Money = Money.ZERO
        internal set

    val isRunning: Boolean get() = status == ProjectStatus.ACTIVE || status == ProjectStatus.STALLED

    /** Total over (or under) the original budget, ppm. */
    val overrunPpm: Long get() = if (originalBudget.raw == 0L) 0L else mulDiv(budget.raw - originalBudget.raw, Scale.PPM, originalBudget.raw)

    /** Money the current phase is expected to spend in all. */
    fun phaseBudget(p: ProjectPhase = phase): Money = Money(mulDiv(budget.raw, p.share, Scale.PPM))

    /** Daily draw during the current phase. */
    fun dailyCost(): Money = Money(phaseBudget().raw / phaseLength.coerceAtLeast(1))

    /** Share of the work done, ppm, from finished phases and the days of the current one. */
    fun progressPpm(): Long {
        if (status == ProjectStatus.COMPLETED) return Scale.PPM
        var done = 0L
        for (p in ProjectPhase.values()) if (p.ordinal < phase.ordinal) done += p.share
        done += mulDiv(phase.share, phaseDay.toLong(), phaseLength.toLong().coerceAtLeast(1L))
        return done.coerceIn(0L, Scale.PPM)
    }

    /** Days left at the planned pace, not counting stalls. */
    fun daysLeft(): Int {
        var days = (phaseLength - phaseDay).coerceAtLeast(0)
        var p = phase.next
        while (p != null) { days += p.baseDays; p = p.next }
        return days
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putInt(kind.ordinal).putString(name).putInt(district ?: -1).putLong(line?.value ?: -1L).putLong(road?.value ?: -1L)
        h.putLong(size).putMoney(originalBudget).putLong(startedAt.value).putBoolean(fixedPrice).putMoney(budget).putMoney(spent)
        h.putInt(phase.ordinal).putInt(phaseDay).putInt(phaseLength).putInt(delayDays).putInt(stalledDays).putInt(status.ordinal)
        h.putLong(finishedAt?.value ?: -1L).putMoney(phaseSpent)
    }
}

sealed class ProjectEvent {
    abstract val project: CapitalProjectId
    data class PhaseFinished(override val project: CapitalProjectId, val phase: ProjectPhase, val overrunPpm: Long, val delayDays: Int, val tick: Tick) : ProjectEvent()
    data class Stalled(override val project: CapitalProjectId, val tick: Tick) : ProjectEvent()
    data class Resumed(override val project: CapitalProjectId, val stalledDays: Int, val tick: Tick) : ProjectEvent()
    data class Completed(override val project: CapitalProjectId, val spent: Money, val overrunPpm: Long, val tick: Tick) : ProjectEvent()
    data class Cancelled(override val project: CapitalProjectId, val spent: Money, val tick: Tick) : ProjectEvent()
}

/** What a project of each kind costs to build. */
object ProjectEstimates {
    fun estimate(kind: ProjectKind, size: Long, params: UrbanParams, linkLanes: Int = 2): Money = when (kind) {
        ProjectKind.TRANSIT_LINE -> Money.units(0) // priced from the line itself by the caller
        ProjectKind.ROAD_UPGRADE -> Money.units(1_800_000L * linkLanes)
        ProjectKind.PARK -> Money.units(110L * size)
        ProjectKind.SCHOOL -> Money.units(18_000_000)
        ProjectKind.HOSPITAL -> Money.units(65_000_000)
        ProjectKind.CIVIC_CENTRE -> Money.units(30_000_000)
        ProjectKind.HOUSING_PROGRAM -> Money(mulDiv(params.buildCostPerSqm.raw * size, 900_000L, Scale.PPM))
        ProjectKind.UTILITIES -> Money.units(12_000_000)
    }
}

/** The projects of one city. */
class ProjectBook {
    private val projects = java.util.TreeMap<CapitalProjectId, CapitalProject>()

    fun all(): List<CapitalProject> = projects.values.toList()
    fun get(id: CapitalProjectId): CapitalProject? = projects[id]
    fun running(): List<CapitalProject> = projects.values.filter { it.isRunning }
    fun finished(): List<CapitalProject> = projects.values.filter { !it.isRunning }
    fun runningOfKind(kind: ProjectKind) = running().filter { it.kind == kind }

    /** Budget committed to running projects that has not been spent yet. */
    fun outstanding(): Money = Money.sum(running().map { it.budget - it.spent })

    fun start(
        id: CapitalProjectId, kind: ProjectKind, name: String, district: Int?, line: TransitLineId?, road: LinkId?, size: Long,
        budget: Money, now: Tick, fixedPrice: Boolean, maxRunning: Int = MAX_RUNNING,
    ): Outcome<CapitalProject> {
        if (name.isBlank()) return Outcome.reject(Rules.INVALID_ARGUMENT, "a project needs a name")
        if (!budget.isPositive) return Outcome.reject(Rules.INVALID_ARGUMENT, "a project needs a budget")
        if (running().size >= maxRunning) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many projects are running")
        if (line != null && running().any { it.line == line }) return Outcome.reject(Rules.DUPLICATE, "that line is already being built")
        if (road != null && running().any { it.road == road }) return Outcome.reject(Rules.DUPLICATE, "that road is already being rebuilt")
        val p = CapitalProject(id, kind, name, district, line, road, size, budget, now, fixedPrice)
        projects[id] = p
        return Outcome.ok(p)
    }

    fun cancel(id: CapitalProjectId, now: Tick): Outcome<ProjectEvent> {
        val p = projects[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such project")
        if (!p.isRunning) return Outcome.reject(Rules.WRONG_STATE, "that project has finished")
        p.status = ProjectStatus.CANCELLED
        p.finishedAt = now
        return Outcome.ok(ProjectEvent.Cancelled(id, p.spent, now))
    }

    /**
     * Moves every running project one day on. [spend] pays for a day's work and says whether the money was there; a
     * project that cannot pay stands still. Two draws are taken from [random] at the end of each phase, whatever happens,
     * so the sequence does not depend on the outcome.
     */
    fun advanceDay(now: Tick, spend: (Money) -> Boolean, random: DeterministicRandom): List<ProjectEvent> {
        val events = ArrayList<ProjectEvent>()
        for (p in projects.values) {
            if (!p.isRunning) continue
            val cost = p.dailyCost()
            if (cost.isPositive && !spend(cost)) {
                p.stalledDays++
                if (p.status == ProjectStatus.ACTIVE) { p.status = ProjectStatus.STALLED; events += ProjectEvent.Stalled(p.id, now) }
                continue
            }
            if (p.status == ProjectStatus.STALLED) {
                p.status = ProjectStatus.ACTIVE
                events += ProjectEvent.Resumed(p.id, p.stalledDays, now)
            }
            p.spent += cost
            p.phaseSpent += cost
            p.phaseDay++
            if (p.phaseDay < p.phaseLength) continue
            finishPhase(p, now, random, events)
        }
        return events
    }

    private fun finishPhase(p: CapitalProject, now: Tick, random: DeterministicRandom, events: MutableList<ProjectEvent>) {
        val finished = p.phase
        val risk = riskPpm(p, finished)
        val overrun = random.nextInRange(-20_000L, risk.coerceAtLeast(1L))
        val slip = random.nextInRange(0L, mulDiv(finished.baseDays.toLong(), risk, Scale.PPM * 2L).coerceAtLeast(1L)).toInt()
        // Money not spent in the phase because it ran under budget is not carried; an overrun raises the whole estimate.
        p.budget = Money(p.budget.raw + mulDiv(mulDiv(p.budget.raw, overrun, Scale.PPM), finished.share, Scale.PPM))
        events += ProjectEvent.PhaseFinished(p.id, finished, overrun, slip, now)
        val next = finished.next
        if (next == null) {
            p.status = ProjectStatus.COMPLETED
            p.finishedAt = now
            events += ProjectEvent.Completed(p.id, p.spent, p.overrunPpm, now)
            return
        }
        p.phase = next
        p.phaseDay = 0
        p.phaseSpent = Money.ZERO
        p.phaseLength = next.baseDays + slip
        p.delayDays += slip
    }

    /** Largest overrun a phase can suffer, ppm. Construction is riskiest; a fixed-price contract halves the risk. */
    fun riskPpm(p: CapitalProject, phase: ProjectPhase): Long {
        var risk = p.kind.complexityPermille * 400L // up to 40 percent for the hardest work
        risk = when (phase) {
            ProjectPhase.CONSTRUCTION -> risk * 2L
            ProjectPhase.PLANNING, ProjectPhase.DESIGN -> risk / 2L
            else -> risk
        }
        if (p.fixedPrice) risk /= 2L
        return risk
    }

    internal fun restore(p: CapitalProject) { projects[p.id] = p }
    internal fun clear() { projects.clear() }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(projects.size)
        for (p in projects.values) p.hashInto(h)
    }

    companion object {
        const val MAX_RUNNING = 6
    }
}
