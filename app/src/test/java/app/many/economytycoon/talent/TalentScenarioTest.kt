package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Multi-year runs of the whole module around a workforce stand-in. */
class TalentScenarioTest {

    @Test fun eightYearsKeepEveryInvariant() {
        val sc = Scenario()
        val problems = sc.run(2_900)
        assertEquals(emptyList<String>(), problems)
        assertTrue(sc.events.any { it is TalentEvent.ClassGraduated })
        assertTrue(sc.intents.isNotEmpty())
        assertTrue(sc.system.education.qualifications(RegionId(1)).of(InstitutionKind.SCHOOL) > 0L)
    }

    @Test fun schoolsFeedThePoolsAndCollegesFollow() {
        val sc = Scenario()
        sc.run(2_000)
        val school = sc.events.filterIsInstance<TalentEvent.ClassGraduated>().filter { it.skill == DefaultTaxonomy.GENERAL }
        assertTrue(school.isNotEmpty())
        assertTrue(sc.system.markets.poolSize(RegionId(1), DefaultTaxonomy.GENERAL) > 0L)
    }

    @Test fun sameSeedGivesTheSameHistoryDifferentSeedsDiffer() {
        fun history(seed: Long): app.many.economytycoon.core.StateHash {
            val s = Scenario(seed)
            s.run(100)
            s.system.execute(TalentCommand.HirePerson(CompanyId(1), "Ada", RoleId("manager"), RegionId(1), Money.units(200), 41), Tick(s.day), s.inputs(), s.world)
            s.system.execute(TalentCommand.SubmitOffer(CompanyId(1), s.machinists1, PoachTarget.Group(s.machinists2, 25L), Money.units(135), Money.ZERO, CovenantStance.RESPECT), Tick(s.day), s.inputs(), s.world)
            s.run(900)
            return s.system.stateHash()
        }
        assertEquals(history(11L), history(11L))
        assertTrue(history(11L) != history(12L))
    }

    @Test fun aPoachingWarMovesPeopleAndKeepsHeadcountsHonest() {
        val sc = Scenario()
        sc.run(30)
        val before = sc.workforce.rows.getValue(sc.machinists2).heads
        val total = sc.workforce.rows.values.sumOf { it.heads }
        val r = sc.system.execute(
            TalentCommand.SubmitOffer(CompanyId(1), sc.machinists1, PoachTarget.Group(sc.machinists2, 25L), Money.units(150), Money.units(40), CovenantStance.RESPECT),
            Tick(sc.day), sc.inputs(), sc.world,
        )
        assertTrue(r is Outcome.Ok)
        sc.run(15)
        val offer = sc.system.poaching.offers().single()
        assertTrue(offer.status != OfferStatus.PENDING)
        if (offer.status == OfferStatus.SUCCEEDED) {
            assertTrue(sc.workforce.rows.getValue(sc.machinists2).heads <= before)
            assertTrue(sc.events.any { it is TalentEvent.PoachSucceeded })
            assertTrue(sc.workforce.rows.getValue(sc.machinists1).heads >= 120L + offer.movedHeads)
        }
        assertEquals(emptyList<String>(), TalentInvariants.violations(sc.system.state, sc.taxonomy, null, Tick(sc.day)))
        assertTrue(sc.workforce.rows.values.sumOf { it.heads } >= total)
    }

    @Test fun trainingRaisesProductivityOverAYear() {
        fun multiplier(train: Boolean): Long {
            val sc = Scenario()
            sc.run(5)
            if (train) sc.system.execute(TalentCommand.StartTraining(CompanyId(1), sc.machinists1, DefaultTaxonomy.MACHINING, ProgrammeKind.CERTIFICATION, 60L, 120), Tick(sc.day), sc.inputs(), sc.world)
            sc.run(360)
            return sc.system.skills.productivity(sc.machinists1)?.multiplierPpm ?: 0L
        }
        assertTrue(multiplier(true) > multiplier(false))
    }

