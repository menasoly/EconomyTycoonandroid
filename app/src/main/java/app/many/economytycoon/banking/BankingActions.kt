package app.many.economytycoon.banking

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.CreditRating
import app.many.economytycoon.finance.SettlementIntent

/** What an accepted [BankAction] did: a line for the log, the money to move, and the events to tell the player about. */
data class ActionResult(
    val message: String,
    val intents: List<SettlementIntent> = emptyList(),
    val events: List<BankEvent> = emptyList(),
)

/**
 * Validates and applies the decisions of bank management. A rejected action changes nothing. The player may only act
 * for a bank they own and run, the planners only for computer-controlled banks, and a bank under supervision may not
 * do anything that makes it riskier or pays money out.
 */
internal class BankingActions(private val book: BankingBook, private val params: BankingParams) {
    private val tester = StressTester(params)

    fun apply(action: BankAction, tick: Tick, inputs: BankingInputs, actor: CompanyId?): Outcome<ActionResult> {
        val bank = book.banks[action.bank] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown bank")
        if (!bank.status.isOperating) return Outcome.reject(Rules.WRONG_STATE, "${bank.name} no longer operates")
        val allowed = if (actor == null) bank.operator == BankOperator.AI else bank.operator == BankOperator.PLAYER && bank.owner == actor
        if (!allowed) return Outcome.reject(Rules.NOT_OWNER, "you do not run ${bank.name}")
        val supervised = bank.status == BankStatus.SUPERVISED || bank.rung.isBreach
        return when (action) {
            is BankAction.FoundBank -> Outcome.reject(Rules.INVALID_ARGUMENT, "founding a bank is not an action on an existing bank")
            is BankAction.SetDepositPremium -> policy(bank, "deposit rates") {
                if (action.basisPoints !in -200L..500L) null
                else {
                    if (supervised && action.basisPoints > it.premiumOf(action.product)) return Outcome.reject(Rules.REGULATION, "a supervised bank may not chase deposits with higher rates")
                    it.withPremium(action.product, action.basisPoints)
                }
            }
            is BankAction.SetLoanMargin -> policy(bank, "loan margin") { if (action.basisPoints !in -150L..400L) null else it.copy(loanMarginBps = action.basisPoints) }
            is BankAction.SetLendingStandards -> policy(bank, "lending standards") {
                if (action.minRating !in RiskTables.LENDABLE || action.maxSectorShare.ppm !in 100_000L..Rate.ONE_PPM) null
                else {
                    if (supervised && (action.minRating > it.minRating || action.maxSectorShare > it.maxSectorShare)) return Outcome.reject(Rules.REGULATION, "a supervised bank may not loosen its lending standards")
                    it.copy(minRating = action.minRating, maxSectorShare = action.maxSectorShare)
                }
            }
            is BankAction.SetSectorAppetite -> policy(bank, "sector appetite") {
                if (action.appetite.ppm !in 0L..Rate.ONE_PPM) null
                else {
                    if (supervised && action.appetite > it.appetiteOf(action.sector)) return Outcome.reject(Rules.REGULATION, "a supervised bank may not expand lending")
                    it.withAppetite(action.sector, action.appetite)
                }
            }
            is BankAction.SetGrowthAppetite -> policy(bank, "growth appetite") {
                if (action.appetite.ppm !in 0L..Rate.ONE_PPM) null
                else {
                    if (supervised && action.appetite > it.growthAppetite) return Outcome.reject(Rules.REGULATION, "a supervised bank may not accelerate growth")
                    it.copy(growthAppetite = action.appetite)
                }
            }
            is BankAction.SetCapitalPolicy -> policy(bank, "capital policy") {
                if (action.cushion.ppm !in 0L..150_000L || action.dividendPayout.ppm !in 0L..Rate.ONE_PPM) null
                else {
                    if (supervised && (action.cushion < it.capitalCushion || action.dividendPayout > it.dividendPayout)) return Outcome.reject(Rules.REGULATION, "a supervised bank must keep capital and may not raise payouts")
                    it.copy(capitalCushion = action.cushion, dividendPayout = action.dividendPayout)
                }
            }
            is BankAction.SetFundingPolicy -> policy(bank, "funding policy") {
                if (action.securitiesShare.ppm !in 0L..600_000L || action.wholesaleCap.ppm !in 0L..400_000L || action.targetLcr.ppm !in 1_000_000L..3_000_000L) null
                else it.copy(securitiesShare = action.securitiesShare, wholesaleCap = action.wholesaleCap, targetLcr = action.targetLcr)
            }
            is BankAction.RaiseCapital -> raise(bank, action, tick)
            is BankAction.BuySecurities -> buy(bank, action, inputs, supervised)
            is BankAction.SellSecurities -> sell(bank, action)
            is BankAction.BorrowWholesale -> borrow(bank, action, inputs, supervised)
            is BankAction.RepayWholesale -> repay(bank, action)
            is BankAction.RequestEmergencyLiquidity -> emergency(bank, action, tick)
            is BankAction.ReassureDepositors -> reassure(bank, action, tick)
            is BankAction.RunStressTest -> stress(bank, action, inputs, tick)
            is BankAction.PayDividend -> dividend(bank, action, supervised, tick, inputs)
        }
    }

