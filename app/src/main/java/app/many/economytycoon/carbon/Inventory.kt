package app.many.economytycoon.carbon

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.ema

/** Key of a company's inventory for one year in one country. */
data class CompanyYearKey(val company: CompanyId, val country: CountryId, val year: Int) : Comparable<CompanyYearKey> {
    override fun compareTo(other: CompanyYearKey): Int =
        compareValuesBy(this, other, { it.country }, { it.company }, { it.year })
}

/** Key of a country's inventory for one year. */
data class NationalKey(val country: CountryId, val year: Int) : Comparable<NationalKey> {
    override fun compareTo(other: NationalKey): Int = compareValuesBy(this, other, { it.country }, { it.year })
}

/**
 * Everything one company released in one country in one year, split by where it came from. [covered] is the part from
 * facilities in industries the country's policy covers that year: the figure the regulator holds the company to.
 */
class CompanyYear(val company: CompanyId, val country: CountryId, val year: Int) {
    var direct: Tonnes = Tonnes.ZERO
        internal set
    var covered: Tonnes = Tonnes.ZERO
        internal set
    var indirect: Tonnes = Tonnes.ZERO
        internal set
    var biogenic: Tonnes = Tonnes.ZERO
        internal set
    var avoided: Tonnes = Tonnes.ZERO
        internal set
    var outputValue: Money = Money.ZERO
        internal set
    var coveredOutput: Money = Money.ZERO
        internal set
    var days: Int = 0
        internal set
    var closed: Boolean = false
        internal set
    internal val bySource = java.util.TreeMap<String, Long>()
    internal val byFamily = java.util.TreeMap<IndustryFamily, Long>()
    internal val byFacility = java.util.TreeMap<FacilityId, Long>()

    /** Kilograms by source, keyed `KIND:key`, direct and biogenic together. */
    fun sources(): Map<String, Tonnes> = bySource.mapValues { Tonnes(it.value) }

    fun families(): Map<IndustryFamily, Tonnes> = byFamily.mapValues { Tonnes(it.value) }

    fun facilities(): Map<FacilityId, Tonnes> = byFacility.mapValues { Tonnes(it.value) }

    /** The industry family that released the most, or null before anything is recorded. */
    fun dominantFamily(): IndustryFamily? = byFamily.entries.maxWithOrNull(compareBy<Map.Entry<IndustryFamily, Long>> { it.value }.thenByDescending { it.key.ordinal })?.key

    /** Direct plus electricity emissions: the figure a company reports as its footprint. */
    val footprint: Tonnes get() = direct + indirect

    /** Grams released per unit of output value, rounded down; zero when nothing was made. */
    val intensityGramsPerUnit: Long
        get() = if (outputValue.raw <= 0L) 0L else app.many.economytycoon.core.mulDiv(footprint.raw * 1_000L, Money.SCALE, outputValue.raw)

    internal fun add(e: FacilityEmissions, isCovered: Boolean) {
        val d = e.direct
        direct += d; indirect += e.indirect; biogenic += e.biogenic; avoided += e.avoided
        outputValue += e.activity.outputValue
        if (isCovered) { covered += d; coveredOutput += e.activity.outputValue }
        for (line in e.lines) {
            if (line.scope == EmissionScope.INDIRECT) continue
            val key = line.kind.name + ":" + line.key
            bySource[key] = (bySource[key] ?: 0L) + line.amount.raw
        }
        if (d.raw > 0L) {
            byFamily[e.activity.family] = (byFamily[e.activity.family] ?: 0L) + d.raw
            byFacility[e.activity.facility] = (byFacility[e.activity.facility] ?: 0L) + d.raw
        }
    }

    internal fun restore(
        direct: Tonnes, covered: Tonnes, indirect: Tonnes, biogenic: Tonnes, avoided: Tonnes, outputValue: Money, coveredOutput: Money,
        days: Int, closed: Boolean, bySource: Map<String, Long>, byFamily: Map<IndustryFamily, Long>, byFacility: Map<FacilityId, Long>,
    ) {
        this.direct = direct; this.covered = covered; this.indirect = indirect; this.biogenic = biogenic; this.avoided = avoided
        this.outputValue = outputValue; this.coveredOutput = coveredOutput; this.days = days; this.closed = closed
        this.bySource.clear(); this.bySource.putAll(bySource)
        this.byFamily.clear(); this.byFamily.putAll(byFamily)
        this.byFacility.clear(); this.byFacility.putAll(byFacility)
    }
}

/** A country's total for one year across every company. */
class NationalYear(val country: CountryId, val year: Int) {
    var direct: Tonnes = Tonnes.ZERO
        internal set
    var indirect: Tonnes = Tonnes.ZERO
        internal set
    var biogenic: Tonnes = Tonnes.ZERO
        internal set
    var avoided: Tonnes = Tonnes.ZERO
        internal set
    var covered: Tonnes = Tonnes.ZERO
        internal set
    /** Electricity generated in the country this year, in raw quantity units. */
    var electricityProduced: Quantity = Quantity.ZERO
        internal set
    internal val byFamily = java.util.TreeMap<IndustryFamily, Long>()

