package app.many.economytycoon.simulation

import app.many.economytycoon.bonds.Bond
import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.finance.LedgerOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiBondsTest {
    private class Rig(val coupon: Long = 70_000L) {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val lender = AiBonds(e, CommandExecutor(e))
        val rivals = (1..6).map { i ->
            CompanyId((sim.execute(Actor.System, Command.FoundCompany("Rival $i", country.id, null, Money.units(1_000_000), CompanyKind.AI)) as Outcome.Ok).value.created!!)
        }
        val bond: Bond = (e.bonds.issue(country.id, 360, coupon, 2_000, e.tick) as Outcome.Ok).value
        fun cash(c: CompanyId) = e.finance.ledger.cash(LedgerOwner.company(c))
        fun sweep() { for (d in 0 until e.bonds.params.subscriptionDays) { lender.run(app.many.economytycoon.core.Tick(bond.issuedAt.value + d)) } }
    }

    @Test fun everyRichRivalLooksOnceDuringTheWindowAndSubscribes() {
        val r = Rig(); r.sweep()
        assertTrue(r.rivals.all { r.bond.heldBy(it) > 0L })
        assertEquals(r.rivals.sumOf { r.bond.heldBy(it) }, r.bond.unitsSold)
    }

    @Test fun eachRivalsOrderIsSmallAndKeepsACashFloor() {
        val r = Rig(); val start = r.cash(r.rivals.first())
        r.sweep()
        for (c in r.rivals) {
            val spent = Money(r.bond.faceEach.raw * r.bond.heldBy(c))
            assertTrue(spent <= Money.units(400_000))
            assertTrue(spent.raw * 100 <= start.raw * 26)
            assertTrue(r.cash(c) >= Money.units(80_000))
        }
    }

    @Test fun aCouponBelowTheHurdleIsIgnored() {
        val r = Rig(coupon = 1_000L); r.sweep()
        assertEquals(0L, r.bond.unitsSold)
    }

    @Test fun poorRivalsStayOut() {
        val r = Rig()
        for (c in r.rivals) {
            val extra = r.cash(c) - Money.units(50_000)
            r.e.finance.ledger.post(
                app.many.economytycoon.finance.TransactionDraft(
                    "test:drain:${c.value}", app.many.economytycoon.core.Tick(0), app.many.economytycoon.finance.ReasonCode.TRANSFER,
                    listOf(
                        app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(LedgerOwner.company(c), app.many.economytycoon.finance.Chart.CASH), -extra),
                        app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(LedgerOwner.company(c), app.many.economytycoon.finance.Chart.OTHER_EXPENSE), extra),
                        app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(LedgerOwner.government(r.country.id), app.many.economytycoon.finance.Chart.CASH), extra),
                        app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(LedgerOwner.government(r.country.id), app.many.economytycoon.finance.Chart.REVENUE_OTHER), -extra),
                    ),
                    "drain",
                ),
            )
        }
        r.sweep()
        assertEquals(0L, r.bond.unitsSold)
    }

    @Test fun anIssueIsNeverOversubscribed() {
        val r = Rig()
        r.e.bonds.addSold(r.bond, r.bond.unitsOffered - 3)
        r.e.bonds.allot(r.bond, CompanyId(500), r.bond.unitsOffered - 3, Money.ZERO)
        r.sweep()
        assertTrue(r.bond.unitsSold <= r.bond.unitsOffered)
        assertTrue(r.bond.unitsLeft >= 0L)
    }

    @Test fun humanCompaniesAreNotSubscribedForThem() {
        val r = Rig()
        val me = CompanyId((r.sim.execute(Actor.Player(1), Command.FoundCompany("Human Works", r.country.id, null, Money.units(1_000_000))) as Outcome.Ok).value.created!!)
        r.sweep()
        assertEquals(0L, r.bond.heldBy(me))
    }

    @Test fun nothingHappensWithoutAnOpenIssue() {
        val r = Rig(); r.e.bonds.closeSubscriptions(r.bond.subscriptionEnds) { Money.ZERO }
        assertEquals(0, r.lender.run(r.bond.subscriptionEnds).subscriptions)
    }
}
