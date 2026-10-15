package app.many.economytycoon.grants

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.GrantId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/**
 * The register of grant programmes and the rules for claiming them. A company applies while a programme is open,
 * and the register notes where it stood. When the measuring period ends, whatever it gained over that baseline is
 * paid at the programme's rate, up to the cap, and scaled back evenly if the programme's budget runs out. Reading
 * the economy and moving money is left to the runner.
 */
class GrantService(private val ids: IdAllocators, val params: GrantParams = GrantParams()) {
    private val programs = java.util.TreeMap<GrantId, GrantProgram>()

    fun all(): List<GrantProgram> = programs.values.toList()
    fun get(id: GrantId): GrantProgram? = programs[id]
    fun open(): List<GrantProgram> = programs.values.filter { it.isOpen }
    fun openIn(country: CountryId): List<GrantProgram> = open().filter { it.country == country }
    fun running(): List<GrantProgram> = programs.values.filter { it.isRunning }
    fun active(): List<GrantProgram> = programs.values.filter { it.status == GrantStatus.ACTIVE }
    fun history(): List<GrantProgram> = programs.values.filter { !it.isRunning }
    fun appliedBy(company: CompanyId): List<GrantProgram> = running().filter { it.applicationOf(company) != null }

    /** Money the programmes of a country may still have to pay out. */
    fun committed(country: CountryId): Money =
        programs.values.filter { it.country == country && it.isRunning }.fold(Money.ZERO) { a, p -> a + p.budget }

    /** Total paid to [company] across every settled programme. */
    fun receivedBy(company: CompanyId): Money =
        programs.values.fold(Money.ZERO) { a, p -> a + (p.applicationOf(company)?.paid ?: Money.ZERO) }

    fun announce(
        country: CountryId, kind: GrantKind, family: IndustryFamily?, rate: Money, cap: Money, budget: Money, tick: Tick,
    ): Outcome<GrantProgram> {
        if (rate.raw <= 0L || cap.raw <= 0L || budget.raw <= 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "a grant needs a rate, a cap and a budget")
        if (kind == GrantKind.FACILITIES && family == null) return Outcome.reject(Rules.INVALID_ARGUMENT, "an investment grant favours an industry")
        if (kind == GrantKind.JOBS && family != null) return Outcome.reject(Rules.INVALID_ARGUMENT, "a jobs grant favours no industry")
        if (openIn(country).size >= params.maxOpenPerCountry) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many open grant programmes")
        val until = Tick(tick.value + params.applyDays)
        val g = GrantProgram(ids.grant(), country, kind, family, rate, cap, budget, tick, until, Tick(until.value + params.holdDays))
        programs[g.id] = g
        return Outcome.ok(g)
    }

    /** Records an application. [baseline] is the company's current count of what the programme rewards. */
    fun apply(id: GrantId, company: CompanyId, baseline: Long, tick: Tick): Outcome<Application> {
        val g = programs[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown grant programme")
        if (!g.isOpen || tick >= g.applyUntil) return Outcome.reject(Rules.WRONG_STATE, "that programme is no longer taking applications")
        if (g.applicationOf(company) != null) return Outcome.reject(Rules.DUPLICATE, "you have already applied")
        if (appliedBy(company).size >= params.maxApplicationsPerCompany) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many grant applications in play")
        val a = Application(company, baseline.coerceAtLeast(0L), tick)
        g.applicationMap[company] = a
        return Outcome.ok(a)
    }

    fun withdraw(id: GrantId, company: CompanyId): Outcome<Unit> {
        val g = programs[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown grant programme")
        if (!g.isRunning) return Outcome.reject(Rules.WRONG_STATE, "that programme has finished")
        if (g.applicationMap.remove(company) == null) return Outcome.reject(Rules.UNKNOWN_ENTITY, "you have not applied to that programme")
        return Outcome.ok(Unit)
    }

    /** Moves programmes whose application window has passed on to measuring, or closes those nobody applied to. */
    fun closeApplications(tick: Tick): List<GrantEvent> {
        val out = ArrayList<GrantEvent>()
        for (g in programs.values) {
            if (!g.isOpen || tick < g.applyUntil) continue
            if (g.applicationMap.isEmpty()) {
                g.status = GrantStatus.NO_APPLICANTS
                g.endedAt = tick
                out += GrantEvent.NoApplicants(g.id, tick)
            } else {
                g.status = GrantStatus.ACTIVE
            }
        }
        return out
    }

    /**
     * Pays out every active programme whose measuring period is over. [measure] gives the current count for an
     * application; [pay] moves the money and returns what was actually paid, which may be less than asked.
     */
    fun settle(tick: Tick, measure: (GrantProgram, Application) -> Long, pay: (GrantProgram, Application, Money) -> Money): List<GrantEvent> {
        val out = ArrayList<GrantEvent>()
        for (g in programs.values) {
            if (g.status != GrantStatus.ACTIVE || tick < g.endsAt) continue
            val apps = g.applications
            val owed = apps.map { a ->
                a.gain = (measure(g, a) - a.baseline).coerceAtLeast(0L)
                Money.min(g.cap, g.rate * a.gain)
            }
            val total = owed.fold(Money.ZERO) { acc, m -> acc + m }
            for ((i, a) in apps.withIndex()) {
                val share = if (total > g.budget) Money(mulDiv(owed[i].raw, g.budget.raw, total.raw)) else owed[i]
                val paid = if (share.raw > 0L) pay(g, a, share) else Money.ZERO
                a.paid = paid
                a.settled = true
                g.paidTotal = g.paidTotal + paid
                out += GrantEvent.Settled(g.id, a.company, a.gain, paid, tick)
            }
            g.status = GrantStatus.PAID
            g.endedAt = tick
            out += GrantEvent.Closed(g.id, g.paidTotal, apps.size, tick)
        }
        return out
    }

    /** Forgets old finished programmes. */
    fun prune(tick: Tick) {
        val cutoff = tick.value - params.keepClosedDays
        programs.values.removeAll { !it.isRunning && (it.endedAt?.value ?: 0L) < cutoff }
    }

    /** Drops a closed company's applications from programmes still running. */
    fun endAllOf(company: CompanyId) {
        for (g in programs.values) if (g.isRunning) g.applicationMap.remove(company)
    }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(programs.size)
        for (g in programs.values) g.hashInto(h)
    }

    internal fun restore(g: GrantProgram) { programs[g.id] = g }
    internal fun clearAll() { programs.clear() }
}
