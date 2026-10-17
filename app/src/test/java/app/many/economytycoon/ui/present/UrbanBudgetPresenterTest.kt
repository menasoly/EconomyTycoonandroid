package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.urban.ConcessionKind
import app.many.economytycoon.urban.PaymentModel
import app.many.economytycoon.urban.ProjectKind
import app.many.economytycoon.urban.Service
import app.many.economytycoon.urban.TaxRates
import app.many.economytycoon.urban.UrbanCommand
import app.many.economytycoon.urban.UrbanReader
import app.many.economytycoon.urban.UrbanTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrbanBudgetPresenterTest {
    private val T = UrbanTestSupport
    private val money: MoneyText = { "$" + it.wholeUnits() }

    private fun busy(days: Int = 60): app.many.economytycoon.urban.CityView {
        val svc = T.withCity()
        val plan = T.plan(svc)
        plan.budget.restore(plan.budget.rates, Money.units(3_000_000_000), Money.ZERO, 40_000L, Money.ZERO, 0, plan.budget.levelsList(), plan.budget.qualityList())
        val d = plan.districts.first()
        svc.execute(UrbanCommand.StartProject(T.city, ProjectKind.PARK, "Riverside park", d.index, null, null, 0L, true), Tick(0)).getOrThrow()
        svc.execute(UrbanCommand.TenderConcession(T.city, ConcessionKind.TOLL_ROAD, "Toll way", plan.network.roads().first().id, null, PaymentModel.TOLL, 240, 0, Money.units(1_000_000), Money.units(2), Money.ZERO, 100_000L, 0L), Tick(0)).getOrThrow()
        T.run(svc, 0, days)
        return UrbanReader.cityView(svc, T.city, Tick(days.toLong()))!!
    }

    @Test fun `ratings read as words`() {
        assertEquals("Excellent", UrbanBudgetPresenter.ratingLabel(900))
        assertEquals("Good", UrbanBudgetPresenter.ratingLabel(700))
        assertEquals("Fair", UrbanBudgetPresenter.ratingLabel(450))
        assertEquals("Weak", UrbanBudgetPresenter.ratingLabel(250))
        assertEquals("Distressed", UrbanBudgetPresenter.ratingLabel(50))
    }

    @Test fun `a new city shows that no month has closed`() {
        val svc = T.service()
        val c = UrbanReader.cityView(T.withCity().also { it.state.plan(T.city)!!.budget.lastReport.let { } }, T.city, Tick(0))!!
        val m = UrbanBudgetPresenter.build(c.copy(budget = c.budget.copy(hasReport = false)), money)
        assertEquals("No month closed yet", m.tiles[0].value)
        assertEquals(Tone.MUTED, m.tiles[0].tone)
        assertEquals(null, svc.state.plan(T.city))
    }

    @Test fun `the page lists revenue, expenses, services, works and concessions`() {
        val m = UrbanBudgetPresenter.build(busy(1), money)
        assertEquals(5, m.tiles.size)
        assertEquals(7, m.revenue.size)
        assertEquals(5, m.expenses.size)
        assertEquals(Service.values().size, m.services.size)
        assertEquals(3, m.taxFacts.size)
        assertEquals("Riverside park", m.projects.single().name)
        assertTrue(m.projects[0].budget.contains("fixed price"))
        assertEquals("Toll way", m.concessions.single().name)
        assertEquals("Open for bids", m.concessions[0].status)
    }

    @Test fun `the monthly balance is revenue less spending`() {
        val c = busy()
        val m = UrbanBudgetPresenter.build(c, money)
        val net = c.budget.revenue.fold(Money.ZERO) { a, r -> a + r.second } - c.budget.expenses.fold(Money.ZERO) { a, r -> a + r.second }
        assertEquals(money(net), m.tiles[0].value)
    }

    @Test fun `service rows can be raised or cut within limits`() {
        val c = busy()
        val rows = UrbanBudgetPresenter.serviceRows(c.budget, money)
        val row = rows[0]
        assertTrue(row.canRaise && row.canCut)
        val up = UrbanBudgetPresenter.raiseService(T.city, row) as UrbanCommand.SetServiceLevel
        val down = UrbanBudgetPresenter.cutService(T.city, row) as UrbanCommand.SetServiceLevel
        assertEquals(row.level + UrbanBudgetPresenter.STEP, up.level)
        assertEquals(row.level - UrbanBudgetPresenter.STEP, down.level)
        assertEquals(1000, (UrbanBudgetPresenter.raiseService(T.city, row.copy(level = 990)) as UrbanCommand.SetServiceLevel).level)
        assertEquals(0, (UrbanBudgetPresenter.cutService(T.city, row.copy(level = 20)) as UrbanCommand.SetServiceLevel).level)
    }

    @Test fun `tax steps move by a tenth of a percent and stay in range`() {
        val rates = TaxRates()
        val up = UrbanBudgetPresenter.incomeTax(T.city, rates, true) as UrbanCommand.SetTaxRates
        assertEquals(rates.incomePpm + 1_000L, up.rates.incomePpm)
        val down = UrbanBudgetPresenter.propertyTax(T.city, rates, false) as UrbanCommand.SetTaxRates
        assertEquals(rates.propertyPerYearPpm - 1_000L, down.rates.propertyPerYearPpm)
        val top = UrbanBudgetPresenter.incomeTax(T.city, rates.copy(incomePpm = TaxRates.MAX_INCOME_PPM), true) as UrbanCommand.SetTaxRates
        assertEquals(TaxRates.MAX_INCOME_PPM, top.rates.incomePpm)
        val floor = UrbanBudgetPresenter.propertyTax(T.city, rates.copy(propertyPerYearPpm = 300L), false) as UrbanCommand.SetTaxRates
        assertEquals(0L, floor.rates.propertyPerYearPpm)
    }

    @Test fun `borrowing and repaying build commands`() {
        assertEquals(Money.units(5), (UrbanBudgetPresenter.borrow(T.city, Money.units(5)) as UrbanCommand.Borrow).amount)
        assertEquals(Money.units(7), (UrbanBudgetPresenter.repay(T.city, Money.units(7)) as UrbanCommand.Repay).amount)
    }

    @Test fun `project rows warn about trouble`() {
        val c = busy()
        val p = c.projects[0]
        assertNull(UrbanBudgetPresenter.projectRow(p.copy(overrunPpm = 0, delayDays = 0, status = app.many.economytycoon.urban.ProjectStatus.ACTIVE), money).note)
        assertTrue(UrbanBudgetPresenter.projectRow(p.copy(overrunPpm = 300_000L), money).note!!.contains("over"))
        assertTrue(UrbanBudgetPresenter.projectRow(p.copy(delayDays = 40, overrunPpm = 0), money).note!!.contains("late"))
        val stalled = UrbanBudgetPresenter.projectRow(p.copy(status = app.many.economytycoon.urban.ProjectStatus.STALLED), money)
        assertEquals(Tone.BAD, stalled.tone)
        assertTrue(stalled.note!!.startsWith("Stalled"))
    }

    @Test fun `concession rows can be ended only while live`() {
        val c = busy().concessions[0]
        assertFalse(UrbanBudgetPresenter.concessionRow(c, money).canTerminate)
        val live = UrbanBudgetPresenter.concessionRow(c.copy(status = app.many.economytycoon.urban.ConcessionStatus.OPERATING), money)
        assertTrue(live.canTerminate)
        assertEquals(Tone.GOOD, live.tone)
        assertTrue(live.terms.contains("User charges"))
        assertEquals(5, live.facts.size)
    }

    @Test fun `a city in debt shows a warning on the debt tile`() {
        val c = busy()
        val limit = c.budget.debtLimit
        val m = UrbanBudgetPresenter.build(c.copy(budget = c.budget.copy(debt = Money(limit.raw * 9 / 10), debtLimit = limit)), money)
        assertEquals(Tone.BAD, m.tiles[2].tone)
    }
}
