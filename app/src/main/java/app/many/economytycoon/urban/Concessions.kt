package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

enum class ConcessionKind(val label: String) {
    TOLL_ROAD("Toll road"), TRANSIT_LINE("Transit line"), ROAD_MAINTENANCE("Road maintenance")
}

/** How the concessionaire is paid. */
enum class PaymentModel(val label: String) {
    /** The concessionaire collects tolls (or fares) and shares them with the city. */
    TOLL("User charges"),
    /** The city pays a monthly sum for keeping the asset available, less deductions when it is not. */
    AVAILABILITY("Availability payment"),
    /** Both: a smaller availability payment plus user charges. */
    MIXED("Availability plus user charges"),
}

enum class ConcessionStatus(val label: String) {
    TENDER("Open for bids"), CONSTRUCTION("Under construction"), OPERATING("Operating"), ENDED("Handed back"), TERMINATED("Terminated"), NO_BIDS("No bids")
}

/** An offer to take the concession for less than the city's asking terms. */
data class ConcessionBid(val company: CompanyId, val discountPpm: Long, val at: Tick)

/** The terms and state of one public-private concession. */
class Concession(
    val id: ConcessionId,
    val kind: ConcessionKind,
    val name: String,
    /** The road tolled, or the road maintained. */
    val road: LinkId?,
    /** The line built and operated. */
    val line: TransitLineId?,
    val model: PaymentModel,
    val termMonths: Int,
    val constructionMonths: Int,
    /** Money the concessionaire invests in building. */
    val capex: Money,
    val toll: Money,
    /** The city's monthly availability payment before any discount. */
    val availabilityMonthly: Money,
    /** Share of user charges that go to the city, ppm, before any discount. */
    val cityShareBasePpm: Long,
    /** Vehicles a day below which the city compensates the concessionaire for part of the shortfall; 0 for no guarantee. */
    val trafficGuarantee: Long,
    val openedForBidsAt: Tick,
    val bidsCloseAt: Tick,
) {
    var status: ConcessionStatus = ConcessionStatus.TENDER
        internal set
    var concessionaire: CompanyId? = null
        internal set
    var winningDiscountPpm: Long = 0L
        internal set
    var awardedAt: Tick? = null
        internal set
    var operatesFrom: Tick? = null
        internal set
    var endsAt: Tick? = null
        internal set
    var capexPaid: Money = Money.ZERO
        internal set
    var userChargesTotal: Money = Money.ZERO
        internal set
    var cityShareTotal: Money = Money.ZERO
        internal set
    var availabilityPaidTotal: Money = Money.ZERO
        internal set
    var deductionsTotal: Money = Money.ZERO
        internal set
    var guaranteePaidTotal: Money = Money.ZERO
        internal set
    var lastRenegotiation: Tick = Tick(-10_000)
        internal set
    internal val bidList = ArrayList<ConcessionBid>()

    val bids: List<ConcessionBid> get() = bidList.toList()
    val isLive: Boolean get() = status == ConcessionStatus.CONSTRUCTION || status == ConcessionStatus.OPERATING

    /** The city's share of user charges after the winning bid. */
    val cityShareNowPpm: Long get() = (cityShareBasePpm + winningDiscountPpm).coerceAtMost(Scale.PPM)

    /** Monthly availability payment after the winning bid. */
    val availabilityNow: Money get() = Money(mulDiv(availabilityMonthly.raw, Scale.PPM - winningDiscountPpm, Scale.PPM))

    /** Investment not yet recovered through the straight-line write-off over the term. */
    fun unrecoveredCapex(now: Tick): Money {
        val from = operatesFrom ?: return capexPaid
        val total = termMonths.toLong() * Scale.MONTH_DAYS
        val used = (now.value - from.value).coerceIn(0L, total)
        return Money(mulDiv(capexPaid.raw, total - used, total.coerceAtLeast(1L)))
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putInt(kind.ordinal).putString(name).putLong(road?.value ?: -1L).putLong(line?.value ?: -1L).putInt(model.ordinal)
        h.putInt(termMonths).putInt(constructionMonths).putMoney(capex).putMoney(toll).putMoney(availabilityMonthly).putLong(cityShareBasePpm)
        h.putLong(trafficGuarantee).putLong(openedForBidsAt.value).putLong(bidsCloseAt.value).putInt(status.ordinal)
        h.putLong(concessionaire?.value ?: -1L).putLong(winningDiscountPpm).putLong(awardedAt?.value ?: -1L).putLong(operatesFrom?.value ?: -1L)
        h.putLong(endsAt?.value ?: -1L).putMoney(capexPaid).putMoney(userChargesTotal).putMoney(cityShareTotal).putMoney(availabilityPaidTotal)
        h.putMoney(deductionsTotal).putMoney(guaranteePaidTotal).putLong(lastRenegotiation.value)
        h.putInt(bidList.size)
        for (b in bidList) h.putLong(b.company.value).putLong(b.discountPpm).putLong(b.at.value)
    }
}

