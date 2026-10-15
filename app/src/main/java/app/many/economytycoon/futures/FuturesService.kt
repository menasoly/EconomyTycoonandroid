package app.many.economytycoon.futures

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FuturesId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rejection
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/**
 * The register of futures positions and the arithmetic of margin. Moving cash is left to the futures runner;
 * this class decides what a contract costs, what a position is worth and when the clearing house steps in.
 */
class FuturesService(private val ids: IdAllocators, val params: FuturesParams = FuturesParams()) {
    private val positions = java.util.TreeMap<FuturesId, Position>()
    var clearingFunded: Boolean = false
        internal set

    fun all(): List<Position> = positions.values.toList()
    fun get(id: FuturesId): Position? = positions[id]
    fun open(): List<Position> = positions.values.filter { it.isOpen }
    fun openOf(company: CompanyId): List<Position> = positions.values.filter { it.isOpen && it.company == company }
    fun historyOf(company: CompanyId): List<Position> = positions.values.filter { !it.isOpen && it.company == company }

    // ---- pricing -------------------------------------------------------------------------------

    /** The price for delivery in [daysLeft] days: today's price plus the cost of carrying the goods until then. */
    fun futuresPrice(spot: Money, daysLeft: Int): Money =
        Money(spot.raw + mulDiv(spot.raw, params.carryPpmPerYear * daysLeft.coerceAtLeast(0), 360L * 1_000_000L))

    /** Buyers pay a little over the futures price and sellers get a little under it. */
    fun entryPrice(spot: Money, daysLeft: Int, side: Side): Money {
        val fair = futuresPrice(spot, daysLeft).raw
        val cut = mulDiv(fair, params.spreadPpm, 1_000_000L)
        return Money(if (side == Side.LONG) fair + cut else fair - cut)
    }

    fun notional(price: Money, contracts: Long): Money = price.valueOf(params.contractSize * contracts)

    fun initialMargin(notional: Money): Money = Money(mulDiv(notional.raw, params.initialMarginPpm, 1_000_000L))
    fun maintenance(notional: Money): Money = Money(mulDiv(notional.raw, params.maintenancePpm, 1_000_000L))
    fun liquidationLevel(notional: Money): Money = Money(mulDiv(notional.raw, params.liquidatePpm, 1_000_000L))
    fun fee(notional: Money): Money = Money(mulDiv(notional.raw, params.feePpm, 1_000_000L))

    fun quote(spot: Money, tenor: Int, side: Side, contracts: Long): FuturesQuote {
        val price = entryPrice(spot, tenor, side)
        val value = notional(price, contracts)
        return FuturesQuote(price, value, initialMargin(value), fee(value))
    }

    // ---- positions -----------------------------------------------------------------------------

    /** Profit (negative for a loss) of closing [side] bought or sold at [entry] when the price is [price]. */
    fun pnl(side: Side, entry: Money, price: Money, contracts: Long): Money {
        val perContract = if (side == Side.LONG) price - entry else entry - price
        return perContract.valueOf(params.contractSize * contracts)
    }

    fun pnl(p: Position, price: Money): Money = pnl(p.side, p.entry, price, p.contracts)
    fun unrealised(p: Position): Money = pnl(p, p.mark)
    fun equity(p: Position): Money = p.margin + unrealised(p)
    fun currentNotional(p: Position): Money = notional(p.mark, p.contracts)
    fun needsCall(p: Position): Boolean = equity(p) < maintenance(currentNotional(p))
    fun mustLiquidate(p: Position): Boolean = equity(p) < liquidationLevel(currentNotional(p))
    fun daysLeft(p: Position, tick: Tick): Int = (p.expiresAt.value - tick.value).coerceAtLeast(0L).toInt()

    /** How much cash would lift the position back above the maintenance level. */
    fun shortfall(p: Position): Money {
        val need = maintenance(currentNotional(p)) - equity(p)
        return if (need.raw > 0L) need else Money.ZERO
    }

    fun checkOpen(company: CompanyId, tenor: Int, contracts: Long): Rejection? = when {
        tenor !in params.tenors -> Rejection(Rules.INVALID_ARGUMENT, "choose one of ${params.tenors.joinToString(", ")} days")
        contracts < 1L -> Rejection(Rules.INVALID_ARGUMENT, "trade at least one contract")
        contracts > params.maxContracts -> Rejection(Rules.LIMIT_EXCEEDED, "at most ${params.maxContracts} contracts in one position")
        openOf(company).size >= params.maxOpenPerCompany -> Rejection(Rules.LIMIT_EXCEEDED, "too many open positions")
        else -> null
    }

    fun open(company: CompanyId, good: GoodId, side: Side, contracts: Long, entry: Money, margin: Money, fee: Money, tick: Tick, tenor: Int): Position {
        val p = Position(ids.future(), company, good, side, contracts, entry, tick, Tick(tick.value + tenor))
        p.margin = margin; p.feePaid = fee
        positions[p.id] = p
        return p
    }

    fun markTo(p: Position, price: Money) { p.mark = price }

    fun addMargin(p: Position, amount: Money): Outcome<Money> {
        if (!p.isOpen) return Outcome.reject(Rules.WRONG_STATE, "the position is closed")
        if (amount.raw <= 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "add a positive amount")
        p.margin = p.margin + amount
        if (!needsCall(p)) p.calledAt = null
        return Outcome.ok(p.margin)
    }

    fun call(p: Position, tick: Tick): Boolean {
        if (p.calledAt != null) return false
        p.calledAt = tick
        return true
    }

    fun clearCall(p: Position) { p.calledAt = null }

    fun finish(p: Position, status: PositionStatus, tick: Tick, realised: Money) {
        p.status = status; p.closedAt = tick; p.realised = realised; p.margin = Money.ZERO; p.calledAt = null
    }

    /** Drops positions that closed long enough ago. */
    fun prune(tick: Tick) {
        val cutoff = tick.value - params.historyDays
        positions.values.removeIf { !it.isOpen && (it.closedAt?.value ?: 0L) < cutoff }
    }

    fun hashInto(h: CanonicalHasher) {
        h.putBoolean(clearingFunded).putInt(positions.size)
        for (p in positions.values) p.hashInto(h)
    }

    internal fun restore(p: Position) { positions[p.id] = p }
    internal fun clearAll() { positions.clear() }
}
