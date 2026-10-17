package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.coal
import app.many.economytycoon.carbon.CarbonTestSupport.emitter
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RecipeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmissionCalculatorTest {
    private val calc = EmissionCalculator(StandardFactors.table)

    @Test fun burningCoalReleasesItsFactorTimesTheQuantity() {
        val e = calc.facilityDay(emitter(1, acme, coalUnits = 100))
        assertEquals(Tonnes.of(20), e.direct)
        assertEquals(Tonnes.of(20), e.of(SourceKind.FUEL))
        assertEquals(FactorSource.TABLE, e.lines.single().source)
    }

    @Test fun biogenicFuelIsReportedButNotDirect() {
        val a = emitter(1, acme, coalUnits = 0).copy(consumed = mapOf(GoodId("biomass") to Quantity.units(10)))
        val e = calc.facilityDay(a)
        assertEquals(Tonnes.ZERO, e.direct)
        assertEquals(Tonnes.ofKg(900), e.biogenic)
    }

    @Test fun processChemistryReleasesPerBatch() {
        val a = emitter(1, acme, coalUnits = 0).copy(batchesByRecipe = mapOf(RecipeId("make_cement") to 2L))
        assertEquals(Tonnes.of(60), calc.facilityDay(a).of(SourceKind.PROCESS))
    }

    @Test fun anUnknownGoodAndAnUnknownRecipeCountAsZeroInsteadOfFailing() {
        val a = emitter(1, acme, coalUnits = 0).copy(consumed = mapOf(GoodId("mystery") to Quantity.units(50)), batchesByRecipe = mapOf(RecipeId("mystery_recipe") to 5L))
        val e = calc.facilityDay(a)
        assertEquals(Tonnes.ZERO, e.direct)
        assertTrue(e.lines.isEmpty())
    }

    @Test fun anUnknownRecipeFallsBackOnTheFamilyDefaultWhenContentNamesItsFamily() {
        val table = StandardFactors.table
        val (grams, source) = table.process(RecipeId("brew_unknown"), IndustryFamily.CHEMICALS)
        assertEquals(800_000L, grams)
        assertEquals(FactorSource.FAMILY_DEFAULT, source)
        assertEquals(FactorSource.NONE, table.process(RecipeId("brew_unknown"), IndustryFamily.FOOD).second)
    }

    @Test fun anEnergyCategoryGoodWithoutAnEntryUsesTheCategoryDefault() {
        val (factor, source) = StandardFactors.table.fuel(GoodId("peat"), GoodCategory.ENERGY)
        assertEquals(60_000L, factor.gramsPerUnit)
        assertEquals(FactorSource.CATEGORY_DEFAULT, source)
        assertEquals(FactorSource.NONE, StandardFactors.table.fuel(GoodId("peat"), null).second)
    }

    @Test fun electricityIsIndirectAndUsesTheNationalGridWhenKnown() {
        val a = emitter(1, acme, coalUnits = 0, electricity = Quantity.units(100))
        val byDefault = calc.facilityDay(a)
        assertEquals(Tonnes.ofKg(2_500), byDefault.indirect)
        assertEquals(Tonnes.ZERO, byDefault.direct)
        val national = calc.facilityDay(a, gridGramsPerUnit = 100_000L)
        assertEquals(Tonnes.of(10), national.indirect)
        assertEquals(FactorSource.TABLE, national.lines.single().source)
    }

    @Test fun overheadIsReleasedEveryOperatingDay() {
        val table = StandardFactors.table.copy(overheadGramsPerDay = mapOf(FacilityTypeId("steel_mill") to 5_000_000L))
        val e = EmissionCalculator(table).facilityDay(emitter(1, acme, coalUnits = 0))
        assertEquals(Tonnes.of(5), e.of(SourceKind.OVERHEAD))
    }

    @Test fun aFacilityThatIsNotOperatingReleasesNothing() {
        val a = emitter(1, acme).copy(operating = false)
        assertEquals(Tonnes.ZERO, calc.facilityDay(a).direct)
    }

    @Test fun anEfficiencyRetrofitCutsFuelAndAlsoTheElectricityLine() {
        val a = emitter(1, acme, coalUnits = 100, electricity = Quantity.units(100))
        val base = calc.facilityDay(a)
        val cut = calc.facilityDay(a, effects = listOf(AbatementEffect(AbatementKind.EFFICIENCY_RETROFIT, 100_000L)))
        assertEquals(Tonnes.of(18), cut.direct)
        assertEquals(Tonnes.ofKg(2_250), cut.indirect)
        assertEquals(Tonnes.ofKg(2_000) + Tonnes.ofKg(250), cut.avoided)
        assertEquals(base.direct + base.indirect, cut.direct + cut.indirect + cut.avoided)
    }

    @Test fun aFuelSwitchActsOnlyOnItsFuel() {
        val a = emitter(1, acme, coalUnits = 100).copy(consumed = mapOf(coal to Quantity.units(100), GoodId("diesel") to Quantity.units(100)))
        val cut = calc.facilityDay(a, effects = listOf(AbatementEffect(AbatementKind.FUEL_SWITCH, 500_000L, "coal")))
        assertEquals(Tonnes.of(10), cut.lines.first { it.key == "coal" }.amount)
        assertEquals(Tonnes.of(8), cut.lines.first { it.key == "diesel" }.amount)
    }

    @Test fun captureTakesProcessEmissionsToo() {
        val a = emitter(1, acme, coalUnits = 0).copy(batchesByRecipe = mapOf(RecipeId("make_cement") to 1L))
        val cut = calc.facilityDay(a, effects = listOf(AbatementEffect(AbatementKind.CARBON_CAPTURE, 850_000L)))
        assertEquals(Tonnes.of(4) + Tonnes.ofKg(500), cut.direct)
    }

    @Test fun stackedMeasuresMultiplyTheirSurvivingShares() {
        val a = emitter(1, acme, coalUnits = 100)
        val cut = calc.facilityDay(a, effects = listOf(AbatementEffect(AbatementKind.EFFICIENCY_RETROFIT, 100_000L), AbatementEffect(AbatementKind.CARBON_CAPTURE, 500_000L)))
        assertEquals(Tonnes.of(9), cut.direct)
    }

    @Test fun effectsRejectAReductionOutsideZeroToOneHundredPercent() {
        val bad = runCatching { AbatementEffect(AbatementKind.FUEL_SWITCH, 1_500_000L) }
        assertTrue(bad.isFailure)
    }

    @Test fun tableKeysAreListedInOrder() {
        val keys = StandardFactors.table.goodKeys().map { it.key }
        assertEquals(keys.sorted(), keys)
        assertTrue("coal" in keys)
    }
}
