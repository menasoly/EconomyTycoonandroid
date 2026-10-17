package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.LedgerOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Multi-step stories that cross several parts of the module. */
class TourismScenarioTest {
    private fun occupancy(p: Property, from: Int, to: Int): Long {
        val days = p.days.drop(from).take(to - from)
        return days.sumOf { it.roomsSold.toLong() } * 1_000_000L / days.sumOf { it.roomsAvailable.toLong() }.coerceAtLeast(1L)
    }

    @Test fun aNewResortOpensFillsUpAndEarnsItsKeepOverASeason() {
        val w = TourismWorld(startTick = 90L)
        val order = w.service.addProperty(w.dest.id, w.owner, null, PropertyKind.RESORT, 4, intArrayOf(120, 60, 20), Tick(w.tick), "Blue Bay").getOrThrow()
        val p = order.property
        assertTrue(p.days.isEmpty())
        w.days(PropertyKind.RESORT.buildDays + 60)
        assertEquals(PropertyStatus.OPEN, p.status)
        val k = TourismAnalytics.kpis(p, 30)
        assertTrue(k.roomsSold > 0L)
        assertTrue(k.totalRevenue.raw > 0L)
        TourismInvariants.check(w.state)
    }

    @Test fun aFestivalFillsTheTownAndDynamicPricingCharges() {
        val w = TourismWorld(startTick = 120L, homeOutbound = 180L, abroadOutbound = 240L)
        val fixed = w.hotel(rooms = intArrayOf(60, 0, 0))
        val smart = w.hotel(rooms = intArrayOf(60, 0, 0))
        w.service.setPricing(smart.id, RatePolicy.DYNAMIC, smart.settings.rackRate).getOrThrow()
        w.service.setFestival(w.dest.id, 6, 10).getOrThrow()
        w.days(60)
        val festival = 150L..159L
        fun adr(p: Property, range: LongRange): Money {
            val ds = p.days.filter { it.tick in range }
            return Money(ds.sumOf { it.roomRevenue.raw } / ds.sumOf { it.roomsSold }.coerceAtLeast(1))
        }
        val normal = 125L..139L
        assertTrue("dynamic ADR rises in the festival", adr(smart, festival) > adr(smart, normal))
        assertEquals("fixed ADR does not move", adr(fixed, festival), adr(fixed, normal))
        val mid = w.dest.days.filter { it.tick in festival }.sumOf { it.arrivals }
        val before = w.dest.days.filter { it.tick in 125L..134L }.sumOf { it.arrivals }
        assertTrue(mid > before)
    }

    @Test fun aStrikeThenARecoveryShowsUpInOccupancyAndInNews() {
        val w = TourismWorld(startTick = 120L)
        val p = w.hotel()
        w.days(40)
        val calm = occupancy(p, 20, 40)
        w.service.triggerShock(ShockKind.STRIKE, w.dest.id, null, Tick(w.tick), 14, 1_000_000L, "General strike").getOrThrow()
        val reports = w.days(14)
        val dip = occupancy(p, 46, 54)
        w.days(30)
        val recovered = occupancy(p, 70, 84)
        assertTrue("dip $dip vs calm $calm", dip < calm)
        assertTrue("recovered $recovered vs dip $dip", recovered > dip)
        assertTrue(reports.flatMap { it.events }.any { it is TourismEvent.ShockStarted })
        assertTrue(TourismNews(w.state) { it.toString() }.notes(reports.first()).any { it.headline == "General strike" })
    }

    @Test fun overbookingEarnsMoreUntilItStartsToWalkGuests() {
        fun revenue(overbook: Long): Pair<Long, Long> {
            val w = TourismWorld(seed = 5L, homeOutbound = 500L, abroadOutbound = 700L)
            val p = w.hotel(rooms = intArrayOf(50, 0, 0))
            w.service.setOverbooking(p.id, overbook).getOrThrow()
            w.days(60)
            val last = p.days.drop(20)
            return last.sumOf { it.roomRevenue.raw - it.walkCost.raw } to last.sumOf { it.walked.toLong() }
        }
        val (none, walkedNone) = revenue(0L)
        val (some, walkedSome) = revenue(80_000L)
        val (reckless, walkedReckless) = revenue(300_000L)
        assertEquals(0L, walkedNone)
        assertTrue(some > none)
        assertTrue(walkedReckless > walkedSome)
        assertTrue(reckless < some + (some - none) * 3)
    }

    @Test fun anAllotmentEmptiesIntoGeneralSaleAfterTheReleaseDate() {
        val w = TourismWorld(startTick = 120L, homeOutbound = 40L, abroadOutbound = 40L)
        val p = w.hotel(rooms = intArrayOf(60, 0, 0))
        val weak = w.operator(strength = 1L)
        val a = w.allotment(weak, p, rooms = 30, release = 5, committed = 0L, days = 60)
        w.days(8)
        var withheld = false
        for (date in w.tick..w.tick + 12) {
            val held = a.heldOn(date, (date - w.tick).toInt())
            if ((date - w.tick) > 5) withheld = withheld || held > 0
            if ((date - w.tick) <= 5) assertEquals(0, held)
        }
        assertTrue(withheld)
        w.days(30)
        TourismInvariants.check(w.state)
        assertTrue(p.days.takeLast(10).all { it.roomsSold > 0 })
    }

