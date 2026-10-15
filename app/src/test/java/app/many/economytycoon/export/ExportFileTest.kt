package app.many.economytycoon.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFileTest {
    @Test fun slugsKeepLettersAndDigitsOnly() {
        assertEquals("north-field-co", ExportFile.slug("North Field Co."))
        assertEquals("a-b-c", ExportFile.slug("  a -- b / c  "))
        assertEquals("company", ExportFile.slug("***"))
        assertEquals("acme-2", ExportFile.slug("ACME 2"))
    }

    @Test fun fileNamesSayWhatAndWhen() {
        assertEquals("north-field-co-yearly-day720.csv", ExportFile.nameFor("North Field Co.", ExportKind.YEARLY, 720))
    }

    @Test fun everyKindHasADistinctStemAndLabel() {
        assertEquals(ExportKind.values().size, ExportKind.values().map { it.stem }.toSet().size)
        assertEquals(ExportKind.values().size, ExportKind.values().map { it.label }.toSet().size)
        assertTrue(ExportKind.values().all { it.blurb.endsWith(".") })
    }

    @Test fun anExportKnowsWhetherItHoldsRows() {
        assertTrue(ExportFile(ExportKind.ACTIVITY, "x.csv", "h\r\n", 0).isEmpty)
        assertFalse(ExportFile(ExportKind.ACTIVITY, "x.csv", "h\r\nr\r\n", 1).isEmpty)
        assertEquals("text/csv", ExportFile(ExportKind.ACTIVITY, "x.csv", "", 0).mime)
    }
}