    fun families(): Map<IndustryFamily, Tonnes> = byFamily.mapValues { Tonnes(it.value) }

    /** What the power sector released: the part of the direct total from the energy family. */
    val power: Tonnes get() = Tonnes(byFamily[IndustryFamily.ENERGY] ?: 0L)

    internal fun add(e: FacilityEmissions, isCovered: Boolean) {
        val d = e.direct
        direct += d; indirect += e.indirect; biogenic += e.biogenic; avoided += e.avoided
        if (isCovered) covered += d
        electricityProduced += e.activity.electricityProduced
        if (d.raw > 0L) byFamily[e.activity.family] = (byFamily[e.activity.family] ?: 0L) + d.raw
    }

    internal fun restore(direct: Tonnes, indirect: Tonnes, biogenic: Tonnes, avoided: Tonnes, covered: Tonnes, produced: Quantity, byFamily: Map<IndustryFamily, Long>) {
        this.direct = direct; this.indirect = indirect; this.biogenic = biogenic; this.avoided = avoided; this.covered = covered; this.electricityProduced = produced
        this.byFamily.clear(); this.byFamily.putAll(byFamily)
    }
}

/**
 * What the package knows about one facility: who owns it and where, the abatement installed, and smoothed daily
 * figures that let abatement be appraised without re-reading the production engine.
 */
class FacilityRecord(
    val facility: FacilityId,
    owner: CompanyId,
    val country: CountryId,
    val type: FacilityTypeId,
    val family: IndustryFamily,
) {
    var owner: CompanyId = owner
        internal set
    var lastSeen: Tick = Tick.ZERO
        internal set
    var samples: Int = 0
        internal set

    /** Smoothed daily release in kilograms, by kind of source. */
    internal val dailyKg = java.util.EnumMap<SourceKind, Long>(SourceKind::class.java)
    internal val fuelKg = java.util.TreeMap<String, Long>()
    var dailyEnergySpend: Money = Money.ZERO
        internal set
    var dailyOutputValue: Money = Money.ZERO
        internal set
    internal val installed = ArrayList<AbatementEffect>()

    val effects: List<AbatementEffect> get() = installed.toList()

    fun daily(kind: SourceKind): Tonnes = Tonnes(dailyKg[kind] ?: 0L)

    /** Smoothed daily direct release, all sources. */
    val dailyDirect: Tonnes get() = Tonnes(dailyKg.values.sum())

    /** Annualised direct release at the smoothed daily rate. */
    val annualDirect: Tonnes get() = Tonnes(Math.multiplyExact(dailyKg.values.sum(), CarbonCalendar.DAYS_PER_YEAR.toLong()))

    fun annual(kind: SourceKind): Tonnes = Tonnes(Math.multiplyExact(dailyKg[kind] ?: 0L, CarbonCalendar.DAYS_PER_YEAR.toLong()))

    /** The fuel that releases the most, with its smoothed daily kilograms. */
    fun dominantFuel(): Pair<String, Long>? =
        fuelKg.entries.filter { it.value > 0L }.maxWithOrNull(compareBy<Map.Entry<String, Long>> { it.value }.thenByDescending { it.key })?.let { it.key to it.value }

    val annualEnergySpend: Money get() = dailyEnergySpend * CarbonCalendar.DAYS_PER_YEAR.toLong()

    internal fun observe(e: FacilityEmissions, tick: Tick, alpha: Rate) {
        val sample = java.util.EnumMap<SourceKind, Long>(SourceKind::class.java)
        val fuelSample = java.util.TreeMap<String, Long>()
        for (line in e.lines) {
            if (line.scope == EmissionScope.INDIRECT) {
                sample.merge(SourceKind.ELECTRICITY, line.amount.raw, Long::plus)
                continue
            }
            sample.merge(line.kind, line.amount.raw, Long::plus)
            if (line.kind == SourceKind.FUEL) fuelSample.merge(line.key, line.amount.raw, Long::plus)
        }
        if (samples == 0) {
            dailyKg.clear(); dailyKg.putAll(sample)
            fuelKg.clear(); fuelKg.putAll(fuelSample)
            dailyEnergySpend = e.activity.energySpend
            dailyOutputValue = e.activity.outputValue
        } else {
            for (k in SourceKind.values()) {
                val prev = dailyKg[k] ?: 0L
                val next = ema(prev, sample[k] ?: 0L, alpha)
                if (next != 0L) dailyKg[k] = next else dailyKg.remove(k)
            }
            for (k in (fuelKg.keys + fuelSample.keys).toSortedSet()) {
                val next = ema(fuelKg[k] ?: 0L, fuelSample[k] ?: 0L, alpha)
                if (next != 0L) fuelKg[k] = next else fuelKg.remove(k)
            }
            dailyEnergySpend = Money(ema(dailyEnergySpend.raw, e.activity.energySpend.raw, alpha))
            dailyOutputValue = Money(ema(dailyOutputValue.raw, e.activity.outputValue.raw, alpha))
        }
        samples++
        lastSeen = tick
        owner = e.activity.company
    }

    internal fun restore(
        lastSeen: Tick, samples: Int, dailyKg: Map<SourceKind, Long>, fuelKg: Map<String, Long>,
        energySpend: Money, outputValue: Money, effects: List<AbatementEffect>,
    ) {
        this.lastSeen = lastSeen; this.samples = samples
        this.dailyKg.clear(); this.dailyKg.putAll(dailyKg)
        this.fuelKg.clear(); this.fuelKg.putAll(fuelKg)
        dailyEnergySpend = energySpend; dailyOutputValue = outputValue
        installed.clear(); installed.addAll(effects)
    }
}

