package app.many.economytycoon.agenda

import app.many.economytycoon.contracts.ContractSide
import app.many.economytycoon.contracts.ContractTerms
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import app.many.economytycoon.grants.GrantKind
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.present.PresentSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaTest {
    private val wheat = GoodId("wheat")
    private val s = PresentSupport.session("Agenda Works", 12L)
    private val e = s.economy
    private val me = s.selectedCompany!!
    private val region = e.world.regions().first().id
    private val builder = AgendaBuilder(e)
    private fun now() = e.tick.value
    private fun farm(owner: app.many.economytycoon.core.CompanyId) = e.production.grantFacility(owner, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
    private fun rival() = e.companies.all().first { it.id != me }.id
    private val ref get() = e.content.good(wheat).referencePrice.perUnit

    @Test fun aNewCompanyHasAnEmptyAgenda() {
        val a = builder.build(me, now())
        assertTrue(a.items.isEmpty())
        assertEquals(null, a.next)
        assertEquals(Money.ZERO, a.outflow(30))
    }

    @Test fun aLoanSchedulesItsPaymentsAndEnd() {
        e.finance.mint(e.tick, e.finance.bank, Money.units(5_000_000), "test:lender")
        val r = s.issueNow(Command.Borrow(me, Money.units(60_000), 90))
        assertTrue(r.toString(), r is Outcome.Ok)
        val a = builder.build(me, now(), 120)
        val pay = a.of(AgendaKind.LOAN_PAYMENT)
        assertTrue(pay.isNotEmpty())
        assertTrue(pay.all { it.cash!!.raw < 0L && it.risky })
        assertEquals(1, a.of(AgendaKind.LOAN_MATURITY).size)
        assertTrue(a.outflow(120).raw < 0L)
        assertEquals(Money.ZERO, a.inflow(120))
    }

    @Test fun onlyTheNextPaymentIsListed() {
        e.finance.mint(e.tick, e.finance.bank, Money.units(5_000_000), "test:lender")
        s.issueNow(Command.Borrow(me, Money.units(60_000), 90))
        val a = builder.build(me, now(), 120)
        assertEquals(1, a.of(AgendaKind.LOAN_PAYMENT).size)
    }

    @Test fun theHorizonLimitsWhatShows() {
        e.finance.mint(e.tick, e.finance.bank, Money.units(5_000_000), "test:lender")
        s.issueNow(Command.Borrow(me, Money.units(60_000), 90))
        assertTrue(builder.build(me, now(), 1).items.isEmpty())
        assertTrue(builder.build(me, now(), 200).items.isNotEmpty())
    }

    @Test fun itemsComeInDateOrder() {
        e.finance.mint(e.tick, e.finance.bank, Money.units(5_000_000), "test:lender")
        s.issueNow(Command.Borrow(me, Money.units(60_000), 90))
        val a = builder.build(me, now(), 200)
        assertEquals(a.items.map { it.day }.sorted(), a.items.map { it.day })
        assertEquals(a.items.first(), a.next)
    }

    @Test fun aSellingContractShowsAnInflowAndABuyingOneAnOutflow() {
        val mine = farm(me); val theirs = farm(rival())
        val terms = ContractTerms(wheat, Quantity.units(10), Price(ref), 5, 4)
        val sell = (e.contracts.propose(me, rival(), ContractSide.SELLER, mine.id, terms, e.tick) as Outcome.Ok).value
        e.contracts.accept(sell.id, rival(), theirs.id, e.tick)
        val buy = (e.contracts.propose(me, rival(), ContractSide.BUYER, mine.id, terms, e.tick) as Outcome.Ok).value
        e.contracts.accept(buy.id, rival(), theirs.id, e.tick)
        val a = builder.build(me, now(), 30)
        val out = a.of(AgendaKind.DELIVERY_OUT); val inn = a.of(AgendaKind.DELIVERY_IN)
        assertEquals(1, out.size); assertEquals(1, inn.size)
        assertTrue(out.single().cash!!.raw > 0L && out.single().risky)
        assertTrue(inn.single().cash!!.raw < 0L && !inn.single().risky)
        assertEquals(wheat, out.single().good)
    }

    @Test fun tendersAppearWhileBiddingAndAfterAward() {
        val mine = farm(me)
        mine.inventory.add(wheat, Quantity.units(100), Money.units(800))
        val country = e.world.countries().first().id
        val t = (e.tenders.issue(country, wheat, Quantity.units(40), Money(ref.raw * 13 / 10), e.tick) as Outcome.Ok).value
        e.tenders.bid(t.id, me, mine.id, ref, Money.ZERO, e.tick)
        val a = builder.build(me, now(), 90)
        assertEquals(1, a.of(AgendaKind.TENDER_CLOSES).size)
        assertTrue(a.of(AgendaKind.TENDER_DUE).isEmpty())
    }

    @Test fun grantsListClosingAndEndDates() {
        val g = (e.grants.announce(e.world.countries().first().id, GrantKind.JOBS, null, Money.units(100), Money.units(10_000), Money.units(100_000), e.tick) as Outcome.Ok).value
        var a = builder.build(me, now(), 200)
        assertEquals(1, a.of(AgendaKind.GRANT_CLOSES).size)
        e.grants.apply(g.id, me, 0L, e.tick)
        a = builder.build(me, now(), 200)
        assertTrue("applied programmes stop asking to apply", a.of(AgendaKind.GRANT_CLOSES).isEmpty())
    }

    @Test fun byDayGroupsAndSorts() {
        val items = listOf(
            AgendaItem(9, AgendaKind.GRANT_ENDS, "g", ""), AgendaItem(3, AgendaKind.LOAN_PAYMENT, "l", "", Money.units(-5)),
            AgendaItem(3, AgendaKind.DELIVERY_IN, "w", "", Money.units(-2)),
        )
        val a = Agenda(0, 10, items)
        assertEquals(listOf(3L, 9L), a.byDay().keys.toList())
        assertEquals(2, a.byDay().getValue(3L).size)
        assertEquals(Money.units(-7), a.outflow(5))
        assertEquals(Money.ZERO, a.outflow(2))
        assertEquals(1, a.between(5, 10).size)
    }

    @Test fun inflowAndOutflowAreSeparate() {
        val a = Agenda(0, 10, listOf(
            AgendaItem(1, AgendaKind.DELIVERY_OUT, "x", "", Money.units(40), risky = true),
            AgendaItem(2, AgendaKind.LOAN_PAYMENT, "y", "", Money.units(-25), risky = true),
            AgendaItem(3, AgendaKind.TENDER_CLOSES, "z", ""),
        ))
        assertEquals(Money.units(40), a.inflow(10))
        assertEquals(Money.units(-25), a.outflow(10))
        assertEquals(2, a.risky.size)
        assertFalse(a.items.last().risky)
    }

    @Test fun everyKindHasALabel() {
        for (k in AgendaKind.values()) assertTrue(k.label.isNotBlank())
    }
}
