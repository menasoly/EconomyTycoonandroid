package app.many.economytycoon.regulation

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegulationId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.world.Country

/** A rule a government can change. Rates are in parts per million; the minimum wage is raw money per day. */
enum class Lever(val label: String, val unit: Unit) {
    CORPORATE_TAX("Corporate tax", Unit.RATE),
    SALES_TAX("Sales tax", Unit.RATE),
    PAYROLL_TAX("Payroll tax", Unit.RATE),
    MINIMUM_WAGE("Minimum wage", Unit.WAGE),
    TARIFF("Import tariff", Unit.RATE),
    ENVIRONMENT("Environmental rules", Unit.RATE);

    enum class Unit { RATE, WAGE }

    fun valueIn(c: Country): Long = when (this) {
        CORPORATE_TAX -> c.corporateTaxPpm
        SALES_TAX -> c.salesTaxPpm
        PAYROLL_TAX -> c.payrollTaxPpm
        MINIMUM_WAGE -> c.minimumWagePerTick
        TARIFF -> c.tariffPpm
        ENVIRONMENT -> c.environmentalStrictnessPpm
    }

    fun with(c: Country, value: Long): Country = when (this) {
        CORPORATE_TAX -> c.copy(corporateTaxPpm = value)
        SALES_TAX -> c.copy(salesTaxPpm = value)
        PAYROLL_TAX -> c.copy(payrollTaxPpm = value)
        MINIMUM_WAGE -> c.copy(minimumWagePerTick = value)
        TARIFF -> c.copy(tariffPpm = value)
        ENVIRONMENT -> c.copy(environmentalStrictnessPpm = value)
    }
}

/** What the party in power believes in. It decides which laws a government proposes. */
enum class Stance(val label: String, val blurb: String) {
    BUSINESS("Pro-business", "Cuts taxes and red tape."),
    CENTRIST("Centrist", "Keeps the rules steady and tweaks them as needed."),
    LABOUR("Labour", "Raises wages and workers' protections."),
    GREEN("Green", "Tightens environmental rules."),
}

data class RegulationParams(
    /** Days between a government's chances to propose a law. */
    val proposeEvery: Int = 45,
    /** Chance the government actually has a law to propose at those times. */
    val proposeChancePpm: Long = 800_000L,
    /** Days between proposal and vote, and between vote and the law taking effect. */
    val debateDays: Int = 20,
    val noticeDays: Int = 10,
    /** Days between elections. */
    val electionEvery: Int = 1_440,
    val maxOpenLaws: Int = 2,
    /** Starting support for a law the government itself proposes. */
    val baseSupportPpm: Long = 620_000L,
    /** How far public approval moves the chance a law passes (the share of the gap from 50%). */
    val approvalWeightPpm: Long = 300_000L,
    /** The most lobbying can swing a vote, either way. */
    val maxLobbySwingPpm: Long = 300_000L,
    /** Lobbying money at which the swing reaches half of its maximum. */
    val lobbyHalfPoint: Money = Money.units(60_000),
    val minLobby: Money = Money.units(1_000),
    /** Largest random wobble in a vote, either way. */
    val voteNoisePpm: Long = 80_000L,
    val historyLength: Int = 40,
    /** Days between environmental inspections, and the number of fines within the window that closes a facility. */
    val inspectEvery: Int = 30,
    val finesToClose: Int = 3,
    val fineWindowDays: Int = 180,
    val maxFineHistory: Int = 100,
    /** A fine is this share of the building's cost per full point of pollution over the limit. */
    val fineRatePpm: Long = 40_000L,
)

enum class LawStatus { PROPOSED, PASSED, ENACTED, REJECTED }

/** A change to one rule in one country, going through debate and a vote. */
class Law(
    val id: RegulationId,
    val country: CountryId,
    val lever: Lever,
    val from: Long,
    val to: Long,
    val proposedAt: Tick,
    val voteAt: Tick,
    val proposedBy: Stance,
) {
    var status: LawStatus = LawStatus.PROPOSED
        internal set
    var effectiveAt: Tick? = null
        internal set
    var endedAt: Tick? = null
        internal set
    var supportScorePpm: Long = 0L
        internal set
    internal val backers = java.util.TreeMap<CompanyId, Money>()
    internal val opponents = java.util.TreeMap<CompanyId, Money>()

    val raises: Boolean get() = to > from
    val isOpen: Boolean get() = status == LawStatus.PROPOSED
    val supportPool: Money get() = backers.values.fold(Money.ZERO) { a, m -> a + m }
    val opposePool: Money get() = opponents.values.fold(Money.ZERO) { a, m -> a + m }
    fun backedBy(c: CompanyId): Money = backers[c] ?: Money.ZERO
    fun opposedBy(c: CompanyId): Money = opponents[c] ?: Money.ZERO

    internal fun restore(status: LawStatus, effectiveAt: Tick?, endedAt: Tick?, score: Long, backers: Map<CompanyId, Money>, opponents: Map<CompanyId, Money>) {
        this.status = status; this.effectiveAt = effectiveAt; this.endedAt = endedAt; supportScorePpm = score
        this.backers.clear(); this.backers.putAll(backers); this.opponents.clear(); this.opponents.putAll(opponents)
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(country.value).putInt(lever.ordinal).putLong(from).putLong(to).putLong(proposedAt.value).putLong(voteAt.value)
        h.putInt(proposedBy.ordinal).putInt(status.ordinal).putLong(effectiveAt?.value ?: -1L).putLong(endedAt?.value ?: -1L).putLong(supportScorePpm)
        h.putInt(backers.size); for ((c, m) in backers) h.putLong(c.value).putLong(m.raw)
        h.putInt(opponents.size); for ((c, m) in opponents) h.putLong(c.value).putLong(m.raw)
    }
}

/** The ruling party of a country and how the public feels about it. */
class Government(val country: CountryId, stance: Stance, val nextElectionFirst: Tick, val baselineWage: Long) {
    var stance: Stance = stance
        internal set
    var approvalPpm: Long = 550_000L
        internal set
    var nextElection: Tick = nextElectionFirst
        internal set
    var terms: Int = 1
        internal set

    internal fun restore(stance: Stance, approval: Long, nextElection: Tick, terms: Int) {
        this.stance = stance; approvalPpm = approval; this.nextElection = nextElection; this.terms = terms
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(country.value).putInt(stance.ordinal).putLong(approvalPpm).putLong(nextElection.value).putInt(terms)
    }
}

/** A penalty for polluting beyond what the country allows. */
data class Fine(val company: CompanyId, val facility: FacilityId, val country: CountryId, val tick: Tick, val pollutionPpm: Long, val limitPpm: Long, val amount: Money, val closed: Boolean)

sealed class RegulationEvent {
    abstract val country: CountryId
    data class Proposed(val law: RegulationId, override val country: CountryId, val tick: Tick) : RegulationEvent()
    data class Passed(val law: RegulationId, override val country: CountryId, val tick: Tick) : RegulationEvent()
    data class Rejected(val law: RegulationId, override val country: CountryId, val tick: Tick) : RegulationEvent()
    data class Enacted(val law: RegulationId, override val country: CountryId, val tick: Tick) : RegulationEvent()
    data class Election(override val country: CountryId, val tick: Tick, val from: Stance, val to: Stance, val approvalPpm: Long) : RegulationEvent()
    data class Fined(val fine: Fine) : RegulationEvent() { override val country: CountryId get() = fine.country }
}
