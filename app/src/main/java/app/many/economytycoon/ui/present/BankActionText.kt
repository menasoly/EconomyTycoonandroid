package app.many.economytycoon.ui.present

import app.many.economytycoon.banking.BankAction
import app.many.economytycoon.banking.CapitalInstrument
import app.many.economytycoon.core.Money
import app.many.economytycoon.session.Formatting

/** One-line descriptions of management decisions, for buttons, the command log and confirmations. */
object BankActionText {
    private fun bp(v: Long) = (if (v > 0) "+" else "") + v + " bp"

    fun short(a: BankAction, money: (Money) -> String = { Formatting.compactMoney(it) }): String = when (a) {
        is BankAction.SetDepositPremium -> "Set ${a.product.label.lowercase()} rate premium to ${bp(a.basisPoints)}"
        is BankAction.SetLoanMargin -> "Set loan margin to ${bp(a.basisPoints)}"
        is BankAction.SetLendingStandards -> "Lend only to ${a.minRating.label} or better, at most ${Formatting.percent(a.maxSectorShare)} per sector"
        is BankAction.SetSectorAppetite -> "Set ${a.sector.label.lowercase()} lending appetite to ${Formatting.percent(a.appetite)}"
        is BankAction.SetGrowthAppetite -> "Set growth appetite to ${Formatting.percent(a.appetite)}"
        is BankAction.SetCapitalPolicy -> "Keep ${Formatting.percent(a.cushion)} above requirements and pay out ${Formatting.percent(a.dividendPayout)} of profit"
        is BankAction.SetFundingPolicy -> "Hold ${Formatting.percent(a.securitiesShare)} in securities, cap wholesale funding at ${Formatting.percent(a.wholesaleCap)}, aim for ${Formatting.percent(a.targetLcr)} liquidity cover"
        is BankAction.RaiseCapital -> "Raise ${money(a.amount)} of ${instrument(a.instrument)}"
        is BankAction.BuySecurities -> "Buy ${money(a.amount)} of ${a.kind.label.lowercase()}"
        is BankAction.SellSecurities -> "Sell ${money(a.amount)} of ${a.kind.label.lowercase()}"
        is BankAction.BorrowWholesale -> "Borrow ${money(a.amount)} in the wholesale market"
        is BankAction.RepayWholesale -> "Repay ${money(a.amount)} of wholesale funding"
        is BankAction.RequestEmergencyLiquidity -> "Ask the central bank for ${money(a.amount)} of emergency liquidity"
        is BankAction.ReassureDepositors -> "Spend ${money(a.spend)} reassuring depositors"
        is BankAction.RunStressTest -> "Run the ${a.scenarioId} stress test"
        is BankAction.PayDividend -> "Pay a dividend of ${money(a.amount)}"
        is BankAction.FoundBank -> "Found ${a.name} with ${money(a.capital)} of capital"
    }

    private fun instrument(i: CapitalInstrument) = i.label.lowercase()
}
