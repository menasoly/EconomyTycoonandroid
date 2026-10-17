package app.many.economytycoon.blueprint

import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import app.many.economytycoon.content.audit.RecipeEconomics
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueprintCostTest {
    private val est = CostEstimator(T.content)

    @Test fun unknownFacilityHasNoEstimate() {
        assertNull(est.estimate(BlueprintSpec(FacilityTypeId("gone"), emptyList())))
    }

    @Test fun capitalAddsBuildLinesMachinesAndStorage() {
        val e = est.estimate(T.sampleSpec())!!
        assertEquals(Money.units(50_000), e.buildCost)
        assertEquals(Money.units(100), e.buildMaterials)
        // two extra lines at 15% of the build cost each
        assertEquals(Money.units(15_000), e.extraLines)
        // oven + oven + mixer
        assertEquals(Money.units(24_000), e.machines)
        // one expansion at 20%
        assertEquals(Money.units(10_000), e.storage)
        assertEquals(e.buildCost + e.buildMaterials + e.extraLines + e.machines + e.storage, e.capital)
    }

    @Test fun runningCostsComeFromTheType() {
        val e = est.estimate(T.sampleSpec())!!
        assertEquals(Money.units(20), e.upkeepPerDay)
        assertEquals(Money.units(60), e.overheadStaffPerDay)
        assertEquals(Money.units(12), e.machineUpkeepPerDay)
    }

    @Test fun lineProfitUsesRecipeEconomicsScaledByMachineThroughput() {
        val e = est.estimate(BlueprintSpec(T.bakery, listOf(T.line(T.makeBread, listOf(T.oven)))))!!
        val base = RecipeEconomics.of(T.content, T.content.recipe(T.makeBread)).profitPerLineDay
        val line = e.lineEconomies.single()
        assertEquals(1_250_000L, line.throughputPpm)
        assertEquals(Money(base * 125 / 100 - Money.units(5).raw), line.profitPerDay)
        assertNull(line.idleReason)
    }

    @Test fun linesWithoutTheirMachineEarnNothing() {
        val e = est.estimate(BlueprintSpec(T.bakery, listOf(T.line(T.makeBread))))!!
        val line = e.lineEconomies.single()
        assertEquals("needs Oven", line.idleReason)
        assertEquals(0L, line.throughputPpm)
        assertEquals(Money.ZERO, e.grossPerDay)
    }

    @Test fun disabledAndEmptyLinesAreIdle() {
        val e = est.estimate(BlueprintSpec(T.bakery, listOf(T.line(T.makeFlour, enabled = false), T.line(null))))!!
        assertEquals(listOf("switched off", "no recipe"), e.lineEconomies.map { it.idleReason })
        assertEquals(Money.ZERO, e.grossPerDay)
        assertNull(e.paybackDays)
    }

    @Test fun profitableSetupHasPaybackAndReturn() {
        val e = est.estimate(BlueprintSpec(T.bakery, listOf(T.line(T.makeBread, listOf(T.oven)))))!!
        assertTrue(e.isProfitable)
        val days = e.paybackDays
        assertNotNull(days)
        assertTrue(days!! > 0)
        // paying the net per day for that many days covers the capital, one day fewer does not
        assertTrue(e.netPerDay.raw * days >= e.capital.raw)
        assertTrue(e.netPerDay.raw * (days - 1) < e.capital.raw)
        assertTrue(e.annualReturnPpm > 0)
    }

    @Test fun moreMachinesMeanMoreCapital() {
        val one = est.estimate(BlueprintSpec(T.bakery, listOf(T.line(T.makeBread, listOf(T.oven)))))!!
        val two = est.estimate(BlueprintSpec(T.bakery, listOf(T.line(T.makeBread, listOf(T.oven, T.mixer)))))!!
        assertEquals(Money.units(4_000), two.machines - one.machines)
        assertTrue(two.machineUpkeepPerDay > one.machineUpkeepPerDay)
    }

    @Test fun estimateIsDeterministic() {
        assertEquals(est.estimate(T.sampleSpec()), est.estimate(T.sampleSpec()))
    }
}