    private inline fun policy(bank: Bank, what: String, change: (BankPolicy) -> BankPolicy?): Outcome<ActionResult> {
        val next = change(bank.policy) ?: return Outcome.reject(Rules.INVALID_ARGUMENT, "$what out of range")
        bank.changePolicy(next)
        return Outcome.ok(ActionResult("${bank.name}: $what updated"))
    }

    private fun raise(bank: Bank, a: BankAction.RaiseCapital, tick: Tick): Outcome<ActionResult> {
        if (a.amount.raw <= 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "the amount must be positive")
        val snap = bank.snapshot()
        if (a.instrument != CapitalInstrument.COMMON_EQUITY) {
            val rwa = CapitalEngine.position(snap, params.capital).rwa.total
            val room = (rwa.times(params.maxInstrumentShare) - bank.additionalTier1 - bank.tier2).coerceAtLeast(Money.ZERO)
            if (a.amount > room) return Outcome.reject(Rules.LIMIT_EXCEEDED, "the market will not take more than ${room.raw / Money.SCALE} of subordinated debt")
        }
        when (a.instrument) {
            CapitalInstrument.COMMON_EQUITY -> bank.shareCapital += a.amount
            CapitalInstrument.ADDITIONAL_TIER1 -> bank.additionalTier1 += a.amount
            CapitalInstrument.TIER2 -> bank.tier2 += a.amount
        }
        bank.reserves += a.amount
        val intent = BankingIntents.capitalInjection(bank.id, bank.owner, a.amount, tick.value)
        return Outcome.ok(ActionResult("${bank.name} raised capital", listOfNotNull(intent), listOf(BankEvent.CapitalRaised(bank.id, a.instrument, a.amount, tick.value))))
    }

    private fun tenor(kind: SecurityKind) = when (kind) {
        SecurityKind.GOVERNMENT_SHORT -> 6
        SecurityKind.GOVERNMENT_LONG -> 84
        SecurityKind.CORPORATE_BONDS -> 60
    }

