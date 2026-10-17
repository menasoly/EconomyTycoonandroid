package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuccessionServiceTest {
    private val state = TalentState()
    private val taxonomy = TalentTestSupport.taxonomy
    private val svc = SuccessionService(state, taxonomy, 99L)
    private val co = CompanyId(1)
    private val region = RegionId(1)
    private val exec = RoleId("executive")
    private val manager = RoleId("manager")

    private fun rule(o: Outcome<*>) = o.rejectionOrNull()?.rule
    private fun person(name: String = "Ada", role: RoleId = manager, age: Int = 45, wage: Long = 200L, levels: Map<SkillId, Long>? = null, company: CompanyId = co, potential: Long = 800_000L): Person =
        (svc.addPerson(company, name, role, region, Money.units(wage), age, Tick(0), levels, potential) as Outcome.Ok).value

    private fun fullLevels(role: RoleId, scale: Long = Ppm.ONE): Map<SkillId, Long> =
        taxonomy.profile(role)!!.requirements.associate { it.skill to Ppm.mul(it.requiredPpm, scale) }

    private fun position(role: RoleId = exec, title: String = "CEO"): KeyPosition = (svc.openPosition(co, role, region, title) as Outcome.Ok).value

    @Test fun peopleNeedAKnownRoleANameAndSanePay() {
        assertEquals(Rules.UNKNOWN_ENTITY, rule(svc.addPerson(co, "X", RoleId("astronaut"), region, Money.units(1), 40, Tick(0))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(svc.addPerson(co, " ", manager, region, Money.units(1), 40, Tick(0))))
        assertEquals(Rules.INVALID_PRICE, rule(svc.addPerson(co, "X", manager, region, Money.ZERO, 40, Tick(0))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(svc.addPerson(co, "X", manager, region, Money.units(1), 12, Tick(0))))
        assertTrue(svc.addPerson(co, "X", manager, region, Money.units(1), 40, Tick(0)) is Outcome.Ok)
    }

    @Test fun drawnLevelsFollowTheRoleAndThePayOnOffer() {
        val profile = taxonomy.profile(manager)!!
        val low = svc.candidateLevels(profile, 700_000L, DeterministicRandom(5L))
        val high = svc.candidateLevels(profile, 1_400_000L, DeterministicRandom(5L))
        assertEquals(profile.requirements.map { it.skill }.toSet(), low.keys)
        for (s in low.keys) assertTrue(high.getValue(s) > low.getValue(s))
    }

    @Test fun drawnLevelsAreDeterministic() {
        val a = SuccessionService(TalentState(), taxonomy, 7L)
        val b = SuccessionService(TalentState(), taxonomy, 7L)
        val pa = (a.addPerson(co, "A", manager, region, Money.units(170), 40, Tick(0)) as Outcome.Ok).value
        val pb = (b.addPerson(co, "A", manager, region, Money.units(170), 40, Tick(0)) as Outcome.Ok).value
        assertEquals(pa.levelsView(), pb.levelsView())
    }

    @Test fun personsLiveInTheCompanyLevelCohortOfTheirRole() {
        val p = person()
        assertEquals(CompanyId(1), p.cohort.company)
        assertEquals(0L, p.cohort.facility.value)
        assertEquals(manager, p.cohort.role)
        assertEquals(listOf(p), svc.peopleOf(co))
        assertEquals(p, svc.person(p.id))
    }

    @Test fun onlyKeyRolesHavePositions() {
        assertEquals(Rules.INVALID_ARGUMENT, rule(svc.openPosition(co, RoleId("machinist"), region, "Foreman")))
        assertEquals(Rules.INVALID_ARGUMENT, rule(svc.openPosition(co, exec, region, " ")))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(svc.openPosition(co, RoleId("astronaut"), region, "x")))
        repeat(svc.params.maxPositionsPerCompany) { position(title = "P$it") }
        assertEquals(Rules.LIMIT_EXCEEDED, rule(svc.openPosition(co, exec, region, "one more")))
    }

    @Test fun appointingFillsAPositionOnce() {
        val pos = position()
        val p = person(role = exec, levels = fullLevels(exec))
        assertTrue(svc.appoint(pos.id, p.id, Tick(1)) is Outcome.Ok)
        assertEquals(p, svc.holderOf(pos.id))
        assertEquals(pos.id, p.position)
        assertEquals(Rules.WRONG_STATE, rule(svc.appoint(pos.id, person("B").id, Tick(2))))
        val other = position(title = "COO")
        assertEquals(Rules.DUPLICATE, rule(svc.appoint(other.id, p.id, Tick(2))))
    }

    @Test fun appointingChecksOwnershipAndExistence() {
        val pos = position()
        val foreign = person(company = CompanyId(2))
        assertEquals(Rules.NOT_OWNER, rule(svc.appoint(pos.id, foreign.id, Tick(1))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(svc.appoint(PositionId(99), foreign.id, Tick(1))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(svc.appoint(pos.id, PersonId(99), Tick(1))))
    }

    @Test fun namingASuccessorComputesReadiness() {
        val pos = position()
        val ready = person("Ready", manager, levels = fullLevels(exec))
        val raw = person("Raw", manager, levels = fullLevels(exec, 400_000L))
        val a = (svc.nameSuccessor(pos.id, ready.id, DevelopmentKind.MENTORING, Tick(0)) as Outcome.Ok).value
        val b = (svc.nameSuccessor(pos.id, raw.id, DevelopmentKind.NONE, Tick(0)) as Outcome.Ok).value
        assertTrue(a.readinessPpm > 900_000L)
        assertTrue("raw=${b.readinessPpm}", b.readinessPpm < 300_000L && b.readinessPpm < a.readinessPpm)
        assertEquals(listOf(ready.id, raw.id), svc.successorsOf(pos.id).map { it.person })
    }

    @Test fun successorRulesAreEnforced() {
        val pos = position()
        val holder = person("Holder", exec, levels = fullLevels(exec)); svc.appoint(pos.id, holder.id, Tick(0))
        assertEquals(Rules.INVALID_ARGUMENT, rule(svc.nameSuccessor(pos.id, holder.id, DevelopmentKind.NONE, Tick(0))))
        val foreign = person("F", manager, company = CompanyId(2))
        assertEquals(Rules.NOT_OWNER, rule(svc.nameSuccessor(pos.id, foreign.id, DevelopmentKind.NONE, Tick(0))))
        val a = person("A")
        svc.nameSuccessor(pos.id, a.id, DevelopmentKind.NONE, Tick(0))
        assertEquals(Rules.DUPLICATE, rule(svc.nameSuccessor(pos.id, a.id, DevelopmentKind.NONE, Tick(0))))
        repeat(svc.params.maxSuccessorsPerPosition - 1) { svc.nameSuccessor(pos.id, person("S$it").id, DevelopmentKind.NONE, Tick(0)) }
        assertEquals(Rules.LIMIT_EXCEEDED, rule(svc.nameSuccessor(pos.id, person("Z").id, DevelopmentKind.NONE, Tick(0))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(svc.nameSuccessor(PositionId(77), a.id, DevelopmentKind.NONE, Tick(0))))
    }

    @Test fun removingAndRedevelopingSuccessors() {
        val pos = position(); val a = person("A")
        svc.nameSuccessor(pos.id, a.id, DevelopmentKind.NONE, Tick(0))
        assertTrue(svc.setDevelopment(pos.id, a.id, DevelopmentKind.STRETCH) is Outcome.Ok)
        assertEquals(DevelopmentKind.STRETCH, svc.successorsOf(pos.id).single().development)
        assertTrue(svc.removeSuccessor(pos.id, a.id) is Outcome.Ok)
        assertEquals(Rules.UNKNOWN_ENTITY, rule(svc.removeSuccessor(pos.id, a.id)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(svc.setDevelopment(pos.id, a.id, DevelopmentKind.NONE)))
    }

    @Test fun coverageCountsPositionsWithAReadySuccessor() {
        assertEquals(Ppm.ONE, svc.coveragePpm(co))
        val p1 = position(title = "A"); val p2 = position(title = "B")
        svc.nameSuccessor(p1.id, person("R", levels = fullLevels(exec)).id, DevelopmentKind.NONE, Tick(0))
        svc.nameSuccessor(p2.id, person("W", levels = fullLevels(exec, 300_000L)).id, DevelopmentKind.NONE, Tick(0))
        assertEquals(500_000L, svc.coveragePpm(co))
    }

    @Test fun aReadySuccessorStepsInWithLittleLoss() {
        val pos = position()
        val holder = person("Old", exec, 64, levels = fullLevels(exec)); svc.appoint(pos.id, holder.id, Tick(0))
        val heir = person("Heir", manager, levels = fullLevels(exec, 1_050_000L))
        svc.nameSuccessor(pos.id, heir.id, DevelopmentKind.MENTORING, Tick(0))
        val r = svc.vacate(pos.id, Tick(100), departed = true)
        assertEquals(heir, svc.holderOf(pos.id))
        assertEquals(Tick(100), holder.retiredAt)
        assertNull(holder.position)
        assertTrue(r.effects.single() is WorkforceEffect.Transfer)
        assertTrue(r.events.any { it is TalentEvent.PositionVacated && it.coveredBySuccessor })
        assertTrue(r.events.any { it is TalentEvent.PositionFilled })
        assertTrue(r.intents.isEmpty())
        assertTrue(svc.successorsOf(pos.id).isEmpty())
        assertTrue(pos.settlingPpm < 100_000L)
    }

    @Test fun withoutASuccessorTheCompanySearchesAndPays() {
        val pos = position()
        val holder = person("Old", exec, 64, levels = fullLevels(exec)); svc.appoint(pos.id, holder.id, Tick(0))
        val r = svc.vacate(pos.id, Tick(100), departed = true)
        assertTrue(pos.isVacant)
        assertEquals(Tick(100), pos.vacantSince)
        assertEquals(Tick(190), pos.searchUntil)
        assertEquals(1, r.intents.size)
        assertEquals(pos.searchSpent, r.intents[0].total)
        assertEquals(Money(Ppm.of(Money.units(320).raw * 360, 250_000L)), pos.searchSpent)
        assertTrue(r.events.any { it is TalentEvent.PositionVacated && !it.coveredBySuccessor })
    }

    @Test fun anUnreadySuccessorIsNotPromoted() {
        val pos = position()
        val holder = person("Old", exec, 64, levels = fullLevels(exec)); svc.appoint(pos.id, holder.id, Tick(0))
        val raw = person("Raw", manager, levels = fullLevels(exec, 300_000L))
        svc.nameSuccessor(pos.id, raw.id, DevelopmentKind.NONE, Tick(0))
        svc.vacate(pos.id, Tick(10), departed = true)
        assertTrue(pos.isVacant)
        assertNull(raw.position)
    }

    @Test fun theSearchEndsWithAnOutsideHire() {
        val pos = position()
        val holder = person("Old", exec, 64, levels = fullLevels(exec)); svc.appoint(pos.id, holder.id, Tick(0))
        svc.vacate(pos.id, Tick(0), departed = true)
        val early = svc.monthly(Tick(60)) { Ppm.ONE }
        assertTrue(pos.isVacant && early.effects.none { it is WorkforceEffect.Hire })
        val late = svc.monthly(Tick(120)) { Ppm.ONE }
        assertFalse(pos.isVacant)
        assertNotNull(svc.holderOf(pos.id))
        assertTrue(late.effects.any { it is WorkforceEffect.Hire })
        assertTrue(late.events.any { it is TalentEvent.PositionFilled })
    }

    @Test fun effectivenessReflectsEveryStageOfAVacancy() {
        val pos = position()
        assertEquals(svc.params.vacantEffectivenessPpm, svc.effectiveness(pos.id, Tick(0)))
        val holder = person("Old", exec, 64, levels = fullLevels(exec)); svc.appoint(pos.id, holder.id, Tick(0))
        assertTrue(svc.effectiveness(pos.id, Tick(0)) >= Ppm.ONE)
        svc.vacate(pos.id, Tick(10), departed = true)
        assertEquals(svc.params.interimEffectivenessPpm, svc.effectiveness(pos.id, Tick(11)))
        assertEquals(Ppm.ONE, svc.effectiveness(PositionId(404), Tick(0)))
    }

    @Test fun aNewSuccessorSettlesInOverTime() {
        val pos = position()
        val holder = person("Old", exec, 64, levels = fullLevels(exec)); svc.appoint(pos.id, holder.id, Tick(0))
        val heir = person("Heir", manager, levels = fullLevels(exec, 850_000L))
        svc.nameSuccessor(pos.id, heir.id, DevelopmentKind.NONE, Tick(0))
        assertTrue("ready=${svc.successorsOf(pos.id).single().readinessPpm}", svc.successorsOf(pos.id).single().readinessPpm >= svc.params.readyPpm)
        svc.vacate(pos.id, Tick(0), departed = true)
        val start = svc.effectiveness(pos.id, Tick(1))
        val end = svc.effectiveness(pos.id, Tick(svc.params.settlingDays.toLong() + 1))
        assertTrue(start < end)
        assertEquals(svc.fit(heir, exec)!!.multiplierPpm, end)
    }

    @Test fun developmentGrowsReadinessAndCostsMoney() {
        val pos = position()
        val holder = person("Old", exec, 50, levels = fullLevels(exec)); svc.appoint(pos.id, holder.id, Tick(0))
        val heir = person("Heir", manager, levels = fullLevels(exec, 600_000L), potential = 950_000L)
        svc.nameSuccessor(pos.id, heir.id, DevelopmentKind.STRETCH, Tick(0))
        val before = svc.successorsOf(pos.id).single().readinessPpm
        var spent = Money.ZERO
        for (m in 1..12) spent += Money.sum(svc.monthly(Tick(30L * m)) { Ppm.ONE }.intents.map { it.total })
        val after = svc.successorsOf(pos.id).single().readinessPpm
        assertTrue(after > before)
        assertEquals(Money.units(14 * 30 * 12), spent)
    }

    @Test fun developmentIsCappedByPotential() {
        val pos = position(); val heir = person("Heir", manager, levels = fullLevels(exec, 600_000L), potential = 650_000L)
        svc.nameSuccessor(pos.id, heir.id, DevelopmentKind.EXECUTIVE_COURSE, Tick(0))
        for (m in 1..60) svc.monthly(Tick(30L * m)) { Ppm.ONE }
        for (r in taxonomy.profile(exec)!!.requirements) assertTrue(heir.level(r.skill) <= maxOf(650_000L, r.requiredPpm) + 1L)
    }

    @Test fun noDevelopmentIsSlowerThanMentoring() {
        val pos = position()
        val a = person("A", manager, levels = fullLevels(exec, 600_000L), potential = 950_000L)
        val b = person("B", manager, levels = fullLevels(exec, 600_000L), potential = 950_000L)
        svc.nameSuccessor(pos.id, a.id, DevelopmentKind.NONE, Tick(0)); svc.nameSuccessor(pos.id, b.id, DevelopmentKind.MENTORING, Tick(0))
        for (m in 1..12) svc.monthly(Tick(30L * m)) { Ppm.ONE }
        assertTrue(b.level(DefaultTaxonomy.LEADERSHIP) > a.level(DefaultTaxonomy.LEADERSHIP))
    }

    @Test fun veteransEventuallyRetire() {
        val pos = position()
        val holder = person("Old", exec, 78, levels = fullLevels(exec)); svc.appoint(pos.id, holder.id, Tick(0))
        var retired = false
        for (m in 1..60) {
            val r = svc.monthly(Tick(30L * m)) { Ppm.ONE }
            if (r.effects.any { it is WorkforceEffect.Depart }) { retired = true; break }
        }
        assertTrue(retired)
        assertFalse(holder.isActive)
    }

    @Test fun youngLoyalHoldersAlmostNeverLeaveInAYear() {
        val pos = position()
        val holder = person("Young", exec, 40, levels = fullLevels(exec)); svc.appoint(pos.id, holder.id, Tick(0))
        holder.loyaltyPpm = 900_000L
        var left = 0
        for (m in 1..12) if (svc.monthly(Tick(30L * m)) { Ppm.ONE }.effects.any { it is WorkforceEffect.Depart }) left++
        assertTrue(left <= 1)
    }

    @Test fun loyaltyFollowsPay() {
        val p = person("P", manager); p.loyaltyPpm = 500_000L
        svc.monthly(Tick(30)) { 1_200_000L }
        assertTrue(p.loyaltyPpm > 500_000L)
        svc.monthly(Tick(60)) { 700_000L }
        svc.monthly(Tick(90)) { 700_000L }
        assertTrue(p.loyaltyPpm < 500_000L + svc.params.loyaltyDriftPpm)
    }

    @Test fun leavingByAnotherRouteVacatesThePost() {
        val pos = position()
        val holder = person("Star", exec, 45, levels = fullLevels(exec)); svc.appoint(pos.id, holder.id, Tick(0))
        val heir = person("Heir", manager, levels = fullLevels(exec, 1_000_000L))
        svc.nameSuccessor(pos.id, heir.id, DevelopmentKind.NONE, Tick(0))
        val r = svc.personLeft(holder.id, Tick(50))
        assertEquals(heir, svc.holderOf(pos.id))
        assertTrue(r.events.isNotEmpty())
        assertTrue(svc.personLeft(PersonId(404), Tick(51)).events.isEmpty())
    }

    @Test fun keyRoleIndexAveragesPositions() {
        assertEquals(Ppm.ONE, svc.keyRoleIndex(co, Tick(0)))
        val a = position(title = "A"); val b = position(title = "B")
        val holder = person("H", exec, 50, levels = fullLevels(exec)); svc.appoint(a.id, holder.id, Tick(0))
        val expected = (svc.effectiveness(a.id, Tick(0)) + svc.params.vacantEffectivenessPpm) / 2
        assertEquals(expected, svc.keyRoleIndex(co, Tick(0)))
        assertEquals(Ppm.ONE, svc.keyRoleIndex(CompanyId(9), Tick(0)))
        assertNotNull(b)
    }

    @Test fun everyoneAgesAYear() {
        val p = person("P", manager, 40)
        svc.rollYear()
        assertEquals(41, p.ageYears)
    }

    @Test fun readinessIsZeroForUnknownRoles() {
        assertEquals(0L, svc.readinessFor(person(), RoleId("astronaut")))
        assertNull(svc.fit(person(), RoleId("astronaut")))
    }

    @Test fun theSameSeedAndStepsGiveTheSameHash() {
        fun run(): TalentState {
            val st = TalentState(); val s = SuccessionService(st, taxonomy, 5L)
            val pos = (s.openPosition(co, exec, region, "CEO") as Outcome.Ok).value
            val h = (s.addPerson(co, "H", exec, region, Money.units(300), 66, Tick(0)) as Outcome.Ok).value
            s.appoint(pos.id, h.id, Tick(0))
            for (m in 1..24) s.monthly(Tick(30L * m)) { Ppm.ONE }
            return st
        }
        assertEquals(run().stateHash(), run().stateHash())
    }
}
