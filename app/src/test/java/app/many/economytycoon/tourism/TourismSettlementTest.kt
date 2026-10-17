package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.SettlementPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourismSettlementTest {
    private fun month(w: TourismWorld, startTick: Long = 120L) = w.also { it.days(30) }

    @Test fun anEmptyWorldSettlesToNothing() {
        val s = TourismService()
        s.advanceDay(Tick(29), DayInputs(), app.many.economytycoon.core.RandomProvider(1L))
        val run = s.settleMonth(Tick(29))
        assertTrue(run.intents.isEmpty() && run.statements.isEmpty() && run.attractionStatements.isEmpty())
        assertEquals(Money.ZERO, run.total)
    }

    @Test fun everyKeyIsUniqueAndNamesItsMonth() {
        val w = TourismWorld(startTick = 120L)
        w.hotel(); w.hotel(who = w.rival); w.attraction(); val i = w.platform(); w.service.listWith(w.state.properties[0].id, i.id).getOrThrow()
        w.allotment(w.operator(who = w.rival).also { }, w.state.properties[1], committed = 0L).let { }
        month(w)
        val run = w.settle()
        assertEquals(4L, run.month)
        val keys = run.intents.map { it.key }
        assertEquals(keys.toSet().size, keys.size)
        assertTrue(keys.all { it.startsWith("tour:") && (it.endsWith(":4") || it.endsWith(":4:dom") || it.endsWith(":4:for")) })
    }

    @Test fun roomRevenueComesFromGuestsAndGoesToTheOwner() {
        val w = TourismWorld(startTick = 120L)
        val p = w.hotel()
        month(w)
        val run = w.settle()
        val owner = LedgerOwner.company(w.owner)
        val revenue = run.intents.filter { it.key.startsWith("tour:rev:${p.id.value}:") }
        assertTrue(revenue.isNotEmpty())
        assertTrue(revenue.all { it.payee == owner && it.reason == ReasonCode.SALE && !it.deferrable })
        val st = requireNotNull(run.statementOf(p.id))
        assertEquals(st.guestRoomRevenue + st.ancillaryRevenue + st.levy, Money.sum(revenue.map { it.total }))
    }

    @Test fun domesticAndForeignGuestsPayFromDifferentSides() {
        val w = TourismWorld(startTick = 120L)
        w.hotel()
        month(w)
        val run = w.settle()
        val dom = run.intents.first { it.key.endsWith(":dom") && it.key.startsWith("tour:rev:") }
        val abroad = run.intents.first { it.key.endsWith(":for") && it.key.startsWith("tour:rev:") }
        assertEquals(LedgerOwner.households(w.dest.region.value), dom.payer)
        assertEquals(LedgerOwner.SYSTEM, abroad.payer)
    }

    @Test fun costsAreChargedToTheOwnerWithTheRightPriorities() {
        val w = TourismWorld(startTick = 120L)
        val p = w.hotel()
        month(w)
        val run = w.settle()
        val wages = run.intents.first { it.key.startsWith("tour:wage:${p.id.value}") }
        val running = run.intents.first { it.key.startsWith("tour:run:${p.id.value}") }
        assertEquals(SettlementPriority.WAGES, wages.priority)
        assertEquals(ReasonCode.WAGES, wages.reason)
        assertEquals(LedgerOwner.company(w.owner), running.payer)
        val st = requireNotNull(run.statementOf(p.id))
        assertEquals(st.wageCost, wages.total)
        assertEquals(st.operatingCost, running.total)
    }

    @Test fun statementsAddUp() {
        val w = TourismWorld(startTick = 120L)
        val p = w.hotel()
        month(w)
        val run = w.settle()
        val st = requireNotNull(run.statementOf(p.id))
        assertEquals(st.guestRoomRevenue + st.operatorRoomRevenue + st.ancillaryRevenue, st.revenue)
        assertEquals(st.revenue - st.commissions - st.walkCost - st.operatingCost - st.wageCost - st.vat - st.listingFees, st.profit)
        assertEquals(p.days.takeLast(30).sumOf { it.roomsSold.toLong() }, st.roomsSold)
        assertEquals(p.days.takeLast(30).sumOf { it.roomsAvailable.toLong() }, st.roomsAvailable)
        assertTrue(st.occupancyPpm in 1..1_000_000L)
    }

    @Test fun theMonthsTotalsAreClearedAfterSettling() {
        val w = TourismWorld(startTick = 120L)
        val p = w.hotel(); val a = w.attraction()
        month(w)
        assertTrue(p.month.roomsSold > 0L && a.monthVisits > 0L)
        w.settle()
        assertEquals(0L, p.month.roomsSold)
        assertEquals(Money.ZERO, p.month.guestRoomRevenue)
        assertEquals(0L, a.monthVisits)
        assertEquals(Money.ZERO, a.monthRevenue)
    }

    @Test fun twoMonthsSettleSeparatelyWithDifferentKeys() {
        val w = TourismWorld(startTick = 120L)
        w.hotel()
        month(w)
        val first = w.settle()
        month(w)
        val second = w.settle()
        assertEquals(first.month + 1, second.month)
        assertTrue(first.intents.map { it.key }.toSet().intersect(second.intents.map { it.key }.toSet()).isEmpty())
    }

    @Test fun governmentGetsVatAndLevy() {
        val w = TourismWorld(startTick = 120L)
        w.service.setTaxes(w.dest.id, Money.units(2), 10_000L, 100_000L).getOrThrow()
        val p = w.hotel(); val a = w.attraction()
        month(w)
        val run = w.settle()
        val gov = LedgerOwner.government(w.dest.country)
        val vat = run.intents.first { it.key == "tour:vat:${p.id.value}:4" }
        val levy = run.intents.first { it.key == "tour:levy:${p.id.value}:4" }
        val tix = run.intents.first { it.key == "tour:attvat:${a.id.value}:4" }
        for (i in listOf(vat, levy, tix)) { assertEquals(gov, i.payee); assertEquals(SettlementPriority.STATUTORY, i.priority); assertEquals(ReasonCode.TAX, i.reason) }
        val st = requireNotNull(run.statementOf(p.id))
        assertEquals(st.vat, vat.total)
        assertEquals(TourismTax.vatInside(st.revenue, 100_000L), vat.total)
        assertTrue(levy.total.raw > 0L)
    }

    @Test fun noTaxesMeansNoTaxIntents() {
        val w = TourismWorld(startTick = 120L)
        w.hotel()
        month(w)
        assertFalse(w.settle().intents.any { it.key.startsWith("tour:vat") || it.key.startsWith("tour:levy") })
    }

    @Test fun platformsAreCreditedWithCommissionAndListingFees() {
        val w = TourismWorld(startTick = 120L)
        val p = w.hotel()
        val platform = w.service.addIntermediary(IntermediaryKind.ONLINE_PLATFORM, w.rival, "BookIt", 150_000L, listingFee = Money.units(500)).getOrThrow()
        w.service.listWith(p.id, platform.id).getOrThrow()
        month(w)
        val run = w.settle()
        val comm = run.intents.first { it.key == "tour:comm:${p.id.value}:${platform.id.value}:4" }
        val fee = run.intents.first { it.key == "tour:list:${p.id.value}:${platform.id.value}:4" }
        assertEquals(LedgerOwner.company(w.rival), comm.payee)
        assertEquals(Money.units(500), fee.total)
        assertEquals(requireNotNull(run.statementOf(p.id)).commissions, comm.total)
        assertEquals(Money.units(500), requireNotNull(run.statementOf(p.id)).listingFees)
        assertEquals(Money.ZERO, platform.monthCommission)
    }

    @Test fun anOwnerlessPlatformIsPaidToTheSystem() {
        val w = TourismWorld(startTick = 120L)
        val p = w.hotel()
        val platform = w.platform()
        w.service.listWith(p.id, platform.id).getOrThrow()
        month(w)
        val comm = w.settle().intents.first { it.key.startsWith("tour:comm:") }
        assertEquals(LedgerOwner.SYSTEM, comm.payee)
    }

    @Test fun operatorsPayHotelsAndTheirCustomersPayOperators() {
        val w = TourismWorld(startTick = 120L)
        val p = w.hotel()
        val op = w.operator(who = w.rival)
        val a = w.allotment(op, p, rooms = 12, committed = 500_000L, days = 90).also { w.service.acceptAllotment(it.id, w.rival, Tick(w.tick)) }
        month(w)
        val run = w.settle()
        val pay = run.intents.first { it.key == "tour:allot:${a.id.value}:4" }
        assertEquals(LedgerOwner.company(w.rival), pay.payer)
        assertEquals(LedgerOwner.company(w.owner), pay.payee)
        val pkg = run.intents.first { it.key == "tour:pkg:${op.id.value}:4" }
        assertEquals(LedgerOwner.company(w.rival), pkg.payee)
        assertTrue(pkg.total > pay.total)
    }

    @Test fun committedRoomsArePaidForEvenIfNoneSold() {
        val w = TourismWorld(startTick = 120L, homeOutbound = 3L, abroadOutbound = 3L)
        val p = w.hotel(rooms = intArrayOf(200, 0, 0))
        val weak = w.operator(who = w.rival, strength = 1L)
        val a = w.allotment(weak, p, rooms = 20, committed = 1_000_000L, netShare = 600_000L, days = 90).also { w.service.acceptAllotment(it.id, w.rival, Tick(w.tick)) }
        month(w)
        val pay = w.settle().intents.first { it.key == "tour:allot:${a.id.value}:4" }
        assertTrue(pay.total.raw > 0L)
    }

    @Test fun attractionsAreSettledLikeBusinesses() {
        val w = TourismWorld(startTick = 120L)
        w.hotel(); val a = w.attraction()
        month(w)
        val run = w.settle()
        val st = run.attractionStatements.first { it.attraction == a.id }
        assertTrue(st.revenue.raw > 0L && st.visits > 0L)
        assertEquals(st.revenue - st.cost - st.vat, st.profit)
        val keys = run.intents.map { it.key }.filter { it.contains(":${a.id.value}:") }
        assertTrue(keys.any { it.startsWith("tour:tix:") } && keys.any { it.startsWith("tour:att:") })
    }

    @Test fun businessesWithoutAnOwnerGenerateNoIntentsButStillHaveStatements() {
        val w = TourismWorld(startTick = 120L)
        val p = w.hotel(who = null)
        month(w)
        val run = w.settle()
        assertTrue(run.intents.none { it.key.contains(":${p.id.value}:") })
        assertNotNull(run.statementOf(p.id))
    }

    @Test fun everyIntentMovesAPositiveAmountBetweenDifferentOwners() {
        val run = RichWorld.build().let { it.days(30); it.settle() }
        assertTrue(run.intents.isNotEmpty())
        for (i in run.intents) {
            assertTrue(i.total.raw > 0L)
            assertTrue(i.payer != i.payee)
        }
        assertEquals(Money.sum(run.intents.map { it.total }), run.total)
    }

    @Test fun settlingTwiceInTheSameMonthIsRefused() {
        val w = TourismWorld(startTick = 120L)
        w.hotel()
        month(w)
        w.settle()
        try { w.settle(); throw AssertionError("settled twice") } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test fun oldEndedContractsAndShocksAreForgottenEventually() {
        val w = TourismWorld(startTick = 120L)
        val p = w.hotel()
        val a = w.allotment(w.operator(), p, days = 10)
        w.service.triggerShock(ShockKind.CONFERENCE, w.dest.id, null, Tick(w.tick), 3, 500_000L, "Summit").getOrThrow()
        w.days(30)
        assertNotNull(w.state.allotment(a.id))
        w.days(200)
        assertEquals(null, w.state.allotment(a.id))
        assertTrue(w.state.shocks.none { it.label == "Summit" })
    }
}