    @Test fun peopleLeaveACountryThatFallsBehind() {
        val world = FixedTalentWorld()
            .region(1L, country = 1L, wage = Money.units(60), happiness = 350_000L)
            .region(2L, country = 1L, wage = Money.units(60), happiness = 350_000L)
            .region(3L, country = 2L, wage = Money.units(180), happiness = 800_000L)
        val sc = Scenario(3L, world)
        sc.run(1_080)
        assertTrue(sc.system.migration.ledger(CountryId(1)).out > 0L)
        assertTrue(sc.system.migration.ledger(CountryId(2)).arrived > 0L)
        assertEquals(emptyList<String>(), TalentInvariants.violations(sc.system.state, sc.taxonomy, null, Tick(sc.day)))
    }

    @Test fun closedBordersStopTheDrain() {
        val world = FixedTalentWorld()
            .region(1L, country = 1L, wage = Money.units(60), happiness = 350_000L)
            .region(2L, country = 1L, wage = Money.units(60), happiness = 350_000L)
            .region(3L, country = 2L, wage = Money.units(180), happiness = 800_000L)
        val sc = Scenario(3L, world)
        sc.system.execute(TalentCommand.SetImmigrationPolicy(CountryId(2), 0L, 0L, false), Tick(0), sc.inputs(), sc.world)
        sc.run(1_080)
        assertEquals(0L, sc.system.migration.ledger(CountryId(2)).arrived)
    }

    @Test fun aKeyExecutiveRetiresAndTheSuccessorTakesOver() {
        val sc = Scenario()
        val exec = RoleId("executive")
        val sys = sc.system
        sys.execute(TalentCommand.OpenPosition(CompanyId(1), exec, RegionId(1), "CEO"), Tick(0), sc.inputs(), sc.world)
        val pos = sys.succession.positionsOf(CompanyId(1)).single()
        val old = (sys.succession.addPerson(CompanyId(1), "Old", exec, RegionId(1), Money.units(400), 74, Tick(0)) as Outcome.Ok).value
        sys.succession.appoint(pos.id, old.id, Tick(0))
        val heir = (sys.succession.addPerson(CompanyId(1), "Heir", RoleId("manager"), RegionId(1), Money.units(300), 38, Tick(0), potentialPpm = 950_000L) as Outcome.Ok).value
        sys.succession.nameSuccessor(pos.id, heir.id, DevelopmentKind.EXECUTIVE_COURSE, Tick(0))
        val problems = sc.run(2_160)
        assertEquals(emptyList<String>(), problems)
        assertTrue(!old.isActive)
        assertTrue(sc.events.any { it is TalentEvent.PositionVacated })
        assertTrue(sc.events.any { it is TalentEvent.PositionFilled })
        assertTrue(!pos.isVacant)
    }

    @Test fun employerBrandingAndRecruitingPayOffForTheCompanyThatRunsThem() {
        val sc = Scenario()
        sc.system.execute(TalentCommand.LaunchCampaign(CompanyId(1), RegionId(1), RoleId("machinist"), Money.units(30_000), 180), Tick(0), sc.inputs(), sc.world)
        sc.run(360)
        val b1 = sc.system.brand.brand(CompanyId(1), RegionId(1))
        val b2 = sc.system.brand.brand(CompanyId(2), RegionId(1))
        assertTrue(b1 != null && b2 != null)
        assertTrue(b1!!.awarenessPpm >= b2!!.awarenessPpm)
    }

    @Test fun nothingNegativeEverAppearsInTheReports() {
        val sc = Scenario()
        repeat(900) {
            val r = sc.step()
            assertTrue(r.intents.all { it.total.raw > 0L })
            assertTrue(r.effects.none { (it is WorkforceEffect.Transfer && it.count <= 0L) || (it is WorkforceEffect.Hire && it.count <= 0L) })
        }
    }
}
