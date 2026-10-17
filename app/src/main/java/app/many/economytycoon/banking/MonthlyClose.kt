package app.many.economytycoon.banking

import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.SettlementIntent

/** Events and money flows produced while stepping the sector. */
internal class Outputs {
    val events = ArrayList<BankEvent>()
    val intents = ArrayList<SettlementIntent>()

    fun add(event: BankEvent) { events.add(event) }
    fun add(intent: SettlementIntent?) { if (intent != null) intents.add(intent) }
    fun addAll(list: List<SettlementIntent>) { intents.addAll(list) }
}

/** What the monthly close leaves for the service to act on once every bank has been closed. */
internal class MonthlyOutcome(val resolutions: List<Pair<BankId, ResolutionTrigger>>, val planningDue: List<BankId>)

/** Money flows of one bank over a month, collected while the close runs. */
private class Flows {
    var loanInterest = Money.ZERO
    var securityIncome = Money.ZERO
    var reserveInterest = Money.ZERO
    var depositHousehold = Money.ZERO
    var depositCorporate = Money.ZERO
    var wholesaleInterest = Money.ZERO
    var subordinatedInterest = Money.ZERO
    var centralBankInterest = Money.ZERO
    var fees = Money.ZERO
    var operating = Money.ZERO
    var provision = Money.ZERO
    var tax = Money.ZERO
    var openEarning = Money.ZERO
    var openLoans = Money.ZERO
    var openEquity = Money.ZERO
    var openDeposits = Money.ZERO
}

/**
 * The monthly close of the banks in one country: deposit competition, interest, run-off, repricing, mark to market, credit
 * migration and write-offs, new lending, the month's result, tax, dividends, rating and the supervisory ladder.
 *
 * Every posting moves cash and retained earnings (or the allowance, or other comprehensive income) together, so the books
 * balance after each step.
 */
