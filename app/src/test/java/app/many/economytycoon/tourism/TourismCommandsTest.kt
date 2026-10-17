package app.many.economytycoon.tourism

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourismCommandsTest {
    private val w = TourismWorld()
    private val exec = TourismCommandExecutor(w.service)
    private val me = w.owner
    private val other = w.rival
    private val now get() = Tick(w.tick)

    private fun run(c: TourismCommand) = exec.execute(c, now)

    private fun build(who: CompanyId = me, rooms: List<Int> = listOf(40, 10, 0), stars: Int = 3, name: String = "Harbour Inn") =
        run(TourismCommand.BuildProperty(who, w.dest.id, PropertyKind.HOTEL, stars, rooms, name))

    // ----- building -----

    @Test fun buildingReturnsAMessageAndThePayment() {
        val r = build()
        assertTrue(r.ok)
        assertTrue(r.message.contains("Harbour Inn"))
        assertEquals(1, r.intents.size)
        assertEquals(AccommodationRules.buildCost(PropertyKind.HOTEL, 3, 50), r.intents[0].total)
        assertEquals(1, w.state.propertiesOf(me).size)
    }

    @Test fun roomCountsMustCoverEveryClass() {
        val r = build(rooms = listOf(40))
        assertFalse(r.ok)
        assertEquals(Rules.INVALID_QUANTITY, r.rejection?.rule)
        assertTrue(w.state.properties.isEmpty())
    }

    @Test fun serviceRejectionsComeThroughWithTheirReason() {
        val r = build(stars = 9)
        assertEquals(Rules.INVALID_ARGUMENT, r.rejection?.rule)
        assertEquals(r.rejection?.message, r.message)
        assertTrue(r.intents.isEmpty())
    }

    @Test fun costIsReportedBeforeAnythingChanges() {
        val c = TourismCommand.BuildProperty(me, w.dest.id, PropertyKind.HOTEL, 3, listOf(40, 10, 0), "X")
        assertEquals(AccommodationRules.buildCost(PropertyKind.HOTEL, 3, 50), exec.cost(c))
        assertTrue(w.state.properties.isEmpty())
    }

    // ----- ownership -----

    @Test fun nobodyTouchesSomeoneElsesProperty() {
        val p = w.hotel(who = other)
        val attempts = listOf(
            TourismCommand.SetRates(me, p.id, RatePolicy.DYNAMIC, Money.units(300)),
            TourismCommand.SetPricingBand(me, p.id, 700_000L, 1_200_000L, 500_000L, 800_000L),
            TourismCommand.SetOverbooking(me, p.id, 50_000L),
            TourismCommand.SetCancellation(me, p.id, CancellationPolicy.NON_REFUNDABLE),
            TourismCommand.Listing(me, p.id, w.platform().id, true),
            TourismCommand.ExpandProperty(me, p.id, RoomClass.STANDARD, 5),
            TourismCommand.UpgradeProperty(me, p.id, 4),
            TourismCommand.RenovateProperty(me, p.id),
            TourismCommand.CloseProperty(me, p.id),
            TourismCommand.ReopenProperty(me, p.id),
        )
        for (c in attempts) {
            val r = run(c)
            assertEquals(c.toString(), Rules.NOT_OWNER, r.rejection?.rule)
            assertTrue(r.intents.isEmpty())
        }
        assertEquals(p.settings.rackRate, w.state.property(p.id)?.settings?.rackRate)
        assertEquals(PropertyStatus.OPEN, p.status)
        assertEquals(0L, p.overbookPpm)
    }

    @Test fun nobodyTouchesSomeoneElsesAttractionOperatorOrPlatform() {
        val a = w.attraction(who = other)
        val op = w.operator(who = other)
        val i = w.platform(who = other)
        assertEquals(Rules.NOT_OWNER, run(TourismCommand.SetTicketPrice(me, a.id, Money.units(1))).rejection?.rule)
        assertEquals(Rules.NOT_OWNER, run(TourismCommand.SetAttractionCapacity(me, a.id, 10)).rejection?.rule)
        assertEquals(Rules.NOT_OWNER, run(TourismCommand.SetAttractionOpen(me, a.id, false)).rejection?.rule)
        assertEquals(Rules.NOT_OWNER, run(TourismCommand.SetOperatorMargin(me, op.id, 100_000L)).rejection?.rule)
        assertEquals(Rules.NOT_OWNER, run(TourismCommand.SetCommission(me, i.id, 10_000L)).rejection?.rule)
        assertTrue(a.open)
    }

    @Test fun unknownThingsAreRefused() {
        assertEquals(Rules.UNKNOWN_ENTITY, run(TourismCommand.CloseProperty(me, PropertyId(404))).rejection?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, run(TourismCommand.SetTicketPrice(me, AttractionId(404), Money.ZERO)).rejection?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, run(TourismCommand.SetOperatorMargin(me, OperatorId(404), 0L)).rejection?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, run(TourismCommand.SetCommission(me, IntermediaryId(404), 0L)).rejection?.rule)
    }

    // ----- running a property -----

    @Test fun settingsReachTheProperty() {
        val p = w.hotel()
        assertTrue(run(TourismCommand.SetRates(me, p.id, RatePolicy.DYNAMIC, Money.units(180))).ok)
        assertTrue(run(TourismCommand.SetPricingBand(me, p.id, 700_000L, 1_400_000L, 600_000L, 850_000L)).ok)
        assertTrue(run(TourismCommand.SetOverbooking(me, p.id, 70_000L)).ok)
        assertTrue(run(TourismCommand.SetCancellation(me, p.id, CancellationPolicy.MODERATE)).ok)
        assertEquals(RatePolicy.DYNAMIC, p.settings.policy)
        assertEquals(Money.units(180), p.settings.rackRate)
        assertEquals(1_400_000L, p.settings.ceilingPpm)
        assertEquals(70_000L, p.overbookPpm)
        assertEquals(CancellationPolicy.MODERATE, p.cancellation)
    }

    @Test fun listingAndDelisting() {
        val p = w.hotel(); val i = w.platform()
        assertTrue(run(TourismCommand.Listing(me, p.id, i.id, true)).ok)
        assertTrue(i.id in p.listedOn)
        assertEquals(Rules.DUPLICATE, run(TourismCommand.Listing(me, p.id, i.id, true)).rejection?.rule)
        assertTrue(run(TourismCommand.Listing(me, p.id, i.id, false)).ok)
        assertFalse(i.id in p.listedOn)
    }

    @Test fun capitalWorksReturnTheirPayments() {
        val p = w.hotel()
        p.conditionPpm = 400_000L
        val expand = run(TourismCommand.ExpandProperty(me, p.id, RoomClass.SUPERIOR, 10))
        val upgrade = run(TourismCommand.UpgradeProperty(me, p.id, 4))
        p.conditionPpm = 400_000L
        val renovate = run(TourismCommand.RenovateProperty(me, p.id))
        for (r in listOf(expand, upgrade, renovate)) { assertTrue(r.message, r.ok); assertEquals(1, r.intents.size) }
        assertEquals(4, p.stars)
        assertEquals(110, p.totalRooms)
        assertEquals(1_000_000L, p.conditionPpm)
    }

    @Test fun closeAndReopen() {
        val p = w.hotel()
        assertTrue(run(TourismCommand.CloseProperty(me, p.id)).ok)
        assertEquals(PropertyStatus.CLOSED, p.status)
        assertEquals(Rules.WRONG_STATE, run(TourismCommand.CloseProperty(me, p.id)).rejection?.rule)
        assertTrue(run(TourismCommand.ReopenProperty(me, p.id)).ok)
        assertEquals(PropertyStatus.OPEN, p.status)
    }

    @Test fun costsOfCapitalWorksMatchWhatTheyCharge() {
        val p = w.hotel()
        p.conditionPpm = 300_000L
        for (c in listOf<TourismCommand>(
            TourismCommand.ExpandProperty(me, p.id, RoomClass.STANDARD, 10), TourismCommand.UpgradeProperty(me, p.id, 4), TourismCommand.RenovateProperty(me, p.id),
        )) {
            val quoted = exec.cost(c)
            val r = run(c)
            assertTrue(r.ok)
            assertEquals(c.toString(), quoted, r.intents.single().total)
            p.conditionPpm = 300_000L
        }
    }

    @Test fun commandsThatCostNothingQuoteZero() {
        val p = w.hotel()
        assertEquals(Money.ZERO, exec.cost(TourismCommand.CloseProperty(me, p.id)))
        assertEquals(Money.ZERO, exec.cost(TourismCommand.SetOverbooking(me, p.id, 10L)))
        assertEquals(Money.ZERO, exec.cost(TourismCommand.UpgradeProperty(me, PropertyId(404), 4)))
    }

    // ----- attractions -----

    @Test fun buildingAndRunningAnAttraction() {
        val r = run(TourismCommand.BuildAttraction(me, w.dest.id, AttractionKind.MUSEUM, "City Museum", 1_500, Money.units(14)))
        assertTrue(r.ok && r.message.contains("City Museum"))
        assertEquals(1, r.intents.size)
        val a = w.state.attractionsOf(me).single()
        assertTrue(run(TourismCommand.SetTicketPrice(me, a.id, Money.units(20))).ok)
        assertTrue(run(TourismCommand.SetAttractionCapacity(me, a.id, 2_000)).ok)
        assertTrue(run(TourismCommand.SetAttractionOpen(me, a.id, false)).ok)
        assertEquals(Money.units(20), a.ticketPrice)
        assertEquals(2_000, a.capacityPerDay)
        assertFalse(a.open)
    }

    @Test fun attractionCostScalesWithCapacity() {
        val full = exec.cost(TourismCommand.BuildAttraction(me, w.dest.id, AttractionKind.MUSEUM, "A", 1_500, Money.units(14)))
        val half = exec.cost(TourismCommand.BuildAttraction(me, w.dest.id, AttractionKind.MUSEUM, "A", 750, Money.units(14)))
        assertEquals(AttractionKind.MUSEUM.buildCost, full)
        assertEquals(Money(full.raw / 2), half)
    }

    @Test fun badAttractionOrdersAreRefused() {
        assertEquals(Rules.INVALID_QUANTITY, run(TourismCommand.BuildAttraction(me, w.dest.id, AttractionKind.MUSEUM, "A", 0, Money.units(1))).rejection?.rule)
        assertEquals(Rules.INVALID_PRICE, run(TourismCommand.BuildAttraction(me, w.dest.id, AttractionKind.MUSEUM, "A", 10, Money(-1))).rejection?.rule)
    }

    // ----- distribution and contracts -----

    @Test fun foundingAnOperatorAndAPlatform() {
        val op = run(TourismCommand.FoundOperator(me, "Sunways", 200_000L))
        assertTrue(op.ok && op.message.contains("Sunways"))
        val operator = w.state.operatorsOf(me).single()
        assertEquals(200_000L, operator.marginPpm)
        assertTrue(run(TourismCommand.SetOperatorMargin(me, operator.id, 250_000L)).ok)
        assertEquals(250_000L, operator.marginPpm)
        val pl = run(TourismCommand.FoundIntermediary(me, IntermediaryKind.ONLINE_PLATFORM, "BookIt", 140_000L, 1_000_000L, Money.units(100)))
        assertTrue(pl.ok)
        val platform = w.state.intermediariesOf(me).single()
        assertTrue(run(TourismCommand.SetCommission(me, platform.id, 120_000L)).ok)
        assertEquals(120_000L, platform.commissionPpm)
        assertEquals(Rules.INVALID_ARGUMENT, run(TourismCommand.SetCommission(me, platform.id, 900_000L)).rejection?.rule)
    }

    @Test fun anAllotmentIsOfferedAcceptedAndEndedByCommand() {
        val p = w.hotel(who = me)
        val op = w.operator(who = other)
        val net = Money(p.rackRate(RoomClass.STANDARD).raw * 650_000L / 1_000_000L)
        val offer = run(TourismCommand.ProposeAllotment(me, ContractSide.PROPERTY, op.id, p.id, RoomClass.STANDARD, 10, net, w.tick, 60, 3, 500_000L))
        assertTrue(offer.ok)
        val a = w.state.allotments.single()
        assertEquals(AllotmentStatus.PROPOSED, a.status)
        assertEquals(Rules.NOT_OWNER, run(TourismCommand.AcceptAllotment(me, a.id)).rejection?.rule)
        assertTrue(run(TourismCommand.AcceptAllotment(other, a.id)).ok)
        assertEquals(AllotmentStatus.ACTIVE, a.status)
        val end = run(TourismCommand.TerminateAllotment(other, a.id))
        assertTrue(end.ok)
        assertEquals(1, end.intents.size)
        assertEquals(AllotmentStatus.CANCELLED, a.status)
    }

    @Test fun declineClosesAnOffer() {
        val p = w.hotel(who = me)
        val op = w.operator(who = other)
        val net = Money(p.rackRate(RoomClass.STANDARD).raw * 650_000L / 1_000_000L)
        run(TourismCommand.ProposeAllotment(me, ContractSide.PROPERTY, op.id, p.id, RoomClass.STANDARD, 10, net, w.tick, 60, 3, 0L))
        val a = w.state.allotments.single()
        assertTrue(run(TourismCommand.DeclineAllotment(other, a.id)).ok)
        assertEquals(AllotmentStatus.DECLINED, a.status)
        assertEquals(Rules.WRONG_STATE, run(TourismCommand.DeclineAllotment(other, a.id)).rejection?.rule)
    }

    @Test fun anIndependentOperatorAnswersAtOnce() {
        val p = w.hotel(who = me)
        val op = w.operator()
        val net = Money(p.rackRate(RoomClass.STANDARD).raw * 650_000L / 1_000_000L)
        val r = run(TourismCommand.ProposeAllotment(me, ContractSide.PROPERTY, op.id, p.id, RoomClass.STANDARD, 10, net, w.tick, 60, 3, 0L))
        assertTrue(r.message.contains("active"))
    }

    @Test fun campaignsCostWhatTheySay() {
        assertEquals(Money.units(60_000), exec.cost(TourismCommand.LaunchCampaign(me, w.dest.id, null, Money.units(60_000))))
        val r = run(TourismCommand.LaunchCampaign(me, w.dest.id, VisitorSegment.FAMILY, Money.units(60_000)))
        assertTrue(r.ok)
        assertEquals(Money.units(60_000), r.intents.single().total)
        assertTrue(w.dest.marketingPpm[VisitorSegment.FAMILY.ordinal] > 0L)
        assertEquals(Rules.INVALID_QUANTITY, run(TourismCommand.LaunchCampaign(me, w.dest.id, null, Money.units(1))).rejection?.rule)
    }

    // ----- descriptions -----

    @Test fun everyCommandHasAReadableDescription() {
        val p = PropertyId(3); val a = AttractionId(4); val o = OperatorId(5); val i = IntermediaryId(6); val al = AllotmentId(7)
        val all = listOf<TourismCommand>(
            TourismCommand.BuildProperty(me, w.dest.id, PropertyKind.RESORT, 4, listOf(10, 5, 1), "Palm"),
            TourismCommand.SetRates(me, p, RatePolicy.DYNAMIC, Money.units(120)), TourismCommand.SetPricingBand(me, p, 1L, 2L, 3L, 4L),
            TourismCommand.SetOverbooking(me, p, 60_000L), TourismCommand.SetCancellation(me, p, CancellationPolicy.NON_REFUNDABLE),
            TourismCommand.Listing(me, p, i, true), TourismCommand.Listing(me, p, i, false), TourismCommand.ExpandProperty(me, p, RoomClass.SUITE, 4),
            TourismCommand.UpgradeProperty(me, p, 5), TourismCommand.RenovateProperty(me, p), TourismCommand.CloseProperty(me, p), TourismCommand.ReopenProperty(me, p),
            TourismCommand.BuildAttraction(me, w.dest.id, AttractionKind.SKI_LIFT, "Lift", 100, Money.units(30)),
            TourismCommand.SetTicketPrice(me, a, Money.units(12)), TourismCommand.SetAttractionCapacity(me, a, 80), TourismCommand.SetAttractionOpen(me, a, true),
            TourismCommand.SetAttractionOpen(me, a, false), TourismCommand.FoundOperator(me, "Op", 150_000L), TourismCommand.SetOperatorMargin(me, o, 120_000L),
            TourismCommand.FoundIntermediary(me, IntermediaryKind.AGENCY, "Agency", 100_000L, 1_000_000L, Money.ZERO), TourismCommand.SetCommission(me, i, 90_000L),
            TourismCommand.ProposeAllotment(me, ContractSide.OPERATOR, o, p, RoomClass.STANDARD, 8, Money.units(70), 10L, 30, 3, 0L),
            TourismCommand.AcceptAllotment(me, al), TourismCommand.DeclineAllotment(me, al), TourismCommand.TerminateAllotment(me, al),
            TourismCommand.LaunchCampaign(me, w.dest.id, null, Money.units(5_000)),
        )
        val lines = all.map { TourismCommandExecutor.describe(it) }
        assertTrue(lines.all { it.isNotBlank() && it.length < 120 })
        assertEquals(lines.size, lines.toSet().size)
        assertTrue(TourismCommandExecutor.describe(all[0]).contains("Palm"))
        assertTrue(TourismCommandExecutor.describe(all[5]).startsWith("List"))
        assertTrue(TourismCommandExecutor.describe(all[6]).startsWith("Delist"))
    }

    @Test fun resultsKnowWhetherTheyWorked() {
        assertTrue(TourismCommandResult("done").ok)
        assertFalse(TourismCommandResult("no", rejection = app.many.economytycoon.core.Rejection("x", "no")).ok)
        assertNull(TourismCommandResult("done").rejection)
        assertNotNull(build().intents.firstOrNull())
    }
}
