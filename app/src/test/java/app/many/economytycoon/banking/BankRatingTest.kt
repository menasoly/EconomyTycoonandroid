package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.CreditRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankRatingTest {
    private val params = BankingFx.params
    private val p = params.rating

    private fun items(b: Bank): List<BankScoreItem> {
        val s = b.snapshot()
        return BankRatingModel.breakdown(s, CapitalEngine.position(s, params.capital), LiquidityEngine.position(s, params.liquidity, params.deposits::insuredShare))
    }

    private fun review(b: Bank, current: CreditRating, dir: Int = 0, count: Int = 0): RatingReview {
        val s = b.snapshot()
        return BankRatingModel.review(s, CapitalEngine.position(s, params.capital), LiquidityEngine.position(s, params.liquidity, params.deposits::insuredShare), current, dir, count, p)
    }

    @Test fun everyFactorIsScoredOnce() {
        val i = items(BankingFx.bank())
        assertEquals(BankScoreFactor.values().toList(), i.map { it.factor })
    }

    @Test fun pointsStayWithinEachFactorsRange() {
        val i = items(BankingFx.bank())
        for (x in i) assertTrue("${x.factor}", x.points <= x.best && x.points >= x.worst)
        assertTrue(i.all { it.headroom >= 0 })
    }

    @Test fun scoreIsBoundedAndAddsBase() {
        val i = items(BankingFx.bank())
        val score = BankRatingModel.score(i, p.base)
        assertEquals((p.base + i.sumOf { it.points }).coerceIn(0, 1000), score)
        assertEquals(1000, BankRatingModel.score(listOf(BankScoreItem(BankScoreFactor.CAPITAL, 5_000, 5_000, 0)), 900))
        assertEquals(0, BankRatingModel.score(listOf(BankScoreItem(BankScoreFactor.CAPITAL, -5_000, 0, -5_000)), 100))
    }

    @Test fun lossesLowerTheScore() {
        val b = BankingFx.bank()
        val before = BankRatingModel.score(items(b), p.base)
        val loss = b.snapshot().cet1Capital.times(Rate.percent(70))
        b.reserves -= loss; b.retained -= loss
        assertTrue(BankRatingModel.score(items(b), p.base) < before)
    }

    @Test fun supervisionAndRunsLowerTheScore() {
        val b = BankingFx.bank()
        val before = BankRatingModel.score(items(b), p.base)
        b.applyConfidence(200_000L, RunStage.PANIC)
        b.setRung(SupervisoryRung.SEVERE, Tick(1))
        assertTrue(BankRatingModel.score(items(b), p.base) < before - 100)
    }

    @Test fun staysPutWhenTheTargetMatches() {
        val b = BankingFx.bank()
        val target = BankRatingModel.score(items(b), p.base).let { app.many.economytycoon.finance.CreditScoring.target(it) }
        val r = review(b, target)
        assertEquals(target, r.rating)
        assertEquals(0, r.count)
        
    }

    @Test fun resolvedBanksAreRatedD() {
        val b = BankingFx.bank()
        b.changeStatus(BankStatus.LIQUIDATED, Tick(1))
        assertEquals(CreditRating.D, review(b, CreditRating.A).rating)
        val c = BankingFx.bank(2)
        c.changeStatus(BankStatus.IN_RESOLUTION, Tick(1))
        assertEquals(CreditRating.D, review(c, CreditRating.A).rating)
    }

    @Test fun downgradesComeFasterThanUpgrades() {
        val bad = BankingFx.bank()
        val loss = bad.snapshot().cet1Capital.times(Rate.percent(80))
        bad.reserves -= loss; bad.retained -= loss
        var rating = CreditRating.AA
        var dir = 0; var count = 0; var downgradeAfter = 0
        for (i in 1..10) {
            val r = review(bad, rating, dir, count)
            rating = r.rating; dir = r.direction; count = r.count
            if (rating != CreditRating.AA) { downgradeAfter = i; break }
        }
        val good = BankingFx.bank()
        var up = CreditRating.B
        dir = 0; count = 0
        var upgradeAfter = 0
        for (i in 1..10) {
            val r = review(good, up, dir, count)
            up = r.rating; dir = r.direction; count = r.count
            if (up != CreditRating.B) { upgradeAfter = i; break }
        }
        assertTrue(downgradeAfter in 1..10 && upgradeAfter in 1..10)
        assertTrue(downgradeAfter <= upgradeAfter)
    }

    @Test fun ratingMovesOneNotchAtATime() {
        val b = BankingFx.bank()
        val loss = b.snapshot().cet1Capital.times(Rate.percent(90))
        b.reserves -= loss; b.retained -= loss
        var rating = CreditRating.AA
        var dir = 0; var count = 0
        repeat(40) {
            val r = review(b, rating, dir, count)
            assertTrue(kotlin.math.abs(r.rating.ordinal - rating.ordinal) <= 1)
            rating = r.rating; dir = r.direction; count = r.count
        }
        assertTrue(rating.ordinal > CreditRating.AA.ordinal)
    }

    @Test fun counterResetsAfterAChange() {
        val b = BankingFx.bank()
        val loss = b.snapshot().cet1Capital.times(Rate.percent(90))
        b.reserves -= loss; b.retained -= loss
        var r = review(b, CreditRating.AA)
        while (r.rating == CreditRating.AA) r = review(b, CreditRating.AA, r.direction, r.count)
        assertEquals(0, r.count)
        assertEquals(0, r.direction)
        assertTrue(Money.ZERO == Money.ZERO)
    }
}
