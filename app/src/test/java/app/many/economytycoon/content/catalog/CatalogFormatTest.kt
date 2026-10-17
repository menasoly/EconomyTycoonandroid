package app.many.economytycoon.content.catalog

import app.many.economytycoon.content.ExpandedContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFormatTest {
    private val report: CatalogReport by lazy { CatalogBuilder.build(ExpandedContent.packs, ExpandedContent.expansionPackIds.toSet()) }

    @Test fun noMarginsReadAsNotAvailable() {
        assertEquals("n/a", CatalogFormat.margin(MarginStats.NONE))
    }

    @Test fun marginsReadAsLowMiddleHigh() {
        assertEquals("10.0% / 20.0% / 30.5%", CatalogFormat.margin(MarginStats(3, 100_000L, 200_000L, 305_000L)))
    }

    @Test fun negativeMarginsKeepTheirSign() {
        assertEquals("-5.0% / 0.0% / 5.0%", CatalogFormat.margin(MarginStats(3, -50_000L, 0L, 50_000L)))
    }

    @Test fun paybackShowsARangeOnlyWhenItDiffers() {
        assertEquals("500d", CatalogFormat.payback(500L, 500L))
        assertEquals("500-700d", CatalogFormat.payback(500L, 700L))
        assertEquals("n/a", CatalogFormat.payback(null, null))
        assertEquals("n/a", CatalogFormat.payback(500L, null))
    }

    @Test fun textStartsWithPacksThenIndustriesThenTotal() {
        val lines = CatalogFormat.text(report).lines()
        assertEquals("Content packs", lines.first())
        assertTrue(lines.contains("Industries"))
        assertTrue(lines.last().startsWith("Total: 252 goods, 257 recipes"))
    }

    @Test fun textHasOneLinePerPackAndIndustry() {
        val lines = CatalogFormat.text(report).lines()
        assertEquals(1 + 8 + 1 + 1 + 34 + 1 + 1, lines.size)
    }

    @Test fun packLinesNameThePackAndItsCounts() {
        val line = CatalogFormat.packLine(report.pack("aerospace")!!)
        assertTrue(line.startsWith("aerospace"))
        assertTrue(line.contains("goods  28"))
        assertTrue(line.contains("recipes  28"))
    }

    @Test fun industryLinesNameTheFamilyAndPayback() {
        val line = CatalogFormat.industryLine(report.industry("space_systems")!!)
        assertTrue(line.startsWith("space_systems"))
        assertTrue(line.contains("Aerospace"))
        assertTrue(line.endsWith("1274d"))
    }

    @Test fun csvHasAHeaderAndOneRowPerIndustry() {
        val lines = CatalogFormat.csv(report).lines()
        assertEquals(35, lines.size)
        assertTrue(lines.first().startsWith("industry,pack,family"))
        assertTrue(lines.drop(1).all { it.split(",").size == 13 })
    }

    @Test fun csvRowsStartWithTheIndustryKey() {
        val keys = CatalogFormat.csv(report).lines().drop(1).map { it.substringBefore(',') }
        assertEquals(report.industries.map { it.key }, keys)
    }

    @Test fun textIsStableAcrossBuilds() {
        val again = CatalogBuilder.build(ExpandedContent.packs, ExpandedContent.expansionPackIds.toSet())
        assertEquals(CatalogFormat.text(report), CatalogFormat.text(again))
    }

    @Test fun anEmptyReportStillFormats() {
        val text = CatalogFormat.text(CatalogReport(emptyList(), emptyList()))
        assertTrue(text.contains("Total: 0 goods, 0 recipes, 0 facility types, 0 techs"))
    }
}
