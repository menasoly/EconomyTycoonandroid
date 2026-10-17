package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapitalProjectsTest {
    private val p = UrbanParams()
    private fun start(book: ProjectBook, id: Long = 1, kind: ProjectKind = ProjectKind.SCHOOL, budget: Money = Money.units(10_000_000), fixed: Boolean = false, line: TransitLineId? = null, road: LinkId? = null) =
        book.start(CapitalProjectId(id), kind, "Project $id", 1, line, road, 0L, budget, Tick(0), fixed)

    private fun run(book: ProjectBook, days: Int, seed: Long = 5L, pay: (Money) -> Boolean = { true }, from: Int = 0): List<ProjectEvent> {
        val rnd = DeterministicRandom(seed)
        val out = ArrayList<ProjectEvent>()
        for (d in 1..days) out += book.advanceDay(Tick((from + d).toLong()), pay, rnd)
        return out
    }

    @Test fun `starting needs a name and a budget`() {
        val book = ProjectBook()
        assertEquals(Rules.INVALID_ARGUMENT, book.start(CapitalProjectId(1), ProjectKind.PARK, " ", 0, null, null, 100, Money.units(1), Tick(0), false).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, book.start(CapitalProjectId(1), ProjectKind.PARK, "P", 0, null, null, 100, Money.ZERO, Tick(0), false).rejectionOrNull()?.rule)
        assertTrue(start(book).isOk)
    }

    @Test fun `only a few projects run at once`() {
        val book = ProjectBook()
        for (i in 1..ProjectBook.MAX_RUNNING) assertTrue(start(book, i.toLong()).isOk)
        assertEquals(Rules.LIMIT_EXCEEDED, start(book, 99).rejectionOrNull()?.rule)
        assertEquals(ProjectBook.MAX_RUNNING, book.running().size)
    }

    @Test fun `a line or road can be under works only once`() {
        val book = ProjectBook()
        start(book, 1, ProjectKind.TRANSIT_LINE, line = TransitLineId(3))
        assertEquals(Rules.DUPLICATE, start(book, 2, ProjectKind.TRANSIT_LINE, line = TransitLineId(3)).rejectionOrNull()?.rule)
        start(book, 3, ProjectKind.ROAD_UPGRADE, road = LinkId(4))
        assertEquals(Rules.DUPLICATE, start(book, 4, ProjectKind.ROAD_UPGRADE, road = LinkId(4)).rejectionOrNull()?.rule)
    }

    @Test fun `phase shares add up to the whole`() {
        assertEquals(Scale.PPM, ProjectPhase.values().sumOf { it.share })
        assertEquals(ProjectPhase.DESIGN, ProjectPhase.PLANNING.next)
        assertEquals(null, ProjectPhase.COMMISSIONING.next)
    }

    @Test fun `a new project starts in planning with a daily draw`() {
        val book = ProjectBook()
        val project = start(book).getOrThrow()
        assertEquals(ProjectPhase.PLANNING, project.phase)
        assertEquals(0L, project.progressPpm())
        assertEquals(project.phaseBudget().raw / ProjectPhase.PLANNING.baseDays, project.dailyCost().raw)
        assertEquals(60 + 90 + 45 + 360 + 30, project.daysLeft())
    }

    @Test fun `a funded project moves through every phase and completes`() {
        val book = ProjectBook()
        val project = start(book).getOrThrow()
        val events = run(book, 1200)
        assertEquals(ProjectStatus.COMPLETED, project.status)
        assertEquals(Scale.PPM, project.progressPpm())
        assertEquals(5, events.filterIsInstance<ProjectEvent.PhaseFinished>().size)
        assertEquals(1, events.filterIsInstance<ProjectEvent.Completed>().size)
        assertNotNull(project.finishedAt)
        assertTrue(book.running().isEmpty())
        assertEquals(listOf(project), book.finished())
    }

    private fun assertNotNull(x: Any?) = org.junit.Assert.assertNotNull(x)

    @Test fun `spending adds up to roughly the final budget`() {
        val book = ProjectBook()
        val project = start(book).getOrThrow()
        run(book, 1500)
        val diff = Math.abs(project.spent.raw - project.budget.raw)
        assertTrue("spent ${project.spent} budget ${project.budget}", diff < project.budget.raw / 5)
    }

    @Test fun `progress never falls while funded`() {
        val book = ProjectBook()
        val project = start(book).getOrThrow()
        val rnd = DeterministicRandom(8)
        var last = 0L
        for (d in 1..700) {
            book.advanceDay(Tick(d.toLong()), { true }, rnd)
            assertTrue(project.progressPpm() >= last)
            last = project.progressPpm()
        }
    }

    @Test fun `a project that cannot pay stalls and then resumes`() {
        val book = ProjectBook()
        val project = start(book).getOrThrow()
        val rnd = DeterministicRandom(1)
        val stalled = book.advanceDay(Tick(1), { false }, rnd)
        assertEquals(listOf(ProjectEvent.Stalled(project.id, Tick(1))), stalled)
        assertEquals(ProjectStatus.STALLED, project.status)
        book.advanceDay(Tick(2), { false }, rnd)
        assertEquals(2, project.stalledDays)
        assertEquals(0, project.phaseDay)
        val resumed = book.advanceDay(Tick(3), { true }, rnd)
        assertEquals(listOf(ProjectEvent.Resumed(project.id, 2, Tick(3))), resumed)
        assertEquals(ProjectStatus.ACTIVE, project.status)
        assertEquals(1, project.phaseDay)
    }

    @Test fun `a stalled project does not draw random numbers so streams stay aligned`() {
        val a = DeterministicRandom(3); val b = DeterministicRandom(3)
        val book = ProjectBook(); start(book)
        repeat(50) { book.advanceDay(Tick(it.toLong()), { false }, a) }
        assertEquals(b.nextLong(), a.nextLong())
    }

    @Test fun `construction is where overruns happen`() {
        val book = ProjectBook()
        val project = start(book, kind = ProjectKind.HOSPITAL).getOrThrow()
        assertTrue(book.riskPpm(project, ProjectPhase.CONSTRUCTION) > book.riskPpm(project, ProjectPhase.PROCUREMENT))
        assertTrue(book.riskPpm(project, ProjectPhase.PROCUREMENT) > book.riskPpm(project, ProjectPhase.DESIGN))
    }

    @Test fun `fixed price contracts halve the risk and complex works carry more of it`() {
        val book = ProjectBook()
        val open = start(book, 1, ProjectKind.HOSPITAL).getOrThrow()
        val fixed = start(book, 2, ProjectKind.HOSPITAL, fixed = true).getOrThrow()
        val park = start(book, 3, ProjectKind.PARK).getOrThrow()
        assertEquals(book.riskPpm(open, ProjectPhase.CONSTRUCTION) / 2, book.riskPpm(fixed, ProjectPhase.CONSTRUCTION))
        assertTrue(book.riskPpm(open, ProjectPhase.CONSTRUCTION) > book.riskPpm(park, ProjectPhase.CONSTRUCTION))
    }

    @Test fun `overruns raise the budget and are bounded by the risk`() {
        for (seed in 1L..20L) {
            val book = ProjectBook()
            val project = start(book, kind = ProjectKind.TRANSIT_LINE, budget = Money.units(100_000_000)).getOrThrow()
            run(book, 1500, seed)
            assertTrue("seed $seed", project.overrunPpm in -50_000L..800_000L)
            assertTrue(project.delayDays >= 0)
        }
    }

    @Test fun `different seeds give different outcomes and the same seed the same`() {
        fun outcome(seed: Long): Pair<Money, Int> {
            val book = ProjectBook()
            val project = start(book, kind = ProjectKind.HOSPITAL).getOrThrow()
            run(book, 1500, seed)
            return project.budget to project.delayDays
        }
        assertEquals(outcome(11), outcome(11))
        assertTrue((1L..12L).map { outcome(it) }.toSet().size > 3)
    }

    @Test fun `cancelling stops the work and keeps the spending`() {
        val book = ProjectBook()
        val project = start(book).getOrThrow()
        run(book, 40)
        val spent = project.spent
        val e = book.cancel(project.id, Tick(41)).getOrThrow()
        assertEquals(ProjectEvent.Cancelled(project.id, spent, Tick(41)), e)
        assertEquals(ProjectStatus.CANCELLED, project.status)
        run(book, 40, from = 41)
        assertEquals(spent, project.spent)
        assertEquals(Rules.WRONG_STATE, book.cancel(project.id, Tick(90)).rejectionOrNull()?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, book.cancel(CapitalProjectId(77), Tick(90)).rejectionOrNull()?.rule)
    }

    @Test fun `outstanding commitments shrink as money is spent`() {
        val book = ProjectBook()
        val project = start(book).getOrThrow()
        assertEquals(project.budget, book.outstanding())
        run(book, 100)
        assertTrue(book.outstanding() < project.budget)
        assertEquals(project.budget - project.spent, book.outstanding())
    }

    @Test fun `estimates scale with size and kind`() {
        assertTrue(ProjectEstimates.estimate(ProjectKind.PARK, 20_000L, p) > ProjectEstimates.estimate(ProjectKind.PARK, 10_000L, p))
        assertTrue(ProjectEstimates.estimate(ProjectKind.HOSPITAL, 0L, p) > ProjectEstimates.estimate(ProjectKind.SCHOOL, 0L, p))
        assertEquals(Money.ZERO, ProjectEstimates.estimate(ProjectKind.TRANSIT_LINE, 0L, p))
        assertEquals(ProjectEstimates.estimate(ProjectKind.ROAD_UPGRADE, 0L, p, 2).raw * 2, ProjectEstimates.estimate(ProjectKind.ROAD_UPGRADE, 0L, p, 4).raw)
        assertEquals(p.buildCostPerSqm.raw * 1000 * 9 / 10, ProjectEstimates.estimate(ProjectKind.HOUSING_PROGRAM, 1000L, p).raw)
    }

    @Test fun `hash reflects progress and a restored book matches`() {
        fun h(b: ProjectBook) = CanonicalHasher().also { b.hashInto(it) }.finish()
        val a = ProjectBook(); start(a)
        val b = ProjectBook(); start(b)
        assertEquals(h(a), h(b))
        run(a, 10)
        assertTrue(h(a) != h(b))
        run(b, 10)
        assertEquals(h(a), h(b))
        assertFalse(a.all().isEmpty())
    }
}