sealed class ConcessionEvent {
    abstract val concession: ConcessionId
    data class Awarded(override val concession: ConcessionId, val company: CompanyId, val discountPpm: Long, val tick: Tick) : ConcessionEvent()
    data class NoBids(override val concession: ConcessionId, val tick: Tick) : ConcessionEvent()
    data class Opened(override val concession: ConcessionId, val tick: Tick) : ConcessionEvent()
    data class HandedBack(override val concession: ConcessionId, val tick: Tick) : ConcessionEvent()
}

/** One month of money flowing under a concession. Everything is nonnegative; the direction is in the field name. */
data class ConcessionSettlement(
    val concession: ConcessionId,
    val concessionaire: CompanyId,
    /** Paid by road users or riders to the concessionaire. */
    val userCharges: Money,
    /** Part of user charges passed on to the city. */
    val cityShare: Money,
    /** Availability payment due before deductions. */
    val availabilityDue: Money,
    /** Taken off the availability payment for poor performance. */
    val deduction: Money,
    /** Paid by the city to cover a traffic shortfall under the guarantee. */
    val guarantee: Money,
    /** Capital the concessionaire spends this month while the asset is being built. */
    val capexSpend: Money,
) {
    val availabilityNet: Money get() = availabilityDue - deduction
    /** What the city pays the concessionaire in all. */
    val cityPays: Money get() = availabilityNet + guarantee
    /** What the concessionaire earns net of the share it passes on, before its own costs. */
    val concessionaireIncome: Money get() = userCharges - cityShare + cityPays
}

/** The concessions of one city. */
class ConcessionBook {
    private val concessions = java.util.TreeMap<ConcessionId, Concession>()

    fun all(): List<Concession> = concessions.values.toList()
    fun get(id: ConcessionId): Concession? = concessions[id]
    fun live(): List<Concession> = concessions.values.filter { it.isLive }
    fun operating(): List<Concession> = concessions.values.filter { it.status == ConcessionStatus.OPERATING }
    fun tendering(): List<Concession> = concessions.values.filter { it.status == ConcessionStatus.TENDER }
    fun heldBy(company: CompanyId): List<Concession> = live().filter { it.concessionaire == company }
    fun onRoad(road: LinkId): Concession? = concessions.values.firstOrNull { it.road == road && (it.isLive || it.status == ConcessionStatus.TENDER) }

    @Suppress("LongParameterList")
    fun tender(
        id: ConcessionId, kind: ConcessionKind, name: String, road: LinkId?, line: TransitLineId?, model: PaymentModel, termMonths: Int,
        constructionMonths: Int, capex: Money, toll: Money, availabilityMonthly: Money, cityShareBasePpm: Long, trafficGuarantee: Long,
        now: Tick, tenderDays: Int = TENDER_DAYS,
    ): Outcome<Concession> {
        if (name.isBlank()) return Outcome.reject(Rules.INVALID_ARGUMENT, "a concession needs a name")
        if (termMonths !in 60..480) return Outcome.reject(Rules.INVALID_ARGUMENT, "a concession runs between 5 and 40 years")
        if (constructionMonths !in 0..120) return Outcome.reject(Rules.INVALID_ARGUMENT, "construction takes at most 10 years")
        if (capex.isNegative || toll.isNegative || availabilityMonthly.isNegative) return Outcome.reject(Rules.INVALID_ARGUMENT, "amounts cannot be negative")
        if (cityShareBasePpm !in 0..Scale.PPM) return Outcome.reject(Rules.INVALID_ARGUMENT, "the city share is a share")
        if (kind == ConcessionKind.TOLL_ROAD && road == null) return Outcome.reject(Rules.INVALID_ARGUMENT, "a toll road concession needs a road")
        if (kind == ConcessionKind.ROAD_MAINTENANCE && road == null) return Outcome.reject(Rules.INVALID_ARGUMENT, "road maintenance needs a road")
        if (kind == ConcessionKind.TRANSIT_LINE && line == null) return Outcome.reject(Rules.INVALID_ARGUMENT, "a transit concession needs a line")
        if (model != PaymentModel.AVAILABILITY && kind == ConcessionKind.ROAD_MAINTENANCE) return Outcome.reject(Rules.INVALID_ARGUMENT, "maintenance is paid by availability")
        if (model != PaymentModel.TOLL && !availabilityMonthly.isPositive) return Outcome.reject(Rules.INVALID_ARGUMENT, "an availability concession needs a payment")
        if (road != null && onRoad(road) != null) return Outcome.reject(Rules.DUPLICATE, "that road is already under a concession")
        val c = Concession(
            id, kind, name, road, line, model, termMonths, constructionMonths, capex, toll, availabilityMonthly, cityShareBasePpm, trafficGuarantee,
            now, Tick(now.value + tenderDays),
        )
        concessions[id] = c
        return Outcome.ok(c)
    }

