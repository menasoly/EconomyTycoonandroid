package app.many.economytycoon.workforce

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.FinanceService
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.OwnerKind
import app.many.economytycoon.finance.SettlementStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkforceTest {
    private val region = RegionId(1)
    private val worker = RoleId("operator")
    private val engineer = RoleId("engineer")
    private val catalog = RoleCatalog.of(
        WorkRole(worker, "Operator", SkillTier.TRAINED, Money.units(100), 45, 5),
        WorkRole(engineer, "Engineer", SkillTier.SPECIALIST, Money.units(160), 120, 10),
    )

    private fun service(supplyWorkers: Long = 10_000, supplyEngineers: Long = 500): WorkforceService {
        val markets = LaborMarkets(catalog)
        markets.setSupply(MarketKey(region, worker), supplyWorkers)
        markets.setSupply(MarketKey(region, engineer), supplyEngineers)
        return WorkforceService(IdAllocators(), catalog, markets, WorkforceParams(payIntervalTicks = 7))
    }

    private fun key(company: Long, role: RoleId = worker, facility: Long = 1) = CohortKey(CompanyId(company), FacilityId(facility), role, region)

    private fun run(w: WorkforceService, from: Long, to: Long, rp: RandomProvider = RandomProvider(1)) {
        for (t in from..to) { w.beginTick(); w.step(Tick(t), rp) }
    }

    @Test fun hiringFillsOpeningsOverTime() {
        val w = service()
        w.setTarget(key(1), 100)
        run(w, 1, 40)
        val c = w.cohort(key(1))!!
        assertTrue("headcount ${c.headcount}", c.headcount in 90..100)
        assertEquals(0L, c.pendingCount)
    }

    @Test fun hiringDrainsSharedLaborSupply() {
        val w = service(supplyWorkers = 150)
        w.setTarget(key(1), 100); w.setTarget(key(2), 100)
        run(w, 1, 60)
        val total = w.employedInRegion(region)
        assertTrue("total $total", total in 120..150)
        assertTrue(w.markets.market(MarketKey(region, worker)).available + total <= 150)
    }

    @Test fun betterPayWinsMoreOfTheApplicants() {
        val w = service(supplyWorkers = 400)
        val a = key(1); val b = key(2)
        w.setTarget(a, 300); w.setTarget(b, 300)
        w.setWage(a, Money.units(130)); w.setWage(b, Money.units(80))
        run(w, 1, 12)
        assertTrue(w.cohort(a)!!.headcount + w.cohort(a)!!.pendingCount > w.cohort(b)!!.headcount + w.cohort(b)!!.pendingCount)
    }

    @Test fun layoffsReleaseWorkersAndOweSeverance() {
        val w = service()
        w.setTarget(key(1), 100)
        run(w, 1, 40)
        w.setTarget(key(1), 40)
        val pool = w.markets.market(MarketKey(region, worker)).available
        run(w, 41, 41)
        assertEquals(40L, w.cohort(key(1))!!.headcount.coerceAtMost(40L))
        assertTrue(w.markets.market(MarketKey(region, worker)).available > pool)
        assertTrue(w.severanceOwed(CompanyId(1)).isPositive)
    }

    @Test fun minimumWageIsEnforced() {
        val w = service()
        w.markets.setMinimumWage(region, Money.units(90))
        assertEquals(Rules.REGULATION, w.setWage(key(1), Money.units(70)).rejectionOrNull()?.rule)
        assertTrue(w.setWage(key(1), Money.units(95)).isOk)
        assertTrue(w.markets.marketWage(MarketKey(region, worker)) >= Money.units(90))
    }

    @Test fun lowPayDrivesAttritionAndLowMorale() {
        val w = service()
        val k = key(1)
        w.setTarget(k, 200)
        run(w, 1, 30)
        val c = w.cohort(k)!!
        val headStart = c.headcount
        c.wage = Money.units(50)
        w.setTarget(k, headStart) // do not rehire
        run(w, 31, 150)
        assertTrue(c.moralePpm < 600_000)
        assertTrue(c.headcount < headStart)
    }

    @Test fun overloadBuildsBurnoutAndCutsProductivity() {
        val w = service()
        val k = key(1)
        w.setTarget(k, 50)
        run(w, 1, 20)
        val c = w.cohort(k)!!
        val before = w.productivity(c, Tick(20))
        w.setLoad(k, 1_600_000)
        run(w, 21, 200)
        assertTrue(c.burnoutPpm > 100_000)
        assertTrue(w.productivity(c, Tick(200)) < before)
    }

    @Test fun trainingRaisesSkillAndProductivity() {
        val w = service()
        val k = key(1)
        w.setTarget(k, 30)
        run(w, 1, 20)
        val c = w.cohort(k)!!
        val skill = c.skillPpm
        w.setTraining(k, 200_000)
        run(w, 21, 200)
        assertTrue(c.skillPpm > skill)
        assertTrue(c.skillPpm <= 1_000_000)
    }

    @Test fun payrollIntentsAppearOnPayDaysAndMatchAccrual() {
        val w = service()
        val k = key(1)
        w.setTarget(k, 50)
        run(w, 1, 6)
        val hc = w.cohort(k)!!.headcount
        assertTrue(w.payrollIntents(Tick(6), LedgerOwner.households(), { LedgerOwner(OwnerKind.GOVERNMENT, 1) }).isEmpty())
        run(w, 7, 7)
        val intents = w.payrollIntents(Tick(7), LedgerOwner.households(), { LedgerOwner(OwnerKind.GOVERNMENT, 1) })
        assertEquals(2, intents.size)
        val wages = intents.first { it.key.startsWith("payroll:") }
        assertTrue(wages.total.isPositive)
        assertTrue(wages.total <= w.cohort(k)!!.wage * (hc + 50) * 7)
        // The accrual was cleared, so the next call on a non-pay day yields nothing.
        assertTrue(w.payrollIntents(Tick(8), LedgerOwner.households(), { LedgerOwner(OwnerKind.GOVERNMENT, 1) }).isEmpty())
    }

    @Test fun unpaidWagesBuildAStreakAndHurtMorale() {
        val w = service()
        val fin = FinanceService(IdAllocators())
        val owner = LedgerOwner.company(CompanyId(1))
        val gov = LedgerOwner(OwnerKind.GOVERNMENT, 1)
        val k = key(1)
        w.setTarget(k, 80)
        run(w, 1, 14)
        for (t in 15L..70L) {
            run(w, t, t)
            val intents = w.payrollIntents(Tick(t), LedgerOwner.households(), { gov })
            if (intents.isNotEmpty()) {
                val res = fin.settlement.settle(Tick(t), intents)
                w.applyResults(res)
                assertTrue(res.any { it.status == SettlementStatus.DEFERRED })
            }
        }
        assertTrue(w.unpaidTicks(CompanyId(1)) >= 28)
        assertTrue(w.cohort(k)!!.moralePpm < 450_000)
        assertTrue(fin.settlement.totalArrears(owner).isPositive)
    }

    @Test fun paidPayrollClearsTheStreak() {
        val w = service()
        val fin = FinanceService(IdAllocators())
        val owner = LedgerOwner.company(CompanyId(1))
        fin.mint(Tick(0), owner, Money.units(10_000_000), "cash")
        val k = key(1)
        w.setTarget(k, 30)
        run(w, 1, 6)
        run(w, 7, 7)
        val res = fin.settlement.settle(Tick(7), w.payrollIntents(Tick(7), LedgerOwner.households(), { LedgerOwner(OwnerKind.GOVERNMENT, 1) }))
        w.applyResults(res)
        assertEquals(0, w.unpaidTicks(CompanyId(1)))
        assertTrue(fin.ledger.cash(LedgerOwner.households()).isPositive)
    }

    @Test fun strikesStopProductionAndPay() {
        val w = service()
        val k = key(1)
        w.setTarget(k, 40)
        run(w, 1, 20)
        val c = w.cohort(k)!!
        c.strikeUntil = 40
        assertEquals(0L, w.productivity(c, Tick(25)))
        assertEquals(0L, w.effectiveLabor(k, Tick(25)))
        c.accruedWages = Money.ZERO
        run(w, 21, 22)
        assertEquals(Money.ZERO, c.accruedWages)
        assertTrue(w.effectiveLabor(k, Tick(45)) > 0)
    }

    @Test fun staffingFractionReflectsHeadcountAndProductivity() {
        val w = service()
        val k = key(1)
        w.setTarget(k, 20)
        run(w, 1, 30)
        val staffing = w.staffingPpm(k, 20, Tick(30))
        assertTrue(staffing in 600_000..1_000_000)
        assertEquals(1_000_000L, w.staffingPpm(k, 0, Tick(30)))
    }

    @Test fun wagesRiseWhenLaborIsScarce() {
        val w = service(supplyWorkers = 300)
        for (c in 1L..6L) w.setTarget(key(c), 400)
        val before = w.markets.marketWage(MarketKey(region, worker))
        run(w, 1, 300)
        assertTrue(w.markets.marketWage(MarketKey(region, worker)) > before)
    }

    @Test fun wagesFallWhenJobseekersAreAbundant() {
        val w = service(supplyWorkers = 50_000)
        w.setTarget(key(1), 10)
        val before = w.markets.marketWage(MarketKey(region, worker))
        run(w, 1, 300)
        assertTrue(w.markets.marketWage(MarketKey(region, worker)) < before)
    }

    @Test fun dissolvingACompanyReturnsItsWorkers() {
        val w = service(supplyWorkers = 1_000)
        w.setTarget(key(1), 100)
        run(w, 1, 30)
        val employed = w.employedBy(CompanyId(1))
        val pool = w.markets.market(MarketKey(region, worker)).available
        w.dissolve(CompanyId(1))
        assertEquals(pool + employed, w.markets.market(MarketKey(region, worker)).available)
        assertTrue(w.cohortsOf(CompanyId(1)).isEmpty())
    }

    @Test fun automationOnlyReducesStaffing() {
        val w = service()
        w.setTarget(key(1), 100)
        assertTrue(w.automate(key(1), 60).isOk)
        assertEquals(60L, w.cohort(key(1))!!.target)
        assertTrue(w.automate(key(1), 90).isRejected)
        assertTrue(w.automate(key(2), 10).isRejected)
    }

    @Test fun contractRenewalEitherRaisesWagesOrCausesAStrike() {
        val w = service()
        val k = key(1)
        w.setTarget(k, 50)
        run(w, 1, 20)
        w.addContract(LaborContract(CompanyId(1), null, Money.units(100), Tick(0), Tick(25)))
        val rp = RandomProvider(9)
        run(w, 21, 30, rp)
        val c = w.cohort(k)!!
        assertTrue(c.strikeUntil > 25 || c.wage >= Money.units(100))
        assertEquals(1, w.contractsOf(CompanyId(1)).size)
        assertTrue(w.contractsOf(CompanyId(1)).single().end > Tick(25))
    }

    @Test fun negotiationFavorsAgreementWhenPayIsFair() {
        var strikes = 0
        val rnd = DeterministicRandom(4)
        repeat(200) {
            val r = Negotiation.resolve(rnd, Money.units(100), Money.units(100), Rate.percent(4), 800_000, 800_000, false)
            if (r.outcome == NegotiationOutcome.STRIKE) strikes++
        }
        assertTrue("strikes $strikes", strikes < 40)
        var angry = 0
        repeat(200) {
            val r = Negotiation.resolve(rnd, Money.units(60), Money.units(100), Rate.ZERO, 200_000, 200_000, true)
            if (r.outcome == NegotiationOutcome.STRIKE) angry++
        }
        assertTrue("angry $angry", angry > strikes * 2)
    }

    @Test fun moraleFormulasStayWithinBounds() {
        for (ratio in listOf(0L, 500_000L, 1_000_000L, 2_000_000L)) for (unpaid in listOf(0, 10, 100)) for (load in listOf(0L, 1_000_000L, 2_000_000L)) {
            val t = Morale.target(MoraleInputs(ratio, unpaid, load, 50_000, 100_000, 720, false))
            assertTrue(t in 0..1_000_000)
        }
        assertTrue(Morale.quitRatePpm(500_000, 100_000, 800_000) > Morale.quitRatePpm(1_200_000, 900_000, 0) * 3)
        assertEquals(500_000L, Morale.step(500_000, 500_000))
        assertTrue(Morale.step(0, 1_000_000) > 0)
    }

    @Test fun deterministicAcrossIdenticalRuns() {
        fun hashOf(): Any {
            val w = service(2_000)
            for (c in 1L..4L) { w.setTarget(key(c), 100 + c * 10); w.setTarget(key(c, engineer), 8) }
            val rp = RandomProvider(7)
            for (t in 1L..200L) {
                if (t == 100L) w.setTarget(key(2), 30)
                w.beginTick(); w.step(Tick(t), rp)
            }
            return CanonicalHasher().also { w.hashInto(it) }.finish()
        }
        assertEquals(hashOf(), hashOf())
    }

    @Test fun saveAndLoadRoundTrip() {
        val w = service(5_000)
        for (c in 1L..3L) { w.setTarget(key(c), 120); w.setBenefits(CompanyId(c), 40_000) }
        w.addContract(LaborContract(CompanyId(1), null, Money.units(100), Tick(0), Tick(150), 20, true))
        val rp = RandomProvider(3)
        for (t in 1L..120L) { w.beginTick(); w.step(Tick(t), rp) }
        val bw = ByteWriter(); WorkforceCodec.write(w, bw)
        val back = service(5_000)
        WorkforceCodec.read(ByteReader(bw.toByteArray()), back)
        val h1 = CanonicalHasher().also { w.hashInto(it) }.finish()
        val h2 = CanonicalHasher().also { back.hashInto(it) }.finish()
        assertEquals(h1, h2)
        assertFalse(back.all().isEmpty())
    }
}
