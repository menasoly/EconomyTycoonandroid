package app.many.economytycoon.export

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {
    @Test fun plainFieldsAreLeftAlone() {
        assertEquals("Revenue", Csv.field("Revenue"))
        assertEquals("", Csv.field(""))
        assertEquals("12.50", Csv.field("12.50"))
    }

    @Test fun commasQuotesAndLineBreaksForceQuoting() {
        assertEquals("\"Cost of goods, net\"", Csv.field("Cost of goods, net"))
        assertEquals("\"She said \"\"yes\"\"\"", Csv.field("She said \"yes\""))
        assertEquals("\"two\nlines\"", Csv.field("two\nlines"))
    }

    @Test fun textThatLooksLikeAFormulaIsDefused() {
        assertEquals("'=SUM(A1:A9)", Csv.field("=SUM(A1:A9)"))
        assertEquals("'@cmd", Csv.field("@cmd"))
        assertEquals("'+name", Csv.field("+name"))
    }

    @Test fun signedNumbersAreNotDefused() {
        assertEquals("-12.50", Csv.field("-12.50"))
        assertEquals("+5", Csv.field("+5"))
    }

    @Test fun aTableHasAHeaderAndOneLinePerRow() {
        val t = Csv.table(listOf("a", "b"), listOf(listOf("1", "x,y"), listOf("2", "z")))
        assertEquals("a,b\r\n1,\"x,y\"\r\n2,z\r\n", t)
    }

    @Test fun whatIsWrittenCanBeReadBack() {
        val rows = listOf(listOf("Name", "Note"), listOf("Alpha, Inc.", "He said \"hi\""), listOf("Beta", "line\nbreak"), listOf("", "x"))
        val back = Csv.parse(Csv.table(rows[0], rows.drop(1)))
        assertEquals(rows, back)
    }

    @Test fun parsingSkipsBlankLinesAndHandlesAMissingFinalNewline() {
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), Csv.parse("a,b\r\n\r\nc,d"))
        assertTrue(Csv.parse("").isEmpty())
    }

    @Test fun moneyIsWrittenWithTwoDecimals() {
        assertEquals("1250.00", Plain.money(Money.units(1_250)))
        assertEquals("-3.50", Plain.money(Money(-35_000)))
        assertEquals("0.01", Plain.money(Money(100)))
        assertEquals("0.00", Plain.money(Money(10)))
        assertEquals("12345678.90", Plain.money(Money(123_456_789_000)))
    }

    @Test fun quantitiesDropTrailingZeros() {
        assertEquals("12", Plain.quantity(Quantity.units(12)))
        assertEquals("12.5", Plain.quantity(Quantity(12_500)))
        assertEquals("0.001", Plain.quantity(Quantity(1)))
        assertEquals("1000", Plain.quantity(Quantity.units(1_000)))
    }

    @Test fun percentagesAreTwoDecimals() {
        assertEquals("25.00", Plain.percent(250_000))
        assertEquals("-1.25", Plain.percent(-12_500))
        assertEquals("100.00", Plain.percent(1_000_000))
    }

    @Test fun missingAmountsAreEmpty() {
        assertEquals("", Plain.optional(null))
        assertEquals("5.00", Plain.optional(Money.units(5)))
    }
}
