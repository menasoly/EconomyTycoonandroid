package app.many.economytycoon.ip

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick

enum class LicenseKind(val label: String, val blurb: String) {
    EXCLUSIVE("Exclusive", "Only the licensee may use the patents in the scope; the licensor stays out of it too."),
    SOLE("Sole", "The licensee is the only one licensed in the scope, but the licensor keeps its own right to use it."),
    NON_EXCLUSIVE("Non-exclusive", "Anyone may be licensed alongside."),
}

enum class LicenseStatus(val label: String, val live: Boolean) {
    OFFERED("Offered", false),
    ACTIVE("In force", true),
    EXPIRED("Ran out", false),
    TERMINATED("Ended early", false),
    BREACHED("Ended for breach", false),
    DECLINED("Declined", false),
    WITHDRAWN("Withdrawn", false),
}

/** How the royalty is worked out. */
enum class RoyaltyBasis(val label: String) {
    PER_UNIT("Per unit"),
    PERCENT("Share of sales"),
    LUMP_SUM("Lump sum"),
    HYBRID("Per unit and share of sales"),
}

/**
 * The money terms of a licence. A lump sum is paid when the licence starts and nothing recurs. The other bases are
 * charged every period on what the licensee sold, never below [minimumPerPeriod] and never above [capPerPeriod].
 */
data class RoyaltyTerms(
    val basis: RoyaltyBasis,
    val perUnit: Money = Money.ZERO,
    val percent: Rate = Rate.ZERO,
    val lumpSum: Money = Money.ZERO,
    val minimumPerPeriod: Money = Money.ZERO,
    val capPerPeriod: Money? = null,
    /** Paid up front on top of the running royalty. */
    val upfront: Money = Money.ZERO,
) {
    init {
        require(perUnit.raw >= 0L && percent.ppm >= 0L && lumpSum.raw >= 0L && minimumPerPeriod.raw >= 0L && upfront.raw >= 0L) { "royalty terms cannot be negative" }
        require(capPerPeriod == null || capPerPeriod.raw > 0L) { "a cap must be positive" }
        require(capPerPeriod == null || capPerPeriod >= minimumPerPeriod) { "a cap cannot be below the minimum" }
    }

    val isRecurring: Boolean get() = basis != RoyaltyBasis.LUMP_SUM

    fun hashInto(h: CanonicalHasher) {
        h.putInt(basis.ordinal).putMoney(perUnit).putRate(percent).putMoney(lumpSum).putMoney(minimumPerPeriod)
        h.putLong(capPerPeriod?.raw ?: -1L).putMoney(upfront)
    }
}

/** Where and for what a licence applies. Empty lists mean "everywhere the patents are in force" and "every industry". */
data class LicenseScope(val industries: List<IndustryFamily> = emptyList(), val countries: List<CountryId> = emptyList()) {
    val allIndustries: Boolean get() = industries.isEmpty()
    val allCountries: Boolean get() = countries.isEmpty()

    fun coversIndustry(i: IndustryFamily?): Boolean = allIndustries || i == null || i in industries
    fun coversCountry(c: CountryId): Boolean = allCountries || c in countries

    /** True when some industry and some country fall inside both scopes. */
    fun overlaps(other: LicenseScope): Boolean {
        val industryOverlap = allIndustries || other.allIndustries || industries.any { it in other.industries }
        val countryOverlap = allCountries || other.allCountries || countries.any { it in other.countries }
        return industryOverlap && countryOverlap
    }

    companion object {
        fun of(industries: Collection<IndustryFamily>, countries: Collection<CountryId>) =
            LicenseScope(industries.toSortedSet().toList(), countries.toSortedSet().toList())
        val EVERYWHERE = LicenseScope()
    }
}

/** A permission to use patents, bought for royalties. */
class License(
    val id: LicenseId,
    licensor: IpOwner,
    val licensee: CompanyId,
    val patents: List<PatentId>,
    val kind: LicenseKind,
    val scope: LicenseScope,
    val terms: RoyaltyTerms,
    /** Days the licence runs once accepted. */
    val termDays: Int,
    val sublicensable: Boolean,
    val frand: Boolean,
    val offeredAt: Tick,
    val offerExpires: Tick,
    val poolId: PoolId? = null,
    val compulsory: Boolean = false,
) {
    /** Whoever owns the patents now; follows them when they are sold. */
    var licensor: IpOwner = licensor
        internal set
    var status: LicenseStatus = LicenseStatus.OFFERED
        internal set
    var startsAt: Tick? = null
        internal set
    var endsAt: Tick? = null
        internal set
    var endedAt: Tick? = null
        internal set
    var crossWith: LicenseId? = null
        internal set
    var paidTotal: Money = Money.ZERO
        internal set
    var lastSettledAt: Tick? = null
        internal set
    var strikes: Int = 0
        internal set
    var arrears: Money = Money.ZERO
        internal set
    var unitsReported: Quantity = Quantity.ZERO
        internal set
    var salesReported: Money = Money.ZERO
        internal set
    var auditsThisYear: Int = 0
        internal set
    var lastAuditAt: Tick? = null
        internal set
    var recovered: Money = Money.ZERO
        internal set
    /** What was truly owed over the life of the licence, which only an audit can see. */
    var dueTrue: Money = Money.ZERO
        internal set
    var dueReported: Money = Money.ZERO
        internal set
    var auditedTrue: Money = Money.ZERO
        internal set
    var auditedReported: Money = Money.ZERO
        internal set
    /** The most recent period's royalty, units and sales as reported. */
    var lastDue: Money = Money.ZERO
        internal set
    var lastUnits: Quantity = Quantity.ZERO
        internal set
    var lastSales: Money = Money.ZERO
        internal set

    val isActive: Boolean get() = status == LicenseStatus.ACTIVE
    val isOffer: Boolean get() = status == LicenseStatus.OFFERED

    fun covers(patent: PatentId): Boolean = patent in patents
    fun runningAt(tick: Tick): Boolean = isActive && (endsAt?.let { tick < it } ?: true)

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putInt(licensor.kind.ordinal).putLong(licensor.id).putLong(licensee.value)
        h.putInt(patents.size); for (p in patents) h.putLong(p.value)
        h.putInt(kind.ordinal).putInt(scope.industries.size); for (i in scope.industries) h.putInt(i.ordinal)
        h.putInt(scope.countries.size); for (c in scope.countries) h.putLong(c.value)
        terms.hashInto(h)
        h.putInt(termDays).putBoolean(sublicensable).putBoolean(frand).putLong(offeredAt.value).putLong(offerExpires.value)
        h.putLong(poolId?.value ?: -1L).putBoolean(compulsory).putInt(status.ordinal)
        h.putLong(startsAt?.value ?: -1L).putLong(endsAt?.value ?: -1L).putLong(endedAt?.value ?: -1L).putLong(crossWith?.value ?: -1L)
        h.putMoney(paidTotal).putLong(lastSettledAt?.value ?: -1L).putInt(strikes).putMoney(arrears)
        h.putQuantity(unitsReported).putMoney(salesReported).putInt(auditsThisYear).putLong(lastAuditAt?.value ?: -1L).putMoney(recovered)
        h.putMoney(dueTrue).putMoney(dueReported).putMoney(auditedTrue).putMoney(auditedReported).putMoney(lastDue).putQuantity(lastUnits).putMoney(lastSales)
    }
}
