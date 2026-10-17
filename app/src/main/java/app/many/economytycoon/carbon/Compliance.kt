package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick

/** A company's standing with one country's regulator. */
data class AccountKey(val company: CompanyId, val country: CountryId) : Comparable<AccountKey> {
    override fun compareTo(other: AccountKey): Int = compareValuesBy(this, other, { it.country }, { it.company })
}

enum class ComplianceStatus(val label: String) {
    /** Surrendered permits (and offsets) for every tonne. */
    COMPLIANT("Compliant"),
    /** Could not cover the obligation by the deadline. */
    SHORTFALL("Shortfall"),
    /** Below the regulator's threshold, so nothing was owed. */
    EXEMPT("Exempt"),
    /** A carbon tax was paid on the year's emissions. */
    TAXED("Taxed"),
    /** An intensity levy was paid on the excess over the benchmark. */
    LEVIED("Levied"),
    /** The country had no carbon policy in force that year. */
    UNREGULATED("Unregulated"),
}

/** How one compliance year ended for a company. */
data class ComplianceRecord(
    val year: Int,
    val regime: RegimeKind,
    val status: ComplianceStatus,
    /** Covered direct emissions, as verified at year end. */
    val verified: Tonnes,
    /** What had to be surrendered: the verified emissions plus any shortfall carried in. */
    val obligation: Tonnes,
    val surrendered: Tonnes,
    val offsets: Tonnes,
    /** Next-year permits surrendered early. */
    val borrowed: Tonnes,
    val shortfall: Tonnes,
    val penalty: Money,
    /** Carbon tax or intensity levy paid for the year. */
    val charge: Money,
    /** Free permits received for the year. */
    val freeReceived: Tonnes,
)

/** A closed year whose permits have not all been surrendered yet. */
class PendingYear(val year: Int, val verified: Tonnes, val obligation: Tonnes, val deadline: Tick) {
    var surrendered: Tonnes = Tonnes.ZERO
        internal set
    var offsets: Tonnes = Tonnes.ZERO
        internal set
    var borrowed: Tonnes = Tonnes.ZERO
        internal set

    val covered: Tonnes get() = surrendered + offsets + borrowed
    val outstanding: Tonnes get() = (obligation - covered).coerceAtLeast(Tonnes.ZERO)
    val isCovered: Boolean get() = outstanding.raw <= 0L

    internal fun restore(surrendered: Tonnes, offsets: Tonnes, borrowed: Tonnes) {
        this.surrendered = surrendered; this.offsets = offsets; this.borrowed = borrowed
    }
}

/** What the company has told the package to do for it at the deadline. */
data class CompliancePolicy(val autoOffsets: Boolean = true, val autoBorrow: Boolean = false)

/** The compliance state of one company in one country. */
class ComplianceAccount(val key: AccountKey) {
    var pending: PendingYear? = null
        internal set
    /** Obligation carried into the next year: unmet shortfall and interest on borrowed permits. */
    var carry: Tonnes = Tonnes.ZERO
        internal set
    /** Years in a row that ended in a shortfall. */
    var violations: Int = 0
        internal set
    /** Carbon tax assessed so far this year. */
    var taxAssessed: Money = Money.ZERO
        internal set
    /** Free permits received this year. */
    var freeThisYear: Tonnes = Tonnes.ZERO
        internal set
    var policy: CompliancePolicy = CompliancePolicy()
        internal set
    internal val monthly = java.util.TreeMap<Int, Long>()
    internal val records = ArrayList<ComplianceRecord>()

    /** Cumulative covered emissions in kilograms at the end of each month of the current year, by month 0..11. */
    fun monitoring(): Map<Int, Tonnes> = monthly.mapValues { Tonnes(it.value) }

    fun history(): List<ComplianceRecord> = records.toList()
    fun lastRecord(): ComplianceRecord? = records.lastOrNull()
    fun recordFor(year: Int): ComplianceRecord? = records.lastOrNull { it.year == year }

    internal fun addRecord(r: ComplianceRecord, keep: Int) {
        records.removeAll { it.year == r.year }
        records += r
        records.sortBy { it.year }
        while (records.size > keep) records.removeAt(0)
    }

    internal fun restore(
        pending: PendingYear?, carry: Tonnes, violations: Int, taxAssessed: Money, freeThisYear: Tonnes, policy: CompliancePolicy,
        monthly: Map<Int, Long>, records: List<ComplianceRecord>,
    ) {
        this.pending = pending; this.carry = carry; this.violations = violations; this.taxAssessed = taxAssessed
        this.freeThisYear = freeThisYear; this.policy = policy
        this.monthly.clear(); this.monthly.putAll(monthly)
        this.records.clear(); this.records.addAll(records)
    }
}

class ComplianceBook {
    private val accountMap = java.util.TreeMap<AccountKey, ComplianceAccount>()

    fun accounts(): List<ComplianceAccount> = accountMap.values.toList()
    fun account(company: CompanyId, country: CountryId): ComplianceAccount? = accountMap[AccountKey(company, country)]
    fun accountsOf(company: CompanyId): List<ComplianceAccount> = accountMap.values.filter { it.key.company == company }
    fun accountsIn(country: CountryId): List<ComplianceAccount> = accountMap.values.filter { it.key.country == country }

    internal fun open(company: CompanyId, country: CountryId): ComplianceAccount =
        accountMap.getOrPut(AccountKey(company, country)) { ComplianceAccount(AccountKey(company, country)) }

    internal fun put(a: ComplianceAccount) { accountMap[a.key] = a }
    internal fun clear() { accountMap.clear() }
}
