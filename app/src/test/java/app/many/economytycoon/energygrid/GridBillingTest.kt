package app.many.economytycoon.energygrid

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridBillingTest {
    private fun world(params: GridParams = GridParams(operatorPlans = false)): GridWorld {
        val w = GridWorld(params = params)
        w.unit(Technology.COAL, 100_000L, owner = w.owner)
        w.unit(Technology.GAS, 150_000L)
        w.households(60_000L)
        w.facility(40_000L)
        return w
    }

    private fun settle(w: GridWorld): BillingRun {
        w.days(30)
        return w.service.settleMonth(Tick(30))
    }

    @Test fun everyConnectedCustomerIsBilledForWhatItWasServed() {
        val w = world()
        w.days(30)
        val wholesale = GridAnalytics.windowAveragePrice(w.grid.history, 30)
        val expected = w.grid.loads.associate { l ->
            val q = Tariffs.quote(w.grid.tariff, l.customer, wholesale, w.grid.capacityLevy)
            l.id to (Tariffs.energyBill(q, l.monthServedKwh) + Tariffs.fixedCharge(w.grid.tariff, l.baseKw))
        }
        val run = w.service.settleMonth(Tick(30))
        for (l in w.grid.loads) {
            val intent = run.intents.single { it.key == "energy:bill:${w.grid.id.value}:1:${l.id.value}" }
            assertEquals(expected.getValue(l.id), intent.total)
            assertEquals(w.grid.systemOwner, intent.payee)
            assertEquals(ReasonCode.ENERGY, intent.reason)
        }
    }

    @Test fun companiesPayAsCompaniesAndHouseholdsAsHouseholds() {
        val w = world()
        val run = settle(w)
        val bills = run.intents.filter { it.key.startsWith("energy:bill:") }
        assertEquals(setOf(LedgerOwner.company(w.buyer), w.grid.householdOwner), bills.map { it.payer }.toSet())
        val homes = bills.single { it.payer == w.grid.householdOwner }
        assertFalse(homes.deferrable)
        assertTrue(bills.single { it.payer == LedgerOwner.company(w.buyer) }.deferrable)
    }

    @Test fun generatorsArePaidWhatThePoolOwesThem() {
        val w = world()
        w.days(30)
        val owed = w.grid.units.associate { it.id to it.poolRevenue }
        val run = w.service.settleMonth(Tick(30))
        for (u in w.grid.units) {
            val intent = run.intents.single { it.key == "energy:pool:${w.grid.id.value}:1:${u.id.value}" }
            assertEquals(owed.getValue(u.id), intent.total)
            assertEquals(w.grid.systemOwner, intent.payer)
            val payee = if (u.owner != null) LedgerOwner.company(u.owner) else LedgerOwner.government(CountryId(1))
            assertEquals(payee, intent.payee)
        }
    }

    @Test fun capacityAwardsArePaidForTheMonth() {
        val w = world()
        w.days(30)
        val round = w.grid.capacityRound ?: error("no auction")
        val run = w.service.settleMonth(Tick(30))
        val paid = run.intents.filter { it.key.startsWith("energy:capacity:") }
        assertEquals(round.awards.count { CapacityMarket.dailyPayment(it.kw, round.clearingPrice).raw > 0L }, paid.size)
        assertEquals(Money.sum(paid.map { it.total }), round.dailyPayment() * 30)
        assertTrue(paid.all { it.reason == ReasonCode.SUBSIDY })
    }

    @Test fun theStatementBalancesBillsAgainstPayments() {
        val w = world()
        val run = settle(w)
        val st = run.statementOf(w.grid.id) ?: error("no statement")
        assertEquals(st.billed + st.capCompensation - st.poolPaid - st.capacityPaid, st.retailerResult)
        val billed = run.intents.filter { it.key.startsWith("energy:bill:") }.fold(Money.ZERO) { a, i -> a + i.total }
        assertEquals(billed, st.billed)
        assertTrue(st.servedKwh > 0L)
        assertEquals(30, w.grid.history.size)
    }

    @Test fun monthlyCountersStartOverAfterBilling() {
        val w = world()
        settle(w)
        assertTrue(w.grid.loads.all { it.monthServedKwh == 0L && it.monthShedKwh == 0L })
        assertTrue(w.grid.units.all { it.soldKwh == 0L && it.poolRevenue == Money.ZERO })
        assertEquals(0L, w.grid.monthDemandKwh)
    }

    @Test fun aMonthIsBilledOnlyOnce() {
        val w = world()
        settle(w)
        val ex = runCatching { w.service.settleMonth(Tick(35)) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        w.days(30)
        assertTrue(w.service.settleMonth(Tick(60)).intents.isNotEmpty())
    }

    @Test fun everyIntentIsPositiveBetweenTwoDifferentParties() {
        val w = world()
        val run = settle(w)
        assertTrue(run.intents.isNotEmpty())
        for (i in run.intents) {
            assertTrue(i.total.raw > 0L)
            assertTrue(i.payer != i.payee)
        }
        assertEquals(run.intents.size, run.intents.map { it.key }.toSet().size)
    }

    @Test fun interruptibleCustomersPayLessPerKilowattHour() {
        val w = GridWorld()
        w.unit(Technology.GAS, 300_000L)
        val firm = w.facility(20_000L, CustomerClass.INDUSTRIAL)
        val flex = w.facility(20_000L, CustomerClass.INTERRUPTIBLE, company = CompanyId(300))
        w.days(30)
        val run = w.service.settleMonth(Tick(30))
        val firmBill = run.intents.single { it.key.endsWith(":${firm.id.value}") && it.key.contains("bill") }.total
        val flexBill = run.intents.single { it.key.endsWith(":${flex.id.value}") && it.key.contains("bill") }.total
        assertTrue(flexBill < firmBill)
    }

    // ----- the household price cap -----

    @Test fun aCapLimitsHouseholdBillsAndTheGovernmentMakesTheRetailerWhole() {
        val w = world()
        w.service.setHouseholdCap(w.grid.id, Money.units(20))
        w.days(30)
        val homes = w.grid.loads.single { it.kind == LoadKind.HOUSEHOLDS }
        val served = homes.monthServedKwh
        val run = w.service.settleMonth(Tick(30))
        val bill = run.intents.single { it.key == "energy:bill:${w.grid.id.value}:1:${homes.id.value}" }
        assertEquals(GridUnits.cost(Money.units(20), served) + Tariffs.fixedCharge(w.grid.tariff, homes.baseKw), bill.total)
        val comp = run.intents.single { it.key == "energy:cap:${w.grid.id.value}:1" }
        assertEquals(LedgerOwner.government(CountryId(1)), comp.payer)
        assertEquals(w.grid.systemOwner, comp.payee)
        assertTrue(comp.total.raw > 0L)
        assertEquals(comp.total, run.statementOf(w.grid.id)?.capCompensation)
    }

    @Test fun withoutCompensationTheShortfallBuildsUp() {
        val w = world(GridParams(operatorPlans = false, compensateCap = false))
        w.service.setHouseholdCap(w.grid.id, Money.units(20))
        val run = settle(w)
        assertTrue(run.intents.none { it.key.startsWith("energy:cap:") })
        assertTrue(w.grid.capShortfall.raw > 0L)
    }

    @Test fun noCapMeansNoCompensation() {
        val w = world()
        val run = settle(w)
        assertTrue(run.intents.none { it.key.startsWith("energy:cap:") })
        assertEquals(Money.ZERO, w.grid.capShortfall)
    }

    // ----- power purchase agreements -----

    private fun ppaWorld(strike: Long, volume: Long, days: Int = 30, penalty: Long = 200_000L): Pair<GridWorld, Ppa> {
        val w = world()
        val p = w.service.offerPpa(
            w.grid.id, w.owner, w.buyer, PpaProfile.BASELOAD, volume, 0L, null, Money.units(strike), Tick(0), days, penalty, Tick(0),
        ).getOrThrow()
        w.service.acceptPpa(p.id, w.buyer, Tick(0)).getOrThrow()
        return w to p
    }

    @Test fun anAgreementAccruesDeliveredEnergyEveryHour() {
        val (w, p) = ppaWorld(strike = 80, volume = 30_000L)
        w.days(10)
        assertEquals(30_000L * 24 * 10, p.deliveredKwh)
        assertEquals(0L, p.shortfallKwh)
        assertEquals(10, p.daysDelivering)
    }

    @Test fun aSettlementPaysTheStrikeAgainstThePoolDifference() {
        val (w, p) = ppaWorld(strike = 80, volume = 30_000L)
        w.days(30)
        val strikeValue = GridUnits.cost(Money.units(80), p.deliveredKwh)
        val market = p.marketValue
        val run = w.service.settleMonth(Tick(30))
        val intent = run.intents.single { it.key == "energy:ppa:${p.id.value}:1" }
        assertEquals(strikeValue - market, intent.total)
        // strike above the pool price: the buyer pays the seller
        assertEquals(LedgerOwner.company(w.buyer), intent.payer)
        assertEquals(LedgerOwner.company(w.owner), intent.payee)
        assertEquals(0L, p.deliveredKwh)
        assertEquals(Money.ZERO - (strikeValue - market), p.netPaidBySeller)
    }

    @Test fun whenThePoolPaysMoreTheSellerPaysTheBuyer() {
        val (w, p) = ppaWorld(strike = 20, volume = 30_000L)
        w.days(30)
        val run = w.service.settleMonth(Tick(30))
        val intent = run.intents.single { it.key == "energy:ppa:${p.id.value}:1" }
        assertEquals(LedgerOwner.company(w.owner), intent.payer)
        assertEquals(LedgerOwner.company(w.buyer), intent.payee)
    }

    @Test fun fallingShortOfABaseloadCostsPenalties() {
        // the coal unit is 100 MW, the agreement asks for all of it, but demand leaves it running below that
        val (w, p) = ppaWorld(strike = 50, volume = 100_000L, penalty = 500_000L)
        w.days(30)
        val shortfall = p.shortfallKwh
        val shortValue = p.shortfallMarketValue
        assertTrue(shortfall > 0L)
        val strikeOnShort = GridUnits.cost(Money.units(50), shortfall)
        val expectedPenalty = Money(strikeOnShort.raw / 2) + (shortValue - strikeOnShort).coerceAtLeast(Money.ZERO)
        val run = w.service.settleMonth(Tick(30))
        val pen = run.intents.single { it.key == "energy:ppa:pen:${p.id.value}:1" }
        assertEquals(LedgerOwner.company(w.owner), pen.payer)
        assertEquals(LedgerOwner.company(w.buyer), pen.payee)
        assertEquals(ReasonCode.FEE, pen.reason)
        assertTrue(Math.abs(pen.total.raw - expectedPenalty.raw) <= 2L)
    }

    @Test fun shortfallsAreReportedAsEvents() {
        val (w, p) = ppaWorld(strike = 50, volume = 100_000L)
        val r = w.day()
        assertTrue(r.eventsOfType<GridEvent.PpaShortfall>().any { it.ppa == p.id })
    }

    @Test fun anAgreementCompletesAtItsEnd() {
        val (w, p) = ppaWorld(strike = 50, volume = 10_000L, days = 30)
        w.days(30)
        w.service.settleMonth(Tick(30))
        assertEquals(PpaStatus.COMPLETED, p.status)
        assertEquals(30L, p.closedAt)
        w.days(5)
        assertEquals(0L, p.deliveredKwh)
    }

    @Test fun anAsProducedAgreementFollowsTheUnit() {
        val w = world()
        val wind = w.unit(Technology.WIND, 60_000L, owner = w.owner)
        val p = w.service.offerPpa(
            w.grid.id, w.owner, w.buyer, PpaProfile.AS_PRODUCED, 0L, 500_000L, wind.id, Money.units(60), Tick(0), 60, 0L, Tick(0),
        ).getOrThrow()
        w.service.acceptPpa(p.id, w.buyer, Tick(0))
        w.days(10)
        assertTrue(p.deliveredKwh > 0L)
        assertTrue(p.deliveredKwh <= wind.lifetimeKwh)
        assertEquals(0L, p.shortfallKwh)
    }

    @Test fun terminatedAgreementsStillSettleWhatTheyAccrued() {
        val (w, p) = ppaWorld(strike = 80, volume = 30_000L, days = 90)
        w.days(10)
        w.service.terminatePpa(p.id, w.buyer, Tick(10))
        w.days(20)
        val run = w.service.settleMonth(Tick(30))
        assertTrue(run.intents.any { it.key == "energy:ppa:${p.id.value}:1" })
        assertEquals(30_000L * 24 * 10, p.totalDeliveredKwh)
    }

    // ----- capital -----

    @Test fun totalOfARunIsTheSumOfItsIntents() {
        val w = world()
        val run = settle(w)
        assertEquals(Money.sum(run.intents.map { it.total }), run.total)
        assertEquals(1L, run.month)
    }
}
