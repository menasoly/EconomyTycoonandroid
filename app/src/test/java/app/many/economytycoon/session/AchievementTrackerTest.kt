package app.many.economytycoon.session

import app.many.economytycoon.achievements.Stat
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FuturesId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.TenderId
import app.many.economytycoon.exchange.ExchangeEvent
import app.many.economytycoon.futures.FuturesEvent
import app.many.economytycoon.property.PropertyEvent
import app.many.economytycoon.scenarios.PlayerProfile
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.TickSummary
import app.many.economytycoon.tenders.TenderEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementTrackerTest {
    private fun session(): GameSession =
        (NewGame.start(NewGameOptions("Tracked Co", Difficulty.RELAXED, seed = 5L), SimTestSupport.content, GameSettings(autosave = false, pauseOnCritical = false)) as Outcome.Ok).value

    private fun GameSession.me(): CompanyId = playerCompanies().first()
    private val other = CompanyId(987_654L)

    private fun summary(tick: Long = 7, build: TickSummary.() -> Unit = {}) = TickSummary(Tick(tick)).also(build)

    @Test fun nothingIsTrackedWithoutAProfile() {
        val s = session()
        assertTrue(s.achievements.onTick(summary()).isEmpty())
        assertTrue(s.achievements.evaluate().isEmpty())
    }

    @Test fun everyTickCountsADayPlayed() {
        val s = session(); val p = PlayerProfile(); s.achievements.profile = p
        repeat(3) { s.achievements.onTick(summary()) }
        assertEquals(3L, p.stats[Stat.DAYS_PLAYED])
    }

    @Test fun onlyTheEventsOfMyCompaniesAreCounted() {
        val s = session(); val p = PlayerProfile(); s.achievements.profile = p
        val me = s.me()
        s.achievements.onTick(summary {
            tenderEvents = listOf(
                TenderEvent.Awarded(TenderId(1), me, Money.units(5), tick), TenderEvent.Awarded(TenderId(2), other, Money.units(5), tick),
                TenderEvent.Delivered(TenderId(1), me, Money.units(5), tick), TenderEvent.Delivered(TenderId(2), other, Money.units(5), tick),
            )
            propertyEvents = listOf(
                PropertyEvent.Sold(ParcelId(1), me, other, Money.units(9), tick), PropertyEvent.Sold(ParcelId(2), other, me, Money.units(9), tick),
                PropertyEvent.LeaseStarted(ParcelId(1), me, 4L, tick), PropertyEvent.LeaseStarted(ParcelId(1), other, 5L, tick),
            )
            futuresEvents = listOf(
                FuturesEvent.Opened(FuturesId(1), me, tick), FuturesEvent.Opened(FuturesId(2), other, tick),
                FuturesEvent.Liquidated(FuturesId(1), me, Money.units(1), tick), FuturesEvent.MarginCall(FuturesId(1), me, tick),
            )
            exchangeEvents = listOf(ExchangeEvent.Listed(me, tick, Money.units(10), 100), ExchangeEvent.Listed(other, tick, Money.units(10), 100))
        })
        assertEquals(1L, p.stats[Stat.TENDERS_WON]); assertEquals(1L, p.stats[Stat.TENDERS_DELIVERED])
        assertEquals(1L, p.stats[Stat.PARCELS_SOLD]); assertEquals(1L, p.stats[Stat.LEASES_SIGNED])
        assertEquals(1L, p.stats[Stat.FUTURES_OPENED]); assertEquals(1L, p.stats[Stat.FUTURES_LIQUIDATED])
        assertEquals(1L, p.stats[Stat.PUBLIC_OFFERINGS])
    }

    @Test fun checksOnlyRunEveryFifthDayUnlessForced() {
        val s = session(); val p = PlayerProfile(); s.achievements.profile = p
        val owner = s.economy.companies.require(s.me()).owner
        s.economy.finance.mint(s.economy.tick, owner, Money.units(2_000_000), "windfall")
        assertTrue(s.achievements.onTick(summary(tick = 3)).isEmpty())
        assertFalse(p.achievements.isUnlocked("first-million"))
        val forced = s.achievements.onTick(summary(tick = 4), forceCheck = true)
        assertTrue(forced.any { it.id == "first-million" })
        assertTrue(p.achievements.isUnlocked("first-million"))
    }

    @Test fun anAchievementIsAwardedOnlyOnce() {
        val s = session(); val p = PlayerProfile(); s.achievements.profile = p
        val owner = s.economy.companies.require(s.me()).owner
        s.economy.finance.mint(s.economy.tick, owner, Money.units(2_000_000), "windfall")
        val first = s.achievements.evaluate().map { it.id }
        assertTrue("six-figures" in first && "first-million" in first)
        assertTrue(s.achievements.evaluate().isEmpty())
    }

    @Test fun theUnlockDayIsTheLifetimeDayCount() {
        val s = session(); val p = PlayerProfile(); s.achievements.profile = p
        p.stats.add(Stat.DAYS_PLAYED, 40)
        s.achievements.evaluate()
        val owner = s.economy.companies.require(s.me()).owner
        s.economy.finance.mint(s.economy.tick, owner, Money.units(200_000), "windfall")
        s.achievements.evaluate()
        assertEquals(40L, p.achievements.unlockedAt("six-figures"))
    }

    @Test fun savingIsAskedForEveryThirtyDays() {
        val s = session(); val p = PlayerProfile(); s.achievements.profile = p
        repeat(29) { s.achievements.onTick(summary(tick = 1)) }
        assertFalse(s.achievements.shouldSave())
        s.achievements.onTick(summary(tick = 1))
        assertTrue(s.achievements.shouldSave())
        s.achievements.markSaved()
        assertFalse(s.achievements.shouldSave())
    }

    @Test fun steppingASessionPostsNewsAndWritesTheProfile() {
        val s = session(); val p = PlayerProfile()
        s.achievements.profile = p
        var writes = 0
        s.profileSink = { writes++ }
        val owner = s.economy.companies.require(s.me()).owner
        s.economy.finance.mint(s.economy.tick, owner, Money.units(1_200_000), "windfall")
        while (s.tick.value < 6) s.step()
        assertTrue(p.achievements.isUnlocked("first-million"))
        assertTrue(s.news.items().any { it.headline == "Achievement unlocked: First Million (Silver)" })
        assertTrue(writes >= 1)
        assertEquals(s.tick.value, p.stats[Stat.DAYS_PLAYED])
    }

    @Test fun aSessionWithoutAProfileStillRuns() {
        val s = session()
        repeat(8) { s.step() }
        assertTrue(s.news.items().none { it.headline.startsWith("Achievement unlocked") })
    }

    @Test fun theMeterReadsTheBestOfMyCompanies() {
        val s = session()
        assertTrue(s.achievements.meter(app.many.economytycoon.scenarios.GoalKind.CASH) > 0L)
        assertEquals(setOf(s.me()), s.achievements.mine())
    }
}
