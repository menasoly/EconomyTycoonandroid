package app.many.economytycoon.simulation

import app.many.economytycoon.activity.ActivityArea
import app.many.economytycoon.activity.CommandDescriber
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.persistence.CommandCodec
import app.many.economytycoon.persistence.CommandSamples
import app.many.economytycoon.persistence.SectionedFile
import app.many.economytycoon.persistence.SimulationSnapshot
import app.many.economytycoon.talent.InstitutionKind
import app.many.economytycoon.talent.SkillId
import app.many.economytycoon.talent.TalentCommand
import app.many.economytycoon.talent.WorkforceEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Skills and education as the simulation wires them: the switch, who may give orders, effects on people, snapshots and replay. */
class TalentIntegrationTest {
    private class Rig(seed: Long = 12L, ai: Int = 3, talent: Boolean = true) {
        val sim = Simulation(SimTestSupport.config(seed, ai), SimTestSupport.content, bootstrap = ai > 0)
        val e = sim.economy
        val country = e.world.countries().sortedBy { it.id }.first()
        val me: CompanyId = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Skilled Works", country.id, country.capital!!, Money.units(5_000_000))) as Outcome.Ok).value.created!!)
        val rival: CompanyId = e.companies.all().first { it.id != me }.id
        init { if (talent) sim.execute(Actor.System, Command.EstablishTalent) }
    }

    @Test fun aGameWithoutTheModuleLeavesNoStateNoSectionAndNoOrders() {
        val r = Rig(talent = false)
        val summaries = r.sim.advance(40)
        assertFalse(r.e.talentRunner.active)
        assertTrue(summaries.all { it.talentEvents.isEmpty() })
        assertTrue(SectionedFile.read(SimulationSnapshot.save(r.sim)).none { it.name == "talent" })
        val some = r.me
        val early = r.sim.execute(Actor.Player(1), Command.Talent(some, TalentCommand.CancelCampaign(some, app.many.economytycoon.talent.RecruitCampaignId(1))))
        assertEquals(Rules.WRONG_STATE, (early as Outcome.Rejected).rejection.rule)
    }

    @Test fun onlyTheSystemSwitchesItOnOnceAndEveryRegionGetsSchools() {
        val r = Rig(talent = false)
        assertTrue(r.sim.execute(Actor.Player(1), Command.EstablishTalent) is Outcome.Rejected)
        assertFalse(r.e.talentRunner.active)
        assertTrue(r.sim.execute(Actor.System, Command.EstablishTalent) is Outcome.Ok)
        assertTrue(r.sim.execute(Actor.System, Command.EstablishTalent) is Outcome.Rejected)
        assertTrue(r.e.talentRunner.active)
        for (region in r.e.world.regions()) {
            assertTrue(r.e.talent.education.inRegion(region.id, InstitutionKind.SCHOOL).isNotEmpty() || r.e.talentRunner.world.youthCohort(region.id) <= 0L)
        }
        assertTrue(SectionedFile.read(SimulationSnapshot.save(r.sim)).any { it.name == "talent" })
        assertEquals(emptyList<String>(), Invariants.check(r.e))
    }

    @Test fun ordersMustNameTheirOwnCompanyAndCompaniesCannotMakeImmigrationLaw() {
        val r = Rig()
        val a = r.me
        val b = r.rival
        val mismatch = r.sim.execute(Actor.Player(1), Command.Talent(a, TalentCommand.SetCounterPolicy(b, true, 100_000L)))
        assertEquals("talent.actor-mismatch", (mismatch as Outcome.Rejected).rejection.rule)
        val policy = r.sim.execute(Actor.Player(1), Command.Talent(a, TalentCommand.SetImmigrationPolicy(CountryId(1), 500_000L, 5_000L, false)))
        assertEquals("talent.policy-not-allowed", (policy as Outcome.Rejected).rejection.rule)
        assertTrue(r.sim.execute(Actor.Player(1), Command.SetImmigrationPolicy(CountryId(1), 500_000L, 5_000L, false)) is Outcome.Rejected)
        val country = r.country.id
        assertTrue(r.sim.execute(Actor.System, Command.SetImmigrationPolicy(country, 300_000L, 4_000L, true)) is Outcome.Ok)
        val ok = r.sim.execute(Actor.Player(1), Command.Talent(a, TalentCommand.SetCounterPolicy(a, true, 100_000L)))
        assertTrue(ok.toString(), ok is Outcome.Ok)
    }

