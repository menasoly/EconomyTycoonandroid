package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** The thresholds a computer-controlled company uses to decide between cutting emissions and paying for them. */
data class AiCarbonTuning(
    /** A measure is taken if its cost per tonne is below this share of the price expected over its life. */
    val hurdlePpm: Long = 900_000L,
    /** A measure must pay for itself within this many years at today's price. */
    val maxPaybackYears: Int = 9,
    /** Share of cash never committed to carbon spending. */
    val cashReservePpm: Long = 300_000L,
    /** The most of the spendable cash a single measure may take. */
    val projectSharePpm: Long = 350_000L,
    /** The company aims to hold this share of its projected emissions in permits by the deadline. */
    val coverTargetPpm: Long = 1_050_000L,
    /** How far above the going price a bid or buy order is placed. */
    val bidMarginPpm: Long = 80_000L,
    /** Permits beyond this share of the target are offered for sale. */
    val surplusPpm: Long = 1_300_000L,
    /** Credits are bought instead of permits when they cost less than this share of the permit price. */
    val offsetPreferencePpm: Long = 850_000L,
    val minAction: Tonnes = Tonnes.of(50),
)

/**
 * A computer-controlled company's carbon decisions. It looks at its position, abates where the marginal abatement cost
 * is below the price it expects to pay, covers the remainder with permits bought at auction or on the secondary market,
 * sells permits it does not need, and falls back on credits when they are the cheaper way to meet a deadline. It returns
 * commands; the caller runs them through the same checks as a player's.
 */
class CarbonAi(private val service: CarbonService, private val views: CarbonViews, val tuning: AiCarbonTuning = AiCarbonTuning()) {

    private fun share(m: Money, ppm: Long) = Money(mulDiv(m.raw, ppm, Rate.ONE_PPM, RoundingMode.HALF_EVEN))

    fun decide(company: CompanyId, cash: Money, tick: Tick, rng: DeterministicRandom): List<CarbonCommand> {
        val view = views.company(company, tick)
        if (view.accounts.isEmpty()) return emptyList()
        val out = ArrayList<CarbonCommand>()
        var spendable = share(cash, Rate.ONE_PPM - tuning.cashReservePpm)
        for (a in view.accounts) {
            val regime = service.state.regime(a.country) ?: continue
            if (!regime.isRegulated) continue
            policy(a, regime, company)?.let { out += it }
            spendable -= abate(view, a, regime, company, spendable, out)
            if (regime.tradesPermits) spendable -= permits(view, a, regime, company, spendable, tick, rng, out)
        }
        return out
    }

    /** Keep credits switched on so a deadline never costs a fine for want of a click; borrow only when it looks short. */
    private fun policy(a: AccountView, regime: CarbonRegime, company: CompanyId): CarbonCommand? {
        val borrow = regime.borrowLimitPpm > 0L && (a.pending?.projectedShortfall?.isPositive == true)
        val wanted = CompliancePolicy(autoOffsets = true, autoBorrow = borrow)
        return if (wanted == a.policy) null else CarbonCommand.SetPolicy(company, a.country, wanted.autoOffsets, wanted.autoBorrow)
    }

    /** Orders the best measures that clear the hurdle; returns the money committed. */
    private fun abate(view: CompanyCarbonView, a: AccountView, regime: CarbonRegime, company: CompanyId, spendable: Money, out: MutableList<CarbonCommand>): Money {
        if (!a.projectedCovered.isPositive) return Money.ZERO
        val record = service.state.inventory
        val expected = regime.expectedPrice(a.year, a.price, service.params.aiHorizonYears)
        var committed = Money.ZERO
        val limit = share(spendable, tuning.projectSharePpm)
        val live = view.abatement.filter { it.status == AbatementStatus.BUILDING || it.status == AbatementStatus.OPERATING }
        val busy = HashSet<Long>()
        for (option in view.options) {
            val f = record.facility(option.facility) ?: continue
            if (f.country != a.country || option.facility.value in busy) continue
            if (live.any { it.facility == option.facility && it.kind == option.kind }) continue
            if (option.capex > limit || committed + option.capex > spendable) continue
            if (option.costPerTonne >= share(expected, tuning.hurdlePpm)) continue
            val payback = option.paybackYears(a.price) ?: continue
            if (payback > tuning.maxPaybackYears) continue
            out += CarbonCommand.StartAbatement(company, option.facility, option.kind)
            committed += option.capex
            busy += option.facility.value
        }
        return committed
    }

