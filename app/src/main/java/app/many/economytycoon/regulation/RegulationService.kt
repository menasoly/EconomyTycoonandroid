package app.many.economytycoon.regulation

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegulationId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.world.Country

/** How a country is doing, as voters see it. All values are parts per million. */
data class Mood(val unemploymentPpm: Long, val happinessPpm: Long, val stabilityPpm: Long, val pollutionPpm: Long = 0L)

/** A change a government with a given stance might make. */
data class Candidate(val lever: Lever, val raise: Boolean, val weight: Int)

/**
 * Keeps the governments, their laws and the fines they hand out, and holds the rules for what a government
 * proposes, how votes are counted and how elections go. Applying a law to the world is left to the runner.
 */
class RegulationService(private val ids: IdAllocators, val params: RegulationParams = RegulationParams()) {
    private val governments = java.util.TreeMap<CountryId, Government>()
    private val laws = java.util.TreeMap<RegulationId, Law>()
    private val fines = ArrayDeque<Fine>()

    fun government(country: CountryId): Government? = governments[country]
    fun governments(): List<Government> = governments.values.toList()
    fun law(id: RegulationId): Law? = laws[id]
    fun laws(): List<Law> = laws.values.toList()
    fun lawsOf(country: CountryId): List<Law> = laws.values.filter { it.country == country }
    fun openLaws(country: CountryId): List<Law> = lawsOf(country).filter { it.isOpen }
    fun pendingEnactment(): List<Law> = laws.values.filter { it.status == LawStatus.PASSED }
    fun enacted(country: CountryId): List<Law> = lawsOf(country).filter { it.status == LawStatus.ENACTED }
    fun fines(): List<Fine> = fines.toList()
    fun finesOf(company: CompanyId): List<Fine> = fines.filter { it.company == company }

    fun ensureGovernment(country: Country, stance: Stance, firstElection: Tick): Government =
        governments.getOrPut(country.id) { Government(country.id, stance, firstElection, country.minimumWagePerTick) }

    // ---- what a law may change -----------------------------------------------------------------

    fun range(lever: Lever, baselineWage: Long): LongRange = when (lever) {
        Lever.CORPORATE_TAX -> 50_000L..400_000L
        Lever.SALES_TAX -> 0L..250_000L
        Lever.PAYROLL_TAX -> 0L..300_000L
        Lever.MINIMUM_WAGE -> (baselineWage / 2)..(baselineWage * 2)
        Lever.TARIFF -> 0L..200_000L
        Lever.ENVIRONMENT -> 50_000L..950_000L
    }

    fun stepFor(lever: Lever, current: Long): Long = when (lever) {
        Lever.CORPORATE_TAX, Lever.SALES_TAX, Lever.PAYROLL_TAX -> 20_000L
        Lever.TARIFF -> 10_000L
        Lever.ENVIRONMENT -> 50_000L
        Lever.MINIMUM_WAGE -> (current / 10).coerceAtLeast(1L)
    }

    /** Where a change in the given direction would land, or null if it would leave the allowed range. */
    fun target(lever: Lever, current: Long, raise: Boolean, baselineWage: Long): Long? {
        val step = stepFor(lever, current)
        val to = if (raise) current + step else current - step
        return to.takeIf { it in range(lever, baselineWage) && it != current }
    }

    /** The changes a government of this stance would like to make, with how keen it is on each. */
    fun candidates(stance: Stance, mood: Mood): List<Candidate> = when (stance) {
        Stance.BUSINESS -> listOf(
            Candidate(Lever.CORPORATE_TAX, false, 3), Candidate(Lever.PAYROLL_TAX, false, 2), Candidate(Lever.SALES_TAX, false, 1),
            Candidate(Lever.ENVIRONMENT, false, 2), Candidate(Lever.MINIMUM_WAGE, false, 1), Candidate(Lever.TARIFF, false, 1),
        )
        Stance.LABOUR -> listOf(
            Candidate(Lever.MINIMUM_WAGE, true, 3), Candidate(Lever.CORPORATE_TAX, true, 2), Candidate(Lever.PAYROLL_TAX, true, 1),
            Candidate(Lever.TARIFF, true, 1), Candidate(Lever.SALES_TAX, false, 1),
        )
        Stance.GREEN -> listOf(
            Candidate(Lever.ENVIRONMENT, true, 4), Candidate(Lever.CORPORATE_TAX, true, 1), Candidate(Lever.SALES_TAX, true, 1),
        )
        Stance.CENTRIST -> buildList {
            if (mood.unemploymentPpm > 100_000L) { add(Candidate(Lever.PAYROLL_TAX, false, 3)); add(Candidate(Lever.MINIMUM_WAGE, false, 2)) }
            if (mood.happinessPpm < 450_000L) { add(Candidate(Lever.MINIMUM_WAGE, true, 3)); add(Candidate(Lever.SALES_TAX, false, 2)) }
            if (mood.pollutionPpm > 500_000L) add(Candidate(Lever.ENVIRONMENT, true, 3))
            add(Candidate(Lever.SALES_TAX, true, 1)); add(Candidate(Lever.TARIFF, false, 1)); add(Candidate(Lever.CORPORATE_TAX, false, 1))
        }
    }

