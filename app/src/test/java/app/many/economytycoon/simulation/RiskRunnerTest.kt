package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.PolicyId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.production.Facility
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.risk.CoverLevel
import app.many.economytycoon.risk.Deductible
import app.many.economytycoon.risk.Peril
import app.many.economytycoon.risk.PolicyStatus
import app.many.economytycoon.risk.RiskEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskRunnerTest {
    private val wheat = GoodId("wheat")

    private class Rig(capital: Long = 400_000) {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        val owner = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Risky Works", country.id, null, Money.units(capital))) as Outcome.Ok).value.created!!)
        val farm: Facility = e.production.grantFacility(owner, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
        fun cash(c: CompanyId = owner) = e.finance.ledger.cash(LedgerOwner.company(c))
        fun insurerCash() = e.finance.ledger.cash(e.risk.insurer)
        fun buy(level: CoverLevel = CoverLevel.FULL, coverage: Long = 1_000_000L, deductible: Deductible = Deductible.LOW) =
            e.riskRunner.buy(owner, farm.id, level, coverage, deductible, e.tick)
        fun strike(peril: Peril, severity: Long, f: Facility = farm) = e.riskRunner.strike(peril, f.region, severity, listOf(f), e.tick, null, false)
        fun stock(qty: Long = 100, value: Long = 1_000) = farm.inventory.add(GoodId("wheat"), Quantity.units(qty), Money.units(value))
    }

    @Test fun aQuoteMatchesTheFirstPremium() {
        val r = Rig()
        val q = r.e.riskRunner.quote(r.farm, CoverLevel.STANDARD, 800_000L, Deductible.MEDIUM)
        val before = r.cash()
        val p = (r.buy(CoverLevel.STANDARD, 800_000L, Deductible.MEDIUM) as Outcome.Ok).value
        assertEquals(q.premium, p.premium)
        assertEquals(before - q.premium, r.cash())
        assertTrue(r.e.finance.invariants().isEmpty())
    }

    @Test fun theInsurerIsSeededOnceAndHoldsPremiums() {
        val r = Rig()
        r.buy()
        val reserve = r.e.risk.params.insurerReserve
        assertTrue(r.insurerCash() >= reserve)
        val p = r.e.risk.activeFor(r.farm.id)!!
        assertEquals(reserve + p.premium, r.insurerCash())
    }

    @Test fun foreignOrMissingFacilitiesAreRefused() {
        val r = Rig()
        val other = CompanyId((r.sim.execute(Actor.Player(2), Command.FoundCompany("Other Works", r.country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        val theirs = r.e.production.grantFacility(other, FacilityTypeId("grain_farm"), r.region, null, null, null, r.e.tick)
        val stolen = r.e.riskRunner.buy(r.owner, theirs.id, CoverLevel.BASIC, 800_000L, Deductible.LOW, r.e.tick) as Outcome.Rejected
        assertEquals(Rules.NOT_OWNER, stolen.rejection.rule)
        val gone = r.e.riskRunner.buy(r.owner, app.many.economytycoon.core.FacilityId(99_999), CoverLevel.BASIC, 800_000L, Deductible.LOW, r.e.tick) as Outcome.Rejected
        assertEquals(Rules.UNKNOWN_ENTITY, gone.rejection.rule)
    }

    @Test fun aDemolishedFacilityCannotBeInsured() {
        val r = Rig()
        r.e.production.demolish(r.owner, r.farm.id)
        assertEquals(FacilityStatus.DEMOLISHED, r.farm.status)
        assertEquals(Rules.WRONG_STATE, (r.buy() as Outcome.Rejected).rejection.rule)
    }

    @Test fun anInsuredFacilityCannotBeInsuredTwice() {
        val r = Rig()
        assertTrue(r.buy() is Outcome.Ok)
        assertEquals(Rules.WRONG_STATE, (r.buy() as Outcome.Rejected).rejection.rule)
    }

    @Test fun aBrokeCompanyCannotPayTheFirstPremium() {
        val r = Rig()
        r.e.finance.ledger.post(
            app.many.economytycoon.finance.TransactionDraft(
                "test:drain", r.e.tick, app.many.economytycoon.finance.ReasonCode.ADJUSTMENT,
                listOf(
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(LedgerOwner.company(r.owner), app.many.economytycoon.finance.Chart.OTHER_EXPENSE), r.cash()),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(LedgerOwner.company(r.owner), app.many.economytycoon.finance.Chart.CASH), -r.cash()),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(r.e.finance.households, app.many.economytycoon.finance.Chart.CASH), r.cash()),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(r.e.finance.households, app.many.economytycoon.finance.Chart.REVENUE_OTHER), -r.cash()),
                ),
                "drain",
            ),
        )
        assertEquals(Rules.INSUFFICIENT_FUNDS, (r.buy() as Outcome.Rejected).rejection.rule)
        assertNull(r.e.risk.activeFor(r.farm.id))
    }

    @Test fun aStrikeDamagesBuildingAndStockWhetherInsuredOrNot() {
        val r = Rig(); r.stock()
        val events = r.strike(Peril.FIRE, 500_000L)
        assertTrue(r.farm.conditionPpm < 1_000_000L)
        assertTrue(r.farm.inventory.quantity(wheat) < Quantity.units(100))
        assertTrue(events.any { it is RiskEvent.Struck })
        val claim = (events.first { it is RiskEvent.Uncovered } as RiskEvent.Uncovered).claim
        assertEquals(Money.ZERO, claim.payout)
        assertFalse(claim.insured)
        assertTrue(claim.loss.raw > 0)
    }

    @Test fun conditionNeverFallsBelowTheFloor() {
        val r = Rig()
        repeat(6) { r.strike(Peril.QUAKE, 800_000L) }
        assertEquals(r.e.risk.params.conditionFloorPpm, r.farm.conditionPpm)
    }

    @Test fun reservedStockIsLeftAlone() {
        val r = Rig(); r.stock()
        r.farm.inventory.reserve(wheat, Quantity.units(100))
        r.strike(Peril.FIRE, 800_000L)
        assertEquals(Quantity.units(100), r.farm.inventory.quantity(wheat))
    }

    @Test fun anInsuredLossIsPaidMinusTheExcess() {
        val r = Rig(); r.stock(200, 50_000)
        r.buy(CoverLevel.FULL, 1_000_000L, Deductible.LOW)
        val cash = r.cash(); val insurer = r.insurerCash()
        val events = r.strike(Peril.FIRE, 600_000L)
        val claim = (events.first { it is RiskEvent.Paid } as RiskEvent.Paid).claim
        val p = r.e.risk.activeFor(r.farm.id)!!
        assertEquals(claim.loss - p.deductibleAmount, claim.payout)
        assertEquals(cash + claim.payout, r.cash())
        assertEquals(insurer - claim.payout, r.insurerCash())
        assertEquals(1, p.claimCount)
        assertEquals(claim.payout, p.claimsPaid)
        assertTrue(r.e.finance.invariants().isEmpty())
    }

    @Test fun partialCoverPaysItsShare() {
        val r = Rig(); r.stock(200, 50_000)
        r.buy(CoverLevel.FULL, 500_000L, Deductible.LOW)
        val claim = (r.strike(Peril.FIRE, 600_000L).first { it is RiskEvent.Paid } as RiskEvent.Paid).claim
        val p = r.e.risk.activeFor(r.farm.id)!!
        val expected = (claim.loss - p.deductibleAmount).raw / 2
        assertEquals(expected.toDouble(), claim.payout.raw.toDouble(), 2.0)
    }

    @Test fun aPerilOutsideTheCoverIsNotPaid() {
        val r = Rig(); r.stock()
        r.buy(CoverLevel.BASIC)
        val events = r.strike(Peril.QUAKE, 500_000L)
        assertTrue(events.any { it is RiskEvent.Uncovered })
        assertTrue(events.none { it is RiskEvent.Paid })
    }

    @Test fun lossesBelowTheExcessPayNothing() {
        val r = Rig()
        r.buy(CoverLevel.FULL, 1_000_000L, Deductible.HIGH)
        val claim = (r.strike(Peril.STORM, 60_000L).first { it is RiskEvent.Paid } as RiskEvent.Paid).claim
        assertEquals(Money.ZERO, claim.payout)
    }

    @Test fun anEmptyInsurerPaysWhatItHasAndRecordsTheShortfall() {
        val r = Rig(); r.stock(200, 80_000)
        r.buy(CoverLevel.FULL, 1_000_000L, Deductible.LOW)
        val have = r.insurerCash()
        val ledger = r.e.finance.ledger
        val drain = have - Money.units(10)
        ledger.post(
            app.many.economytycoon.finance.TransactionDraft(
                "test:insurer", r.e.tick, app.many.economytycoon.finance.ReasonCode.ADJUSTMENT,
                listOf(
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(r.e.risk.insurer, app.many.economytycoon.finance.Chart.OTHER_EXPENSE), drain),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(r.e.risk.insurer, app.many.economytycoon.finance.Chart.CASH), -drain),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(r.e.finance.households, app.many.economytycoon.finance.Chart.CASH), drain),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(r.e.finance.households, app.many.economytycoon.finance.Chart.REVENUE_OTHER), -drain),
                ),
                "drain",
            ),
        )
        val claim = (r.strike(Peril.FIRE, 700_000L).first { it is RiskEvent.Paid } as RiskEvent.Paid).claim
        assertEquals(Money.units(10), claim.payout)
        assertTrue(claim.shortfall.raw > 0)
        assertEquals(Money.ZERO, r.insurerCash())
    }

    @Test fun regionalEventsHitEveryFacilityThere() {
        val r = Rig()
        val second = r.e.production.grantFacility(r.owner, FacilityTypeId("grain_farm"), r.region, null, null, null, r.e.tick)
        val events = r.e.riskRunner.strike(Peril.FLOOD, r.region, 500_000L, listOf(r.farm, second), r.e.tick, null, false)
        val d = (events.first { it is RiskEvent.Struck } as RiskEvent.Struck).disaster
        assertEquals(2, d.facilitiesHit)
        assertEquals(r.e.risk.disasters().last(), d)
        assertEquals(2, r.e.risk.claims().size)
    }

    @Test fun premiumsAreBilledEveryMonthAndFollowTheStock() {
        val r = Rig()
        val p = (r.buy() as Outcome.Ok).value
        val first = p.premium
        r.stock(1_000, 400_000)
        val cash = r.cash()
        r.e.tick = Tick(p.paidThrough.value)
        r.e.riskRunner.run(r.e.tick)
        assertTrue(p.premium > first)
        assertEquals(cash - p.premium, r.cash())
        assertEquals(Tick(p.paidThrough.value), p.paidThrough)
        assertTrue(p.paidThrough.value > 30)
    }

    @Test fun anUnpaidPremiumLapsesThePolicy() {
        val r = Rig()
        val p = (r.buy() as Outcome.Ok).value
        val cash = r.cash()
        r.e.finance.ledger.post(
            app.many.economytycoon.finance.TransactionDraft(
                "test:drain", r.e.tick, app.many.economytycoon.finance.ReasonCode.ADJUSTMENT,
                listOf(
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(LedgerOwner.company(r.owner), app.many.economytycoon.finance.Chart.OTHER_EXPENSE), cash),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(LedgerOwner.company(r.owner), app.many.economytycoon.finance.Chart.CASH), -cash),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(r.e.finance.households, app.many.economytycoon.finance.Chart.CASH), cash),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(r.e.finance.households, app.many.economytycoon.finance.Chart.REVENUE_OTHER), -cash),
                ),
                "drain",
            ),
        )
        r.e.tick = Tick(p.paidThrough.value)
        val events = r.e.riskRunner.run(r.e.tick)
        assertEquals(PolicyStatus.LAPSED, p.status)
        assertTrue(events.any { it is RiskEvent.Lapsed && it.policy == p.id })
    }

    @Test fun demolishingTheFacilityLapsesItsPolicyAtBilling() {
        val r = Rig()
        val p = (r.buy() as Outcome.Ok).value
        r.e.production.demolish(r.owner, r.farm.id)
        r.e.tick = Tick(p.paidThrough.value)
        r.e.riskRunner.run(r.e.tick)
        assertEquals(PolicyStatus.LAPSED, p.status)
    }

    @Test fun cancellingStopsBillingAndPayouts() {
        val r = Rig(); r.stock(100, 20_000)
        val p = (r.buy() as Outcome.Ok).value
        assertTrue(r.e.riskRunner.cancel(r.owner, p.id, r.e.tick) is Outcome.Ok)
        val events = r.strike(Peril.FIRE, 600_000L)
        assertTrue(events.any { it is RiskEvent.Uncovered })
        assertEquals(Money.ZERO, p.claimsPaid)
    }

    @Test fun dissolvingACompanyEndsItsPolicies() {
        val r = Rig()
        r.buy()
        r.e.retire(r.owner)
        assertTrue(r.e.risk.activePoliciesOf(r.owner).isEmpty())
    }

    @Test fun commandsWorkThroughTheExecutor() {
        val r = Rig()
        val bought = r.sim.execute(Actor.Player(1), Command.BuyPolicy(r.owner, r.farm.id, CoverLevel.STANDARD, 800_000L, Deductible.MEDIUM)) as Outcome.Ok
        val id = PolicyId(bought.value.created!!)
        assertTrue(r.e.risk.policy(id)!!.isActive)
        assertTrue(r.sim.execute(Actor.Player(1), Command.CancelPolicy(r.owner, id)) is Outcome.Ok)
        assertFalse(r.e.risk.policy(id)!!.isActive)
    }

    @Test fun otherPlayersCannotBuyForYourCompany() {
        val r = Rig()
        val e = r.sim.execute(Actor.Player(77), Command.BuyPolicy(r.owner, r.farm.id, CoverLevel.BASIC, 800_000L, Deductible.LOW))
        assertTrue(e is Outcome.Rejected)
    }

    @Test fun aLongRunProducesDisastersDeterministicallyAndStaysBalanced() {
        fun play(): Triple<Int, Int, String> {
            val r = Rig()
            r.stock(500, 100_000)
            r.buy(CoverLevel.FULL, 800_000L, Deductible.MEDIUM)
            r.e.finance.mint(r.e.tick, r.e.finance.households, Money.units(20_000_000), "test:households")
            r.sim.advance(1_080)
            return Triple(r.e.risk.disasters().size, r.e.risk.claims().size, r.sim.hash())
        }
        val a = play(); val b = play()
        assertEquals(a, b)
        assertTrue("three years should bring at least one event", a.first > 0)
    }
}
