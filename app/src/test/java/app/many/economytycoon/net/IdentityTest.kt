package app.many.economytycoon.net

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityTest {
    private fun book(seed: Long = 1) = IdentityBook(NetFixtures.session, SeededTokenSource(seed))

    @Test fun seededSourceIsReproducibleAndVaries() {
        val a = SeededTokenSource(5); val b = SeededTokenSource(5)
        assertArrayEquals(a.next(32), b.next(32))
        assertFalse(a.next(32).contentEquals(a.next(32)))
        assertEquals(13, SeededTokenSource(1).next(13).size)
    }

    @Test fun playersGetSequentialIds() {
        val b = book()
        assertEquals(listOf(1L, 2L, 3L), List(3) { b.enroll("P$it").first.id.value })
        assertEquals(3, b.size)
    }

    @Test fun differentTokensGiveDifferentKeys() {
        val b = book()
        val (r1, t1) = b.enroll("A"); val (r2, t2) = b.enroll("B")
        assertFalse(t1.contentEquals(t2))
        assertFalse(r1.key.contentEquals(r2.key))
        assertArrayEquals(r1.key, Signing.deriveKey(t1, NetFixtures.session, r1.id))
    }

    @Test fun keyDependsOnSessionAndPlayer() {
        val token = ByteArray(32) { 3 }
        val k = Signing.deriveKey(token, SessionId(1), PlayerId(1))
        assertFalse(k.contentEquals(Signing.deriveKey(token, SessionId(2), PlayerId(1))))
        assertFalse(k.contentEquals(Signing.deriveKey(token, SessionId(1), PlayerId(2))))
    }

    @Test fun commandMacBindsEveryField() {
        val key = ByteArray(32) { 9 }
        val s = SessionId(1); val p = PlayerId(2); val payload = byteArrayOf(1, 2, 3)
        val base = Signing.commandMac(key, s, p, 5, 10, payload)
        assertEquals(16, base.size)
        assertArrayEquals(base, Signing.commandMac(key, s, p, 5, 10, payload))
        assertFalse(base.contentEquals(Signing.commandMac(key, SessionId(9), p, 5, 10, payload)))
        assertFalse(base.contentEquals(Signing.commandMac(key, s, PlayerId(3), 5, 10, payload)))
        assertFalse(base.contentEquals(Signing.commandMac(key, s, p, 6, 10, payload)))
        assertFalse(base.contentEquals(Signing.commandMac(key, s, p, 5, 11, payload)))
        assertFalse(base.contentEquals(Signing.commandMac(key, s, p, 5, 10, byteArrayOf(1, 2, 4))))
    }

    @Test fun resumeRequiresTheSecretAndAFreshNonce() {
        val b = book()
        val (r, _) = b.enroll("A")
        val proof = Signing.resumeProof(r.key, NetFixtures.session, r.id, 5, 100)
        assertTrue(b.acceptResume(r.id, 5, 100, proof))
        assertFalse("replayed", b.acceptResume(r.id, 5, 100, proof))
        assertFalse("older nonce", b.acceptResume(r.id, 4, 100, Signing.resumeProof(r.key, NetFixtures.session, r.id, 4, 100)))
        assertTrue(b.acceptResume(r.id, 6, 100, Signing.resumeProof(r.key, NetFixtures.session, r.id, 6, 100)))
    }

    @Test fun resumeWithWrongProofOrUnknownPlayerFails() {
        val b = book()
        val (r, _) = b.enroll("A")
        assertFalse(b.acceptResume(r.id, 1, 0, ByteArray(16)))
        assertFalse(b.acceptResume(PlayerId(99), 1, 0, Signing.resumeProof(r.key, NetFixtures.session, PlayerId(99), 1, 0)))
        val tampered = Signing.resumeProof(r.key, NetFixtures.session, r.id, 1, 5)
        assertFalse("lastTick is part of the proof", b.acceptResume(r.id, 1, 6, tampered))
    }

    @Test fun bannedPlayersCannotResume() {
        val b = book()
        val (r, _) = b.enroll("A")
        b.ban(r.id)
        assertTrue(b.isBanned(r.id))
        assertFalse(b.acceptResume(r.id, 1, 0, Signing.resumeProof(r.key, NetFixtures.session, r.id, 1, 0)))
        assertEquals(listOf(r.id), b.bannedIds())
    }

    @Test fun bookRoundTripsThroughItsCodec() {
        val b = book(3)
        val (r, _) = b.enroll("A"); b.enroll("B")
        b.ban(PlayerId(2))
        b.acceptResume(r.id, 4, 1, Signing.resumeProof(r.key, NetFixtures.session, r.id, 4, 1))
        val w = ByteWriter(); b.write(w)
        val copy = book(99); copy.read(ByteReader(w.toByteArray()))
        assertEquals(2, copy.size)
        assertArrayEquals(r.key, copy.get(r.id)?.key)
        assertEquals(4L, copy.get(r.id)?.lastResumeNonce)
        assertTrue(copy.isBanned(PlayerId(2)))
        assertEquals(3L, copy.enroll("C").first.id.value)
        val w2 = ByteWriter(); b.write(w2)
        assertArrayEquals(w.toByteArray(), w2.toByteArray())
    }

    @Test fun corruptBookIsRefused() {
        val w = ByteWriter(); w.writeVarInt(9)
        try { book().read(ByteReader(w.toByteArray())); org.junit.Assert.fail() } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message.orEmpty().contains("version")) }
        val w2 = ByteWriter(); w2.writeVarInt(1); w2.writeVarLong(1); w2.writeVarInt(1_000_000)
        try { book().read(ByteReader(w2.toByteArray())); org.junit.Assert.fail() } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message.orEmpty().contains("implausible")) }
    }

    @Test fun renameChangesTheStoredName() {
        val b = book()
        val (r, _) = b.enroll("Old")
        b.rename(r.id, "New")
        assertEquals("New", b.get(r.id)?.name)
        assertNull(b.get(PlayerId(50)))
        assertNotEquals(0, b.all().size)
    }
}
