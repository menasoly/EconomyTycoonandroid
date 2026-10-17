package app.many.economytycoon.content.catalog

import app.many.economytycoon.content.IndustryFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogHandmadeTest {
    private val report = CatalogBuilder.build(CatalogFixtures.packs)

    @Test fun twoPacksAndThreeIndustries() {
        assertEquals(listOf("mini-a", "mini-b"), report.packs.map { it.packId })
        assertEquals(listOf("baking", "milling", "pastry"), report.industries.map { it.key }.sorted())
    }

    @Test fun industriesComeInPackOrderThenKey() {
        assertEquals(listOf("baking", "milling", "pastry"), report.industries.map { it.key })
    }

    @Test fun packCounts() {
        val a = report.pack("mini-a")!!
        assertEquals(2, a.industries); assertEquals(3, a.goods); assertEquals(2, a.recipes); assertEquals(2, a.facilityTypes)
        assertEquals(0, a.machines); assertEquals(1, a.techs); assertEquals(1, a.demandProfiles)
        val b = report.pack("mini-b")!!
        assertEquals(1, b.industries); assertEquals(1, b.goods); assertEquals(1, b.recipes); assertEquals(1, b.machines); assertEquals(1, b.techs)
    }

    @Test fun packVersionIsPrinted() {
        assertEquals("mini-a@1.0", report.pack("mini-a")?.version)
    }

    @Test fun recipeMarginsAreWorkedOutByHand() {
        assertEquals(666_666L, report.industry("milling")!!.margins.medianPpm)
        assertEquals(333_333L, report.industry("baking")!!.margins.medianPpm)
    }

    @Test fun packMarginSpansItsRecipes() {
        val m = report.pack("mini-a")!!.margins
        assertEquals(2, m.count); assertEquals(333_333L, m.minPpm); assertEquals(666_666L, m.maxPpm)
    }

    @Test fun industryGoodsAreWhatItsRecipesMake() {
        assertEquals(listOf("flour"), report.industry("milling")!!.goodKeys)
        assertEquals(listOf("bread"), report.industry("baking")!!.goodKeys)
        assertEquals(listOf("cake"), report.industry("pastry")!!.goodKeys)
    }

    @Test fun industryFacilitiesAndMachines() {
        assertEquals(listOf("mill"), report.industry("milling")!!.facilityKeys)
        assertEquals(listOf("patisserie"), report.industry("pastry")!!.facilityKeys)
        assertEquals(listOf("mixer"), report.industry("pastry")!!.machineKeys)
        assertTrue(report.industry("milling")!!.machineKeys.isEmpty())
    }

    @Test fun technologiesAreLinkedThroughTheirUnlocks() {
        assertEquals(listOf("milling_tech"), report.industry("milling")!!.techKeys)
        assertEquals(listOf("pastry_tech"), report.industry("pastry")!!.techKeys)
        assertTrue(report.industry("baking")!!.techKeys.isEmpty())
    }

    @Test fun paybackIsBuildCostOverNetIncome() {
        assertNotNull(report.industry("milling")!!.bestPaybackDays)
        val days = report.industry("milling")!!.bestPaybackDays!!
        assertTrue("days $days", days > 0L)
    }

    @Test fun anIndustryWithOneFacilityHasEqualBestAndWorst() {
        val e = report.industry("baking")!!
        assertEquals(e.bestPaybackDays, e.worstPaybackDays)
    }

    @Test fun familyIsKept() {
        assertTrue(report.industries.all { it.family == IndustryFamily.TEXTILES })
        assertEquals(3, report.industriesIn(IndustryFamily.TEXTILES).size)
        assertTrue(report.industriesIn(IndustryFamily.MINING).isEmpty())
    }

    @Test fun totalsAddUpAcrossPacks() {
        assertEquals(4, report.totalGoods); assertEquals(3, report.totalRecipes)
        assertEquals(3, report.totalFacilityTypes); assertEquals(2, report.totalTechs)
    }

    @Test fun limitingToTheSecondPackStillUsesTheFirstForPrices() {
        val only = CatalogBuilder.build(CatalogFixtures.packs, setOf("mini-b"))
        assertEquals(listOf("pastry"), only.industries.map { it.key })
        assertEquals(1, only.industry("pastry")!!.recipes)
        assertNull(only.industry("milling"))
    }

    @Test fun cakeMarginIsMeasuredAgainstItsIngredients() {
        val m = report.industry("pastry")!!.margins
        // inputs 25 + 15, labor 10, outputs 44: profit 44 - 50 is a loss
        assertTrue(m.medianPpm < 0L)
    }

    @Test fun textShowsBothPacks() {
        val text = CatalogFormat.text(report)
        assertTrue(text.contains("mini-a"))
        assertTrue(text.contains("mini-b"))
        assertTrue(text.endsWith("Total: 4 goods, 3 recipes, 3 facility types, 2 techs"))
    }

    @Test fun csvHasThreeRows() {
        assertEquals(4, CatalogFormat.csv(report).lines().size)
    }
}
