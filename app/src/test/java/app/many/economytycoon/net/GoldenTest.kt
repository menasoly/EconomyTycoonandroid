package app.many.economytycoon.net

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Values that must never change without a protocol version bump: they are what two builds of the game compare when
 * they talk to each other, so a change here means older clients and hosts no longer understand one another.
 */
class GoldenTest {
    private val key = Signing.deriveKey(NetFixtures.bytes(32, 3), NetFixtures.session, PlayerId(1))

    @Test fun theChainOfAnEmptyFirstCommit() {
        assertEquals("60daa3a5f7dbfa20", java.lang.Long.toHexString(CommitChain.next(0L, 0L, emptyList(), emptyList())))
    }

    @Test fun theChainCoversEntriesAndTransfers() {
        val entries = listOf(CommitEntry(PlayerId(1), 1, byteArrayOf(1, 2, 3)))
        val transfers = listOf(Transfer(TradeId(4), 11, 0, TradeAsset.cash(5_000_000)))
        assertEquals("60f9d28d32651949", java.lang.Long.toHexString(CommitChain.next(0L, 5L, entries, transfers)))
    }

    @Test fun theDerivedPlayerKey() {
        assertEquals("14b66399bbc65a7021519963757cdf502a05ccce3d736241406c6bc6d70e5fd8", Hex.encode(key))
    }

    @Test fun theCommandSignature() {
        assertEquals("db4e833866836dcc0b7ddf7cdc11549b", Hex.encode(Signing.commandMac(key, NetFixtures.session, PlayerId(1), 7, 44, byteArrayOf(9, 9))))
    }

    @Test fun theResumeProof() {
        assertEquals("7e337adab1cd5b5f757f8ff8d503bb6c", Hex.encode(Signing.resumeProof(key, NetFixtures.session, PlayerId(1), 3, 120)))
    }

    @Test fun twoSmallFramesOnTheWire() {
        assertEquals("e77c02281208c601d00f00000000a359d50b", Hex.encode(MessageCodec.encode(9, NetMessage.Ping(99, 1000))))
        assertEquals("e77c020b04040a5800000000ead3d793", Hex.encode(MessageCodec.encode(2, NetMessage.Ack(5, 44))))
    }

    @Test fun theToyGameHashAfterTenTicks() {
        val sim = ToySimulation().also { it.seat(PlayerId(1), "Alpha Works"); repeat(10) { _ -> it.advance() } }
        assertEquals("33dec50beff08d195ac474e6e7929ac14d4c69e1c4a0e6a0a80a310406a888b9", Hex.encode(sim.stateHash()))
    }

    @Test fun theExpectedScoreTable() {
        assertEquals(500_000, Rating.expectedPpm(1500, 1500))
        assertEquals(759_747, Rating.expectedPpm(1700, 1500))
        assertEquals(90_909, Rating.expectedPpm(1500, 1900))
    }
}
