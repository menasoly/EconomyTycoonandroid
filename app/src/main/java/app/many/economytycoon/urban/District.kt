package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick

/**
 * One district of a city. The fields below are the district's own state; surfaces such as pollution are derived from
 * them on demand. Only the owning [UrbanService] changes a district.
 */
class District(
    val id: DistrictId,
    val city: CityId,
    val index: Int,
    val name: String,
    zone: ZoneUse,
    rules: DensityRules,
    /** Land area in square metres. */
    val landSqm: Long,
) {
    var zone: ZoneUse = zone
        internal set
    var rules: DensityRules = rules
        internal set

    val stock = BuildingStock()

    /** Heritage districts keep their oldest buildings. */
    var heritage: Boolean = false
        internal set

    var residents: Long = 0L
        internal set
    var jobs: Long = 0L
        internal set
    var workersLiving: Long = 0L
        internal set

    /** Monthly rent for a square metre of housing. */
    var rentPerSqm: Money = Money.ZERO
        internal set
    var vacancyPpm: Long = 0L
        internal set

    /** Share of residents with low incomes, ppm. */
    var lowIncomeSharePpm: Long = 450_000L
        internal set
    var displacedTotal: Long = 0L
        internal set
    /** Anger over displacement and rising rents, ppm; it fades with time and feeds the council's mood. */
    var backlashPpm: Long = 0L
        internal set
    /** Tenant protection from 0 (none) to 1000 (strict rent control); it slows displacement and slows building. */
    var tenantProtectionPermille: Int = 0
        internal set

    var landValuePerSqm: Money = Money.ZERO
        internal set
    var accessibilityPermille: Int = 500
        internal set
    var trafficLoadPermille: Int = 0
        internal set
    var averageCommuteSeconds: Long = 0L
        internal set
    /** Permanent improvements from public works (parks, schools, clinics), added to the district's amenity, permille. */
    var amenityBonus: Int = 0
        internal set
    var lastRezonedAt: Tick = Tick(-1)
        internal set
    /** Floor area under construction that will join the stock when finished. */
    var pipelineArea: Long = 0L
        internal set
    var pipelineReadyAt: Tick = Tick(0)
        internal set

    private val rentHistory = LongArray(HISTORY)
    private var historyCount = 0
    private var historyHead = 0

    val floorArea: Long get() = stock.totalArea
    val residentialArea: Long get() = Zoning.residentialArea(zone, floorArea)
    val farMilli: Int get() = rules.farOf(floorArea, landSqm)
    val maxFloorArea: Long get() = rules.maxFloorArea(landSqm).let { cap -> minOf(cap, rules.maxFloorAreaByHeight(landSqm)) }

    /** Room left under the density limit, counting what is already being built. */
    val headroom: Long get() = (maxFloorArea - floorArea - pipelineArea).coerceAtLeast(0L)

    /** Records this month's rent, keeping the last [HISTORY] months. */
    fun recordRent() {
        rentHistory[historyHead] = rentPerSqm.raw
        historyHead = (historyHead + 1) % HISTORY
        if (historyCount < HISTORY) historyCount++
    }

    /** Rent a full year (or as long as there is history) ago, or the current rent when there is none. */
    fun rentYearAgo(): Money {
        if (historyCount == 0) return rentPerSqm
        val oldest = if (historyCount < HISTORY) 0 else historyHead
        return Money(rentHistory[oldest])
    }

    /** Rent growth over the recorded history, ppm of the oldest recorded rent. */
    fun rentGrowthPpm(): Long {
        val then = rentYearAgo().raw
        if (then <= 0L) return 0L
        return (rentPerSqm.raw - then) * Scale.PPM / then
    }

    fun rentHistoryList(): List<Money> = List(historyCount) { i ->
        val start = if (historyCount < HISTORY) 0 else historyHead
        Money(rentHistory[(start + i) % HISTORY])
    }

    internal fun restoreRentHistory(values: List<Money>) {
        require(values.size <= HISTORY) { "too much rent history" }
        historyCount = values.size
        historyHead = values.size % HISTORY
        for (i in values.indices) rentHistory[i] = values[i].raw
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(city.value).putInt(index).putString(name).putInt(zone.ordinal)
        h.putInt(rules.maxFarMilli).putInt(rules.maxFloors).putLong(rules.lotCoveragePpm).putLong(landSqm)
        stock.hashInto(h)
        h.putBoolean(heritage).putLong(residents).putLong(jobs).putLong(workersLiving).putMoney(rentPerSqm).putLong(vacancyPpm)
        h.putLong(lowIncomeSharePpm).putLong(displacedTotal).putLong(backlashPpm).putInt(tenantProtectionPermille)
        h.putMoney(landValuePerSqm).putInt(accessibilityPermille).putInt(trafficLoadPermille).putLong(averageCommuteSeconds)
        h.putInt(amenityBonus).putLong(lastRezonedAt.value).putLong(pipelineArea).putLong(pipelineReadyAt.value)
        h.putInt(historyCount)
        for (m in rentHistoryList()) h.putMoney(m)
    }

    companion object {
        const val HISTORY = 12
    }
}
