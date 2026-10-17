package app.many.economytycoon.banking

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.finance.SettlementPriority
import app.many.economytycoon.finance.TaxIntents

/**
 * Requests to move money in the real ledger. The banking engine keeps its own books and only describes the flows that
 * touch the rest of the economy: interest to depositors, dividends to owners, tax and levies to the state. The runner
 * hands the intents to the settlement engine; nothing here posts anything.
 *
 * Customers that are not individually modelled (borrowers, corporate depositors) are represented by
 * [LedgerOwner.SYSTEM] and households by the households account of the bank's country.
 */
object BankingIntents {
    private fun owner(bank: BankId) = LedgerOwner.bank(bank.value)
    private fun households(country: CountryId) = LedgerOwner.households(country.value)

    private fun simple(
        key: String, payer: LedgerOwner, payee: LedgerOwner, amount: Money, priority: SettlementPriority, reason: ReasonCode,
        debit: Chart, credit: Chart, memo: String,
    ): SettlementIntent? =
        if (amount.raw <= 0L) null else SettlementIntent.simple(key, payer, payee, amount, priority, reason, debit, credit, deferrable = false, memo = memo)

    /** Interest the bank pays on deposits: to households, or to the pool of corporate depositors. */
    fun depositInterest(bank: BankId, country: CountryId, depositor: Depositor, amount: Money, tick: Long): SettlementIntent? {
        val payee = if (depositor == Depositor.HOUSEHOLD) households(country) else LedgerOwner.SYSTEM
        return simple("bank:${bank.value}:deposit-interest:${depositor.name.lowercase()}:$tick", owner(bank), payee, amount,
            SettlementPriority.OTHER, ReasonCode.LOAN_INTEREST, Chart.INTEREST_EXPENSE, Chart.INTEREST_INCOME, "deposit interest, ${depositor.label.lowercase()}")
    }

    /** Interest and fees paid by borrowers and account holders. */
    fun customerIncome(bank: BankId, amount: Money, tick: Long): SettlementIntent? =
        simple("bank:${bank.value}:customer-income:$tick", LedgerOwner.SYSTEM, owner(bank), amount, SettlementPriority.OTHER,
            ReasonCode.LOAN_INTEREST, Chart.INTEREST_EXPENSE, Chart.INTEREST_INCOME, "interest and fees from customers")

    /** Coupons on government securities the bank holds. */
    fun securityIncome(bank: BankId, country: CountryId, amount: Money, tick: Long): SettlementIntent? =
        simple("bank:${bank.value}:security-income:$tick", LedgerOwner.government(country), owner(bank), amount, SettlementPriority.OTHER,
            ReasonCode.LOAN_INTEREST, Chart.INTEREST_EXPENSE, Chart.INTEREST_INCOME, "coupons on securities")

    /** Interest on wholesale funding, capital instruments and interbank borrowing, paid to the market. */
    fun marketFunding(bank: BankId, amount: Money, tick: Long): SettlementIntent? =
        simple("bank:${bank.value}:market-funding:$tick", owner(bank), LedgerOwner.SYSTEM, amount, SettlementPriority.SECURED_DEBT,
            ReasonCode.LOAN_INTEREST, Chart.INTEREST_EXPENSE, Chart.INTEREST_INCOME, "interest on market funding")

    /** Wages and running costs of the bank. */
    fun operatingCosts(bank: BankId, country: CountryId, wages: Money, other: Money, tick: Long): List<SettlementIntent> = listOfNotNull(
        simple("bank:${bank.value}:wages:$tick", owner(bank), households(country), wages, SettlementPriority.WAGES, ReasonCode.WAGES,
            Chart.WAGES_EXPENSE, Chart.REVENUE_OTHER, "staff costs"),
        simple("bank:${bank.value}:running-costs:$tick", owner(bank), LedgerOwner.SYSTEM, other, SettlementPriority.SUPPLIERS, ReasonCode.PURCHASE,
            Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, "running costs"),
    )

