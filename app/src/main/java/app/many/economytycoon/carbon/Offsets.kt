package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/**
 * What a kind of offset project costs and how it behaves. All shares are ppm.
 *
 *  * [upfrontPerAnnualTonne]: what it costs to fund a stream of one nominal tonne a year for the whole life.
 *  * [haircutPpm]: the share of claimed reductions that would have happened anyway (lack of additionality); it is never issued.
 *  * [deliveryRiskPpm] and [underDeliveryPpm]: the chance that an issuance period falls short, and by how much.
 *  * [bufferPpm]: the share of issued credits held back as insurance against reversal; [reversalRiskPpm] is the chance per issuance that the insurance is used up.
 *  * [validationFailPpm]: the chance the independent verifier turns the project down.
 */
data class OffsetProfile(
    val kind: OffsetKind,
    val upfrontPerAnnualTonne: Money,
    val leadDays: Int,
    val validationDays: Int,
    val issuanceEveryDays: Int,
    val lifeYears: Int,
    val haircutPpm: Long,
    val deliveryRiskPpm: Long,
    val underDeliveryPpm: Long,
    val bufferPpm: Long,
    val reversalRiskPpm: Long,
    val validationFailPpm: Long,
    val spotBase: Money,
    val marketMonthly: Tonnes,
) {
    init {
        require(leadDays > 0 && validationDays > 0 && issuanceEveryDays > 0 && lifeYears > 0) { "durations must be positive" }
        require(listOf(haircutPpm, deliveryRiskPpm, underDeliveryPpm, bufferPpm, reversalRiskPpm, validationFailPpm).all { it in 0L..Rate.ONE_PPM }) { "shares must be between 0 and 100%" }
    }

    /** Credits a project of [annual] nominal tonnes a year is expected to yield over its life, after the haircut and the buffer. */
    fun expectedCredits(annual: Tonnes): Tonnes {
        val nominal = annual * lifeYears.toLong()
        val net = nominal.times(Rate(Rate.ONE_PPM - haircutPpm))
        return net - net.times(Rate(bufferPpm))
    }

    /** The cost of one credit if the project delivers as expected: the upfront money over the credits it should yield. */
    fun costPerExpectedCredit(): Money {
        val credits = expectedCredits(Tonnes.of(1))
        return if (credits.raw <= 0L) Money.ZERO else Money(mulDiv(upfrontPerAnnualTonne.raw, Tonnes.SCALE, credits.raw))
    }
}

object OffsetProfiles {
    private val all: Map<OffsetKind, OffsetProfile> = listOf(
        OffsetProfile(OffsetKind.REFORESTATION, Money.units(110), 360, 60, 180, 20, 250_000L, 250_000L, 400_000L, 200_000L, 30_000L, 100_000L, Money.units(8), Tonnes.of(12_000)),
        OffsetProfile(OffsetKind.METHANE_CAPTURE, Money.units(100), 90, 30, 90, 10, 100_000L, 100_000L, 200_000L, 0L, 0L, 50_000L, Money.units(14), Tonnes.of(6_000)),
        OffsetProfile(OffsetKind.CLEAN_COOKING, Money.units(34), 60, 30, 90, 8, 450_000L, 300_000L, 300_000L, 0L, 0L, 80_000L, Money.units(6), Tonnes.of(20_000)),
        OffsetProfile(OffsetKind.DIRECT_AIR_CAPTURE, Money.units(720), 540, 30, 90, 15, 0L, 50_000L, 100_000L, 0L, 0L, 10_000L, Money.units(60), Tonnes.of(800)),
    ).associateBy { it.kind }

    fun of(kind: OffsetKind): OffsetProfile = all.getValue(kind)
}

enum class OffsetStatus(val label: String) {
    DEVELOPMENT("In development"),
    VALIDATION("Being validated"),
    ISSUING("Issuing credits"),
    COMPLETED("Completed"),
    REJECTED("Rejected"),
}