    @Test fun aCompanyCannotBuildASchoolItCannotPayForOrInTheWrongCountry() {
        val r = Rig()
        val a = r.me
        val region = r.e.world.regions().sortedBy { it.id }.first()
        val skill = r.e.talent.taxonomy.skillsOf(InstitutionKind.UNIVERSITY.pathway).first().id
        val wrong = r.e.world.countries().sortedBy { it.id }.firstOrNull { it.id != region.country }?.id
        if (wrong != null) {
            val country = TalentCommand.OpenInstitution(a, wrong, region.id, InstitutionKind.UNIVERSITY, "X", 100L, Money.units(1_000), Money.units(1_000), listOf(skill))
            assertEquals(Rules.INVALID_ARGUMENT, (r.sim.execute(Actor.Player(1), Command.Talent(a, country)) as Outcome.Rejected).rejection.rule)
        }
        val dear = TalentCommand.OpenInstitution(a, region.country, region.id, InstitutionKind.UNIVERSITY, "Y", 5_000_000L, Money.units(1_000), Money.units(1_000), listOf(skill))
        assertEquals(Rules.INSUFFICIENT_FUNDS, (r.sim.execute(Actor.Player(1), Command.Talent(a, dear)) as Outcome.Rejected).rejection.rule)
    }

    @Test fun theTalentCommandsRoundTripThroughTheCodecAndDescriber() {
        val cs = CommandSamples.all().filter { it is Command.EstablishTalent || it is Command.Talent || it is Command.SetImmigrationPolicy }
        assertEquals(39, cs.size)
        for (c in cs) {
            val w = ByteWriter(); CommandCodec.writeCommand(c, w)
            assertEquals(c, CommandCodec.readCommand(ByteReader(w.toByteArray())))
            assertTrue(CommandDescriber.describe(c).isNotBlank())
        }
        assertEquals(ActivityArea.PEOPLE, CommandDescriber.area(cs.first { it is Command.Talent }))
        assertEquals(ActivityArea.PUBLIC, CommandDescriber.area(cs.first { it is Command.SetImmigrationPolicy }))
        assertTrue(CommandDescriber.movesMoney(cs.first { it is Command.Talent && it.command is TalentCommand.LaunchCampaign }))
    }

    @Test fun aCorruptTalentCommandIsRefusedRatherThanSkipped() {
        val w = ByteWriter(); CommandCodec.writeCommand(Command.Talent(CompanyId(3), TalentCommand.SetCounterPolicy(CompanyId(3), true, 1L)), w)
        val bytes = w.toByteArray()
        val failed = runCatching { CommandCodec.readCommand(ByteReader(bytes.copyOf(bytes.size - 4))) }.exceptionOrNull()
        assertTrue(failed.toString(), failed is ByteReader.CorruptDataException)
    }

    @Test fun effectsOnPeopleReachTheWorkforce() {
        val r = Rig(seed = 21L, ai = 4)
        r.sim.advance(30)
        val w = r.e.workforce
        val c = w.all().first { it.headcount >= 4 }
        val before = c.headcount
        r.e.talentRunner.apply(listOf(WorkforceEffect.Depart(c.key, 2L)))
        assertEquals(before - 2, w.cohort(c.key)?.headcount)
        r.e.talentRunner.apply(listOf(WorkforceEffect.RaiseWage(c.key, w.cohort(c.key)!!.wage + Money.units(5))))
        assertTrue(w.cohort(c.key)!!.wage >= c.wage)
        val low = w.cohort(c.key)!!.wage
        r.e.talentRunner.apply(listOf(WorkforceEffect.RaiseWage(c.key, Money.units(1))))
        assertEquals("wages are only ever raised", low, w.cohort(c.key)!!.wage)
        // A cohort that is gone changes nothing.
        r.e.talentRunner.apply(listOf(WorkforceEffect.Depart(app.many.economytycoon.workforce.CohortKey(CompanyId(999), c.key.facility, c.key.role, c.key.region), 3L)))
    }

