package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BudgetTest {
    private val p = UrbanParams()

    private fun inputs(
        population: Long = 100_000L, wages: Money = Money.units(40_000_000), assessed: Money = Money.units(12_000_000_000), business: Long = 400_000L,
        transit: Money = Money.ZERO, availability: Money = Money.ZERO, fares: Money = Money.ZERO,
    ) = BudgetInputs(population, wages, assessed, business, Money.units(15), fares, Money.ZERO, Money.units(10_000), transit, availability)

    @Test fun `default rates are valid and limits are enforced`() {
        val r = TaxRates()
        assertEquals(25_000L, r.incomePpm)
        try { TaxRates(incomePpm = TaxRates.MAX_INCOME_PPM + 1); fail() } catch (e: IllegalArgumentException) { }
        try { TaxRates(propertyPerYearPpm = -1L); fail() } catch (e: IllegalArgumentException) { }
        try { TaxRates(businessPerSqm = Money.units(-1)); fail() } catch (e: IllegalArgumentException) { }
    }

    @Test fun `service levels change cost and are bounded`() {
        val b = MunicipalBudget()
        val base = b.serviceCost(Service.POLICE, 100_000L)
        assertTrue(b.setLevel(Service.POLICE, 1000).isOk)
        assertTrue(b.serviceCost(Service.POLICE, 100_000L) > base)
        assertEquals(Rules.INVALID_ARGUMENT, b.setLevel(Service.POLICE, 1001).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, b.setLevel(Service.POLICE, -1).rejectionOrNull()?.rule)
        assertEquals(1000, b.level(Service.POLICE))
        b.setLevel(Service.POLICE, 0)
        assertTrue(b.serviceCost(Service.POLICE, 100_000L) < base)

    }

    @Test fun `total service cost is the sum of services`() {
        val b = MunicipalBudget()
        assertEquals(Money.sum(Service.values().map { b.serviceCost(it, 50_000L) }), b.totalServiceCost(50_000L))
        assertEquals(Money.ZERO, b.totalServiceCost(0L))
    }

    @Test fun `a month brings in taxes and pays for services`() {
        val b = MunicipalBudget()
        val before = b.cash
        val r = b.settleMonth(inputs(), Money.ZERO, p)
        assertTrue(r.totalRevenue.isPositive)
        assertEquals(before + r.totalRevenue - r.expenses.getValue(ExpenseLine.SERVICES) - r.expenses.getValue(ExpenseLine.TRANSIT) - r.expenses.getValue(ExpenseLine.DEBT_SERVICE) - r.expenses.getValue(ExpenseLine.AVAILABILITY), b.cash)
        assertEquals(RevenueLine.values().toList(), r.revenue.keys.toList())
        assertEquals(Scale.PPM, r.fundingRatioPpm)
    }

    @Test fun `income tax is a share of wages`() {
        val b = MunicipalBudget()
        val r = b.settleMonth(inputs(wages = Money.units(1_000_000)), Money.ZERO, p)
        assertEquals(Money.units(1_000_000).raw * 25_000 / 1_000_000, r.revenue.getValue(RevenueLine.INCOME_TAX).raw)
    }

    @Test fun `property tax is a share of assessed value over twelve months`() {
        val b = MunicipalBudget()
        val r = b.settleMonth(inputs(assessed = Money.units(1_200_000_000)), Money.ZERO, p)
        assertEquals(Money.units(1_200_000_000).raw * 9_000 / 1_000_000 / 12, r.revenue.getValue(RevenueLine.PROPERTY_TAX).raw)
    }

    @Test fun `higher taxes bring in more`() {
        val low = MunicipalBudget(); val high = MunicipalBudget()
        high.setRates(TaxRates(propertyPerYearPpm = 20_000L, incomePpm = 50_000L, businessPerSqm = Money.units(2)))
        assertTrue(high.settleMonth(inputs(), Money.ZERO, p).totalRevenue > low.settleMonth(inputs(), Money.ZERO, p).totalRevenue)
    }

    @Test fun `a city that cannot pay borrows within its limit`() {
        val b = MunicipalBudget()
        b.cash = Money.ZERO
        b.settleMonth(inputs(), Money.ZERO, p)
        val r = b.settleMonth(inputs(wages = Money.ZERO, assessed = Money.ZERO, business = 0L), Money.ZERO, p)
        assertTrue(r.borrowed.isPositive || r.fundingRatioPpm < Scale.PPM)
        assertTrue(b.debt <= b.debtLimit(p))
        assertFalse(b.cash.isNegative)
    }

    @Test fun `unfunded services lose quality`() {
        val b = MunicipalBudget()
        b.cash = Money.ZERO
        val starved = inputs(wages = Money.ZERO, assessed = Money.ZERO, business = 0L).copy(transferPerResident = Money.ZERO, fees = Money.ZERO)
        repeat(8) { b.settleMonth(starved, Money.ZERO, p.copy(debtLimitMonthsOfRevenue = 0)) }
        assertTrue(b.quality(Service.EDUCATION) < 500)
        assertTrue(b.lastReport!!.fundingRatioPpm < Scale.PPM)
        assertTrue(b.consecutiveDeficitMonths >= 1)
    }

    @Test fun `quality follows the funded level with a lag`() {
        val b = MunicipalBudget()
        b.setLevel(Service.FIRE, 900)
        b.settleMonth(inputs(), Money.ZERO, p)
        val one = b.quality(Service.FIRE)
        assertTrue(one in 501..899)
        repeat(30) { b.settleMonth(inputs(), Money.ZERO, p) }
        assertTrue(b.quality(Service.FIRE) in one..900)
    }

    @Test fun `borrowing and repaying respect limits`() {
        val b = MunicipalBudget()
        b.settleMonth(inputs(), Money.ZERO, p)
        val limit = b.debtLimit(p)
        assertTrue(limit.isPositive)
        assertEquals(Rules.INVALID_ARGUMENT, b.borrow(Money.ZERO, p).rejectionOrNull()?.rule)
        assertEquals(Rules.CREDIT_LIMIT, b.borrow(limit + Money.units(1), p).rejectionOrNull()?.rule)
        val cash = b.cash
        assertTrue(b.borrow(Money.units(1_000_000), p).isOk)
        assertEquals(cash + Money.units(1_000_000), b.cash)
        assertEquals(Rules.INVALID_ARGUMENT, b.repay(Money.units(2_000_000)).rejectionOrNull()?.rule)
        assertTrue(b.repay(Money.units(400_000)).isOk)
        assertEquals(Money.units(600_000), b.debt)
        b.cash = Money.units(10)
        assertEquals(Rules.INSUFFICIENT_FUNDS, b.repay(Money.units(100)).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, b.repay(Money.units(-5)).rejectionOrNull()?.rule)
    }

    @Test fun `debt costs interest each month`() {
        val b = MunicipalBudget()
        b.settleMonth(inputs(), Money.ZERO, p)
        b.borrow(Money.units(50_000_000), p)
        val r = b.settleMonth(inputs(), Money.ZERO, p)
        assertTrue(r.expenses.getValue(ExpenseLine.DEBT_SERVICE).isPositive)
        assertEquals(b.interestRatePpm() > b.annualBaseRatePpm - 1, true)
    }

    @Test fun `credit rating falls with debt and deficits`() {
        val b = MunicipalBudget()
        assertEquals(700, b.rating())
        b.settleMonth(inputs(), Money.ZERO, p)
        val clean = b.rating()
        b.borrow(Money.units(300_000_000), p.copy(debtLimitMonthsOfRevenue = 10_000))
        assertTrue(b.rating() < clean)
        b.consecutiveDeficitMonths = 10
        assertTrue(b.rating() < clean - 100)
        assertTrue(b.interestRatePpm() > MunicipalBudget().apply { settleMonth(inputs(), Money.ZERO, p) }.interestRatePpm())
    }

    @Test fun `capital spending needs cash`() {
        val b = MunicipalBudget()
        val cash = b.cash
        assertTrue(b.spendCapital(Money.units(1_000)))
        assertEquals(cash - Money.units(1_000), b.cash)
        assertFalse(b.spendCapital(cash))
        assertFalse(b.spendCapital(Money.units(-1)))
    }

    @Test fun `one off receipts add cash and negative ones are ignored`() {
        val b = MunicipalBudget()
        val cash = b.cash
        b.receive(Money.units(500))
        b.receive(Money.units(-900))
        assertEquals(cash + Money.units(500), b.cash)
    }

    @Test fun `spendable cash leaves a month of running costs`() {
        val b = MunicipalBudget()
        b.cash = Money.units(900_000_000)
        val reserve = b.totalServiceCost(100_000L) + Money.units(1_000)
        assertEquals(b.cash - reserve, b.spendable(100_000L, Money.units(1_000)))
        b.cash = Money.units(5)
        assertEquals(Money.ZERO, b.spendable(100_000L, Money.ZERO))
    }

    @Test fun `public works can lift service quality up to the cap`() {
        val b = MunicipalBudget()
        b.boostQuality(Service.EDUCATION, 120)
        assertEquals(620, b.quality(Service.EDUCATION))
        b.boostQuality(Service.EDUCATION, 9_000)
        assertEquals(1000, b.quality(Service.EDUCATION))
    }

    @Test fun `service effects follow police sanitation and parks quality`() {
        val b = MunicipalBudget()
        b.boostQuality(Service.POLICE, 100)
        val e = b.effects()
        assertEquals(600, e.policePermille)
        assertEquals(500, e.sanitationPermille)
        assertEquals(500, e.parksPermille)
    }

    @Test fun `restore reproduces the budget exactly`() {
        fun h(b: MunicipalBudget) = CanonicalHasher().also { b.hashInto(it) }.finish()
        val a = MunicipalBudget()
        a.setLevel(Service.HEALTH, 800)
        repeat(3) { a.settleMonth(inputs(), Money.ZERO, p) }
        val b = MunicipalBudget()
        b.restore(a.rates, a.cash, a.debt, a.annualBaseRatePpm, a.lastRevenue, a.consecutiveDeficitMonths, a.levelsList(), a.qualityList())
        assertEquals(h(a), h(b))
        try { b.restore(a.rates, a.cash, a.debt, 1L, a.lastRevenue, 0, listOf(1), listOf(1)); fail() } catch (e: IllegalArgumentException) { }
    }

    @Test fun `the report balance is revenue less expenses`() {
        val r = MunicipalBudget().settleMonth(inputs(transit = Money.units(100_000), fares = Money.units(30_000)), Money.units(250_000), p)
        assertEquals(r.totalRevenue - r.totalExpenses, r.balance)
        assertEquals(Money.units(250_000), r.expenses.getValue(ExpenseLine.CAPITAL))
        assertEquals(Money.units(100_000), r.expenses.getValue(ExpenseLine.TRANSIT))
    }
}