    fun bid(id: ConcessionId, company: CompanyId, discountPpm: Long, now: Tick): Outcome<ConcessionBid> {
        val c = concessions[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such concession")
        if (c.status != ConcessionStatus.TENDER || now >= c.bidsCloseAt) return Outcome.reject(Rules.WRONG_STATE, "bidding is closed")
        if (discountPpm !in 0..MAX_DISCOUNT_PPM) return Outcome.reject(Rules.INVALID_ARGUMENT, "the discount is between 0 and 40 percent")
        if (c.bidList.any { it.company == company }) return Outcome.reject(Rules.DUPLICATE, "that company has already bid")
        val b = ConcessionBid(company, discountPpm, now)
        c.bidList += b
        return Outcome.ok(b)
    }

    /** The bid that wins: the largest discount, and among equals the earlier bid, then the lower company id. */
    fun winner(c: Concession): ConcessionBid? =
        c.bidList.sortedWith(compareBy<ConcessionBid>({ -it.discountPpm }, { it.at }, { it.company })).firstOrNull()

    /** Closes tenders that are due, starts and finishes construction and ends concessions that have run their term. */
    fun advanceDay(now: Tick): List<ConcessionEvent> {
        val out = ArrayList<ConcessionEvent>()
        for (c in concessions.values) {
            when (c.status) {
                ConcessionStatus.TENDER -> if (now >= c.bidsCloseAt) {
                    val w = winner(c)
                    if (w == null) {
                        c.status = ConcessionStatus.NO_BIDS
                        out += ConcessionEvent.NoBids(c.id, now)
                    } else {
                        c.concessionaire = w.company
                        c.winningDiscountPpm = w.discountPpm
                        c.awardedAt = now
                        val opens = Tick(now.value + c.constructionMonths * Scale.MONTH_DAYS)
                        c.operatesFrom = opens
                        c.endsAt = Tick(opens.value + c.termMonths * Scale.MONTH_DAYS)
                        c.status = if (c.constructionMonths == 0) ConcessionStatus.OPERATING else ConcessionStatus.CONSTRUCTION
                        out += ConcessionEvent.Awarded(c.id, w.company, w.discountPpm, now)
                        if (c.constructionMonths == 0) out += ConcessionEvent.Opened(c.id, now)
                    }
                }
                ConcessionStatus.CONSTRUCTION -> if (now >= (c.operatesFrom ?: now)) {
                    c.status = ConcessionStatus.OPERATING
                    out += ConcessionEvent.Opened(c.id, now)
                }
                ConcessionStatus.OPERATING -> if (now >= (c.endsAt ?: now)) {
                    c.status = ConcessionStatus.ENDED
                    out += ConcessionEvent.HandedBack(c.id, now)
                }
                else -> {}
            }
        }
        return out
    }

    /**
     * One month of settlement for a live concession. [vehiclesPerDay] are the vehicles (or riders) that used the asset;
     * [availabilityPermille] says how well the asset was kept available. Returns null if the concession is not live.
     */
    fun settleMonth(id: ConcessionId, vehiclesPerDay: Long, availabilityPermille: Int, riderCharges: Money = Money.ZERO): ConcessionSettlement? {
        val c = concessions[id] ?: return null
        val company = c.concessionaire ?: return null
        if (c.status == ConcessionStatus.CONSTRUCTION) {
            val months = c.constructionMonths.coerceAtLeast(1)
            val spend = Money.min(c.capex - c.capexPaid, Money(c.capex.raw / months))
            c.capexPaid += spend
            return ConcessionSettlement(id, company, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, spend)
        }
        if (c.status != ConcessionStatus.OPERATING) return null
        val charging = c.model != PaymentModel.AVAILABILITY
        val paying = c.model != PaymentModel.TOLL
        val vehicles = vehiclesPerDay.coerceAtLeast(0L)
        val userCharges = if (charging) c.toll * (vehicles * Scale.MONTH_DAYS) + riderCharges else Money.ZERO
        val cityShare = Money(mulDiv(userCharges.raw, c.cityShareNowPpm, Scale.PPM))
        val due = if (paying) c.availabilityNow else Money.ZERO
        val shortfall = (c.trafficGuarantee - vehicles).coerceAtLeast(0L)
        val guarantee = if (charging && c.trafficGuarantee > 0L) Money(mulDiv(c.toll.raw * shortfall * Scale.MONTH_DAYS, 500_000L, Scale.PPM)) else Money.ZERO
        val score = availabilityPermille.coerceIn(0, 1000)
        val deduction = if (!paying || score >= 950) Money.ZERO else {
            val missing = (1000 - score).toLong()
            val factor = if (score < 800) 2L else 1L
            Money.min(due, Money(mulDiv(due.raw, missing * factor, 1000L)))
        }
        c.userChargesTotal += userCharges
        c.cityShareTotal += cityShare
        c.availabilityPaidTotal += due - deduction
        c.deductionsTotal += deduction
        c.guaranteePaidTotal += guarantee
        return ConcessionSettlement(id, company, userCharges, cityShare, due, deduction, guarantee, Money.ZERO)
    }

    /**
     * Ends a concession early. The city compensates the concessionaire for the part of its investment that has not yet been
     * recovered; a tenth of that is deducted because the concessionaire keeps the asset's salvage and its remaining contract rights.
     */
    fun terminate(id: ConcessionId, now: Tick): Outcome<Money> {
        val c = concessions[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such concession")
        if (!c.isLive) return Outcome.reject(Rules.WRONG_STATE, "that concession is not running")
        val owed = Money(mulDiv(c.unrecoveredCapex(now).raw, 900_000L, Scale.PPM))
        c.status = ConcessionStatus.TERMINATED
        c.endsAt = now
        return Outcome.ok(owed)
    }

    /** Changes the toll of a running concession. Allowed once a year; a cut is compensated for a year of lost charges. */
    fun renegotiateToll(id: ConcessionId, newToll: Money, expectedVehiclesPerDay: Long, now: Tick): Outcome<Money> {
        val c = concessions[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such concession")
        if (c.status != ConcessionStatus.OPERATING) return Outcome.reject(Rules.WRONG_STATE, "only an operating concession can be renegotiated")
        if (c.model == PaymentModel.AVAILABILITY) return Outcome.reject(Rules.WRONG_STATE, "this concession charges no tolls")
        if (newToll.isNegative) return Outcome.reject(Rules.INVALID_ARGUMENT, "a toll cannot be negative")
        if (now.value - c.lastRenegotiation.value < Scale.YEAR_DAYS) return Outcome.reject(Rules.RATE_LIMITED, "terms can be reopened once a year")
        val cut = (c.toll - newToll).coerceAtLeast(Money.ZERO)
        val compensation = cut * (expectedVehiclesPerDay.coerceAtLeast(0L) * Scale.YEAR_DAYS)
        val replaced = Concession(
            c.id, c.kind, c.name, c.road, c.line, c.model, c.termMonths, c.constructionMonths, c.capex, newToll, c.availabilityMonthly,
            c.cityShareBasePpm, c.trafficGuarantee, c.openedForBidsAt, c.bidsCloseAt,
        )
        copyState(c, replaced)
        replaced.lastRenegotiation = now
        concessions[id] = replaced
        return Outcome.ok(compensation)
    }

    private fun copyState(from: Concession, to: Concession) {
        to.status = from.status; to.concessionaire = from.concessionaire; to.winningDiscountPpm = from.winningDiscountPpm
        to.awardedAt = from.awardedAt; to.operatesFrom = from.operatesFrom; to.endsAt = from.endsAt; to.capexPaid = from.capexPaid
        to.userChargesTotal = from.userChargesTotal; to.cityShareTotal = from.cityShareTotal
        to.availabilityPaidTotal = from.availabilityPaidTotal; to.deductionsTotal = from.deductionsTotal
        to.guaranteePaidTotal = from.guaranteePaidTotal; to.lastRenegotiation = from.lastRenegotiation
        to.bidList.addAll(from.bidList)
    }

    internal fun restore(c: Concession) { concessions[c.id] = c }
    internal fun clear() { concessions.clear() }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(concessions.size)
        for (c in concessions.values) c.hashInto(h)
    }

    companion object {
        const val TENDER_DAYS = 45
        const val MAX_DISCOUNT_PPM = 400_000L
    }
}
