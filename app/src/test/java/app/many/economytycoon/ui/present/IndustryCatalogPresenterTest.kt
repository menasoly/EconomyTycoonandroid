package app.many.economytycoon.ui.present

import app.many.economytycoon.content.ExpandedContent
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.content.catalog.CatalogBuilder
import app.many.economytycoon.content.catalog.CatalogReport
import app.many.economytycoon.content.catalog.MarginStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndustryCatalogPresenterTest {
    private val report: CatalogReport by lazy { CatalogBuilder.build(ExpandedContent.packs, ExpandedContent.expansionPackIds.toSet()) }

    @Test fun unfilteredModelListsEveryIndustry() {
        val m = IndustryCatalogPresenter.build(report)
        assertEquals(34, m.rows.size)
        assertEquals("", m.empty)
    }

    @Test fun tilesShowTheTotals() {
        val tiles = IndustryCatalogPresenter.build(report).tiles.associate { it.label to it.value }
        assertEquals("34", tiles["Industries"]); assertEquals("252", tiles["Goods"]); assertEquals("257", tiles["Recipes"])
        assertEquals("54", tiles["Facilities"]); assertEquals("65", tiles["Technologies"])
    }

    @Test fun packChoicesFollowTheReport() {
        assertEquals(ExpandedContent.expansionPackIds, IndustryCatalogPresenter.build(report).packs)
    }

    @Test fun familyChoicesAreUniqueAndOrdered() {
        val f = IndustryCatalogPresenter.build(report).families
        assertEquals(f.distinct(), f)
        assertTrue("Chemicals" in f && "Healthcare" in f)
    }

    @Test fun filteringByPackKeepsOnlyThatPack() {
        val m = IndustryCatalogPresenter.build(report, CatalogFilter(packId = "medical_devices"))
        assertEquals(5, m.rows.size)
    }

    @Test fun filteringByFamily() {
        val m = IndustryCatalogPresenter.build(report, CatalogFilter(family = IndustryFamily.AUTOMOTIVE))
        assertEquals(5, m.rows.size)
    }

    @Test fun filteringByNameIgnoresCase() {
        val m = IndustryCatalogPresenter.build(report, CatalogFilter(query = "  SPACE SYSTEMS "))
        assertEquals(listOf("space_systems"), m.rows.map { it.key })
    }

    @Test fun filterQueryMatchesTheKeyWithSpaces() {
        val m = IndustryCatalogPresenter.build(report, CatalogFilter(query = "two wheelers"))
        assertEquals(listOf("two_wheelers"), m.rows.map { it.key })
    }

    @Test fun filtersCombine() {
        val m = IndustryCatalogPresenter.build(report, CatalogFilter(packId = "chemicals", query = "polymers"))
        assertEquals(listOf("engineering_polymers"), m.rows.map { it.key })
    }

    @Test fun noMatchGivesTheEmptyMessage() {
        val m = IndustryCatalogPresenter.build(report, CatalogFilter(query = "zzz"))
        assertTrue(m.rows.isEmpty())
        assertEquals(IndustryCatalogPresenter.EMPTY, m.empty)
    }

    @Test fun rowsCarryTitleSubtitleAndMargin() {
        val row = IndustryCatalogPresenter.build(report, CatalogFilter(query = "biologics")).rows.single()
        assertEquals("biologics", row.key)
        assertTrue(row.title.isNotBlank())
        assertTrue(row.subtitle.startsWith("Pharmaceuticals, 9 goods"))
        assertTrue(row.margin.contains("%"))
        assertTrue(row.payback.endsWith("d"))
    }

    @Test fun marginTonesGrade() {
        assertEquals(Tone.MUTED, IndustryCatalogPresenter.marginTone(0L, 0))
        assertEquals(Tone.BAD, IndustryCatalogPresenter.marginTone(30_000L, 3))
        assertEquals(Tone.WARN, IndustryCatalogPresenter.marginTone(70_000L, 3))
        assertEquals(Tone.GOOD, IndustryCatalogPresenter.marginTone(200_000L, 3))
    }

    @Test fun everyExpansionIndustryIsHealthy() {
        assertTrue(IndustryCatalogPresenter.build(report).rows.all { it.tone == Tone.GOOD })
    }

    @Test fun detailOfAKnownIndustry() {
        val found = IndustryCatalogPresenter.detail(report, "space_systems") as CatalogLookup.Found
        assertEquals("Space systems", found.detail.title)
        assertEquals(listOf("Pack", "Family", "Margin", "Payback"), found.detail.tiles.map { it.label })
        assertTrue(found.detail.sections.any { it.heading == "Goods" && it.items.size == 4 })
    }

    @Test fun detailSectionsSkipEmptyLists() {
        val found = IndustryCatalogPresenter.detail(report, "coatings_and_sealants") as CatalogLookup.Found
        assertTrue(found.detail.sections.none { it.items.isEmpty() })
    }

    @Test fun detailItemsAreReadable() {
        val found = IndustryCatalogPresenter.detail(report, "space_systems") as CatalogLookup.Found
        val goods = found.detail.sections.first { it.heading == "Goods" }.items
        assertTrue(goods.all { it.first().isUpperCase() && !it.contains('_') })
    }

    @Test fun detailOfAnUnknownIndustryIsMissing() {
        assertEquals(CatalogLookup.Missing("nope"), IndustryCatalogPresenter.detail(report, "nope"))
    }

    @Test fun marginTileUsesTheTone() {
        val found = IndustryCatalogPresenter.detail(report, "biologics") as CatalogLookup.Found
        assertEquals(Tone.GOOD, found.detail.tiles.first { it.label == "Margin" }.tone)
    }

    @Test fun anEmptyReportGivesAnEmptyModel() {
        val m = IndustryCatalogPresenter.build(CatalogReport(emptyList(), emptyList()))
        assertTrue(m.rows.isEmpty()); assertEquals(IndustryCatalogPresenter.EMPTY, m.empty)
    }

    @Test fun matchesHandlesBlankQuery() {
        val e = report.industries.first()
        assertTrue(IndustryCatalogPresenter.matches(e, CatalogFilter(query = "   ")))
    }

    @Test fun marginStatsWithNoRecipesAreMutedInTheRow() {
        val e = report.industries.first().copy(margins = MarginStats.NONE)
        assertEquals(Tone.MUTED, IndustryCatalogPresenter.row(e).tone)
        assertEquals("n/a", IndustryCatalogPresenter.row(e).margin)
    }
}