internal class MonthlyClose(
    private val book: BankingBook,
    private val params: BankingParams,
    private val loanMarket: LoanMarket,
    private val resolution: ResolutionPlanner,
    private val ai: BankAiPlanner,
) {
    private fun income(b: Bank, x: Money) { if (x.raw != 0L) { b.reserves += x; b.retained += x } }
    private fun expense(b: Bank, x: Money) { if (x.raw != 0L) { b.reserves -= x; b.retained -= x } }

    fun run(country: app.many.economytycoon.core.CountryId, banks: List<Bank>, inputs: BankingInputs, tick: Tick, random: RandomProvider, out: Outputs): MonthlyOutcome {
        val live = banks.filter { it.status.isOperating }.sortedBy { it.id }
        if (live.isEmpty()) return MonthlyOutcome(emptyList(), emptyList())
        val policy = inputs.policyRate(country)
        val corridor = CentralBankFacilities.corridor(policy, params.facilities)
        val collateral = inputs.collateralIndex(country)

        // Customers move their deposits around and new money arrives.
        for (b in live) DepositMarket.reprice(b, policy, params.deposits)
        for (d in Depositor.values()) for (product in DepositProduct.values()) DepositMarket.churn(live, d, product, params.deposits)
        for (d in Depositor.values()) DepositMarket.applyFlow(live, d, inputs.savingsFlow(country, d), params.deposits)

        val flows = live.associate { it.id to Flows() }
        for (b in live) phaseA(b, flows.getValue(b.id), inputs, policy, corridor, collateral, random.stream("banking.credit", b.id.value), tick)

        val originations = loanMarket.run(live, inputs)
        for (o in originations) flows.getValue(o.bank).fees += o.fee

        val resolutions = ArrayList<Pair<BankId, ResolutionTrigger>>()
        val due = ArrayList<BankId>()
        for (b in live) {
            phaseC(b, flows.getValue(b.id), inputs, tick, collateral, out)
            val trigger = review(b, tick, out)
            if (trigger != null) resolutions.add(b.id to trigger)
            else if (b.strategy != null && b.operator == BankOperator.AI && ai.isDue(b.snapshot(), b.strategy, tick.value, b.id.value * BankMath.MONTH_TICKS)) due.add(b.id)
        }
        return MonthlyOutcome(resolutions, due)
    }

    // Phase A: interest, run-off, repricing, mark to market, migration and write-offs -------------------------------------

    private fun phaseA(
        b: Bank, f: Flows, inputs: BankingInputs, policy: Rate, corridor: Corridor, collateral: Long, random: DeterministicRandom, tick: Tick,
    ) {
        val open = b.snapshot()
        f.openEarning = open.earningAssets; f.openLoans = open.loansGross; f.openEquity = open.bookEquity; f.openDeposits = open.depositsTotal

        for ((key, pool) in open.loans) if (key.stage != LoanStage.IMPAIRED) f.loanInterest += BankMath.monthlyInterest(pool.principal, pool.ratePpm)
        income(b, f.loanInterest)
        for (h in open.securities.values) f.securityIncome += BankMath.monthlyInterest(h.value, h.yieldPpm)
        income(b, f.securityIncome)
        val required = LiquidityEngine.requiredReserves(open, params.liquidity)
        f.reserveInterest = CentralBankFacilities.monthlyRemuneration(open, required, corridor)
        income(b, f.reserveInterest)

        for ((key, pool) in open.deposits) {
            val interest = BankMath.monthlyInterest(pool.balance, pool.ratePpm)
            if (key.depositor == Depositor.HOUSEHOLD) f.depositHousehold += interest else f.depositCorporate += interest
            if (key.product == DepositProduct.DEMAND) f.fees += BankMath.monthlyInterest(pool.balance, params.deposits.demandFee.ppm)
        }
        expense(b, f.depositHousehold + f.depositCorporate)
        f.wholesaleInterest = BankMath.monthlyInterest(open.wholesale, open.wholesaleRatePpm)
        expense(b, f.wholesaleInterest)
        f.subordinatedInterest = BankMath.monthlyInterest(open.additionalTier1, policy.ppm + 450_00L) + BankMath.monthlyInterest(open.tier2, policy.ppm + 300_00L)
        expense(b, f.subordinatedInterest)
        f.centralBankInterest = CentralBankFacilities.monthlyInterest(open, corridor)
        expense(b, f.centralBankInterest)
        income(b, f.fees.also { })
        f.operating = BankMath.monthlyInterest(open.totalAssets, params.lending.opexRate.ppm)
        expense(b, f.operating)

        repriceLoans(b, inputs, policy)
        runOff(b)
        markSecurities(b, inputs)
        rollWholesale(b, policy, tick)

        val multiplier = { s: LoanSector -> inputs.defaultMultiplier(b.country, s) }
        Provisioning.migrate(b, multiplier, params.lending.migrationNoise, random)
        Provisioning.writeOff(b, collateral)
    }

    /** Floating loans follow the market: they move half way to what a new loan would cost, scaled by the sector's floating share. */
    private fun repriceLoans(b: Bank, inputs: BankingInputs, policy: Rate) {
        for ((key, pool) in b.loans.toMap()) {
            val term = RiskTables.termMonths(key.sector)
            val curve = (inputs.sovereignYield(b.country, term.coerceAtMost(120)).ppm - policy.ppm).coerceAtLeast(0L)
            val target = policy.ppm.coerceAtLeast(0L) + LoanPricing.creditSpread(key.sector, key.rating).ppm + curve.coerceAtMost(params.lending.maxTermPremium.ppm) +
                Rate.basisPoints(b.policy.loanMarginBps).ppm
            val moved = mulDiv(mulDiv(target - pool.ratePpm, RiskTables.floatingShare(key.sector).ppm, Rate.ONE_PPM), 500_000L, Rate.ONE_PPM)
            if (moved != 0L) b.setLoanRate(key, (pool.ratePpm + moved).coerceAtLeast(0L))
        }
    }

    /** Borrowers repay a share of each performing pool; the cash comes in as reserves. */
    private fun runOff(b: Bank) {
        for ((key, pool) in b.loans.toMap()) {
            if (key.stage == LoanStage.IMPAIRED) continue
            val repay = Money(mulDiv(pool.principal.raw, RiskTables.monthlyRunoff(key.sector).ppm, Rate.ONE_PPM))
            val taken = b.removeLoans(key, repay)
            b.reserves += taken
        }
    }

    /** Securities are carried at fair value: a rise in yields lowers the value, and the change goes to other comprehensive income. */
    private fun markSecurities(b: Bank, inputs: BankingInputs) {
        for ((kind, h) in b.securities.toMap()) {
            val tenor = when (kind) {
                SecurityKind.GOVERNMENT_SHORT -> 6
                SecurityKind.GOVERNMENT_LONG -> 84
                SecurityKind.CORPORATE_BONDS -> 60
            }
            val market = inputs.sovereignYield(b.country, tenor).ppm + if (kind == SecurityKind.CORPORATE_BONDS) 15_000L else 0L
            val delta = market - h.yieldPpm
            val change = Money(-mulDiv(h.value.raw, delta * h.durationMonths, Rate.ONE_PPM * 12L))
            val value = (h.value + change).coerceAtLeast(Money.ZERO)
            val applied = value - h.value
            b.putSecurity(kind, SecurityHolding(value, market, h.durationMonths))
            b.oci += applied
        }
    }

    /**
     * A tenth of wholesale funding falls due each month. Banks the market trusts roll it over at a rate that reflects their
     * rating; others must repay it out of reserves, which is how a funding squeeze arrives.
     */
    private fun rollWholesale(b: Bank, policy: Rate, tick: Tick) {
        val maturing = b.wholesale.times(params.liquidity.wholesaleMaturingShare)
        if (maturing.raw <= 0L) return
        val trusted = b.rating.ordinal <= app.many.economytycoon.finance.CreditRating.B.ordinal && b.runStage < RunStage.RUN && b.rung < SupervisoryRung.SEVERE
        val newRate = policy.ppm + b.rating.spread.ppm + params.wholesaleSpread.ppm
        if (trusted) {
            val rest = b.wholesale - maturing
            val rate = BankMath.blend(b.wholesaleRatePpm, newRate, maturing.raw, (rest + maturing).raw)
            b.setWholesale(b.wholesale, rate.coerceAtLeast(0L))
        } else {
            b.setWholesale(b.wholesale - maturing, b.wholesaleRatePpm)
            b.reserves -= maturing
        }
    }

    // Phase C: allowance, result, tax, dividend, rating -----------------------------------------------------------------------

    private fun phaseC(b: Bank, f: Flows, inputs: BankingInputs, tick: Tick, collateral: Long, out: Outputs) {
        val multiplier = { s: LoanSector -> inputs.defaultMultiplier(b.country, s) }
        f.provision = Provisioning.adjustAllowance(b, multiplier, collateral)
        val interbankIncome = f.reserveInterest + b.periodInterbankIncome
        val interestIncome = f.loanInterest + f.securityIncome + interbankIncome
        val interestExpense = f.depositHousehold + f.depositCorporate + f.wholesaleInterest + f.subordinatedInterest + f.centralBankInterest + b.periodInterbankCost
        val fees = f.fees
        val preTax = interestIncome - interestExpense + fees + b.periodTrading - f.operating - f.provision
        f.tax = taxOf(b, preTax)
        expense(b, f.tax)

        val close = b.snapshot()
        val result = MonthlyResult(
            tick.value, f.loanInterest, f.securityIncome, interbankIncome, f.depositHousehold + f.depositCorporate, f.wholesaleInterest,
            f.centralBankInterest, b.periodInterbankCost, f.subordinatedInterest, fees, b.periodTrading, f.operating, f.provision, f.tax,
            average(f.openEarning, close.earningAssets), average(f.openLoans, close.loansGross), average(f.openEquity, close.bookEquity),
            average(f.openDeposits, close.depositsTotal),
        )
        b.record(result, params.historyMonths)
        b.resetPeriod()
        b.lastMonthClosed = tick.value
        emitIntents(b, f, result, tick, out)
        payDividend(b, tick, inputs, out)
    }

    private fun average(a: Money, c: Money) = Money((a.raw + c.raw) / 2L)

    /** Tax on the month's profit after tax losses brought forward; a loss adds to the losses carried. */
    private fun taxOf(b: Bank, preTax: Money): Money {
        if (preTax.raw <= 0L) { b.taxLossCarry += -preTax; return Money.ZERO }
        val used = Money.min(preTax, b.taxLossCarry)
        b.taxLossCarry -= used
        return (preTax - used).times(params.lending.taxRate)
    }

    private fun emitIntents(b: Bank, f: Flows, r: MonthlyResult, tick: Tick, out: Outputs) {
        val t = tick.value
        out.add(BankingIntents.customerIncome(b.id, f.loanInterest + f.fees, t))
        out.add(BankingIntents.securityIncome(b.id, b.country, f.securityIncome, t))
        out.add(BankingIntents.depositInterest(b.id, b.country, Depositor.HOUSEHOLD, f.depositHousehold, t))
        out.add(BankingIntents.depositInterest(b.id, b.country, Depositor.CORPORATE, f.depositCorporate, t))
        out.add(BankingIntents.marketFunding(b.id, f.wholesaleInterest + f.subordinatedInterest + r.interbankCost, t))
        out.add(BankingIntents.centralBank(b.id, b.country, f.centralBankInterest - f.reserveInterest, t))
        val wages = f.operating.times(Rate.percent(60))
        out.addAll(BankingIntents.operatingCosts(b.id, b.country, wages, f.operating - wages, t))
        out.add(BankingIntents.tax(b.id, b.country, f.tax, t))
    }

    private fun payDividend(b: Bank, tick: Tick, inputs: BankingInputs, out: Outputs) {
        // Banks run by the player pay dividends when the player decides to; the others follow their payout policy each quarter.
        if (b.operator == BankOperator.PLAYER || tick.value % (Dividends.PERIOD_MONTHS * BankMath.MONTH_TICKS) != 0L) return
        val view = BankView(b.snapshot(), inputs, params)
        val capacity = view.dividend
        if (capacity.amount.raw <= 0L) return
        val paid = Dividends.pay(b, capacity.amount)
        if (paid.raw <= 0L) return
        b.lastDividend = tick
        out.add(BankingIntents.dividend(b.id, b.country, b.owner, paid, tick.value))
        out.add(BankEvent.DividendPaid(b.id, paid, tick.value))
    }

    // Review: rating, ladder, supervision --------------------------------------------------------------------------------------

    /** Reviews rating, ladder rung and supervision after the close. Returns the reason to resolve the bank, if there is one. */
    private fun review(b: Bank, tick: Tick, out: Outputs): ResolutionTrigger? {
        val snap = b.snapshot()
        val capital = CapitalEngine.position(snap, params.capital)
        val liquidity = LiquidityEngine.position(snap, params.liquidity, params.deposits::insuredShare)
        val reviewed = BankRatingModel.review(snap, capital, liquidity, b.rating, b.ratingPendingDirection, b.ratingPendingCount, params.rating)
        if (reviewed.rating != b.rating) out.add(BankEvent.RatingChanged(b.id, b.rating, reviewed.rating, tick.value))
        b.setRating(reviewed.rating, reviewed.score, reviewed.direction, reviewed.count)

        val previous = b.rung
        var next = previous
        val candidate = capital.rung
        when {
            candidate > previous -> { next = candidate; b.cleanMonths = 0 }
            candidate < previous -> {
                b.cleanMonths += 1
                if (b.cleanMonths >= params.capital.releaseMonths) { next = SupervisoryRung.values()[previous.ordinal - 1]; b.cleanMonths = 0 }
            }
            else -> b.cleanMonths = 0
        }
        if (next != previous) {
            b.setRung(next, tick)
            out.add(BankEvent.RungChanged(b.id, previous, next, tick.value))
        }
        if (next.isBreach) {
            b.monthsSincePlan += 1
            if (b.restorationDeadline < 0L) b.restorationDeadline = tick.value + params.capital.restorationMonths * BankMath.MONTH_TICKS.toLong()
        } else {
            b.monthsSincePlan = 0
            b.restorationDeadline = -1L
        }
        if (next >= SupervisoryRung.RESTRICTED && b.status == BankStatus.ACTIVE) {
            b.changeStatus(BankStatus.SUPERVISED, tick)
            b.interventionReason = "Capital below the requirement"
            out.add(BankEvent.Supervised(b.id, b.interventionReason, tick.value))
        }
        return resolution.trigger(b.snapshot(), CapitalEngine.position(b.snapshot(), params.capital), false, b.monthsSincePlan)
    }
}
