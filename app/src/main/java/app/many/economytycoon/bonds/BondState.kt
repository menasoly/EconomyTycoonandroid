package app.many.economytycoon.bonds

import app.many.economytycoon.core.BondId
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick

data class BondParams(
    /** Days between a government's chances to borrow, and the chance it does. */
    val issueEvery: Int = 60,
    val issueChancePpm: Long = 700_000L,
    /** Days investors have to subscribe before an issue closes. */
    val subscriptionDays: Int = 6,
    /** Lengths of bond a government may sell, in days. */
    val tenors: List<Int> = listOf(180, 360, 720),
    /** Days between coupon payments. */
    val couponEvery: Int = 30,
    val faceValue: Money = Money.units(1_000),
    val minUnits: Long = 200L,
    val maxUnits: Long = 2_500L,
    /** What a government pays above the market yield to get its issue taken up. */
    val premiumPpm: Long = 5_000L,
    /** Extra yield demanded of a country with no stability at all; scales down to zero for a perfectly stable one. */
    val riskSpreadMaxPpm: Long = 60_000L,
    /** Extra yield per year of tenor. */
    val termSlopePpm: Long = 4_000L,
    /** Half of the dealer's spread on price. */
    val dealerSpreadPpm: Long = 4_000L,
    val dealerReserve: Money = Money.units(20_000_000),
    val dealerMaxUnits: Long = 6_000L,
    val minPricePpm: Long = 600_000L,
    val maxPricePpm: Long = 1_300_000L,
    /** A government will not owe more than this many days of public spending in bonds. */
    val debtCeilingDays: Int = 120,
    val historyLength: Int = 40,
    val maxHoldersPerBond: Int = 64,
) {
    init { require(tenors.isNotEmpty() && couponEvery > 0 && minUnits in 1..maxUnits) }
}

enum class BondStatus { OPEN, OUTSTANDING, REDEEMED, CANCELLED }

/** What someone holds of one bond: units and what they paid for them. */
class Holding(units: Long, cost: Money) {
    var units: Long = units
        internal set
    var cost: Money = cost
        internal set

    val isEmpty get() = units <= 0L
}

/** The holder number the dealer's own stock is kept under. */
val DEALER = CompanyId(0L)

/** One issue of government debt: a fixed number of equal units that pay a coupon until they mature. */
class Bond(
    val id: BondId,
    val country: CountryId,
    val tenorDays: Int,
    val couponPpm: Long,
    val faceEach: Money,
    val unitsOffered: Long,
    val issuedAt: Tick,
    val subscriptionEnds: Tick,
) {
    val maturesAt: Tick get() = Tick(subscriptionEnds.value + tenorDays)

    var status: BondStatus = BondStatus.OPEN
        internal set
    var unitsSold: Long = 0L
        internal set
    var endedAt: Tick? = null
        internal set
    var couponsPaid: Money = Money.ZERO
        internal set
    internal val holders = java.util.TreeMap<CompanyId, Holding>()

    val isOpen get() = status == BondStatus.OPEN
    val isOutstanding get() = status == BondStatus.OUTSTANDING
    val unitsLeft get() = unitsOffered - unitsSold
    val faceTotal: Money get() = Money(faceEach.raw * unitsSold)
    fun heldBy(c: CompanyId): Long = holders[c]?.units ?: 0L
    fun costOf(c: CompanyId): Money = holders[c]?.cost ?: Money.ZERO
    fun holderIds(): List<CompanyId> = holders.keys.toList()
    fun unitsInCirculation(): Long = holders.values.sumOf { it.units }

    internal fun restore(status: BondStatus, sold: Long, endedAt: Tick?, coupons: Money, holders: Map<CompanyId, Holding>) {
        this.status = status; unitsSold = sold; this.endedAt = endedAt; couponsPaid = coupons
        this.holders.clear(); this.holders.putAll(holders)
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(country.value).putInt(tenorDays).putLong(couponPpm).putLong(faceEach.raw).putLong(unitsOffered)
        h.putLong(issuedAt.value).putLong(subscriptionEnds.value).putInt(status.ordinal).putLong(unitsSold).putLong(endedAt?.value ?: -1L).putLong(couponsPaid.raw)
        h.putInt(holders.size); for ((c, x) in holders) h.putLong(c.value).putLong(x.units).putLong(x.cost.raw)
    }
}

sealed class BondEvent {
    abstract val bond: BondId
    data class Issued(override val bond: BondId, val country: CountryId, val tick: Tick) : BondEvent()
    data class Placed(override val bond: BondId, val country: CountryId, val units: Long, val raised: Money, val tick: Tick) : BondEvent()
    data class NoSale(override val bond: BondId, val country: CountryId, val tick: Tick) : BondEvent()
    data class Coupon(override val bond: BondId, val country: CountryId, val paid: Money, val tick: Tick) : BondEvent()
    data class Redeemed(override val bond: BondId, val country: CountryId, val repaid: Money, val tick: Tick) : BondEvent()
}

/** A price the dealer will deal at, per unit and for the whole order. */
data class BondQuote(val bond: BondId, val units: Long, val buy: Boolean, val pricePerUnit: Money, val total: Money, val pricePpm: Long, val yieldPpm: Long)
