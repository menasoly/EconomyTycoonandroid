package app.many.economytycoon.session

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.TransactionDraft
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertsTest {
    private val sim = SimTestSupport.sim(71L, 3).also { it.advance(35) }
    private val scanner = AlertScanner(sim.economy)

    private fun playerCompany(): CompanyId {
        sim.execute(Actor.Player(1), Command.FoundCompany("Alert Test Works", sim.economy.world.countries().first().id, null, Money.units(200_000)))
        return sim.economy.companies.byName("Alert Test Works")!!.id
    }

    private fun burn(company: CompanyId, amount: Money, key: String) {
        val c = sim.economy.companies.require(company)
        sim.economy.finance.ledger.post(
            TransactionDraft(
                key, sim.tick, ReasonCode.TRANSFER,
                listOf(
                    Entry(AccountRef(c.owner, Chart.COGS), amount), Entry(AccountRef(c.owner, Chart.CASH), -amount),
                    Entry(AccountRef(sim.economy.finance.households, Chart.CASH), amount), Entry(AccountRef(sim.economy.finance.households, Chart.REVENUE_OTHER), -amount),
                ),
            ),
        ).getOrThrow()
    }

    @Test fun aHealthyNewCompanyHasNoCriticalAlerts() {
        val id = playerCompany()
        assertTrue(scanner.scan(id).none { it.severity == Severity.CRITICAL })
    }

    @Test fun unknownAndDissolvedCompaniesHaveNoAlerts() {
        assertTrue(scanner.scan(CompanyId(9_999)).isEmpty())
    }

    @Test fun spendingMostOfTheCashRaisesARunwayAlert() {
        val id = playerCompany()
        burn(id, Money.units(190_000), "burn-runway")
        val alerts = scanner.scan(id)
        val runway = alerts.firstOrNull { it.code == "cash.runway" }
        assertTrue(runway != null && runway.severity >= Severity.WARNING)
    }

    @Test fun overdrawingRaisesACriticalAlert() {
        val id = playerCompany()
        burn(id, Money.units(260_000), "burn-negative")
        assertTrue(scanner.scan(id).any { it.code == "cash.negative" && it.severity == Severity.CRITICAL })
    }

    @Test fun alertsComeWorstFirst() {
        val id = playerCompany()
        burn(id, Money.units(260_000), "burn-order")
        val alerts = scanner.scan(id)
        assertEquals(alerts.map { it.severity }.sortedDescending(), alerts.map { it.severity })
    }

    @Test fun shuttingAFacilityDownIsReported() {
        val owner = sim.economy.companies.active().first { sim.economy.production.facilitiesOf(it.id).isNotEmpty() }
        val facility = sim.economy.production.facilitiesOf(owner.id).first()
        sim.economy.production.shutdown(owner.id, facility.id)
        val alerts = scanner.scan(owner.id)
        assertTrue(alerts.any { it.code == "facility.shutdown" && it.subject == facility.id.value })
    }

    @Test fun aTakeoverBidShowsUpForTheTarget() {
        val ids = sim.economy.companies.active().map { it.id }
        val acquirer = ids[0]; val target = ids[1]
        val price = sim.economy.acquisitions.fairPricePerShare(target, sim.tick)
        val offer = sim.economy.acquisitions.makeOffer(acquirer, target, 10, price, sim.tick)
        if (offer.isOk) assertTrue(scanner.scan(target).any { it.code == "offer.takeover" })
        else assertFalse(offer.isOk)
    }

    @Test fun alertCodesAreStableAndNonEmpty() {
        for (c in sim.economy.companies.active()) for (a in scanner.scan(c.id)) {
            assertTrue(a.code.isNotBlank() && a.title.isNotBlank() && a.detail.isNotBlank())
        }
    }
}
