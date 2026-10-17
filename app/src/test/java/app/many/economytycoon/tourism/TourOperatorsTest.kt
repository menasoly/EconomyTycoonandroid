package app.many.economytycoon.tourism

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourOperatorsTest {
    private val w = TourismWorld()
    private val hotelOwner = w.owner
    private val operatorOwner = w.rival

    private fun propose(
        side: ContractSide = ContractSide.PROPERTY, who: app.many.economytycoon.core.CompanyId = hotelOwner, op: TourOperator, p: Property, rooms: Int = 10,
        netShare: Long = 650_000L, startOffset: Long = 0L, days: Int = 60, release: Int = 3, committed: Long = 0L,
    ) = w.service.proposeAllotment(
        side, who, op.id, p.id, RoomClass.STANDARD, rooms, Money(p.rackRate(RoomClass.STANDARD).raw * netShare / 1_000_000L), Tick(w.tick + startOffset), days, release, committed, Tick(w.tick),
    )

    @Test fun anIndependentOperatorAcceptsAFairContractAtOnce() {
        val p = w.hotel()
        val a = propose(op = w.operator(), p = p).getOrThrow()
        assertEquals(AllotmentStatus.ACTIVE, a.status)
    }

    @Test fun anIndependentOperatorDeclinesAnOutOfRangeRate() {
        val p = w.hotel()
        val op = w.operator()
        assertEquals(AllotmentStatus.DECLINED, propose(op = op, p = p, netShare = 400_000L).getOrThrow().status)
        assertEquals(AllotmentStatus.DECLINED, propose(op = op, p = p, netShare = 900_000L).getOrThrow().status)
    }

    @Test fun anOwnedOperatorHasToAcceptExplicitly() {
        val p = w.hotel()
        val op = w.operator(who = operatorOwner)
        val a = propose(op = op, p = p).getOrThrow()
        assertEquals(AllotmentStatus.PROPOSED, a.status)
        assertEquals(Rules.NOT_OWNER, w.service.acceptAllotment(a.id, hotelOwner, Tick(w.tick)).rejectionOrNull()?.rule)
        assertEquals(AllotmentStatus.ACTIVE, w.service.acceptAllotment(a.id, operatorOwner, Tick(w.tick)).getOrThrow().status)
        assertEquals(Rules.WRONG_STATE, w.service.acceptAllotment(a.id, operatorOwner, Tick(w.tick)).rejectionOrNull()?.rule)
    }

    @Test fun anOperatorOfferToAPlayerHotelIsAcceptedByTheHotel() {
        val p = w.hotel()
        val op = w.operator(who = operatorOwner)
        val a = propose(ContractSide.OPERATOR, operatorOwner, op, p).getOrThrow()
        assertEquals(AllotmentStatus.PROPOSED, a.status)
        assertEquals(Rules.NOT_OWNER, w.service.acceptAllotment(a.id, operatorOwner, Tick(w.tick)).rejectionOrNull()?.rule)
        assertTrue(w.service.acceptAllotment(a.id, hotelOwner, Tick(w.tick)).isOk)
    }

    @Test fun theProposerCancelsAndTheOtherSideDeclines() {
        val p = w.hotel()
        val op = w.operator(who = operatorOwner)
        val a = propose(op = op, p = p).getOrThrow()
        assertEquals(AllotmentStatus.CANCELLED, w.service.declineAllotment(a.id, hotelOwner).getOrThrow().status)
        val b = propose(op = op, p = p).getOrThrow()
        assertEquals(AllotmentStatus.DECLINED, w.service.declineAllotment(b.id, operatorOwner).getOrThrow().status)
        assertEquals(Rules.NOT_OWNER, w.service.declineAllotment(propose(op = op, p = p).getOrThrow().id, CompanyIdOther).rejectionOrNull()?.rule)
    }

    @Test fun onlyTheProposersOwnSideMayProposeIt() {
        val p = w.hotel()
        val op = w.operator(who = operatorOwner)
        assertEquals(Rules.NOT_OWNER, propose(ContractSide.PROPERTY, operatorOwner, op, p).rejectionOrNull()?.rule)
        assertEquals(Rules.NOT_OWNER, propose(ContractSide.OPERATOR, hotelOwner, op, p).rejectionOrNull()?.rule)
    }

    @Test fun badTermsAreRejected() {
        val p = w.hotel()
        val op = w.operator()
        assertEquals(Rules.INVALID_QUANTITY, propose(op = op, p = p, rooms = 0).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, propose(op = op, p = p, days = 0).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, propose(op = op, p = p, days = 400).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, propose(op = op, p = p, startOffset = -1).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, propose(op = op, p = p, release = 14).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, propose(op = op, p = p, committed = 1_000_001L).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_PRICE, propose(op = op, p = p, netShare = 200_000L).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_PRICE, propose(op = op, p = p, netShare = 1_200_000L).rejectionOrNull()?.rule)
    }

    @Test fun tooManyRoomsCannotBeSetAside() {
        val p = w.hotel(rooms = intArrayOf(100, 0, 0))
        val op = w.operator()
        assertTrue(propose(op = op, p = p, rooms = 60).isOk)
        assertEquals(Rules.LIMIT_EXCEEDED, propose(op = op, p = p, rooms = 20).rejectionOrNull()?.rule)
        assertTrue("different dates do not count", propose(op = op, p = p, rooms = 20, startOffset = 100).isOk)
    }

    @Test fun aClosedHotelTakesNoContracts() {
        val p = w.hotel()
        w.service.closeProperty(p.id).getOrThrow()
        assertEquals(Rules.WRONG_STATE, propose(op = w.operator(), p = p).rejectionOrNull()?.rule)
    }

    @Test fun heldRoomsReleaseBeforeTheStay() {
        val p = w.hotel()
        val a = propose(op = w.operator(), p = p, release = 3, startOffset = 0).getOrThrow()
        val date = a.startsAt.value + 10
        assertEquals(10, a.heldOn(date, lead = 8))
        assertEquals(10, a.heldOn(date, lead = 4))
        assertEquals(0, a.heldOn(date, lead = 3))
        assertEquals(0, a.heldOn(date, lead = 0))
        assertEquals(10, a.openOn(date))
    }

    @Test fun soldRoomsReduceWhatIsHeldAndOpen() {
        val p = w.hotel()
        val a = propose(op = w.operator(), p = p).getOrThrow()
        val date = a.startsAt.value + 5
        a.sell(date, 4)
        assertEquals(6, a.heldOn(date, 8))
        assertEquals(6, a.openOn(date))
        a.sell(date, 20)
        assertEquals(0, a.openOn(date))
        assertEquals(24, a.soldOn(date))
    }

    @Test fun outsideTheContractNothingIsHeld() {
        val p = w.hotel()
        val a = propose(op = w.operator(), p = p, startOffset = 10, days = 5).getOrThrow()
        assertFalse(a.covers(a.startsAt.value - 1))
        assertTrue(a.covers(a.startsAt.value))
        assertTrue(a.covers(a.endsAt.value - 1))
        assertFalse(a.covers(a.endsAt.value))
        assertEquals(0, a.heldOn(a.endsAt.value, 10))
        assertEquals(0, a.openOn(a.startsAt.value - 1))
        assertEquals(5L, a.nights)
    }

    @Test fun aNonActiveContractHoldsNothing() {
        val p = w.hotel()
        val a = propose(op = w.operator(who = operatorOwner), p = p).getOrThrow()
        assertEquals(AllotmentStatus.PROPOSED, a.status)
        assertEquals(0, a.heldOn(a.startsAt.value + 3, 10))
    }

    @Test fun forgettingOldSalesKeepsTheRest() {
        val p = w.hotel()
        val a = propose(op = w.operator(), p = p).getOrThrow()
        for (d in 0L..9L) a.sell(a.startsAt.value + d, 1)
        a.forgetBefore(a.startsAt.value + 5)
        assertEquals(5, a.soldDates.size)
        assertEquals(0, a.soldOn(a.startsAt.value))
        assertEquals(1, a.soldOn(a.startsAt.value + 7))
    }

    @Test fun committedRoomsRoundUp() {
        val p = w.hotel()
        val a = propose(op = w.operator(), p = p, rooms = 10, committed = 250_000L).getOrThrow()
        assertEquals(3, a.committedRooms)
        assertEquals(0, propose(op = w.operator(), p = p, rooms = 5, committed = 0L, startOffset = 200).getOrThrow().committedRooms)
    }

    @Test fun shortfallIsTheCommittedRoomsNotSold() {
        val p = w.hotel()
        val a = propose(op = w.operator(), p = p, rooms = 10, committed = 500_000L).getOrThrow()
        assertEquals(5, AllotmentRules.shortfallRooms(a, 0))
        assertEquals(2, AllotmentRules.shortfallRooms(a, 3))
        assertEquals(0, AllotmentRules.shortfallRooms(a, 9))
    }

    @Test fun terminationFeeIsAQuarterOfTheRemainingCommittedValue() {
        val p = w.hotel()
        val a = propose(op = w.operator(), p = p, rooms = 10, committed = 500_000L, days = 40).getOrThrow()
        val full = AllotmentRules.terminationFee(a, a.startsAt)
        assertEquals(Money(a.netRate.raw * 200L / 4L), full)
        val half = AllotmentRules.terminationFee(a, Tick(a.startsAt.value + 20))
        assertEquals(Money(full.raw / 2), half)
        assertEquals(Money.ZERO, AllotmentRules.terminationFee(a, a.endsAt))
        assertEquals(full, AllotmentRules.terminationFee(a, Tick(a.startsAt.value - 30)))
    }

    @Test fun endingEarlyChargesTheEnderAndCancelsTheContract() {
        val p = w.hotel()
        val op = w.operator(who = operatorOwner)
        val a = propose(op = op, p = p, rooms = 10, committed = 500_000L, days = 40).getOrThrow()
        w.service.acceptAllotment(a.id, operatorOwner, Tick(w.tick)).getOrThrow()
        val t = w.service.terminateAllotment(a.id, operatorOwner, Tick(w.tick)).getOrThrow()
        assertEquals(AllotmentStatus.CANCELLED, t.allotment.status)
        assertEquals(ContractSide.OPERATOR, t.allotment.terminatedBy)
        assertTrue(t.fee.raw > 0L)
        assertTrue(t.payment != null)
        assertEquals("tour:term:${a.id.value}", t.payment?.key)
        assertEquals(Rules.WRONG_STATE, w.service.terminateAllotment(a.id, operatorOwner, Tick(w.tick)).rejectionOrNull()?.rule)
    }

    @Test fun onlyAPartyMayEndAContract() {
        val p = w.hotel()
        val a = propose(op = w.operator(), p = p).getOrThrow()
        assertEquals(Rules.NOT_OWNER, w.service.terminateAllotment(a.id, CompanyIdOther, Tick(w.tick)).rejectionOrNull()?.rule)
    }

    @Test fun anUncommittedContractCostsNothingToEnd() {
        val p = w.hotel()
        val a = propose(op = w.operator(), p = p, committed = 0L).getOrThrow()
        val t = w.service.terminateAllotment(a.id, hotelOwner, Tick(w.tick)).getOrThrow()
        assertEquals(Money.ZERO, t.fee)
        assertNull(t.payment)
    }

    @Test fun autoAcceptWindowIsInclusive() {
        assertTrue(AllotmentRules.autoAccepts(AllotmentRules.AUTO_ACCEPT_MIN_PPM))
        assertTrue(AllotmentRules.autoAccepts(AllotmentRules.AUTO_ACCEPT_MAX_PPM))
        assertFalse(AllotmentRules.autoAccepts(AllotmentRules.AUTO_ACCEPT_MIN_PPM - 1))
        assertFalse(AllotmentRules.autoAccepts(AllotmentRules.AUTO_ACCEPT_MAX_PPM + 1))
    }

    @Test fun packagePriceAddsTheOperatorMargin() {
        assertEquals(Money.units(118), AllotmentRules.packagePrice(Money.units(100), 180_000L))
        assertEquals(Money.units(100), AllotmentRules.packagePrice(Money.units(100), 0L))
    }

    @Test fun roomsSplitByStrengthExactly() {
        for (rooms in listOf(0L, 1L, 7L, 100L, 1_001L)) {
            val parts = AllotmentRules.splitByStrength(rooms, listOf(1_000_000L, 500_000L, 250_000L))
            assertEquals(rooms, parts.sum())
        }
        val parts = AllotmentRules.splitByStrength(70L, listOf(2_000_000L, 1_000_000L))
        assertTrue(parts[0] > parts[1])
        assertEquals(listOf(0L, 0L), AllotmentRules.splitByStrength(10L, listOf(0L, 0L)))
        assertEquals(emptyList<Long>(), AllotmentRules.splitByStrength(10L, emptyList()))
    }

    @Test fun netShareOfRackMeasuresAgainstTheRoomRate() {
        val p = w.hotel()
        val a = propose(op = w.operator(), p = p, netShare = 650_000L).getOrThrow()
        assertEquals(650_000L, AllotmentRules.netShareOfRack(a, p.rackRate(RoomClass.STANDARD)))
        assertEquals(0L, AllotmentRules.netShareOfRack(a, Money.ZERO))
    }

    @Test fun contractHashFollowsStatusAndSales() {
        val p = w.hotel()
        val a = propose(op = w.operator(), p = p).getOrThrow()
        fun h() = CanonicalHasher().also { a.hashInto(it) }.finish()
        val before = h()
        a.sell(a.startsAt.value, 1)
        assertNotEquals(before, h())
        val mid = h()
        a.status = AllotmentStatus.ENDED
        assertNotEquals(mid, h())
    }

    @Test fun operatorsKeepTheirTerms() {
        val op = w.service.addOperator(operatorOwner, "Sunways", 1_500_000L, 220_000L).getOrThrow()
        assertEquals(1_500_000L, op.strengthPpm)
        w.service.setOperatorMargin(op.id, 300_000L).getOrThrow()
        assertEquals(300_000L, op.marginPpm)
        assertEquals(Rules.INVALID_ARGUMENT, w.service.setOperatorMargin(op.id, 1_000_001L).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, w.service.addOperator(null, " ").rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, w.service.addOperator(null, "X", strengthPpm = 0L).rejectionOrNull()?.rule)
    }

    private companion object {
        val CompanyIdOther = app.many.economytycoon.core.CompanyId(999)
    }
}
