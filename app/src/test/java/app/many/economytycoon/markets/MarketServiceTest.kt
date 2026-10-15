package app.many.economytycoon.markets

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rejection
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.markets.MarketTestSupport.FakePopulation
import app.many.economytycoon.markets.MarketTestSupport.Rig
import app.many.economytycoon.markets.MarketTestSupport.alice
import app.many.economytycoon.markets.MarketTestSupport.bob
import app.many.economytycoon.markets.MarketTestSupport.bread
import app.many.economytycoon.markets.MarketTestSupport.carol
import app.many.economytycoon.markets.MarketTestSupport.flour
import app.many.economytycoon.markets.MarketTestSupport.gold
import app.many.economytycoon.markets.MarketTestSupport.north
import app.many.economytycoon.markets.MarketTestSupport.price
import app.many.economytycoon.markets.MarketTestSupport.qty
import app.many.economytycoon.markets.MarketTestSupport.salt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketServiceTest {
    private fun noPop() = FakePopulation(low = 0, middle = 0, high = 0)
    private fun rule(o: app.many.economytycoon.core.Outcome<*>) = o.rejectionOrNull()?.rule

    @Test fun crossingOrdersTradeAndFilledOrdersLeaveTheBook() {
        val r = Rig(noPop())
        val b = r.buy(alice, flour, 10.0, 30.0); val s = r.sell(bob, flour, 10.0, 20.0, source = 77)
        val rep = r.step()
        assertEquals(1, rep.trades.size)
        val t = rep.trades[0]
        assertEquals(qty(10.0), t.quantity)
        assertEquals(alice, t.buyer); assertEquals(bob, t.seller); assertEquals(77L, t.sellerSource)
        assertTrue(t.price.perUnit.raw in 200_000L..300_000L)
        assertNull(r.market.order(b.id)); assertNull(r.market.order(s.id))
        assertEquals(2, rep.filled.size)
        assertEquals(t.price.valueOf(t.quantity), rep.tradedValue)
    }

    @Test fun partialFillsKeepTheRemainder() {
        val r = Rig(noPop())
        val b = r.buy(alice, flour, 10.0, 30.0); r.sell(bob, flour, 4.0, 20.0)
        r.step()
        assertEquals(qty(6.0), r.market.order(b.id)!!.remaining)
        assertEquals(qty(4.0), r.market.order(b.id)!!.filled)
    }

    @Test fun nonCrossingOrdersRest() {
        val r = Rig(noPop())
        r.buy(alice, flour, 10.0, 20.0); r.sell(bob, flour, 10.0, 30.0)
        val rep = r.step()
        assertTrue(rep.trades.isEmpty())
        val q = r.market.quote(MarketKey(north, flour))
        assertEquals(price(20.0), q.bid); assertEquals(price(30.0), q.ask)
    }

    @Test fun marketOrdersTradeAtTheReferencePrice() {
        val r = Rig(noPop())
        r.buy(alice, flour, 5.0, null); r.sell(bob, flour, 5.0, null)
        val rep = r.step()
        assertEquals(1, rep.trades.size)
        assertTrue(rep.trades[0].price.perUnit.raw in 240_000L..260_000L)
    }

    @Test fun ordersExpireAndAreReported() {
        val r = Rig(noPop())
        val o = r.buy(alice, flour, 5.0, 10.0, ttl = 2)
        r.step()
        assertNotNull(r.market.order(o.id))
        val rep = r.step()
        assertEquals(listOf(o.id), rep.expired.map { it.id })
        assertNull(r.market.order(o.id))
    }

    @Test fun validationRejectsBadOrders() {
        val r = Rig(noPop())
        val k = MarketKey(north, flour)
        fun place(key: MarketKey = k, qty: Quantity = qty(1.0), limit: Double? = 25.0, ttl: Int = 5) =
            r.market.placeOrder(alice, key, Side.BUY, limit?.let { price(it) }, qty, ttl, 0, Tick(0))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(place(key = MarketKey(north, GoodId("nope")))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(place(key = MarketKey(RegionId(99), flour))))
        assertNotNull(place(key = MarketKey(north, GoodId("loose"))).getOrNull())
        assertEquals(Rules.INVALID_QUANTITY, rule(place(qty = Quantity.ofRaw(1))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(place(ttl = 0)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(place(ttl = 1000)))
        assertEquals(Rules.INVALID_PRICE, rule(place(limit = 0.001)))
        assertEquals(Rules.INVALID_PRICE, rule(place(limit = 100_000.0)))
        assertNotNull(place().getOrNull())
    }

    @Test fun rateLimitAppliesPerPartyPerTick() {
        val r = Rig(noPop())
        val limit = r.market.content.params.orderRateLimitPerTick
        repeat(limit) { r.buy(alice, flour, 1.0, 20.0) }
        assertEquals(Rules.RATE_LIMITED, rule(r.market.placeOrder(alice, MarketKey(north, flour), Side.BUY, price(20.0), qty(1.0), 5, 0, Tick(0))))
        assertNotNull(r.market.placeOrder(bob, MarketKey(north, flour), Side.BUY, price(20.0), qty(1.0), 5, 0, Tick(0)).getOrNull())
        r.step()
        assertNotNull(r.market.placeOrder(alice, MarketKey(north, flour), Side.BUY, price(20.0), qty(1.0), 5, 0, Tick(1)).getOrNull())
    }

    @Test fun openOrderCapIsEnforced() {
        val r = Rig(noPop())
        val cap = r.market.params.maxOpenOrdersPerParty
        var placed = 0
        while (placed < cap) {
            repeat(10) { if (placed < cap) { r.buy(alice, flour, 1.0, 20.0, ttl = 90); placed++ } }
            r.step()
        }
        assertEquals(Rules.LIMIT_EXCEEDED, rule(r.market.placeOrder(alice, MarketKey(north, flour), Side.BUY, price(20.0), qty(1.0), 90, 0, Tick(r.tick))))
    }

    @Test fun gateCanVetoOrders() {
        val r = Rig(noPop())
        r.market.gate = OrderGate { party, _, _, _, _ -> if (party == carol) Rejection(Rules.REGULATION, "sanctioned") else null }
        assertEquals(Rules.REGULATION, rule(r.market.placeOrder(carol, MarketKey(north, flour), Side.BUY, price(20.0), qty(1.0), 5, 0, Tick(0))))
        assertNotNull(r.market.placeOrder(alice, MarketKey(north, flour), Side.BUY, price(20.0), qty(1.0), 5, 0, Tick(0)).getOrNull())
    }

    @Test fun onlyTheOwnerCancels() {
        val r = Rig(noPop())
        val o = r.buy(alice, flour, 1.0, 20.0)
        assertEquals(Rules.NOT_OWNER, rule(r.market.cancelOrder(bob, o.id)))
        assertEquals(o.id, r.market.cancelOrder(alice, o.id).getOrThrow().id)
        assertEquals(Rules.UNKNOWN_ENTITY, rule(r.market.cancelOrder(alice, o.id)))
    }

    @Test fun cancelAllReturnsEverythingBooked() {
        val r = Rig(noPop())
        r.buy(alice, flour, 1.0, 20.0); r.sell(alice, flour, 1.0, 40.0, source = 5); r.buy(bob, flour, 1.0, 20.0)
        val gone = r.market.cancelAll(alice)
        assertEquals(2, gone.size)
        assertEquals(1, r.market.allOrders().size)
    }

    @Test fun depthAggregatesByPriceLevel() {
        val r = Rig(noPop())
        r.buy(alice, flour, 2.0, 20.0); r.buy(bob, flour, 3.0, 20.0); r.buy(alice, flour, 1.0, 19.0); r.sell(bob, flour, 4.0, 30.0)
        val d = r.market.depth(MarketKey(north, flour))
        val bids = d.filter { it.side == Side.BUY }
        assertEquals(price(20.0), bids[0].price); assertEquals(qty(5.0), bids[0].quantity); assertEquals(2, bids[0].orders)
        assertEquals(price(19.0), bids[1].price)
        assertEquals(qty(4.0), d.first { it.side == Side.SELL }.quantity)
    }

    @Test fun householdsBuyFromSellers() {
        val r = Rig()
        r.sell(alice, bread, 10_000.0, 30.0, ttl = 30)
        val rep = r.step()
        val spend = rep.householdSpend[north]!!
        assertTrue(spend.raw > 0)
        // 3000 units wanted at about 40 each
        val bought = rep.trades.filter { it.buyer.kind == PartyKind.HOUSEHOLDS }.sumOf { it.quantity.raw }
        assertTrue("bought $bought", bought in 2_500_000L..3_500_000L)
        assertEquals(qty(10_000.0) - Quantity(bought), r.market.allOrders().single().remaining)
    }

    @Test fun budgetCapsHouseholdSpending() {
        val r = Rig()
        r.sell(alice, bread, 10_000.0, 30.0, ttl = 30)
        val rep = r.step { Money.units(20_000) }
        assertTrue(rep.householdSpend[north]!!.raw <= Money.units(20_000).raw)
        assertTrue(rep.householdSpend[north]!!.raw > Money.units(15_000).raw)
    }

    @Test fun necessitiesClearBeforeLuxuries() {
        val r = Rig()
        r.sell(alice, salt, 10_000.0, 4.0, ttl = 30); r.sell(alice, bread, 10_000.0, 30.0, ttl = 30)
        val rep = r.step { Money.units(100) }
        val saltSpend = rep.trades.filter { it.key.good == salt && it.buyer.kind == PartyKind.HOUSEHOLDS }.sumOf { it.value.raw }
        val breadSpend = rep.trades.filter { it.key.good == bread && it.buyer.kind == PartyKind.HOUSEHOLDS }.sumOf { it.value.raw }
        assertTrue(saltSpend > 0)
        assertTrue(saltSpend > breadSpend)
    }

    @Test fun scarcityRaisesPricesWithinTheCap() {
        val r = Rig()
        val k = MarketKey(north, bread)
        var prev = r.market.price(k).perUnit.raw
        val move = app.many.economytycoon.markets.PriceRules.maxMovePpm(r.market.content.good(bread), r.market.content.params)
        repeat(20) {
            r.step()
            val now = r.market.price(k).perUnit.raw
            assertTrue("price fell from $prev to $now", now >= prev - prev / 50)
            assertTrue(now <= prev + prev * move / 1_000_000L + 1)
            prev = now
        }
        assertTrue(r.market.price(k).perUnit.raw > price(40.0).perUnit.raw)
        assertTrue(r.market.state(k)!!.unmetDemand.raw == 0L || r.market.state(k)!!.unmetDemand.raw > 0L)
    }

    @Test fun gluttedMarketsFallTowardTheFloor() {
        val r = Rig(noPop())
        r.sell(alice, flour, 500.0, 5.0, ttl = 90)
        repeat(40) { r.step() }
        assertTrue(r.market.price(MarketKey(north, flour)).perUnit.raw < price(25.0).perUnit.raw)
    }

    @Test fun priceNeverLeavesTheFloorAndCeiling() {
        val r = Rig()
        repeat(300) { r.step() }
        val ref = price(40.0).perUnit.raw
        val p = r.market.price(MarketKey(north, bread)).perUnit.raw
        assertTrue(p <= ref * 10)
        assertTrue(p >= ref / 10)
    }

    @Test fun shocksMovePricesAndFade() {
        val r = Rig(noPop())
        val k = MarketKey(north, gold)
        r.buy(alice, gold, 1.0, 1500.0); r.sell(bob, gold, 1.0, 600.0)
        r.market.applyShock(k, 150_000)
        val rep = r.step()
        assertTrue(rep.trades[0].price.perUnit.raw > price(1000.0).perUnit.raw)
        repeat(30) { r.step() }
        assertTrue(Math.abs(r.market.state(k)!!.shockPpm) < 1_000)
    }

    @Test fun demandShiftScalesHouseholdDemand() {
        val a = Rig(); a.sell(alice, bread, 100_000.0, 30.0, ttl = 30)
        val b = Rig(); b.sell(alice, bread, 100_000.0, 30.0, ttl = 30)
        b.market.setDemandShift(MarketKey(north, bread), 500_000)
        val sa = a.step().householdSpend[north]!!.raw; val sb = b.step().householdSpend[north]!!.raw
        assertTrue(sb * 2 in (sa * 9 / 10)..(sa * 11 / 10))
        val c = Rig(); c.sell(alice, bread, 100_000.0, 30.0, ttl = 30)
        c.market.setDemandShift(MarketKey(north, bread), 0)
        assertNull(c.step().householdSpend[north])
    }

    @Test fun regionsAreSeparateMarkets() {
        val r = Rig(FakePopulation(regions = listOf(north, MarketTestSupport.south), low = 0, middle = 0, high = 0))
        r.buy(alice, flour, 5.0, 30.0, region = north); r.sell(bob, flour, 5.0, 20.0, region = MarketTestSupport.south)
        assertTrue(r.step().trades.isEmpty())
    }

    @Test fun historyTracksCloses() {
        val r = Rig(noPop())
        r.buy(alice, flour, 1.0, null); r.sell(bob, flour, 1.0, null)
        r.step(); r.step(); r.step()
        val h = r.market.state(MarketKey(north, flour))!!.history
        assertEquals(3, h.size)
        assertTrue(h.volume(2)!! > 0)
        assertEquals(0L, h.volume(0))
    }

    @Test fun sameSeedSameHistory() {
        fun run(seed: Long): String {
            val r = Rig(seed = seed)
            r.sell(alice, bread, 5_000.0, 35.0, ttl = 90)
            repeat(30) { r.step { Money.units(50_000) } }
            return r.hash()
        }
        assertEquals(run(3), run(3))
        assertTrue(run(3) != run(4))
    }

    @Test fun saveAndLoadContinuesIdentically() {
        val r = Rig()
        r.sell(alice, bread, 20_000.0, 35.0, ttl = 90); r.buy(bob, flour, 10.0, 20.0, ttl = 90)
        repeat(35) { r.step() }
        val w = ByteWriter(); MarketCodec.write(r.market, w)
        val copy = Rig()
        MarketCodec.read(ByteReader(w.toByteArray()), copy.market)
        assertEquals(r.hash(), copy.hash())
        copy.random.restore(r.random.snapshot()); copy.tick = r.tick
        repeat(10) { r.step(); copy.step() }
        assertEquals(r.hash(), copy.hash())
    }

    @Test fun badSaveVersionIsRejected() {
        val w = ByteWriter(); w.writeVarInt(42)
        var failed = false
        try { MarketCodec.read(ByteReader(w.toByteArray()), Rig().market) } catch (e: ByteReader.CorruptDataException) { failed = true }
        assertTrue(failed)
    }
}
