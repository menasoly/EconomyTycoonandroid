package app.many.economytycoon.grants

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.GrantId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick

/** What a grant rewards. */
enum class GrantKind(val label: String, val unit: String, val blurb: String) {
    JOBS("Jobs grant", "job", "Paid for every net new job you create in the country while the grant runs."),
    FACILITIES("Investment grant", "facility", "Paid for every new facility you open in the favoured industry while the grant runs."),
}

/** How governments hand out money for growth. Amounts are whole currency units unless the name says otherwise. */
data class GrantParams(
    /** Days between a government's new programmes. */
    val issueEvery: Int = 20,
    val issueChancePpm: Long = 700_000L,
    val maxOpenPerCountry: Int = 3,
    /** Days a programme takes applications. */
    val applyDays: Int = 10,
    /** Days the growth is measured over once applications close. */
    val holdDays: Int = 60,
    val jobRateUnits: Long = 150L,
    val facilityRateUnits: Long = 12_000L,
    /** The most one company can receive from one programme. */
    val capUnits: Long = 60_000L,
    /** Money a programme sets aside, between these. */
    val minBudgetUnits: Long = 80_000L,
    val maxBudgetUnits: Long = 300_000L,
    /** A government commits at most this many days of its public spending to programmes still running. */
    val budgetDays: Int = 15,
    val maxApplicationsPerCompany: Int = 4,
    val keepClosedDays: Int = 120,
)

enum class GrantStatus { OPEN, ACTIVE, PAID, NO_APPLICANTS }

/** One company's claim on a programme. */
class Application(val company: CompanyId, val baseline: Long, val appliedAt: Tick) {
    var gain: Long = 0L
        internal set
    var paid: Money = Money.ZERO
        internal set
    var settled: Boolean = false
        internal set
}

/** A government's offer to pay for growth, with a budget that is shared out if it runs over. */
class GrantProgram(
    val id: GrantId,
    val country: CountryId,
    val kind: GrantKind,
    /** The industry a facilities grant favours; null for a jobs grant. */
    val family: IndustryFamily?,
    val rate: Money,
    val cap: Money,
    val budget: Money,
    val announcedAt: Tick,
    val applyUntil: Tick,
    val endsAt: Tick,
) {
    var status: GrantStatus = GrantStatus.OPEN
        internal set
    var endedAt: Tick? = null
        internal set
    var paidTotal: Money = Money.ZERO
        internal set
    internal val applicationMap = java.util.TreeMap<CompanyId, Application>()

    val applications: List<Application> get() = applicationMap.values.toList()
    val isOpen: Boolean get() = status == GrantStatus.OPEN
    val isRunning: Boolean get() = status == GrantStatus.OPEN || status == GrantStatus.ACTIVE
    fun applicationOf(company: CompanyId): Application? = applicationMap[company]

    internal fun restore(status: GrantStatus, endedAt: Tick?, paidTotal: Money, applications: List<Application>) {
        this.status = status; this.endedAt = endedAt; this.paidTotal = paidTotal
        applicationMap.clear(); for (a in applications) applicationMap[a.company] = a
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(country.value).putInt(kind.ordinal).putInt(family?.ordinal ?: -1).putLong(rate.raw).putLong(cap.raw).putLong(budget.raw)
        h.putLong(announcedAt.value).putLong(applyUntil.value).putLong(endsAt.value).putInt(status.ordinal).putLong(endedAt?.value ?: -1L).putLong(paidTotal.raw)
        h.putInt(applicationMap.size)
        for (a in applicationMap.values) h.putLong(a.company.value).putLong(a.baseline).putLong(a.appliedAt.value).putLong(a.gain).putLong(a.paid.raw).putBoolean(a.settled)
    }
}

sealed class GrantEvent {
    abstract val program: GrantId
    data class Announced(override val program: GrantId, val country: CountryId, val kind: GrantKind, val tick: Tick) : GrantEvent()
    data class Settled(override val program: GrantId, val company: CompanyId, val gain: Long, val paid: Money, val tick: Tick) : GrantEvent()
    data class Closed(override val program: GrantId, val paidTotal: Money, val applicants: Int, val tick: Tick) : GrantEvent()
    data class NoApplicants(override val program: GrantId, val tick: Tick) : GrantEvent()
}
