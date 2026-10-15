package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FuturesId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.TransactionDraft
import app.many.economytycoon.futures.FuturesEvent
import app.many.economytycoon.futures.FuturesQuote
import app.many.economytycoon.futures.Position
import app.many.economytycoon.futures.PositionStatus
import app.many.economytycoon.futures.Side

/**
 * Cash-settled commodity futures. A company locks margin in its investments, the clearing house takes the
 * other side, and every day the position is valued at the going price. Losses beyond the margin are absorbed
 * by the clearing house, which is why it closes positions that get too close to that point.
 */
class FuturesRunner(private val e: Economy) {
    private var seq = 0L

    val clearing: LedgerOwner = LedgerOwner.bank(9003)

    // ---- prices --------------------------------------------------------------------------------

    /** Today's price of a good, falling back to its reference price before any trade has happened. */
    fun spot(good: GoodId): Money {
        val p = e.markets.averagePrice(good).perUnit
        return if (p.raw > 0L) p else e.content.good(good).referencePrice.perUnit
    }

    fun quote(good: GoodId, side: Side, contracts: Long, tenor: Int): FuturesQuote = e.futures.quote(spot(good), tenor, side, contracts)

    private fun post(key: String, tick: Tick, memo: String, entries: List<Entry>): Outcome<*> =
        e.finance.ledger.post(TransactionDraft(key, tick, ReasonCode.TRANSFER, entries, memo))

    private fun ensureClearing(tick: Tick) {
        if (e.futures.clearingFunded) return
        e.finance.mint(tick, clearing, e.futures.params.clearingReserve, "futures-clearing")
        e.futures.clearingFunded = true
    }

    // ---- trading -------------------------------------------------------------------------------