/** A project that avoids or removes carbon and is paid for by credits. [owner] is null for the projects of the open market. */
class OffsetProject(
    val id: OffsetProjectId,
    val kind: OffsetKind,
    val owner: CompanyId?,
    val host: CountryId,
    val annual: Tonnes,
    val capex: Money,
    val startedAt: Tick,
) {
    var status: OffsetStatus = OffsetStatus.DEVELOPMENT
        internal set
    var validationAt: Tick = startedAt
        internal set
    var nextIssuance: Tick = startedAt
        internal set
    var endsAt: Tick = startedAt
        internal set
    var issued: Tonnes = Tonnes.ZERO
        internal set
    var buffer: Tonnes = Tonnes.ZERO
        internal set
    var bufferLost: Tonnes = Tonnes.ZERO
        internal set
    var periods: Int = 0
        internal set
    var shortPeriods: Int = 0
        internal set
    /** The part of the money paid that has not yet been charged to credits. */
    var basisLeft: Money = capex
        internal set

    val isLive: Boolean get() = status == OffsetStatus.DEVELOPMENT || status == OffsetStatus.VALIDATION || status == OffsetStatus.ISSUING

    internal fun setSchedule(validationAt: Tick, nextIssuance: Tick, endsAt: Tick) {
        this.validationAt = validationAt; this.nextIssuance = nextIssuance; this.endsAt = endsAt
    }

    internal fun restore(
        status: OffsetStatus, validationAt: Tick, nextIssuance: Tick, endsAt: Tick, issued: Tonnes, buffer: Tonnes, bufferLost: Tonnes,
        periods: Int, shortPeriods: Int, basisLeft: Money,
    ) {
        this.status = status; this.validationAt = validationAt; this.nextIssuance = nextIssuance; this.endsAt = endsAt
        this.issued = issued; this.buffer = buffer; this.bufferLost = bufferLost; this.periods = periods; this.shortPeriods = shortPeriods; this.basisLeft = basisLeft
    }

    internal fun advanceStatus(s: OffsetStatus) { status = s }
    internal fun addIssued(credits: Tonnes, bufferAdded: Tonnes, short: Boolean) {
        issued += credits; buffer += bufferAdded; periods++; if (short) shortPeriods++
    }
    internal fun loseBuffer() { bufferLost += buffer; buffer = Tonnes.ZERO }
    internal fun releaseBuffer(): Tonnes { val b = buffer; buffer = Tonnes.ZERO; return b }
    internal fun amortise(amount: Money) { basisLeft -= amount }
}

/** Credits of one kind held by one company, with what they cost. */
class OffsetLot(tonnes: Tonnes, cost: Money) {
    var tonnes: Tonnes = tonnes
        internal set
    var cost: Money = cost
        internal set
}

/** A purchase quote from the exchange. */
data class OffsetQuote(val kind: OffsetKind, val tonnes: Tonnes, val unitPrice: Money, val total: Money, val slippagePpm: Long)

/**
 * The open market for credits: a stock of credits per kind and a spot price that moves with buying pressure. Credits
 * issued to the market's own projects and the monthly trickle of other sellers feed the stock.
 */
class OffsetExchange {
    private val stockMap = java.util.EnumMap<OffsetKind, Tonnes>(OffsetKind::class.java)
    private val spotMap = java.util.EnumMap<OffsetKind, Money>(OffsetKind::class.java)
    private val boughtMonth = java.util.EnumMap<OffsetKind, Tonnes>(OffsetKind::class.java)
    private val addedMonth = java.util.EnumMap<OffsetKind, Tonnes>(OffsetKind::class.java)

    init { for (k in OffsetKind.values()) { stockMap[k] = Tonnes.ZERO; spotMap[k] = OffsetProfiles.of(k).spotBase; boughtMonth[k] = Tonnes.ZERO; addedMonth[k] = Tonnes.ZERO } }

    fun stock(kind: OffsetKind): Tonnes = stockMap.getValue(kind)
    fun spot(kind: OffsetKind): Money = spotMap.getValue(kind)
    fun boughtThisMonth(kind: OffsetKind): Tonnes = boughtMonth.getValue(kind)
    fun addedThisMonth(kind: OffsetKind): Tonnes = addedMonth.getValue(kind)

    /**
     * What buying [tonnes] would cost. Large orders move the price: the unit price rises by half the share of the order in
     * the stock-plus-order, up to 50%. An order for more than the stock cannot be filled.
     */
    fun quote(kind: OffsetKind, tonnes: Tonnes): OffsetQuote? {
        val stock = stock(kind)
        if (!tonnes.isPositive || tonnes > stock) return null
        val slip = (mulDiv(tonnes.raw, Rate.ONE_PPM, stock.raw + tonnes.raw) / 2).coerceAtMost(500_000L)
        val unit = Money(mulDiv(spot(kind).raw, Rate.ONE_PPM + slip, Rate.ONE_PPM, RoundingMode.HALF_EVEN))
        return OffsetQuote(kind, tonnes, unit, tonnes.cost(unit), slip)
    }

    internal fun add(kind: OffsetKind, tonnes: Tonnes) { stockMap[kind] = stock(kind) + tonnes; addedMonth[kind] = addedThisMonth(kind) + tonnes }
    internal fun remove(kind: OffsetKind, tonnes: Tonnes) { stockMap[kind] = stock(kind) - tonnes; boughtMonth[kind] = boughtThisMonth(kind) + tonnes }

    /** Moves each spot price by the month's buying pressure and starts a new month. */
    internal fun closeMonth() {
        for (k in OffsetKind.values()) {
            val base = OffsetProfiles.of(k).spotBase
            val bought = boughtThisMonth(k).raw
            val added = addedThisMonth(k).raw
            val pressure = mulDiv(bought - added, Rate.ONE_PPM, stock(k).raw + added + bought + Tonnes.of(1).raw)
            val moved = mulDiv(spot(k).raw, Rate.ONE_PPM + pressure / 4, Rate.ONE_PPM, RoundingMode.HALF_EVEN)
            spotMap[k] = Money(moved.coerceIn(base.raw / 2, base.raw * 5))
            boughtMonth[k] = Tonnes.ZERO
            addedMonth[k] = Tonnes.ZERO
        }
    }

