package app.many.economytycoon.ui.present

import app.many.economytycoon.content.catalog.CatalogBuilder
import app.many.economytycoon.content.catalog.CatalogFixtures
import app.many.economytycoon.content.catalog.CatalogReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndustryCatalogHandmadeTest {
    private val report: CatalogReport = CatalogBuilder.build(CatalogFixtures.packs)

    @Test fun rowsFollowTheReportOrder() {
        assertEquals(listOf("baking", "milling", "pastry"), IndustryCatalogPresenter.build(report).rows.map { it.key })
    }

    @Test fun rowSubtitleCountsGoodsRecipesAndTechnologies() {
        val row = IndustryCatalogPresenter.build(report, CatalogFilter(query = "milling")).rows.single()
        assertEquals("Textiles, 1 good, 1 recipe, 1 technology", row.subtitle)
    }

    @Test fun marginFormatsAsLowMiddleHigh() {
        val row = IndustryCatalogPresenter.build(report, CatalogFilter(query = "milling")).rows.single()
        assertEquals("66.7% / 66.7% / 66.7%", row.margin)
    }

    @Test fun healthyAndLosingIndustriesGetDifferentTones() {
        val rows = IndustryCatalogPresenter.build(report).rows.associateBy { it.key }
        assertEquals(Tone.GOOD, rows.getValue("milling").tone)
        assertEquals(Tone.BAD, rows.getValue("pastry").tone)
    }

    @Test fun tilesCountTheSmallContent() {
        val tiles = IndustryCatalogPresenter.build(report).tiles.associate { it.label to it.value }
        assertEquals("3", tiles["Industries"]); assertEquals("4", tiles["Goods"]); assertEquals("3", tiles["Recipes"])
    }

    @Test fun onlyOneFamilyIsOffered() {
        assertEquals(listOf("Textiles"), IndustryCatalogPresenter.build(report).families)
    }

    @Test fun detailListsGoodsRecipesFacilitiesMachinesAndTechnologies() {
        val found = IndustryCatalogPresenter.detail(report, "pastry") as CatalogLookup.Found
        assertEquals(listOf("Goods", "Recipes", "Facilities", "Machines", "Technologies"), found.detail.sections.map { it.heading })
        assertEquals(listOf("Cake"), found.detail.sections.first().items)
        assertEquals(listOf("Pastry tech"), found.detail.sections.last().items)
    }

    @Test fun detailLeavesOutMachinesWhenThereAreNone() {
        val found = IndustryCatalogPresenter.detail(report, "milling") as CatalogLookup.Found
        assertTrue(found.detail.sections.none { it.heading == "Machines" })
    }

    @Test fun detailTitleIsTheIndustryName() {
        val found = IndustryCatalogPresenter.detail(report, "baking") as CatalogLookup.Found
        assertEquals("Baking", found.detail.title)
    }

    @Test fun packTileShowsThePackId() {
        val found = IndustryCatalogPresenter.detail(report, "pastry") as CatalogLookup.Found
        assertEquals("mini-b", found.detail.tiles.first { it.label == "Pack" }.value)
    }

    @Test fun marginTileHasAnExplanatoryNote() {
        val found = IndustryCatalogPresenter.detail(report, "milling") as CatalogLookup.Found
        assertEquals("lowest / middle / highest", found.detail.tiles.first { it.label == "Margin" }.note)
    }

    @Test fun filterByPackOfTheSmallContent() {
        assertEquals(listOf("pastry"), IndustryCatalogPresenter.build(report, CatalogFilter(packId = "mini-b")).rows.map { it.key })
    }

    @Test fun filterThatMatchesNothingExplainsWhy() {
        val m = IndustryCatalogPresenter.build(report, CatalogFilter(packId = "mini-b", query = "milling"))
        assertTrue(m.rows.isEmpty()); assertEquals(IndustryCatalogPresenter.EMPTY, m.empty)
    }

    @Test fun missingDetailKeepsTheKey() {
        val r = IndustryCatalogPresenter.detail(report, "unknown")
        assertTrue(r is CatalogLookup.Missing)
        assertEquals("unknown", (r as CatalogLookup.Missing).key)
    }
}
