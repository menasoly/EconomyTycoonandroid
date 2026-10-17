package app.many.economytycoon.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatModeratorTest {
    private val p = PlayerId(1); private val q = PlayerId(2)
    private fun text(s: String) = NetMessage.Chat(ChatKind.TEXT, 0, s)
    private fun emote(i: Int) = NetMessage.Chat(ChatKind.EMOTE, i, "")
    private fun allowed(v: ChatVerdict) = v as ChatVerdict.Allowed
    private fun blocked(v: ChatVerdict) = v as ChatVerdict.Blocked

    @Test fun plainMessagePasses() {
        val m = ChatModerator()
        assertEquals("hello", allowed(m.check(p, text("hello"), 0)).text)
    }

    @Test fun whitespaceAndInvisibleCharactersAreCleaned() {
        val m = ChatModerator()
        assertEquals("a b c", allowed(m.check(p, text("  a ​  b\t c  "), 0)).text)
    }

    @Test fun emptyMessagesAreDroppedWithoutPenalty() {
        val m = ChatModerator()
        val v = blocked(m.check(p, text("  ​ "), 0))
        assertEquals(NetError.INVALID_COMMAND, v.error)
        assertFalse(v.strike)
    }

    @Test fun fastTypistsAreRateLimited() {
        val m = ChatModerator(ChatConfig(burst = 2, perSecond = 1))
        assertTrue(m.check(p, text("one"), 0) is ChatVerdict.Allowed)
        assertTrue(m.check(p, text("two"), 0) is ChatVerdict.Allowed)
        val v = blocked(m.check(p, text("three"), 0))
        assertEquals(NetError.RATE_LIMITED, v.error)
        assertTrue(v.strike)
        assertTrue(m.check(p, text("four"), 2_000) is ChatVerdict.Allowed)
    }

    @Test fun repeatedTextIsSpamUntilTheWindowPasses() {
        val m = ChatModerator(ChatConfig(repeatWindowMs = 5_000))
        m.check(p, text("buy my stuff"), 0)
        assertEquals("repeated message", blocked(m.check(p, text("BUY my stuff"), 3_000)).reason)
        assertTrue(m.check(p, text("buy my stuff"), 9_000) is ChatVerdict.Allowed)
    }

    @Test fun linksAreBlockedUnlessAllowed() {
        for (s in listOf("see https://x.y", "go to www.spam.biz", "visit cheap.com now")) {
            assertEquals(s, "links are not allowed", blocked(ModeratorFor().check(p, text(s), 0)).reason)
        }
        assertTrue(ChatModerator(ChatConfig(allowLinks = true)).check(p, text("https://ok.example"), 0) is ChatVerdict.Allowed)
    }

    private fun ModeratorFor() = ChatModerator()

    @Test fun maskedTermsAreReplaced() {
        val m = ChatModerator(ChatConfig(maskedTerms = setOf("darn", "Heck")))
        assertEquals("oh **** and ****!", allowed(m.check(p, text("oh DARN and heck!"), 0)).text)
    }

    @Test fun maskingHandlesRegexCharactersInTerms() {
        val m = ChatModerator(ChatConfig(maskedTerms = setOf("a.b(")))
        assertEquals("x **** y", allowed(m.check(p, text("x a.b( y"), 0)).text)
        assertEquals("axb( stays", allowed(m.check(q, text("axb( stays"), 0)).text)
    }

    @Test fun shoutingIsSoftened() {
        val m = ChatModerator()
        assertEquals("Everyone buy wheat now", allowed(m.check(p, text("EVERYONE BUY WHEAT NOW"), 0)).text)
        assertEquals("OK GO", allowed(m.check(q, text("OK GO"), 0)).text)
    }

    @Test fun threeStrikesEarnAMuteThatGrowsWithRepeats() {
        val m = ChatModerator(ChatConfig(burst = 1, perSecond = 0, strikesToMute = 3, muteMs = 1_000))
        m.check(p, text("ok"), 0)
        repeat(3) { m.check(p, text("spam $it"), 10) }
        assertTrue(m.isMuted(p, 500))
        assertEquals(NetError.MUTED, blocked(m.check(p, text("hello"), 500)).error)
        assertFalse(blocked(m.check(p, text("hello"), 500)).strike)
        assertFalse(m.isMuted(p, 1_010))
        assertNotNull(m.mutedUntil(p))
    }

    @Test fun strikesExpireOutsideTheWindow() {
        val m = ChatModerator(ChatConfig(burst = 1, perSecond = 0, strikesToMute = 3, strikeWindowMs = 100))
        m.check(p, text("ok"), 0)
        m.check(p, text("a"), 0); m.check(p, text("b"), 1_000); m.check(p, text("c"), 2_000)
        assertFalse(m.isMuted(p, 2_000))
    }

    @Test fun hostMuteAndUnmute() {
        val m = ChatModerator()
        m.mute(p, 10_000)
        assertTrue(m.isMuted(p, 9_999))
        assertEquals(NetError.MUTED, blocked(m.check(p, text("hi"), 5)).error)
        m.unmute(p)
        assertFalse(m.isMuted(p, 5))
        assertNull(m.mutedUntil(p))
        assertTrue(m.check(p, text("hi"), 5) is ChatVerdict.Allowed)
    }

    @Test fun mutesArePerPlayer() {
        val m = ChatModerator()
        m.mute(p, 10_000)
        assertTrue(m.check(q, text("hi"), 5) is ChatVerdict.Allowed)
    }

    @Test fun emotesPassWithinTheirOwnLimit() {
        val m = ChatModerator(ChatConfig(emoteBurst = 2, emotePerSecond = 0))
        assertEquals(3, allowed(m.check(p, emote(3), 0)).emote)
        assertTrue(m.check(p, emote(4), 0) is ChatVerdict.Allowed)
        assertEquals(NetError.RATE_LIMITED, blocked(m.check(p, emote(5), 0)).error)
        assertTrue(m.check(p, text("text is separate"), 0) is ChatVerdict.Allowed)
    }

    @Test fun malformedEmotesAreRefused() {
        val m = ChatModerator()
        assertEquals("bad emote", blocked(m.check(p, NetMessage.Chat(ChatKind.EMOTE, 99, ""), 0)).reason)
        assertEquals("bad emote", blocked(m.check(p, NetMessage.Chat(ChatKind.EMOTE, 1, "text"), 0)).reason)
    }

    @Test fun clientsCannotSendSystemMessages() {
        val m = ChatModerator()
        val v = blocked(m.check(p, NetMessage.Chat(ChatKind.SYSTEM, 0, "server says"), 0))
        assertTrue(v.strike)
    }

    @Test fun historyKeepsTheNewestLines() {
        val h = ChatHistory(3)
        for (i in 1..5) h.add(ChatLine(i.toLong(), p, ChatKind.TEXT, 0, "m$i"))
        assertEquals(listOf("m3", "m4", "m5"), h.recent().map { it.text })
        assertEquals(listOf("m5"), h.recent(1).map { it.text })
        assertEquals(3, h.size)
    }
}
