package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.talent.Accreditation
import app.many.economytycoon.talent.TalentCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvidersPresenterTest {
    @Test fun ownProvidersComeWithControls() {
        val (_, v) = TalentViews.busy()
        val m = ProvidersPresenter.build(v)
        val p = m.mine.single()
        assertEquals("Night School", p.title)
        assertEquals("You", p.owner)
        assertTrue(p.accredit is TalentCommand.AccreditProvider)
        assertEquals(Accreditation.BASIC, (p.accredit as TalentCommand.AccreditProvider).level)
        assertTrue(p.moreTeaching is TalentCommand.SetTeaching && p.lessTeaching is TalentCommand.SetTeaching)
        assertEquals(1_200_000L, (p.moreTeaching as TalentCommand.SetTeaching).levelPpm)
        assertEquals(800_000L, (p.lessTeaching as TalentCommand.SetTeaching).levelPpm)
    }

    @Test fun otherPeoplesProvidersAreReadOnly() {
        val (_, v) = TalentViews.busy(who = CompanyId(2))
        val m = ProvidersPresenter.build(v)
        assertTrue(m.mine.isEmpty())
        val p = m.market.first()
        assertNull(p.accredit); assertNull(p.moreTeaching); assertNull(p.lessTeaching)
        assertTrue(p.courses.all { it.close == null })
    }

    @Test fun coursesAreListedWithTheirNumbers() {
        val (_, v) = TalentViews.busy()
        val c = ProvidersPresenter.build(v).mine.single().courses.single()
        assertEquals("Lathe basics", c.title)
        assertTrue(c.detail.contains("Bootcamp in machining"))
        assertTrue(c.close is TalentCommand.CloseCourse)
    }

    @Test fun accreditationSteps() {
        assertEquals(Accreditation.BASIC, ProvidersPresenter.nextLevel(Accreditation.NONE))
        assertEquals(Accreditation.PREMIER, ProvidersPresenter.nextLevel(Accreditation.RECOGNISED))
        assertNull(ProvidersPresenter.nextLevel(Accreditation.PREMIER))
    }

    @Test fun rowsShowRankAndBusinessFigures() {
        val (_, v) = TalentViews.busy()
        val p = ProvidersPresenter.build(v).mine.single()
        assertTrue(p.rank.startsWith("#"))
        assertTrue(p.business.contains("Revenue") && (p.business.contains("loss") || p.business.contains("profit")))
        assertTrue(p.quality.startsWith("Quality"))
    }

    @Test fun tilesSummariseTheMarket() {
        val (_, v) = TalentViews.busy()
        val m = ProvidersPresenter.build(v)
        assertEquals(listOf("Providers", "Yours", "Students", "Profit"), m.tiles.map { it.label })
        assertEquals("1", m.tiles[1].value)
        assertEquals(Tone.BAD, m.tiles[3].tone)
    }

    @Test fun anEmptyMarketInvitesTheOwnerToOpenOne() {
        val (_, v) = TalentViews.quiet()
        val m = ProvidersPresenter.build(v)
        assertTrue(m.mine.isEmpty() && m.market.isEmpty())
        assertTrue(m.note.startsWith("Open a provider"))
    }
}