    internal fun restore(kind: OffsetKind, stock: Tonnes, spot: Money, bought: Tonnes, added: Tonnes) {
        stockMap[kind] = stock; spotMap[kind] = spot; boughtMonth[kind] = bought; addedMonth[kind] = added
    }

    internal fun reset() { for (k in OffsetKind.values()) restore(k, Tonnes.ZERO, OffsetProfiles.of(k).spotBase, Tonnes.ZERO, Tonnes.ZERO) }
}

/** Everything about offsets: the projects, who holds credits, and the exchange. */
class OffsetBook {
    private val projectMap = java.util.TreeMap<OffsetProjectId, OffsetProject>()
    private val holdingMap = java.util.TreeMap<CompanyId, java.util.EnumMap<OffsetKind, OffsetLot>>()
    val exchange = OffsetExchange()
    /** Credits retired for compliance or voluntarily, by company and year, for reporting. */
    private val retiredMap = java.util.TreeMap<CompanyYearKey, Long>()

    fun projects(): List<OffsetProject> = projectMap.values.toList()
    fun project(id: OffsetProjectId): OffsetProject? = projectMap[id]
    fun projectsOf(company: CompanyId): List<OffsetProject> = projectMap.values.filter { it.owner == company }
    fun companies(): List<CompanyId> = holdingMap.keys.toList()

    fun held(company: CompanyId, kind: OffsetKind): Tonnes = holdingMap[company]?.get(kind)?.tonnes ?: Tonnes.ZERO
    fun heldTotal(company: CompanyId): Tonnes = Tonnes.sum(OffsetKind.values().map { held(company, it) })
    fun costOf(company: CompanyId, kind: OffsetKind): Money = holdingMap[company]?.get(kind)?.cost ?: Money.ZERO
    fun retiredBy(company: CompanyId, country: CountryId, year: Int): Tonnes = Tonnes(retiredMap[CompanyYearKey(company, country, year)] ?: 0L)

    internal fun put(p: OffsetProject) { projectMap[p.id] = p }

    internal fun credit(company: CompanyId, kind: OffsetKind, tonnes: Tonnes, cost: Money) {
        if (tonnes.raw <= 0L && cost.raw <= 0L) return
        val lot = holdingMap.getOrPut(company) { java.util.EnumMap(OffsetKind::class.java) }.getOrPut(kind) { OffsetLot(Tonnes.ZERO, Money.ZERO) }
        lot.tonnes += tonnes
        lot.cost += cost
    }

    /** Removes up to [wanted] credits and returns how many came out and what they had cost. */
    internal fun debit(company: CompanyId, kind: OffsetKind, wanted: Tonnes): Pair<Tonnes, Money> {
        val mine = holdingMap[company] ?: return Tonnes.ZERO to Money.ZERO
        val lot = mine[kind] ?: return Tonnes.ZERO to Money.ZERO
        val part = Tonnes.min(wanted, lot.tonnes)
        if (part.raw <= 0L) return Tonnes.ZERO to Money.ZERO
        val cost = if (part == lot.tonnes) lot.cost else Money(mulDiv(lot.cost.raw, part.raw, lot.tonnes.raw, RoundingMode.HALF_EVEN))
        lot.tonnes -= part
        lot.cost -= cost
        if (lot.tonnes.raw == 0L) {
            // Any rounding remainder of cost leaves with the last credit.
            mine.remove(kind)
            if (mine.isEmpty()) holdingMap.remove(company)
            return part to (cost + lot.cost)
        }
        return part to cost
    }

    internal fun noteRetired(company: CompanyId, country: CountryId, year: Int, tonnes: Tonnes) {
        retiredMap.merge(CompanyYearKey(company, country, year), tonnes.raw, Long::plus)
    }

    internal fun retiredEntries(): List<Pair<CompanyYearKey, Long>> = retiredMap.entries.map { it.key to it.value }
    internal fun holdingEntries(): List<Triple<CompanyId, OffsetKind, OffsetLot>> =
        holdingMap.flatMap { (c, m) -> m.entries.map { Triple(c, it.key, it.value) } }

    internal fun putRetired(key: CompanyYearKey, raw: Long) { retiredMap[key] = raw }
    internal fun putLot(company: CompanyId, kind: OffsetKind, lot: OffsetLot) {
        holdingMap.getOrPut(company) { java.util.EnumMap(OffsetKind::class.java) }[kind] = lot
    }
    internal fun clear() { projectMap.clear(); holdingMap.clear(); retiredMap.clear(); exchange.reset() }
}
