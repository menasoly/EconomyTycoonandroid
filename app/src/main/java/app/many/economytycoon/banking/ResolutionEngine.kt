package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/**
 * Carries out a resolution plan on the books. Every step keeps the books of the banks involved in balance: losses go
 * through the allowance and retained earnings, instruments are converted or written down against equity, and a merger
 * moves whole balance sheets across.
 */
class ResolutionEngine(private val params: BankingParams, private val planner: ResolutionPlanner) {
    private val p get() = params.resolution

    /**
     * Writes the loans down to what they would fetch in a sale as a going concern, by raising the allowance. Returns the
     * extra charge, which has reduced retained earnings.
     */
    fun recogniseLosses(bank: Bank, collateralIndexPpm: Long): Money {
        val snap = bank.snapshot()
        val fair = planner.valuation(snap, collateralIndexPpm).goingConcern -
            (snap.reserves.coerceAtLeast(Money.ZERO) + snap.securitiesValue() + snap.interbankLent + snap.fixedAssets)
        val carrying = snap.loansGross - snap.allowance
        val extra = (carrying - fair).coerceAtLeast(Money.ZERO)
        bank.allowance += extra
        bank.retained -= extra
        return extra
    }

    /** Converts capital instruments into common equity, additional tier 1 first. Returns the amount converted. */
    private fun convertInstruments(bank: Bank, amount: Money): Money {
        val fromAt1 = Money.min(amount, bank.additionalTier1)
        val fromT2 = Money.min(amount - fromAt1, bank.tier2)
        bank.additionalTier1 -= fromAt1
        bank.tier2 -= fromT2
        val total = fromAt1 + fromT2
        bank.shareCapital += total
        return total
    }

    fun recapitalise(bank: Bank, plan: ResolutionPlan, collateralIndexPpm: Long, tick: Tick): ResolutionOutcome {
        require(plan.tool == ResolutionTool.RECAPITALISE) { "not a recapitalisation plan" }
        recogniseLosses(bank, collateralIndexPpm)
        val snap = bank.snapshot()
        val capital = CapitalEngine.position(snap, params.capital)
        val goal = capital.rwa.total.times(params.capital.minCet1 + params.capital.conservationBuffer)
        val deficit = (goal - snap.cet1Capital).coerceAtLeast(Money.ZERO)
        val converted = convertInstruments(bank, deficit)
        val injection = (deficit - converted).coerceAtLeast(Money.ZERO)
        bank.reserves += injection
        bank.shareCapital += injection
        bank.changeStatus(BankStatus.SUPERVISED, tick)
        bank.interventionReason = plan.trigger.label
        return ResolutionOutcome(bank.id, ResolutionTool.RECAPITALISE, plan.trigger, tick.value, null, Money.ZERO, Money.ZERO, Money.ZERO, converted, injection, Money.ZERO)
    }

    private fun mergePools(target: Bank, into: Bank) {
        for ((key, pool) in target.loans.toMap()) into.addLoans(key, pool.principal, pool.ratePpm)
        for ((key, pool) in target.deposits.toMap()) {
            val old = into.depositPool(key)
            val total = old.balance + pool.balance
            val rate = if (total.isZero) 0L else BankMath.blend(old.ratePpm, pool.ratePpm, pool.balance.raw, total.raw)
            into.deposits[key] = DepositPool(total, rate)
        }
        for ((kind, h) in target.securities.toMap()) {
            val old = into.security(kind)
            val total = old.value + h.value
            val yield = if (total.isZero) h.yieldPpm else BankMath.blend(old.yieldPpm, h.yieldPpm, h.value.raw, total.raw)
            val duration = if (total.isZero) h.durationMonths else BankMath.blend(old.durationMonths.toLong(), h.durationMonths.toLong(), h.value.raw, total.raw).toInt()
            into.putSecurity(kind, SecurityHolding(total, yield, duration))
        }
    }

