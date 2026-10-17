package app.many.economytycoon.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandshakeTest {
    private val offer = ServerOffer(NetFixtures.session, "base:1.0")
    private fun hello(
        protocol: Int = 1, min: Int = 1, caps: Long = Capabilities.ALL, session: SessionId = NetFixtures.session, content: String = "base:1.0",
    ) = NetMessage.Hello(protocol, min, caps, "Ada", session, content, false, null)

    @Test fun matchingPeersAgreeOnEverythingTheyShare() {
        val r = Handshake.negotiate(hello(caps = Capabilities.CHAT or Capabilities.TRADE), offer) as Negotiation.Agreed
        assertEquals(1, r.protocol)
        assertEquals(Capabilities.CHAT or Capabilities.TRADE, r.capabilities)
    }

    @Test fun unknownCapabilityBitsAreIgnored() {
        val r = Handshake.negotiate(hello(caps = Capabilities.CHAT or (1L shl 40)), offer) as Negotiation.Agreed
        assertEquals(Capabilities.CHAT, r.capabilities)
    }

    @Test fun wrongSessionIsRefused() {
        val r = Handshake.negotiate(hello(session = SessionId(1)), offer) as Negotiation.Refused
        assertEquals(NetError.SESSION_UNKNOWN, r.error)
    }

    @Test fun versionRangesMustOverlap() {
        val newer = offer.copy(protocol = 3, minProtocol = 2)
        assertEquals(NetError.UNSUPPORTED_VERSION, (Handshake.negotiate(hello(protocol = 1, min = 1), newer) as Negotiation.Refused).error)
        val agreed = Handshake.negotiate(hello(protocol = 2, min = 1), newer) as Negotiation.Agreed
        assertEquals(2, agreed.protocol)
    }

    @Test fun highestCommonVersionIsChosen() {
        val server = offer.copy(protocol = 4, minProtocol = 1)
        assertEquals(3, (Handshake.negotiate(hello(protocol = 3, min = 2), server) as Negotiation.Agreed).protocol)
    }

    @Test fun invertedClientRangeIsRefused() {
        assertTrue(Handshake.negotiate(hello(protocol = 1, min = 3), offer) is Negotiation.Refused)
    }

    @Test fun contentMustMatch() {
        val r = Handshake.negotiate(hello(content = "other:2.0"), offer) as Negotiation.Refused
        assertEquals(NetError.CONTENT_MISMATCH, r.error)
    }

    @Test fun requiredCapabilitiesAreEnforced() {
        val strict = offer.copy(required = Capabilities.EVERY_TICK_HASH)
        val r = Handshake.negotiate(hello(caps = Capabilities.CHAT), strict) as Negotiation.Refused
        assertEquals(NetError.MISSING_CAPABILITY, r.error)
        assertTrue(r.detail.contains("every-tick-hash"))
        assertTrue(Handshake.negotiate(hello(caps = Capabilities.ALL), strict) is Negotiation.Agreed)
    }

    @Test fun namesAreCleanedOrRefused() {
        assertEquals("Ada Lovelace", NameRules.clean("  Ada   Lovelace "))
        assertNull(NameRules.clean("a"))
        assertNull(NameRules.clean("x".repeat(25)))
        assertEquals("x".repeat(24), NameRules.clean("x".repeat(24)))
        assertNull(NameRules.clean("bad\u0007name"))
        assertNull(NameRules.clean("!!!"))
        assertNull(NameRules.clean("evil‮name"))
        assertNull(NameRules.clean("      "))
    }
}
