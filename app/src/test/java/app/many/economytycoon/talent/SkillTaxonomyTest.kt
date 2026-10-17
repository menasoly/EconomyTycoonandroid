package app.many.economytycoon.talent

import app.many.economytycoon.content.RoleDef
import app.many.economytycoon.content.SkillBand
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillTaxonomyTest {
    private val t = TalentTestSupport.taxonomy

    @Test fun everyShippedRoleHasAProfileWithKnownSkills() {
        for (def in SimTestSupport.content.allRoles) {
            val p = t.profile(def.id)
            assertNotNull("no profile for ${def.id.key}", p)
            for (r in p!!.requirements) assertTrue(t.hasSkill(r.skill))
        }
    }

    @Test fun primarySkillIsTheFirstRequirement() {
        assertEquals(DefaultTaxonomy.MACHINING, t.profile(RoleId("machinist"))!!.primary)
        assertEquals(DefaultTaxonomy.ENGINEERING, t.profile(RoleId("engineer"))!!.primary)
    }

    @Test fun higherBandsAskForMore() {
        val laborer = t.profile(RoleId("laborer"))!!.standardLevelPpm()
        val engineer = t.profile(RoleId("engineer"))!!.standardLevelPpm()
        val scientist = t.profile(RoleId("scientist"))!!.standardLevelPpm()
        assertTrue(laborer < engineer)
        assertTrue(engineer < scientist)
    }

    @Test fun tiersCoverTheScale() {
        assertEquals(SkillTier.NOVICE, SkillTier.of(0L))
        assertEquals(SkillTier.APPRENTICE, SkillTier.of(250_000L))
        assertEquals(SkillTier.COMPETENT, SkillTier.of(400_000L))
        assertEquals(SkillTier.PROFICIENT, SkillTier.of(799_999L))
        assertEquals(SkillTier.EXPERT, SkillTier.of(1_000_000L))
    }

    @Test fun rolesRequiringASkillIncludeSecondaryUse() {
        val ids = t.rolesRequiring(DefaultTaxonomy.COMMUNICATION).map { it.role.key }
        assertTrue("sales" in ids && "manager" in ids)
        assertTrue(t.rolesWithPrimary(DefaultTaxonomy.COMMUNICATION).isEmpty())
        assertTrue(DefaultTaxonomy.COMMUNICATION in t.secondarySkills())
        assertFalse(DefaultTaxonomy.MACHINING in t.secondarySkills())
    }

    @Test fun unknownContentRolesFallBackToGeneralWork() {
        val extra = RoleDef(RoleId("beekeeper"), "Beekeeper", SkillBand.SKILLED, Money.units(70), 20)
        val grown = t.withRolesFrom(listOf(extra))
        val p = grown.profile(RoleId("beekeeper"))!!
        assertEquals(DefaultTaxonomy.GENERAL, p.primary)
        assertEquals(450_000L, p.requirements.first().requiredPpm)
        assertNull(t.profile(RoleId("beekeeper")))
        assertTrue(grown.profile(RoleId("laborer")) != null)
    }

    @Test fun pathwaysSplitTheSkills() {
        assertTrue(DefaultTaxonomy.GENERAL in t.skillsOf(Pathway.SCHOOL).map { it.id })
        assertTrue(DefaultTaxonomy.ENGINEERING in t.skillsOf(Pathway.UNIVERSITY).map { it.id })
        assertTrue(DefaultTaxonomy.MACHINING in t.skillsOf(Pathway.VOCATIONAL).map { it.id })
        assertTrue(t.skillsOf(Pathway.EXPERIENCE).isNotEmpty())
    }

    @Test(expected = IllegalArgumentException::class) fun aProfileWithAnUnknownSkillIsRefused() {
        SkillTaxonomy(listOf(DefaultTaxonomy.skills.first()), listOf(DefaultTaxonomy.profileOf("machinist", "Machinist", SkillBand.SKILLED, 95, 45)))
    }

    @Test(expected = IllegalArgumentException::class) fun duplicateSkillsAreRefused() {
        SkillTaxonomy(listOf(DefaultTaxonomy.skills.first(), DefaultTaxonomy.skills.first()), emptyList())
    }

    @Test(expected = IllegalArgumentException::class) fun aRoleNeedsAtLeastOneSkill() {
        RoleProfile(RoleId("x"), "X", SkillBand.SKILLED, Money.units(1), 1, emptyList())
    }

    @Test fun keyRolesAreExecutivesAndManagers() {
        val keys = t.profiles().filter { it.keyRole }.map { it.role.key }
        assertEquals(listOf("executive", "manager"), keys)
    }

    @Test fun profileOfBuildsFromPlainNumbers() {
        val p = DefaultTaxonomy.profileOf("driver", "Driver", SkillBand.SKILLED, 80, 30)
        assertEquals(DefaultTaxonomy.DRIVING, p.primary)
        assertEquals(Money.units(80), p.baseWage)
    }
}