    // ---- proposals and votes -------------------------------------------------------------------

    /** The government's pick for its next law, or null if nothing fits. */
    fun choose(country: Country, mood: Mood, random: DeterministicRandom): Pair<Lever, Long>? {
        val gov = governments[country.id] ?: return null
        if (openLaws(country.id).size >= params.maxOpenLaws) return null
        val open = openLaws(country.id).map { it.lever }.toSet()
        val options = candidates(gov.stance, mood).mapNotNull { c ->
            if (c.lever in open) return@mapNotNull null
            val to = target(c.lever, c.lever.valueIn(country), c.raise, gov.baselineWage) ?: return@mapNotNull null
            c to to
        }
        if (options.isEmpty()) return null
        var roll = random.nextInt(options.sumOf { it.first.weight })
        for ((c, to) in options) { roll -= c.weight; if (roll < 0) return c.lever to to }
        return options.last().let { it.first.lever to it.second }
    }

    fun propose(country: Country, lever: Lever, to: Long, tick: Tick): Outcome<Law> {
        val gov = governments[country.id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no government")
        if (openLaws(country.id).size >= params.maxOpenLaws) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many laws before parliament")
        if (openLaws(country.id).any { it.lever == lever }) return Outcome.reject(Rules.WRONG_STATE, "that rule is already being debated")
        val from = lever.valueIn(country)
        if (to == from || to !in range(lever, gov.baselineWage)) return Outcome.reject(Rules.INVALID_ARGUMENT, "that change is outside what the country allows")
        val law = Law(ids.regulation(), country.id, lever, from, to, tick, Tick(tick.value + params.debateDays), gov.stance)
        laws[law.id] = law
        return Outcome.ok(law)
    }

    /** Backs or opposes a law with money. The caller has already taken the money from the company. */
    fun lobby(id: RegulationId, company: CompanyId, support: Boolean, amount: Money): Outcome<Law> {
        val law = laws[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown law")
        if (!law.isOpen) return Outcome.reject(Rules.WRONG_STATE, "parliament has already voted on that law")
        if (amount < params.minLobby) return Outcome.reject(Rules.INVALID_ARGUMENT, "lobbying needs at least ${params.minLobby}")
        val side = if (support) law.backers else law.opponents
        side[company] = (side[company] ?: Money.ZERO) + amount
        return Outcome.ok(law)
    }

    /** How strongly parliament leans towards passing the law, in parts per million of certainty (500,000 is a coin toss). */
    fun leaning(law: Law): Long {
        val gov = governments[law.country]
        val approval = gov?.approvalPpm ?: 500_000L
        val base = params.baseSupportPpm + mulDiv(approval - 500_000L, params.approvalWeightPpm, 1_000_000L)
        return (base + lobbySwing(law)).coerceIn(0L, 1_000_000L)
    }

    fun lobbySwing(law: Law): Long {
        val support = law.supportPool; val oppose = law.opposePool
        val net = (support - oppose).raw
        val total = (support + oppose).raw
        return mulDiv(params.maxLobbySwingPpm, net, total + params.lobbyHalfPoint.raw)
    }

    /** Counts the vote on every law whose debate is over. */
    fun vote(tick: Tick, random: (CountryId) -> DeterministicRandom): List<RegulationEvent> {
        val out = ArrayList<RegulationEvent>()
        for (law in laws.values) {
            if (!law.isOpen || tick < law.voteAt) continue
            val noise = random(law.country).nextInRange(-params.voteNoisePpm, params.voteNoisePpm)
            val score = (leaning(law) + noise).coerceIn(0L, 1_000_000L)
            law.supportScorePpm = score
            law.endedAt = tick
            if (score >= 500_000L) {
                law.status = LawStatus.PASSED
                law.effectiveAt = Tick(tick.value + params.noticeDays)
                out += RegulationEvent.Passed(law.id, law.country, tick)
            } else {
                law.status = LawStatus.REJECTED
                out += RegulationEvent.Rejected(law.id, law.country, tick)
            }
        }
        return out
    }

    /** Laws that take effect now. The runner applies each one and then calls [enacted]. */
    fun due(tick: Tick): List<Law> = pendingEnactment().filter { (it.effectiveAt ?: tick) <= tick }

    fun markEnacted(law: Law, tick: Tick): RegulationEvent {
        law.status = LawStatus.ENACTED
        law.endedAt = tick
        return RegulationEvent.Enacted(law.id, law.country, tick)
    }

    // ---- approval and elections ----------------------------------------------------------------

    fun approvalFor(mood: Mood): Long {
        val jobs = 1_000_000L - (mood.unemploymentPpm * 4).coerceAtMost(1_000_000L)
        return (mulDiv(mood.happinessPpm, 450_000L, 1_000_000L) + mulDiv(jobs, 350_000L, 1_000_000L) + mulDiv(mood.stabilityPpm, 200_000L, 1_000_000L)).coerceIn(0L, 1_000_000L)
    }

    /** Moves approval a little way towards what the mood warrants, so one bad month does not sink a government. */
    fun updateApproval(country: CountryId, mood: Mood) {
        val gov = governments[country] ?: return
        val target = approvalFor(mood)
        gov.approvalPpm = gov.approvalPpm + (target - gov.approvalPpm) / 6
    }

    fun nextStance(current: Stance, mood: Mood, random: DeterministicRandom): Stance {
        val weights = Stance.values().filter { it != current }.associateWith { s ->
            when (s) {
                Stance.BUSINESS -> 2 + if (mood.unemploymentPpm > 80_000L) 3 else 0
                Stance.LABOUR -> 2 + if (mood.happinessPpm < 500_000L) 3 else 0
                Stance.GREEN -> 1 + if (mood.pollutionPpm > 500_000L) 3 else 0
                Stance.CENTRIST -> 2
            }
        }
        var roll = random.nextInt(weights.values.sum())
        for ((s, w) in weights) { roll -= w; if (roll < 0) return s }
        return weights.keys.last()
    }

    fun elections(tick: Tick, mood: (CountryId) -> Mood, random: (CountryId) -> DeterministicRandom): List<RegulationEvent> {
        val out = ArrayList<RegulationEvent>()
        for (gov in governments.values) {
            if (tick < gov.nextElection) continue
            val r = random(gov.country)
            val m = mood(gov.country)
            val noise = r.nextInRange(-100_000L, 100_000L)
            val old = gov.stance
            if (gov.approvalPpm + noise >= 450_000L) {
                gov.terms += 1
            } else {
                gov.stance = nextStance(old, m, r)
                gov.terms = 1
                gov.approvalPpm = 550_000L
            }
            gov.nextElection = Tick(tick.value + params.electionEvery)
            out += RegulationEvent.Election(gov.country, tick, old, gov.stance, gov.approvalPpm)
        }
        return out
    }

    // ---- fines ---------------------------------------------------------------------------------

    fun recordFine(f: Fine) {
        fines.addLast(f)
        while (fines.size > params.maxFineHistory) fines.removeFirst()
    }

    fun recentFines(company: CompanyId, facility: app.many.economytycoon.core.FacilityId, tick: Tick): Int =
        fines.count { it.company == company && it.facility == facility && tick.value - it.tick.value < params.fineWindowDays }

    // ---- housekeeping --------------------------------------------------------------------------

    fun prune(tick: Tick) {
        val finished = laws.values.filter { !it.isOpen && it.status != LawStatus.PASSED }.sortedBy { it.endedAt?.value ?: 0L }
        val surplus = finished.size - params.historyLength
        if (surplus > 0) for (l in finished.take(surplus)) laws.remove(l.id)
    }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(governments.size); for (g in governments.values) g.hashInto(h)
        h.putInt(laws.size); for (l in laws.values) l.hashInto(h)
        h.putInt(fines.size)
        for (f in fines) h.putLong(f.company.value).putLong(f.facility.value).putLong(f.country.value).putLong(f.tick.value).putLong(f.pollutionPpm).putLong(f.limitPpm).putLong(f.amount.raw).putInt(if (f.closed) 1 else 0)
    }

    internal fun restore(g: Government) { governments[g.country] = g }
    internal fun restore(l: Law) { laws[l.id] = l }
    internal fun restoreFine(f: Fine) { fines.addLast(f) }
    internal fun clearAll() { governments.clear(); laws.clear(); fines.clear() }
}
