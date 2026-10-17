package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.CreditRating

/** A request for credit from one class of borrower. */
data class LoanApplication(val sector: LoanSector, val rating: CreditRating, val amount: Money) {
    init { require(amount.raw > 0L) { "an application needs an amount" } }
}

enum class DeclineReason(val label: String) {
    SUPERVISORY_FREEZE("The supervisor has frozen new lending"),
    RATING_BELOW_STANDARD("Below the bank's minimum borrower rating"),
    SECTOR_CLOSED("The bank is not lending to this sector"),
    BELOW_HURDLE("Does not earn the required return on capital"),
    SECTOR_CONCENTRATION("Sector limit reached"),
    CAPITAL_LIMIT("Not enough capital headroom"),
    LIQUIDITY_LIMIT("Liquidity would fall too low"),
    LOSS_BUDGET("Expected losses would use too much capital"),
    GROWTH_BUDGET("Monthly growth budget used up"),
    TOO_SMALL("Too small to be worth a loan officer's time"),
}

sealed class LoanDecision {
    /** The bank will lend [amount], which may be less than asked; [limitedBy] says what held it back. */
    data class Approved(
        val amount: Money,
        val quote: LoanQuote,
        val expectedLoss: Rate,
        val raroc: Rate,
        val limitedBy: DeclineReason?,
    ) : LoanDecision()

    data class Declined(val reason: DeclineReason) : LoanDecision()
}

/** Everything a bank looks at before lending, worked out once per month from its books. */
class LendingContext(val inputs: BankingInputs, val params: BankingParams, val bank: BankSnapshot) {
    val capital: CapitalPosition = CapitalEngine.position(bank, params.capital)
    val liquidity: LiquidityPosition = LiquidityEngine.position(bank, params.liquidity, params.deposits::insuredShare)
    val fundingCost: Rate = LoanPricing.fundingCost(bank, inputs, params.facilities)
    val collateralIndex: Long = inputs.collateralIndex(bank.country)

    /** Loans the bank may add in a month, in total. */
    val growthBudget: Money = bank.totalAssets.times(params.lending.monthlyGrowthCap)
        .let { Money(mulDiv(it.raw, bank.policy.growthAppetite.ppm, 500_000L)) }

    /** The annual expected loss of the book as it stands. */
    val annualExpectedLoss: Money = Provisioning.annualExpectedLoss(bank, { sector -> inputs.defaultMultiplier(bank.country, sector) }, inputs.collateralIndex(bank.country))
        

    private val appetiteSum: Long = LoanSector.values().sumOf { bank.policy.appetiteOf(it).ppm }

    /** The part of the monthly growth budget this sector may use. */
    fun sectorBudget(sector: LoanSector): Money {
        if (appetiteSum <= 0L) return Money.ZERO
        return Money(mulDiv(growthBudget.raw, bank.policy.appetiteOf(sector).ppm, appetiteSum))
    }
}

object Underwriter {
    /** Annual expected loss of a borrower class as a share of the exposure: probability of default times loss given default. */
    fun expectedLossRate(sector: LoanSector, rating: CreditRating, multiplierPpm: Long, collateralIndexPpm: Long): Rate {
        val pd = RiskTables.stressedPd(rating, multiplierPpm)
        return Rate(mulDiv(pd.ppm, RiskTables.lgd(sector, collateralIndexPpm).ppm, Rate.ONE_PPM))
    }

    /** Annual expected loss of what a bank has lent to a sector, as a share of the balance on performing and watch-listed loans. */
    fun expectedLossRateOfBook(s: BankSnapshot, sector: LoanSector, multiplierPpm: Long, collateralIndexPpm: Long): Rate {
        var loss = Money.ZERO
        var balance = Money.ZERO
        for ((key, pool) in s.loans) {
            if (key.sector != sector || key.stage == LoanStage.IMPAIRED) continue
            loss += pool.principal.times(expectedLossRate(sector, key.rating, multiplierPpm, collateralIndexPpm))
            balance += pool.principal
        }
        return BankMath.ratio(loss, balance)
    }

    /**
     * Return on the capital a loan ties up, after tax: the rate less funding cost, expected loss and running cost,
     * over the capital the exposure's risk weight demands.
     */
    fun raroc(quote: LoanQuote, ctx: LendingContext, expectedLoss: Rate): Rate {
        val p = ctx.params.lending
        val margin = quote.total.ppm - ctx.fundingCost.ppm - expectedLoss.ppm - p.opexRate.ppm
        val afterTax = if (margin > 0L) margin - mulDiv(margin, p.taxRate.ppm, Rate.ONE_PPM) else margin
        val rw = RiskTables.riskWeight(quote.sector, quote.rating, LoanStage.PERFORMING).ppm
        val capital = mulDiv(rw, p.allocatedCapitalRatio.ppm, Rate.ONE_PPM).coerceAtLeast(1L)
        return Rate(mulDiv(afterTax, Rate.ONE_PPM, capital))
    }

