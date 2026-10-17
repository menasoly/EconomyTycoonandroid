package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TariffsTest {
    private val schedule = TariffSchedule()
    private val wholesale = Money.units(50)
    private val levy = Money.units(3)

    @Test fun anIndustrialCustomerPaysEnergyMarginNetworkAndLevy() {
        val q = Tariffs.quote(schedule, CustomerClass.INDUSTRIAL, wholesale, levy)
        assertEquals(Money.units(50), q.energy)
        assertEquals(Money.units(6), q.margin)
        assertEquals(Money.units(14), q.network)
        assertEquals(Money.units(73), q.perMwh)
        assertFalse(q.capped)
    }

    @Test fun passThroughScalesOnlyTheEnergyPart() {
        val q = Tariffs.quote(schedule.copy(passThroughPpm = 500_000L), CustomerClass.COMMERCIAL, wholesale, levy)
        assertEquals(Money.units(25), q.energy)
        assertEquals(Money.units(48), q.perMwh)
    }

    @Test fun interruptibleCustomersGetADiscountOnEnergyAndMargin() {
        val q = Tariffs.quote(schedule, CustomerClass.INTERRUPTIBLE, wholesale, levy)
        assertEquals(Money(425_000L), q.energy)
        assertEquals(Money(51_000L), q.margin)
        assertEquals(Money(646_000L), q.perMwh)
    }

    @Test fun aHouseholdCapLimitsTheBillAndRecordsTheShortfall() {
        val capped = schedule.copy(householdCapPerMwh = Money.units(60))
        val q = Tariffs.quote(capped, CustomerClass.HOUSEHOLD, wholesale, levy)
        assertEquals(Money.units(60), q.perMwh)
        assertEquals(Money.units(73), q.uncappedPerMwh)
        assertTrue(q.capped)
        assertEquals(Money.units(13), q.shortfallPerMwh)
        assertEquals(Money.units(26), Tariffs.capShortfall(q, 2_000L))
    }

    @Test fun theCapDoesNotApplyToBusinessOrWhenPricesAreLow() {
        val capped = schedule.copy(householdCapPerMwh = Money.units(60))
        assertEquals(Money.units(73), Tariffs.quote(capped, CustomerClass.INDUSTRIAL, wholesale, levy).perMwh)
        val cheap = Tariffs.quote(capped, CustomerClass.HOUSEHOLD, Money.units(10), Money.ZERO)
        assertEquals(Money.units(30), cheap.perMwh)
        assertFalse(cheap.capped)
    }

    @Test fun aNegativeWholesalePriceNeverPaysTheCustomer() {
        val q = Tariffs.quote(schedule, CustomerClass.HOUSEHOLD, Money.units(-20), Money.ZERO)
        assertEquals(Money.ZERO, q.energy)
        assertEquals(Money.units(20), q.perMwh)
    }

    @Test fun billsAreEnergyTimesTheQuotedRate() {
        val q = Tariffs.quote(schedule, CustomerClass.INDUSTRIAL, wholesale, levy)
        assertEquals(Money(1_095_000L), Tariffs.energyBill(q, 1_500L))
        assertEquals(Money.ZERO, Tariffs.energyBill(q, 0L))
    }

    @Test fun fixedChargesFollowTheConnectionSize() {
        assertEquals(Money.units(50_000), Tariffs.fixedCharge(schedule, 100_000L))
        assertEquals(Money.ZERO, Tariffs.fixedCharge(schedule, 0L))
    }

    @Test(expected = IllegalArgumentException::class) fun aCapMustBePositive() {
        TariffSchedule(householdCapPerMwh = Money.ZERO)
    }

    @Test(expected = IllegalArgumentException::class) fun passThroughIsAtMostEverything() {
        TariffSchedule(passThroughPpm = 1_200_000L)
    }

    @Test fun scheduleHashChangesWithEveryTerm() {
        fun hash(s: TariffSchedule) = app.many.economytycoon.core.CanonicalHasher().also { s.hashInto(it) }.finish()
        assertEquals(hash(schedule), hash(TariffSchedule()))
        assertTrue(hash(schedule) != hash(schedule.copy(householdCapPerMwh = Money.units(60))))
        assertTrue(hash(schedule) != hash(schedule.copy(retailMarginPerMwh = Money.units(7))))
    }
}
