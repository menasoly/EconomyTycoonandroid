package app.many.economytycoon.simulation

import app.many.economytycoon.bonds.Bond
import app.many.economytycoon.bonds.BondEvent
import app.many.economytycoon.bonds.BondQuote
import app.many.economytycoon.bonds.DEALER
import app.many.economytycoon.core.BondId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
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

/**
 * Runs government borrowing: treasuries offer bonds, companies subscribe at face value, coupons are paid on a
 * fixed schedule and the face value is repaid at maturity. A dealer lets holders sell early and others buy.
 * Treasuries that run short are topped up by the central bank, as for all public spending.
 */
class BondRunner(private val e: Economy) {
    private var seq = 0L

    val dealer: LedgerOwner = LedgerOwner.bank(9002)

    private fun ownerOf(holder: CompanyId): LedgerOwner =
        if (holder == DEALER) dealer else (e.companies.company(holder)?.owner ?: LedgerOwner.company(holder))

    // ---- yields --------------------------------------------------------------------------------

    fun yieldFor(country: CountryId, tenorDays: Int): Long =
        e.bonds.marketYieldPpm(e.macro.policyRatePpm(country), e.world.country(country).stabilityPpm, tenorDays)

    fun currentYield(bond: Bond, tick: Tick): Long {
        val left = (bond.maturesAt.value - maxOf(tick.value, bond.subscriptionEnds.value)).toInt().coerceAtLeast(0)
        return yieldFor(bond.country, left)
    }

    fun pricePpm(bond: Bond, tick: Tick): Long = e.bonds.pricePpm(bond, tick, currentYield(bond, tick))

    /** The most the country will owe in bonds. */
    fun debtCeiling(country: CountryId): Money =
        Money(e.tenderRunner.dailyPublicSpending(country).raw * e.bonds.params.debtCeilingDays)

    // ---- the day -------------------------------------------------------------------------------

    fun run(tick: Tick): List<BondEvent> {
        val out = ArrayList<BondEvent>()
        out += issue(tick)
        out += e.bonds.closeSubscriptions(tick) { b -> b.faceTotal }
        out += payCoupons(tick)
        out += redeem(tick)
        e.bonds.prune()
        return out
    }

    private fun issue(tick: Tick): List<BondEvent> {
        val p = e.bonds.params
        val out = ArrayList<BondEvent>()
        for (country in e.world.countries().sortedBy { it.id }) {
            if ((tick.value + country.id.value) % p.issueEvery != 0L) continue
            if (e.bonds.open().any { it.country == country.id }) continue
            val random = e.random.stream("bonds", country.id.value)
            if (random.nextLong(1_000_000L) >= p.issueChancePpm) continue
            val room = (debtCeiling(country.id) - e.bonds.owed(country.id)).raw / p.faceValue.raw
            if (room < p.minUnits) continue
            val tenor = p.tenors[random.nextInt(p.tenors.size)]
            val top = minOf(p.maxUnits, room)
            val units = p.minUnits + random.nextLong(top - p.minUnits + 1)
            val coupon = e.bonds.couponFor(yieldFor(country.id, tenor))
            val made = e.bonds.issue(country.id, tenor, coupon, units, tick)
            if (made is Outcome.Ok) out += BondEvent.Issued(made.value.id, country.id, tick)
        }
        return out
    }

    // ---- subscribing and dealing ---------------------------------------------------------------

