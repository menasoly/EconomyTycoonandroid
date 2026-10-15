package app.many.economytycoon.bonds

import app.many.economytycoon.core.BondId
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/**
 * The register of government bonds and the rules for pricing them. Moving money is left to the bond runner;
 * this class decides what a bond yields, who holds how much and when payments fall due.
 */
class BondService(private val ids: IdAllocators, val params: BondParams = BondParams()) {
    private val bonds = java.util.TreeMap<BondId, Bond>()
    var dealerFunded: Boolean = false
        internal set

    fun all(): List<Bond> = bonds.values.toList()
    fun get(id: BondId): Bond? = bonds[id]
    fun open(): List<Bond> = bonds.values.filter { it.isOpen }
    fun outstanding(): List<Bond> = bonds.values.filter { it.isOutstanding }
    fun live(): List<Bond> = bonds.values.filter { it.isOpen || it.isOutstanding }
    fun history(): List<Bond> = bonds.values.filter { !it.isOpen && !it.isOutstanding }
    fun heldBy(company: CompanyId): List<Bond> = bonds.values.filter { it.isOutstanding && it.heldBy(company) > 0L }
    fun ofCountry(country: CountryId): List<Bond> = bonds.values.filter { it.country == country }

    /** Face value of everything the country still owes or is currently offering. */
    fun owed(country: CountryId): Money =
        bonds.values.filter { it.country == country }.fold(Money.ZERO) { a, b ->
            when (b.status) {
                BondStatus.OPEN -> a + Money(b.faceEach.raw * b.unitsOffered)
                BondStatus.OUTSTANDING -> a + b.faceTotal
                else -> a
            }
        }

    // ---- pricing -------------------------------------------------------------------------------

    /** What investors want per year to lend to this country for this long. */
    fun marketYieldPpm(policyRatePpm: Long, stabilityPpm: Long, tenorDays: Int): Long {
        val risk = mulDiv(params.riskSpreadMaxPpm, (1_000_000L - stabilityPpm).coerceIn(0L, 1_000_000L), 1_000_000L)
        val term = mulDiv(params.termSlopePpm, tenorDays.toLong(), 360L)
        return policyRatePpm + risk + term
    }

    /** The coupon a new issue carries: the market yield, rounded up to a tenth of a percent, plus the premium. */
    fun couponFor(marketYieldPpm: Long): Long = ((marketYieldPpm + 999L) / 1_000L) * 1_000L + params.premiumPpm

    /** Price as parts per million of face for a bond whose market yield is [yieldPpm]. */
    fun pricePpm(bond: Bond, tick: Tick, yieldPpm: Long): Long {
        val left = (bond.maturesAt.value - maxOf(tick.value, bond.subscriptionEnds.value)).coerceAtLeast(0L)
        val adjust = (bond.couponPpm - yieldPpm) * left / 360L
        return (1_000_000L + adjust).coerceIn(params.minPricePpm, params.maxPricePpm)
    }

    fun unitPrice(bond: Bond, pricePpm: Long): Money = Money(mulDiv(bond.faceEach.raw, pricePpm, 1_000_000L))

    fun quote(bond: Bond, buy: Boolean, units: Long, tick: Tick, yieldPpm: Long): Outcome<BondQuote> {
        if (!bond.isOutstanding) return Outcome.reject(Rules.WRONG_STATE, "that bond is not trading")
        if (units <= 0L) return Outcome.reject(Rules.INVALID_QUANTITY, "trade at least one unit")
        val mid = pricePpm(bond, tick, yieldPpm)
        val ppm = if (buy) mid + params.dealerSpreadPpm else mid - params.dealerSpreadPpm
        val per = unitPrice(bond, ppm.coerceAtLeast(1L))
        return Outcome.ok(BondQuote(bond.id, units, buy, per, Money(per.raw * units), ppm, yieldPpm))
    }

    /** The coupon due on each unit on a payment date. */
    fun couponPerUnit(bond: Bond): Money = Money(mulDiv(bond.faceEach.raw, bond.couponPpm * params.couponEvery, 360L * 1_000_000L))

    // ---- issue and subscription ----------------------------------------------------------------

    fun issue(country: CountryId, tenorDays: Int, couponPpm: Long, units: Long, tick: Tick): Outcome<Bond> {
        if (tenorDays !in params.tenors) return Outcome.reject(Rules.INVALID_ARGUMENT, "that length of bond is not offered")
        if (units < params.minUnits || units > params.maxUnits) return Outcome.reject(Rules.INVALID_QUANTITY, "an issue is ${params.minUnits} to ${params.maxUnits} units")
        if (couponPpm <= 0L) return Outcome.reject(Rules.INVALID_PRICE, "a bond needs a coupon")
        val bond = Bond(ids.bond(), country, tenorDays, couponPpm, params.faceValue, units, tick, Tick(tick.value + params.subscriptionDays))
        bonds[bond.id] = bond
        return Outcome.ok(bond)
    }