    private fun clear(bank: Bank) {
        bank.loans.clear(); bank.deposits.clear(); bank.securities.clear()
        bank.reserves = Money.ZERO; bank.interbankLent = Money.ZERO; bank.interbankBorrowed = Money.ZERO
        bank.centralBankMarginal = Money.ZERO; bank.centralBankEmergency = Money.ZERO
        bank.fixedAssets = Money.ZERO; bank.intangibles = Money.ZERO
        bank.shareCapital = Money.ZERO; bank.retained = Money.ZERO; bank.oci = Money.ZERO
        bank.additionalTier1 = Money.ZERO; bank.tier2 = Money.ZERO
        bank.wholesale = Money.ZERO; bank.allowance = Money.ZERO
    }

    fun merge(
        target: Bank, acquirer: Bank, fund: GuaranteeFund, banks: List<Bank>, plan: ResolutionPlan, collateralIndexPpm: Long, tick: Tick,
    ): ResolutionOutcome {
        require(plan.tool == ResolutionTool.MERGER) { "not a merger plan" }
        require(target.id != acquirer.id) { "a bank cannot merge into itself" }
        recogniseLosses(target, collateralIndexPpm)
        // Junior capital absorbs the hole first.
        var bailed = Money.ZERO
        val hole = (-target.snapshot().bookEquity).coerceAtLeast(Money.ZERO)
        if (hole.isPositive) {
            val fromAt1 = Money.min(hole, target.additionalTier1)
            val fromT2 = Money.min(hole - fromAt1, target.tier2)
            target.additionalTier1 -= fromAt1
            target.tier2 -= fromT2
            target.retained += fromAt1 + fromT2
            bailed = fromAt1 + fromT2
        }
        // The fund fills what is still missing so that the business is handed over with no negative equity.
        val missing = (-target.snapshot().bookEquity).coerceAtLeast(Money.ZERO)
        if (missing.isPositive) {
            GuaranteeScheme.payOut(fund, missing, banks, p, params.deposits, tick.value)
            target.reserves += missing
            target.retained += missing
        }
        val net = target.snapshot().bookEquity
        val price = Money.min(net.times(p.purchasePriceShare), acquirer.reserves.coerceAtLeast(Money.ZERO))

        mergePools(target, acquirer)
        acquirer.reserves += target.reserves - price
        acquirer.interbankLent += target.interbankLent
        acquirer.interbankBorrowed += target.interbankBorrowed
        acquirer.centralBankMarginal += target.centralBankMarginal
        acquirer.centralBankEmergency += target.centralBankEmergency
        acquirer.fixedAssets += target.fixedAssets
        acquirer.intangibles += target.intangibles
        acquirer.additionalTier1 += target.additionalTier1
        acquirer.tier2 += target.tier2
        acquirer.allowance += target.allowance
        val wholesale = acquirer.wholesale + target.wholesale
        val wholesaleRate = if (wholesale.isZero) acquirer.wholesaleRatePpm
        else BankMath.blend(acquirer.wholesaleRatePpm, target.wholesaleRatePpm, target.wholesale.raw, wholesale.raw)
        acquirer.setWholesale(wholesale, wholesaleRate)
        acquirer.retained += net - price

        clear(target)
        target.changeStatus(BankStatus.MERGED, tick)
        target.interventionReason = plan.trigger.label
        return ResolutionOutcome(target.id, ResolutionTool.MERGER, plan.trigger, tick.value, acquirer.id, missing, Money.ZERO, Money.ZERO, bailed, Money.ZERO, price)
    }

