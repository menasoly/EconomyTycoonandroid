package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.tenders.Tender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTendersTest {
    private class Rig {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        val bidder = AiTenders(e, CommandExecutor(e))
        fun ai(name: String): CompanyId = CompanyId((sim.execute(Actor.System, Command.FoundCompany(name, country.id, null, Money.units(300_000), CompanyKind.AI)) as Outcome.Ok).value.created!!)
        fun farm(owner: CompanyId, stock: Long) = e.production.grantFacility(owner, FacilityTypeId("grain_farm"), region, null, null, null, e.tick).also {
            it.inventory.add(GoodId("wheat"), Quantity.units(stock), Money.units(stock * 8))
        }
        val ref get() = e.content.good(GoodId("wheat")).referencePrice.perUnit
        fun tender(qty: Long): Tender = (e.tenders.issue(country.id, GoodId("wheat"), Quantity.units(qty), Money(ref.raw * 13 / 10), e.tick) as Outcome.Ok).value
    }

    @Test fun anAiCompanyWithTheStockBidsWithinTheBand() {
        val r = Rig(); val ai = r.ai("Rival Works"); r.farm(ai, 500)
        val t = r.tender(100)
        assertEquals(1, r.bidder.run(r.e.tick).bids)
        val bid = t.bidOf(ai)!!
        assertTrue(bid.unitPrice <= t.ceiling)
        assertTrue(bid.unitPrice >= r.e.tenderRunner.floorFor(t))
    }

    @Test fun withoutEnoughStockThereIsNoBid() {
        val r = Rig(); val ai = r.ai("Rival Works"); r.farm(ai, 20)
        val t = r.tender(100)
        assertEquals(0, r.bidder.run(r.e.tick).bids)
        assertNull(t.bidOf(ai))
    }

    @Test fun anAiCompanyBidsOnlyOncePerTender() {
        val r = Rig(); val ai = r.ai("Rival Works"); r.farm(ai, 500)
        val t = r.tender(100)
        r.bidder.run(r.e.tick); r.bidder.run(r.e.tick)
        assertEquals(1, t.bids.size)
    }

    @Test fun rivalsPriceDifferentlyAndTheCheaperWins() {
        val r = Rig(); val a = r.ai("Rival A"); val b = r.ai("Rival B")
        r.farm(a, 500); r.farm(b, 500)
        val t = r.tender(100)
        r.bidder.run(r.e.tick)
        assertEquals(2, t.bids.size)
        val prices = t.bids.map { it.unitPrice }
        assertTrue(prices.first() <= prices.last())
        assertEquals(prices.first(), t.leadingBid()!!.unitPrice)
    }

    @Test fun playersAreNeverBidForAutomatically() {
        val r = Rig()
        val p = CompanyId((r.sim.execute(Actor.Player(5), Command.FoundCompany("Mine Works", r.country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        r.farm(p, 500)
        val t = r.tender(100)
        r.bidder.run(r.e.tick)
        assertTrue(t.bids.isEmpty())
    }

    @Test fun aBarredCompanyDoesNotBid() {
        val r = Rig(); val ai = r.ai("Rival Works"); r.farm(ai, 500)
        repeat(3) {
            val old = r.tender(10)
            r.e.tenders.bid(old.id, ai, r.e.production.facilitiesOf(ai).first().id, r.ref, Money.ZERO, r.e.tick)
            r.e.tenders.closeBidding(old.closesAt) { true }
            r.e.tenders.failed(old, old.closesAt, Money.ZERO)
        }
        r.e.tick = app.many.economytycoon.core.Tick(30)
        val t = r.tender(100)
        r.bidder.run(r.e.tick)
        assertNull(t.bidOf(ai))
    }
}