    /** Buys, bids and sells permits; returns the most the orders could cost. */
    private fun permits(
        view: CompanyCarbonView, a: AccountView, regime: CarbonRegime, company: CompanyId, spendable: Money, tick: Tick, rng: DeterministicRandom,
        out: MutableList<CarbonCommand>,
    ): Money {
        val minTrade = service.params.minTrade
        val market = service.state.market(a.country) ?: return Money.ZERO
        val price = a.price
        val bidPrice = share(price, Rate.ONE_PPM + tuning.bidMarginPpm + rng.nextInRange(0L, tuning.bidMarginPpm / 2L))
        val pending = a.pending
        if (pending != null) {
            val missing = (pending.outstanding - pending.usablePermits).coerceAtLeast(Tonnes.ZERO)
            if (missing < tuning.minAction) return Money.ZERO
            val spot = OffsetKind.values().filter { it in regime.eligibleOffsets }.minByOrNull { service.state.offsets.exchange.spot(it) }
            val allowance = Tonnes.min(pending.offsetAllowance - pending.offsetsHeld, missing).coerceAtLeast(Tonnes.ZERO)
            if (spot != null && allowance >= minTrade && service.state.offsets.exchange.spot(spot) < share(price, tuning.offsetPreferencePpm)) {
                val quote = service.state.offsets.exchange.quote(spot, allowance)
                if (quote != null && quote.total <= spendable) {
                    out += CarbonCommand.BuyOffsets(company, spot, allowance)
                    return quote.total
                }
            }
            val penalty = regime.stepFor(pending.year)?.penalty
            val limit = if (penalty != null && penalty.isPositive) Money.min(bidPrice, penalty) else bidPrice
            val affordable = Tonnes(mulDiv(spendable.raw, Tonnes.SCALE, limit.raw.coerceAtLeast(1L)))
            val qty = Tonnes.min(missing, affordable)
            if (qty < minTrade) return Money.ZERO
            out += CarbonCommand.PlaceOrder(company, a.country, PermitSide.BUY, qty, limit)
            return qty.cost(limit)
        }
        val need = a.projectedCovered.times(Rate(tuning.coverTargetPpm)) + a.carry
        val gap = need - a.permitsHeld
        val year = market.supply(a.year)
        val auctionsLeft = (AUCTIONS_PER_YEAR - (year?.auctionsHeld ?: AUCTIONS_PER_YEAR)).coerceAtLeast(0)
        if (gap >= tuning.minAction && auctionsLeft > 0) {
            val qty = Tonnes(gap.raw / auctionsLeft)
            val affordable = Tonnes(mulDiv(spendable.raw, Tonnes.SCALE, bidPrice.raw.coerceAtLeast(1L)))
            val bid = Tonnes.min(qty, affordable)
            if (bid >= minTrade && market.bidsOf(company).isEmpty()) {
                out += CarbonCommand.BidAtAuction(company, a.country, bid, bidPrice)
                return bid.cost(bidPrice)
            }
        } else if (a.permitsHeld > need.times(Rate(tuning.surplusPpm)) && !a.permitsHeld.isZero) {
            val excess = a.permitsHeld - need.times(Rate(tuning.surplusPpm))
            val offer = Tonnes.min(excess, a.permitsHeld - market.reservedForSale(company))
            if (offer >= tuning.minAction && market.ordersOf(company).none { it.side == PermitSide.SELL }) {
                out += CarbonCommand.PlaceOrder(company, a.country, PermitSide.SELL, offer, share(price, Rate.ONE_PPM - tuning.bidMarginPpm / 2L))
            }
        }
        return Money.ZERO
    }
}
