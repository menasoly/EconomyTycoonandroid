package app.many.economytycoon.simulation

import app.many.economytycoon.bonds.Bond
import app.many.economytycoon.bonds.BondEvent
import app.many.economytycoon.bonds.BondStatus
import app.many.economytycoon.bonds.DEALER
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.LedgerOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BondRunnerTest {
    private class Rig {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val run = e.bondRunner
        val country = e.world.countries().first()
        val gov = LedgerOwner.government(country.id)
        fun company(name: String, p: Long): CompanyId =
            CompanyId((sim.execute(Actor.Player(p), Command.FoundCompany(name, country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        fun cash(c: CompanyId) = e.finance.ledger.cash(LedgerOwner.company(c))
        fun bal(c: CompanyId, chart: Chart) = e.finance.ledger.balance(LedgerOwner.company(c), chart)
        fun bond(tenor: Int = 360, coupon: Long = 60_000L, units: Long = 500): Bond =
            (e.bonds.issue(country.id, tenor, coupon, units, e.tick) as Outcome.Ok).value
        /** An issue that has closed with [units] bought by [by]. */
        fun placed(by: CompanyId, units: Long = 100, tenor: Int = 360, coupon: Long = 60_000L): Bond {
            val b = bond(tenor, coupon)
            assertTrue(run.subscribe(by, b.id, units, e.tick) is Outcome.Ok)
            e.tick = b.subscriptionEnds
            run.run(e.tick)
            assertEquals(BondStatus.OUTSTANDING, b.status)
            return b
        }
        fun at(t: Long) { e.tick = Tick(t) }
        fun balanced() = assertEquals(0L, e.finance.ledger.trialBalance())
    }

    @Test fun subscribingMovesFaceValueToTheTreasuryAndBooksTheDebt() {
        val r = Rig(); val c = r.company("Lender Works", 1); val b = r.bond()
        val cash = r.cash(c); val treasury = r.e.finance.ledger.cash(r.gov)
        val paid = (r.run.subscribe(c, b.id, 40, r.e.tick) as Outcome.Ok).value
        assertEquals(Money(b.faceEach.raw * 40), paid)
        assertEquals(cash - paid, r.cash(c)); assertEquals(treasury + paid, r.e.finance.ledger.cash(r.gov))
        assertEquals(paid, r.bal(c, Chart.INVESTMENTS))
        assertEquals(-paid, r.e.finance.ledger.balance(r.gov, Chart.DEBT_LONG))
        assertEquals(40L, b.heldBy(c)); assertEquals(40L, b.unitsSold); assertEquals(460L, b.unitsLeft)
        r.balanced()
    }

    @Test fun badSubscriptionsAreRefusedWithoutSideEffects() {
        val r = Rig(); val c = r.company("Lender Works", 1); val b = r.bond()
        val cash = r.cash(c)
        assertEquals(Rules.UNKNOWN_ENTITY, (r.run.subscribe(c, app.many.economytycoon.core.BondId(99), 1, r.e.tick) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, (r.run.subscribe(CompanyId(99), b.id, 1, r.e.tick) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INSUFFICIENT_STOCK, (r.run.subscribe(c, b.id, 501, r.e.tick) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INSUFFICIENT_FUNDS, (r.run.subscribe(c, b.id, 400, r.e.tick) as Outcome.Rejected).rejection.rule)
        assertEquals(cash, r.cash(c)); assertEquals(0L, b.unitsSold)
        r.balanced()
    }

    @Test fun anIssueClosesIntoOutstandingBondsOrIsCancelled() {
        val r = Rig(); val c = r.company("Lender Works", 1)
        val sold = r.bond(); val unsold = r.bond()
        r.run.subscribe(c, sold.id, 60, r.e.tick)
        r.at(sold.subscriptionEnds.value - 1)
        assertTrue(r.run.run(r.e.tick).none { it is BondEvent.Placed || it is BondEvent.NoSale })
        r.at(sold.subscriptionEnds.value)
        val events = r.run.run(r.e.tick)
        assertTrue(events.any { it is BondEvent.Placed && it.bond == sold.id && it.units == 60L })
        assertTrue(events.any { it is BondEvent.NoSale && it.bond == unsold.id })
        assertEquals(BondStatus.CANCELLED, unsold.status)
        assertEquals(Rules.WRONG_STATE, (r.run.subscribe(c, sold.id, 1, r.e.tick) as Outcome.Rejected).rejection.rule)
    }

    @Test fun couponsArePaidMonthlyEvenWhenTheTreasuryIsEmpty() {
        val r = Rig(); val c = r.company("Lender Works", 1); val b = r.placed(c, units = 100)
        val per = r.e.bonds.couponPerUnit(b); val expected = Money(per.raw * 100)
        val cash = r.cash(c)
        r.at(b.subscriptionEnds.value + 30)
        val events = r.run.run(r.e.tick)
        val paid = events.filterIsInstance<BondEvent.Coupon>().single()
        assertEquals(expected, paid.paid)
        assertEquals(cash + expected, r.cash(c))
        assertEquals(expected, r.e.finance.ledger.balance(LedgerOwner.company(c), Chart.INTEREST_INCOME).let { Money(-it.raw) })
        assertEquals(expected, b.couponsPaid)
        r.balanced()
        r.at(b.subscriptionEnds.value + 31)
        assertTrue(r.run.run(r.e.tick).none { it is BondEvent.Coupon })
    }

    @Test fun maturityPaysTheLastCouponAndTheFaceValue() {
        val r = Rig(); val c = r.company("Lender Works", 1); val b = r.placed(c, units = 100, tenor = 180)
        val face = Money(b.faceEach.raw * 100)
        r.at(b.maturesAt.value - 1)
        val before = r.cash(c)
        for (t in b.subscriptionEnds.value + 1..b.maturesAt.value - 1) { r.at(t); r.run.run(r.e.tick) }
        val afterCoupons = r.cash(c)
        assertTrue(afterCoupons > before)
        r.at(b.maturesAt.value)
        val events = r.run.run(r.e.tick)
        val redeemed = events.filterIsInstance<BondEvent.Redeemed>().single()
        assertEquals(face, redeemed.repaid)
        assertEquals(BondStatus.REDEEMED, b.status)
        assertEquals(Money.ZERO, r.bal(c, Chart.INVESTMENTS))
        assertEquals(Money.ZERO, r.e.finance.ledger.balance(r.gov, Chart.DEBT_LONG))
        assertTrue(r.cash(c) >= afterCoupons + face)
        assertEquals(0L, b.unitsInCirculation())
        r.balanced()
    }

    @Test fun anInvestorWholeUnderTheCouponsEndsAheadOfWhereItStarted() {
        val r = Rig(); val c = r.company("Lender Works", 1); val start = r.cash(c)
        val b = r.placed(c, units = 200, tenor = 180)
        for (t in b.subscriptionEnds.value..b.maturesAt.value) { r.at(t); r.run.run(r.e.tick) }
        assertTrue(r.cash(c) > start)
        r.balanced()
    }

    @Test fun theDealerIsFundedOnFirstUseAndPaysItsBid() {
        val r = Rig(); val c = r.company("Lender Works", 1); val b = r.placed(c, units = 100)
        assertEquals(Money.ZERO, r.e.finance.ledger.cash(r.run.dealer))
        val cash = r.cash(c)
        val q = (r.run.trade(c, b.id, 40, false, r.e.tick) as Outcome.Ok).value
        assertFalse(q.buy)
        assertEquals(cash + q.total, r.cash(c))
        assertEquals(r.e.bonds.params.dealerReserve - q.total, r.e.finance.ledger.cash(r.run.dealer))
        assertEquals(60L, b.heldBy(c)); assertEquals(40L, b.heldBy(DEALER))
        assertTrue(r.e.bonds.dealerFunded)
        r.balanced()
    }

    @Test fun sellingAndBuyingBackCostsTheSpread() {
        val r = Rig(); val c = r.company("Lender Works", 1); val b = r.placed(c, units = 100)
        val start = r.cash(c)
        r.run.trade(c, b.id, 100, false, r.e.tick)
        r.run.trade(c, b.id, 100, true, r.e.tick)
        assertEquals(100L, b.heldBy(c)); assertEquals(0L, b.heldBy(DEALER))
        val loss = start - r.cash(c)
        assertTrue(loss.raw > 0L)
        val spread = Money(b.faceEach.raw * 100 * r.e.bonds.params.dealerSpreadPpm * 2 / 1_000_000L)
        assertTrue(loss.raw in spread.raw - 100L..spread.raw + 100L)
        r.balanced()
    }

    @Test fun theCostBasisFollowsTheUnitsThroughTrades() {
        val r = Rig(); val c = r.company("Lender Works", 1); val b = r.placed(c, units = 100)
        val face = Money(b.faceEach.raw * 100)
        assertEquals(face, b.costOf(c))
        r.run.trade(c, b.id, 25, false, r.e.tick)
        assertEquals(Money(face.raw * 75 / 100), b.costOf(c))
        assertTrue(b.costOf(DEALER).raw > 0L)
    }

    @Test fun tradesAreRefusedWhenThereIsNothingToTrade() {
        val r = Rig(); val c = r.company("Lender Works", 1); val b = r.placed(c, units = 10)
        assertEquals(Rules.INSUFFICIENT_STOCK, (r.run.trade(c, b.id, 11, false, r.e.tick) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INSUFFICIENT_STOCK, (r.run.trade(c, b.id, 1, true, r.e.tick) as Outcome.Rejected).rejection.rule)
        val open = r.bond()
        assertEquals(Rules.WRONG_STATE, (r.run.trade(c, open.id, 1, true, r.e.tick) as Outcome.Rejected).rejection.rule)
        r.balanced()
    }

    @Test fun theDealerHasALimitAndAPurse() {
        val r = Rig(); val c = r.company("Lender Works", 1); val b = r.placed(c, units = 100)
        r.e.bonds.allot(b, DEALER, r.e.bonds.params.dealerMaxUnits - 5, Money.ZERO)
        assertEquals(Rules.LIMIT_EXCEEDED, (r.run.trade(c, b.id, 10, false, r.e.tick) as Outcome.Rejected).rejection.rule)
        assertTrue(r.run.trade(c, b.id, 5, false, r.e.tick) is Outcome.Ok)
    }

    @Test fun risingRatesMakeHeldBondsWorthLess() {
        val r = Rig(); val c = r.company("Lender Works", 1); val b = r.placed(c, units = 100, coupon = 50_000L)
        r.at(b.subscriptionEnds.value + 5)
        val before = r.run.pricePpm(b, r.e.tick)
        r.e.macro.of(r.country.id).bank.setManual(180_000L)
        repeat(30) { r.e.macro.of(r.country.id).bank.review(app.many.economytycoon.macro.PolicyInputs(0, 0, 0)) }
        assertTrue(r.run.pricePpm(b, r.e.tick) < before)
        assertTrue(r.run.worth(c, r.e.tick) < Money(b.faceEach.raw * 100))
    }

    @Test fun priceAtIssueIsNearParBecauseCouponTracksTheMarket() {
        val r = Rig(); val b = r.bond(coupon = r.e.bonds.couponFor(r.run.yieldFor(r.country.id, 360)))
        val p = r.run.pricePpm(b, b.subscriptionEnds)
        assertTrue("price $p", p in 990_000L..1_030_000L)
    }

    @Test fun governmentsIssueOnTheirDayAndNeverTwiceAtOnce() {
        val r = Rig(); val p = r.e.bonds.params
        var issued = 0
        for (t in 1L..p.issueEvery.toLong() * 6) {
            r.at(t)
            val events = r.run.run(r.e.tick).filterIsInstance<BondEvent.Issued>()
            issued += events.size
            for (c in r.e.world.countries()) assertTrue(r.e.bonds.open().count { it.country == c.id } <= 1)
        }
        assertTrue("issued $issued", issued >= 1)
        for (b in r.e.bonds.all()) {
            assertTrue(b.unitsOffered in p.minUnits..p.maxUnits)
            assertTrue(b.tenorDays in p.tenors)
            assertTrue(b.couponPpm > 0L)
        }
    }

    @Test fun theDebtCeilingStopsFurtherIssues() {
        val r = Rig()
        val ceiling = r.run.debtCeiling(r.country.id)
        val face = r.e.bonds.params.faceValue
        assertTrue(ceiling.raw > 0L)
        val units = ceiling.raw / face.raw
        val b = r.e.bonds.issue(r.country.id, 360, 50_000L, minOf(units, r.e.bonds.params.maxUnits), r.e.tick) as Outcome.Ok
        r.e.bonds.addSold(b.value, b.value.unitsOffered)
        r.e.bonds.closeSubscriptions(b.value.subscriptionEnds) { Money.ZERO }
        // Pile up debt until the room is below the smallest issue.
        while ((ceiling - r.e.bonds.owed(r.country.id)).raw / face.raw >= r.e.bonds.params.minUnits) {
            val x = (r.e.bonds.issue(r.country.id, 360, 50_000L, r.e.bonds.params.maxUnits, r.e.tick) as Outcome.Ok).value
            r.e.bonds.addSold(x, x.unitsOffered)
            r.e.bonds.closeSubscriptions(x.subscriptionEnds) { Money.ZERO }
        }
        val before = r.e.bonds.all().size
        for (t in 1L..r.e.bonds.params.issueEvery.toLong() * 4) { r.at(t); r.run.run(r.e.tick) }
        assertTrue(r.e.bonds.all().none { it.isOpen })
        assertTrue(r.e.bonds.all().size <= before)
    }

    @Test fun aWoundUpCompanysBondsGoToTheDealer() {
        val r = Rig(); val c = r.company("Lender Works", 1); val b = r.placed(c, units = 30)
        r.run.forget(c)
        assertEquals(0L, b.heldBy(c)); assertEquals(30L, b.heldBy(DEALER))
        r.at(b.maturesAt.value)
        r.run.run(r.e.tick)
        assertEquals(BondStatus.REDEEMED, b.status)
        r.balanced()
    }

    @Test fun theCommandsGoThroughTheExecutor() {
        val r = Rig(); val c = r.company("Lender Works", 1); val b = r.bond()
        val sub = r.sim.execute(Actor.Player(1), Command.SubscribeBond(c, b.id, 20))
        assertTrue(sub.toString(), sub is Outcome.Ok)
        assertTrue(r.sim.execute(Actor.Player(2), Command.SubscribeBond(c, b.id, 20)) is Outcome.Rejected)
        r.e.tick = b.subscriptionEnds; r.run.run(r.e.tick)
        val sell = r.sim.execute(Actor.Player(1), Command.TradeBond(c, b.id, 5, false))
        assertTrue(sell.toString(), sell is Outcome.Ok)
        assertEquals(15L, b.heldBy(c))
    }

    @Test fun aLongGameKeepsTheBooksBalancedAndRepaysBonds() {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val country = sim.economy.world.countries().first()
        val c = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Patient Works", country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        var placed = 0; var redeemed = 0
        repeat(1_300) {
            for (b in sim.economy.bonds.open()) {
                if (b.heldBy(c) == 0L && sim.economy.finance.ledger.cash(LedgerOwner.company(c)).raw > b.faceEach.raw * 20) sim.execute(Actor.Player(1), Command.SubscribeBond(c, b.id, 10))
            }
            for (ev in sim.advance().bondEvents) when (ev) { is BondEvent.Placed -> placed++; is BondEvent.Redeemed -> redeemed++; else -> Unit }
        }
        assertTrue("placed $placed", placed >= 2)
        assertTrue("redeemed $redeemed", redeemed >= 1)
        assertEquals(0L, sim.economy.finance.ledger.trialBalance())
        assertEquals(sim.economy.bonds.outstanding().sumOf { it.unitsInCirculation() * it.faceEach.raw }, -sim.economy.world.countries().sumOf { sim.economy.finance.ledger.balance(LedgerOwner.government(it.id), Chart.DEBT_LONG).raw })
    }
}
