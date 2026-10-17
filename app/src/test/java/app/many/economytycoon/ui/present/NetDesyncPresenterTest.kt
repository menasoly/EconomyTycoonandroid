package app.many.economytycoon.ui.present

import app.many.economytycoon.net.ClientView
import app.many.economytycoon.net.ClientState
import app.many.economytycoon.net.DesyncAnalysis
import app.many.economytycoon.net.DesyncCause
import app.many.economytycoon.net.DesyncReport
import app.many.economytycoon.net.PlayerId
import app.many.economytycoon.net.Quality
import app.many.economytycoon.net.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetDesyncPresenterTest {
    private val names = { p: PlayerId -> mapOf(1L to "Ada", 2L to "Bob", 3L to "Cy")[p.value] ?: "Player ${p.value}" }
    private fun h(n: Int) = ByteArray(32) { (it + n).toByte() }
    private fun report(tick: Long, server: Int, vararg clients: Pair<Long, Int>) = DesyncAnalysis.analyse(tick, h(server), clients.associate { PlayerId(it.first) to h(it.second) })
    private fun view(desyncs: Int, resyncs: Int) = ClientView(ClientState.RUNNING, PlayerId(1), Role.PLAYER, 5, 5, 0, null, Quality.GOOD, 10, 0, 0, desyncs, resyncs, 0, 0, 0)

    @Test fun anEmptyHistoryIsReassuring() {
        val m = NetDesyncPresenter.build(emptyList(), names)
        assertEquals(Tone.GOOD, m.tiles[0].tone)
        assertEquals(listOf("0", "-", "-"), m.tiles.map { it.value })
        assertEquals(NetDesyncPresenter.EMPTY, m.empty)
    }

    @Test fun aSingleDivergentPlayerIsNamed() {
        val r = report(120, 1, 1L to 1, 2L to 9, 3L to 1)
        val row = NetDesyncPresenter.row(r, names)
        assertEquals("Tick 120", row.tick)
        assertEquals("A player's copy diverged", row.headline)
        assertTrue(row.detail.startsWith("Bob did not match the host"))
        assertEquals(Tone.WARN, row.tone)
        assertEquals(2, row.groups.size)
        assertTrue(row.groups[0].startsWith("Ada and Cy: "))
    }

    @Test fun twoCulpritsAreJoinedInPlainWords() {
        val r = report(50, 1, 1L to 1, 2L to 9, 3L to 9)
        assertTrue(NetDesyncPresenter.row(r, names).detail.startsWith("Bob and Cy did not match"))
    }

    @Test fun whenEveryoneAgreesAgainstTheHostTheHostIsBlamed() {
        val r = report(50, 1, 1L to 7, 2L to 7)
        assertEquals(DesyncCause.SERVER, r.cause)
        val row = NetDesyncPresenter.row(r, names)
        assertEquals("The host's copy diverged", row.headline)
        assertEquals(Tone.BAD, row.tone)
    }

    @Test fun threeDifferentAnswersCannotBeAttributed() {
        val r = report(50, 1, 1L to 5, 2L to 6, 3L to 7)
        assertEquals("Several copies diverged", NetDesyncPresenter.row(r, names).headline)
    }

    @Test fun everyCauseHasAHeadlineAndTone() {
        assertEquals(DesyncCause.values().size, DesyncCause.values().map { NetDesyncPresenter.headline(it) }.toSet().size)
        assertEquals(Tone.NEUTRAL, NetDesyncPresenter.tone(DesyncCause.NONE))
    }

    @Test fun theNewestMismatchComesFirstAndTilesCountBlame() {
        val reports = listOf(report(10, 1, 1L to 1, 2L to 9), report(30, 1, 1L to 1, 2L to 9), report(20, 1, 1L to 9, 2L to 1))
        val m = NetDesyncPresenter.build(reports, names)
        assertEquals(listOf("Tick 30", "Tick 20", "Tick 10"), m.rows.map { it.tick })
        assertEquals("3", m.tiles[0].value)
        assertEquals("Bob", m.tiles[2].value)
        assertEquals("2 times", m.tiles[2].note)
    }

    @Test fun onlyTheLatestTwentyAreShown() {
        val reports = (1L..40L).map { report(it, 1, 1L to 9) }
        assertEquals(20, NetDesyncPresenter.build(reports, names).rows.size)
        assertEquals("Tick 40", NetDesyncPresenter.build(reports, names).rows.first().tick)
    }

    @Test fun theSelfSummaryTracksRepairs() {
        assertEquals("Your copy has matched the host's throughout.", NetDesyncPresenter.selfSummary(view(0, 0)))
        assertEquals("Your copy drifted 2 times and was repaired each time.", NetDesyncPresenter.selfSummary(view(2, 2)))
        assertEquals("Your copy drifted 1 time; a repair is in progress.", NetDesyncPresenter.selfSummary(view(1, 0)))
    }

    @Test fun largeTicksGetThousandsSeparators() {
        assertEquals("Tick 12,000", NetDesyncPresenter.row(report(12_000, 1, 1L to 9), names).tick)
    }

    @Test fun reportsBuiltByHandAreSupported() {
        val r = DesyncReport(5, h(1), emptyList(), emptyList(), DesyncCause.NONE)
        assertEquals("Resolved", NetDesyncPresenter.row(r, names).headline)
    }
}
