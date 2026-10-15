package app.many.economytycoon.activity

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.LoggedCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityLogTest {
    private val me = CompanyId(1)
    private val rival = CompanyId(2)
    private val f = FacilityId(3)
    private fun log(seq: Long, tick: Long, actor: Actor, cmd: Command) = LoggedCommand(seq, tick, actor, cmd)

    private val sample = listOf(
        log(1, 0, Actor.Player(1), Command.Rename(me, "Mine Co")),
        log(2, 1, Actor.Ai(rival), Command.Shutdown(rival, f)),
        log(3, 5, Actor.Player(1), Command.Borrow(me, Money.units(1_000), 90)),
        log(4, 5, Actor.Player(1), Command.Shutdown(me, f)),
        log(5, 9, Actor.System, Command.FileInsolvency(me)),
        log(6, 12, Actor.Player(1), Command.Repay(me, null, Money.units(200))),
    )

    @Test fun originsFollowTheActor() {
        assertEquals(Origin.PLAYER, ActivityLog.originOf(Actor.Player(4)))
        assertEquals(Origin.AI, ActivityLog.originOf(Actor.Ai(rival)))
        assertEquals(Origin.SYSTEM, ActivityLog.originOf(Actor.System))
        assertEquals(Origin.values().size, Origin.values().map { it.label }.toSet().size)
    }

    @Test fun onlyTheCompanysOwnPlayerCommandsAreShownByDefault() {
        val entries = ActivityLog.of(sample, me)
        assertEquals(listOf(6L, 4L, 3L, 1L), entries.map { it.sequence })
    }

    @Test fun otherOriginsCanBeAskedFor() {
        val all = ActivityLog.of(sample, me, origins = Origin.values().toSet())
        assertEquals(listOf(6L, 5L, 4L, 3L, 1L), all.map { it.sequence })
        assertEquals(listOf(2L), ActivityLog.of(sample, rival, origins = setOf(Origin.AI)).map { it.sequence })
        assertTrue(ActivityLog.of(sample, rival).isEmpty())
    }

    @Test fun theLimitKeepsTheNewest() {
        assertEquals(listOf(6L, 4L), ActivityLog.of(sample, me, limit = 2).map { it.sequence })
    }

    @Test fun entriesCarryTextAreaAndMoneyFlag() {
        val borrow = ActivityLog.of(sample, me).first { it.sequence == 3L }
        assertEquals("Borrowed ${Money.units(1_000)} for 90 days", borrow.text)
        assertEquals(ActivityArea.FINANCE, borrow.area)
        assertTrue(borrow.movesMoney)
        assertFalse(ActivityLog.of(sample, me).first { it.sequence == 1L }.movesMoney)
    }

    @Test fun areaFilterKeepsMatchingEntriesOnly() {
        val entries = ActivityLog.of(sample, me)
        assertEquals(listOf(3L, 6L), ActivityLog.inArea(entries, ActivityArea.FINANCE).map { it.sequence }.sorted())
        assertEquals(entries, ActivityLog.inArea(entries, null))
    }

    @Test fun areasAreListedMostFrequentFirst() {
        val areas = ActivityLog.areasIn(ActivityLog.of(sample, me))
        assertEquals(ActivityArea.FINANCE, areas.first())
        assertEquals(setOf(ActivityArea.FINANCE, ActivityArea.FACILITIES, ActivityArea.COMPANY), areas.toSet())
    }

    @Test fun daysAreGroupedNewestFirst() {
        val days = ActivityLog.byDay(ActivityLog.of(sample, me))
        assertEquals(listOf(12L, 5L, 0L), days.map { it.first })
        assertEquals(2, days[1].second.size)
    }

    @Test fun anEmptyLogGivesNothing() {
        assertTrue(ActivityLog.of(emptyList(), me).isEmpty())
        assertTrue(ActivityLog.byDay(emptyList()).isEmpty())
        assertTrue(ActivityLog.areasIn(emptyList()).isEmpty())
    }
}