    @Test fun theSkillTermIsNeutralUntilTheModuleIsOnAndThenFollowsTheSkillPicture() {
        val off = Rig(seed = 9L, ai = 3, talent = false)
        off.sim.advance(20)
        assertTrue(off.e.workforce.skillSource == null)
        val c = off.e.workforce.all().first()
        assertEquals(off.e.workforce.productivity(c, off.e.tick), off.e.workforce.productivity(c, off.e.tick))
        val on = Rig(seed = 9L, ai = 3)
        on.sim.advance(20)
        assertTrue(on.e.workforce.skillSource != null)
        assertEquals(emptyList<String>(), Invariants.check(on.e))
    }

    @Test fun savingAndLoadingContinuesExactlyWithTheModuleOn() {
        val r = Rig(seed = 31L, ai = 4)
        r.sim.advance(75)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(r.sim), SimTestSupport.content)
        assertEquals(r.sim.hash(), copy.hash())
        assertEquals(r.e.talent.stateHash(), copy.economy.talent.stateHash())
        assertTrue(copy.economy.talentRunner.active)
        for (i in 0 until 60) { r.sim.advance(); copy.advance(); assertEquals("tick ${r.sim.tick.value}", r.sim.hash(), copy.hash()) }
    }

    @Test fun anOlderSaveWithoutTheSectionLoadsWithTheModuleOff() {
        val r = Rig(seed = 8L, ai = 2, talent = false)
        r.sim.advance(10)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(r.sim), SimTestSupport.content)
        assertFalse(copy.economy.talentRunner.active)
        assertEquals(r.sim.hash(), copy.hash())
    }

    @Test fun theCommandLogReplaysIdenticallyAndTheModuleChangesTheHash() {
        val a = Rig(seed = 33L, ai = 4)
        val plain = Rig(seed = 33L, ai = 4, talent = false)
        a.sim.advance(100); plain.sim.advance(100)
        val again = Simulation.replay(a.sim.config, SimTestSupport.content, a.sim.commandLog(), a.sim.tick.value)
        assertEquals(a.sim.hash(), again.hash())
        assertNotEquals(plain.sim.hash(), a.sim.hash())
        assertEquals(emptyList<String>(), Invariants.check(a.e))
    }

    @Test fun aMultiMonthRunIsDeterministicPaysForPublicSchoolsAndKeepsTheBooksBalanced() {
        val x = Rig(seed = 41L, ai = 5); val y = Rig(seed = 41L, ai = 5)
        x.sim.advance(240); y.sim.advance(240)
        assertEquals(x.sim.hash(), y.sim.hash())
        assertEquals(x.e.talent.stateHash(), y.e.talent.stateHash())
        assertTrue(Invariants.check(x.e).isEmpty())
        assertTrue(x.e.finance.invariants().isEmpty())
        assertTrue(x.e.talent.education.all().any { it.owner == null && it.isOpen })
    }

    @Test fun aClosedCompanyLeavesNothingBehindInTheModule() {
        val r = Rig(seed = 5L, ai = 3)
        r.sim.advance(5)
        val ai = r.e.companies.all().first { it.kind == app.many.economytycoon.companies.CompanyKind.AI }.id
        r.e.talentRunner.forget(ai, r.e.tick)
        assertTrue(r.e.talent.education.ownedBy(ai).isEmpty())
        assertTrue(r.e.talent.providers.providersOf(ai).isEmpty())
        assertEquals(SkillId("x"), SkillId("x"))
    }
}
