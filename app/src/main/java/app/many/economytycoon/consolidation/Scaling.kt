package app.many.economytycoon.consolidation

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.BalanceSheet
import app.many.economytycoon.finance.IncomeStatement

/** Multiplies money by a share in parts per million without losing precision on large sums. */
internal fun Money.share(ppm: Long): Money = Money(mulDiv(raw, ppm, 1_000_000L))

/** The part of an income statement that belongs to an owner of [ppm] millionths of the company. */
fun IncomeStatement.scaled(ppm: Long): IncomeStatement = copy(
    salesRevenue = salesRevenue.share(ppm), serviceRevenue = serviceRevenue.share(ppm), otherIncome = otherIncome.share(ppm),
    cogs = cogs.share(ppm), wages = wages.share(ppm), energy = energy.share(ppm), rent = rent.share(ppm),
    maintenance = maintenance.share(ppm), transport = transport.share(ppm), marketing = marketing.share(ppm),
    research = research.share(ppm), waste = waste.share(ppm), otherExpenses = otherExpenses.share(ppm),
    depreciation = depreciation.share(ppm), interestExpense = interestExpense.share(ppm),
    interestIncome = interestIncome.share(ppm), incomeTax = incomeTax.share(ppm),
)

/** Adds two statements of the same period line by line; the owner and period come from the receiver. */
operator fun IncomeStatement.plus(o: IncomeStatement): IncomeStatement = copy(
    salesRevenue = salesRevenue + o.salesRevenue, serviceRevenue = serviceRevenue + o.serviceRevenue,
    otherIncome = otherIncome + o.otherIncome, cogs = cogs + o.cogs, wages = wages + o.wages, energy = energy + o.energy,
    rent = rent + o.rent, maintenance = maintenance + o.maintenance, transport = transport + o.transport,
    marketing = marketing + o.marketing, research = research + o.research, waste = waste + o.waste,
    otherExpenses = otherExpenses + o.otherExpenses, depreciation = depreciation + o.depreciation,
    interestExpense = interestExpense + o.interestExpense, interestIncome = interestIncome + o.interestIncome,
    incomeTax = incomeTax + o.incomeTax,
)

fun BalanceSheet.scaled(ppm: Long): BalanceSheet = copy(
    cash = cash.share(ppm), receivables = receivables.share(ppm), inventory = inventory.share(ppm), prepaid = prepaid.share(ppm),
    fixedAssetsGross = fixedAssetsGross.share(ppm), accumulatedDepreciation = accumulatedDepreciation.share(ppm),
    intangibles = intangibles.share(ppm), investments = investments.share(ppm), loansReceivable = loansReceivable.share(ppm),
    payables = payables.share(ppm), wagesPayable = wagesPayable.share(ppm), taxPayable = taxPayable.share(ppm),
    accruedInterest = accruedInterest.share(ppm), deferredRevenue = deferredRevenue.share(ppm),
    shortDebt = shortDebt.share(ppm), longDebt = longDebt.share(ppm), deposits = deposits.share(ppm),
    shareCapital = shareCapital.share(ppm), retainedEarnings = retainedEarnings.share(ppm), dividends = dividends.share(ppm),
    treasuryShares = treasuryShares.share(ppm), earningsToDate = earningsToDate.share(ppm),
)

operator fun BalanceSheet.plus(o: BalanceSheet): BalanceSheet = copy(
    cash = cash + o.cash, receivables = receivables + o.receivables, inventory = inventory + o.inventory, prepaid = prepaid + o.prepaid,
    fixedAssetsGross = fixedAssetsGross + o.fixedAssetsGross, accumulatedDepreciation = accumulatedDepreciation + o.accumulatedDepreciation,
    intangibles = intangibles + o.intangibles, investments = investments + o.investments, loansReceivable = loansReceivable + o.loansReceivable,
    payables = payables + o.payables, wagesPayable = wagesPayable + o.wagesPayable, taxPayable = taxPayable + o.taxPayable,
    accruedInterest = accruedInterest + o.accruedInterest, deferredRevenue = deferredRevenue + o.deferredRevenue,
    shortDebt = shortDebt + o.shortDebt, longDebt = longDebt + o.longDebt, deposits = deposits + o.deposits,
    shareCapital = shareCapital + o.shareCapital, retainedEarnings = retainedEarnings + o.retainedEarnings,
    dividends = dividends + o.dividends, treasuryShares = treasuryShares + o.treasuryShares,
    earningsToDate = earningsToDate + o.earningsToDate,
)