    /** Checks an order against the issue; the runner takes the money and then calls [allot]. */
    fun checkSubscription(bond: Bond, company: CompanyId, units: Long): app.many.economytycoon.core.Rejection? = when {
        !bond.isOpen -> app.many.economytycoon.core.Rejection(Rules.WRONG_STATE, "the subscription period is over")
        units <= 0L -> app.many.economytycoon.core.Rejection(Rules.INVALID_QUANTITY, "subscribe for at least one unit")
        units > bond.unitsLeft -> app.many.economytycoon.core.Rejection(Rules.INSUFFICIENT_STOCK, "only ${bond.unitsLeft} units are left")
        bond.heldBy(company) == 0L && bond.holders.size >= params.maxHoldersPerBond -> app.many.economytycoon.core.Rejection(Rules.LIMIT_EXCEEDED, "the issue has as many investors as it can take")
        else -> null
    }

    /** Puts [units] into a holder's name at the given total cost. */
    fun allot(bond: Bond, holder: CompanyId, units: Long, cost: Money) {
        val h = bond.holders.getOrPut(holder) { Holding(0L, Money.ZERO) }
        h.units += units; h.cost = h.cost + cost
    }

    /** Takes units away from a holder and returns the cost that went with them. */
    fun take(bond: Bond, holder: CompanyId, units: Long): Money {
        val h = bond.holders[holder] ?: return Money.ZERO
        val n = minOf(units, h.units)
        val basis = if (n == h.units) h.cost else Money(mulDiv(h.cost.raw, n, h.units))
        h.units -= n; h.cost = h.cost - basis
        if (h.isEmpty) bond.holders.remove(holder)
        return basis
    }

    fun addSold(bond: Bond, units: Long) { bond.unitsSold += units }

    /** Closes the subscription of every issue whose time is up. */
    fun closeSubscriptions(tick: Tick, raised: (Bond) -> Money): List<BondEvent> {
        val out = ArrayList<BondEvent>()
        for (b in bonds.values.filter { it.isOpen && tick >= it.subscriptionEnds }) {
            if (b.unitsSold == 0L) {
                b.status = BondStatus.CANCELLED; b.endedAt = tick
                out += BondEvent.NoSale(b.id, b.country, tick)
            } else {
                b.status = BondStatus.OUTSTANDING
                out += BondEvent.Placed(b.id, b.country, b.unitsSold, raised(b), tick)
            }
        }
        return out
    }

    // ---- payments ------------------------------------------------------------------------------

    /** Outstanding bonds with a coupon payment today that is not also the maturity. */
    fun couponDue(tick: Tick): List<Bond> = bonds.values.filter {
        it.isOutstanding && tick > it.subscriptionEnds && tick < it.maturesAt && (tick.value - it.subscriptionEnds.value) % params.couponEvery == 0L
    }

    fun maturing(tick: Tick): List<Bond> = bonds.values.filter { it.isOutstanding && tick >= it.maturesAt }

    fun markRedeemed(bond: Bond, tick: Tick) { bond.status = BondStatus.REDEEMED; bond.endedAt = tick }

    fun addCoupons(bond: Bond, amount: Money) { bond.couponsPaid = bond.couponsPaid + amount }

    // ---- housekeeping --------------------------------------------------------------------------

    fun prune() {
        val finished = bonds.values.filter { !it.isOpen && !it.isOutstanding }.sortedBy { it.endedAt?.value ?: 0L }
        val surplus = finished.size - params.historyLength
        if (surplus > 0) for (b in finished.take(surplus)) bonds.remove(b.id)
    }

    /** Hands everything a company holds to the dealer without payment, as when a company is wound up. */
    fun transferAll(from: CompanyId) {
        for (b in bonds.values) {
            val h = b.holders[from] ?: continue
            allot(b, DEALER, h.units, h.cost)
            b.holders.remove(from)
        }
    }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(if (dealerFunded) 1 else 0)
        h.putInt(bonds.size); for (b in bonds.values) b.hashInto(h)
    }

    internal fun restore(b: Bond) { bonds[b.id] = b }
    internal fun clearAll() { bonds.clear(); dealerFunded = false }
}
