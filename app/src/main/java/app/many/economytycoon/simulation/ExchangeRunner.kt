package app.many.economytycoon.simulation

import app.many.economytycoon.companies.Company
import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.exchange.ExchangeEvent
import app.many.economytycoon.exchange.MarketFacts
import app.many.economytycoon.exchange.PublicInvestors
import app.many.economytycoon.exchange.TradeQuote
import app.many.economytycoon.exchange.TradeSide
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.finance.Holder
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.TransactionDraft

/** What came of floating a company. */
data class ListingResult(val shares: Long, val price: Money, val raised: Money, val fee: Money)

/** One line of an investor's portfolio. */
data class HoldingValue(val issuer: CompanyId, val shares: Long, val price: Money, val value: Money, val cost: Money) {
    val gain: Money get() = value - cost
}

/**
 * Does the money and share movements behind the exchange: flotations, trades between companies and the
 * public, and the end-of-day price round. Every posting balances, and shares only move together with cash.
 */
class ExchangeRunner(private val e: Economy) : MarketFacts {
    private val common = "common"
    private val public = PublicInvestors.holder
    private var seq = 0L
    private val pending = ArrayList<ExchangeEvent>()

    // ---- facts for pricing ---------------------------------------------------------------------

    override fun fairPrice(issuer: CompanyId, tick: Tick): Money = e.acquisitions.fairPricePerShare(issuer, tick)

    override fun publicShares(issuer: CompanyId): Long {
        val c = e.companies.company(issuer) ?: return 0L
        return e.finance.equity.table(c.owner)?.held(public, common) ?: 0L
    }

    override fun outstanding(issuer: CompanyId): Long {
        val c = e.companies.company(issuer) ?: return 0L
        return e.finance.equity.table(c.owner)?.totalOutstanding() ?: 0L
    }

    override fun sentimentPpm(issuer: CompanyId): Long {
        val c = e.companies.company(issuer) ?: return 1_000_000L
        return e.macro.demandShiftPpm(c.country)
    }

    // ---- the day -------------------------------------------------------------------------------

    fun closeDay(tick: Tick): List<ExchangeEvent> {
        val events = ArrayList<ExchangeEvent>()
        for (l in e.exchange.listings()) {
            val c = e.companies.company(l.issuer)
            if (c == null || c.status != CompanyStatus.ACTIVE) {
                e.exchange.delist(l.issuer)
                events += ExchangeEvent.Delisted(l.issuer, tick)
            }
        }
        events += e.exchange.closeDay(tick, this, e.random.stream("exchange"))
        val out = ArrayList<ExchangeEvent>(pending)
        pending.clear()
        out += events
        return out
    }

    /** A company that ceases to exist leaves the exchange, and whatever it held goes with it. */
    fun forget(company: CompanyId) {
        if (e.exchange.delist(company)) pending += ExchangeEvent.Delisted(company, e.tick)
        val holder = Holder.owner(LedgerOwner.company(company))
        for ((issuer, _) in e.exchange.positionsOf(holder)) e.exchange.releaseBasis(holder, issuer, 1L, 1L)
    }

    // ---- floating ------------------------------------------------------------------------------

    fun listingProblem(c: Company, tick: Tick): String? {
        val p = e.exchange.params
        if (c.kind == CompanyKind.STATE) return "state enterprises are not listed"
        if (c.status != CompanyStatus.ACTIVE) return "${c.name} is not trading"
        if (e.exchange.isListed(c.id)) return "${c.name} is already listed"
        if (tick.value - c.founded.value < p.minAgeTicks) return "a company must have traded for ${p.minAgeTicks} days before it lists"
        val worth = e.finance.statements.balance(c.owner, tick).netWorth
        if (worth < p.minNetWorth) return "listing needs a net worth of at least ${p.minNetWorth}"
        if (e.finance.equity.table(c.owner) == null) return "the company has no share register"
        return null
    }

