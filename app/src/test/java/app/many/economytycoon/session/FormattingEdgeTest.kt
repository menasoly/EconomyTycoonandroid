package app.many.economytycoon.session

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingEdgeTest {
    @Test fun durationUsesTheTwoLargestUnits() {
        assertEquals("0 days", Formatting.duration(0))
        assertEquals("0 days", Formatting.duration(-4))
        assertEquals("1 day", Formatting.duration(1))
        assertEquals("29 days", Formatting.duration(29))
        assertEquals("1 month", Formatting.duration(30))
        assertEquals("1 month 5 days", Formatting.duration(35))
        assertEquals("1 year", Formatting.duration(360))
        assertEquals("1 year 1 month", Formatting.duration(390))
        assertEquals("2 years 3 months", Formatting.duration(360 * 2 + 30 * 3 + 7))
    }

    @Test fun pluralHandlesTheEndingsTheGameUses() {
        assertEquals("1 day", Formatting.plural(1, "day"))
        assertEquals("0 days", Formatting.plural(0, "day"))
        assertEquals("2 facilities", Formatting.plural(2, "facility"))
        assertEquals("2 days", Formatting.plural(2, "day"))
        assertEquals("3 taxes", Formatting.plural(3, "tax"))
        assertEquals("2 branches", Formatting.plural(2, "branch"))
        assertEquals("2 businesses", Formatting.plural(2, "business"))
    }

    @Test fun percentRoundsToTenthsAndKeepsTheSign() {
        assertEquals("0.0%", Formatting.percentPpm(0))
        assertEquals("0.0%", Formatting.percentPpm(-400))
        assertEquals("0.1%", Formatting.percentPpm(600))
        assertEquals("-0.1%", Formatting.percentPpm(-600))
        assertEquals("12.5%", Formatting.percentPpm(125_000))
        assertEquals("100.0%", Formatting.percentPpm(1_000_000))
        assertEquals("12.5%", Formatting.percent(Rate(125_000)))
    }

    @Test fun signedPercentAddsAPlusOnlyForRealGains() {
        assertEquals("+3.2%", Formatting.signedPercentPpm(32_000))
        assertEquals("-1.0%", Formatting.signedPercentPpm(-10_000))
        assertEquals("0.0%", Formatting.signedPercentPpm(0))
        assertEquals("0.0%", Formatting.signedPercentPpm(300))
    }

    @Test fun ratiosShowTwoDecimals() {
        assertEquals("1.85x", Formatting.ratioTimes(1_850_000))
        assertEquals("0.00x", Formatting.ratioTimes(0))
        assertEquals("0.05x", Formatting.ratioTimes(50_000))
        assertEquals("-2.50x", Formatting.ratioTimes(-2_500_000))
        assertEquals("10.00x", Formatting.ratioTimes(10_000_000))
    }

    @Test fun signedMoneyMarksOnlyPositiveAmounts() {
        assertEquals("+" + Formatting.money(Money.units(5)), Formatting.signedMoney(Money.units(5)))
        assertEquals(Formatting.money(Money.ZERO), Formatting.signedMoney(Money.ZERO))
        assertEquals(Formatting.money(Money.units(-5)), Formatting.signedMoney(Money.units(-5)))
    }

    @Test fun datesStartAtTheStartOfTheCalendar() {
        assertEquals("1 Jan 2000", Formatting.date(Tick(0)))
        assertEquals("January 2000", Formatting.monthYear(Tick(0)))
        assertEquals("1 Feb 2000", Formatting.date(Tick(30)))
        assertEquals("1 Jan 2001", Formatting.date(Tick(360)))
        assertEquals("December 2000", Formatting.monthYear(Tick(359)))
    }

    @Test fun groupedInsertsSeparatorsEveryThreeDigits() {
        assertEquals("0", Formatting.grouped(0))
        assertEquals("999", Formatting.grouped(999))
        assertEquals("1,000", Formatting.grouped(1_000))
        assertEquals("1,234,567", Formatting.grouped(1_234_567))
        assertEquals("-1,234", Formatting.grouped(-1_234))
    }

    @Test fun compactMoneyIsNeverLongerThanTheFullAmount() {
        for (n in listOf(5L, 999L, 12_345L, 1_200_000L, 45_000_000_000L)) {
            val full = Formatting.money(Money.units(n)); val short = Formatting.compactMoney(Money.units(n))
            assertEquals("$n: $short vs $full", true, short.length <= full.length)
        }
    }

    @Test fun compactMoneyUsesSuffixesForBigAmounts() {
        val s = Formatting.compactMoney(Money.units(1_200_000))
        assertEquals(true, s.endsWith("M"))
        assertEquals(true, Formatting.compactMoney(Money.units(45_000)).endsWith("K"))
    }

    @Test fun titleCasesKeys() {
        assertEquals("Grain farm", Formatting.title("grain_farm"))
        assertEquals("Wheat", Formatting.title("wheat"))
    }
}
