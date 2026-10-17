package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals as eq
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbonCodecTest {
    private fun busy(seed: Long = 7L, days: Long = 470): CarbonTestSupport.Fixture =
        CarbonTestSupport.Fixture(seed).also { CarbonTestSupport.busy(it, days) }

    private fun restored(bytes: ByteArray): CarbonState = CarbonState().also { CarbonCodec.decode(bytes, it) }

    @Test fun aFreshStateRoundTrips() {
        val a = CarbonState()
        val b = restored(CarbonCodec.encode(a))
        assertEquals(a.stateHash(), b.stateHash())
        assertEquals(-1L, b.lastTick)
    }

    @Test fun aBusyStateRoundTripsToTheSameBytes() {
        val fx = busy()
        val bytes = CarbonCodec.encode(fx.state)
        assertTrue(bytes.size > 2_000)
        assertArrayEquals(bytes, CarbonCodec.encode(restored(bytes)))
    }

    @Test fun theRestoredStateHashesLikeTheOriginal() {
        val fx = busy()
        assertEquals(fx.state.stateHash(), restored(CarbonCodec.encode(fx.state)).stateHash())
    }

    @Test fun theRestoredStateReadsLikeTheOriginal() {
        val fx = busy()
        val copy = restored(CarbonCodec.encode(fx.state))
        assertEquals(fx.state.market(uk)!!.holdings.total(acme), copy.market(uk)!!.holdings.total(acme))
        assertEquals(fx.state.compliance.accounts().size, copy.compliance.accounts().size)
        assertEquals(fx.state.compliance.account(acme, uk)!!.history(), copy.compliance.account(acme, uk)!!.history())
        assertEquals(fx.state.reports.statements(), copy.reports.statements())
        assertEquals(fx.state.reports.ratings(), copy.reports.ratings())
        assertEquals(fx.state.market(uk)!!.auctions(), copy.market(uk)!!.auctions())
        assertEquals(fx.state.market(uk)!!.trades(), copy.market(uk)!!.trades())
        assertEquals(fx.state.abatement.projects().map { it.id to it.status }, copy.abatement.projects().map { it.id to it.status })
        assertEquals(fx.state.offsets.projects().map { it.id to it.issued }, copy.offsets.projects().map { it.id to it.issued })
        assertEquals(fx.state.inventory.facility(app.many.economytycoon.core.FacilityId(1))!!.effects, copy.inventory.facility(app.many.economytycoon.core.FacilityId(1))!!.effects)
        assertEquals(fx.state.border.policy(uk), copy.border.policy(uk))
        assertEquals(fx.state.gridIntensity(uk), copy.gridIntensity(uk))
        assertEquals(fx.state.receipts(uk), copy.receipts(uk))
        assertEquals(fx.state.greenFund(uk), copy.greenFund(uk))
        assertEquals(fx.state.lastTick, copy.lastTick)
        assertEquals(fx.state.regime(uk), copy.regime(uk))
    }

    @Test fun theBusyScenarioActuallyExercisesTheFormat() {
        val fx = busy()
        val s = fx.state
        assertTrue(s.market(uk)!!.auctions().isNotEmpty())
        assertTrue(s.compliance.accounts().any { it.pending != null || it.history().isNotEmpty() })
        assertTrue(s.offsets.projects().isNotEmpty())
        assertTrue(s.abatement.projects().isNotEmpty())
        assertTrue(s.reports.statements().isNotEmpty())
        assertTrue(s.inventory.facilities().isNotEmpty())
        assertTrue(s.counters.peek(CarbonIdKind.BID) > 1L)
    }

    @Test fun theHashChangesWhenAnythingMoves() {
        val fx = busy(days = 100)
        val before = fx.state.stateHash()
        fx.step(101, emptyList())
        assertNotEquals(before, fx.state.stateHash())
    }

    @Test fun theHashChangesWithASingleCommand() {
        val fx = busy(days = 100)
        val before = fx.state.stateHash()
        fx.exec(CarbonCommand.SetPolicy(acme, uk, autoOffsets = false, autoBorrow = true), 101)
        assertNotEquals(before, fx.state.stateHash())
    }

    @Test fun theVersionByteComesFirst() {
        val bytes = CarbonCodec.encode(CarbonState())
        assertEquals(CarbonCodec.VERSION, ByteReader(bytes).readVarInt())
    }

    @Test(expected = ByteReader.CorruptDataException::class) fun anUnknownVersionIsRefused() {
        val bytes = CarbonCodec.encode(CarbonState())
        bytes[0] = 198.toByte()
        CarbonCodec.decode(bytes, CarbonState())
    }

    @Test fun aTruncatedSaveIsRefusedRatherThanHalfLoaded() {
        val bytes = CarbonCodec.encode(busy(days = 60).state)
        val cut = bytes.copyOf(bytes.size / 2)
        val result = runCatching { CarbonCodec.decode(cut, CarbonState()) }
        assertTrue(result.isFailure)
    }

    @Test fun loadingReplacesWhateverWasInTheTargetState() {
        val target = busy(seed = 3L, days = 200).state
        val source = busy(seed = 9L, days = 60).state
        CarbonCodec.decode(CarbonCodec.encode(source), target)
        assertEquals(source.stateHash(), target.stateHash())
    }

    @Test fun theSameHistoryGivesTheSameBytesAcrossRuns() {
        assertArrayEquals(CarbonCodec.encode(busy().state), CarbonCodec.encode(busy().state))
    }

    @Test fun aSavedGameResumesExactlyWhereItStopped() {
        val plants = listOf(CarbonTestSupport.emitter(1, acme), CarbonTestSupport.emitter(2, CarbonTestSupport.borea))
        val a = CarbonTestSupport.Fixture(seed = 5L)
        a.service.installRegime(CarbonTestSupport.capAndTrade(), Tick(0)).value()
        a.run(0, 200, plants)
        a.exec(CarbonCommand.FundOffsetProject(acme, OffsetKind.METHANE_CAPTURE, uk, Tonnes.of(500)), 200).value()
        val bytes = CarbonCodec.encode(a.state)
        val randoms = a.service.random.snapshot()
        a.run(201, 900, plants)

        val b = CarbonTestSupport.Fixture(seed = 5L)
        CarbonCodec.decode(bytes, b.state)
        b.service.random.restore(randoms)
        b.run(201, 900, plants)
        assertEquals(a.state.stateHash(), b.state.stateHash())
    }

    private fun <T> app.many.economytycoon.core.Outcome<T>.value(): T = (this as app.many.economytycoon.core.Outcome.Ok).value
}
