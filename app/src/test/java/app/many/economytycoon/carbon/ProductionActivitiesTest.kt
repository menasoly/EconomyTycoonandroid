package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.production.Facility
import app.many.economytycoon.production.FacilityReport
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.production.Inventory
import app.many.economytycoon.production.ProductionLine
import app.many.economytycoon.world.WorldTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionActivitiesTest {
    private val content = WorldTestSupport.content
    private val adapter = ProductionActivities(content, StandardFactors.table, { r -> if (r == RegionId(1)) uk else null })

    private fun facility(id: Long = 1, type: String = "steel_mill", region: Long = 1, recipes: List<String> = listOf("make_steel")): Facility {
        val f = Facility(FacilityId(id), FacilityTypeId(type), acme, RegionId(region), null, null, Tick(0), Inventory(Quantity.units(10_000)))
        for ((i, r) in recipes.withIndex()) f.lines += ProductionLine(LineId(id * 10 + i), i).also { it.recipe = RecipeId(r) }
        return f
    }

    private fun report(f: Facility, batches: Long = 4) = FacilityReport(f.id).also {
        it.batches = batches
        it.consumed[GoodId("coal")] = Quantity.units(30)
        it.energyUsed = Quantity.units(100)
        it.producedValue = Money.units(900)
    }

    @Test fun aReportBecomesAnActivityWithItsFacilitysOwnerCountryAndFamily() {
        val f = facility()
        val a = adapter.activity(report(f), f)!!
        assertEquals(acme, a.company)
        assertEquals(uk, a.country)
        assertEquals(FacilityId(1), a.facility)
        assertEquals(content.facilityTypeOrNull(FacilityTypeId("steel_mill"))!!.family, a.family)
        assertEquals(Quantity.units(30), a.consumed[GoodId("coal")])
        assertEquals(Quantity.units(100), a.electricityUsed)
        assertEquals(Money.units(900), a.outputValue)
        assertTrue(a.operating)
    }

    @Test fun batchesAreAttributedToTheRunningRecipes() {
        val f = facility(recipes = listOf("make_steel"))
        assertEquals(mapOf(RecipeId("make_steel") to 4L), adapter.activity(report(f, 4), f)!!.batchesByRecipe)
    }

    @Test fun severalRecipesShareTheBatchesAndTheSplitAddsUp() {
        val f = facility(recipes = listOf("make_steel", "make_pig_iron", "make_stainless"))
        val parts = adapter.activity(report(f, 10), f)!!.batchesByRecipe
        assertEquals(3, parts.size)
        assertEquals(10L, parts.values.sum())
    }

    @Test fun noBatchesMeansNoRecipeAttribution() {
        val f = facility()
        assertTrue(adapter.activity(report(f, 0), f)!!.batchesByRecipe.isEmpty())
    }

    @Test fun disabledLinesTakeNoShare() {
        val f = facility(recipes = listOf("make_steel", "make_pig_iron"))
        f.lines[1].enabled = false
        assertEquals(setOf(RecipeId("make_steel")), adapter.activity(report(f, 5), f)!!.batchesByRecipe.keys)
    }

    @Test fun energySpendCountsFuelsAndElectricityAtReferencePrices() {
        val f = facility()
        val a = adapter.activity(report(f), f)!!
        assertTrue(a.energySpend.isPositive)
        val coalOnly = FacilityReport(f.id).also { it.consumed[GoodId("iron_ore")] = Quantity.units(50) }
        assertEquals(Money.ZERO, adapter.activity(coalOnly, f)!!.energySpend)
    }

    @Test fun aFacilityInAnUnknownRegionOrOfAnUnknownTypeIsSkipped() {
        val abroad = facility(region = 9)
        assertNull(adapter.activity(report(abroad), abroad))
        val strange = facility(type = "no_such_type")
        assertNull(adapter.activity(report(strange), strange))
    }

    @Test fun aReportForAnotherFacilityIsIgnored() {
        val f = facility(1)
        assertNull(adapter.activity(FacilityReport(FacilityId(2)), f))
    }

    @Test fun aShutDownFacilityIsNotOperating() {
        val f = facility()
        f.status = FacilityStatus.SHUTDOWN
        assertFalse(adapter.activity(report(f), f)!!.operating)
    }

    @Test fun activitiesComeOutInFacilityOrderAndSkipMissingOnes() {
        val a = facility(2); val b = facility(1)
        val list = adapter.activities(listOf(report(a), report(b), FacilityReport(FacilityId(3)))) { id -> listOf(a, b).firstOrNull { it.id == id } }
        assertEquals(listOf(FacilityId(1), FacilityId(2)), list.map { it.facility })
    }

    @Test fun anAdaptedActivityFeedsTheCalculator() {
        val f = facility()
        val e = EmissionCalculator(StandardFactors.table, content).facilityDay(adapter.activity(report(f, 2), f)!!)
        assertEquals(Tonnes.of(6), e.of(SourceKind.FUEL))
        assertEquals(Tonnes.of(8), e.of(SourceKind.PROCESS))
        assertTrue(IndustryFamily.METALS == e.activity.family || e.activity.family != IndustryFamily.FOOD)
        assertEquals(CountryId(1), e.activity.country)
    }
}
