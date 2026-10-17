package app.many.economytycoon.ui.present

import app.many.economytycoon.net.ChatKind
import app.many.economytycoon.net.ChatLine
import app.many.economytycoon.net.NetLimits
import app.many.economytycoon.net.PlayerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetChatPresenterTest {
    private val names = { p: PlayerId -> if (p.value == 1L) "Ada" else "Bob" }
    private val me = PlayerId(1)

    @Test fun thereIsOneLabelForEveryEmote() {
        assertEquals(NetLimits.MAX_EMOTE + 1, NetChatPresenter.emotes.size)
        assertEquals(NetChatPresenter.emotes.size, NetChatPresenter.emotes.toSet().size)
        assertEquals("...", NetChatPresenter.emote(99))
    }

    @Test fun textLinesNameTheSenderAndMarkYourOwn() {
        val mine = NetChatPresenter.row(ChatLine(5, PlayerId(1), ChatKind.TEXT, 0, "hello"), me, names)
        assertEquals("Ada", mine.who); assertEquals("hello", mine.text); assertEquals(Tone.INFO, mine.tone)
        assertEquals(Tone.NEUTRAL, NetChatPresenter.row(ChatLine(5, PlayerId(2), ChatKind.TEXT, 0, "hi"), me, names).tone)
    }

    @Test fun emotesShowTheirLabel() {
        val r = NetChatPresenter.row(ChatLine(5, PlayerId(2), ChatKind.EMOTE, 2, ""), me, names)
        assertEquals("[Well played]", r.text)
        assertEquals(Tone.MUTED, r.tone)
    }

    @Test fun hostNoticesAreAttributedToTheHost() {
        val r = NetChatPresenter.row(ChatLine(5, PlayerId.SERVER, ChatKind.SYSTEM, 0, "Warning"), me, names)
        assertEquals("Host", r.who)
        assertEquals(Tone.INFO, r.tone)
    }

    @Test fun onlyTheLatestLinesAreShown() {
        val lines = (1..80).map { ChatLine(it.toLong(), PlayerId(2), ChatKind.TEXT, 0, "line $it") }
        val rows = NetChatPresenter.build(lines, me, names, shown = 30)
        assertEquals(30, rows.size)
        assertEquals("line 80", rows.last().text)
        assertEquals("line 51", rows.first().text)
    }

    @Test fun draftsAreCheckedBeforeSending() {
        assertEquals("Write something first.", NetChatPresenter.problem("   "))
        assertNull(NetChatPresenter.problem("fine"))
        assertTrue(NetChatPresenter.problem("x".repeat(NetLimits.MAX_CHAT_CHARS + 1))?.contains("200") == true)
        assertNull(NetChatPresenter.problem("x".repeat(NetLimits.MAX_CHAT_CHARS)))
    }
}
