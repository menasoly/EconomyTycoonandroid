package app.many.economytycoon.carbon

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Hashable
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.StateHash

/**
 * All saved state of the carbon package: the regimes in force, one permit market per country, the emissions
 * inventories, compliance accounts, offsets, abatement projects, border policies, reports and the green funds.
 * It changes only through [CarbonService]; everything is public to read.
 */
class CarbonState : Hashable {
    val counters = CarbonCounters()
    val inventory = InventoryBook()
    val compliance = ComplianceBook()
    val offsets = OffsetBook()
    val abatement = AbatementBook()
    val border = BorderBook()
    val reports = ReportBook()

    private val regimeMap = java.util.TreeMap<CountryId, CarbonRegime>()
    private val marketMap = java.util.TreeMap<CountryId, PermitMarket>()
    private val gridMap = java.util.TreeMap<CountryId, Long>()
    private val fundMap = java.util.TreeMap<CountryId, Long>()
    private val receiptMap = java.util.TreeMap<CountryId, Long>()

    /** The last tick the package advanced to; a day is never processed twice. */
    var lastTick: Long = -1L
        internal set

    /** True while nothing has ever been set up or observed: no regime, no border policy and no day run. Such a state is neither saved nor hashed. */
    val isPristine: Boolean get() = lastTick < 0L && regimeMap.isEmpty() && border.policies().isEmpty()

    fun regimes(): List<CarbonRegime> = regimeMap.values.toList()
    fun regime(country: CountryId): CarbonRegime? = regimeMap[country]
    fun markets(): List<PermitMarket> = marketMap.values.toList()
    fun market(country: CountryId): PermitMarket? = marketMap[country]

    /** Grams released per unit of electricity in [country], or null before the first estimate. */
    fun gridIntensity(country: CountryId): Long? = gridMap[country]

    /** Money set aside from carbon receipts to pay abatement subsidies. */
    fun greenFund(country: CountryId): Money = Money(fundMap[country] ?: 0L)

    /** Everything the government has taken in through carbon pricing. */
    fun receipts(country: CountryId): Money = Money(receiptMap[country] ?: 0L)

    internal fun installRegime(r: CarbonRegime) { regimeMap[r.country] = r }

    internal fun marketFor(country: CountryId): PermitMarket = marketMap.getOrPut(country) { PermitMarket(country) }

    internal fun putMarket(m: PermitMarket) { marketMap[m.country] = m }

    internal fun setGrid(country: CountryId, gramsPerUnit: Long) { gridMap[country] = gramsPerUnit }

    internal fun addToFund(country: CountryId, amount: Money) { fundMap.merge(country, amount.raw, Long::plus) }

    internal fun drawFromFund(country: CountryId, amount: Money) { fundMap.merge(country, -amount.raw, Long::plus) }

    internal fun noteReceipt(country: CountryId, amount: Money) { receiptMap.merge(country, amount.raw, Long::plus) }

    internal fun gridEntries(): List<Pair<CountryId, Long>> = gridMap.entries.map { it.key to it.value }
    internal fun fundEntries(): List<Pair<CountryId, Long>> = fundMap.entries.map { it.key to it.value }
    internal fun receiptEntries(): List<Pair<CountryId, Long>> = receiptMap.entries.map { it.key to it.value }
    internal fun putFund(country: CountryId, raw: Long) { fundMap[country] = raw }
    internal fun putReceipt(country: CountryId, raw: Long) { receiptMap[country] = raw }

    internal fun clear() {
        inventory.clear(); compliance.clear(); offsets.clear(); abatement.clear(); border.clear(); reports.clear(); counters.reset()
        regimeMap.clear(); marketMap.clear(); gridMap.clear(); fundMap.clear(); receiptMap.clear(); lastTick = -1L
    }

    /**
     * The canonical hash is taken over the saved bytes, so it covers exactly what a save keeps and a field added to
     * the format can never be forgotten by the hash.
     */
    override fun hashInto(hasher: CanonicalHasher) {
        hasher.section("carbon")
        hasher.putBytes(CarbonCodec.encode(this))
    }

    fun stateHash(): StateHash = CanonicalHasher().also { hashInto(it) }.finish()
}