    /** What floating [floatPpm] of the company would sell and raise, without doing it. Null when the company is unknown or has no register. */
    fun previewListing(company: CompanyId, floatPpm: Long, tick: Tick): ListingResult? {
        val c = e.companies.company(company) ?: return null
        val table = e.finance.equity.table(c.owner) ?: return null
        val p = e.exchange.params
        val f = floatPpm.coerceIn(p.minFloatPpm, p.maxFloatPpm)
        val newShares = (table.totalOutstanding() * f / (1_000_000L - f)).coerceAtLeast(1L)
        val price = Money(mulDiv(e.acquisitions.fairPricePerShare(company, tick).raw, 1_000_000L - p.ipoDiscountPpm, 1_000_000L))
        val raised = price * newShares
        return ListingResult(newShares, price, raised, Money(mulDiv(raised.raw, FLOTATION_FEE_PPM, 1_000_000L)))
    }

    fun list(company: CompanyId, floatPpm: Long, tick: Tick): Outcome<ListingResult> {
        val c = e.companies.company(company) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown company")
        val p = e.exchange.params
        listingProblem(c, tick)?.let { return Outcome.reject(Rules.REGULATION, it) }
        if (floatPpm !in p.minFloatPpm..p.maxFloatPpm) return Outcome.reject(Rules.INVALID_ARGUMENT, "sell between ${p.minFloatPpm / 10_000}% and ${p.maxFloatPpm / 10_000}% of the company")
        val table = e.finance.equity.table(c.owner)!!
        val existing = table.totalOutstanding()
        val newShares = (existing * floatPpm / (1_000_000L - floatPpm)).coerceAtLeast(1L)
        val fair = e.acquisitions.fairPricePerShare(company, tick)
        val price = Money(mulDiv(fair.raw, 1_000_000L - p.ipoDiscountPpm, 1_000_000L))
        if (price < p.minPrice) return Outcome.reject(Rules.INVALID_PRICE, "the company is not worth enough per share to list")
        val raised = price * newShares
        val capacity = Money(mulDiv(e.finance.ledger.cash(e.finance.households).raw.coerceAtLeast(0L), p.publicCapacityPpm, 1_000_000L))
        if (raised > capacity) return Outcome.reject(Rules.LIMIT_EXCEEDED, "the market cannot absorb that much stock; sell a smaller share")
        val issued = e.finance.equity.issue(c.owner, common, public, e.finance.households, newShares, price, tick)
        if (issued is Outcome.Rejected) return Outcome.Rejected(issued.rejection)
        e.exchange.list(company, tick, price, fair)
        pending += ExchangeEvent.Listed(company, tick, price, newShares)
        e.exchange.addBasis(public, company, raised)
        // Bankers' fee, taken from the proceeds when the company can afford it.
        val fee = Money(mulDiv(raised.raw, FLOTATION_FEE_PPM, 1_000_000L))
        val paid = if (e.payer.charge(company, fee, "flotation fee")) fee else Money.ZERO
        e.companies.groups.rebuild(e.finance.equity, e.companies.all())
        return Outcome.ok(ListingResult(newShares, price, raised, paid))
    }

