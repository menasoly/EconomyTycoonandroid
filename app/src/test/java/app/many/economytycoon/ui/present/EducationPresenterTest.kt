package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.talent.TalentCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EducationPresenterTest {
    @Test fun ownAndOtherInstitutionsAreSeparated() {
        val (_, v) = TalentViews.busy()
        val m = EducationPresenter.build(v)
        assertEquals(1, m.mine.size)
        assertTrue(m.others.isNotEmpty())
        assertEquals("Yours", m.mine.single().owner)
        assertTrue(m.others.all { it.owner != "Yours" })
    }

    @Test fun onlyOwnInstitutionsCanBeClosed() {
        val (_, v) = TalentViews.busy()
        val m = EducationPresenter.build(v)
        assertTrue(m.mine.single().close is TalentCommand.CloseInstitution)
        assertTrue(m.others.all { it.close == null })
    }

    @Test fun rowsDescribeSeatsFeesAndResults() {
        val (_, v) = TalentViews.busy()
        val r = EducationPresenter.build(v).mine.single()
        assertTrue(r.seats.contains(" of ") && r.seats.endsWith("places"))
        assertTrue(r.fees.endsWith("a year"))
        assertTrue(r.results.contains("graduated"))
        assertEquals("Machining", r.fields)
        assertTrue(r.where.startsWith("Vocational"))
    }

    @Test fun publicSchoolsAreFree() {
        val (_, v) = TalentViews.quiet()
        val m = EducationPresenter.build(v)
        assertTrue(m.others.all { it.fees == "Free to attend" })
        assertTrue(m.others.any { it.fields == "General schooling" })
    }

    @Test fun newBuildingIsMentioned() {
        val (sc, _) = TalentViews.busy()
        val inst = sc.system.education.ownedBy(TalentViews.company).single()
        sc.system.execute(TalentCommand.ExpandInstitution(TalentViews.company, inst.id, 50L), app.many.economytycoon.core.Tick(sc.day), sc.inputs(), sc.world)
        val v = app.many.economytycoon.talent.TalentReader(sc.system).view(TalentViews.company, app.many.economytycoon.core.Tick(sc.day), sc.inputs(), sc.world)
        assertEquals("50 places being built", EducationPresenter.build(v).mine.single().building)
    }

    @Test fun qualificationRowsShowEachRegion() {
        val (_, v) = TalentViews.quiet()
        val m = EducationPresenter.build(v)
        assertEquals(v.qualifications.size, m.qualifications.size)
        assertTrue(m.qualifications.all { it.index.endsWith("%") })
    }

    @Test fun tilesCountPlacesAndStudents() {
        val (_, v) = TalentViews.quiet()
        val m = EducationPresenter.build(v)
        assertEquals(listOf("Institutions", "Places", "Students", "Yours"), m.tiles.map { it.label })
        assertEquals("0", m.tiles[3].value)
        assertNull(m.mine.firstOrNull())
    }

    @Test fun moneyIsFormattedByTheCaller() {
        val (_, v) = TalentViews.busy()
        val m = EducationPresenter.build(v) { Money.ZERO.let { _ -> "<money>" } }
        assertTrue(m.mine.single().fees.startsWith("<money>"))
    }
}
