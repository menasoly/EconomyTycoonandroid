package app.many.economytycoon.tourism

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HotelierAiTest {
    private val me = CompanyId(300)

    private fun prop(
        id: Long = 1, policy: RatePolicy = RatePolicy.DYNAMIC, rack: Money = Money.units(100), reference: Money = Money.units(100), occ14: Long = 700_000L,
        occ30: Long = 700_000L, adrIndex: Long = 1_000_000L, rating: Int = 38, condition: Long = 900_000L, overbook: Long = 0L, show: Long = 900_000L,
        walked: Int = 0, listed: Int = 1, unmet: Long = 0L, upgrade: Money? = Money.units(300_000), renovation: Money = Money.units(100_000), perRoom: Money = Money.units(70_000),
        rooms: Int = 100, stars: Int = 3,
    ) = PropertyView(
        PropertyId(id), DestinationId(1), PropertyKind.HOTEL, stars, rooms, policy, rack, reference, occ14, occ30, adrIndex, rating, condition, overbook, show, walked,
        listed, unmet, upgrade, renovation, perRoom,
    )

    private fun view(
        props: List<PropertyView> = listOf(prop()), cash: Money = Money.units(5_000_000), stance: HotelierStance = HotelierStance.BALANCED,
        openings: List<Opening> = emptyList(), platforms: List<IntermediaryId> = emptyList(),
    ) = HotelierView(me, 200L, cash, stance, props, openings, platforms)

    private fun opening(unmet: Long = 300_000L, occ: Long = 900_000L, cost: Money = Money.units(3_000_000), rooms: Int = 60, rate: Money = Money.units(120)) =
        Opening(DestinationId(1), PropertyKind.HOTEL, 3, rooms, cost, occ, unmet, rate)

    private inline fun <reified T : HotelierMove> moves(v: HotelierView) = HotelierAi.decide(v).filterIsInstance<T>()

    // ----- stances -----

    @Test fun stancesGetBolderInOrder() {
        val c = HotelierStance.CAUTIOUS; val b = HotelierStance.BALANCED; val a = HotelierStance.AGGRESSIVE
        assertTrue(c.priceStepPpm < b.priceStepPpm && b.priceStepPpm < a.priceStepPpm)
        assertTrue(c.cashShareToSpendPpm < b.cashShareToSpendPpm && b.cashShareToSpendPpm < a.cashShareToSpendPpm)
        assertTrue(c.maxProperties < b.maxProperties && b.maxProperties < a.maxProperties)
        assertFalse(c.usesDynamicPricing)
        assertEquals(0L, c.maxOverbookPpm)
    }

    @Test(expected = IllegalArgumentException::class) fun aStanceNeedsAPositivePaybackLimit() {
        HotelierStance(1L, 1L, 0L, false, 0, 1)
    }

    // ----- pricing -----

    @Test fun aFullHotelRaisesItsRack() {
        val m = moves<HotelierMove.SetRack>(view(listOf(prop(occ14 = 950_000L))))
        assertEquals(1, m.size)
        assertTrue(m[0].rack > Money.units(100))
        assertEquals(Money.units(105), m[0].rack)
    }

    @Test fun rackNeverClimbsPastTheCeilingOverReference() {
        val capped = prop(occ14 = 950_000L, rack = Money.units(179))
        val m = moves<HotelierMove.SetRack>(view(listOf(capped)))
        assertEquals(Money.units(180), m.single().rack)
        assertTrue(moves<HotelierMove.SetRack>(view(listOf(prop(occ14 = 950_000L, rack = Money.units(180))))).isEmpty())
    }

    @Test fun anEmptyHotelCutsItsRackButNotBelowTheFloor() {
        val m = moves<HotelierMove.SetRack>(view(listOf(prop(occ14 = 300_000L))))
        assertEquals(Money.units(95), m.single().rack)
        val atFloor = prop(occ14 = 300_000L, rack = Money.units(56))
        assertEquals(Money.units(55), moves<HotelierMove.SetRack>(view(listOf(atFloor))).single().rack)
        assertTrue(moves<HotelierMove.SetRack>(view(listOf(prop(occ14 = 300_000L, rack = Money.units(55))))).isEmpty())
    }

    @Test fun aDearHalfEmptyHotelAlsoCuts() {
        assertEquals(1, moves<HotelierMove.SetRack>(view(listOf(prop(occ14 = 620_000L, adrIndex = 1_300_000L)))).size)
    }

    @Test fun aHotelInTheMiddleLeavesItsRackAlone() {
        assertTrue(moves<HotelierMove.SetRack>(view(listOf(prop(occ14 = 720_000L)))).isEmpty())
    }

    @Test fun biggerStepsForBolderStances() {
        val soft = moves<HotelierMove.SetRack>(view(listOf(prop(occ14 = 950_000L)), stance = HotelierStance.CAUTIOUS)).single().rack
        val hard = moves<HotelierMove.SetRack>(view(listOf(prop(occ14 = 950_000L)), stance = HotelierStance.AGGRESSIVE)).single().rack
        assertTrue(hard > soft)
    }

    @Test fun dynamicPricingIsSwitchedOnOnlyByStancesThatUseIt() {
        val fixed = listOf(prop(policy = RatePolicy.FIXED))
        assertEquals(1, moves<HotelierMove.UseDynamic>(view(fixed)).size)
        assertEquals(780_000L, moves<HotelierMove.UseDynamic>(view(fixed)).single().targetOccupancyPpm)
        assertTrue(moves<HotelierMove.UseDynamic>(view(fixed, stance = HotelierStance.CAUTIOUS)).isEmpty())
        assertTrue(moves<HotelierMove.UseDynamic>(view(listOf(prop(policy = RatePolicy.DYNAMIC)))).isEmpty())
    }

    // ----- overbooking and listing -----

    @Test fun overbookingFollowsTheShowRateAndTheStancesCap() {
        val m = moves<HotelierMove.SetOverbook>(view(listOf(prop(show = 850_000L, rooms = 200))))
        assertEquals(1, m.size)
        assertTrue(m[0].ppm in 1..HotelierStance.BALANCED.maxOverbookPpm)
    }

    @Test fun overbookingBacksOffAfterWalks() {
        val calm = moves<HotelierMove.SetOverbook>(view(listOf(prop(show = 850_000L, rooms = 200)))).single().ppm
        val burnt = moves<HotelierMove.SetOverbook>(view(listOf(prop(show = 850_000L, rooms = 200, walked = 6)))).single().ppm
        assertTrue(burnt <= calm / 2 + 1)
    }

    @Test fun anAverseStanceTurnsOverbookingOff() {
        val m = moves<HotelierMove.SetOverbook>(view(listOf(prop(overbook = 50_000L)), stance = HotelierStance.CAUTIOUS))
        assertEquals(0L, m.single().ppm)
        assertTrue(moves<HotelierMove.SetOverbook>(view(listOf(prop(overbook = 0L)), stance = HotelierStance.CAUTIOUS)).isEmpty())
    }

    @Test fun smallChangesToOverbookingAreNotWorthMaking() {
        val ideal = moves<HotelierMove.SetOverbook>(view(listOf(prop(show = 850_000L, rooms = 200)))).single().ppm
        assertTrue(moves<HotelierMove.SetOverbook>(view(listOf(prop(show = 850_000L, rooms = 200, overbook = ideal)))).isEmpty())
    }

    @Test fun anEmptyHotelListsOnAnOnlinePlatform() {
        val v = view(listOf(prop(listed = 0, occ30 = 400_000L)), platforms = listOf(IntermediaryId(7), IntermediaryId(8)))
        assertEquals(IntermediaryId(7), moves<HotelierMove.ListOnPlatform>(v).single().platform)
        assertTrue(moves<HotelierMove.ListOnPlatform>(view(listOf(prop(listed = 0, occ30 = 400_000L)))).isEmpty())
        assertTrue(moves<HotelierMove.ListOnPlatform>(view(listOf(prop(listed = 0, occ30 = 800_000L)), platforms = listOf(IntermediaryId(7)))).isEmpty())
    }

    // ----- maintenance and growth -----

    @Test fun aWornHotelIsRenovatedWhenAffordable() {
        val worn = prop(condition = 300_000L)
        assertEquals(1, moves<HotelierMove.Renovate>(view(listOf(worn))).size)
        assertTrue(moves<HotelierMove.Renovate>(view(listOf(worn), cash = Money.units(100_000))).isEmpty())
        assertTrue(moves<HotelierMove.Renovate>(view(listOf(prop(condition = 700_000L)))).isEmpty())
    }

    @Test fun aFullWellLikedHotelMovesUpAStar() {
        val star = prop(rating = 44, occ30 = 900_000L, adrIndex = 1_100_000L, upgrade = Money.units(200_000))
        val m = moves<HotelierMove.Upgrade>(view(listOf(star)))
        assertEquals(4, m.single().stars)
    }

    @Test fun noUpgradeWithoutAGoodRecordOrMoneyOrPayback() {
        val base = prop(rating = 44, occ30 = 900_000L, adrIndex = 1_100_000L, upgrade = Money.units(200_000))
        assertTrue(moves<HotelierMove.Upgrade>(view(listOf(base.copy(ratingTenths = 38)))).isEmpty())
        assertTrue(moves<HotelierMove.Upgrade>(view(listOf(base.copy(occupancy30Ppm = 700_000L)))).isEmpty())
        assertTrue(moves<HotelierMove.Upgrade>(view(listOf(base.copy(adrIndexPpm = 900_000L)))).isEmpty())
        assertTrue(moves<HotelierMove.Upgrade>(view(listOf(base.copy(upgradeCost = null)))).isEmpty())
        assertTrue(moves<HotelierMove.Upgrade>(view(listOf(base), cash = Money.units(100_000))).isEmpty())
        assertTrue(moves<HotelierMove.Upgrade>(view(listOf(base.copy(upgradeCost = Money.units(900_000_000))), cash = Money.units(5_000_000_000))).isEmpty())
    }

    @Test fun roomsAreAddedWhereVisitorsAreTurnedAway() {
        val m = moves<HotelierMove.Expand>(view(listOf(prop(occ30 = 920_000L, unmet = 300_000L, perRoom = Money.units(20_000)))))
        assertEquals(10, m.single().rooms)
        assertEquals(Money.units(200_000), m.single().estimate)
    }

    @Test fun noExpansionWithoutUnmetDemandOrFullHouse() {
        assertTrue(moves<HotelierMove.Expand>(view(listOf(prop(occ30 = 920_000L, unmet = 50_000L)))).isEmpty())
        assertTrue(moves<HotelierMove.Expand>(view(listOf(prop(occ30 = 700_000L, unmet = 400_000L)))).isEmpty())
    }

    @Test fun expansionSizeIsBounded() {
        assertEquals(5, moves<HotelierMove.Expand>(view(listOf(prop(rooms = 20, occ30 = 920_000L, unmet = 300_000L, perRoom = Money.units(1_000))))).single().rooms)
        assertEquals(50, moves<HotelierMove.Expand>(view(listOf(prop(rooms = 800, occ30 = 920_000L, unmet = 300_000L, perRoom = Money.units(1_000))))).single().rooms)
    }

    @Test fun theBudgetIsSharedBetweenMoves() {
        val tight = view(listOf(prop(condition = 300_000L, occ30 = 920_000L, unmet = 300_000L, perRoom = Money.units(40_000))), cash = Money.units(500_000), stance = HotelierStance.CAUTIOUS)
        val spend = HotelierAi.decide(tight).sumOf { it.cost.raw }
        assertTrue(spend <= Money.units(500_000).raw * HotelierStance.CAUTIOUS.cashShareToSpendPpm / 1_000_000L)
    }

    @Test fun aNewHouseGoesWhereDemandIsUnmet() {
        val best = opening(unmet = 400_000L)
        val v = view(openings = listOf(opening(unmet = 200_000L), best), cash = Money.units(20_000_000))
        val b = moves<HotelierMove.Build>(v)
        assertEquals(1, b.size)
        assertEquals(400_000L, b[0].opening.unmetPpm)
    }

    @Test fun noNewHouseWhenTheMarketIsSlackTooDearOrThePortfolioIsFull() {
        assertTrue(moves<HotelierMove.Build>(view(openings = listOf(opening(unmet = 50_000L)), cash = Money.units(20_000_000))).isEmpty())
        assertTrue(moves<HotelierMove.Build>(view(openings = listOf(opening(occ = 600_000L)), cash = Money.units(20_000_000))).isEmpty())
        assertTrue(moves<HotelierMove.Build>(view(openings = listOf(opening(cost = Money.units(900_000_000))))).isEmpty())
        val full = List(3) { prop(id = it + 1L) }
        assertTrue(moves<HotelierMove.Build>(view(props = full, stance = HotelierStance.CAUTIOUS, openings = listOf(opening()), cash = Money.units(50_000_000))).isEmpty())
    }

    @Test fun paybackIsCostOverYearlyProfit() {
        val years = HotelierAi.paybackYears(Money.units(3_000_000), 60, Money.units(120), 800_000L)
        assertEquals(5L, years)
        assertTrue(HotelierAi.paybackYears(Money.units(3_000_000), 60, Money.units(120), 400_000L) > years)
        assertEquals(Long.MAX_VALUE, HotelierAi.paybackYears(Money.units(3_000_000), 0, Money.units(120), 800_000L))
        assertTrue(HotelierAi.paybackYears(Money.ZERO, 60, Money.units(120), 800_000L) == 0L)
    }

    @Test fun noPropertiesNoMoves() {
        assertTrue(HotelierAi.decide(view(props = emptyList())).isEmpty())
    }

    @Test fun decisionsAreDeterministic() {
        val v = view(listOf(prop(occ14 = 950_000L, condition = 300_000L, listed = 0), prop(id = 2, occ14 = 300_000L)), platforms = listOf(IntermediaryId(1)), openings = listOf(opening()))
        assertEquals(HotelierAi.decide(v), HotelierAi.decide(v))
    }

    // ----- seeing and doing, against a real state -----

    @Test fun theViewOfARealCompanyListsItsOpenHotels() {
        val w = TourismWorld()
        val mine = w.hotel(who = me)
        w.hotel(who = w.rival)
        w.service.closeProperty(w.hotel(who = me).id).getOrThrow()
        w.platform()
        w.days(40)
        val v = HotelierAi.viewOf(w.state, me, Money.units(1_000_000), HotelierStance.BALANCED, w.tick)
        assertEquals(listOf(mine.id), v.properties.map { it.property })
        assertEquals(1, v.onlinePlatforms.size)
        assertEquals(1, v.openings.size)
        val pv = v.properties.single()
        assertEquals(mine.settings.rackRate, pv.rack)
        assertTrue(pv.occupancy14Ppm > 0L)
        assertTrue(pv.showPpm in 1..1_000_000L)
    }

    @Test fun movesAreCarriedOutThroughTheService() {
        val w = TourismWorld()
        val p = w.hotel(who = me)
        val platform = w.platform()
        val v = HotelierAi.viewOf(w.state, me, Money.units(20_000_000), HotelierStance.AGGRESSIVE, w.tick)
        val moves = listOf(
            HotelierMove.SetRack(p.id, Money.units(130)), HotelierMove.UseDynamic(p.id, 800_000L), HotelierMove.SetOverbook(p.id, 60_000L),
            HotelierMove.ListOnPlatform(p.id, platform.id), HotelierMove.Expand(p.id, 10, Money.ZERO),
        )
        val results = HotelierAi.execute(w.service, v, moves, Tick(w.tick))
        assertTrue(results.all { it.outcome.isOk })
        assertEquals(Money.units(130), p.settings.rackRate)
        assertEquals(RatePolicy.DYNAMIC, p.settings.policy)
        assertEquals(800_000L, p.settings.targetOccupancyPpm)
        assertEquals(60_000L, p.overbookPpm)
        assertTrue(platform.id in p.listedOn)
        assertEquals(110, p.totalRooms)
        assertEquals(1, results.last().intents.size)
        assertTrue(results.dropLast(1).all { it.intents.isEmpty() })
    }

    @Test fun refusedMovesComeBackAsRejections() {
        val w = TourismWorld()
        val p = w.hotel(who = me)
        val v = HotelierAi.viewOf(w.state, me, Money.units(1), HotelierStance.BALANCED, w.tick)
        val r = HotelierAi.execute(w.service, v, listOf(HotelierMove.SetOverbook(p.id, 900_000L), HotelierMove.Renovate(p.id, Money.ZERO)), Tick(w.tick))
        assertTrue(r.all { it.outcome.isRejected })
        assertTrue(r.all { it.intents.isEmpty() })
    }

    @Test fun buildingAHouseAddsAPropertyUnderConstruction() {
        val w = TourismWorld()
        w.hotel(who = w.rival)
        val v = HotelierAi.viewOf(w.state, me, Money.units(50_000_000), HotelierStance.BALANCED, w.tick)
        val r = HotelierAi.execute(w.service, v, listOf(HotelierMove.Build(opening(cost = Money.units(1)).copy(destination = w.dest.id))), Tick(w.tick)).single()
        assertTrue(r.outcome.isOk)
        val built = w.state.propertiesOf(me).single()
        assertEquals(PropertyStatus.UNDER_CONSTRUCTION, built.status)
        assertEquals(60, built.totalRooms)
        assertNotNull(r.intents.singleOrNull())
        assertNull(r.intents.firstOrNull { !it.key.startsWith("tour:capex:") })
    }

    @Test fun aRivalOverManyMonthsKeepsItsHouseTradingAndSolvent() {
        val w = TourismWorld(homeOutbound = 300L, abroadOutbound = 400L)
        val p = w.hotel(who = me, rooms = intArrayOf(40, 0, 0))
        w.hotel(who = w.owner)
        w.platform()
        for (round in 1..6) {
            w.days(30)
            val v = HotelierAi.viewOf(w.state, me, Money.units(30_000_000), HotelierStance.BALANCED, w.tick)
            HotelierAi.execute(w.service, v, HotelierAi.decide(v), Tick(w.tick))
            w.settle()
        }
        TourismInvariants.check(w.state)
        assertTrue(w.state.propertiesOf(me).isNotEmpty())
        assertTrue(p.lifetimeRevenue.raw > 0L)
        assertEquals(RatePolicy.DYNAMIC, p.settings.policy)
    }
}
