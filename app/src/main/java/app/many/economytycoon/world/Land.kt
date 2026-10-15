package app.many.economytycoon.world

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.isqrt
import app.many.economytycoon.core.mulDiv

/** A tradable plot of land. Facilities are built on parcels; mines on deposits inside industrial or mining zones. */
class Parcel(
    val id: ParcelId,
    val region: RegionId,
    val city: CityId?,
    val location: Point,
    val zone: ZoneType,
    /** Area in land units; a factory typically needs 20 to 200. */
    val area: Int,
    val terrain: Terrain,
) {
    var owner: CompanyId? = null
        internal set
    var developed: Boolean = false
        internal set
    var lastPrice: Money = Money.ZERO
        internal set
    var rezonedTo: ZoneType? = null
        internal set

    val effectiveZone: ZoneType get() = rezonedTo ?: zone
    val forSale: Boolean get() = owner == null && effectiveZone != ZoneType.PROTECTED

    internal fun restore(owner: CompanyId?, developed: Boolean, lastPrice: Money, rezoned: ZoneType?) {
        this.owner = owner; this.developed = developed; this.lastPrice = lastPrice; rezonedTo = rezoned
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(owner?.value ?: -1L).putBoolean(developed).putMoney(lastPrice).putInt(effectiveZone.ordinal)
    }
}

/** Pricing and ownership of land. Payment is handled by the caller through the finance module. */
class LandRegistry {
    private val parcels = java.util.TreeMap<ParcelId, Parcel>()

    fun add(p: Parcel) { parcels[p.id] = p }
    fun get(id: ParcelId): Parcel? = parcels[id]
    fun all(): List<Parcel> = parcels.values.toList()
    fun inCity(city: CityId): List<Parcel> = parcels.values.filter { it.city == city }
    fun inRegion(region: RegionId): List<Parcel> = parcels.values.filter { it.region == region }
    fun ownedBy(company: CompanyId): List<Parcel> = parcels.values.filter { it.owner == company }
    fun available(city: CityId, zone: ZoneType? = null): List<Parcel> =
        inCity(city).filter { it.forSale && (zone == null || it.effectiveZone == zone) }

    fun assign(id: ParcelId, company: CompanyId, price: Money): Boolean {
        val p = parcels[id] ?: return false
        if (!p.forSale) return false
        p.owner = company
        p.lastPrice = price
        return true
    }

    /** Moves an owned, undeveloped parcel to another company at [price]. */
    fun transfer(id: ParcelId, company: CompanyId, price: Money): Boolean {
        val p = parcels[id] ?: return false
        if (p.owner == null || p.developed) return false
        p.owner = company
        p.lastPrice = price
        return true
    }

    fun release(id: ParcelId) {
        val p = parcels[id] ?: return
        p.owner = null
        p.developed = false
    }

    fun markDeveloped(id: ParcelId, developed: Boolean) { parcels[id]?.developed = developed }

    /** Changes the allowed use of a parcel; protected land cannot be rezoned. */
    fun rezone(id: ParcelId, zone: ZoneType): Boolean {
        val p = parcels[id] ?: return false
        if (p.zone == ZoneType.PROTECTED || p.developed) return false
        p.rezonedTo = if (zone == p.zone) null else zone
        return true
    }
}

object LandPricing {
    /** Price per land unit in raw money at a reference city of 100,000 people, before multipliers. */
    private const val BASE_PER_UNIT = 40_0000L // 40 currency units

    fun zoneMultiplierPpm(zone: ZoneType): Long = when (zone) {
        ZoneType.COMMERCIAL -> 1_800_000
        ZoneType.RESIDENTIAL -> 1_200_000
        ZoneType.MIXED -> 1_400_000
        ZoneType.INDUSTRIAL -> 700_000
        ZoneType.MINING -> 250_000
        ZoneType.AGRICULTURAL -> 120_000
        ZoneType.PROTECTED -> 0
    }

    /** City size factor: grows with the square root of population relative to 100k. */
    fun citySizePpm(population: Long): Long {
        val rootRatio = isqrt(mulDiv(population.coerceAtLeast(1), 1_000_000L, 100_000L) * 1_000_000L)
        return rootRatio.coerceIn(150_000L, 6_000_000L)
    }

    /** Distance factor: parcels in the core cost up to three times those at the edge. */
    fun proximityPpm(distanceFromCenter: Int, radius: Int = 40): Long {
        val d = distanceFromCenter.coerceIn(0, radius)
        return 3_000_000L - mulDiv(2_000_000L, d.toLong(), radius.toLong())
    }

    fun price(parcel: Parcel, cityPopulation: Long, cityCenter: Point?, rentIndexPpm: Long = 1_000_000L): Money {
        val zone = parcel.effectiveZone
        if (zone == ZoneType.PROTECTED) return Money.ZERO
        val size = if (parcel.city != null) citySizePpm(cityPopulation) else 300_000L
        val prox = if (cityCenter != null) proximityPpm(parcel.location.distanceTo(cityCenter)) else 1_000_000L
        var perUnit = mulDiv(BASE_PER_UNIT, zoneMultiplierPpm(zone), 1_000_000L)
        perUnit = mulDiv(perUnit, size, 1_000_000L)
        perUnit = mulDiv(perUnit, prox, 1_000_000L)
        perUnit = mulDiv(perUnit, rentIndexPpm, 1_000_000L)
        // Hard ground costs more to prepare, reflected in the asking price.
        perUnit = mulDiv(perUnit, 700_000L + parcel.terrain.buildCostPpm * 3 / 10, 1_000_000L)
        return Money(perUnit * parcel.area)
    }

    /** Annual property tax base for a parcel at [price]. */
    fun taxBase(price: Money): Money = price
}
