package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConcessionsTest {
    private val a = CompanyId(1)
    private val b = CompanyId(2)
    private val c3 = CompanyId(3)

    private fun toll(book: ConcessionBook, id: Long = 1, road: Long? = 1, model: PaymentModel = PaymentModel.TOLL, term: Int = 240, build: Int = 12,
                     availability: Money = Money.ZERO, guarantee: Long = 0L, share: Long = 200_000L, now: Long = 0): Concession =
        book.tender(
            ConcessionId(id), ConcessionKind.TOLL_ROAD, "Toll $id", road?.let { LinkId(it) }, null, model, term, build, Money.units(120_000_000), Money.units(3),
            availability, share, guarantee, Tick(now),
        ).getOrThrow()

    private fun award(book: ConcessionBook, c: Concession, who: CompanyId = a, discount: Long = 100_000L): Long {
        book.bid(c.id, who, discount, Tick(1)).getOrThrow()
        val close = c.bidsCloseAt.value
        book.advanceDay(Tick(close))
        return close
    }

    private fun open(book: ConcessionBook, c: Concession): Long {
        val t = award(book, c)
        val opens = c.operatesFrom!!.value
        book.advanceDay(Tick(opens))
        return maxOf(t, opens)
    }

    @Test fun `tender terms are validated`() {
        val book = ConcessionBook()
        fun t(name: String = "T", kind: ConcessionKind = ConcessionKind.TOLL_ROAD, road: LinkId? = LinkId(1), line: TransitLineId? = null, model: PaymentModel = PaymentModel.TOLL,
              term: Int = 240, build: Int = 12, toll: Money = Money.units(3), avail: Money = Money.ZERO, share: Long = 100_000L) =
            book.tender(ConcessionId(1), kind, name, road, line, model, term, build, Money.units(1_000_000), toll, avail, share, 0L, Tick(0)).rejectionOrNull()?.rule
        assertEquals(Rules.INVALID_ARGUMENT, t(name = ""))
        assertEquals(Rules.INVALID_ARGUMENT, t(term = 12))
        assertEquals(Rules.INVALID_ARGUMENT, t(term = 600))
        assertEquals(Rules.INVALID_ARGUMENT, t(build = 200))
        assertEquals(Rules.INVALID_ARGUMENT, t(toll = Money.units(-1)))
        assertEquals(Rules.INVALID_ARGUMENT, t(share = 2_000_000L))
        assertEquals(Rules.INVALID_ARGUMENT, t(road = null))
        assertEquals(Rules.INVALID_ARGUMENT, t(kind = ConcessionKind.TRANSIT_LINE, road = null, line = null))
        assertEquals(Rules.INVALID_ARGUMENT, t(kind = ConcessionKind.ROAD_MAINTENANCE, model = PaymentModel.TOLL, avail = Money.units(1)))
        assertEquals(Rules.INVALID_ARGUMENT, t(model = PaymentModel.AVAILABILITY, avail = Money.ZERO))
        assertNull(t())
    }

    @Test fun `a road can be under only one concession`() {
        val book = ConcessionBook()
        toll(book)
        assertEquals(Rules.DUPLICATE, book.tender(ConcessionId(2), ConcessionKind.TOLL_ROAD, "Again", LinkId(1), null, PaymentModel.TOLL, 240, 12, Money.units(1), Money.units(1), Money.ZERO, 0L, 0L, Tick(0)).rejectionOrNull()?.rule)
        assertEquals(book.get(ConcessionId(1)), book.onRoad(LinkId(1)))
    }

    @Test fun `bids are checked and unique per company`() {
        val book = ConcessionBook()
        val c = toll(book)
        assertEquals(Rules.UNKNOWN_ENTITY, book.bid(ConcessionId(9), a, 0L, Tick(1)).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, book.bid(c.id, a, -1L, Tick(1)).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, book.bid(c.id, a, ConcessionBook.MAX_DISCOUNT_PPM + 1, Tick(1)).rejectionOrNull()?.rule)
        assertTrue(book.bid(c.id, a, 50_000L, Tick(1)).isOk)
        assertEquals(Rules.DUPLICATE, book.bid(c.id, a, 90_000L, Tick(2)).rejectionOrNull()?.rule)
        assertEquals(Rules.WRONG_STATE, book.bid(c.id, b, 90_000L, c.bidsCloseAt).rejectionOrNull()?.rule)
        assertEquals(1, c.bids.size)
    }

    @Test fun `the largest discount wins and ties go to the earlier bid`() {
        val book = ConcessionBook()
        val c = toll(book)
        book.bid(c.id, a, 100_000L, Tick(5))
        book.bid(c.id, b, 150_000L, Tick(9))
        book.bid(c.id, c3, 150_000L, Tick(7))
        val w = book.winner(c)!!
        assertEquals(c3, w.company)
        assertNull(book.winner(toll(book, 2, 2)))
    }

    @Test fun `a tender without bids ends with none`() {
        val book = ConcessionBook()
        val c = toll(book)
        val events = book.advanceDay(c.bidsCloseAt)
        assertEquals(listOf(ConcessionEvent.NoBids(c.id, c.bidsCloseAt)), events)
        assertEquals(ConcessionStatus.NO_BIDS, c.status)
        assertTrue(book.tendering().isEmpty())
        assertTrue(book.advanceDay(Tick(900)).isEmpty())
    }

    @Test fun `an award starts construction and then operation`() {
        val book = ConcessionBook()
        val c = toll(book, build = 12)
        book.bid(c.id, a, 80_000L, Tick(1))
        assertTrue(book.advanceDay(Tick(10)).isEmpty())
        val awarded = book.advanceDay(c.bidsCloseAt)
        assertEquals(listOf(ConcessionEvent.Awarded(c.id, a, 80_000L, c.bidsCloseAt)), awarded)
        assertEquals(ConcessionStatus.CONSTRUCTION, c.status)
        assertEquals(c.bidsCloseAt.value + 12 * 30, c.operatesFrom!!.value)
        assertEquals(c.operatesFrom!!.value + 240 * 30, c.endsAt!!.value)
        assertEquals(listOf(c), book.heldBy(a))
        val opened = book.advanceDay(c.operatesFrom!!)
        assertEquals(listOf<ConcessionEvent>(ConcessionEvent.Opened(c.id, c.operatesFrom!!)), opened)
        assertEquals(ConcessionStatus.OPERATING, c.status)
    }

    @Test fun `no construction means it opens at once`() {
        val book = ConcessionBook()
        val c = toll(book, build = 0)
        book.bid(c.id, a, 0L, Tick(1))
        val events = book.advanceDay(c.bidsCloseAt)
        assertEquals(2, events.size)
        assertTrue(events[1] is ConcessionEvent.Opened)
        assertEquals(ConcessionStatus.OPERATING, c.status)
    }

    @Test fun `the concession is handed back at the end of the term`() {
        val book = ConcessionBook()
        val c = toll(book, term = 60, build = 0)
        open(book, c)
        val events = book.advanceDay(c.endsAt!!)
        assertEquals(listOf<ConcessionEvent>(ConcessionEvent.HandedBack(c.id, c.endsAt!!)), events)
        assertEquals(ConcessionStatus.ENDED, c.status)
        assertTrue(book.live().isEmpty())
        assertNull(book.settleMonth(c.id, 10_000L, 1000))
    }

    @Test fun `during construction the concessionaire spends its capital evenly`() {
        val book = ConcessionBook()
        val c = toll(book, build = 12)
        award(book, c)
        var total = Money.ZERO
        repeat(12) {
            val s = book.settleMonth(c.id, 0L, 1000)!!
            assertEquals(Money.ZERO, s.userCharges)
            total += s.capexSpend
        }
        assertEquals(c.capex, total)
        assertEquals(Money.ZERO, book.settleMonth(c.id, 0L, 1000)!!.capexSpend)
        assertEquals(c.capex, c.capexPaid)
    }

    @Test fun `a toll concession shares user charges with the city`() {
        val book = ConcessionBook()
        val c = toll(book, build = 0, share = 200_000L)
        open(book, c)
        val s = book.settleMonth(c.id, 20_000L, 1000)!!
        assertEquals(Money.units(3) * (20_000L * 30), s.userCharges)
        assertEquals(300_000L, c.cityShareNowPpm)
        assertEquals(s.userCharges.raw * 300_000 / 1_000_000, s.cityShare.raw)
        assertEquals(Money.ZERO, s.availabilityDue)
        assertEquals(s.userCharges - s.cityShare, s.concessionaireIncome)
        assertEquals(s.userCharges, c.userChargesTotal)
    }

    @Test fun `an availability concession charges nobody and is paid by the city`() {
        val book = ConcessionBook()
        val c = toll(book, model = PaymentModel.AVAILABILITY, build = 0, availability = Money.units(1_000_000))
        open(book, c)
        val s = book.settleMonth(c.id, 50_000L, 1000)!!
        assertEquals(Money.ZERO, s.userCharges)
        assertEquals(Money.units(1_000_000).raw * 900_000 / 1_000_000, s.availabilityDue.raw)
        assertEquals(s.availabilityDue, s.cityPays)
        assertEquals(s.availabilityDue, c.availabilityPaidTotal)
    }

    @Test fun `poor availability reduces the payment, twice as fast below 80 percent`() {
        val book = ConcessionBook()
        val c = toll(book, model = PaymentModel.AVAILABILITY, build = 0, availability = Money.units(1_000_000))
        open(book, c)
        val good = book.settleMonth(c.id, 0L, 960)!!
        val fair = book.settleMonth(c.id, 0L, 900)!!
        val bad = book.settleMonth(c.id, 0L, 700)!!
        val dead = book.settleMonth(c.id, 0L, 0)!!
        assertEquals(Money.ZERO, good.deduction)
        assertEquals(fair.availabilityDue.raw * 100 / 1000, fair.deduction.raw)
        assertEquals(bad.availabilityDue.raw * 600 / 1000, bad.deduction.raw)
        assertEquals(dead.availabilityDue, dead.deduction)
        assertEquals(Money.ZERO, dead.availabilityNet)
        assertEquals(fair.deduction + bad.deduction + dead.deduction, c.deductionsTotal)
    }

    @Test fun `a mixed concession both charges and is paid`() {
        val book = ConcessionBook()
        val c = toll(book, model = PaymentModel.MIXED, build = 0, availability = Money.units(500_000))
        open(book, c)
        val s = book.settleMonth(c.id, 10_000L, 1000)!!
        assertTrue(s.userCharges.isPositive && s.availabilityDue.isPositive)
        assertEquals(s.concessionaireIncome, s.userCharges - s.cityShare + s.availabilityNet)
    }

    @Test fun `the traffic guarantee pays half the missing tolls`() {
        val book = ConcessionBook()
        val c = toll(book, build = 0, guarantee = 30_000L)
        open(book, c)
        val low = book.settleMonth(c.id, 20_000L, 1000)!!
        val high = book.settleMonth(c.id, 40_000L, 1000)!!
        assertEquals(Money.units(3).raw * 10_000 * 30 / 2, low.guarantee.raw)
        assertEquals(Money.ZERO, high.guarantee)
        assertEquals(low.guarantee, low.cityPays)
        assertEquals(low.guarantee, c.guaranteePaidTotal)
    }

    @Test fun `termination pays the unrecovered investment less a tenth`() {
        val book = ConcessionBook()
        val c = toll(book, build = 12, term = 240)
        award(book, c)
        repeat(12) { book.settleMonth(c.id, 0L, 1000) }
        book.advanceDay(c.operatesFrom!!)
        val halfway = Tick(c.operatesFrom!!.value + 120 * 30)
        val owed = book.terminate(c.id, halfway).getOrThrow()
        assertEquals(c.capex.raw / 2 * 9 / 10, owed.raw)
        assertEquals(ConcessionStatus.TERMINATED, c.status)
        assertEquals(halfway, c.endsAt)
        assertEquals(Rules.WRONG_STATE, book.terminate(c.id, halfway).rejectionOrNull()?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, book.terminate(ConcessionId(8), halfway).rejectionOrNull()?.rule)
    }

    @Test fun `unrecovered capex is full before opening and zero at the end`() {
        val book = ConcessionBook()
        val c = toll(book, build = 0, term = 120)
        open(book, c)
        c.capexPaid = Money.units(1_200)
        assertEquals(Money.units(1_200), c.unrecoveredCapex(c.operatesFrom!!))
        assertEquals(Money.ZERO, c.unrecoveredCapex(Tick(c.operatesFrom!!.value + 9_999)))
        assertEquals(Money.units(600), c.unrecoveredCapex(Tick(c.operatesFrom!!.value + 60 * 30)))
    }

    @Test fun `tolls can be reopened once a year with compensation for a cut`() {
        val book = ConcessionBook()
        val c = toll(book, build = 0)
        val t = open(book, c)
        assertEquals(Rules.INVALID_ARGUMENT, book.renegotiateToll(c.id, Money.units(-1), 1000L, Tick(t + 1)).rejectionOrNull()?.rule)
        val comp = book.renegotiateToll(c.id, Money.units(2), 10_000L, Tick(t + 1)).getOrThrow()
        assertEquals(Money.units(1) * (10_000L * 360), comp)
        val now = book.get(c.id)!!
        assertEquals(Money.units(2), now.toll)
        assertEquals(ConcessionStatus.OPERATING, now.status)
        assertEquals(a, now.concessionaire)
        assertEquals(Rules.RATE_LIMITED, book.renegotiateToll(c.id, Money.units(5), 1000L, Tick(t + 100)).rejectionOrNull()?.rule)
        val raise = book.renegotiateToll(c.id, Money.units(5), 1000L, Tick(t + 400)).getOrThrow()
        assertEquals(Money.ZERO, raise)
    }

    @Test fun `renegotiation keeps the history of settlements`() {
        val book = ConcessionBook()
        val c = toll(book, build = 0)
        val t = open(book, c)
        book.settleMonth(c.id, 1_000L, 1000)
        val before = c.userChargesTotal
        book.renegotiateToll(c.id, Money.units(4), 0L, Tick(t + 1))
        assertEquals(before, book.get(c.id)!!.userChargesTotal)
        assertEquals(1, book.get(c.id)!!.bids.size)
    }

    @Test fun `renegotiation is refused in the wrong state or model`() {
        val book = ConcessionBook()
        val tender = toll(book, 1, 1)
        assertEquals(Rules.WRONG_STATE, book.renegotiateToll(tender.id, Money.units(1), 0L, Tick(5)).rejectionOrNull()?.rule)
        val avail = toll(book, 2, 2, model = PaymentModel.AVAILABILITY, build = 0, availability = Money.units(1))
        open(book, avail)
        assertEquals(Rules.WRONG_STATE, book.renegotiateToll(avail.id, Money.units(1), 0L, Tick(900)).rejectionOrNull()?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, book.renegotiateToll(ConcessionId(9), Money.units(1), 0L, Tick(900)).rejectionOrNull()?.rule)
    }

    @Test fun `hash changes with state`() {
        fun h(b: ConcessionBook) = CanonicalHasher().also { b.hashInto(it) }.finish()
        val x = ConcessionBook(); val y = ConcessionBook()
        toll(x); toll(y)
        assertEquals(h(x), h(y))
        y.bid(ConcessionId(1), a, 10_000L, Tick(2))
        assertTrue(h(x) != h(y))
    }
}
