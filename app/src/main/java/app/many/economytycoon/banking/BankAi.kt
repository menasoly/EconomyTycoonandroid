package app.many.economytycoon.banking

import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditRating

/**
 * The management of a computer-controlled bank. Each round it reads its own books and the market through a [BankView]
 * and proposes [BankAction]s, which go through the same checks as a player's. The rules are a handful of plain
 * heuristics ordered by urgency: protect capital, protect liquidity, then compete for deposits and loans.
 */
class BankAiPlanner(private val params: BankingParams) {

    /** Premium a bank of this temperament pays on a product when it feels no pressure, in basis points. */
    fun restingPremium(strategy: BankStrategy, product: DepositProduct): Long {
        val base = (strategy.depositAggression.ppm - 500_000L) / 2_500L
        return when (product) {
            DepositProduct.DEMAND -> base / 4L
            DepositProduct.SAVINGS -> base
            DepositProduct.TERM -> base / 2L
        }
    }

    fun isDue(bank: BankSnapshot, strategy: BankStrategy, tick: Long, offset: Long): Boolean {
        val period = strategy.thinkMonths.toLong() * BankMath.MONTH_TICKS
        return period > 0L && (tick + offset) % period == 0L
    }

    fun plan(view: BankView, strategy: BankStrategy, random: DeterministicRandom): List<BankAction> {
        val b = view.bank
        if (!b.isOperating) return emptyList()
        val out = ArrayList<BankAction>()
        out += capitalActions(view, strategy)
        out += liquidityActions(view, strategy)
        out += depositActions(view, strategy, random)
        out += lendingActions(view, strategy)
        out += balanceSheetActions(view, strategy)
        return out
    }

    private fun capitalActions(v: BankView, st: BankStrategy): List<BankAction> {
        val b = v.bank
        val out = ArrayList<BankAction>()
        val short = v.cushionHeadroom
        if (short.ppm < 0L) {
            // Short of the cushion it wants: keep profits, slow down, lend more carefully, and raise capital if it is in the buffer.
            if (b.policy.dividendPayout.ppm > 0L || b.policy.growthAppetite.ppm > 200_000L) {
                out += BankAction.SetCapitalPolicy(b.id, b.policy.capitalCushion, Rate.ZERO)
                out += BankAction.SetGrowthAppetite(b.id, Rate(minOf(b.policy.growthAppetite.ppm, 150_000L)))
            }
            if (v.capital.rung >= SupervisoryRung.BUFFER && b.rung < SupervisoryRung.SEVERE) {
                val needed = CapitalEngine.capitalNeeded(b, params.capital, st.capitalCushion)
                if (needed.isPositive) out += BankAction.RaiseCapital(b.id, CapitalInstrument.COMMON_EQUITY, needed + needed.times(Rate.percent(10)))
            }
            if (b.policy.minRating.ordinal > CreditRating.BB.ordinal && short.ppm < -10_000L) {
                out += BankAction.SetLendingStandards(b.id, b.policy.minRating.up(), b.policy.maxSectorShare)
            }
        } else if (short.ppm > 40_000L && b.rung == SupervisoryRung.NORMAL) {
            // Plenty of capital: pay owners what the strategy says and use the room to grow.
            if (b.policy.dividendPayout < st.payout) out += BankAction.SetCapitalPolicy(b.id, st.capitalCushion, st.payout)
            if (b.policy.growthAppetite < st.growthAppetite) out += BankAction.SetGrowthAppetite(b.id, st.growthAppetite)
            val desired = BankPolicy.from(st).minRating
            if (b.policy.minRating.ordinal < desired.ordinal) out += BankAction.SetLendingStandards(b.id, b.policy.minRating.down(), b.policy.maxSectorShare)
        }
        return out
    }

    private fun liquidityActions(v: BankView, st: BankStrategy): List<BankAction> {
        val b = v.bank
        val out = ArrayList<BankAction>()
        val lcr = v.liquidity.lcr
        val target = Rate.ONE + st.liquidityCushion
        if (lcr < target) {
            // Build the buffer: hold short government debt instead of corporate bonds and long bonds.
            val corporate = b.securityOf(SecurityKind.CORPORATE_BONDS)
            if (corporate.isPositive) out += BankAction.SellSecurities(b.id, SecurityKind.CORPORATE_BONDS, corporate.times(Rate.percent(50)))
            out += BankAction.SetFundingPolicy(b.id, Rate(maxOf(b.policy.securitiesShare.ppm, st.securitiesShare.ppm) + 20_000L), b.policy.wholesaleCap, target)
        }
        if (b.runStage >= RunStage.WATCHFUL) {
            val spend = b.totalAssets.times(Rate.basisPoints(2))
            out += BankAction.ReassureDepositors(b.id, spend)
        }
        return out
    }

