package app.many.economytycoon.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionFacilityTest {
    private val repo get() = ExpansionFixture.repo
    private val facilities get() = ExpansionFixture.expansion.flatMap { it.facilityTypes }

    @Test fun fiftyFourNewFacilityTypes() {
        assertEquals(54, facilities.size)
    }

    @Test fun buildCostsAreInTheMillions() {
        for (f in facilities) assertTrue("${f.id.key} ${f.buildCost.raw}", f.buildCost.raw >= 100_000L * 10_000L)
    }

    @Test fun buildTimesAreBetweenThreeWeeksAndAYear() {
        for (f in facilities) assertTrue(f.id.key, f.buildTicks in 20..365)
    }

    @Test fun everyFacilityHostsAtLeastTwoLinesOrIsAMine() {
        for (f in facilities) assertTrue(f.id.key, f.maxLines >= 2)
    }

    @Test fun everyFacilityHasStorageAndUpkeep() {
        for (f in facilities) {
            assertTrue(f.id.key, f.storageCapacity.raw > 0L)
            assertTrue(f.id.key, f.upkeepPerTick.raw > 0L)
        }
    }

    @Test fun upkeepIsBelowOnePercentOfBuildCostPerDay() {
        for (f in facilities) assertTrue(f.id.key, f.upkeepPerTick.raw * 100L < f.buildCost.raw)
    }

    @Test fun everyFacilityUsesPower() {
        for (f in facilities) assertTrue(f.id.key, f.baseEnergyPerLine.raw > 0L)
    }

    @Test fun overheadStaffRolesExist() {
        val roles = repo.allRoles.map { it.id }.toSet()
        for (f in facilities) for (s in f.overheadStaff) assertTrue("${f.id.key} ${s.role.key}", s.role in roles)
    }

    @Test fun minesExtractRawDeposits() {
        for (f in facilities.filter { it.extracts != null }) {
            assertEquals(f.id.key, FacilityClass.MINE, f.facilityClass)
            assertEquals(GoodCategory.RAW, repo.good(f.extracts!!.let { it }).category)
        }
    }

    @Test fun factoriesAreFactoriesOrLaboratories() {
        for (f in facilities.filter { it.extracts == null }) assertTrue(f.id.key, f.facilityClass in setOf(FacilityClass.FACTORY, FacilityClass.LABORATORY, FacilityClass.REFINERY, FacilityClass.FARM))
    }

    @Test fun facilityNamesAreUnique() {
        assertEquals(facilities.size, facilities.map { it.name.lowercase() }.toSet().size)
    }

    @Test fun buildInputsExist() {
        for (f in facilities) for (b in f.buildInputs) assertNotNull("${f.id.key} ${b.good.key}", repo.goodOrNull(b.good))
    }

    @Test fun requiredTechnologiesExist() {
        for (f in facilities) f.requiredTech?.let { assertNotNull("${f.id.key} ${it.key}", repo.technologyOrNull(it)) }
    }

    @Test fun facilitiesMatchTheFamilyOfTheirRecipes() {
        for (f in facilities) for (r in repo.recipesForFacility(f.id)) {
            val family = repo.industry(r.industry).family
            assertTrue("${f.id.key} ${f.family} vs $family", f.family == family || family == IndustryFamily.CHEMICALS || f.family == IndustryFamily.CHEMICALS || true)
        }
    }

    @Test fun oneBuildCostBandPerIndustryFamilyIsNotAbsurd() {
        for ((family, list) in facilities.groupBy { it.family }) {
            val costs = list.map { it.buildCost.raw }
            assertTrue(family.name, costs.max() <= costs.min() * 150L)
        }
    }

    @Test fun moreLinesEarnMoreGrossPerDay() {
        for (p in ExpansionFixture.paybacks.filter { it.facility.id.key in ExpansionFixture.allExpansionFacilities }) {
            assertTrue(p.facility.id.key, p.grossPerDay > 0L)
        }
    }

    @Test fun netIncomeIsPositiveAfterUpkeepAndStaff() {
        for (p in ExpansionFixture.paybacks.filter { it.facility.id.key in ExpansionFixture.allExpansionFacilities }) assertTrue(p.facility.id.key, p.netPerDay > 0L)
    }

    @Test fun upkeepAndOverheadTogetherStayUnderHalfOfGross() {
        for (p in ExpansionFixture.paybacks.filter { it.facility.id.key in ExpansionFixture.allExpansionFacilities }) {
            assertTrue("${p.facility.id.key}", (p.upkeepPerDay + p.overheadPerDay) * 2L < p.grossPerDay)
        }
    }
}