    /** Buys every public share back at a small premium and takes the company off the exchange. */
    fun delist(company: CompanyId, tick: Tick): Outcome<Money> {
        val c = e.companies.company(company) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown company")
        val l = e.exchange.listing(company) ?: return Outcome.reject(Rules.WRONG_STATE, "the company is not listed")
        val shares = publicShares(company)
        val price = Money(mulDiv(l.price.raw, 1_000_000L + DELIST_PREMIUM_PPM, 1_000_000L))
        if (shares > 0L) {
            val cost = price * shares
            if (e.finance.ledger.cash(c.owner) < cost) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "buying out the public would cost $cost")
            val basis = e.exchange.releaseBasis(public, company, shares, shares)
            val r = e.finance.equity.buyBack(c.owner, common, public, e.finance.households, shares, price, tick)
            if (r is Outcome.Rejected) { e.exchange.addBasis(public, company, basis); return Outcome.Rejected(r.rejection) }
        }
        e.exchange.delist(company)
        pending += ExchangeEvent.Delisted(company, tick)
        return Outcome.ok(price * shares)
    }

    /** The company buys back some of its public shares at the market price, shrinking the float. */
    fun buyBack(company: CompanyId, shares: Long, tick: Tick): Outcome<TradeQuote> {
        val c = e.companies.company(company) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown company")
        val held = publicShares(company)
        if (!e.exchange.isListed(company)) return Outcome.reject(Rules.WRONG_STATE, "the company is not listed")
        if (shares <= 0L) return Outcome.reject(Rules.INVALID_QUANTITY, "buy back at least one share")
        if (shares > held) return Outcome.reject(Rules.INSUFFICIENT_STOCK, "the public holds only $held shares")
        val quote = (e.exchange.quote(company, TradeSide.BUY, shares, held) as? Outcome.Ok)?.value ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "not listed")
        val total = quote.gross
        if (e.finance.ledger.cash(c.owner) < total) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "not enough cash to buy back")
        val basis = e.exchange.releaseBasis(public, company, shares, held)
        val r = e.finance.equity.buyBack(c.owner, common, public, e.finance.households, shares, quote.averagePrice, tick)
        if (r is Outcome.Rejected) { e.exchange.addBasis(public, company, basis); return Outcome.Rejected(r.rejection) }
        e.exchange.recordTrade(quote, held)
        return Outcome.ok(quote)
    }

    // ---- trading -------------------------------------------------------------------------------

    fun holdingOf(investor: CompanyId, issuer: CompanyId): Long {
        val c = e.companies.company(investor) ?: return 0L
        val issuerCompany = e.companies.company(issuer) ?: return 0L
        return e.finance.equity.table(issuerCompany.owner)?.held(c.holder, common) ?: 0L
    }

    fun buy(investor: CompanyId, issuer: CompanyId, shares: Long, tick: Tick): Outcome<TradeQuote> {
        val inv = e.companies.company(investor) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown company")
        val target = e.companies.company(issuer) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown company")
        if (investor == issuer) return Outcome.reject(Rules.INVALID_ARGUMENT, "a company cannot buy its own shares this way; use a buyback")
        if (inv.kind == CompanyKind.STATE) return Outcome.reject(Rules.REGULATION, "state enterprises do not trade shares")
        val table = e.finance.equity.table(target.owner) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no share register")
        val publicHeld = table.held(public, common)
        val quote = when (val q = e.exchange.quote(issuer, TradeSide.BUY, shares, publicHeld)) {
            is Outcome.Ok -> q.value
            is Outcome.Rejected -> return Outcome.Rejected(q.rejection)
        }
        val after = table.held(inv.holder, common) + shares
        if (after * 1_000_000L / table.totalOutstanding().coerceAtLeast(1L) > MAX_PORTFOLIO_STAKE_PPM) {
            return Outcome.reject(Rules.REGULATION, "a stake above 49% needs a takeover offer")
        }
        if (e.finance.ledger.cash(inv.owner) < quote.net) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "not enough cash: the trade costs ${quote.net}")

        val gov = e.governmentOfCompany(investor)
        val basis = e.exchange.releaseBasis(public, issuer, shares, publicHeld)
        val gain = quote.gross - basis
        val entries = ArrayList<Entry>()
        entries += Entry(AccountRef(inv.owner, Chart.INVESTMENTS), quote.gross)
        entries += Entry(AccountRef(inv.owner, Chart.OTHER_EXPENSE), quote.fee + quote.duty)
        entries += Entry(AccountRef(inv.owner, Chart.CASH), -quote.net)
        val hh = e.finance.households
        entries += Entry(AccountRef(hh, Chart.CASH), quote.gross + quote.fee)
        entries += Entry(AccountRef(hh, Chart.INVESTMENTS), -basis)
        entries += Entry(AccountRef(hh, Chart.REVENUE_OTHER), -quote.fee)
        entries += if (gain.raw >= 0L) Entry(AccountRef(hh, Chart.REVENUE_OTHER), -gain) else Entry(AccountRef(hh, Chart.OTHER_EXPENSE), -gain)
        if (quote.duty.raw > 0L) {
            entries += Entry(AccountRef(gov, Chart.CASH), quote.duty)
            entries += Entry(AccountRef(gov, Chart.REVENUE_OTHER), -quote.duty)
        }
        val posted = post("share:buy:${investor.value}:${issuer.value}:${tick.value}:${seq++}", tick, entries, "share purchase")
        if (posted is Outcome.Rejected) { e.exchange.addBasis(public, issuer, basis); return Outcome.Rejected(posted.rejection) }
        e.finance.equity.transfer(target.owner, common, public, inv.holder, shares)
        e.exchange.addBasis(inv.holder, issuer, quote.gross)
        e.exchange.recordTrade(quote, publicHeld)
        if (quote.duty.raw > 0L) e.macro.recordRevenue(e.companies.require(investor).country, quote.duty)
        e.companies.groups.rebuild(e.finance.equity, e.companies.all())
        return Outcome.ok(quote)
    }

    fun sell(investor: CompanyId, issuer: CompanyId, shares: Long, tick: Tick): Outcome<TradeQuote> {
        val inv = e.companies.company(investor) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown company")
        val target = e.companies.company(issuer) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown company")
        val table = e.finance.equity.table(target.owner) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no share register")
        val held = table.held(inv.holder, common)
        if (shares > held) return Outcome.reject(Rules.INSUFFICIENT_STOCK, "you hold only $held shares")
        val publicHeld = table.held(public, common)
        val quote = when (val q = e.exchange.quote(issuer, TradeSide.SELL, shares, publicHeld)) {
            is Outcome.Ok -> q.value
            is Outcome.Rejected -> return Outcome.Rejected(q.rejection)
        }
        val hh = e.finance.households
        if (e.finance.ledger.cash(hh) < quote.net) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "the market cannot pay for that many shares today")

        val basis = e.exchange.releaseBasis(inv.holder, issuer, shares, held)
        val gain = quote.gross - basis
        val entries = ArrayList<Entry>()
        entries += Entry(AccountRef(inv.owner, Chart.CASH), quote.net)
        entries += Entry(AccountRef(inv.owner, Chart.OTHER_EXPENSE), quote.fee)
        entries += Entry(AccountRef(inv.owner, Chart.INVESTMENTS), -basis)
        entries += if (gain.raw >= 0L) Entry(AccountRef(inv.owner, Chart.REVENUE_OTHER), -gain) else Entry(AccountRef(inv.owner, Chart.OTHER_EXPENSE), -gain)
        entries += Entry(AccountRef(hh, Chart.INVESTMENTS), quote.gross)
        entries += Entry(AccountRef(hh, Chart.CASH), -quote.net)
        entries += Entry(AccountRef(hh, Chart.REVENUE_OTHER), -quote.fee)
        val posted = post("share:sell:${investor.value}:${issuer.value}:${tick.value}:${seq++}", tick, entries, "share sale")
        if (posted is Outcome.Rejected) {
            if (basis.raw > 0L) e.exchange.addBasis(inv.holder, issuer, basis)
            return Outcome.Rejected(posted.rejection)
        }
        e.finance.equity.transfer(target.owner, common, inv.holder, public, shares)
        e.exchange.addBasis(public, issuer, quote.gross)
        e.exchange.recordTrade(quote, publicHeld)
        e.companies.groups.rebuild(e.finance.equity, e.companies.all())
        return Outcome.ok(quote)
    }

    private fun post(key: String, tick: Tick, entries: List<Entry>, memo: String): Outcome<*> {
        val merged = java.util.TreeMap<AccountRef, Long>()
        for (en in entries) merged[en.account] = (merged[en.account] ?: 0L) + en.amount.raw
        return e.finance.ledger.post(TransactionDraft(key, tick, ReasonCode.TRANSFER, merged.filter { it.value != 0L }.map { Entry(it.key, Money(it.value)) }, memo))
    }

    // ---- views ---------------------------------------------------------------------------------

    fun portfolio(investor: CompanyId): List<HoldingValue> {
        val c = e.companies.company(investor) ?: return emptyList()
        val out = ArrayList<HoldingValue>()
        for (l in e.exchange.listings()) {
            val shares = holdingOf(investor, l.issuer)
            if (shares <= 0L) continue
            out += HoldingValue(l.issuer, shares, l.price, l.price * shares, e.exchange.basisOf(c.holder, l.issuer))
        }
        return out
    }

    fun marketCap(issuer: CompanyId): Money {
        val l = e.exchange.listing(issuer) ?: return Money.ZERO
        return l.price * outstanding(issuer)
    }

    companion object {
        const val FLOTATION_FEE_PPM = 20_000L
        const val DELIST_PREMIUM_PPM = 50_000L
        const val MAX_PORTFOLIO_STAKE_PPM = 490_000L
    }
}
