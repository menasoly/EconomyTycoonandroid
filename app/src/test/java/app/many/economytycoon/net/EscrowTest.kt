package app.many.economytycoon.net

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscrowTest {
    private val a = PlayerId(1); private val b = PlayerId(2); private val c = PlayerId(3)

    private class Harness(config: EscrowConfig = EscrowConfig(), val supportsGoods: Boolean = true) {
        val sim = ToySimulation(income = false)
        val book = EscrowBook(config)
        val coA: Long; val coB: Long; val coC: Long
        init {
            coA = (sim.seat(PlayerId(1), "Alpha Co") as Outcome.Ok).value
            coB = (sim.seat(PlayerId(2), "Beta Co") as Outcome.Ok).value
            coC = (sim.seat(PlayerId(3), "Gamma Co") as Outcome.Ok).value
            sim.giveGoods(coA, "wheat", 5_000); sim.giveGoods(coB, "steel", 2_000)
        }
        val startCash = sim.totalCash()
        val view = object : EscrowView {
            override fun owns(player: PlayerId, company: Long) = sim.companiesOf(player).contains(company)
            override fun supports(kind: AssetKind) = supportsGoods || kind == AssetKind.CASH
            override fun available(company: Long, asset: TradeAsset) = sim.available(company, asset)
        }
        val transcript = ArrayList<String>()

        fun tick(): List<Transfer> {
            book.beginTick(sim.tick)
            val transfers = book.drain()
            val results = sim.applyTransfers(transfers)
            book.applied(results)
            transcript += transfers.zip(results).joinToString { "${it.first.from}>${it.first.to}:${it.first.asset.amount}:${it.second}" }
            sim.advance()
            check()
            return transfers
        }

        fun check() {
            assertEquals(emptyList<String>(), book.invariants())
            assertEquals("cash is conserved", startCash, sim.totalCash())
            assertEquals("escrow account matches the book", sim.escrowCash, book.heldTotals()[AssetKind.CASH to ""] ?: 0L)
            assertEquals(sim.escrowGoods["wheat"] ?: 0L, book.heldTotals()[AssetKind.GOODS to "wheat"] ?: 0L)
            assertEquals(5_000L, sim.totalGoods("wheat"))
            assertEquals(2_000L, sim.totalGoods("steel"))
        }

        fun offer(give: List<TradeAsset>, want: List<TradeAsset>, ttl: Int = 10, from: PlayerId = PlayerId(1), fromCo: Long = coA, to: PlayerId = PlayerId(2), toCo: Long = coB) =
            book.offer(view, from, fromCo, to, toCo, give, want, sim.tick, ttl)

        fun cash(co: Long) = sim.companies[co]?.cash ?: 0L
    }

    private fun cash(n: Long) = TradeAsset.cash(n)
    private fun rule(o: Outcome<*>) = (o as Outcome.Rejected).rejection.rule
    private fun id(o: Outcome<TradeId>) = (o as Outcome.Ok).value

    @Test fun offerLocksTheGiversAssetsOnTheNextTick() {
        val h = Harness()
        val before = h.cash(h.coA)
        val t = id(h.offer(listOf(cash(1_000_000)), listOf(TradeAsset.goods("steel", 500))))
        assertEquals(before, h.cash(h.coA))
        val sent = h.tick()
        assertEquals(1, sent.size)
        assertEquals(before - 1_000_000, h.cash(h.coA))
        assertEquals(1_000_000L, h.sim.escrowCash)
        assertEquals(TradeState.OFFERED, h.book.trade(t)?.state)
    }

    @Test fun fullTradeSettlesBothWays() {
        val h = Harness()
        val a0 = h.cash(h.coA); val b0 = h.cash(h.coB)
        val t = id(h.offer(listOf(cash(2_000_000)), listOf(TradeAsset.goods("steel", 700))))
        h.tick()
        assertTrue(h.book.accept(h.view, t, b, h.sim.tick) is Outcome.Ok)
        h.tick()
        assertEquals(TradeState.ACCEPTED, h.book.trade(t)?.state)
        h.tick()
        assertEquals(TradeState.SETTLED, h.book.trade(t)?.state)
        assertEquals(a0 - 2_000_000, h.cash(h.coA))
        assertEquals(b0 + 2_000_000, h.cash(h.coB))
        assertEquals(700L, h.sim.companies[h.coA]?.goods?.get("steel"))
        assertEquals(1_300L, h.sim.companies[h.coB]?.goods?.get("steel"))
        assertEquals(0L, h.sim.escrowCash)
        assertTrue(h.book.heldTotals().isEmpty())
        h.tick()
        assertEquals(0, h.book.open().size)
    }

    @Test fun cashForCashAndGoodsForGoodsWork() {
        val h = Harness()
        val t1 = id(h.offer(listOf(cash(100)), listOf(cash(250))))
        val t2 = id(h.offer(listOf(TradeAsset.goods("wheat", 40)), listOf(TradeAsset.goods("steel", 10))))
        h.tick()
        h.book.accept(h.view, t1, b, h.sim.tick); h.book.accept(h.view, t2, b, h.sim.tick)
        repeat(3) { h.tick() }
        assertEquals(TradeState.SETTLED, h.book.trade(t1)?.state)
        assertEquals(TradeState.SETTLED, h.book.trade(t2)?.state)
        assertEquals(10L, h.sim.companies[h.coA]?.goods?.get("steel"))
        assertEquals(40L, h.sim.companies[h.coB]?.goods?.get("wheat"))
    }

    @Test fun giftsNeedNothingBack() {
        val h = Harness()
        val t = id(h.offer(listOf(cash(5_000)), emptyList()))
        h.tick()
        h.book.accept(h.view, t, b, h.sim.tick)
        assertEquals(TradeState.ACCEPTED, h.book.trade(t)?.state)
        h.tick()
        assertEquals(TradeState.SETTLED, h.book.trade(t)?.state)
        assertEquals(0L, h.sim.escrowCash)
    }

    @Test fun declineReturnsEverything() {
        val h = Harness()
        val a0 = h.cash(h.coA)
        val t = id(h.offer(listOf(cash(900_000)), listOf(cash(10))))
        h.tick()
        assertTrue(h.book.decline(t, b) is Outcome.Ok)
        h.tick(); h.tick()
        assertEquals(TradeState.DECLINED, h.book.trade(t)?.state)
        assertEquals(a0, h.cash(h.coA))
        assertEquals(0L, h.sim.escrowCash)
    }

    @Test fun cancelReturnsEverythingToTheGiver() {
        val h = Harness()
        val a0 = h.cash(h.coA)
        val t = id(h.offer(listOf(cash(900_000), TradeAsset.goods("wheat", 100)), listOf(cash(10))))
        h.tick()
        assertEquals(Rules.NOT_OWNER, rule(h.book.cancel(t, b)))
        assertTrue(h.book.cancel(t, a) is Outcome.Ok)
        h.tick(); h.tick()
        assertEquals(TradeState.CANCELLED, h.book.trade(t)?.state)
        assertEquals(a0, h.cash(h.coA))
        assertEquals(5_000L, h.sim.companies[h.coA]?.goods?.get("wheat"))
    }

    @Test fun unansweredOffersExpireAndUnlock() {
        val h = Harness()
        val a0 = h.cash(h.coA)
        val t = id(h.offer(listOf(cash(900_000)), listOf(cash(10)), ttl = 3))
        repeat(2) { h.tick() }
        assertEquals(TradeState.OFFERED, h.book.trade(t)?.state)
        repeat(3) { h.tick() }
        assertEquals(TradeState.EXPIRED, h.book.trade(t)?.state)
        assertEquals(a0, h.cash(h.coA))
        assertEquals(0L, h.sim.escrowCash)
    }

    @Test fun cannotAcceptAnExpiredOrAlreadyAcceptedOffer() {
        val h = Harness()
        val t = id(h.offer(listOf(cash(10)), listOf(cash(5)), ttl = 2))
        h.tick()
        assertTrue(h.book.accept(h.view, t, b, h.sim.tick) is Outcome.Ok)
        assertEquals(Rules.WRONG_STATE, rule(h.book.accept(h.view, t, b, h.sim.tick)))
        val late = id(h.offer(listOf(cash(10)), listOf(cash(5)), ttl = 1))
        h.tick(); h.tick()
        assertEquals(Rules.WRONG_STATE, rule(h.book.accept(h.view, late, b, h.sim.tick)))
    }

    @Test fun onlyTheAddresseeCanAcceptOrDecline() {
        val h = Harness()
        val t = id(h.offer(listOf(cash(10)), emptyList()))
        h.tick()
        assertEquals(Rules.NOT_OWNER, rule(h.book.accept(h.view, t, c, h.sim.tick)))
        assertEquals(Rules.NOT_OWNER, rule(h.book.decline(t, a)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(h.book.accept(h.view, TradeId(99), b, h.sim.tick)))
    }

    @Test fun anAcceptedTradeCannotBeWithdrawn() {
        val h = Harness()
        val t = id(h.offer(listOf(cash(10)), emptyList()))
        h.tick(); h.book.accept(h.view, t, b, h.sim.tick); h.tick()
        assertEquals(Rules.WRONG_STATE, rule(h.book.cancel(t, a)))
        assertEquals(Rules.WRONG_STATE, rule(h.book.decline(t, b)))
    }

    // ---- validation ----

    @Test fun offersAreValidated() {
        val h = Harness()
        assertEquals(Rules.INVALID_ARGUMENT, rule(h.offer(listOf(cash(5)), emptyList(), to = a, toCo = h.coA)))
        assertEquals(Rules.NOT_OWNER, rule(h.offer(listOf(cash(5)), emptyList(), fromCo = h.coC)))
        assertEquals(Rules.NOT_OWNER, rule(h.offer(listOf(cash(5)), emptyList(), toCo = h.coC)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(h.offer(emptyList(), listOf(cash(5)))))
        assertEquals(Rules.INVALID_QUANTITY, rule(h.offer(listOf(cash(0)), emptyList())))
        assertEquals(Rules.INVALID_QUANTITY, rule(h.offer(listOf(cash(-5)), emptyList())))
        assertEquals(Rules.DUPLICATE, rule(h.offer(listOf(cash(5), cash(6)), emptyList())))
        assertEquals(Rules.INVALID_ARGUMENT, rule(h.offer(listOf(TradeAsset(AssetKind.GOODS, "", 5)), emptyList())))
        assertEquals(Rules.INVALID_ARGUMENT, rule(h.offer(listOf(TradeAsset(AssetKind.CASH, "x", 5)), emptyList())))
        assertEquals(Rules.INVALID_ARGUMENT, rule(h.offer(listOf(cash(5)), emptyList(), ttl = 0)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(h.offer(listOf(cash(5)), emptyList(), ttl = 100_000)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(h.offer(listOf(cash(5)), emptyList(), fromCo = 0, toCo = 0, to = b)))
    }

    @Test fun tooManyLinesAreRefused() {
        val h = Harness()
        val many = (1..5).map { TradeAsset.goods("g$it", 1) }
        assertEquals(Rules.LIMIT_EXCEEDED, rule(h.offer(many, emptyList())))
    }

    @Test fun cannotOfferWhatIsNotThere() {
        val h = Harness()
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(h.offer(listOf(cash(h.cash(h.coA) + 1)), emptyList())))
        assertEquals(Rules.INSUFFICIENT_STOCK, rule(h.offer(listOf(TradeAsset.goods("wheat", 5_001)), emptyList())))
        assertEquals(Rules.INSUFFICIENT_STOCK, rule(h.offer(listOf(TradeAsset.goods("gold", 1)), emptyList())))
    }

    @Test fun goodsAreRefusedWhenThePortCannotMoveThem() {
        val h = Harness(supportsGoods = false)
        assertEquals(Rules.NOT_UNLOCKED, rule(h.offer(listOf(TradeAsset.goods("wheat", 1)), emptyList())))
        assertEquals(Rules.NOT_UNLOCKED, rule(h.offer(listOf(cash(1)), listOf(TradeAsset.goods("steel", 1)))))
        assertTrue(h.offer(listOf(cash(1)), emptyList()) is Outcome.Ok)
    }

    @Test fun theSameCashCannotBePromisedTwice() {
        val h = Harness()
        val most = h.cash(h.coA) - 100
        assertTrue(h.offer(listOf(cash(most)), emptyList()) is Outcome.Ok)
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(h.offer(listOf(cash(most)), emptyList())))
        h.tick()
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(h.offer(listOf(cash(most)), emptyList())))
        assertTrue(h.offer(listOf(cash(100)), emptyList()) is Outcome.Ok)
    }

    @Test fun theReceiverCannotPromiseMoreThanTheyHave() {
        val h = Harness()
        val t = id(h.offer(listOf(cash(10)), listOf(cash(h.cash(h.coB) + 1))))
        h.tick()
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(h.book.accept(h.view, t, b, h.sim.tick)))
        assertEquals(TradeState.OFFERED, h.book.trade(t)?.state)
    }

    @Test fun openOfferLimitPerPlayer() {
        val h = Harness(EscrowConfig(maxOpenPerPlayer = 2))
        h.offer(listOf(cash(1)), emptyList()); h.offer(listOf(cash(1)), emptyList())
        assertEquals(Rules.LIMIT_EXCEEDED, rule(h.offer(listOf(cash(1)), emptyList())))
    }

    // ---- failures ----

    @Test fun lockThatCannotLandFailsTheTradeAndRestoresEverything() {
        val h = Harness()
        h.offer(listOf(cash(h.cash(h.coA) - 1_000), TradeAsset.goods("wheat", 50)), emptyList()).let { id(it) }
        // the giver spends money before the lock is applied
        h.sim.companies[h.coA]?.cash = 100
        h.sim.companies[h.coC]?.cash = (h.sim.companies[h.coC]?.cash ?: 0L) + (10_000_000L - 100 - 1_000_000L) // keep totals equal
        val before = h.sim.totalCash()
        h.book.beginTick(h.sim.tick)
        val transfers = h.book.drain()
        h.book.applied(h.sim.applyTransfers(transfers))
        assertEquals(before, h.sim.totalCash())
        h.book.trade(TradeId(1))?.let { assertEquals(TradeState.FAILED, it.state) }
        // the wheat part of the lock landed and has to come back
        h.book.beginTick(h.sim.tick + 1)
        h.book.applied(h.sim.applyTransfers(h.book.drain()))
        assertEquals(5_000L, h.sim.companies[h.coA]?.goods?.get("wheat"))
        assertEquals(0L, h.sim.escrowGoods["wheat"] ?: 0L)
        assertEquals(emptyList<String>(), h.book.invariants())
    }

    @Test fun payoutToAGoneCompanyIsStrandedAndRetried() {
        val h = Harness()
        val t = id(h.offer(listOf(cash(500)), listOf(cash(300))))
        h.tick(); h.book.accept(h.view, t, b, h.sim.tick); h.tick()
        h.sim.companies[h.coB]?.alive = false
        h.tick()
        assertEquals(TradeState.SETTLED, h.book.trade(t)?.state)
        assertEquals(1, h.book.strandedTransfers().size)
        assertEquals(500L, h.book.strandedTransfers().single().asset.amount)
        h.check()
        h.sim.companies[h.coB]?.alive = true
        h.tick(); h.tick()
        assertTrue(h.book.strandedTransfers().isEmpty())
        assertTrue(h.book.heldTotals().isEmpty())
    }

    @Test fun strandedRetriesAreBounded() {
        val h = Harness(EscrowConfig(strandedRetries = 2))
        val t = id(h.offer(listOf(cash(500)), emptyList()))
        h.tick(); h.book.accept(h.view, t, b, h.sim.tick)
        h.sim.companies[h.coB]?.alive = false
        repeat(8) { h.tick() }
        assertEquals(1, h.book.strandedTransfers().size)
        assertEquals(0, h.book.pendingTransfers())
        h.check()
    }

    @Test fun drainRespectsTheTransferBudgetAndKeepsGroupsWhole() {
        val h = Harness(EscrowConfig(maxOpenPerPlayer = 50))
        repeat(10) { h.offer(listOf(cash(10), TradeAsset.goods("wheat", 1)), emptyList()) }
        h.book.beginTick(0)
        val first = h.book.drain(limit = 5)
        assertEquals(4, first.size)
        h.book.applied(h.sim.applyTransfers(first))
        val second = h.book.drain(limit = 100)
        assertEquals(16, second.size)
        h.book.applied(h.sim.applyTransfers(second))
        assertEquals(0, h.book.pendingTransfers())
    }

    @Test fun reportingTheWrongNumberOfResultsIsRefused() {
        val h = Harness()
        h.offer(listOf(cash(10)), emptyList())
        h.book.beginTick(0)
        h.book.drain()
        try { h.book.applied(emptyList()); org.junit.Assert.fail() } catch (e: IllegalArgumentException) { assertTrue(true) }
    }

    // ---- notices, persistence, determinism ----

    @Test fun bothPartiesAreToldOfEveryChange() {
        val h = Harness()
        val t = id(h.offer(listOf(cash(10)), listOf(cash(5))))
        h.tick()
        h.book.accept(h.view, t, b, h.sim.tick)
        repeat(3) { h.tick() }
        val states = h.book.takeNotices().map { it.state }
        assertEquals(listOf(TradeState.OFFERED, TradeState.ACCEPTED, TradeState.SETTLED), states)
        assertTrue(h.book.takeNotices().isEmpty())
    }

    @Test fun bookSurvivesASaveAndLoadMidTrade() {
        val h = Harness()
        val t1 = id(h.offer(listOf(cash(1_000)), listOf(cash(500))))
        val t2 = id(h.offer(listOf(TradeAsset.goods("wheat", 20)), emptyList()))
        h.tick()
        h.book.accept(h.view, t1, b, h.sim.tick)
        val w = ByteWriter(); h.book.write(w)
        val copy = EscrowBook(); copy.read(ByteReader(w.toByteArray()))
        val w2 = ByteWriter(); copy.write(w2)
        assertArrayEquals(w.toByteArray(), w2.toByteArray())
        assertEquals(emptyList<String>(), copy.invariants())
        assertEquals(h.book.all().map { it.state }, copy.all().map { it.state })
        // the loaded copy carries on from where the original was
        copy.beginTick(h.sim.tick)
        h.book.beginTick(h.sim.tick)
        assertEquals(h.book.drain().map { it.asset.amount }, copy.drain().map { it.asset.amount })
        assertEquals(TradeState.OFFERED, copy.trade(t2)?.state)
    }

    @Test fun corruptBookDataIsRefused() {
        val w = ByteWriter(); w.writeVarInt(5)
        try { EscrowBook().read(ByteReader(w.toByteArray())); org.junit.Assert.fail() } catch (e: ByteReader.CorruptDataException) { assertTrue(true) }
        val h = Harness(); h.offer(listOf(cash(5)), emptyList())
        val ok = ByteWriter(); h.book.write(ok)
        val bytes = ok.toByteArray()
        for (n in 0 until bytes.size) {
            try { EscrowBook().read(ByteReader(bytes.copyOf(n))); org.junit.Assert.fail("cut at $n") } catch (e: ByteReader.CorruptDataException) { assertTrue(true) }
        }
    }

    private fun randomRun(seed: Long): Harness {
        val h = Harness(EscrowConfig(maxOpenPerPlayer = 4))
        val rnd = DeterministicRandom(seed)
        val players = listOf(PlayerId(1) to h.coA, PlayerId(2) to h.coB, PlayerId(3) to h.coC)
        for (round in 0 until 220) {
            repeat(rnd.nextInt(3)) {
                val (from, fromCo) = players[rnd.nextInt(3)]
                val (to, toCo) = players[rnd.nextInt(3)]
                fun asset(): TradeAsset = when (rnd.nextInt(3)) {
                    0 -> cash(1 + rnd.nextInt(2_000_000).toLong())
                    1 -> TradeAsset.goods("wheat", 1 + rnd.nextInt(300).toLong())
                    else -> TradeAsset.goods("steel", 1 + rnd.nextInt(300).toLong())
                }
                val give = listOf(asset()); val want = if (rnd.nextBoolean()) listOf(asset()) else emptyList()
                h.book.offer(h.view, from, fromCo, to, toCo, give.distinct(), want.distinct(), h.sim.tick, 1 + rnd.nextInt(12))
            }
            for (t in h.book.open()) when (rnd.nextInt(6)) {
                0 -> h.book.accept(h.view, t.id, t.to, h.sim.tick)
                1 -> h.book.decline(t.id, t.to)
                2 -> h.book.cancel(t.id, t.from)
                else -> Unit
            }
            if (rnd.nextInt(40) == 0) h.sim.companies[h.coC]?.alive = !(h.sim.companies[h.coC]?.alive ?: true)
            if (rnd.nextInt(30) == 0) {
                val spender = h.sim.companies[h.coA]
                val spent = (spender?.cash ?: 0L) / 2
                spender?.cash = (spender?.cash ?: 0L) - spent
                h.sim.companies[h.coB]?.let { it.cash += spent }
            }
            h.tick()
        }
        h.sim.companies[h.coC]?.alive = true
        repeat(10) { h.tick() }
        return h
    }

    @Test fun randomisedTradingNeverBreaksConservation() {
        for (seed in 1L..5L) {
            val h = randomRun(seed)
            assertTrue("some trades happened", h.book.all().size > 20)
            assertTrue(h.book.all().any { it.state == TradeState.SETTLED })
            h.check()
        }
    }

    @Test fun everyTradeEndsInATerminalStateOnceTimeHasPassed() {
        val h = randomRun(11)
        repeat(130) { h.tick() }
        assertEquals(emptyList<Trade>(), h.book.open())
        assertEquals(0L, h.sim.escrowCash - h.book.strandedTransfers().filter { it.asset.kind == AssetKind.CASH }.sumOf { it.asset.amount })
    }

    @Test fun sameInputsGiveTheSameHistory() {
        assertEquals(randomRun(3).transcript, randomRun(3).transcript)
        assertTrue(randomRun(3).transcript != randomRun(4).transcript)
    }
}
