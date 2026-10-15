package app.many.economytycoon.advisor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorRulesTest {
    private val t = AdvisorTuning()
    private fun fac(id: Long = 1, name: String = "Mill", running: Boolean = true, util: Long = 800_000, cond: Long = 900_000, blocked: String? = null, heads: Long = 10, stock: Long = 0) =
        FacilityFact(id, name, running, util, cond, blocked, heads, stock)

    private fun codes(f: AdvisorFacts) = AdvisorRules.all.flatMap { it.check(f, t) }.map { it.code }
    private fun healthy() = AdvisorFacts(
        tick = 100, cashUnits = 50_000, netWorthUnits = 500_000, debtUnits = 0, monthRevenueUnits = 100_000, monthNetIncomeUnits = 20_000,
        dailyPayrollUnits = 800, facilities = listOf(fac(1), fac(2, "Farm")), averageMoralePpm = 700_000, cohorts = 2, industryCount = 2,
        netMarginPpm = 200_000, currentRatioPpm = 2_500_000, listed = false,
    )

    @Test fun aHealthyCompanyIsLeftAlone() {
        assertEquals(emptyList<String>(), codes(healthy()))
    }

    @Test fun anEmptyCompanyShouldBuildSomething() {
        assertEquals(listOf("no-facilities"), codes(AdvisorFacts()))
        assertFalse("no-facilities" in codes(healthy()))
    }

    @Test fun lossesAndThinMarginsAreToldApart() {
        val loss = healthy().copy(monthNetIncomeUnits = -5_000, netMarginPpm = -50_000)
        assertTrue("loss-making" in codes(loss)); assertFalse("thin-margin" in codes(loss))
        val thin = healthy().copy(monthNetIncomeUnits = 1_000, netMarginPpm = 10_000)
        assertTrue("thin-margin" in codes(thin)); assertFalse("loss-making" in codes(thin))
        assertFalse("thin-margin" in codes(healthy().copy(monthRevenueUnits = 0, monthNetIncomeUnits = 0, netMarginPpm = 0)))
    }

    @Test fun indebtedCompaniesWithTightLiquidityAreWarned() {
        assertTrue("tight-liquidity" in codes(healthy().copy(debtUnits = 10_000, currentRatioPpm = 800_000)))
        assertFalse("tight-liquidity" in codes(healthy().copy(debtUnits = 0, currentRatioPpm = 800_000)))
        assertFalse("tight-liquidity" in codes(healthy().copy(debtUnits = 10_000, currentRatioPpm = 1_000_000)))
    }

    @Test fun idleCashNeedsAHighShareAndNoDebt() {
        val rich = healthy().copy(cashUnits = 300_000, netWorthUnits = 500_000)
        assertTrue("idle-cash" in codes(rich))
        assertFalse("idle-cash" in codes(rich.copy(debtUnits = 1_000)))
        assertFalse("idle-cash" in codes(rich.copy(cashUnits = 100_000, netWorthUnits = 200_000)))
        assertFalse("idle-cash" in codes(rich.copy(facilities = emptyList())))
        assertFalse("idle-cash" in codes(rich.copy(cashUnits = 190_000)))
    }

    @Test fun expensiveDebtIsRepayableOnlyWhenCashCoversIt() {
        val f = healthy().copy(loanRatePpm = 95_000, debtUnits = 40_000, cashUnits = 60_000)
        val hit = AdvisorRules.expensiveDebt.check(f, t).single()
        assertTrue(hit.detail.contains("9% a year"))
        assertTrue(AdvisorRules.expensiveDebt.check(f.copy(cashUnits = 30_000), t).isEmpty())
        assertTrue(AdvisorRules.expensiveDebt.check(f.copy(loanRatePpm = 40_000), t).isEmpty())
        assertTrue(AdvisorRules.expensiveDebt.check(f.copy(loanRatePpm = null), t).isEmpty())
    }

    @Test fun bigUnlistedCompaniesMayGoPublic() {
        assertTrue("go-public" in codes(healthy().copy(netWorthUnits = 2_500_000, cashUnits = 10_000)))
        assertFalse("go-public" in codes(healthy().copy(netWorthUnits = 2_500_000, listed = true, cashUnits = 10_000)))
        assertFalse("go-public" in codes(healthy().copy(netWorthUnits = 1_900_000, cashUnits = 10_000)))
    }

    @Test fun idleFacilitiesAreCountedAndNamed() {
        val f = healthy().copy(facilities = listOf(fac(1, "Quiet Mill", util = 50_000), fac(2, "Dead Farm", util = 0), fac(3, "Busy"), fac(4, "Stuck", util = 0, blocked = "no labour")))
        val hit = AdvisorRules.idleFacilities.check(f, t).single()
        assertEquals(52, hit.priority)
        assertTrue(hit.title.startsWith("2 facilities"))
        assertTrue(hit.detail.contains("Quiet Mill") && hit.detail.contains("Dead Farm") && !hit.detail.contains("Stuck"))
    }

    @Test fun shutDownFacilitiesAreNotNagged() {
        val f = healthy().copy(facilities = listOf(fac(1, running = false, util = 0, cond = 100_000)))
        assertFalse("idle-facility" in codes(f)); assertFalse("worn-facility" in codes(f))
    }

    @Test fun blockedFacilitiesGiveTheReason() {
        val f = healthy().copy(facilities = listOf(fac(1, "Smelter", blocked = "no energy"), fac(2, "Mill", blocked = "output full")))
        val hit = AdvisorRules.blockedFacilities.check(f, t).single()
        assertTrue(hit.detail.contains("Smelter is stuck: no energy"))
        assertTrue(hit.detail.contains("1 more"))
        assertEquals(64, hit.priority)
    }

    @Test fun wornFacilitiesAskForMaintenance() {
        assertTrue("worn-facility" in codes(healthy().copy(facilities = listOf(fac(1, cond = 300_000)))))
        assertFalse("worn-facility" in codes(healthy().copy(facilities = listOf(fac(1, cond = 500_000)))))
    }

    @Test fun overstockIsAShareOfWorth() {
        val f = healthy().copy(facilities = listOf(fac(1, stock = 100_000), fac(2, stock = 60_000)))
        assertTrue("overstock" in codes(f))
        assertFalse("overstock" in codes(healthy().copy(facilities = listOf(fac(1, stock = 100_000)))))
        assertFalse("overstock" in codes(f.copy(netWorthUnits = 0)))
    }

    @Test fun peopleProblemsAreSeparate() {
        assertTrue("understaffed" in codes(healthy().copy(vacancies = 3)))
        assertTrue("low-morale" in codes(healthy().copy(averageMoralePpm = 300_000)))
        assertFalse("low-morale" in codes(healthy().copy(averageMoralePpm = 300_000, facilities = listOf(fac(1, heads = 0)))))
        assertTrue("underpaid" in codes(healthy().copy(underpaidCohorts = 1)))
        assertEquals("3 positions unfilled", AdvisorRules.understaffed.check(healthy().copy(vacancies = 3), t).single().title)
        assertEquals("1 position unfilled", AdvisorRules.understaffed.check(healthy().copy(vacancies = 1), t).single().title)
    }

    @Test fun heavyPayrollComparesAMonthToSales() {
        val f = healthy().copy(dailyPayrollUnits = 2_500, monthRevenueUnits = 100_000)
        assertTrue("heavy-payroll" in codes(f))
        assertFalse("heavy-payroll" in codes(f.copy(dailyPayrollUnits = 2_000)))
        assertFalse("heavy-payroll" in codes(f.copy(monthRevenueUnits = 0)))
    }

    @Test fun idleResearchLandAndOffersHaveTheirOwnRules() {
        assertTrue("idle-research" in codes(healthy().copy(researchIdle = true)))
        assertTrue("idle-land" in codes(healthy().copy(idleParcels = 2)))
        assertTrue("contract-offers" in codes(healthy().copy(contractOffers = 1)))
    }

    @Test fun oneIndustryWithManyFacilitiesIsConcentrated() {
        val f = healthy().copy(industryCount = 1, facilities = List(4) { fac(it.toLong()) })
        assertTrue("concentration" in codes(f))
        assertFalse("concentration" in codes(f.copy(industryCount = 2)))
        assertFalse("concentration" in codes(f.copy(facilities = List(3) { fac(it.toLong()) })))
    }

    @Test fun everyRuleCodeIsUniqueAndPrioritiesAreInRange() {
        val worst = AdvisorFacts(
            tick = 1, cashUnits = 900_000, netWorthUnits = 3_000_000, debtUnits = 0, monthRevenueUnits = 100_000, monthNetIncomeUnits = -1,
            dailyPayrollUnits = 5_000, facilities = List(5) { fac(it.toLong(), util = 0, cond = 100_000, blocked = if (it == 0) "x" else null, stock = 500_000) },
            vacancies = 5, averageMoralePpm = 100_000, underpaidCohorts = 2, industryCount = 1, researchIdle = true, idleParcels = 1, contractOffers = 2,
            netMarginPpm = -10_000, currentRatioPpm = 100_000, loanRatePpm = 90_000,
        )
        val all = AdvisorRules.all.flatMap { it.check(worst, t) }
        assertEquals(all.size, all.map { it.code }.toSet().size)
        assertTrue(all.size >= 14)
        assertTrue(all.all { it.priority in 0..100 && it.title.isNotBlank() && it.detail.isNotBlank() })
    }

    @Test fun priorityIsValidated() {
        var failed = false
        try { Insight("x", Topic.MARKET, 101, "t", "d") } catch (e: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }
}