    /**
     * Decides on an application. [usedInSector] and [usedTotal] are what the bank has already committed this month, so
     * a long queue of applications cannot together overrun the growth budget.
     */
    fun evaluate(ctx: LendingContext, app: LoanApplication, usedInSector: Money, usedTotal: Money): LoanDecision {
        val bank = ctx.bank
        val p = ctx.params.lending
        if (!bank.isOperating || bank.rung.isBreach || bank.runStage >= RunStage.RUN) return LoanDecision.Declined(DeclineReason.SUPERVISORY_FREEZE)
        if (app.rating.ordinal > bank.policy.minRating.ordinal) return LoanDecision.Declined(DeclineReason.RATING_BELOW_STANDARD)
        if (bank.policy.appetiteOf(app.sector).ppm <= 0L) return LoanDecision.Declined(DeclineReason.SECTOR_CLOSED)

        val quote = LoanPricing.quote(ctx.inputs, bank, ctx.liquidity, app.sector, app.rating, ctx.params)
        val multiplier = ctx.inputs.defaultMultiplier(bank.country, app.sector)
        val el = expectedLossRate(app.sector, app.rating, multiplier, ctx.collateralIndex)
        val raroc = raroc(quote, ctx, el)
        if (raroc < p.hurdleReturn) return LoanDecision.Declined(DeclineReason.BELOW_HURDLE)

        var amount = app.amount
        var binding: DeclineReason? = null
        fun limit(cap: Money, reason: DeclineReason) {
            if (cap < amount) { amount = cap.coerceAtLeast(Money.ZERO); binding = reason }
        }

        limit((ctx.sectorBudget(app.sector) - usedInSector).coerceAtLeast(Money.ZERO), DeclineReason.GROWTH_BUDGET)
        limit((ctx.growthBudget - usedTotal).coerceAtLeast(Money.ZERO), DeclineReason.GROWTH_BUDGET)
        limit(concentrationRoom(ctx, app.sector), DeclineReason.SECTOR_CONCENTRATION)

        val rw = RiskTables.riskWeight(app.sector, app.rating, LoanStage.PERFORMING)
        val capacity = CapitalEngine.exposureCapacity(bank, ctx.params.capital, bank.policy.capitalCushion)
        limit(if (rw.ppm <= 0L) amount else Money(mulDiv(capacity.raw, Rate.ONE_PPM, rw.ppm)), DeclineReason.CAPITAL_LIMIT)

        limit(liquidityRoom(ctx), DeclineReason.LIQUIDITY_LIMIT)
        limit(lossRoom(ctx, el), DeclineReason.LOSS_BUDGET)

        if (amount < Money.units(p.minTicketUnits)) {
            return LoanDecision.Declined(binding ?: DeclineReason.TOO_SMALL)
        }
        return LoanDecision.Approved(amount, quote, el, raroc, binding)
    }

    /** How much more a sector can grow before it passes its share limit. Unlimited for a book too small to judge. */
    fun concentrationRoom(ctx: LendingContext, sector: LoanSector): Money {
        val bank = ctx.bank
        val limit = bank.policy.maxSectorShare
        if (bank.loansGross < Money.units(ctx.params.lending.minTicketUnits * 20L) || limit >= Rate.ONE) return BankMath.UNLIMITED
        if (limit.ppm <= 0L) return Money.ZERO
        // (sector + A) <= limit * (loans + A)  =>  A <= (limit * loans - sector) / (1 - limit)
        val numerator = bank.loansGross.times(limit) - bank.loansOf(sector)
        if (numerator.raw <= 0L) return Money.ZERO
        return Money(mulDiv(numerator.raw, Rate.ONE_PPM, Rate.ONE_PPM - limit.ppm))
    }

    /** Lending that still leaves the liquid buffer at the minimum coverage once the cash that leaks out is gone. */
    fun liquidityRoom(ctx: LendingContext): Money {
        val leak = ctx.params.lending.leakage
        val lp = ctx.liquidity
        val spare = lp.hqla - lp.netOutflows.times(ctx.params.liquidity.minLcr)
        if (spare.raw <= 0L) return Money.ZERO
        if (leak.ppm <= 0L) return BankMath.UNLIMITED
        return Money(mulDiv(spare.raw, Rate.ONE_PPM, leak.ppm))
    }

    /** Lending whose expected loss still fits inside what the bank is willing to lose in a year. */
    fun lossRoom(ctx: LendingContext, el: Rate): Money {
        val budget = ctx.capital.cet1.times(ctx.params.lending.maxLossToCapital) - ctx.annualExpectedLoss
        if (el.ppm <= 0L) return BankMath.UNLIMITED
        if (budget.raw <= 0L) return Money.ZERO
        return Money(mulDiv(budget.raw, Rate.ONE_PPM, el.ppm))
    }
}