    fun subscribe(company: CompanyId, bond: BondId, units: Long, tick: Tick): Outcome<Money> {
        val b = e.bonds.get(bond) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown bond")
        val c = e.companies.company(company) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown company")
        if (!c.isActive) return Outcome.reject(Rules.WRONG_STATE, "company is not active")
        e.bonds.checkSubscription(b, company, units)?.let { return Outcome.Rejected(it) }
        val cost = Money(b.faceEach.raw * units)
        if (e.finance.ledger.cash(c.owner) < cost) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "not enough cash")
        val gov = LedgerOwner.government(b.country)
        val posted = post(
            "bondsub:${b.id.value}:${company.value}:${seq++}", tick, ReasonCode.TRANSFER, "bond subscription",
            listOf(
                Entry(AccountRef(c.owner, Chart.INVESTMENTS), cost), Entry(AccountRef(c.owner, Chart.CASH), -cost),
                Entry(AccountRef(gov, Chart.CASH), cost), Entry(AccountRef(gov, Chart.DEBT_LONG), -cost),
            ),
        )
        if (posted is Outcome.Rejected) return Outcome.Rejected(posted.rejection)
        e.bonds.allot(b, company, units, cost)
        e.bonds.addSold(b, units)
        return Outcome.ok(cost)
    }

    fun quote(bond: BondId, buy: Boolean, units: Long, tick: Tick): Outcome<BondQuote> {
        val b = e.bonds.get(bond) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown bond")
        return e.bonds.quote(b, buy, units, tick, currentYield(b, tick))
    }

    private fun ensureDealer(tick: Tick) {
        if (e.bonds.dealerFunded) return
        e.finance.mint(tick, dealer, e.bonds.params.dealerReserve, "bond-dealer")
        e.bonds.dealerFunded = true
    }

    /** A company buys ([buy] true) from the dealer's stock or sells to the dealer at the dealer's price. */
    fun trade(company: CompanyId, bond: BondId, units: Long, buy: Boolean, tick: Tick): Outcome<BondQuote> {
        val b = e.bonds.get(bond) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown bond")
        val c = e.companies.company(company) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown company")
        if (!c.isActive) return Outcome.reject(Rules.WRONG_STATE, "company is not active")
        val q = when (val r = quote(bond, buy, units, tick)) { is Outcome.Ok -> r.value; is Outcome.Rejected -> return Outcome.Rejected(r.rejection) }
        ensureDealer(tick)
        val total = q.total
        if (buy) {
            if (b.heldBy(DEALER) < units) return Outcome.reject(Rules.INSUFFICIENT_STOCK, "the dealer has only ${b.heldBy(DEALER)} units")
            if (e.finance.ledger.cash(c.owner) < total) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "not enough cash")
            if (b.heldBy(company) == 0L && b.holders.size >= e.bonds.params.maxHoldersPerBond) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many investors in this bond")
            val basis = b.costOf(DEALER).let { if (b.heldBy(DEALER) == units) it else Money(it.raw * units / b.heldBy(DEALER)) }
            val posted = post(
                "bondbuy:${b.id.value}:${company.value}:${seq++}", tick, ReasonCode.TRANSFER, "bond purchase",
                listOf(
                    Entry(AccountRef(c.owner, Chart.INVESTMENTS), total), Entry(AccountRef(c.owner, Chart.CASH), -total),
                    Entry(AccountRef(dealer, Chart.CASH), total), Entry(AccountRef(dealer, Chart.INVESTMENTS), -basis),
                ) + gainEntries(dealer, total - basis),
            )
            if (posted is Outcome.Rejected) return Outcome.Rejected(posted.rejection)
            e.bonds.take(b, DEALER, units)
            e.bonds.allot(b, company, units, total)
        } else {
            if (b.heldBy(company) < units) return Outcome.reject(Rules.INSUFFICIENT_STOCK, "you hold only ${b.heldBy(company)} units")
            if (b.heldBy(DEALER) + units > e.bonds.params.dealerMaxUnits) return Outcome.reject(Rules.LIMIT_EXCEEDED, "the dealer cannot take that many")
            if (e.finance.ledger.cash(dealer) < total) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "the dealer cannot pay for that many")
            val basis = b.costOf(company).let { if (b.heldBy(company) == units) it else Money(it.raw * units / b.heldBy(company)) }
            val posted = post(
                "bondsell:${b.id.value}:${company.value}:${seq++}", tick, ReasonCode.TRANSFER, "bond sale",
                listOf(
                    Entry(AccountRef(c.owner, Chart.CASH), total), Entry(AccountRef(c.owner, Chart.INVESTMENTS), -basis),
                    Entry(AccountRef(dealer, Chart.INVESTMENTS), total), Entry(AccountRef(dealer, Chart.CASH), -total),
                ) + gainEntries(c.owner, total - basis),
            )
            if (posted is Outcome.Rejected) return Outcome.Rejected(posted.rejection)
            e.bonds.take(b, company, units)
            e.bonds.allot(b, DEALER, units, total)
        }
        return Outcome.ok(q)
    }

    // ---- payments ------------------------------------------------------------------------------

    private fun gainEntries(owner: LedgerOwner, gain: Money): List<Entry> = when {
        gain.raw > 0L -> listOf(Entry(AccountRef(owner, Chart.REVENUE_OTHER), -gain))
        gain.raw < 0L -> listOf(Entry(AccountRef(owner, Chart.OTHER_EXPENSE), -gain))
        else -> emptyList()
    }

    private fun post(key: String, tick: Tick, reason: ReasonCode, memo: String, entries: List<Entry>): Outcome<*> =
        e.finance.ledger.post(TransactionDraft(key, tick, reason, entries, memo))

    private fun topUp(gov: LedgerOwner, need: Money, tick: Tick, key: String) {
        val cash = e.finance.ledger.cash(gov)
        if (cash < need) e.finance.mint(tick, gov, need - cash, key)
    }

    private fun payCoupon(b: Bond, tick: Tick): Money {
        val per = e.bonds.couponPerUnit(b)
        val gov = LedgerOwner.government(b.country)
        val total = Money(per.raw * b.unitsInCirculation())
        if (total.raw <= 0L) return Money.ZERO
        topUp(gov, total, tick, "bondcoupon:${b.id.value}:${tick.value}")
        var paid = Money.ZERO
        for (holder in b.holderIds()) {
            val amount = Money(per.raw * b.heldBy(holder))
            if (amount.raw <= 0L) continue
            val to = ownerOf(holder)
            val r = post(
                "bondcoupon:${b.id.value}:${tick.value}:${holder.value}", tick, ReasonCode.TRANSFER, "bond coupon",
                listOf(
                    Entry(AccountRef(gov, Chart.INTEREST_EXPENSE), amount), Entry(AccountRef(gov, Chart.CASH), -amount),
                    Entry(AccountRef(to, Chart.CASH), amount), Entry(AccountRef(to, Chart.INTEREST_INCOME), -amount),
                ),
            )
            if (r is Outcome.Ok) paid = paid + amount
        }
        e.bonds.addCoupons(b, paid)
        e.macro.recordSpending(b.country, paid)
        return paid
    }

    private fun payCoupons(tick: Tick): List<BondEvent> =
        e.bonds.couponDue(tick).mapNotNull { b ->
            val paid = payCoupon(b, tick)
            if (paid.raw > 0L) BondEvent.Coupon(b.id, b.country, paid, tick) else null
        }

    private fun redeem(tick: Tick): List<BondEvent> {
        val out = ArrayList<BondEvent>()
        for (b in e.bonds.maturing(tick)) {
            val last = payCoupon(b, tick)
            if (last.raw > 0L) out += BondEvent.Coupon(b.id, b.country, last, tick)
            val gov = LedgerOwner.government(b.country)
            val owed = b.faceTotal
            topUp(gov, owed, tick, "bondredeem:${b.id.value}")
            var repaid = Money.ZERO
            for (holder in b.holderIds()) {
                val units = b.heldBy(holder)
                val face = Money(b.faceEach.raw * units)
                val cost = b.costOf(holder)
                val to = ownerOf(holder)
                val r = post(
                    "bondredeem:${b.id.value}:${holder.value}", tick, ReasonCode.TRANSFER, "bond repayment",
                    listOf(
                        Entry(AccountRef(gov, Chart.DEBT_LONG), face), Entry(AccountRef(gov, Chart.CASH), -face),
                        Entry(AccountRef(to, Chart.CASH), face), Entry(AccountRef(to, Chart.INVESTMENTS), -cost),
                    ) + gainEntries(to, face - cost),
                )
                if (r is Outcome.Ok) repaid = repaid + face
            }
            b.holders.clear()
            e.bonds.markRedeemed(b, tick)
            out += BondEvent.Redeemed(b.id, b.country, repaid, tick)
        }
        return out
    }

    /** A company that has ceased to exist hands its bonds to the dealer. */
    fun forget(company: CompanyId) { e.bonds.transferAll(company) }

    /** Face value and cost of everything a company holds, and what the dealer would pay for it today. */
    fun worth(company: CompanyId, tick: Tick): Money {
        var sum = Money.ZERO
        for (b in e.bonds.heldBy(company)) {
            val per = e.bonds.unitPrice(b, pricePpm(b, tick) - e.bonds.params.dealerSpreadPpm)
            sum = sum + Money(per.raw * b.heldBy(company))
        }
        return sum
    }
}
