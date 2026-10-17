package app.many.economytycoon.banking

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.SettlementIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankingIntentsTest {
    private val bank = BankId(7)
    private val country = CountryId(2)
    private val amount = Money.units(1_000)

    private fun all(): List<SettlementIntent?> = listOf(
        BankingIntents.depositInterest(bank, country, Depositor.HOUSEHOLD, amount, 30),
        BankingIntents.depositInterest(bank, country, Depositor.CORPORATE, amount, 30),
        BankingIntents.customerIncome(bank, amount, 30),
        BankingIntents.securityIncome(bank, country, amount, 30),
        BankingIntents.marketFunding(bank, amount, 30),
        BankingIntents.dividend(bank, country, CompanyId(1), amount, 30),
        BankingIntents.tax(bank, country, amount, 30),
        BankingIntents.guaranteeLevy(bank, country, amount, 30),
        BankingIntents.centralBank(bank, country, amount, 30),
        BankingIntents.capitalInjection(bank, CompanyId(1), amount, 30),
        BankingIntents.stateRecapitalisation(bank, country, amount, 30),
        BankingIntents.insuredPayout(bank, country, amount, 30),
        BankingIntents.acquisitionPrice(bank, country, CompanyId(1), amount, 30),
    ) + BankingIntents.operatingCosts(bank, country, amount, amount, 30)

    @Test fun zeroAmountsProduceNoIntents() {
        assertNull(BankingIntents.depositInterest(bank, country, Depositor.HOUSEHOLD, Money.ZERO, 1))
        assertNull(BankingIntents.customerIncome(bank, Money.ZERO, 1))
        assertNull(BankingIntents.dividend(bank, country, null, Money.ZERO, 1))
        assertNull(BankingIntents.centralBank(bank, country, Money.ZERO, 1))
        assertTrue(BankingIntents.operatingCosts(bank, country, Money.ZERO, Money.ZERO, 1).isEmpty())
        assertNull(BankingIntents.tax(bank, country, -amount, 1))
    }

    @Test fun everyIntentCarriesItsAmountAndOnlyTaxMayBeDeferred() {
        for (i in all()) {
            assertNotNull(i)
            assertEquals(i!!.key, amount, i.total)
            if (!i.key.contains(":tax:")) assertTrue(i.key, !i.deferrable)
        }
    }

    @Test fun keysAreUniqueWithinOneMonth() {
        val keys = all().map { it!!.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun keysDifferFromOneMonthToTheNext() {
        val a = BankingIntents.tax(bank, country, amount, 30)!!.key
        val b = BankingIntents.tax(bank, country, amount, 60)!!.key
        assertTrue(a != b)
    }

    @Test fun keysDifferBetweenBanks() {
        assertTrue(BankingIntents.tax(BankId(1), country, amount, 30)!!.key != BankingIntents.tax(BankId(2), country, amount, 30)!!.key)
    }

    @Test fun dividendGoesToTheOwnerOrToHouseholds() {
        assertEquals(LedgerOwner.company(CompanyId(5)), BankingIntents.dividend(bank, country, CompanyId(5), amount, 1)!!.payee)
        assertEquals(LedgerOwner.households(country.value), BankingIntents.dividend(bank, country, null, amount, 1)!!.payee)
        assertEquals(ReasonCode.DIVIDEND, BankingIntents.dividend(bank, country, null, amount, 1)!!.reason)
    }

    @Test fun capitalComesFromTheOwnerOrTheMarket() {
        assertEquals(LedgerOwner.company(CompanyId(5)), BankingIntents.capitalInjection(bank, CompanyId(5), amount, 1)!!.payer)
        assertEquals(LedgerOwner.SYSTEM, BankingIntents.capitalInjection(bank, null, amount, 1)!!.payer)
        assertEquals(LedgerOwner.bank(bank.value), BankingIntents.capitalInjection(bank, null, amount, 1)!!.payee)
    }

    @Test fun centralBankFlowDependsOnWhoOwes() {
        val pays = BankingIntents.centralBank(bank, country, amount, 1)!!
        val earns = BankingIntents.centralBank(bank, country, -amount, 1)!!
        assertEquals(LedgerOwner.bank(bank.value), pays.payer)
        assertEquals(LedgerOwner.bank(bank.value), earns.payee)
    }

    @Test fun acquisitionPriceIsPaidByTheAcquirer() {
        val i = BankingIntents.acquisitionPrice(BankId(3), country, null, amount, 1)!!
        assertEquals(LedgerOwner.bank(3), i.payer)
        assertEquals(LedgerOwner.households(country.value), i.payee)
    }

    @Test fun insuredPayoutIsPaidToHouseholdsByTheState() {
        val i = BankingIntents.insuredPayout(bank, country, amount, 1)!!
        assertEquals(LedgerOwner.government(country), i.payer)
        assertEquals(LedgerOwner.households(country.value), i.payee)
    }

    @Test fun staffCostsGoToHouseholds() {
        val list = BankingIntents.operatingCosts(bank, country, amount, amount, 1)
        assertEquals(2, list.size)
        assertEquals(ReasonCode.WAGES, list[0].reason)
        assertEquals(LedgerOwner.households(country.value), list[0].payee)
    }
}
