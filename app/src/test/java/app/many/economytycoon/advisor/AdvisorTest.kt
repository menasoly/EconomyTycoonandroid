package app.many.economytycoon.advisor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorTest {
    private val busy = AdvisorFacts(
        tick = 50, monthRevenueUnits = 100_000, monthNetIncomeUnits = -2_000, netMarginPpm = -20_000, vacancies = 2, idleParcels = 1,
        facilities = listOf(FacilityFact(1, "Mill", true, 800_000, 900_000, null, 10, 0)), industryCount = 2, netWorthUnits = 400_000,
    )

    @Test fun suggestionsComeMostPressingFirst() {
        val a = Advisor().advise(busy)
        assertEquals(listOf("loss-making", "understaffed", "idle-land"), a.map { it.code })
        assertEquals(a.map { it.priority }, a.map { it.priority }.sortedDescending())
    }

    @Test fun equalPrioritiesBreakByCode() {
        val rules = listOf(
            AdvisorRule { _, _ -> listOf(Insight("b", Topic.MARKET, 40, "B", "d")) },
            AdvisorRule { _, _ -> listOf(Insight("a", Topic.MARKET, 40, "A", "d")) },
        )
        assertEquals(listOf("a", "b"), Advisor(rules).advise(AdvisorFacts()).map { it.code })
    }

    @Test fun theListCanBeCapped() {
        assertEquals(2, Advisor().advise(busy, limit = 2).size)
    }

    @Test fun hidingSetsASuggestionAsideForAWhile() {
        val adv = Advisor()
        adv.hide("loss-making", tick = 50, days = 10)
        assertTrue(adv.isHidden("loss-making", 55)); assertTrue(adv.isHidden("loss-making", 59))
        assertFalse(adv.isHidden("loss-making", 60))
        assertFalse("loss-making" in adv.advise(busy).map { it.code })
        assertTrue("loss-making" in adv.advise(busy.copy(tick = 60)).map { it.code })
        assertTrue("loss-making" in adv.all(busy).map { it.code })
    }

    @Test fun hiddenCodesListOnlyThoseStillHidden() {
        val adv = Advisor()
        adv.hide("a", 0, 5); adv.hide("b", 0, 50)
        assertEquals(listOf("b"), adv.hiddenCodes(10))
        assertEquals(listOf("a", "b"), adv.hiddenCodes(1))
    }

    @Test fun unhidingBringsBackOneOrAll() {
        val adv = Advisor()
        adv.hide("loss-making", 50); adv.hide("idle-land", 50)
        adv.unhide("loss-making")
        assertTrue("loss-making" in adv.advise(busy).map { it.code }); assertFalse("idle-land" in adv.advise(busy).map { it.code })
        adv.unhideAll()
        assertTrue("idle-land" in adv.advise(busy).map { it.code })
    }

    @Test fun aZeroDayHideStillLastsADay() {
        val adv = Advisor()
        adv.hide("x", 10, 0)
        assertTrue(adv.isHidden("x", 10)); assertFalse(adv.isHidden("x", 11))
    }

    @Test fun nothingToSayWhenAllIsWell() {
        val calm = AdvisorFacts(facilities = listOf(FacilityFact(1, "Mill", true, 800_000, 900_000, null, 10, 0)), industryCount = 2, netWorthUnits = 400_000, averageMoralePpm = 700_000)
        assertTrue(Advisor().advise(calm).isEmpty())
    }
}
