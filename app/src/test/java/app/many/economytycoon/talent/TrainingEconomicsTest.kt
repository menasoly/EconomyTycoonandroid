package app.many.economytycoon.talent

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingEconomicsTest {
    private fun plan(
        kind: ProgrammeKind = ProgrammeKind.CERTIFICATION, seats: Long = 20L, days: Int = 60, wage: Long = 100L, current: Long = 400_000L,
        required: Long = 600_000L, quit: Long = 100_000L, clause: RetentionClause? = null, horizon: Int = 720,
    ) = TrainingPlan(kind, seats, days, TrainingEconomics.costPerHead(kind, days, Ppm.ONE), Money.units(wage), current, required, quit, clause, horizon)

    @Test fun gainIsCappedBelowTheCeiling() {
        // coaching: 22k a month for three months, but only 100k above a 600k requirement is allowed
        assertEquals(66_000L, TrainingEconomics.levelGain(ProgrammeKind.ON_THE_JOB, 90, 500_000L, 600_000L))
        assertEquals(100_000L, TrainingEconomics.levelGain(ProgrammeKind.ON_THE_JOB, 360, 600_000L, 600_000L))
        assertEquals(0L, TrainingEconomics.levelGain(ProgrammeKind.ON_THE_JOB, 360, 700_000L, 600_000L))
        assertEquals(0L, TrainingEconomics.levelGain(ProgrammeKind.CERTIFICATION, 60, 900_000L, 600_000L))
    }

    @Test fun costScalesWithLocalPayAndDuration() {
        val base = TrainingEconomics.costPerHead(ProgrammeKind.APPRENTICESHIP, 360, Ppm.ONE)
        assertEquals(Money.units(9 * 360), base)
        assertEquals(Money.units(9 * 180), TrainingEconomics.costPerHead(ProgrammeKind.APPRENTICESHIP, 360, 500_000L))
        assertEquals(base.raw / 2, TrainingEconomics.costPerHead(ProgrammeKind.APPRENTICESHIP, 180, Ppm.ONE).raw)
    }

    @Test fun aWellTargetedCourseForWellPaidStaffPaysBack() {
        val c = TrainingEconomics.evaluate(plan())
        assertTrue(c.levelGainPpm > 0L && c.productivityGainPpm > 0L)
        assertTrue(c.benefit > c.totalCost)
        assertTrue(c.worthIt)
        assertNotNull(c.paybackDays)
        assertTrue(c.paybackDays!! > 60)
    }

    @Test fun peopleWhoAlreadyMeetTheBarGainNothing() {
        val c = TrainingEconomics.evaluate(plan(current = 900_000L))
        assertEquals(0L, c.levelGainPpm)
        assertFalse(c.worthIt)
        assertNull(c.paybackDays)
        assertTrue(c.net.isNegative)
    }

    @Test fun highTurnoverEatsTheBenefit() {
        val stable = TrainingEconomics.evaluate(plan(quit = 50_000L))
        val churn = TrainingEconomics.evaluate(plan(quit = 900_000L))
        assertTrue(stable.benefit > churn.benefit)
        assertTrue(stable.survivingSharePpm > churn.survivingSharePpm)
    }

    @Test fun aRetentionClauseRecoversPartOfTheCostFromLeavers() {
        val without = TrainingEconomics.evaluate(plan(quit = 500_000L))
        val with = TrainingEconomics.evaluate(plan(quit = 500_000L, clause = RetentionClause(360, 800_000L)))
        assertEquals(Money.ZERO, without.expectedClawback)
        assertTrue(with.expectedClawback.isPositive)
        assertTrue(with.net > without.net)
    }

    @Test fun aLongerBondRecoversMore() {
        val short = TrainingEconomics.evaluate(plan(quit = 500_000L, clause = RetentionClause(90, 800_000L)))
        val long = TrainingEconomics.evaluate(plan(quit = 500_000L, clause = RetentionClause(540, 800_000L)))
        assertTrue(long.expectedClawback > short.expectedClawback)
    }

    @Test fun lostWorkTimeIsACost() {
        val c = TrainingEconomics.evaluate(plan(seats = 10L, days = 60, wage = 100L))
        // certification takes half of a trainee's time: 10 seats x 60 days x 100 x 0.5
        assertEquals(Money.units(30_000), c.lostOutput)
    }

    @Test fun biggerGainsAreWorthMoreToExpensiveStaff() {
        val cheap = TrainingEconomics.evaluate(plan(wage = 50L))
        val dear = TrainingEconomics.evaluate(plan(wage = 200L))
        assertTrue(dear.benefit > cheap.benefit)
    }

    @Test fun aShortHorizonCannotPayBack() {
        val c = TrainingEconomics.evaluate(plan(horizon = 60))
        assertEquals(Money.ZERO, c.benefit)
        assertFalse(c.worthIt)
    }

    @Test fun costsAreLinearInSeats() {
        val one = TrainingEconomics.evaluate(plan(seats = 1L))
        val ten = TrainingEconomics.evaluate(plan(seats = 10L))
        assertEquals(one.totalCost * 10L, ten.totalCost)
    }

    @Test fun clauseValidatesItsTerms() {
        assertTrue(runCatching { RetentionClause(0, 500_000L) }.isFailure)
        assertTrue(runCatching { RetentionClause(2_000, 500_000L) }.isFailure)
        assertTrue(runCatching { RetentionClause(100, 0L) }.isFailure)
        assertTrue(runCatching { RetentionClause(100, 1_500_000L) }.isFailure)
        assertEquals(100, RetentionClause(100, 500_000L).bondDays)
    }
}
