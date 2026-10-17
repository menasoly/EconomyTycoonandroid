package app.many.economytycoon.ui.present

import app.many.economytycoon.advisor.Insight
import app.many.economytycoon.advisor.Topic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketingScreenPresenterTest {
    private fun insight(code: String, priority: Int) = Insight(code, Topic.MARKET, priority, "Title $code", "Detail $code")

    @Test fun noInsightsMeansAHealthyHeadline() {
        val m = MarketingAdvicePresenter.build(emptyList())
        assertTrue(m.items.isEmpty())
        assertEquals("Your marketing looks healthy.", m.headline)
    }

    @Test fun oneInsightIsNamed() {
        val m = MarketingAdvicePresenter.build(listOf(insight("a", 55)))
        assertEquals("One suggestion: Title a", m.headline)
        assertEquals(Tone.WARN, m.items.single().tone)
    }

    @Test fun manyInsightsNameTheMostPressing() {
        val m = MarketingAdvicePresenter.build(listOf(insight("a", 80), insight("b", 30), insight("c", 40)))
        assertEquals("3 suggestions. Most pressing: Title a", m.headline)
        assertEquals(listOf(Tone.BAD, Tone.NEUTRAL, Tone.INFO), m.items.map { it.tone })
    }

    @Test fun theListIsLimited() {
        val m = MarketingAdvicePresenter.build(List(20) { insight("c$it", 50) }, limit = 5)
        assertEquals(5, m.items.size)
        assertTrue(m.headline.startsWith("20 suggestions"))
    }

    @Test fun tabsHaveDistinctLabels() {
        assertEquals(MarketingTab.values().size, MarketingTab.values().map { it.label }.toSet().size)
        assertEquals("Campaigns", MarketingTab.CAMPAIGNS.label)
    }
}
