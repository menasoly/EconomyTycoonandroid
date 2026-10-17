package app.many.economytycoon.banking

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditRating

enum class CapitalInstrument(val label: String) { COMMON_EQUITY("Common equity"), ADDITIONAL_TIER1("Additional tier 1"), TIER2("Tier 2 subordinated debt") }

/**
 * Something the management of a bank decides. The player issues these for a bank they run and the planners issue them
 * for computer-controlled banks; both go through [BankingService.apply] and are checked in the same way.
 */
sealed class BankAction {
    abstract val bank: BankId

    data class SetDepositPremium(override val bank: BankId, val product: DepositProduct, val basisPoints: Long) : BankAction()
    data class SetLoanMargin(override val bank: BankId, val basisPoints: Long) : BankAction()
    data class SetLendingStandards(override val bank: BankId, val minRating: CreditRating, val maxSectorShare: Rate) : BankAction()
    data class SetSectorAppetite(override val bank: BankId, val sector: LoanSector, val appetite: Rate) : BankAction()
    data class SetGrowthAppetite(override val bank: BankId, val appetite: Rate) : BankAction()
    data class SetCapitalPolicy(override val bank: BankId, val cushion: Rate, val dividendPayout: Rate) : BankAction()
    data class SetFundingPolicy(override val bank: BankId, val securitiesShare: Rate, val wholesaleCap: Rate, val targetLcr: Rate) : BankAction()
    data class RaiseCapital(override val bank: BankId, val instrument: CapitalInstrument, val amount: Money) : BankAction()
    data class BuySecurities(override val bank: BankId, val kind: SecurityKind, val amount: Money) : BankAction()
    data class SellSecurities(override val bank: BankId, val kind: SecurityKind, val amount: Money) : BankAction()
    data class BorrowWholesale(override val bank: BankId, val amount: Money) : BankAction()
    data class RepayWholesale(override val bank: BankId, val amount: Money) : BankAction()
    data class RequestEmergencyLiquidity(override val bank: BankId, val amount: Money) : BankAction()
    data class ReassureDepositors(override val bank: BankId, val spend: Money) : BankAction()
    data class RunStressTest(override val bank: BankId, val scenarioId: String) : BankAction()
    data class PayDividend(override val bank: BankId, val amount: Money) : BankAction()

    /** Starts a new bank. [bank] is ignored; the service allocates the id. */
    data class FoundBank(val name: String, val country: CountryId, val owner: CompanyId?, val capital: Money, val player: Boolean) : BankAction() {
        override val bank: BankId get() = BankId(0L)
    }
}
