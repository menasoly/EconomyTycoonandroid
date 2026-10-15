package app.many.economytycoon.simulation

import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.content.GoodDef
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.TenderId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.TransactionDraft
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.tenders.Bid
import app.many.economytycoon.tenders.Tender
import app.many.economytycoon.tenders.TenderEvent

/**
 * Runs the public tender cycle: governments post calls for goods, bidding closes, the cheapest eligible bid
 * wins, the winner delivers from its facility and is paid from the treasury, or pays a penalty if it fails.
 */
class TenderRunner(private val e: Economy) {
    private var seq = 0L

    private val wanted = setOf(GoodCategory.RAW, GoodCategory.INTERMEDIATE, GoodCategory.CONSUMER, GoodCategory.INDUSTRIAL)

    fun candidateGoods(): List<GoodDef> =
        e.content.allGoods.filter { it.category in wanted && it.tradable && it.storable && it.referencePrice.perUnit.raw > 0L }

    fun reference(t: Tender): Money = e.content.goodOrNull(t.good)?.referencePrice?.perUnit ?: t.ceiling
    fun floorFor(t: Tender): Money = Money(mulDiv(reference(t).raw, e.tenders.params.floorPpm, 1_000_000L))

    /** What the government spends on its people in a day; tenders are paid out of the same pot. */
    fun dailyPublicSpending(country: CountryId): Money {
        val cities = e.world.citiesOf(country)
        val unemployed = cities.sumOf { e.world.population(it.id).unemployed }
        val people = cities.sumOf { e.world.population(it.id).total }
        return Money(e.config.benefitPerTick.raw * unemployed + e.config.publicSpendingPerCapita.raw * people)
    }

    fun budgetFor(country: CountryId): Money = dailyPublicSpending(country) * e.tenders.params.budgetDays.toLong()

    // ---- bidding -------------------------------------------------------------------------------

    fun bid(company: CompanyId, tender: TenderId, facility: FacilityId, unitPrice: Money, tick: Tick): Outcome<Bid> {
        val t = e.tenders.get(tender) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown tender")
        val f = e.production.facility(facility)
        if (f == null || f.owner != company) return Outcome.reject(Rules.NOT_OWNER, "not your facility")
        if (f.status == FacilityStatus.DEMOLISHED) return Outcome.reject(Rules.WRONG_STATE, "that facility has been demolished")
        return e.tenders.bid(tender, company, facility, unitPrice, floorFor(t), tick)
    }

    fun withdraw(company: CompanyId, tender: TenderId): Outcome<Unit> = e.tenders.withdraw(tender, company)

    // ---- the day -------------------------------------------------------------------------------

    fun run(tick: Tick): List<TenderEvent> {
        val events = ArrayList<TenderEvent>()
        events += issue(tick)
        events += e.tenders.closeBidding(tick) { eligible(it, tick) }
        events += deliver(tick)
        e.tenders.prune(tick)
        return events
    }

    private fun eligible(b: Bid, tick: Tick): Boolean {
        val c = e.companies.company(b.company) ?: return false
        if (!c.isActive || e.tenders.isBarred(b.company, tick)) return false
        val f = e.production.facility(b.facility) ?: return false
        return f.owner == b.company && f.status != FacilityStatus.DEMOLISHED
    }

    private fun issue(tick: Tick): List<TenderEvent> {
        val p = e.tenders.params
        val out = ArrayList<TenderEvent>()
        val goods = candidateGoods()
        if (goods.isEmpty()) return out
        for (country in e.world.countries().sortedBy { it.id }) {
            if ((tick.value + country.id.value) % p.issueEvery != 0L) continue
            val random = e.random.stream("tenders", country.id.value)
            if (random.nextLong(1_000_000L) >= p.issueChancePpm) continue
            val good = goods[random.nextInt(goods.size)]
            val ref = good.referencePrice.perUnit
            val targetUnits = p.minValueUnits + random.nextLong(p.maxValueUnits - p.minValueUnits + 1)
            val qty = Quantity.units((targetUnits * Money.SCALE / ref.raw).coerceAtLeast(1L))
            val ceiling = Money(mulDiv(ref.raw, p.ceilingPpm, 1_000_000L))
            val value = Money(mulDiv(ceiling.raw, qty.raw, Quantity.SCALE))
            if (e.tenders.committed(country.id) + value > budgetFor(country.id)) continue
            val issued = e.tenders.issue(country.id, good.id, qty, ceiling, tick)
            if (issued is Outcome.Ok) out += TenderEvent.Issued(issued.value.id, country.id, good.id, tick)
        }
        return out
    }

    private fun deliver(tick: Tick): List<TenderEvent> {
        val out = ArrayList<TenderEvent>()
        for (t in e.tenders.awarded()) {
            val winner = t.winner!!
            val f = t.winnerFacility?.let { e.production.facility(it) }
            val usable = f != null && f.owner == winner && f.status != FacilityStatus.DEMOLISHED
            if (usable && f!!.inventory.available(t.good) >= t.quantity) {
                val value = t.awardedValue
                val gov = LedgerOwner.government(t.country)
                val cost = (f.inventory.remove(t.good, t.quantity) as? Outcome.Ok)?.value
                if (cost != null) {
                    // The central bank tops the treasury up when a purchase outruns it, as it does for public spending.
                    val cash = e.finance.ledger.cash(gov)
                    if (cash < value) e.finance.mint(tick, gov, value - cash, "tender:${t.id.value}")
                    val booked = e.finance.recordSale("tender:${t.id.value}", tick, LedgerOwner.company(winner), gov, value, cost, buyerChart = Chart.OTHER_EXPENSE)
                    if (booked is Outcome.Ok) {
                        e.macro.record(t.country, app.many.economytycoon.macro.GdpKind.GOVERNMENT, value)
                        out += e.tenders.delivered(t, tick, value)
                        continue
                    }
                    f.inventory.add(t.good, t.quantity, cost, allowOverflow = true)
                }
            }
            if (tick >= t.deliverBy) {
                val fine = penalise(t, winner, tick)
                out += e.tenders.failed(t, tick, fine)
            }
        }
        return out
    }

    /** Takes the penalty for a missed delivery, as much as the company can pay. */
    private fun penalise(t: Tender, from: CompanyId, tick: Tick): Money {
        val due = t.awardedValue.scale(e.tenders.params.penaltyPpm, 1_000_000L)
        val payer = LedgerOwner.company(from)
        val pay = Money.min(due, e.finance.ledger.cash(payer).coerceAtLeast(Money.ZERO))
        if (pay.raw <= 0L) return Money.ZERO
        val gov = LedgerOwner.government(t.country)
        val posted = e.finance.ledger.post(
            TransactionDraft(
                "tenderfee:${t.id.value}:${seq++}", tick, ReasonCode.FEE,
                listOf(
                    Entry(AccountRef(payer, Chart.OTHER_EXPENSE), pay), Entry(AccountRef(payer, Chart.CASH), -pay),
                    Entry(AccountRef(gov, Chart.CASH), pay), Entry(AccountRef(gov, Chart.REVENUE_OTHER), -pay),
                ),
                "tender penalty",
            ),
        )
        return if (posted is Outcome.Ok) pay else Money.ZERO
    }

    fun forget(company: CompanyId, tick: Tick) { e.tenders.endAllOf(company, tick) }
}