/**
 * Company and national emissions inventories. Companies report what they release by country and year; the national
 * inventory is the sum of the companies' reports. Old years are dropped once they are [keepYears] behind.
 */
class InventoryBook {
    private val companyYears = java.util.TreeMap<CompanyYearKey, CompanyYear>()
    private val nationalYears = java.util.TreeMap<NationalKey, NationalYear>()
    private val facilityRecords = java.util.TreeMap<FacilityId, FacilityRecord>()

    fun company(company: CompanyId, country: CountryId, year: Int): CompanyYear? = companyYears[CompanyYearKey(company, country, year)]

    fun companyYears(): List<CompanyYear> = companyYears.values.toList()

    fun companyYearsOf(company: CompanyId): List<CompanyYear> = companyYears.values.filter { it.company == company }.sortedBy { it.year }

    fun companiesIn(country: CountryId, year: Int): List<CompanyYear> =
        companyYears.values.filter { it.country == country && it.year == year }

    fun national(country: CountryId, year: Int): NationalYear? = nationalYears[NationalKey(country, year)]

    fun nationalYears(): List<NationalYear> = nationalYears.values.toList()

    fun nationalHistory(country: CountryId): List<NationalYear> = nationalYears.values.filter { it.country == country }.sortedBy { it.year }

    fun facility(id: FacilityId): FacilityRecord? = facilityRecords[id]

    fun facilities(): List<FacilityRecord> = facilityRecords.values.toList()

    fun facilitiesOf(company: CompanyId): List<FacilityRecord> = facilityRecords.values.filter { it.owner == company }

    /** Total a company released in a year across every country. */
    fun totalFootprint(company: CompanyId, year: Int): Tonnes =
        Tonnes.sum(companyYears.values.filter { it.company == company && it.year == year }.map { it.footprint })

    internal fun yearFor(company: CompanyId, country: CountryId, year: Int): CompanyYear =
        companyYears.getOrPut(CompanyYearKey(company, country, year)) { CompanyYear(company, country, year) }

    internal fun nationalFor(country: CountryId, year: Int): NationalYear =
        nationalYears.getOrPut(NationalKey(country, year)) { NationalYear(country, year) }

    internal fun recordFor(a: FacilityActivity): FacilityRecord =
        facilityRecords.getOrPut(a.facility) { FacilityRecord(a.facility, a.company, a.country, a.type, a.family) }

    /** Books one facility-day into the company and national inventories and the facility's smoothed figures. */
    internal fun record(e: FacilityEmissions, tick: Tick, year: Int, isCovered: Boolean, alpha: Rate) {
        val a = e.activity
        yearFor(a.company, a.country, year).add(e, isCovered)
        nationalFor(a.country, year).add(e, isCovered)
        recordFor(a).observe(e, tick, alpha)
    }

    /** Counts a day against every company that reported in [year]; used for the intensity of partial years. */
    internal fun markDay(company: CompanyId, country: CountryId, year: Int) {
        yearFor(company, country, year).days += 1
    }

    internal fun close(year: Int) { for (c in companyYears.values) if (c.year == year) c.closed = true }

    internal fun prune(currentYear: Int, keepYears: Int) {
        val oldest = currentYear - keepYears
        companyYears.keys.removeAll { it.year < oldest }
        nationalYears.keys.removeAll { it.year < oldest }
    }

    internal fun forget(facility: FacilityId) { facilityRecords.remove(facility) }

    internal fun putCompanyYear(c: CompanyYear) { companyYears[CompanyYearKey(c.company, c.country, c.year)] = c }
    internal fun putNationalYear(n: NationalYear) { nationalYears[NationalKey(n.country, n.year)] = n }
    internal fun putFacility(f: FacilityRecord) { facilityRecords[f.facility] = f }
    internal fun clear() { companyYears.clear(); nationalYears.clear(); facilityRecords.clear() }
}
