package app.many.economytycoon.session

import app.many.economytycoon.activity.ActivityArea
import app.many.economytycoon.activity.ActivityEntry
import app.many.economytycoon.activity.Origin
import app.many.economytycoon.consolidation.GroupFixture
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import app.many.economytycoon.export.Csv
import app.many.economytycoon.export.ExportKind
import app.many.economytycoon.intel.LineFlow
import app.many.economytycoon.intel.SectorMath
import app.many.economytycoon.reporting.AnnualReport
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.present.PresentSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportsTest {
    private fun cols(t: ExportTables.Table) = t.header.size

    @Test fun incomeListsEveryLineWithPlainNumbers() {
        val t = ExportTables.income(GroupFixture.income(sales = 10_000, cogs = 4_000, wages = 2_000, tax = 500, interest = 100))
        assertEquals(listOf("Line", "Amount"), t.header)
        assertEquals("10000.00", t.rows.first { it[0] == "Total revenue" }[1])
        assertEquals("3400.00", t.rows.first { it[0] == "Net income" }[1])
        assertTrue(t.rows.all { it.size == 2 })
    }

    @Test fun balanceSplitsIntoThreeSections() {
        val t = ExportTables.balance(GroupFixture.balance())
        assertEquals(setOf("Assets", "Liabilities", "Equity"), t.rows.map { it[0] }.toSet())
        assertEquals("25000.00", t.rows.first { it[1] == "Total assets" }[2])
        assertEquals("17000.00", t.rows.first { it[1] == "Total equity" }[2])
    }

    @Test fun yearlyHasOneRowPerYearAndBlanksWhereThereIsNoFigure() {
        val a = AnnualReport(CompanyId(1), "Test", 0, Tick(0), Tick(360), false, GroupFixture.income(sales = 0, cogs = 0, wages = 0, tax = 0, interest = 0), GroupFixture.balance(), GroupFixture.balance(), null)
        val b = AnnualReport(CompanyId(1), "Test", 1, Tick(360), Tick(460), true, GroupFixture.income(), GroupFixture.balance(), GroupFixture.balance(), a)
        val t = ExportTables.yearly(listOf(a, b))
        assertEquals(2, t.rows.size)
        assertEquals("2000", t.rows[0][0])
        assertEquals("", t.rows[0][5])
        assertEquals("partial", t.rows[1][1])
        assertEquals(cols(t), t.rows[1].size)
    }

    @Test fun historyHasOneRowPerSample() {
        val t = ExportTables.history(listOf(StatPoint(5, Money.units(10), Money.units(20), Money.units(3), Money.units(1), 12), StatPoint(10, Money.units(11), Money.units(21), Money.units(4), Money.units(2), 14)))
        assertEquals(listOf("5", "10.00", "20.00", "3.00", "1.00", "12"), t.rows[0])
        assertEquals(2, t.rows.size)
    }

    @Test fun activityKeepsTheWordsAndFlagsMoney() {
        val t = ExportTables.activity(listOf(ActivityEntry(1, 7, Origin.PLAYER, ActivityArea.FINANCE, "Borrowed 10 for 90 days, in full", true)))
        assertEquals(listOf("7", "Finance", "You", "Borrowed 10 for 90 days, in full", "yes"), t.rows.single())
        assertTrue(Csv.table(t.header, t.rows).contains("\"Borrowed 10 for 90 days, in full\""))
    }

    @Test fun sectorsCarryNamesSharesAndQuantities() {
        val wheat = GoodId("wheat")
        val s = SectorMath.build(listOf(LineFlow(CompanyId(1), FacilityId(1), wheat, 12_500, 1_000_000, true)), { "Alpha" }, { 1_250_000 })
        val t = ExportTables.sectors(s) { "Wheat" }
        val r = t.rows.single()
        assertEquals("Wheat", r[0])
        assertEquals("Alpha", r[2])
        assertEquals("100.00", r[3])
        assertEquals("12.5", r[4])
        assertEquals("25.00", r.last())
        assertEquals(cols(t), r.size)
    }

    @Test fun fileNamesAndRowCountsCarryThrough() {
        val f = ExportTables.file(ExportKind.INCOME_MONTH, "Test Works", 30, ExportTables.income(GroupFixture.income()))
        assertEquals("test-works-income-30d-day30.csv", f.fileName)
        assertEquals(23, f.rows)
        assertEquals(24, Csv.parse(f.text).size)
    }

    @Test fun aSessionBuildsEveryKindAndEachFileReadsBack() {
        val s = PresentSupport.session("Export Works", 17L)
        val x = s.exports()
        assertEquals(ExportKind.values().toList(), x.available())
        for (k in ExportKind.values()) {
            val f = x.build(k)!!
            val parsed = Csv.parse(f.text)
            assertTrue(k.name, parsed.isNotEmpty())
            assertEquals(k.name, f.rows + 1, parsed.size)
            assertTrue(k.name, parsed.all { it.size == parsed[0].size })
            assertTrue(f.fileName, f.fileName.startsWith("export-works-"))
        }
    }

    @Test fun activityExportListsWhatWasOrdered() {
        val s = PresentSupport.session("Export Works", 17L)
        val me = s.selectedCompany!!
        s.issueNow(Command.Rename(me, "Export Works Ltd"))
        val f = s.exports().build(ExportKind.ACTIVITY)!!
        assertEquals(1, f.rows)
        assertTrue(f.text, f.text.contains("Renamed"))
        assertFalse(f.isEmpty)
    }
}
