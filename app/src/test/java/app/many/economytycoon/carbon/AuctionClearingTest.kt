package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.cyrus
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuctionClearingTest {
    private val rich: (CompanyId) -> Money = { Money.units(1_000_000_000) }
    private var next = 1L

    private fun bid(company: CompanyId, tonnes: Long, limit: Long) =
        AuctionBid(BidId(next++), company, Tonnes.of(tonnes), Money.units(limit), Tick(0))

    private fun clear(supply: Long, floor: Long, ceiling: Long, vararg bids: AuctionBid, cash: (CompanyId) -> Money = rich) =
        AuctionClearing.clear(Tonnes.of(supply), Money.units(floor), Money.units(ceiling), bids.toList(), cash)

    @Test fun withLessDemandThanSupplyEveryoneIsFilledAtTheFloor() {
        val o = clear(1_000, 5, 0, bid(acme, 300, 20), bid(borea, 200, 9))
        assertEquals(Money.units(5), o.price)
        assertEquals(Tonnes.of(500), o.sold)
        assertEquals(Tonnes.of(500), o.unsold)
        assertEquals(Tonnes.of(300), o.fills.first { it.company == acme }.tonnes)
        assertEquals(Money.units(1_500), o.fills.first { it.company == acme }.cost)
    }

    @Test fun anOversubscribedAuctionClearsAtTheMarginalBid() {
        val o = clear(100, 5, 0, bid(acme, 60, 30), bid(borea, 60, 20), bid(cyrus, 60, 10))
        assertEquals(Money.units(20), o.price)
        assertEquals(Tonnes.of(100), o.sold)
        assertEquals(Tonnes.of(60), o.fills.first { it.company == acme }.tonnes)
        assertEquals(Tonnes.of(40), o.fills.first { it.company == borea }.tonnes)
        assertTrue(o.fills.none { it.company == cyrus })
        assertTrue(o.fills.all { it.cost == it.tonnes.cost(o.price) })
    }

    @Test fun bidsExactlyAtTheMarginShareWhatIsLeftInProportion() {
        val o = clear(100, 5, 0, bid(acme, 100, 20), bid(borea, 50, 20), bid(cyrus, 50, 20))
        assertEquals(Money.units(20), o.price)
        val bySize = o.fills.associate { it.company to it.tonnes }
        assertEquals(Tonnes.of(50), bySize[acme])
        assertEquals(Tonnes.of(25), bySize[borea])
        assertEquals(Tonnes.of(25), bySize[cyrus])
    }

    @Test fun theProRataSplitAddsUpExactlyEvenWhenItDoesNotDivide() {
        val o = clear(8, 1, 0, bid(acme, 3, 9), bid(borea, 3, 9), bid(cyrus, 3, 9))
        assertEquals(Tonnes.of(8), Tonnes.sum(o.fills.map { it.tonnes }))
        assertTrue(o.fills.all { it.tonnes <= Tonnes.of(3) && it.tonnes >= Tonnes.ofKg(2_666) })
        val whole = clear(10, 1, 0, bid(acme, 3, 9), bid(borea, 3, 9), bid(cyrus, 3, 9))
        assertEquals(Tonnes.of(9), whole.sold)
        assertEquals(Tonnes.of(1), whole.unsold)
    }

    @Test fun bidsBelowTheFloorAreVoid() {
        val o = clear(100, 10, 0, bid(acme, 50, 9), bid(borea, 50, 11))
        assertTrue(o.fills.none { it.company == acme })
        assertEquals(Money.units(10), o.price)
    }

    @Test fun noBidsLeavesEverythingUnsoldAtTheFloor() {
        val o = clear(100, 7, 0)
        assertEquals(Tonnes.of(100), o.unsold)
        assertEquals(Tonnes.ZERO, o.sold)
        assertEquals(Money.units(7), o.price)
        assertTrue(o.fills.isEmpty())
    }

    @Test fun aBidderIsTrimmedToWhatItCanPay() {
        val o = clear(1_000, 5, 0, bid(acme, 500, 20), cash = { Money.units(2_000) })
        assertEquals(Tonnes.of(100), o.fills.single().tonnes)
        assertTrue(o.fills.single().cost <= Money.units(2_000))
    }

    @Test fun trimmingStartsWithTheLowestLimitSoTheBestBidSurvives() {
        val o = clear(1_000, 5, 0, bid(acme, 100, 40), bid(acme, 100, 10), cash = { Money.units(4_500) })
        val fills = o.fills.groupBy { it.bid }
        assertEquals(Tonnes.of(100), fills.getValue(BidId(next - 2)).single().tonnes)
        assertTrue(o.fills.sumOf { it.cost.raw } <= Money.units(4_500).raw)
    }

    @Test fun theCeilingCapsABidAndForcesTheCeilingReserveWhenBidsAtTheCeilingExceedSupply() {
        val o = clear(100, 5, 50, bid(acme, 80, 70), bid(borea, 80, 90), bid(cyrus, 40, 20))
        assertEquals(Money.units(50), o.price)
        assertEquals(Tonnes.of(100), o.sold)
        assertEquals(Tonnes.of(60), o.ceilingSold)
        assertEquals(Tonnes.of(160), Tonnes.sum(o.fills.map { it.tonnes }))
        assertTrue(o.fills.none { it.company == cyrus })
    }

    @Test fun belowTheCeilingTheCeilingReserveIsNotTouched() {
        val o = clear(100, 5, 50, bid(acme, 80, 70), bid(borea, 80, 20))
        assertEquals(Tonnes.ZERO, o.ceilingSold)
        assertTrue(o.price <= Money.units(50))
    }

    @Test fun everyWinnerPaysNoMoreThanItsLimit() {
        val bids = listOf(bid(acme, 40, 33), bid(borea, 40, 27), bid(cyrus, 40, 21), bid(acme, 40, 12))
        val o = AuctionClearing.clear(Tonnes.of(90), Money.units(5), Money.ZERO, bids, rich)
        for (f in o.fills) {
            val limit = bids.first { it.id == f.bid }.limit
            assertTrue(o.price <= limit)
        }
    }

    @Test fun clearingTwiceGivesTheSameResult() {
        val bids = listOf(bid(acme, 40, 33), bid(borea, 40, 33), bid(cyrus, 40, 33))
        val a = AuctionClearing.clear(Tonnes.of(100), Money.units(5), Money.ZERO, bids, rich)
        val b = AuctionClearing.clear(Tonnes.of(100), Money.units(5), Money.ZERO, bids, rich)
        assertEquals(a.fills, b.fills)
        assertEquals(a.price, b.price)
    }
}
