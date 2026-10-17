package app.many.economytycoon.ui.present

import app.many.economytycoon.talent.ProgrammeKind
import app.many.economytycoon.talent.ProgrammeStatus
import app.many.economytycoon.talent.TalentCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPresenterTest {
    @Test fun runningProgrammesCanBeCancelled() {
        val (_, v) = TalentViews.busy()
        val m = TrainingPresenter.build(v)
        assertEquals(1, m.running.size)
        assertTrue(m.running.single().cancel is TalentCommand.CancelTraining)
        assertEquals("Running", m.running.single().status)
        assertTrue(m.running.single().timing.startsWith("Ends in"))
    }

    @Test fun clausesAreDescribed() {
        val (_, v) = TalentViews.busy()
        val bond = TrainingPresenter.build(v).running.single().bond
        assertNotNull(bond)
        assertTrue(bond!!.startsWith("Bound for 360 days"))
    }

    @Test fun optionsOfferTrainingWhereSkillsFallShort() {
        val (_, v) = TalentViews.quiet(30)
        val m = TrainingPresenter.build(v)
        val weak = v.cohorts.count { it.weakestSkillId != null }
        assertEquals(weak * 2, m.options.size)
        for (o in m.options) {
            val s = o.start as TalentCommand.StartTraining
            assertTrue(s.seats >= 1L && s.kind in listOf(ProgrammeKind.ON_THE_JOB, ProgrammeKind.CERTIFICATION))
        }
    }

    @Test fun cohortsAlreadyInTrainingGetNoSecondOffer() {
        val (_, v) = TalentViews.busy()
        val m = TrainingPresenter.build(v)
        assertTrue(m.options.none { (it.start as TalentCommand.StartTraining).cohort == v.programmes.first().cohort })
    }

    @Test fun statusWordsAndTones() {
        assertEquals("Finished", TrainingPresenter.statusLabel(ProgrammeStatus.COMPLETED))
        assertEquals(Tone.MUTED, TrainingPresenter.statusTone(ProgrammeStatus.CANCELLED))
        assertEquals(Tone.GOOD, TrainingPresenter.statusTone(ProgrammeStatus.COMPLETED))
    }

    @Test fun finishedProgrammesGoToThePastList() {
        val (sc, _) = TalentViews.busy()
        sc.run(400)
        val v = app.many.economytycoon.talent.TalentReader(sc.system).view(TalentViews.company, app.many.economytycoon.core.Tick(sc.day), sc.inputs(), sc.world)
        val m = TrainingPresenter.build(v)
        assertTrue(m.running.isEmpty())
        assertTrue(m.past.isNotEmpty())
        assertNull(m.past.first().cancel)
    }

    @Test fun tilesCountTrainees() {
        val (_, v) = TalentViews.busy()
        val m = TrainingPresenter.build(v)
        assertEquals("10", m.tiles[1].value)
    }
}