    fun open(company: CompanyId, good: GoodId, side: Side, contracts: Long, tenor: Int, tick: Tick): Outcome<Position> {
        val c = e.companies.company(company) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown company")
        if (!c.isActive) return Outcome.reject(Rules.WRONG_STATE, "company is not active")
        if (e.content.goodOrNull(good) == null) return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown good")
        e.futures.checkOpen(company, tenor, contracts)?.let { return Outcome.Rejected(it) }
        val q = quote(good, side, contracts, tenor)
        if (e.finance.ledger.cash(c.owner) < q.cashNeeded) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "needs ${q.cashNeeded} for margin and fee")
        ensureClearing(tick)
        val posted = post(
            "fut:open:${company.value}:${tick.value}:${seq++}", tick, "futures margin",
            listOf(
                Entry(AccountRef(c.owner, Chart.INVESTMENTS), q.margin), Entry(AccountRef(c.owner, Chart.CASH), -q.cashNeeded),
                Entry(AccountRef(c.owner, Chart.OTHER_EXPENSE), q.fee),
                Entry(AccountRef(clearing, Chart.CASH), q.cashNeeded), Entry(AccountRef(clearing, Chart.DEPOSITS), -q.margin),
                Entry(AccountRef(clearing, Chart.REVENUE_OTHER), -q.fee),
            ),
        )
        if (posted is Outcome.Rejected) return Outcome.Rejected(posted.rejection)
        return Outcome.ok(e.futures.open(company, good, side, contracts, q.price, q.margin, q.fee, tick, tenor))
    }

    private fun owned(company: CompanyId, id: FuturesId): Outcome<Position> {
        val p = e.futures.get(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown position")
        if (p.company != company) return Outcome.reject(Rules.NOT_OWNER, "that position belongs to another company")
        if (!p.isOpen) return Outcome.reject(Rules.WRONG_STATE, "the position is closed")
        return Outcome.ok(p)
    }

    /** Closes a position early at today's price for the days still to run, giving up half the spread. */
    fun close(company: CompanyId, id: FuturesId, tick: Tick): Outcome<Money> {
        val p = when (val r = owned(company, id)) { is Outcome.Ok -> r.value; is Outcome.Rejected -> return Outcome.Rejected(r.rejection) }
        val other = if (p.side == Side.LONG) Side.SHORT else Side.LONG
        val price = e.futures.entryPrice(spot(p.good), e.futures.daysLeft(p, tick), other)
        return settle(p, price, PositionStatus.CLOSED, tick)
    }

    fun addMargin(company: CompanyId, id: FuturesId, amount: Money, tick: Tick): Outcome<Money> {
        val p = when (val r = owned(company, id)) { is Outcome.Ok -> r.value; is Outcome.Rejected -> return Outcome.Rejected(r.rejection) }
        val c = e.companies.company(company) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown company")
        if (amount.raw <= 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "add a positive amount")
        if (e.finance.ledger.cash(c.owner) < amount) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "not enough cash")
        val posted = post(
            "fut:margin:${id.value}:${tick.value}:${seq++}", tick, "futures margin top-up",
            listOf(
                Entry(AccountRef(c.owner, Chart.INVESTMENTS), amount), Entry(AccountRef(c.owner, Chart.CASH), -amount),
                Entry(AccountRef(clearing, Chart.CASH), amount), Entry(AccountRef(clearing, Chart.DEPOSITS), -amount),
            ),
        )
        if (posted is Outcome.Rejected) return Outcome.Rejected(posted.rejection)
        return e.futures.addMargin(p, amount)
    }

    /** Pays out the margin and the result of a position at [price]; the clearing house covers any loss beyond the margin. */
    private fun settle(p: Position, price: Money, status: PositionStatus, tick: Tick): Outcome<Money> {
        val c = e.companies.company(p.company)
        val owner = c?.owner ?: LedgerOwner.company(p.company)
        ensureClearing(tick)
        val raw = e.futures.pnl(p, price)
        val result = if (raw.raw < -p.margin.raw) -p.margin else raw
        val margin = p.margin
        val payout = margin + result
        if (payout.raw > 0L) {
            val cash = e.finance.ledger.cash(clearing)
            if (cash < payout) e.finance.mint(tick, clearing, payout - cash, "futures:${p.id.value}:${tick.value}")
        }
        val entries = ArrayList<Entry>()
        entries += Entry(AccountRef(owner, Chart.INVESTMENTS), -margin)
        entries += Entry(AccountRef(owner, Chart.CASH), payout)
        entries += Entry(AccountRef(clearing, Chart.DEPOSITS), margin)
        entries += Entry(AccountRef(clearing, Chart.CASH), -payout)
        if (result.raw > 0L) {
            entries += Entry(AccountRef(owner, Chart.REVENUE_OTHER), -result)
            entries += Entry(AccountRef(clearing, Chart.OTHER_EXPENSE), result)
        } else if (result.raw < 0L) {
            entries += Entry(AccountRef(owner, Chart.OTHER_EXPENSE), -result)
            entries += Entry(AccountRef(clearing, Chart.REVENUE_OTHER), result)
        }
        val posted = post("fut:settle:${p.id.value}:${tick.value}", tick, "futures settlement", entries)
        if (posted is Outcome.Rejected) return Outcome.Rejected(posted.rejection)
        e.futures.finish(p, status, tick, result)
        return Outcome.ok(result)
    }

    // ---- the day -------------------------------------------------------------------------------

    fun run(tick: Tick): List<FuturesEvent> {
        val out = ArrayList<FuturesEvent>()
        for (p in e.futures.open()) {
            val left = e.futures.daysLeft(p, tick)
            val spot = spot(p.good)
            if (left == 0) {
                val r = settle(p, spot, PositionStatus.EXPIRED, tick)
                if (r is Outcome.Ok) out += FuturesEvent.Expired(p.id, p.company, r.value, tick)
                continue
            }
            e.futures.markTo(p, e.futures.futuresPrice(spot, left))
            when {
                e.futures.mustLiquidate(p) -> {
                    val r = settle(p, p.mark, PositionStatus.LIQUIDATED, tick)
                    if (r is Outcome.Ok) out += FuturesEvent.Liquidated(p.id, p.company, -r.value, tick)
                }
                e.futures.needsCall(p) -> if (e.futures.call(p, tick)) out += FuturesEvent.MarginCall(p.id, p.company, tick)
                else -> e.futures.clearCall(p)
            }
        }
        e.futures.prune(tick)
        return out
    }

    /** A retiring company's positions are closed at the going price so no margin is stranded. */
    fun forget(company: CompanyId, tick: Tick) {
        for (p in e.futures.openOf(company)) settle(p, p.mark, PositionStatus.CLOSED, tick)
    }

    /** What the company's open positions are worth if closed now: margin plus unrealised result. */
    fun worth(company: CompanyId): Money = e.futures.openOf(company).fold(Money.ZERO) { a, p -> a + e.futures.equity(p) }
}