    private fun buy(bank: Bank, a: BankAction.BuySecurities, inputs: BankingInputs, supervised: Boolean): Outcome<ActionResult> {
        if (a.amount.raw <= 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "the amount must be positive")
        if (supervised && a.kind != SecurityKind.GOVERNMENT_SHORT) return Outcome.reject(Rules.REGULATION, "a supervised bank may only buy short government paper")
        val position = LiquidityEngine.position(bank.snapshot(), params.liquidity, params.deposits::insuredShare)
        if (a.amount > position.excessReserves) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "that would take the bank below its reserve target")
        val extra = if (a.kind == SecurityKind.CORPORATE_BONDS) 15_000L else 0L
        val yieldPpm = inputs.sovereignYield(bank.country, tenor(a.kind)).ppm + extra
        val bought = bank.buySecurities(a.kind, a.amount, yieldPpm, tenor(a.kind))
        return Outcome.ok(ActionResult("${bank.name} bought securities for ${bought.raw / Money.SCALE}"))
    }

    private fun sell(bank: Bank, a: BankAction.SellSecurities): Outcome<ActionResult> {
        if (a.amount.raw <= 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "the amount must be positive")
        val held = bank.security(a.kind).value
        if (held.raw <= 0L) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "the bank holds none of that")
        val sold = bank.sellSecurities(a.kind, a.amount, params.saleDiscount)
        return Outcome.ok(ActionResult("${bank.name} sold securities worth ${sold.raw / Money.SCALE}"))
    }

    private fun borrow(bank: Bank, a: BankAction.BorrowWholesale, inputs: BankingInputs, supervised: Boolean): Outcome<ActionResult> {
        if (a.amount.raw <= 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "the amount must be positive")
        if (supervised) return Outcome.reject(Rules.REGULATION, "a supervised bank may not raise new wholesale funding")
        if (bank.rating > CreditRating.B) return Outcome.reject(Rules.CREDIT_LIMIT, "the market will not lend to a bank rated ${bank.rating}")
        val snap = bank.snapshot()
        val cap = snap.totalAssets.times(bank.policy.wholesaleCap)
        if (bank.wholesale + a.amount > cap) return Outcome.reject(Rules.LIMIT_EXCEEDED, "wholesale funding would exceed the bank's own cap")
        val newRate = inputs.policyRate(bank.country).ppm + bank.rating.spread.ppm + params.wholesaleSpread.ppm
        val total = bank.wholesale + a.amount
        val rate = BankMath.blend(bank.wholesaleRatePpm, newRate, a.amount.raw, total.raw)
        bank.setWholesale(total, rate.coerceAtLeast(0L))
        bank.reserves += a.amount
        return Outcome.ok(ActionResult("${bank.name} borrowed ${a.amount.raw / Money.SCALE} in the wholesale market"))
    }

    private fun repay(bank: Bank, a: BankAction.RepayWholesale): Outcome<ActionResult> {
        if (a.amount.raw <= 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "the amount must be positive")
        if (bank.wholesale.raw <= 0L) return Outcome.reject(Rules.WRONG_STATE, "no wholesale funding to repay")
        val position = LiquidityEngine.position(bank.snapshot(), params.liquidity, params.deposits::insuredShare)
        val pay = Money.min(Money.min(a.amount, bank.wholesale), position.excessReserves)
        if (pay.raw <= 0L) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "the bank has no reserves to spare")
        bank.setWholesale(bank.wholesale - pay, bank.wholesaleRatePpm)
        bank.reserves -= pay
        return Outcome.ok(ActionResult("${bank.name} repaid ${pay.raw / Money.SCALE} of wholesale funding"))
    }

    private fun emergency(bank: Bank, a: BankAction.RequestEmergencyLiquidity, tick: Tick): Outcome<ActionResult> {
        if (a.amount.raw <= 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "the amount must be positive")
        val cet1 = CapitalEngine.position(bank.snapshot(), params.capital).cet1Ratio
        val got = CentralBankFacilities.borrowEmergency(bank, a.amount, cet1, params.facilities)
        if (got.raw <= 0L) return Outcome.reject(Rules.CREDIT_LIMIT, "the central bank will not lend against what the bank can pledge")
        return Outcome.ok(ActionResult("${bank.name} drew ${got.raw / Money.SCALE} of emergency liquidity", emptyList(), listOf(BankEvent.FacilityUsed(bank.id, Money.ZERO, got, tick.value))))
    }

    private fun reassure(bank: Bank, a: BankAction.ReassureDepositors, tick: Tick): Outcome<ActionResult> {
        if (a.spend.raw <= 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "the amount must be positive")
        val snap = bank.snapshot()
        if (a.spend > bank.reserves.coerceAtLeast(Money.ZERO)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "the bank cannot pay for the campaign from its reserves")
        val gain = BankMath.ratio(a.spend, snap.totalAssets).ppm * params.reassuranceEfficiency
        val room = (params.maxSupportPpm - bank.supportPpm).coerceAtLeast(0L)
        if (room == 0L) return Outcome.reject(Rules.LIMIT_EXCEEDED, "depositors are as reassured as they will be")
        val applied = gain.coerceAtMost(room)
        bank.reserves -= a.spend
        bank.retained -= a.spend
        bank.setSupport(bank.supportPpm + applied)
        return Outcome.ok(ActionResult("${bank.name} ran a reassurance campaign", BankingIntents.operatingCosts(bank.id, bank.country, Money.ZERO, a.spend, tick.value)))
    }

    private fun stress(bank: Bank, a: BankAction.RunStressTest, inputs: BankingInputs, tick: Tick): Outcome<ActionResult> {
        val scenario = StressScenario.byId(a.scenarioId) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown stress scenario")
        val result = tester.run(bank.snapshot(), scenario, inputs.policyRate(bank.country), inputs.collateralIndex(bank.country))
        book.stress[bank.id] = StressRecord.of(tick.value, result)
        return Outcome.ok(ActionResult("${bank.name}: ${scenario.name} stress test ${if (result.passed) "passed" else "failed"}"))
    }

    private fun dividend(bank: Bank, a: BankAction.PayDividend, supervised: Boolean, tick: Tick, inputs: BankingInputs): Outcome<ActionResult> {
        if (a.amount.raw <= 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "the amount must be positive")
        if (supervised) return Outcome.reject(Rules.REGULATION, "a supervised bank may not pay dividends")
        val capacity = BankView(bank.snapshot(), inputs, params).dividend
        if (a.amount > capacity.amount) return Outcome.reject(Rules.REGULATION, "the dividend is limited to ${capacity.amount.raw / Money.SCALE} (${capacity.limitedBy.label})")
        val paid = Dividends.pay(bank, a.amount)
        if (paid.raw <= 0L) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "the bank has no free cash")
        bank.lastDividend = tick
        return Outcome.ok(ActionResult("${bank.name} paid a dividend", listOfNotNull(BankingIntents.dividend(bank.id, bank.country, bank.owner, paid, tick.value)), listOf(BankEvent.DividendPaid(bank.id, paid, tick.value))))
    }
}
