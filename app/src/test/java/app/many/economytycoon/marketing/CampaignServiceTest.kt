package app.many.economytycoon.marketing

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.marketing.MarketingTestSupport.acme
import app.many.economytycoon.marketing.MarketingTestSupport.audiences
import app.many.economytycoon.marketing.MarketingTestSupport.bank
import app.many.economytycoon.marketing.MarketingTestSupport.beer
import app.many.economytycoon.marketing.MarketingTestSupport.book
import app.many.economytycoon.marketing.MarketingTestSupport.bread
import app.many.economytycoon.marketing.MarketingTestSupport.north
import app.many.economytycoon.marketing.MarketingTestSupport.rejection
import app.many.economytycoon.marketing.MarketingTestSupport.request
import app.many.economytycoon.marketing.MarketingTestSupport.rival
import app.many.economytycoon.marketing.MarketingTestSupport.south
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignServiceTest {
    private val s = MarketingTestSupport.system()
    private val svc = s.campaigns

    private fun create(r: CampaignRequest, tick: Long = 0L, cash: Money = bank) = svc.create(r, Tick(tick), cash, audiences())

    // --- booking ---

    @Test fun aBookingPaysTheCreativeFeeUpFront() {
        val b = create(request(daily = 500, tier = CreativeTier.PROFESSIONAL)).getOrThrow()
        assertEquals(1, b.intents.size)
        val i = b.intents[0]
        assertEquals(Money.units(1_000), i.total)
        assertEquals(LedgerOwner.company(acme), i.payer)
        assertEquals(s.ledger.agency, i.payee)
        assertEquals(Chart.MARKETING_EXPENSE, i.lines[0].payerDebit)
        assertEquals(Chart.REVENUE_SERVICES, i.lines[0].payeeCredit)
        assertTrue(!i.deferrable)
    }

    @Test fun anInHouseCreativeCostsNothingToBook() {
        val b = create(request(tier = CreativeTier.BASIC)).getOrThrow()
        assertTrue(b.intents.isEmpty())
        assertEquals(CreativeTier.BASIC.qualityPpm, b.campaign.creative.qualityPpm)
    }

    @Test fun televisionAsksForAWeekOfDepositUpFront() {
        val b = create(request(channel = Channel.TELEVISION, daily = 1_000, tier = CreativeTier.BASIC)).getOrThrow()
        assertEquals(Money.units(7_000), b.campaign.prepaid)
        assertEquals(Money.units(7_000), b.total)
    }

    @Test fun aShortBookingOnlyDepositsItsOwnDays() {
        val b = create(request(channel = Channel.TELEVISION, daily = 1_000, days = 5, tier = CreativeTier.BASIC)).getOrThrow()
        assertEquals(Money.units(5_000), b.campaign.prepaid)
    }

    @Test fun aNewCampaignStartsRunningTodayOrWaits() {
        assertEquals(CampaignStatus.ACTIVE, create(request()).getOrThrow().campaign.status)
        val later = create(request(channel = Channel.ONLINE, start = Tick(5))).getOrThrow().campaign
        assertEquals(CampaignStatus.SCHEDULED, later.status)
    }

    @Test fun idsAreAllocatedInOrder() {
        val a = create(request(channel = Channel.RADIO)).getOrThrow().campaign
        val b = create(request(channel = Channel.ONLINE)).getOrThrow().campaign
        assertEquals(CampaignId(1), a.id)
        assertEquals(CampaignId(2), b.id)
    }

    @Test fun aBookingCreatesTheBrandStock() {
        create(request())
        assertNotNull(s.state.stock(BrandKey(acme, beer, north)))
    }

    @Test fun rejectsBudgetsUnderTheChannelMinimum() {
        assertEquals(Rules.INVALID_ARGUMENT, rejection(create(request(channel = Channel.TELEVISION, daily = 100))))
    }

    @Test fun rejectsBudgetsOverTheChannelMaximum() {
        assertEquals(Rules.LIMIT_EXCEEDED, rejection(create(request(channel = Channel.LOCAL_PRESS, daily = 50_000))))
    }

    @Test fun rejectsCampaignsThatAreTooShortOrTooLong() {
        assertEquals(Rules.INVALID_ARGUMENT, rejection(create(request(days = 1))))
        assertEquals(Rules.INVALID_ARGUMENT, rejection(create(request(days = 1_000))))
    }

    @Test fun rejectsUnknownPlaces() {
        assertEquals(Rules.UNKNOWN_ENTITY, rejection(create(request(region = RegionId(77)))))
    }

    @Test fun rejectsStartsInThePast() {
        assertEquals(Rules.INVALID_ARGUMENT, rejection(create(request(start = Tick(2)), tick = 5)))
    }

    @Test fun rejectsWhenCashCannotCoverTheUpFrontPayment() {
        val r = create(request(channel = Channel.TELEVISION, daily = 1_000), cash = Money.units(100))
        assertEquals(Rules.INSUFFICIENT_FUNDS, rejection(r))
        assertTrue(s.state.campaigns().isEmpty())
    }

    @Test fun cashExactlyEqualToTheUpFrontPaymentIsEnough() {
        val r = create(request(channel = Channel.TELEVISION, daily = 1_000, tier = CreativeTier.BASIC), cash = Money.units(7_000))
        assertTrue(r is Outcome.Ok)
    }

    @Test fun refusesASecondCampaignInTheSameSlot() {
        create(request())
        assertEquals(Rules.DUPLICATE, rejection(create(request(daily = 300))))
    }

    @Test fun otherChannelsGoodsAndRegionsAreDifferentSlots() {
        create(request())
        assertTrue(create(request(channel = Channel.ONLINE)) is Outcome.Ok)
        assertTrue(create(request(good = bread)) is Outcome.Ok)
        assertTrue(create(request(region = south)) is Outcome.Ok)
        assertTrue(create(request(city = MarketingTestSupport.harbour)) is Outcome.Ok)
    }

    @Test fun differentCompaniesMayAdvertiseTheSameSlot() {
        create(request())
        assertTrue(create(request(company = rival)) is Outcome.Ok)
    }

    @Test fun anEndedCampaignFreesItsSlot() {
        val c = create(request()).getOrThrow().campaign
        svc.cancel(c.id, Tick(1))
        assertTrue(create(request(), tick = 1) is Outcome.Ok)
    }

    @Test fun limitsHowManyCampaignsACompanyRuns() {
        val tight = MarketingTestSupport.system(params = MarketingParams(maxCampaignsPerCompany = 2))
        tight.campaigns.create(request(channel = Channel.RADIO), Tick(0), bank, audiences())
        tight.campaigns.create(request(channel = Channel.ONLINE), Tick(0), bank, audiences())
        val third = tight.campaigns.create(request(channel = Channel.IN_STORE), Tick(0), bank, audiences())
        assertEquals(Rules.LIMIT_EXCEEDED, rejection(third))
    }

    @Test fun aRejectedBookingChangesNothing() {
        val before = s.stateHash()
        create(request(daily = 1))
        assertEquals(before, s.stateHash())
    }

    @Test fun aLongBookingEarnsADiscountOnEveryBill() {
        val c = create(request(daily = 1_000, days = 60, tier = CreativeTier.BASIC)).getOrThrow().campaign
        assertEquals(Money.units(940), svc.billFor(c))
        val short = create(request(channel = Channel.ONLINE, daily = 1_000, days = 59, tier = CreativeTier.BASIC)).getOrThrow().campaign
        assertEquals(Money.units(1_000), svc.billFor(short))
    }

    // --- pause and resume ---

    @Test fun pauseAndResume() {
        val c = book(s)
        assertEquals(CampaignStatus.PAUSED, svc.pause(c.id).getOrThrow().status)
        assertEquals(CampaignStatus.ACTIVE, svc.resume(c.id, Tick(1)).getOrThrow().status)
    }

    @Test fun onlyARunningCampaignCanBePaused() {
        val c = book(s)
        svc.pause(c.id)
        assertEquals(Rules.WRONG_STATE, rejection(svc.pause(c.id)))
        assertEquals(Rules.WRONG_STATE, rejection(svc.resume(book(s, request(channel = Channel.ONLINE)).id, Tick(0))))
    }

    @Test fun unknownCampaignsAreRefused() {
        assertEquals(Rules.UNKNOWN_ENTITY, rejection(svc.pause(CampaignId(9))))
        assertEquals(Rules.UNKNOWN_ENTITY, rejection(svc.cancel(CampaignId(9), Tick(0))))
        assertEquals(Rules.UNKNOWN_ENTITY, rejection(svc.extend(CampaignId(9), 3, Tick(0), bank)))
        assertEquals(Rules.UNKNOWN_ENTITY, rejection(svc.setBudget(CampaignId(9), Money.units(100), Tick(0), bank)))
        assertEquals(Rules.UNKNOWN_ENTITY, rejection(svc.refreshCreative(CampaignId(9), CreativeTier.BASIC, Tick(0), bank)))
        assertEquals(Rules.UNKNOWN_ENTITY, rejection(svc.voidBooking(CampaignId(9))))
    }

    // --- extend and budget ---

    @Test fun extendingAddsDays() {
        val c = book(s, request(days = 10))
        val b = svc.extend(c.id, 5, Tick(0), bank).getOrThrow()
        assertEquals(15, c.daysBooked)
        assertEquals(15, c.daysRemaining)
        assertTrue(b.intents.isEmpty())
    }

    @Test fun extendingATelevisionBookingTopsUpTheDeposit() {
        val c = book(s, request(channel = Channel.TELEVISION, daily = 1_000, days = 3, tier = CreativeTier.BASIC))
        assertEquals(Money.units(3_000), c.prepaid)
        val b = svc.extend(c.id, 10, Tick(0), bank).getOrThrow()
        assertEquals(Money.units(7_000), c.prepaid)
        assertEquals(Money.units(4_000), b.total)
    }

    @Test fun extensionBeyondTheMaximumIsRefused() {
        val c = book(s, request(days = 300))
        assertEquals(Rules.LIMIT_EXCEEDED, rejection(svc.extend(c.id, 100, Tick(0), bank)))
        assertEquals(300, c.daysBooked)
    }

    @Test fun aFailedExtensionRestoresTheBooking() {
        val c = book(s, request(channel = Channel.TELEVISION, daily = 1_000, days = 3, tier = CreativeTier.BASIC))
        assertEquals(Rules.INSUFFICIENT_FUNDS, rejection(svc.extend(c.id, 10, Tick(0), Money.units(10))))
        assertEquals(3, c.daysBooked)
        assertEquals(3, c.daysRemaining)
        assertEquals(Money.units(3_000), c.prepaid)
    }

    @Test fun extendingNeedsAPositiveNumberOfDays() {
        val c = book(s)
        assertEquals(Rules.INVALID_ARGUMENT, rejection(svc.extend(c.id, 0, Tick(0), bank)))
    }

    @Test fun anEndedCampaignCannotBeExtended() {
        val c = book(s)
        svc.cancel(c.id, Tick(0))
        assertEquals(Rules.WRONG_STATE, rejection(svc.extend(c.id, 5, Tick(0), bank)))
    }

    @Test fun changingTheBudgetRespectsTheLimits() {
        val c = book(s)
        assertEquals(Rules.INVALID_ARGUMENT, rejection(svc.setBudget(c.id, Money.units(10), Tick(0), bank)))
        assertEquals(Rules.LIMIT_EXCEEDED, rejection(svc.setBudget(c.id, Money.units(900_000), Tick(0), bank)))
        svc.setBudget(c.id, Money.units(800), Tick(0), bank).getOrThrow()
        assertEquals(Money.units(800), c.dailyBudget)
    }

    @Test fun raisingATelevisionBudgetAsksForMoreDeposit() {
        val c = book(s, request(channel = Channel.TELEVISION, daily = 1_000, tier = CreativeTier.BASIC))
        val b = svc.setBudget(c.id, Money.units(2_000), Tick(0), bank).getOrThrow()
        assertEquals(Money.units(7_000), b.total)
        assertEquals(Money.units(14_000), c.prepaid)
    }

    @Test fun aBudgetChangeWithoutCashIsUndone() {
        val c = book(s, request(channel = Channel.TELEVISION, daily = 1_000, tier = CreativeTier.BASIC))
        assertEquals(Rules.INSUFFICIENT_FUNDS, rejection(svc.setBudget(c.id, Money.units(2_000), Tick(0), Money.units(1))))
        assertEquals(Money.units(1_000), c.dailyBudget)
    }

    // --- cancellation ---

    @Test fun cancellingARadioCampaignHasNothingToRefund() {
        val c = book(s)
        val r = svc.cancel(c.id, Tick(1)).getOrThrow()
        assertEquals(Money.ZERO, r.refund)
        assertTrue(r.intents.isEmpty())
        assertEquals(CampaignStatus.CANCELLED, c.status)
        assertEquals(Tick(1), c.endedAt)
    }

    @Test fun cancellingEarlyCostsADoubleFee() {
        val c = book(s, request(channel = Channel.TELEVISION, daily = 1_000, tier = CreativeTier.BASIC))
        val (refund, fee) = svc.cancellationTerms(c)
        assertEquals(Money.units(3_500), refund)
        assertEquals(Money.units(3_500), fee)
    }

    @Test fun cancellingLaterCostsTheNormalFee() {
        val c = book(s, request(channel = Channel.SPONSORSHIP, daily = 1_000, days = 60, tier = CreativeTier.BASIC))
        run(c, 10)
        val (refund, fee) = svc.cancellationTerms(c)
        assertEquals(Channel.SPONSORSHIP.spec.cancelFeePpm, fee.raw * 1_000_000L / (fee.raw + refund.raw))
    }

    private fun run(c: Campaign, days: Int) { MarketingTestSupport.run(s, 0, days) }

    @Test fun cancellationReturnsTheRefundAsAnIntentFromTheAgency() {
        val c = book(s, request(channel = Channel.TELEVISION, daily = 1_000, tier = CreativeTier.BASIC))
        val r = svc.cancel(c.id, Tick(0)).getOrThrow()
        assertEquals(1, r.intents.size)
        val i = r.intents[0]
        assertEquals(s.ledger.agency, i.payer)
        assertEquals(LedgerOwner.company(acme), i.payee)
        assertEquals(r.refund, i.total)
        assertEquals(Chart.MARKETING_EXPENSE, i.lines[0].payeeCredit)
        assertEquals(Money.ZERO, c.prepaid)
        assertEquals(r.refund, c.refundedTotal)
        assertEquals(r.fee, c.feesTotal)
    }

    @Test fun aCampaignCannotBeCancelledTwice() {
        val c = book(s)
        svc.cancel(c.id, Tick(0))
        assertEquals(Rules.WRONG_STATE, rejection(svc.cancel(c.id, Tick(0))))
    }

    @Test fun feeNeverExceedsTheDeposit() {
        val hungry = MarketingTestSupport.system(params = MarketingParams(earlyCancelDays = 100))
        val c = book(hungry, request(channel = Channel.SPONSORSHIP, daily = 1_000, tier = CreativeTier.BASIC))
        val (refund, fee) = hungry.campaigns.cancellationTerms(c)
        assertTrue(refund.raw >= 0L && fee.raw >= 0L)
        assertEquals(c.prepaid, refund + fee)
    }

    // --- creative ---

    @Test fun refreshingRestoresFreshnessAndChargesFees() {
        val c = book(s)
        c.creative.freshnessPpm = 100_000L
        val b = svc.refreshCreative(c.id, CreativeTier.STUDIO, Tick(5), bank).getOrThrow()
        assertEquals(Ppm.ONE, c.creative.freshnessPpm)
        assertEquals(CreativeTier.STUDIO.qualityPpm, c.creative.qualityPpm)
        assertEquals(2, c.creative.version)
        assertEquals(Tick(5), c.creative.lastRefreshed)
        assertEquals(Money.units(500 * (5 + 3)), b.total)
    }

    @Test fun refreshesNeedACooldown() {
        val c = book(s)
        svc.refreshCreative(c.id, CreativeTier.BASIC, Tick(5), bank).getOrThrow()
        assertEquals(Rules.RATE_LIMITED, rejection(svc.refreshCreative(c.id, CreativeTier.BASIC, Tick(10), bank)))
        assertTrue(svc.refreshCreative(c.id, CreativeTier.BASIC, Tick(25), bank) is Outcome.Ok)
    }

    @Test fun refreshNeedsCash() {
        val c = book(s)
        assertEquals(Rules.INSUFFICIENT_FUNDS, rejection(svc.refreshCreative(c.id, CreativeTier.STUDIO, Tick(1), Money.units(10))))
        assertEquals(1, c.creative.version)
    }

    @Test fun refreshIsBlockedWhileATestRuns() {
        val c = book(s)
        s.experiments.start(c.id, CreativeTier.STUDIO, 10, Tick(0), bank).getOrThrow()
        assertEquals(Rules.WRONG_STATE, rejection(svc.refreshCreative(c.id, CreativeTier.BASIC, Tick(1), bank)))
    }

    // --- housekeeping ---

    @Test fun voidingAnUnrunBookingRemovesIt() {
        val c = book(s)
        svc.voidBooking(c.id).getOrThrow()
        assertNull(s.state.campaign(c.id))
    }

    @Test fun aCampaignThatHasRunCannotBeVoided() {
        val c = book(s)
        MarketingTestSupport.run(s, 0, 1)
        assertEquals(Rules.WRONG_STATE, rejection(svc.voidBooking(c.id)))
    }

    @Test fun closingACompanyEndsItsCampaigns() {
        book(s); book(s, request(channel = Channel.ONLINE)); book(s, request(company = rival))
        svc.endAllOf(acme, Tick(3))
        assertEquals(0, s.state.liveCampaignsOf(acme).size)
        assertEquals(1, s.state.liveCampaignsOf(rival).size)
    }

    @Test fun invariantsHoldAfterManyOperations() {
        val a = book(s)
        val t = book(s, request(channel = Channel.TELEVISION, daily = 2_000))
        svc.pause(a.id); svc.resume(a.id, Tick(1)); svc.extend(t.id, 10, Tick(1), bank); svc.setBudget(a.id, Money.units(700), Tick(1), bank)
        svc.cancel(t.id, Tick(2))
        assertEquals(emptyList<String>(), s.invariants())
    }
}
