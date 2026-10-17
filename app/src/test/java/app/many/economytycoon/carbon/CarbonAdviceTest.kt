package app.many.economytycoon.carbon

import app.many.economytycoon.advisor.Insight
import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.cyrus
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbonAdviceTest {
    private val none = CompanyCarbonView(acme, 2000, emptyList(), null, emptyMap(), emptyList(), emptyList(), emptyList(), emptyList())

    private fun account(
        kind: RegimeKind = RegimeKind.CAP_AND_TRADE, held: Long = 0, projected: Long = 1_000, expiring: Long = 0, price: Long = 20, pending: PendingView? = null,
    ) = AccountView(
        uk, kind, 2000, Tonnes.of(projected / 2), Tonnes.of(projected), Tonnes.of(projected), 180, Money.units(price), Tonnes.of(held), Money.ZERO,
        Tonnes.of(expiring), Tonnes.ZERO, 0, Tonnes.ZERO, CompliancePolicy(), pending, null,
    )

    private fun pending(outstanding: Long, held: Long, days: Int, allowance: Long = 0, credits: Long = 0) = PendingView(
        1999, Tonnes.of(outstanding), Tonnes.of(outstanding), Tonnes.ZERO, Tonnes.ZERO, Tonnes.ZERO, Tonnes.of(outstanding), Tick(0), days,
        Tonnes.of(held), Tonnes.of(allowance), Tonnes.of(credits),
    )

    private fun view(vararg a: AccountView) = none.copy(accounts = a.toList())
    private fun codes(list: List<Insight>) = list.map { it.code }

    @Test fun noAccountsMeansNoAdvice() = assertTrue(CarbonAdvice.insights(none, Money.units(1_000_000)).isEmpty())

    @Test fun anUnpricedCountryIsIgnored() {
        assertTrue(CarbonAdvice.insights(view(account(RegimeKind.NONE)), Money.units(1_000_000)).isEmpty())
    }

    @Test fun aShortfallIsFlaggedAndUrgentNearTheDeadline() {
        val near = CarbonAdvice.insights(view(account(pending = pending(500, 100, days = 10))), Money.units(10_000_000)).first { it.code == "carbon.shortfall" }
        val far = CarbonAdvice.insights(view(account(pending = pending(500, 100, days = 80))), Money.units(10_000_000)).first { it.code == "carbon.shortfall" }
        assertTrue(near.priority > far.priority)
        assertTrue(near.priority >= 90)
    }

    @Test fun coveredObligationsRaiseNoShortfall() {
        val out = CarbonAdvice.insights(view(account(pending = pending(500, 600, days = 10))), Money.units(10_000_000))
        assertTrue("carbon.shortfall" !in codes(out))
    }

    @Test fun creditsReduceTheShortfall() {
        val out = CarbonAdvice.insights(view(account(pending = pending(500, 100, days = 10, allowance = 400, credits = 400))), Money.units(10_000_000))
        assertTrue("carbon.shortfall" !in codes(out))
    }

    @Test fun smallShortfallsAreNotWorthMentioning() {
        val out = CarbonAdvice.insights(view(account(pending = pending(30, 0, days = 10))), Money.units(10_000_000))
        assertTrue("carbon.shortfall" !in codes(out))
    }

    @Test fun spareHoldingsAreFlagged() {
        val out = CarbonAdvice.insights(view(account(held = 2_000, projected = 1_000)), Money.units(10_000_000))
        assertTrue("carbon.surplus" in codes(out))
    }

    @Test fun aSnugHoldingIsNotSurplus() {
        val out = CarbonAdvice.insights(view(account(held = 1_100, projected = 1_000)), Money.units(10_000_000))
        assertTrue("carbon.surplus" !in codes(out))
    }

    @Test fun surplusAdviceOnlyAppliesToPermitMarkets() {
        val out = CarbonAdvice.insights(view(account(RegimeKind.CARBON_TAX, held = 2_000, projected = 1_000)), Money.units(10_000_000))
        assertTrue("carbon.surplus" !in codes(out))
    }

    @Test fun lapsingPermitsAreFlagged() {
        val out = CarbonAdvice.insights(view(account(expiring = 80)), Money.units(10_000_000))
        assertTrue("carbon.lapsing" in codes(out))
    }

    @Test fun aHeavyBillIsFlaggedAgainstRevenue() {
        val a = account(RegimeKind.CARBON_TAX, projected = 100_000, price = 20)
        assertTrue("carbon.exposure" in codes(CarbonAdvice.insights(view(a), Money.units(5_000_000))))
        assertTrue("carbon.exposure" !in codes(CarbonAdvice.insights(view(a), Money.units(5_000_000_000))))
    }

    @Test fun noRevenueMeansNoExposureAdvice() {
        val a = account(RegimeKind.CARBON_TAX, projected = 100_000)
        assertTrue("carbon.exposure" !in codes(CarbonAdvice.insights(view(a), Money.ZERO)))
    }

    @Test fun adviceIsSortedByPriorityThenCode() {
        val a = account(held = 5_000, projected = 1_000, expiring = 100, pending = pending(900, 0, days = 5))
        val out = CarbonAdvice.insights(view(a), Money.units(100_000))
        assertEquals(out.sortedWith(compareByDescending<Insight> { it.priority }.thenBy { it.code }), out)
        assertEquals("carbon.shortfall", out.first().code)
    }

    @Test fun everyInsightHasAValidPriority() {
        val a = account(held = 5_000, projected = 1_000, expiring = 100, pending = pending(900, 0, days = 5))
        for (i in CarbonAdvice.insights(view(a), Money.units(1))) assertTrue(i.priority in 0..100)
    }

    @Test fun adviceOnRealPositionsIsStableAndSane() {
        val fx = CarbonTestSupport.Fixture().also { CarbonTestSupport.busy(it, 365) }
        for (c in listOf(acme, borea, cyrus, CompanyId(99))) {
            val v = fx.views.company(c, Tick(365))
            val first = CarbonAdvice.insights(v, Money.units(2_000_000))
            assertEquals(first, CarbonAdvice.insights(v, Money.units(2_000_000)))
            assertEquals(first.map { it.code }.size, first.map { it.code + it.title }.distinct().size)
        }
    }

    @Test fun aWeakRatingIsFlaggedWithItsWeakestFactor() {
        val rating = CarbonRating(acme, 2000, 300, CarbonGrade.CCC, listOf(RatingFactor("Intensity", 10, 400, "far above the median"), RatingFactor("Disclosure", 100, 100, "ok")))
        val out = CarbonAdvice.insights(view(account()).copy(rating = rating), Money.units(10_000_000))
        val r = out.first { it.code == "carbon.rating" }
        assertTrue(r.detail.contains("intensity"))
    }

    @Test fun aStrongRatingIsLeftAlone() {
        val rating = CarbonRating(acme, 2000, 850, CarbonGrade.AA, listOf(RatingFactor("Intensity", 380, 400, "good")))
        assertTrue("carbon.rating" !in codes(CarbonAdvice.insights(view(account()).copy(rating = rating), Money.units(10_000_000))))
    }
}