    /**
     * The other banks' interbank positions with the failed bank are settled: they repay what they owed the estate and are
     * paid back [recovery] of what they lent it. This is how a failure spreads to the others.
     */
    private fun settleInterbank(target: Bank, others: List<Bank>, recovery: Rate) {
        val lenders = others.filter { it.interbankLent.isPositive }
        val owed = target.interbankBorrowed
        if (owed.isPositive && lenders.isNotEmpty()) {
            val shares = BankMath.splitCapped(owed, LongArray(lenders.size) { lenders[it].interbankLent.raw }, Array(lenders.size) { lenders[it].interbankLent })
            for (i in lenders.indices) {
                val back = shares[i].times(recovery)
                lenders[i].interbankLent -= shares[i]
                lenders[i].reserves += back
                lenders[i].retained -= (shares[i] - back)
            }
        }
        val due = target.interbankLent
        val borrowers = others.filter { it.interbankBorrowed.isPositive }
        if (due.isPositive && borrowers.isNotEmpty()) {
            val shares = BankMath.splitCapped(due, LongArray(borrowers.size) { borrowers[it].interbankBorrowed.raw }, Array(borrowers.size) { borrowers[it].interbankBorrowed })
            for (i in borrowers.indices) {
                borrowers[i].interbankBorrowed -= shares[i]
                borrowers[i].reserves -= shares[i]
            }
        }
    }

    fun liquidate(
        target: Bank, fund: GuaranteeFund, banks: List<Bank>, plan: ResolutionPlan, collateralIndexPpm: Long, tick: Tick,
    ): ResolutionOutcome {
        require(plan.tool == ResolutionTool.LIQUIDATION) { "not a liquidation plan" }
        val snap = target.snapshot()
        val value = planner.valuation(snap, collateralIndexPpm)
        val claims = BankWaterfall.claimsOf(snap, params.deposits)
        val dist = BankWaterfall.distribute(value.liquidation, claims)
        val insured = dist.first { it.rank == ClaimRank.INSURED_DEPOSITS }
        val uninsured = dist.first { it.rank == ClaimRank.UNINSURED_DEPOSITS }
        val wholesale = dist.first { it.rank == ClaimRank.SENIOR_WHOLESALE }

        // The fund pays the insured depositors in full and takes their place in the queue.
        GuaranteeScheme.payOut(fund, insured.claim, banks, p, params.deposits, tick.value)
        fund.balance += insured.paid
        val extraCost = snap.totalAssets.times(p.resolutionCost)
        GuaranteeScheme.payOut(fund, extraCost, banks, p, params.deposits, tick.value)

        val others = banks.filter { it.id != target.id && it.status.isOperating }.sortedBy { it.id }
        val interbankClaim = snap.interbankBorrowed
        val interbankRecovery = if (wholesale.claim.isZero) Rate.ONE else wholesale.recovery
        settleInterbank(target, others, if (interbankClaim.isPositive) interbankRecovery else Rate.ONE)

        clear(target)
        target.changeStatus(BankStatus.LIQUIDATED, tick)
        target.interventionReason = plan.trigger.label
        val cost = insured.loss + extraCost
        return ResolutionOutcome(target.id, ResolutionTool.LIQUIDATION, plan.trigger, tick.value, null, cost, insured.claim, uninsured.loss, Money.ZERO, Money.ZERO, Money.ZERO)
    }

    /** Places a bank under supervision: new lending stops and the supervisor's restrictions apply. */
    fun intervene(bank: Bank, reason: String, tick: Tick) {
        if (bank.status == BankStatus.ACTIVE) bank.changeStatus(BankStatus.SUPERVISED, tick)
        bank.interventionReason = reason
    }

    /** Lifts supervision from a bank that has stayed clear of the ladder long enough. */
    fun release(bank: Bank, tick: Tick): Boolean {
        if (bank.status != BankStatus.SUPERVISED) return false
        if (bank.rung != SupervisoryRung.NORMAL || bank.runStage != RunStage.CALM) return false
        if (tick.value - bank.statusSince.value < p.supervisionMonths * BankMath.MONTH_TICKS) return false
        bank.changeStatus(BankStatus.ACTIVE, tick)
        bank.interventionReason = ""
        return true
    }

}
