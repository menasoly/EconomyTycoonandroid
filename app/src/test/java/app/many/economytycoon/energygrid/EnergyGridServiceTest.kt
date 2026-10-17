package app.many.economytycoon.energygrid

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.OwnerKind
import app.many.economytycoon.world.ClimateZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyGridServiceTest {
    private fun rule(o: Outcome<*>) = o.rejectionOrNull()?.rule

    // ----- grids -----

    @Test fun gridsGetSequentialIdsAndOnePerRegion() {
        val s = EnergyGridService()
        val a = s.addGrid(RegionId(1), CountryId(1), "North", ClimateZone.COLD).getOrThrow()
        val b = s.addGrid(RegionId(2), CountryId(1), "South", ClimateZone.ARID).getOrThrow()
        assertEquals(GridId(1), a.id)
        assertEquals(GridId(2), b.id)
        assertEquals(Rules.DUPLICATE, rule(s.addGrid(RegionId(1), CountryId(1), "Again", ClimateZone.COLD)))
        assertEquals(a, s.state.gridOfRegion(RegionId(1)))
    }

    @Test fun aGridNeedsAName() {
        assertEquals(Rules.INVALID_ARGUMENT, rule(EnergyGridService().addGrid(RegionId(1), CountryId(1), "  ", ClimateZone.COLD)))
    }

    @Test fun aGridsSystemAccountBelongsToIt() {
        val w = GridWorld()
        assertEquals(OwnerKind.SYSTEM, w.grid.systemOwner.kind)
        assertEquals(w.grid.id.value, w.grid.systemOwner.id)
        assertEquals(OwnerKind.HOUSEHOLDS, w.grid.householdOwner.kind)
    }

    @Test fun regulatorSettingsApplyToTheGrid() {
        val w = GridWorld()
        w.service.setHouseholdCap(w.grid.id, Money.units(55))
        assertEquals(Money.units(55), w.grid.tariff.householdCapPerMwh)
        w.service.setHouseholdCap(w.grid.id, null)
        assertNull(w.grid.tariff.householdCapPerMwh)
        assertEquals(Rules.INVALID_PRICE, rule(w.service.setHouseholdCap(w.grid.id, Money.ZERO)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(w.service.setHouseholdCap(GridId(99), Money.units(5))))
        w.service.setParams(w.grid.id, GridParams(priceCap = Money.units(300)))
        assertEquals(Money.units(300), w.grid.params.priceCap)
        w.service.setTariff(w.grid.id, TariffSchedule(retailMarginPerMwh = Money.units(9)))
        assertEquals(Money.units(9), w.grid.tariff.retailMarginPerMwh)
    }

    @Test fun buildableTechnologiesRoundTripThroughTheMask() {
        val p = GridParams().withBuildable(setOf(Technology.GAS, Technology.HYDRO))
        assertEquals(setOf(Technology.GAS, Technology.HYDRO), p.buildable())
        assertTrue(Technology.WIND in GridParams().buildable() && Technology.HYDRO !in GridParams().buildable())
    }

    // ----- units -----

    @Test fun addedUnitsAreAvailableAndStaggeredForMaintenance() {
        val w = GridWorld()
        val a = w.unit(Technology.GAS, 50_000L)
        val b = w.unit(Technology.GAS, 50_000L)
        assertEquals(UnitStatus.AVAILABLE, a.status)
        assertTrue(a.lastMaintenance != b.lastMaintenance)
        assertTrue(a.lastMaintenance <= 0L)
        assertEquals(listOf(a, b), w.grid.units)
        assertEquals(100_000L, w.grid.installedKw())
    }

    @Test fun aUnitNeedsCapacityAndAKnownGrid() {
        val w = GridWorld()
        assertEquals(Rules.INVALID_QUANTITY, rule(w.service.addUnit(w.grid.id, Technology.GAS, 0L, null, Tick(0))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(w.service.addUnit(GridId(9), Technology.GAS, 10L, null, Tick(0))))
    }

    @Test fun aGridHoldsABoundedNumberOfUnits() {
        val s = EnergyGridService(limits = GridLimits(maxUnitsPerGrid = 2))
        val g = s.addGrid(RegionId(1), CountryId(1), "G", ClimateZone.TEMPERATE).getOrThrow()
        s.addUnit(g.id, Technology.GAS, 10L, null, Tick(0))
        s.addUnit(g.id, Technology.GAS, 10L, null, Tick(0))
        assertEquals(Rules.LIMIT_EXCEEDED, rule(s.addUnit(g.id, Technology.GAS, 10L, null, Tick(0))))
    }

    @Test fun orderedUnitsTakeTheBuildTimeAndReturnAPayment() {
        val w = GridWorld()
        val order = w.service.orderUnit(w.grid.id, Technology.GAS, 100_000L, w.owner, Tick(10)).getOrThrow()
        assertEquals(UnitStatus.BUILDING, order.unit.status)
        assertEquals(10L + Technology.GAS.profile.buildTicks, order.unit.statusUntil)
        // 75 a kilowatt
        assertEquals(Money.units(7_500_000), order.cost)
        assertEquals(order.cost, order.payment.total)
        assertEquals(app.many.economytycoon.finance.LedgerOwner.company(w.owner), order.payment.payer)
        assertEquals(w.grid.systemOwner, order.payment.payee)
        assertEquals(0L, w.grid.installedKw())
        assertEquals(CapacityMarket.firmKw(Technology.GAS, 100_000L), w.grid.firmUnderConstructionKw())
    }

    @Test fun publicUnitsAreOrderedByTheGovernment() {
        val w = GridWorld()
        val order = w.service.orderUnit(w.grid.id, Technology.WIND, 10_000L, null, Tick(0)).getOrThrow()
        assertEquals(app.many.economytycoon.finance.LedgerOwner.government(CountryId(1)), order.payment.payer)
        assertNull(order.unit.owner)
    }

    @Test fun retiredUnitsStayOnRecordButStopCounting() {
        val w = GridWorld()
        val u = w.unit(Technology.COAL, 50_000L)
        w.service.retireUnit(u.id).getOrThrow()
        assertEquals(UnitStatus.RETIRED, u.status)
        assertEquals(0L, w.grid.installedKw())
        assertEquals(Rules.WRONG_STATE, rule(w.service.retireUnit(u.id)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(w.service.retireUnit(UnitId(77))))
        assertEquals(1, w.grid.units.size)
    }

    @Test fun mothballedUnitsCanBeRestartedAfterTwoWeeks() {
        val w = GridWorld()
        val u = w.unit(Technology.COAL, 50_000L)
        w.service.mothball(u.id).getOrThrow()
        assertEquals(0L, w.grid.firmKw())
        assertEquals(Rules.WRONG_STATE, rule(w.service.mothball(u.id)))
        w.service.restart(u.id, Tick(5)).getOrThrow()
        assertEquals(UnitStatus.MAINTENANCE, u.status)
        assertEquals(5L + EnergyGridService.RESTART_DAYS, u.statusUntil)
        assertEquals(Rules.WRONG_STATE, rule(w.service.restart(u.id, Tick(5))))
    }

    @Test fun ageStartsAtCommissioning() {
        val w = GridWorld()
        val u = w.unit(Technology.GAS, 1_000L)
        assertEquals(100L, u.ageAt(100L))
        val b = w.service.orderUnit(w.grid.id, Technology.GAS, 1_000L, null, Tick(0)).getOrThrow().unit
        assertEquals(0L, b.ageAt(50L))
    }

    // ----- loads -----

    @Test fun facilitiesConnectOnceAndKeepTheirClass() {
        val w = GridWorld()
        val l = w.service.connectFacility(w.grid.id, CustomerClass.COMMERCIAL, w.buyer, FacilityId(7), 5_000L).getOrThrow()
        assertEquals(LoadKind.FACILITY, l.kind)
        assertEquals(l, w.service.loadOfFacility(FacilityId(7)))
        assertEquals(Rules.DUPLICATE, rule(w.service.connectFacility(w.grid.id, CustomerClass.COMMERCIAL, w.buyer, FacilityId(7), 5_000L)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(w.service.connectFacility(w.grid.id, CustomerClass.HOUSEHOLD, w.buyer, FacilityId(8), 5_000L)))
        assertEquals(Rules.INVALID_QUANTITY, rule(w.service.connectFacility(w.grid.id, CustomerClass.COMMERCIAL, w.buyer, FacilityId(9), -1L)))
    }

    @Test fun aFacilityCannotBeOnTwoGrids() {
        val w = GridWorld()
        val other = w.otherGrid()
        w.service.connectFacility(w.grid.id, CustomerClass.INDUSTRIAL, w.buyer, FacilityId(1), 1L)
        assertEquals(Rules.DUPLICATE, rule(w.service.connectFacility(other.id, CustomerClass.INDUSTRIAL, w.buyer, FacilityId(1), 1L)))
    }

    @Test fun householdsHaveOneConnectionPerGridThatIsUpdatedInPlace() {
        val w = GridWorld()
        val a = w.households(1_000L)
        val b = w.households(2_500L)
        assertEquals(a.id, b.id)
        assertEquals(2_500L, a.baseKw)
        assertEquals(1, w.grid.loads.size)
        assertEquals(CustomerClass.HOUSEHOLD, a.customer)
    }

    @Test fun loadsCanBeResizedReclassifiedAndDisconnected() {
        val w = GridWorld()
        val l = w.facility(1_000L)
        w.service.setLoadKw(l.id, 4_000L).getOrThrow()
        assertEquals(4_000L, l.baseKw)
        assertEquals(Rules.INVALID_QUANTITY, rule(w.service.setLoadKw(l.id, -5L)))
        val r = w.service.reclassify(l.id, CustomerClass.INTERRUPTIBLE).getOrThrow()
        assertEquals(CustomerClass.INTERRUPTIBLE, r.customer)
        assertEquals(4_000L, r.baseKw)
        assertEquals(r, w.grid.load(l.id))
        assertEquals(Rules.INVALID_ARGUMENT, rule(w.service.reclassify(l.id, CustomerClass.HOUSEHOLD)))
        w.service.setConnected(l.id, false)
        assertEquals(0L, w.grid.connectedBaseKw())
        val facility = l.facility ?: error("a facility load")
        w.service.disconnectFacility(facility).getOrThrow()
        assertTrue(w.grid.loads.isEmpty())
        assertEquals(Rules.UNKNOWN_ENTITY, rule(w.service.disconnectFacility(facility)))
    }

    @Test fun householdLoadsCannotBeReclassified() {
        val w = GridWorld()
        assertEquals(Rules.INVALID_ARGUMENT, rule(w.service.reclassify(w.households(1_000L).id, CustomerClass.COMMERCIAL)))
    }

    // ----- links -----

    @Test fun linksJoinTwoDifferentGridsOnlyOnce() {
        val w = GridWorld()
        val b = w.otherGrid()
        val l = w.service.addLink(w.grid.id, b.id, 50_000L, 20_000L, Money.units(1)).getOrThrow()
        assertEquals(listOf(l), w.state.linksOf(b.id))
        assertEquals(Rules.DUPLICATE, rule(w.service.addLink(b.id, w.grid.id, 10L, 0L, Money.ZERO)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(w.service.addLink(w.grid.id, w.grid.id, 10L, 0L, Money.ZERO)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(w.service.addLink(w.grid.id, GridId(9), 10L, 0L, Money.ZERO)))
        assertEquals(Rules.INVALID_QUANTITY, rule(w.service.addLink(w.grid.id, b.id, 0L, 0L, Money.ZERO)))
    }

    @Test fun linkTermsAreValidated() {
        val w = GridWorld()
        val b = w.otherGrid()
        assertEquals(Rules.INVALID_ARGUMENT, rule(w.service.addLink(w.grid.id, b.id, 10L, 600_000L, Money.ZERO)))
        assertEquals(Rules.INVALID_PRICE, rule(w.service.addLink(w.grid.id, b.id, 10L, 0L, Money(-1))))
    }

    @Test fun linksCanBeUpgradedAndTakenOutOfService() {
        val w = GridWorld()
        val b = w.otherGrid()
        val l = w.service.addLink(w.grid.id, b.id, 50_000L, 0L, Money.ZERO).getOrThrow()
        w.service.upgradeLink(l.id, 25_000L).getOrThrow()
        assertEquals(75_000L, l.capacityKw)
        assertEquals(Rules.INVALID_QUANTITY, rule(w.service.upgradeLink(l.id, 0L)))
        w.service.setLinkInService(l.id, false)
        assertEquals(0L, l.view().capacityKw)
        assertEquals(Rules.UNKNOWN_ENTITY, rule(w.service.upgradeLink(LinkId(9), 1L)))
    }

    // ----- agreements -----

    private fun ppaWorld(): GridWorld {
        val w = GridWorld()
        w.unit(Technology.GAS, 100_000L, owner = w.owner)
        w.facility(10_000L)
        return w
    }

    private fun offer(w: GridWorld, volume: Long = 50_000L, strike: Long = 50, start: Long = 5L, days: Int = 90, tick: Long = 0L) =
        w.service.offerPpa(w.grid.id, w.owner, w.buyer, PpaProfile.BASELOAD, volume, 0L, null, Money.units(strike), Tick(start), days, 200_000L, Tick(tick))

    @Test fun aSellerOffersAndABuyerAccepts() {
        val w = ppaWorld()
        val p = offer(w).getOrThrow()
        assertEquals(PpaStatus.OFFERED, p.status)
        assertEquals(95L, p.endsAt)
        assertEquals(Rules.NOT_OWNER, rule(w.service.acceptPpa(p.id, w.owner, Tick(1))))
        w.service.acceptPpa(p.id, w.buyer, Tick(1)).getOrThrow()
        assertEquals(PpaStatus.ACTIVE, p.status)
        assertEquals(Rules.WRONG_STATE, rule(w.service.acceptPpa(p.id, w.buyer, Tick(2))))
    }

    @Test fun anOfferNeedsASellerWithGenerationAndABuyerWithLoad() {
        val w = ppaWorld()
        val stranger = CompanyId(300)
        assertEquals(Rules.NOT_OWNER, rule(w.service.offerPpa(w.grid.id, stranger, w.buyer, PpaProfile.BASELOAD, 1_000L, 0L, null, Money.units(50), Tick(1), 90, 0L, Tick(0))))
        assertEquals(Rules.PREREQUISITE_MISSING, rule(w.service.offerPpa(w.grid.id, w.owner, stranger, PpaProfile.BASELOAD, 1_000L, 0L, null, Money.units(50), Tick(1), 90, 0L, Tick(0))))
    }

    @Test fun aSellerCannotPromiseMoreThanItHasBuilt() {
        val w = ppaWorld()
        assertEquals(Rules.INSUFFICIENT_CAPACITY, rule(offer(w, volume = 100_001L)))
        assertTrue(offer(w, volume = 100_000L).isOk)
    }

    @Test fun termsAreValidated() {
        val w = ppaWorld()
        assertEquals(Rules.INVALID_PRICE, rule(offer(w, strike = 0)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(offer(w, days = 10)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(offer(w, days = 5_000)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(offer(w, start = 2L, tick = 5L)))
        assertEquals(Rules.INVALID_QUANTITY, rule(offer(w, volume = 0L)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(w.service.offerPpa(w.grid.id, w.owner, w.owner, PpaProfile.BASELOAD, 1L, 0L, null, Money.units(5), Tick(1), 90, 0L, Tick(0))))
    }

    @Test fun anAsProducedOfferNamesTheSellersUnit() {
        val w = ppaWorld()
        val wind = w.unit(Technology.WIND, 20_000L, owner = w.owner)
        val foreign = w.unit(Technology.WIND, 20_000L, owner = CompanyId(999))
        fun offerOf(unit: UnitId?, share: Long) = w.service.offerPpa(w.grid.id, w.owner, w.buyer, PpaProfile.AS_PRODUCED, 0L, share, unit, Money.units(40), Tick(1), 90, 0L, Tick(0))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(offerOf(null, 500_000L)))
        assertEquals(Rules.NOT_OWNER, rule(offerOf(foreign.id, 500_000L)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(offerOf(wind.id, 0L)))
        val p = offerOf(wind.id, 500_000L).getOrThrow()
        assertEquals(wind.id, p.unit)
        assertEquals(0L, p.volumeKw)
    }

    @Test fun aCompanyHasLimitsOnOpenAgreements() {
        val w = ppaWorld()
        val s = EnergyGridService(limits = GridLimits(maxPpasPerCompany = 1))
        val g = s.addGrid(RegionId(1), CountryId(1), "G", ClimateZone.TEMPERATE).getOrThrow()
        s.addUnit(g.id, Technology.GAS, 100_000L, w.owner, Tick(0))
        s.connectFacility(g.id, CustomerClass.INDUSTRIAL, w.buyer, FacilityId(1), 1_000L)
        fun o() = s.offerPpa(g.id, w.owner, w.buyer, PpaProfile.BASELOAD, 1_000L, 0L, null, Money.units(50), Tick(1), 90, 0L, Tick(0))
        assertTrue(o().isOk)
        assertEquals(Rules.LIMIT_EXCEEDED, rule(o()))
    }

    @Test fun offersCanBeDeclinedByEitherPartyButNotBystanders() {
        val w = ppaWorld()
        val p = offer(w).getOrThrow()
        assertEquals(Rules.NOT_OWNER, rule(w.service.declinePpa(p.id, CompanyId(5), Tick(1))))
        w.service.declinePpa(p.id, w.owner, Tick(1)).getOrThrow()
        assertEquals(PpaStatus.DECLINED, p.status)
        assertEquals(Rules.WRONG_STATE, rule(w.service.declinePpa(p.id, w.owner, Tick(2))))
    }

    @Test fun aLateAcceptanceFindsTheOfferLapsed() {
        val w = ppaWorld()
        val p = offer(w, start = 100L).getOrThrow()
        val r = w.service.acceptPpa(p.id, w.buyer, Tick(40))
        assertEquals(Rules.WRONG_STATE, rule(r))
        assertEquals(PpaStatus.DECLINED, p.status)
    }

    @Test fun leavingEarlyCostsTheLeaverAnExitFee() {
        val w = ppaWorld()
        val p = offer(w, start = 0L, days = 100).getOrThrow()
        w.service.acceptPpa(p.id, w.buyer, Tick(0))
        val intent = w.service.terminatePpa(p.id, w.buyer, Tick(40)).getOrThrow() ?: error("an exit fee")
        // 50 MW for 24 hours at 50 is 60,000 a day; 60 days left; 10%
        assertEquals(Money.units(360_000), intent.total)
        assertEquals(app.many.economytycoon.finance.LedgerOwner.company(w.buyer), intent.payer)
        assertEquals(app.many.economytycoon.finance.LedgerOwner.company(w.owner), intent.payee)
        assertEquals(PpaStatus.TERMINATED, p.status)
        assertEquals(Rules.WRONG_STATE, rule(w.service.terminatePpa(p.id, w.buyer, Tick(41))))
    }

    @Test fun leavingAtTheVeryEndCostsNothing() {
        val w = ppaWorld()
        val p = offer(w, start = 0L, days = 100).getOrThrow()
        w.service.acceptPpa(p.id, w.buyer, Tick(0))
        assertNull(w.service.terminatePpa(p.id, w.owner, Tick(100)).getOrThrow())
    }

    @Test fun onlyPartiesCanTerminate() {
        val w = ppaWorld()
        val p = offer(w, start = 0L).getOrThrow()
        w.service.acceptPpa(p.id, w.buyer, Tick(0))
        assertEquals(Rules.NOT_OWNER, rule(w.service.terminatePpa(p.id, CompanyId(5), Tick(5))))
        assertFalse(w.state.ppasOfCompany(CompanyId(5)).isNotEmpty())
        assertEquals(1, w.state.ppasOfCompany(w.owner).size)
    }
}
