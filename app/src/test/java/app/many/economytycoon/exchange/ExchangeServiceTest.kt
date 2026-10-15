package app.many.economytycoon.exchange

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Holder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeServiceTest {
    private val a = CompanyId(1)
    private val b = CompanyId(2)
    private val investor = Holder("owner", 9)

    private class Facts(var fair: Money = Money.units(10), var sentiment: Long = 1_000_000L, var outstanding: Long = 1_000, var publicShares: Long = 250) : MarketFacts {
        override fun fairPrice(issuer: CompanyId, tick: Tick) = fair
        override fun publicShares(issuer: CompanyId) = publicShares
        override fun outstanding(issuer: CompanyId) = outstanding
        override fun sentimentPpm(issuer: CompanyId) = sentiment
    }

    private fun listed(price: Long = 10, params: ExchangeParams = ExchangeParams(valuationEvery = 1)): ExchangeService =
        ExchangeService(params).also { it.list(a, Tick(0), Money.units(price)) }

    private fun hash(s: ExchangeService) = CanonicalHasher().also { s.hashInto(it) }.finish().toHex()

    @Test fun aCompanyListsOnceAtItsPrice() {
        val s = ExchangeService()
        val l = (s.list(a, Tick(3), Money.units(12)) as Outcome.Ok).value
        assertEquals(Money.units(12), l.price)
        assertEquals(listOf(Money.units(12)), l.history)
        assertTrue(s.isListed(a))
        assertEquals(Rules.DUPLICATE, (s.list(a, Tick(4), Money.units(12)) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INVALID_PRICE, (s.list(b, Tick(4), Money(1)) as Outcome.Rejected).rejection.rule)
    }

    @Test fun delistingForgetsTheListingAndItsPositions() {
        val s = listed()
        s.addBasis(investor, a, Money.units(500))
        assertTrue(s.delist(a))
        assertFalse(s.isListed(a))
        assertEquals(Money.ZERO, s.basisOf(investor, a))
        assertFalse(s.delist(a))
    }

    @Test fun releasingBasisIsProportionalAndClearsAtTheEnd() {
        val s = listed()
        s.addBasis(investor, a, Money.units(1_000))
        assertEquals(Money.units(250), s.releaseBasis(investor, a, 25, 100))
        assertEquals(Money.units(750), s.basisOf(investor, a))
        assertEquals(Money.units(750), s.releaseBasis(investor, a, 75, 75))
        assertEquals(Money.ZERO, s.basisOf(investor, a))
        assertEquals(Money.ZERO, s.releaseBasis(investor, a, 1, 1))
    }

    @Test fun addingBasisAccumulates() {
        val s = listed()
        s.addBasis(investor, a, Money.units(100)); s.addBasis(investor, a, Money.units(50))
        assertEquals(Money.units(150), s.basisOf(investor, a))
        assertEquals(1, s.positionsOf(investor).size)
        assertEquals(a, s.positionsOf(investor).single().first)
    }

    @Test fun buyingCostsMoreThanTheMidPriceAndSellingPaysLess() {
        val s = listed()
        val buy = (s.quote(a, TradeSide.BUY, 10, 250) as Outcome.Ok).value
        val sell = (s.quote(a, TradeSide.SELL, 10, 250) as Outcome.Ok).value
        assertTrue(buy.averagePrice > Money.units(10))
        assertTrue(sell.averagePrice < Money.units(10))
        assertTrue(buy.net > buy.gross)
        assertTrue(sell.net < sell.gross)
        assertTrue(buy.duty.raw > 0L && sell.duty.raw == 0L)
    }

    @Test fun biggerOrdersMoveThePriceMore() {
        val s = listed()
        val small = (s.quote(a, TradeSide.BUY, 5, 250) as Outcome.Ok).value
        val big = (s.quote(a, TradeSide.BUY, 200, 250) as Outcome.Ok).value
        assertTrue(big.impactPpm > small.impactPpm)
        assertTrue(big.averagePrice > small.averagePrice)
    }

    @Test fun impactIsCappedAndFloatlessCompaniesAreMaximallyThin() {
        val s = listed()
        assertEquals(s.params.maxImpactPpm, s.impactPpm(Money.units(1_000_000), Money.units(1)))
        assertEquals(s.params.maxImpactPpm, s.impactPpm(Money.units(1), Money.ZERO))
        assertEquals(0L, s.impactPpm(Money.ZERO, Money.units(100)))
    }

    @Test fun quotesRefuseBadRequests() {
        val s = listed()
        assertEquals(Rules.UNKNOWN_ENTITY, (s.quote(b, TradeSide.BUY, 1, 10) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INVALID_QUANTITY, (s.quote(a, TradeSide.BUY, 0, 10) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INSUFFICIENT_STOCK, (s.quote(a, TradeSide.BUY, 11, 10) as Outcome.Rejected).rejection.rule)
        assertTrue(s.quote(a, TradeSide.SELL, 500, 10) is Outcome.Ok)
    }

    @Test fun aRecordedTradeCountsVolumeAndPushesThePrice() {
        val s = listed()
        val q = (s.quote(a, TradeSide.BUY, 100, 250) as Outcome.Ok).value
        val before = s.listing(a)!!.price
        s.recordTrade(q, 250)
        val l = s.listing(a)!!
        assertEquals(100L, l.volumeToday)
        assertEquals(q.gross, l.turnoverToday)
        assertTrue(l.price > before)
        assertTrue(l.pressurePpm > 0L)
    }

    @Test fun sellingPushesThePriceDownAndNeverBelowTheFloor() {
        val s = listed()
        val q = (s.quote(a, TradeSide.SELL, 250, 250) as Outcome.Ok).value
        s.recordTrade(q, 250)
        val l = s.listing(a)!!
        assertTrue(l.price < Money.units(10))
        assertTrue(l.price >= s.params.minPrice)
        assertTrue(l.pressurePpm < 0L)
    }

    @Test fun priceDriftsTowardWhatTheBusinessIsWorth() {
        val s = listed(price = 10, params = ExchangeParams(valuationEvery = 1, volatilityPpm = 0))
        val facts = Facts(fair = Money.units(20))
        val rnd = DeterministicRandom(3)
        var last = s.listing(a)!!.price
        repeat(30) { t ->
            s.closeDay(Tick(t.toLong() + 1), facts, rnd)
            val now = s.listing(a)!!.price
            assertTrue(now >= last)
            last = now
        }
        assertTrue(last > Money.units(14))
        assertTrue(last < Money.units(20))
    }

    @Test fun sentimentShiftsTheTarget() {
        val strong = listed(params = ExchangeParams(valuationEvery = 1, volatilityPpm = 0))
        val weak = listed(params = ExchangeParams(valuationEvery = 1, volatilityPpm = 0))
        val rnd = DeterministicRandom(3)
        repeat(60) {
            strong.closeDay(Tick(it + 1L), Facts(sentiment = 1_150_000L), rnd)
            weak.closeDay(Tick(it + 1L), Facts(sentiment = 850_000L), rnd)
        }
        assertTrue(strong.listing(a)!!.price > weak.listing(a)!!.price)
    }

    @Test fun pressureFadesOverTheDays() {
        val s = listed(params = ExchangeParams(valuationEvery = 1, volatilityPpm = 0))
        val facts = Facts()
        val q = (s.quote(a, TradeSide.BUY, 200, 250) as Outcome.Ok).value
        s.recordTrade(q, 250)
        val p0 = s.listing(a)!!.pressurePpm
        s.closeDay(Tick(1), facts, DeterministicRandom(1))
        val p1 = s.listing(a)!!.pressurePpm
        assertTrue(p1 < p0 && p1 > 0L)
        repeat(30) { s.closeDay(Tick(it + 2L), facts, DeterministicRandom(1)) }
        assertTrue(s.listing(a)!!.pressurePpm < 1_000L)
    }

    @Test fun closingTheDayResetsTheDaysCounters() {
        val s = listed()
        s.recordTrade((s.quote(a, TradeSide.BUY, 10, 250) as Outcome.Ok).value, 250)
        assertEquals(10L, s.listing(a)!!.volumeTotal)
        s.closeDay(Tick(1), Facts(), DeterministicRandom(1))
        val l = s.listing(a)!!
        assertEquals(0L, l.volumeToday)
        assertEquals(Money.ZERO, l.turnoverToday)
        assertEquals(10L, l.volumeTotal)
        assertEquals(l.price, l.previousClose)
    }

    @Test fun historyIsCappedAtTheConfiguredLength() {
        val s = listed(params = ExchangeParams(historyLength = 10, valuationEvery = 1))
        repeat(40) { s.closeDay(Tick(it + 1L), Facts(), DeterministicRandom(it.toLong())) }
        assertEquals(10, s.listing(a)!!.history.size)
        assertEquals(10, s.indexHistory().size)
    }

    @Test fun bigMovesMakeHeadlines() {
        val s = listed(params = ExchangeParams(valuationEvery = 1, volatilityPpm = 0, reversionPpm = 1_000_000L))
        val up = s.closeDay(Tick(1), Facts(fair = Money.units(20)), DeterministicRandom(1))
        assertTrue(up.single() is ExchangeEvent.Surge)
        val down = s.closeDay(Tick(2), Facts(fair = Money.units(5)), DeterministicRandom(1))
        assertTrue(down.single() is ExchangeEvent.Slump)
        assertTrue(s.closeDay(Tick(3), Facts(fair = Money.units(5)), DeterministicRandom(1)).isEmpty())
    }

    @Test fun theIndexFollowsTheMarketWeightedByValue() {
        val s = ExchangeService(ExchangeParams(valuationEvery = 1, volatilityPpm = 0, reversionPpm = 1_000_000L))
        s.list(a, Tick(0), Money.units(10)); s.list(b, Tick(0), Money.units(10))
        val facts = object : MarketFacts {
            override fun fairPrice(issuer: CompanyId, tick: Tick) = if (issuer == a) Money.units(20) else Money.units(10)
            override fun publicShares(issuer: CompanyId) = 10L
            override fun outstanding(issuer: CompanyId) = if (issuer == a) 9_000L else 1_000L
            override fun sentimentPpm(issuer: CompanyId) = 1_000_000L
        }
        s.closeDay(Tick(1), facts, DeterministicRandom(1))
        // A (90% of the weight) doubled; B did not move: the index rises about 90%.
        assertEquals(190_000L, s.index)
        assertEquals(900_000L, s.indexChangePpm.let { it })
    }

    @Test fun sameSeedGivesSameDays() {
        fun run(): String {
            val s = listed(params = ExchangeParams(valuationEvery = 1))
            val rnd = DeterministicRandom(77)
            repeat(50) { s.closeDay(Tick(it + 1L), Facts(), rnd) }
            return hash(s)
        }
        assertEquals(run(), run())
    }

    @Test fun theRegisterSurvivesAWriteAndRead() {
        val s = listed(params = ExchangeParams(valuationEvery = 1))
        s.list(b, Tick(2), Money.units(30))
        s.addBasis(investor, a, Money.units(123))
        s.addBasis(PublicInvestors.holder, b, Money.units(900))
        s.recordTrade((s.quote(a, TradeSide.SELL, 30, 250) as Outcome.Ok).value, 250)
        repeat(8) { s.closeDay(Tick(it + 1L), Facts(), DeterministicRandom(it.toLong())) }
        val w = ByteWriter(); ExchangeCodec.write(s, w)
        val back = ExchangeService(ExchangeParams(valuationEvery = 1))
        ExchangeCodec.read(ByteReader(w.toByteArray()), back)
        assertEquals(hash(s), hash(back))
        assertEquals(s.index, back.index)
        assertEquals(Money.units(123), back.basisOf(investor, a))
        assertNotNull(back.listing(b))
    }

    @Test fun negativePressureSurvivesTheCodec() {
        val s = listed()
        s.recordTrade((s.quote(a, TradeSide.SELL, 100, 250) as Outcome.Ok).value, 250)
        assertTrue(s.listing(a)!!.pressurePpm < 0L)
        val w = ByteWriter(); ExchangeCodec.write(s, w)
        val back = ExchangeService(); ExchangeCodec.read(ByteReader(w.toByteArray()), back)
        assertEquals(s.listing(a)!!.pressurePpm, back.listing(a)!!.pressurePpm)
    }

    @Test fun anUnknownVersionIsRefused() {
        val w = ByteWriter(); w.writeVarInt(42)
        val refused = try { ExchangeCodec.read(ByteReader(w.toByteArray()), ExchangeService()); false } catch (e: ByteReader.CorruptDataException) { true }
        assertTrue(refused)
    }

    @Test fun highAndLowCoverTheRecentCloses() {
        val s = listed(params = ExchangeParams(valuationEvery = 1))
        repeat(20) { s.closeDay(Tick(it + 1L), Facts(), DeterministicRandom(it * 7L)) }
        val l = s.listing(a)!!
        assertTrue(l.high >= l.price && l.low <= l.price)
        assertTrue(l.high >= l.low)
        assertNull(s.listing(b))
    }
}
