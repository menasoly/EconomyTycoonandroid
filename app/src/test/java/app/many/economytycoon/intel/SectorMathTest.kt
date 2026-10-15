package app.many.economytycoon.intel

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SectorMathTest {
    private val wheat = GoodId("wheat")
    private val flour = GoodId("flour")
    private fun make(company: Long, facility: Long, good: GoodId, perDay: Long, util: Long = 1_000_000L) =
        LineFlow(CompanyId(company), FacilityId(facility), good, perDay, util, true)
    private fun use(company: Long, facility: Long, good: GoodId, perDay: Long, util: Long = 1_000_000L) =
        LineFlow(CompanyId(company), FacilityId(facility), good, perDay, util, false)

    private fun sectors(vararg flows: LineFlow, ratio: Long = 1_000_000L) =
        SectorMath.build(flows.toList(), { "Company ${it.value}" }, { ratio })

    @Test fun actualOutputFollowsUtilisation() {
        assertEquals(500L, make(1, 1, wheat, 1_000, 500_000).actualPerDay)
        assertEquals(1_000L, make(1, 1, wheat, 1_000, 5_000_000).actualPerDay)
        assertEquals(0L, make(1, 1, wheat, 1_000, -4).actualPerDay)
    }

    @Test fun sharesAreOfTotalCapacity() {
        val s = sectors(make(1, 1, wheat, 3_000), make(2, 2, wheat, 1_000)).single()
        assertEquals(4_000L, s.capacityPerDay)
        assertEquals(750_000L, s.shareOf(CompanyId(1)))
        assertEquals(250_000L, s.shareOf(CompanyId(2)))
        assertEquals(0L, s.shareOf(CompanyId(3)))
    }

    @Test fun producersAreRankedLargestFirstWithTiesByCompany() {
        val s = sectors(make(2, 2, wheat, 1_000), make(1, 1, wheat, 1_000), make(3, 3, wheat, 5_000)).single()
        assertEquals(listOf(3L, 1L, 2L), s.producers.map { it.company.value })
        assertEquals(1, s.rankOf(CompanyId(3)))
        assertEquals(3, s.rankOf(CompanyId(2)))
        assertNull(s.rankOf(CompanyId(9)))
        assertEquals(CompanyId(3), s.leader!!.company)
    }

    @Test fun aCompanysLinesAreAddedTogether() {
        val s = sectors(make(1, 1, wheat, 1_000), make(1, 1, wheat, 1_000), make(1, 2, wheat, 500)).single()
        val p = s.producers.single()
        assertEquals(2_500L, p.capacityPerDay)
        assertEquals(2, p.facilities)
        assertEquals(1_000_000L, p.sharePpm)
    }

    @Test fun oneSellerIsAMonopoly() {
        assertEquals(Concentration.MONOPOLY, sectors(make(1, 1, wheat, 1_000)).single().concentration)
        assertEquals(10_000L, sectors(make(1, 1, wheat, 1_000)).single().hhi)
    }

    @Test fun concentrationFollowsTheIndexBands() {
        val even4 = sectors(make(1, 1, wheat, 100), make(2, 2, wheat, 100), make(3, 3, wheat, 100), make(4, 4, wheat, 100)).single()
        assertEquals(2_500L, even4.hhi)
        assertEquals(Concentration.CONCENTRATED, even4.concentration)
        val even8 = sectors(*(1L..8L).map { make(it, it, wheat, 100) }.toTypedArray()).single()
        assertEquals(1_250L, even8.hhi)
        assertEquals(Concentration.COMPETITIVE, even8.concentration)
        val six = sectors(*(1L..6L).map { make(it, it, wheat, 100) }.toTypedArray()).single()
        assertEquals(Concentration.MODERATE, six.concentration)
    }

    @Test fun aGoodOnlyUsedHasNoProducers() {
        val s = sectors(use(1, 1, flour, 400)).single()
        assertEquals(Concentration.NONE, s.concentration)
        assertEquals(Balance.UNMADE, s.balance)
        assertEquals(400L, s.industryUsePerDay)
        assertNull(s.leader)
    }

    @Test fun balanceComparesWhatIsMadeWithWhatIndustryTakes() {
        fun bal(made: Long, used: Long) = sectors(make(1, 1, wheat, made), use(2, 2, wheat, used)).single().balance
        assertEquals(Balance.SURPLUS, bal(2_000, 1_000))
        assertEquals(Balance.SHORT, bal(1_000, 2_000))
        assertEquals(Balance.BALANCED, bal(1_000, 1_000))
        assertEquals(Balance.BALANCED, bal(1_100, 1_000))
        assertEquals(Balance.UNUSED, sectors(make(1, 1, wheat, 1_000)).single().balance)
    }

    @Test fun balanceUsesWhatLinesActuallyDo() {
        val s = sectors(make(1, 1, wheat, 1_000, 250_000), use(2, 2, wheat, 1_000)).single()
        assertEquals(Balance.SHORT, s.balance)
    }

    @Test fun openingsNeedFewSellersAndAHighPrice() {
        val dear = sectors(make(1, 1, wheat, 1_000), ratio = 1_400_000L).single()
        assertTrue(dear.isOpening())
        assertFalse(sectors(make(1, 1, wheat, 1_000), ratio = 1_000_000L).single().isOpening())
        val crowded = sectors(make(1, 1, wheat, 1_000), make(2, 2, wheat, 1_000), make(3, 3, wheat, 1_000), ratio = 1_400_000L).single()
        assertFalse(crowded.isOpening())
    }

    @Test fun eachGoodGetsItsOwnSectorInOrder() {
        val all = sectors(make(1, 1, wheat, 100), make(1, 1, flour, 100), use(1, 1, wheat, 50))
        assertEquals(listOf("flour", "wheat"), all.map { it.good.key })
    }

    @Test fun priceRatioIsCarriedThrough() {
        assertEquals(1_250_000L, sectors(make(1, 1, wheat, 100), ratio = 1_250_000L).single().priceRatioPpm)
    }

    @Test fun namesComeFromTheLookup() {
        val s = SectorMath.build(listOf(make(7, 1, wheat, 100)), { "Seven Co" }, { 1_000_000L }).single()
        assertEquals("Seven Co", s.producers.single().name)
    }
}
