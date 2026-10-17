package app.many.economytycoon.marketing

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.marketing.MarketingTestSupport.acme
import app.many.economytycoon.marketing.MarketingTestSupport.beer
import app.many.economytycoon.marketing.MarketingTestSupport.book
import app.many.economytycoon.marketing.MarketingTestSupport.bread
import app.many.economytycoon.marketing.MarketingTestSupport.north
import app.many.economytycoon.marketing.MarketingTestSupport.request
import app.many.economytycoon.marketing.MarketingTestSupport.rival
import app.many.economytycoon.marketing.MarketingTestSupport.run
import app.many.economytycoon.marketing.MarketingTestSupport.south
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionTest {
    private val params = MarketingParams()
    private val s = MarketingTestSupport.system()
    private val audience = Audience.even(north, 500_000L)

    private fun duel(mine: Long = 400, theirs: Long = 6_000) {
        book(s, request(daily = mine, days = 90, tier = CreativeTier.BASIC))
        book(s, request(company = rival, daily = theirs, days = 90, tier = CreativeTier.BASIC))
        run(s, 0, 30)
    }

    @Test fun voiceSharesSumToAboutOne() {
        duel()
        val v = Competition.voice(s.state, beer, north)
        assertEquals(2, v.entries.size)
        assertTrue(Math.abs(v.entries.sumOf { it.sharePpm } - 1_000_000L) < 5L)
    }

    @Test fun theBiggerSpenderHoldsMoreOfTheVoice() {
        duel()
        val v = Competition.voice(s.state, beer, north)
        assertTrue(v.of(rival)!!.sharePpm > v.of(acme)!!.sharePpm)
        assertEquals(rival, v.leader()!!.company)
        assertEquals(Money.units(6_400), v.totalSpend)
    }

    @Test fun anEmptyMarketHasNoVoice() {
        val v = Competition.voice(s.state, beer, south)
        assertTrue(v.entries.isEmpty())
        assertNull(v.leader())
    }

    @Test fun marketsListEveryPlaceWithABrand() {
        book(s, request(daily = 400, tier = CreativeTier.BASIC))
        book(s, request(good = bread, region = south, daily = 400, tier = CreativeTier.BASIC))
        assertEquals(listOf(MarketKey(beer, north), MarketKey(bread, south)), Competition.markets(s.state))
    }

    @Test fun theLoudestRivalIsFoundOnlyWhenSomeoneIsLouder() {
        duel()
        val v = Competition.voice(s.state, beer, north)
        assertEquals(rival, Competition.loudestRival(v, acme)!!.company)
        assertNull(Competition.loudestRival(v, rival))
    }

    @Test fun aCounterCampaignAnswersTheGapOnAFreeChannel() {
        duel()
        val p = Competition.counter(s.state, acme, beer, north, audience, params, Money.units(5_000))
        assertNotNull(p)
        assertEquals(rival, p!!.rival)
        assertTrue(p.channel != Channel.RADIO)
        assertTrue(p.dailyBudget >= p.channel.spec.minDailySpend)
        assertTrue(p.dailyBudget <= p.channel.spec.maxDailySpend)
        assertTrue(p.gapPpm > 0L)
        assertTrue(p.reason.isNotBlank())
    }

    @Test fun noCounterWhenYouAreTheLoudest() {
        duel(mine = 6_000, theirs = 400)
        assertNull(Competition.counter(s.state, acme, beer, north, audience, params, Money.units(5_000)))
    }

    @Test fun noCounterWhenThereIsNoMoneyForIt() {
        duel()
        assertNull(Competition.counter(s.state, acme, beer, north, audience, params, Money.units(1)))
    }

    @Test fun theCounterBudgetIsAFractionOfTheSpendGap() {
        duel(mine = 400, theirs = 6_000)
        val p = Competition.counter(s.state, acme, beer, north, audience, params, Money.units(50_000), responsePpm = 500_000L)!!
        assertTrue(p.dailyBudget <= Money.units(2_800) || p.dailyBudget == p.channel.spec.minDailySpend)
    }

    @Test fun aCompanyWithNoBrandStillGetsAnAnswer() {
        duel()
        val p = Competition.counter(s.state, CompanyId(9), beer, north, audience, params, Money.units(5_000))
        assertNotNull(p)
    }
}
