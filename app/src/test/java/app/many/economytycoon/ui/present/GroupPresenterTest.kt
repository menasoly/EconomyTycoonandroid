package app.many.economytycoon.ui.present

import app.many.economytycoon.consolidation.GroupFixture
import app.many.economytycoon.consolidation.GroupStatements
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.session.GameSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupPresenterTest {
    private val settings = GameSettings(autosave = false, pauseOnCritical = false)
    private val root = GroupFixture.member(1, "Parent", 1_000_000)

    private fun group(vararg subs: app.many.economytycoon.consolidation.MemberStatements) =
        GroupStatements.of(CompanyId(1), listOf(root) + subs)!!

    @Test fun fullOwnershipReadsAsAWholeHundred() {
        assertEquals("100%", GroupPresenter.interest(1_000_000))
        assertTrue(GroupPresenter.interest(450_000).startsWith("45"))
    }

    @Test fun theRootIsMarkedAndSubsidiariesAreNot() {
        val g = group(GroupFixture.member(2, "Branch", 600_000))
        val rows = g.members.map { GroupPresenter.row(g, it, settings) }
        assertTrue(rows[0].isRoot)
        assertFalse(rows[1].isRoot)
    }

    @Test fun lossMakersAreNamed() {
        val loser = GroupFixture.member(2, "Drain", 1_000_000, GroupFixture.income(sales = 1_000, cogs = 4_000))
        val notes = GroupPresenter.notes(group(loser))
        assertTrue(notes.toString(), notes.any { it.startsWith("Drain is losing money") })
    }

    @Test fun severalLossMakersAreListedTogether() {
        val a = GroupFixture.member(2, "Drain", 1_000_000, GroupFixture.income(sales = 1_000, cogs = 4_000))
        val b = GroupFixture.member(3, "Sink", 1_000_000, GroupFixture.income(sales = 1_000, cogs = 4_000))
        val note = GroupPresenter.notes(group(a, b)).first { it.contains("losing") }
        assertTrue(note, note.contains("Drain, Sink are losing"))
    }

    @Test fun aDominantSubsidiaryIsCalledOut() {
        val giant = GroupFixture.member(2, "Giant", 1_000_000, GroupFixture.income(sales = 90_000))
        assertTrue(GroupPresenter.notes(group(giant)).any { it.startsWith("Giant brings in most") })
    }

    @Test fun methodNotesAlwaysComeFirst() {
        val notes = GroupPresenter.notes(group())
        assertEquals(listOf(GroupPresenter.METHOD, GroupPresenter.INTRAGROUP), notes)
    }

    @Test fun modelCarriesTheTilesAndBothStatements() {
        val m = GroupPresenter.model(group(GroupFixture.member(2, "Branch", 500_000)), "Parent", settings)
        assertEquals("Parent group", m.title)
        assertEquals("2", m.tiles.first().value)
        assertEquals(2, m.members.size)
        assertEquals(m.income.size, 3)
    }
}