    private fun depositActions(v: BankView, st: BankStrategy, random: DeterministicRandom): List<BankAction> {
        val b = v.bank
        val out = ArrayList<BankAction>()
        val pressure = v.liquidity.lcr < Rate.ONE + st.liquidityCushion || v.depositMomentum.ppm > 20_000L
        val comfortable = v.liquidity.lcr > Rate.ONE + st.liquidityCushion + Rate.percent(200)
        val flush = v.liquidity.excessReserves > b.totalAssets.times(IDLE_CASH)
        val relaxed = (comfortable && v.depositMomentum.ppm <= 0L) || flush
        for (product in DepositProduct.values()) {
            val resting = restingPremium(st, product)
            val now = b.policy.premiumOf(product)
            val jitter = random.nextInRange(-1L, 1L)
            val next = when {
                pressure -> now + 10L + jitter
                relaxed -> now - 5L + jitter
                now > resting -> now - 2L
                now < resting -> now + 2L
                else -> now
            }.coerceIn(-params.deposits.maxPremiumBps / 2L, params.deposits.maxPremiumBps)
            if (next != now) out += BankAction.SetDepositPremium(b.id, product, next)
        }
        return out
    }

    private fun lendingActions(v: BankView, st: BankStrategy): List<BankAction> {
        val b = v.bank
        val out = ArrayList<BankAction>()
        val sectors = v.margin.bySector.associateBy { it.sector }
        val average = if (sectors.isEmpty()) 0L else sectors.values.sumOf { it.riskAdjustedSpread.ppm } / sectors.size
        for (sector in LoanSector.values()) {
            val bias = st.biasOf(sector).ppm
            val m = sectors[sector]
            var appetite = bias
            if (m != null) {
                // More appetite where the risk-adjusted spread is above average, less where it is below.
                appetite += (m.riskAdjustedSpread.ppm - average) * 10L
                if (m.impairedShare.ppm > 80_000L) appetite = appetite * 7L / 10L
                if (m.share > b.policy.maxSectorShare) appetite = 0L
            }
            val target = Rate(appetite.coerceIn(0L, Rate.ONE_PPM))
            if ((target.ppm - b.policy.appetiteOf(sector).ppm).let { if (it < 0) -it else it } > 50_000L) {
                out += BankAction.SetSectorAppetite(b.id, sector, target)
            }
        }
        val nim = v.margin.trailingMargin.ppm.takeIf { b.history.size >= 3 } ?: v.margin.netInterestMargin.ppm
        val margin = b.policy.loanMarginBps
        if (b.history.size >= 3) {
            if (nim < 15_000L && margin < 150L) out += BankAction.SetLoanMargin(b.id, margin + 10L)
            else if (nim > 35_000L && margin > -50L && st.growthAppetite.ppm > 600_000L) out += BankAction.SetLoanMargin(b.id, margin - 5L)
        }
        return out
    }

    private fun balanceSheetActions(v: BankView, st: BankStrategy): List<BankAction> {
        val b = v.bank
        val out = ArrayList<BankAction>()
        val lp = v.liquidity
        val share = v.securitiesShare
        val excess = lp.excessReserves
        if (excess.isPositive && share.ppm < st.securitiesShare.ppm - 30_000L) {
            val gap = b.totalAssets.times(Rate(st.securitiesShare.ppm - share.ppm))
            val spend = Money.min(gap, excess.times(Rate.percent(50)))
            if (spend.isPositive) {
                val kind = if (v.liquidity.lcr < Rate.percent(150)) SecurityKind.GOVERNMENT_SHORT else SecurityKind.GOVERNMENT_LONG
                out += BankAction.BuySecurities(b.id, kind, spend)
            }
        }
        // Cash that sits idle earns little: park most of the surplus in government paper.
        val idle = excess - b.totalAssets.times(IDLE_CASH)
        if (idle.isPositive && out.none { it is BankAction.BuySecurities }) {
            val kind = if (lp.lcr < Rate.percent(150)) SecurityKind.GOVERNMENT_SHORT else SecurityKind.GOVERNMENT_LONG
            out += BankAction.BuySecurities(b.id, kind, idle.times(Rate.percent(70)))
        }
        val wholesaleShare = b.wholesaleShare
        if (lp.reserves < lp.desiredReserves && wholesaleShare < st.wholesaleReliance && b.rung < SupervisoryRung.RESTRICTED) {
            val need = lp.desiredReserves - lp.reserves
            out += BankAction.BorrowWholesale(b.id, need.times(Rate.percent(120)))
        } else if (excess > lp.desiredReserves && b.wholesale.isPositive) {
            out += BankAction.RepayWholesale(b.id, Money.min(b.wholesale, excess.times(Rate.percent(50))))
        }
        return out
    }

    private companion object {
        /** Reserves above this share of assets, beyond the working target, count as idle cash. */
        val IDLE_CASH = Rate.percent(8)
    }
}
