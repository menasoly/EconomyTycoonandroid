package app.many.economytycoon.research

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.TechDef
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** Where research points come from. The simulation maps this onto each company's research department. */
interface ResearchFunding {
    fun available(company: CompanyId): Long
    fun spend(company: CompanyId, points: Long): Boolean
}

/** Moves money for licences between companies. */
fun interface LicensePayer {
    fun pay(from: CompanyId, to: CompanyId, amount: Money, memo: String): Boolean
}

object FreeLicenses : LicensePayer {
    override fun pay(from: CompanyId, to: CompanyId, amount: Money, memo: String) = true
}

/**
 * Technology progress per company. Projects absorb points from the research department up to a pace
 * limit and finish when fully funded. Finished technologies unlock recipes, facilities and machines and
 * feed modifiers into production.
 */
class ResearchService(
    val content: ContentRepository,
    private val funding: ResearchFunding,
    val params: ResearchParams = ResearchParams(),
    private val licensePayer: LicensePayer = FreeLicenses,
) {
    private val states = java.util.TreeMap<CompanyId, CompanyResearch>()

    fun of(company: CompanyId): CompanyResearch = states.getOrPut(company) { CompanyResearch(company) }
    fun peek(company: CompanyId): CompanyResearch? = states[company]
    fun companies(): List<CompanyId> = states.keys.toList()

    fun isUnlocked(company: CompanyId, tech: TechId): Boolean = states[company]?.isUnlocked(tech) == true

    /** Points a technology costs: one per whole currency unit of its research cost. */
    fun costPoints(def: TechDef): Long = (def.researchCost.raw / Money.SCALE).coerceAtLeast(1L)

    /** Most points the project can take per tick. */
    fun paceCap(def: TechDef): Long {
        val nominal = (costPoints(def) + def.researchTicks - 1) / def.researchTicks.coerceAtLeast(1)
        return mulDiv(nominal, params.paceCapPpm, 1_000_000L).coerceAtLeast(1L)
    }

    /** Fewest ticks the technology can take when funding is unlimited. */
    fun minimumTicks(def: TechDef): Long = (costPoints(def) + paceCap(def) - 1) / paceCap(def)

    fun missingPrerequisites(company: CompanyId, tech: TechId): List<TechId> {
        val def = content.technologyOrNull(tech) ?: return emptyList()
        val have = states[company]
        return def.prerequisites.filter { have?.isUnlocked(it) != true }
    }

    /** Technologies the company could start now: not known, not running, prerequisites met. */
    fun available(company: CompanyId): List<TechDef> {
        val s = states[company]
        return content.allTechnologies.filter { t ->
            s?.isUnlocked(t.id) != true && s?.project(t.id) == null && s?.queue?.contains(t.id) != true &&
                t.prerequisites.all { s?.isUnlocked(it) == true }
        }
    }

    /** Grants a technology outright, for starting positions and scripted events. */
    fun grant(company: CompanyId, tech: TechId, tick: Tick): Outcome<Unit> {
        if (content.technologyOrNull(tech) == null) return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown technology ${tech.key}")
        val s = of(company)
        if (s.isUnlocked(tech)) return Outcome.reject(Rules.DUPLICATE, "already known")
        s.unlocked[tech] = tick
        s.active.removeAll { it.tech == tech }
        s.queue.remove(tech)
        return Outcome.ok(Unit)
    }

    fun start(company: CompanyId, tech: TechId, tick: Tick): Outcome<ResearchProject> {
        val def = content.technologyOrNull(tech) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown technology ${tech.key}")
        val s = of(company)
        if (s.isUnlocked(tech)) return Outcome.reject(Rules.DUPLICATE, "already known")
        if (s.project(tech) != null) return Outcome.reject(Rules.DUPLICATE, "already being researched")
        val missing = missingPrerequisites(company, tech)
        if (missing.isNotEmpty()) return Outcome.reject(Rules.PREREQUISITE_MISSING, "needs ${missing.joinToString { it.key }}")
        if (s.active.size >= params.maxConcurrent) return Outcome.reject(Rules.LIMIT_EXCEEDED, "all research slots are busy")
        s.queue.remove(tech)
        val p = ResearchProject(tech, tick, costPoints(def))
        s.active.add(p)
        return Outcome.ok(p)
    }

    /** Puts a technology behind the running projects; it starts as soon as a slot and its prerequisites allow. */
    fun enqueue(company: CompanyId, tech: TechId): Outcome<Unit> {
        if (content.technologyOrNull(tech) == null) return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown technology ${tech.key}")
        val s = of(company)
        if (s.isUnlocked(tech) || s.project(tech) != null || s.queue.contains(tech)) return Outcome.reject(Rules.DUPLICATE, "already known, running or queued")
        if (s.queue.size >= params.maxQueued) return Outcome.reject(Rules.LIMIT_EXCEEDED, "research queue is full")
        val blocked = missingPrerequisites(company, tech).filter { it !in s.queue && s.project(it) == null }
        if (blocked.isNotEmpty()) return Outcome.reject(Rules.PREREQUISITE_MISSING, "queue ${blocked.joinToString { it.key }} first")
        s.queue.add(tech)
        return Outcome.ok(Unit)
    }

    fun dequeue(company: CompanyId, tech: TechId): Boolean = states[company]?.queue?.remove(tech) == true

    /** Stops a project. Points already spent are lost. */
    fun cancel(company: CompanyId, tech: TechId, tick: Tick): Outcome<ResearchEvent.Cancelled> {
        val s = states[company] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no research for this company")
        val p = s.project(tech) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "not being researched")
        s.active.remove(p)
        return Outcome.ok(ResearchEvent.Cancelled(company, tech, tick))
    }

    /** Licenses a known technology from another company at a fee. Prerequisites of the licensee are waived. */
    fun license(licensor: CompanyId, licensee: CompanyId, tech: TechId, tick: Tick): Outcome<Money> {
        val def = content.technologyOrNull(tech) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown technology ${tech.key}")
        if (licensor == licensee) return Outcome.reject(Rules.INVALID_ARGUMENT, "cannot license to yourself")
        if (!isUnlocked(licensor, tech)) return Outcome.reject(Rules.NOT_UNLOCKED, "the licensor does not hold this technology")
        if (isUnlocked(licensee, tech)) return Outcome.reject(Rules.DUPLICATE, "already known")
        val fee = licenseFee(def)
        if (!licensePayer.pay(licensee, licensor, fee, "licence ${tech.key}")) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot pay the licence fee")
        grant(licensee, tech, tick)
        return Outcome.ok(fee)
    }

    fun licenseFee(def: TechDef): Money = Money(mulDiv(def.researchCost.raw, params.licenseFeePpm, 1_000_000L))

    /** Feeds projects from the research budget, completes funded ones and starts queued work. */
    fun step(tick: Tick): List<ResearchEvent> {
        val events = ArrayList<ResearchEvent>()
        for (s in states.values.toList()) {
            for (p in s.active.toList()) {
                val def = content.technologyOrNull(p.tech)
                if (def == null) { s.active.remove(p); continue }
                val want = minOf(paceCap(def), p.remaining, funding.available(s.company))
                if (want > 0 && funding.spend(s.company, want)) p.invested += want
                if (p.isFunded) {
                    s.active.remove(p)
                    s.unlocked[p.tech] = tick
                    events += ResearchEvent.Completed(s.company, p.tech, tick)
                }
            }
            promote(s, tick, events)
        }
        return events
    }

    private fun promote(s: CompanyResearch, tick: Tick, events: MutableList<ResearchEvent>) {
        var i = 0
        while (i < s.queue.size && s.active.size < params.maxConcurrent) {
            val t = s.queue[i]
            if (s.isUnlocked(t)) { s.queue.removeAt(i); continue }
            if (missingPrerequisites(s.company, t).isEmpty()) {
                s.queue.removeAt(i)
                val def = content.technologyOrNull(t) ?: continue
                s.active.add(ResearchProject(t, tick, costPoints(def)))
                events += ResearchEvent.Started(s.company, t, tick)
            } else i++
        }
    }

    /** Drops all research state of a company that no longer exists. */
    fun remove(company: CompanyId) { states.remove(company) }

    /** Gives an acquirer the technologies of a company it has absorbed. */
    fun absorb(into: CompanyId, from: CompanyId, tick: Tick): Int {
        val src = states[from] ?: return 0
        var n = 0
        for (t in src.unlockedTechs()) if (grant(into, t, tick).isOk) n++
        return n
    }

    internal fun restore(state: CompanyResearch) { states[state.company] = state }
    internal fun clearAll() { states.clear() }

    fun hashInto(h: CanonicalHasher) {
        h.section("research")
        h.putInt(states.size)
        for (s in states.values) s.hashInto(h)
    }
}