    /** Dividend to the owning company, or to the households account for a bank nobody in the game owns. */
    fun dividend(bank: BankId, country: CountryId, owner: CompanyId?, amount: Money, tick: Long): SettlementIntent? {
        val payee = if (owner != null) LedgerOwner.company(owner) else households(country)
        return simple("bank:${bank.value}:dividend:$tick", owner(bank), payee, amount, SettlementPriority.OTHER, ReasonCode.DIVIDEND,
            Chart.DIVIDENDS, Chart.REVENUE_OTHER, "bank dividend")
    }

    fun tax(bank: BankId, country: CountryId, amount: Money, tick: Long): SettlementIntent? =
        if (amount.raw <= 0L) null else TaxIntents.corporate("bank:${bank.value}:tax:$tick", owner(bank), LedgerOwner.government(country), amount)

    /** Levy for the guarantee fund, which the state holds. */
    fun guaranteeLevy(bank: BankId, country: CountryId, amount: Money, tick: Long): SettlementIntent? =
        simple("bank:${bank.value}:levy:$tick", owner(bank), LedgerOwner.government(country), amount, SettlementPriority.STATUTORY, ReasonCode.FEE,
            Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, "deposit guarantee levy")

    /** Net interest on central bank facilities and reserves: positive when the bank pays, negative when it earns. */
    fun centralBank(bank: BankId, country: CountryId, net: Money, tick: Long): SettlementIntent? = when {
        net.raw > 0L -> simple("bank:${bank.value}:central-bank:$tick", owner(bank), LedgerOwner.government(country), net, SettlementPriority.SECURED_DEBT,
            ReasonCode.LOAN_INTEREST, Chart.INTEREST_EXPENSE, Chart.INTEREST_INCOME, "central bank facility interest")
        net.raw < 0L -> simple("bank:${bank.value}:central-bank:$tick", LedgerOwner.government(country), owner(bank), -net, SettlementPriority.OTHER,
            ReasonCode.LOAN_INTEREST, Chart.INTEREST_EXPENSE, Chart.INTEREST_INCOME, "interest on reserves")
        else -> null
    }

    /** New capital paid in by an owning company, or by investors in general when nobody in the game owns the bank. */
    fun capitalInjection(bank: BankId, from: CompanyId?, amount: Money, tick: Long): SettlementIntent? {
        val payer = if (from != null) LedgerOwner.company(from) else LedgerOwner.SYSTEM
        return simple("bank:${bank.value}:capital:$tick", payer, owner(bank), amount, SettlementPriority.OTHER, ReasonCode.CAPITAL,
            Chart.INVESTMENTS, Chart.SHARE_CAPITAL, "capital injection")
    }

    /** The state puts capital into a bank it has stepped in to rescue. */
    fun stateRecapitalisation(bank: BankId, country: CountryId, amount: Money, tick: Long): SettlementIntent? =
        simple("bank:${bank.value}:state-capital:$tick", LedgerOwner.government(country), owner(bank), amount, SettlementPriority.OTHER, ReasonCode.CAPITAL,
            Chart.INVESTMENTS, Chart.SHARE_CAPITAL, "state recapitalisation")

    /** The guarantee fund pays insured depositors of a failed bank. */
    fun insuredPayout(failed: BankId, country: CountryId, amount: Money, tick: Long): SettlementIntent? =
        simple("bank:${failed.value}:payout:$tick", LedgerOwner.government(country), households(country), amount, SettlementPriority.OTHER,
            ReasonCode.TRANSFER, Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, "deposit guarantee payout")

    /** Price an acquiring bank pays the former owners of a bank it takes over. */
    fun acquisitionPrice(acquirer: BankId, country: CountryId, to: CompanyId?, amount: Money, tick: Long): SettlementIntent? {
        val payee = if (to != null) LedgerOwner.company(to) else households(country)
        return simple("bank:${acquirer.value}:acquisition:$tick", owner(acquirer), payee, amount, SettlementPriority.OTHER, ReasonCode.TRANSFER,
            Chart.INVESTMENTS, Chart.REVENUE_OTHER, "price for the acquired bank")
    }
}
