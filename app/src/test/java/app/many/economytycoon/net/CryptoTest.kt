package app.many.economytycoon.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoTest {
    private fun hex(b: ByteArray) = Hex.encode(b)
    private fun bytes(s: String) = s.toByteArray(Charsets.UTF_8)

    @Test fun sha256KnownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex(Hmac.sha256(ByteArray(0))))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex(Hmac.sha256(bytes("abc"))))
    }

    @Test fun hmacRfc4231CaseOne() {
        val key = ByteArray(20) { 0x0b }
        assertEquals("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7", hex(Hmac.sign(key, bytes("Hi There"))))
    }

    @Test fun hmacRfc4231CaseTwo() {
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843", hex(Hmac.sign(bytes("Jefe"), bytes("what do ya want for nothing?"))))
    }

    @Test fun hmacRfc4231CaseThree() {
        val key = ByteArray(20) { 0xaa.toByte() }
        val data = ByteArray(50) { 0xdd.toByte() }
        assertEquals("773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe", hex(Hmac.sign(key, data)))
    }

    @Test fun hmacLongKeyIsHashedFirst() {
        val key = ByteArray(131) { 0xaa.toByte() }
        val data = bytes("Test Using Larger Than Block-Size Key - Hash Key First")
        assertEquals("60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54", hex(Hmac.sign(key, data)))
    }

    @Test fun tagIsAPrefixOfTheSignature() {
        val sig = Hmac.sign(bytes("k"), bytes("d"))
        assertArrayEquals(sig.copyOf(16), Hmac.tag(bytes("k"), bytes("d")))
    }

    @Test fun verifyAcceptsRightAndRefusesWrong() {
        val key = bytes("secret"); val data = bytes("payload")
        val tag = Hmac.tag(key, data)
        assertTrue(Hmac.verify(key, data, tag))
        assertFalse(Hmac.verify(key, bytes("payloaD"), tag))
        assertFalse(Hmac.verify(bytes("secreT"), data, tag))
        assertFalse(Hmac.verify(key, data, ByteArray(0)))
        val flipped = tag.copyOf(); flipped[3] = (flipped[3].toInt() xor 1).toByte()
        assertFalse(Hmac.verify(key, data, flipped))
        assertFalse(Hmac.verify(key, data, ByteArray(40)))
    }

    @Test fun constantTimeEqualsComparesLengthAndContent() {
        assertTrue(Hmac.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 2)))
        assertFalse(Hmac.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 3)))
        assertFalse(Hmac.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1)))
    }

    @Test fun hexRoundTripsAndRefusesBadInput() {
        val data = ByteArray(256) { it.toByte() }
        assertArrayEquals(data, Hex.decode(Hex.encode(data)))
        assertEquals("00ff10", Hex.encode(byteArrayOf(0, -1, 16)))
        assertNull(Hex.decode("abc"))
        assertNull(Hex.decode("zz"))
        assertArrayEquals(byteArrayOf(-1), Hex.decode("FF"))
    }
}
