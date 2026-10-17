package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.net.LeaderRow
import app.many.economytycoon.net.PlayerId
import app.many.economytycoon.net.ScoreBreakdown
import app.many.economytycoon.net.ScoreLine
import app.many.economytycoon.net.SeasonClock
import app.many.economytycoon.net.SeasonResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetLeaderboardPresenterTest {
    private fun row(rank: Int, company: Long, name: String, total: Long, tied: Boolean = false, player: Long? = company) = LeaderRow(
        rank, company, name, player?.let { PlayerId(it) },
        ScoreBreakdown(listOf(ScoreLine("Net worth growth", total - 100), ScoreLine("Solvency", 100)), total),
        Money.units(50_000L * (5 - rank)), Money.units(1_000L - rank), Money.units(9_000), tied,
    )

    private val clock = SeasonClock(2, 100, 200)
    private val names = { p: PlayerId -> "Player ${p.value}" }

    @Test fun pointsAreSignedAndGrouped() {
        assertEquals("+1,250", NetLeaderboardPresenter.points(1_250))
        assertEquals("-300", NetLeaderboardPresenter.points(-300))
        assertEquals("0", NetLeaderboardPresenter.points(0))
    }

    @Test fun tiedRanksAreMarked() {
        assertEquals("=2", NetLeaderboardPresenter.rank(row(2, 1, "A", 10, tied = true)))
        assertEquals("1", NetLeaderboardPresenter.rank(row(1, 1, "A", 10)))
    }

    @Test fun yourOwnRowIsMarkedAndColoured() {
        val r = NetLeaderboardPresenter.row(row(2, 7, "Seven Co", 400), 7)
        assertTrue(r.mine)
        assertEquals("Seven Co (you)", r.name)
        assertEquals(Tone.INFO, r.tone)
    }

    @Test fun theLeaderIsGreenAndALosingCompanyRed() {
        assertEquals(Tone.GOOD, NetLeaderboardPresenter.row(row(1, 1, "A", 900), null).tone)
        assertEquals(Tone.BAD, NetLeaderboardPresenter.row(row(3, 3, "C", -50), null).tone)
        assertEquals(Tone.NEUTRAL, NetLeaderboardPresenter.row(row(2, 2, "B", 50), null).tone)
    }

    @Test fun theBreakdownListsEveryScoreLine() {
        val r = NetLeaderboardPresenter.row(row(1, 1, "A", 900), null)
        assertEquals(listOf("Net worth growth", "Solvency"), r.breakdown.map { it.label })
        assertEquals(listOf("+800", "+100"), r.breakdown.map { it.value })
        assertEquals(Tone.GOOD, r.breakdown[0].tone)
    }

    @Test fun tilesShowSeasonLeaderAndGapToYou() {
        val rows = listOf(row(1, 1, "Alpha", 900), row(2, 2, "Beta", 650), row(3, 3, "Gamma", 100))
        val m = NetLeaderboardPresenter.build(rows, clock, 150, myCompany = 2)
        assertEquals("2", m.tiles[0].value)
        assertEquals("150 ticks left", m.tiles[0].note)
        assertEquals("Alpha", m.tiles[1].value)
        assertEquals("2", m.tiles[2].value)
        assertEquals("250 points behind", m.tiles[2].note)
        assertEquals(250_000L, m.progressPpm)
    }

    @Test fun theLeaderHasNoGapToThemselves() {
        val m = NetLeaderboardPresenter.build(listOf(row(1, 1, "Alpha", 900)), clock, 150, myCompany = 1)
        assertEquals(null, m.tiles[2].note)
    }

    @Test fun aFinishedSeasonSaysSo() {
        val m = NetLeaderboardPresenter.build(listOf(row(1, 1, "Alpha", 900)), clock, 400, null)
        assertEquals("Finished", m.tiles[0].note)
        assertEquals(1_000_000L, m.progressPpm)
        assertEquals("-", m.tiles[2].value)
    }

    @Test fun anEmptyTableStillBuilds() {
        val m = NetLeaderboardPresenter.build(emptyList(), clock, 100, null)
        assertTrue(m.rows.isEmpty())
        assertEquals("-", m.tiles[1].value)
        assertEquals(NetLeaderboardPresenter.EMPTY, m.empty)
    }

    @Test fun historyIsNewestFirstWithTheTopThree() {
        val results = listOf(
            SeasonResult(1, 100, listOf(row(1, 1, "Alpha", 500))),
            SeasonResult(2, 300, listOf(row(1, 2, "Beta", 700), row(2, 1, "Alpha", 600), row(3, 3, "Gamma", 20), row(4, 4, "Delta", 5))),
        )
        val h = NetLeaderboardPresenter.history(results, names)
        assertEquals(listOf("Season 2", "Season 1"), h.map { it.title })
        assertEquals("Player 2", h[0].winner)
        assertEquals("+700", h[0].points)
        assertEquals(3, h[0].standings.size)
        assertEquals("1. Player 2 +700", h[0].standings[0])
    }

    @Test fun anAiCompanyIsShownByItsOwnName() {
        val h = NetLeaderboardPresenter.history(listOf(SeasonResult(1, 10, listOf(row(1, 9, "Rival Corp", 300, player = null)))), names)
        assertEquals("Rival Corp", h[0].winner)
        assertFalse(h[0].standings[0].contains("Player"))
    }
}
