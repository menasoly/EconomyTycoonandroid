package app.many.economytycoon.session

import app.many.economytycoon.bonds.BondEvent
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.simulation.TickSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BondNewsAndAlertsTest {
    private val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
    private val e = sim.economy
    private val country = e.world.countries().first()
    private val owner = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Gilt Works", country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
    private val scanner = AlertScanner(e)
    private val bond = (e.bonds.issue(country.id, 360, 55_000L, 400, e.tick) as Outcome.Ok).value

    private fun headlines(vararg events: BondEvent): List<String> {
        val feed = NewsFeed(e)
        val s = TickSummary(Tick(5))
        s.bondEvents = events.toList()
        feed.record(s, emptyList())
        return feed.items().map { it.headline }
    }

    @Test fun issuesSalesAndRepaymentsAreNewsButCouponsAreNot() {
        val lines = headlines(
            BondEvent.Issued(bond.id, country.id, Tick(5)), BondEvent.Placed(bond.id, country.id, 300, Money.units(300_000), Tick(5)),
            BondEvent.NoSale(bond.id, country.id, Tick(5)), BondEvent.Redeemed(bond.id, country.id, Money.units(300_000), Tick(5)),
            BondEvent.Coupon(bond.id, country.id, Money.units(1_000), Tick(5)),
        )
        assertEquals(4, lines.size)
        assertTrue(lines.any { it.contains("offers 360-day bonds paying 5.5%") })
        assertTrue(lines.any { it.contains("raises") }); assertTrue(lines.any { it.contains("no buyers") }); assertTrue(lines.any { it.contains("repays") })
        assertTrue(lines.all { it.contains(country.name) })
    }

    @Test fun anOpenIssueIsAnAlertWhileYouHaveCashAndNoStake() {
        val a = scanner.scan(owner).single { it.code == "bond.open" }
        assertEquals(bond.id.value, a.subject); assertEquals(Severity.INFO, a.severity)
        assertTrue(a.title.contains("5.5%")); assertTrue(a.detail.contains("close in"))
        e.bondRunner.subscribe(owner, bond.id, 5, e.tick)
        assertFalse(scanner.scan(owner).any { it.code == "bond.open" })
    }

    @Test fun aClosedIssueNoLongerAlerts() {
        e.bonds.closeSubscriptions(bond.subscriptionEnds) { Money.ZERO }
        assertFalse(scanner.scan(owner).any { it.code == "bond.open" })
    }

    @Test fun holdingsNearMaturityAlertWithTheFaceValue() {
        e.bondRunner.subscribe(owner, bond.id, 20, e.tick)
        e.tick = bond.subscriptionEnds; e.bondRunner.run(e.tick)
        assertFalse(scanner.scan(owner).any { it.code == "bond.matures" })
        e.tick = Tick(bond.maturesAt.value - 4)
        val a = scanner.scan(owner).single { it.code == "bond.matures" }
        assertTrue(a.title.contains("4 days")); assertTrue(a.detail.contains(Formatting.money(Money(bond.faceEach.raw * 20))))
    }

    @Test fun otherInvestorsMaturitiesAreNotYourAlerts() {
        val other = CompanyId((sim.execute(Actor.Player(2), Command.FoundCompany("Other Works", country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        e.bondRunner.subscribe(other, bond.id, 20, e.tick)
        e.tick = bond.subscriptionEnds; e.bondRunner.run(e.tick)
        e.tick = Tick(bond.maturesAt.value - 2)
        assertFalse(scanner.scan(owner).any { it.code == "bond.matures" })
        assertTrue(scanner.scan(other).any { it.code == "bond.matures" })
    }

    @Test fun aCompanyTooPoorToBuyAUnitIsNotNagged() {
        val who = e.companies.require(owner).owner
        val extra = e.finance.ledger.cash(who) - Money.units(100)
        val gov = app.many.economytycoon.finance.LedgerOwner.government(country.id)
        e.finance.ledger.post(
            app.many.economytycoon.finance.TransactionDraft(
                "test:drain", Tick(0), app.many.economytycoon.finance.ReasonCode.TRANSFER,
                listOf(
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(who, app.many.economytycoon.finance.Chart.CASH), -extra),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(who, app.many.economytycoon.finance.Chart.OTHER_EXPENSE), extra),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(gov, app.many.economytycoon.finance.Chart.CASH), extra),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(gov, app.many.economytycoon.finance.Chart.REVENUE_OTHER), -extra),
                ),
                "drain",
            ),
        )
        assertFalse(scanner.scan(owner).any { it.code == "bond.open" })
    }
}
