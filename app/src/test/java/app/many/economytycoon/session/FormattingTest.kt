package app.many.economytycoon.session

import app.many.economytycoon.core.GameDate
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
    @Test fun groupingHandlesSignsAndSizes() {
        assertEquals("0", Formatting.grouped(0))
        assertEquals("999", Formatting.grouped(999))
        assertEquals("1,000", Formatting.grouped(1_000))
        assertEquals("-1,234,567", Formatting.grouped(-1_234_567))
        assertEquals("9,223,372,036,854,775,807", Formatting.grouped(Long.MAX_VALUE))
        assertEquals("-9,223,372,036,854,775,808", Formatting.grouped(Long.MIN_VALUE))
    }

    @Test fun moneyShowsTwoDecimalsAndRoundsHalfAwayFromZero() {
        assertEquals("1,234.50", Formatting.money(Money.units(1_234) + Money.cents(50)))
        assertEquals("0.00", Formatting.money(Money.ZERO))
        assertEquals("-0.05", Formatting.money(-Money.cents(5)))
        assertEquals("0.01", Formatting.money(Money(50)))
        assertEquals("-0.01", Formatting.money(Money(-50)))
        assertEquals("0.00", Formatting.money(Money(49)))
        assertEquals("$12.00", Formatting.money(Money.units(12), "$"))
        assertEquals("-$3.00", Formatting.money(-Money.units(3), "$"))
    }

    @Test fun compactMoneyKeepsThreeSignificantDigits() {
        assertEquals("950", Formatting.compactMoney(Money.units(950)))
        assertEquals("1.23K", Formatting.compactMoney(Money.units(1_234)))
        assertEquals("12.3K", Formatting.compactMoney(Money.units(12_345)))
        assertEquals("123K", Formatting.compactMoney(Money.units(123_456)))
        assertEquals("4.56M", Formatting.compactMoney(Money.units(4_567_000)))
        assertEquals("7.80B", Formatting.compactMoney(Money.units(7_800_000_000)))
        assertEquals("-1.50T", Formatting.compactMoney(-Money.units(1_500_000_000_000), ""))
        assertEquals("\$2.00K", Formatting.compactMoney(Money.units(2_000), "$"))
    }

    @Test fun quantitiesTrimTrailingZeros() {
        assertEquals("12", Formatting.quantity(Quantity.units(12)))
        assertEquals("12.5", Formatting.quantity(Quantity(12_500)))
        assertEquals("0.25", Formatting.quantity(Quantity(250)))
        assertEquals("-3.07 t", Formatting.quantity(Quantity(-3_070), "t"))
        assertEquals("1,000 kg", Formatting.quantity(Quantity.units(1_000), "kg"))
    }

    @Test fun percentagesUseOneDecimal() {
        assertEquals("12.3%", Formatting.percent(Rate(123_000)))
        assertEquals("0.0%", Formatting.percentPpm(0))
        assertEquals("-1.5%", Formatting.percentPpm(-15_000))
        assertEquals("+3.2%", Formatting.signedPercentPpm(32_000))
        assertEquals("0.0%", Formatting.signedPercentPpm(10))
        assertEquals("100.0%", Formatting.percentPpm(1_000_000))
    }

    @Test fun datesUseTheGameCalendar() {
        assertEquals("1 Jan 2000", Formatting.date(Tick(0)))
        assertEquals("1 Feb 2000", Formatting.date(Tick(30)))
        assertEquals("30 Dec 2000", Formatting.date(Tick(359)))
        assertEquals("1 Jan 2001", Formatting.date(Tick(360)))
        assertEquals("5 Mar 2003", Formatting.date(GameDate(2003, 3, 5)))
        assertEquals("February 2000", Formatting.monthYear(Tick(45)))
    }

    @Test fun durationsShowTheTwoLargestUnits() {
        assertEquals("0 days", Formatting.duration(0))
        assertEquals("1 day", Formatting.duration(1))
        assertEquals("29 days", Formatting.duration(29))
        assertEquals("1 month", Formatting.duration(30))
        assertEquals("2 months 5 days", Formatting.duration(65))
        assertEquals("1 year 3 months", Formatting.duration(360 + 95))
        assertEquals("2 years", Formatting.duration(720 + 12))
    }

    @Test fun contentKeysBecomeTitles() {
        assertEquals("Spare parts", Formatting.title("spare_parts"))
        assertEquals("Iron ore", Formatting.title("iron-ore"))
        assertEquals("", Formatting.title("  "))
        assertEquals("1.20M", Formatting.compactCount(1_200_000))
    }

    @Test fun ratiosShowTwoDecimals() {
        assertEquals("1.85x", Formatting.ratioTimes(1_850_000))
        assertEquals("0.00x", Formatting.ratioTimes(0))
        assertEquals("-0.50x", Formatting.ratioTimes(-500_000))
        assertEquals("12.01x", Formatting.ratioTimes(12_007_000))
    }

    @Test fun pluralsFollowEnglishSpelling() {
        assertEquals("1 facility", Formatting.plural(1, "facility"))
        assertEquals("3 facilities", Formatting.plural(3, "facility"))
        assertEquals("2 companies", Formatting.plural(2, "company"))
        assertEquals("2 days", Formatting.plural(2, "day"))
        assertEquals("0 boxes", Formatting.plural(0, "box"))
        assertEquals("2 batches", Formatting.plural(2, "batch"))
        assertEquals("5 shares", Formatting.plural(5, "share"))
    }
}
