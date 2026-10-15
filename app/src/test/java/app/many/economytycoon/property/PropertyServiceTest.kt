package app.many.economytycoon.property

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyServiceTest {
    private val s = PropertyService()
    private val me = CompanyId(1)
    private val plot = ParcelId(10)
    private val value = Money.units(100_000)

    @Test fun askingPricesAreBoundedByTheAppraisal() {
        assertNull(s.checkListing(me, value, value))
        assertEquals(Rules.INVALID_PRICE, s.checkListing(me, Money.ZERO, value)!!.rule)
        assertEquals(Rules.INVALID_PRICE, s.checkListing(me, Money(s.minAsk(value).raw - 1), value)!!.rule)
        assertEquals(Rules.INVALID_PRICE, s.checkListing(me, Money(s.maxAsk(value).raw + 1), value)!!.rule)
        assertNull(s.checkListing(me, s.minAsk(value), value)); assertNull(s.checkListing(me, s.maxAsk(value), value))
    }

    @Test fun listingsAreCappedPerCompany() {
        repeat(s.params.maxListingsPerCompany) { s.addListing(ParcelId(100L + it), me, value, Tick(0)) }
        assertEquals(Rules.LIMIT_EXCEEDED, s.checkListing(me, value, value)!!.rule)
        assertNull(s.checkListing(CompanyId(2), value, value))
    }

    @Test fun aListingKeepsThePlotEncumberedUntilRemoved() {
        assertFalse(s.encumbered(plot))
        s.addListing(plot, me, value, Tick(5))
        assertTrue(s.encumbered(plot)); assertEquals(Tick(5 + s.params.listingDays.toLong()), s.listing(plot)!!.expiresAt)
        s.removeListing(plot); assertFalse(s.encumbered(plot))
    }

    @Test fun fairRentIsAYearlyYieldSpreadOverTwelveMonths() {
        assertEquals(Money(value.raw * 60_000L / (12L * 1_000_000L)), s.fairRent(value))
        assertTrue(s.minRent(value) < s.fairRent(value) && s.fairRent(value) < s.maxRent(value))
    }

    @Test fun rentOffersNeedAKnownTermAndASaneRent() {
        val fair = s.fairRent(value)
        assertNull(s.checkOffer(me, 90, fair, value))
        assertEquals(Rules.INVALID_ARGUMENT, s.checkOffer(me, 100, fair, value)!!.rule)
        assertEquals(Rules.INVALID_PRICE, s.checkOffer(me, 90, Money(s.minRent(value).raw - 1), value)!!.rule)
        assertEquals(Rules.INVALID_PRICE, s.checkOffer(me, 90, Money(s.maxRent(value).raw + 1), value)!!.rule)
    }

    @Test fun tenantsComeFasterToCheapOffersAndStopComingWhenRentIsHigh() {
        val fair = s.fairRent(value)
        val cheap = s.tenantChancePpm(Money(fair.raw / 2), value); val atFair = s.tenantChancePpm(fair, value)
        val dear = s.tenantChancePpm(Money(fair.raw * 2), value); val absurd = s.tenantChancePpm(Money(fair.raw * 5), value)
        assertEquals(s.params.tenantChancePpm, atFair)
        assertTrue(cheap > atFair && atFair > dear && dear > absurd)
        assertEquals(0L, s.tenantChancePpm(Money.ZERO, value))
    }

    @Test fun startingALeaseConsumesTheOfferAndSchedulesRent() {
        val o = s.addOffer(plot, me, 180, Money.units(500), Tick(10))
        val l = s.startLease(o, 3, Tick(20))
        assertNull(s.offer(plot)); assertEquals(1L, l.id)
        assertEquals(Tick(200), l.endsAt); assertEquals(Tick(50), l.nextRent)
        assertEquals(l, s.leaseOf(plot)); assertTrue(s.encumbered(plot))
    }

    @Test fun rentIsCollectedOnScheduleAndMissesBuildUp() {
        val l = s.startLease(s.addOffer(plot, me, 180, Money.units(500), Tick(0)), 0, Tick(0))
        assertFalse(s.rentDue(l, Tick(29))); assertTrue(s.rentDue(l, Tick(30)))
        s.rentPaid(l, Money.units(500))
        assertEquals(Money.units(500), l.collected); assertEquals(Tick(60), l.nextRent)
        s.rentMissed(l); assertEquals(1, l.missed); assertFalse(s.shouldEvict(l))
        s.rentMissed(l); assertTrue(s.shouldEvict(l))
        s.rentPaid(l, Money.units(500)); assertEquals(0, l.missed)
    }

    @Test fun endingALeaseFreesThePlotAndKeepsTheRecord() {
        val l = s.startLease(s.addOffer(plot, me, 90, Money.units(500), Tick(0)), 0, Tick(0))
        s.endLease(l, Tick(40))
        assertFalse(s.encumbered(plot)); assertNull(s.leaseOf(plot)); assertEquals(listOf(l), s.historyOf(me)); assertTrue(s.leasesOf(me).isEmpty())
    }

    @Test fun lettingsCountAgainstTheLimit() {
        repeat(s.params.maxLeasesPerCompany) { s.addOffer(ParcelId(200L + it), me, 90, Money.units(500), Tick(0)) }
        assertEquals(Rules.LIMIT_EXCEEDED, s.checkOffer(me, 90, s.fairRent(value), value)!!.rule)
    }

    @Test fun earlyEndingCostsAFewMonthsOfRent() {
        val l = s.startLease(s.addOffer(plot, me, 90, Money.units(500), Tick(0)), 0, Tick(0))
        assertEquals(Money.units(500 * s.params.earlyEndMonths.toLong()), s.earlyEndCost(l))
    }

    @Test fun holdingPeriodsAreTrackedFromAcquisition() {
        assertEquals(0L, s.heldDays(plot, Tick(100))); assertNull(s.heldSince(plot))
        s.noteAcquired(plot, Tick(40))
        assertEquals(60L, s.heldDays(plot, Tick(100)))
        assertEquals(0L, s.heldDays(plot, Tick(10)))
    }

    @Test fun transferTaxIsAShareOfThePrice() {
        assertEquals(Money(value.raw * 20_000L / 1_000_000L), s.transferTax(value))
    }

    @Test fun clearingAParcelDropsItsListingAndOffer() {
        s.addListing(plot, me, value, Tick(0)); s.addOffer(plot, me, 90, Money.units(500), Tick(0))
        s.clearParcel(plot)
        assertNull(s.listing(plot)); assertNull(s.offer(plot))
    }

    @Test fun oldLeaseRecordsAreTrimmedToTheHistoryLength() {
        repeat(s.params.historyLength + 5) { i ->
            val l = s.startLease(s.addOffer(ParcelId(1000L + i), me, 90, Money.units(500), Tick(0)), 0, Tick(0))
            s.endLease(l, Tick(i.toLong()))
        }
        s.prune()
        assertEquals(s.params.historyLength, s.leases().size)
        assertNotNull(s.lease(s.leaseSequence))
        assertNull(s.lease(1L))
    }

    @Test fun theHashCoversEveryRecord() {
        fun h(p: PropertyService) = app.many.economytycoon.core.CanonicalHasher().also { p.hashInto(it) }.finish()
        val a = PropertyService(); val b = PropertyService()
        assertEquals(h(a), h(b))
        a.addListing(plot, me, value, Tick(0)); assertTrue(h(a) != h(b))
        b.addListing(plot, me, value, Tick(0)); assertEquals(h(a), h(b))
        a.noteAcquired(plot, Tick(3)); assertTrue(h(a) != h(b))
    }
}
