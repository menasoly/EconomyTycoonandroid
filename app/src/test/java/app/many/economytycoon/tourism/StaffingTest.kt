package app.many.economytycoon.tourism

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffingTest {
    private val w = TourismWorld()

    @Test fun baseStaffRoundsUpPerHundredRooms() {
        assertEquals(12, Staffing.baseStaff(PropertyKind.HOTEL, 100))
        assertEquals(1, Staffing.baseStaff(PropertyKind.HOTEL, 1))
        assertEquals(0, Staffing.baseStaff(PropertyKind.HOTEL, 0))
        assertEquals(60, Staffing.baseStaff(PropertyKind.RESORT, 300))
    }

    @Test fun guestStaffGrowsWithOccupancyAndStars() {
        assertEquals(0, Staffing.guestStaff(PropertyKind.HOTEL, 3, 0))
        assertEquals(5, Staffing.guestStaff(PropertyKind.HOTEL, 3, 10))
        assertTrue(Staffing.guestStaff(PropertyKind.HOTEL, 5, 50) > Staffing.guestStaff(PropertyKind.HOTEL, 3, 50))
        assertTrue(Staffing.guestStaff(PropertyKind.HOTEL, 1, 50) < Staffing.guestStaff(PropertyKind.HOTEL, 3, 50))
        assertEquals(0, Staffing.guestStaff(PropertyKind.HOTEL, 3, -4))
    }

    @Test fun neededIsBasePlusGuestStaff() {
        assertEquals(Staffing.baseStaff(PropertyKind.HOTEL, 100) + Staffing.guestStaff(PropertyKind.HOTEL, 3, 70), Staffing.needed(PropertyKind.HOTEL, 3, 100, 70))
    }

    @Test fun serviceLevelIsCappedAtOneAndPerfectWhenNobodyIsNeeded() {
        assertEquals(1_000_000L, Staffing.serviceLevelPpm(0, 0))
        assertEquals(1_000_000L, Staffing.serviceLevelPpm(10, 15))
        assertEquals(500_000L, Staffing.serviceLevelPpm(10, 5))
        assertEquals(0L, Staffing.serviceLevelPpm(10, 0))
    }

    @Test fun wagesAreHeadsTimesPay() {
        assertEquals(Money.units(700), Staffing.wageCost(10, Money.units(70)))
        assertEquals(Money.ZERO, Staffing.wageCost(0, Money.units(70)))
    }

    @Test fun defaultWageIsSixtyPercentAboveTheMinimum() {
        assertEquals(Money.units(16), Staffing.defaultWage(10))
    }

    @Test fun supplyImplementationsBehave() {
        val dest = DestinationId(1)
        assertEquals(40, FullStaffSupply.available(null, dest, 40, Tick(1)))
        assertEquals(20, ShareStaffSupply(500_000L).available(CompanyId(1), dest, 40, Tick(1)))
        assertEquals(40, ShareStaffSupply(5_000_000L).available(CompanyId(1), dest, 40, Tick(1)))
        assertEquals(0, ShareStaffSupply(-3L).available(CompanyId(1), dest, 40, Tick(1)))
    }

    @Test fun planFollowsExpectedOccupancy() {
        val p = w.hotel(rooms = intArrayOf(100, 0, 0))
        val plan = Staffing.plan(p, listOf(0L, 7L, 14L, 21L), listOf(300_000L, 900_000L, 500_000L, 1_200_000L))
        assertEquals(4, plan.weeks.size)
        assertTrue(plan.weeks[1].staffNeeded > plan.weeks[0].staffNeeded)
        assertEquals(plan.weeks[3].staffNeeded, Staffing.needed(p.kind, p.stars, p.totalRooms, 100))
        assertEquals(plan.peak, plan.weeks.maxOf { it.staffNeeded })
        assertEquals(plan.trough, plan.weeks.minOf { it.staffNeeded })
        assertEquals(plan.peak - plan.trough, plan.seasonal)
        assertEquals(plan.trough, plan.permanent)
        assertTrue(plan.average in plan.trough..plan.peak)
    }

    @Test fun emptyPlanHasZeroFigures() {
        val plan = StaffingPlan(emptyList())
        assertEquals(0, plan.peak)
        assertEquals(0, plan.trough)
        assertEquals(0, plan.average)
        assertEquals(0, plan.seasonal)
    }

    @Test(expected = IllegalArgumentException::class) fun planNeedsOneOccupancyPerWeek() {
        Staffing.plan(w.hotel(), listOf(0L, 7L), listOf(500_000L))
    }

    @Test fun aShortfallOfStaffLowersServiceAndTheRecordShowsIt() {
        val p = w.hotel()
        w.days(30, DayInputs(staff = ShareStaffSupply(500_000L)))
        val last = p.days.takeLast(10)
        assertTrue(last.all { it.staffHad < it.staffNeeded })
        assertTrue(p.serviceLevelPpm < 700_000L)
    }

    @Test fun fullStaffMeansPerfectService() {
        val p = w.hotel()
        w.days(30)
        assertEquals(1_000_000L, p.serviceLevelPpm)
        assertTrue(p.days.all { it.staffHad == it.staffNeeded })
    }
}
