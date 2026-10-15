package app.many.economytycoon.futures

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FuturesId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick

data class FuturesParams(
    /** Days to expiry a contract can be opened with. */
    val tenors: List<Int> = listOf(30, 60, 90),
    /** Goods covered by one contract. */
    val contractSize: Quantity = Quantity.units(100),
    /** Cash locked as a share of the contract's value when it is opened. */
    val initialMarginPpm: Long = 150_000L,
    /** When equity falls below this share of the current value a margin call is issued. */
    val maintenancePpm: Long = 80_000L,
    /** Below this share the clearing house closes the position itself. */
    val liquidatePpm: Long = 35_000L,
    /** Cost of carry per year, built into the futures price. */
    val carryPpmPerYear: Long = 40_000L,
    /** Half the gap between what buyers pay and sellers get. */
    val spreadPpm: Long = 6_000L,
    /** Charged on the value when a position is opened. */
    val feePpm: Long = 1_500L,
    val maxContracts: Long = 40L,
    val maxOpenPerCompany: Int = 10,
    val clearingReserve: Money = Money.units(5_000_000),
    val historyDays: Int = 180,
)

enum class Side { LONG, SHORT }

enum class PositionStatus { OPEN, CLOSED, EXPIRED, LIQUIDATED }

/** One side of a cash-settled futures contract held against the clearing house. */
class Position(
    val id: FuturesId,
    val company: CompanyId,
    val good: GoodId,
    val side: Side,
    val contracts: Long,
    val entry: Money,
    val openedAt: Tick,
    val expiresAt: Tick,
) {
    var margin: Money = Money.ZERO
        internal set
    var mark: Money = entry
        internal set
    var status: PositionStatus = PositionStatus.OPEN
        internal set
    var calledAt: Tick? = null
        internal set
    var closedAt: Tick? = null
        internal set
    var realised: Money = Money.ZERO
        internal set
    var feePaid: Money = Money.ZERO
        internal set

    val isOpen get() = status == PositionStatus.OPEN

    internal fun restore(margin: Money, mark: Money, status: PositionStatus, calledAt: Tick?, closedAt: Tick?, realised: Money, fee: Money) {
        this.margin = margin; this.mark = mark; this.status = status; this.calledAt = calledAt; this.closedAt = closedAt
        this.realised = realised; feePaid = fee
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(company.value).putString(good.key).putInt(side.ordinal).putLong(contracts).putLong(entry.raw)
        h.putLong(openedAt.value).putLong(expiresAt.value).putLong(margin.raw).putLong(mark.raw).putInt(status.ordinal)
        h.putLong(calledAt?.value ?: -1L).putLong(closedAt?.value ?: -1L).putLong(realised.raw).putLong(feePaid.raw)
    }
}

sealed class FuturesEvent {
    abstract val position: FuturesId
    data class Opened(override val position: FuturesId, val company: CompanyId, val tick: Tick) : FuturesEvent()
    data class MarginCall(override val position: FuturesId, val company: CompanyId, val tick: Tick) : FuturesEvent()
    data class Liquidated(override val position: FuturesId, val company: CompanyId, val loss: Money, val tick: Tick) : FuturesEvent()
    data class Expired(override val position: FuturesId, val company: CompanyId, val realised: Money, val tick: Tick) : FuturesEvent()
}

/** What it costs to open a position right now. */
data class FuturesQuote(val price: Money, val notional: Money, val margin: Money, val fee: Money) {
    val cashNeeded: Money get() = margin + fee
}
