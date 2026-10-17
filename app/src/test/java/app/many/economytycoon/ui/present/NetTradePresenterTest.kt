package app.many.economytycoon.ui.present

import app.many.economytycoon.net.NetMessage
import app.many.economytycoon.net.PlayerId
import app.many.economytycoon.net.TradeAsset
import app.many.economytycoon.net.TradeId
import app.many.economytycoon.net.TradeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetTradePresenterTest {
    private val names = { p: PlayerId -> if (p.value == 1L) "Ada" else "Bob" }
    private fun notice(id: Long, state: TradeState, from: Long = 1, to: Long = 2) =
        NetMessage.TradeNotice(TradeId(id), state, PlayerId(from), PlayerId(to), listOf(TradeAsset.cash(5_000_000)), listOf(TradeAsset.goods("wheat", 2_500)), 90)

    @Test fun assetsReadNaturally() {
        assertEquals("nothing", NetTradePresenter.assets(emptyList()))
        assertEquals("2.5 wheat", NetTradePresenter.asset(TradeAsset.goods("wheat", 2_500)))
        assertTrue(NetTradePresenter.asset(TradeAsset.cash(5_000_000)).contains("5"))
    }

    @Test fun anOfferYouMadeCanBeWithdrawnNotAnswered() {
        val r = NetTradePresenter.row(notice(1, TradeState.OFFERED), PlayerId(1), names)
        assertEquals("Offer to Bob", r.headline)
        assertTrue(r.canWithdraw); assertFalse(r.canAnswer)
        assertTrue(r.gives.contains("5")); assertEquals("2.5 wheat", r.wants)
    }

    @Test fun anOfferToYouCanBeAnsweredAndTheSidesAreFlipped() {
        val r = NetTradePresenter.row(notice(1, TradeState.OFFERED), PlayerId(2), names)
        assertEquals("Offer from Ada", r.headline)
        assertTrue(r.canAnswer); assertFalse(r.canWithdraw)
        assertEquals("2.5 wheat", r.gives)
        assertTrue(r.wants.contains("5"))
    }

    @Test fun settledAndClosedOffersCannotBeActedOn() {
        for (s in TradeState.values().filter { it != TradeState.OFFERED }) {
            val r = NetTradePresenter.row(notice(1, s), PlayerId(2), names)
            assertFalse(s.name, r.canAnswer || r.canWithdraw)
        }
    }

    @Test fun everyStateHasItsOwnWords() {
        assertEquals(TradeState.values().size, TradeState.values().map { NetTradePresenter.status(it).first }.toSet().size)
        assertEquals(Tone.GOOD, NetTradePresenter.status(TradeState.SETTLED).second)
        assertEquals(Tone.BAD, NetTradePresenter.status(TradeState.FAILED).second)
    }

    @Test fun openOffersComeFirstThenNewest() {
        val m = NetTradePresenter.build(listOf(notice(1, TradeState.SETTLED), notice(2, TradeState.OFFERED), notice(3, TradeState.SETTLED)), PlayerId(1), names)
        assertEquals(listOf(2L, 3L, 1L), m.rows.map { it.id })
    }

    @Test fun noOffersShowsTheExplanation() {
        assertEquals(NetTradePresenter.EMPTY, NetTradePresenter.build(emptyList(), PlayerId(1), names).empty)
    }
}