    @Test fun aPlatformWinsShareFromACompetitorAtTheCostOfCommission() {
        val w = TourismWorld(seed = 3L, homeOutbound = 60L, abroadOutbound = 80L)
        val listed = w.hotel(rooms = intArrayOf(200, 0, 0), name = "Listed")
        val unlisted = w.hotel(rooms = intArrayOf(200, 0, 0), name = "Unlisted")
        val platform = w.platform(commission = 180_000L)
        w.service.listWith(listed.id, platform.id).getOrThrow()
        w.days(60)
        val kl = TourismAnalytics.kpis(listed, 30); val ku = TourismAnalytics.kpis(unlisted, 30)
        assertTrue("listed fills more: ${kl.roomsSold} vs ${ku.roomsSold}", kl.roomsSold > ku.roomsSold)
        assertTrue(kl.commissions.raw > 0L)
        assertEquals(0L, ku.commissions.raw)
        assertTrue(kl.commissionShareOfRevenuePpm < 180_000L)
    }

    @Test fun aLoneHotelOnlyPaysForAPlatformThatSendsNoOneNew() {
        val bare = TourismWorld(seed = 3L, homeOutbound = 60L, abroadOutbound = 80L)
        val listed = TourismWorld(seed = 3L, homeOutbound = 60L, abroadOutbound = 80L)
        val a = bare.hotel(rooms = intArrayOf(200, 0, 0)); val b = listed.hotel(rooms = intArrayOf(200, 0, 0))
        listed.service.listWith(b.id, listed.platform().id).getOrThrow()
        bare.platform()
        bare.days(60); listed.days(60)
        assertEquals(TourismAnalytics.kpis(a, 30).roomsSold, TourismAnalytics.kpis(b, 30).roomsSold)
        assertTrue(TourismAnalytics.kpis(b, 30).gop < TourismAnalytics.kpis(a, 30).gop)
    }

    @Test fun staffingFollowsTheSeasonAndTheAdvisorSeesItComing() {
        val w = TourismWorld(startTick = 0L)
        val p = w.hotel(rooms = intArrayOf(120, 0, 0))
        w.days(100)
        val plan = Staffing.plan(p, listOf(0L, 7L, 14L, 21L), DemandForecast.weeklyOccupancyPpm(p, w.tick, TourismAnalytics.marketMix(w.state), 4))
        assertTrue(plan.peak >= plan.trough)
        val f = PropertyFacts.of(w.state, p, w.tick)
        assertTrue(f.staffNow > 0 && f.staffNextMonth > 0)
    }

    @Test fun aLevyTradesRevenueForGovernmentIncome() {
        val low = TourismWorld(startTick = 120L, seed = 9L)
        val high = TourismWorld(startTick = 120L, seed = 9L)
        low.hotel(); high.hotel()
        high.service.setTaxes(high.dest.id, Money.units(6), 40_000L, 0L).getOrThrow()
        low.days(30); high.days(30)
        val rl = low.settle(); val rh = high.settle()
        assertTrue(rl.intents.none { it.key.startsWith("tour:levy") })
        val levy = rh.intents.first { it.key.startsWith("tour:levy") }
        assertEquals(LedgerOwner.government(high.dest.country), levy.payee)
        assertTrue(levy.total.raw > 0L)
        val pl = requireNotNull(rl.statements.single().let { it }).revenue
        val ph = requireNotNull(rh.statements.single().let { it }).revenue
        assertTrue(ph <= pl)
    }

    @Test fun localBusinessFeelsTheTouristsInSummerMoreThanInWinter() {
        val w = TourismWorld(startTick = 0L, homeOutbound = 120L, abroadOutbound = 160L)
        w.hotel(rooms = intArrayOf(300, 0, 0))
        val inputs = DayInputs(residents = mapOf(w.dest.id to 60_000L))
        val winter = w.days(40, inputs).last().spendingOf(w.dest.id)!!.upliftOf(SpendCategory.SHOPS)
        w.days(150, inputs)
        val summer = w.days(10, inputs).last().spendingOf(w.dest.id)!!.upliftOf(SpendCategory.SHOPS)
        assertTrue("summer $summer vs winter $winter", summer > winter)
    }

    @Test fun twoHoteliersCompeteAndTheRivalAiKeepsUp() {
        val w = TourismWorld(startTick = 120L, homeOutbound = 160L, abroadOutbound = 220L)
        val player = w.hotel(who = w.owner)
        val rival = w.hotel(who = w.rival)
        w.platform()
        for (round in 1..4) {
            w.days(30)
            val v = HotelierAi.viewOf(w.state, w.rival, Money.units(10_000_000), HotelierStance.BALANCED, w.tick)
            HotelierAi.execute(w.service, v, HotelierAi.decide(v), Tick(w.tick))
            w.settle()
        }
        TourismInvariants.check(w.state)
        val kp = TourismAnalytics.kpis(player, 30)
        val kr = TourismAnalytics.kpis(rival, 30)
        assertTrue(kr.revPar.raw * 3 > kp.revPar.raw)
        assertEquals(RatePolicy.DYNAMIC, rival.settings.policy)
        assertEquals(RatePolicy.FIXED, player.settings.policy)
    }

    @Test fun aWholeYearKeepsEveryInvariantAndSavesCleanly() {
        val w = TourismWorld(startTick = 0L, homeOutbound = 120L, abroadOutbound = 160L)
        w.hotel(); w.hotel(4, intArrayOf(50, 25, 5)); w.attraction(AttractionKind.THEME_PARK); w.platform()
        for (m in 1..12) {
            w.days(30)
            w.settle()
        }
        TourismInvariants.check(w.state)
        val back = TourismCodec.fromBytes(TourismCodec.toBytes(w.state))
        assertEquals(w.state.stateHash().toHex(), back.stateHash().toHex())
        assertEquals(12, w.dest.months.size)
        assertEquals(11L, w.state.lastSettledMonth)
    }
}
