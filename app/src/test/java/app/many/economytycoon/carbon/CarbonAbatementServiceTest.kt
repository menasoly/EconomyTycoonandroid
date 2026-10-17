package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.capAndTrade
import app.many.economytycoon.carbon.CarbonTestSupport.emitter
import app.many.economytycoon.carbon.CarbonTestSupport.rule
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.carbon.CarbonTestSupport.value
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.ReasonCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbonAbatementServiceTest {
    private val fx = CarbonTestSupport.Fixture()
    private val plant = emitter(1, acme)
    private val plants = listOf(plant, emitter(2, borea))
    private val build = Money.units(2_000_000)

    private fun open() {
        fx.service.installRegime(capAndTrade(), Tick(0)).value()
        fx.run(0, 5, plants)
    }

    private fun start(kind: AbatementKind, day: Long = 5) = fx.exec(CarbonCommand.StartAbatement(acme, FacilityId(1), kind), day)

    @Test fun aMeasureIsOrderedAndPaidForUpFront() {
        open()
        val e = start(AbatementKind.FUEL_SWITCH).value()
        val p = fx.state.abatement.projects().single()
        assertEquals(AbatementStatus.BUILDING, p.status)
        assertEquals(Money.units(180_000), p.capex)
        assertEquals(p.capex, e.settlement.payments.single().total)
        assertEquals(Tick(5 + 90), p.completesAt)
    }

    @Test fun onlyTheOwnerOfAMeasuredFacilityMayOrderOne() {
        open()
        assertEquals(Rules.NOT_OWNER, fx.exec(CarbonCommand.StartAbatement(borea, FacilityId(1), AbatementKind.FUEL_SWITCH), 5).rule())
        assertEquals(Rules.NOT_OWNER, fx.exec(CarbonCommand.StartAbatement(acme, FacilityId(99), AbatementKind.FUEL_SWITCH), 5).rule())
    }

    @Test fun anUnmeasuredFacilityHasNoOptionsYet() {
        fx.own(plant)
        assertEquals(Rules.PREREQUISITE_MISSING, start(AbatementKind.FUEL_SWITCH, 0).rule())
    }

    @Test fun aMeasureCannotBeOrderedTwice() {
        open()
        start(AbatementKind.FUEL_SWITCH).value()
        assertEquals(Rules.DUPLICATE, start(AbatementKind.FUEL_SWITCH).rule())
    }

    @Test fun aFacilityHoldsOnlySoManyMeasuresAtOnce() {
        open()
        start(AbatementKind.FUEL_SWITCH).value(); start(AbatementKind.EFFICIENCY_RETROFIT).value(); start(AbatementKind.CARBON_CAPTURE).value()
        assertEquals(3, fx.state.abatement.liveAt(FacilityId(1)).size)
    }

    @Test fun orderingNeedsCash() {
        open()
        fx.cash[acme] = Money.units(1_000)
        assertEquals(Rules.INSUFFICIENT_FUNDS, start(AbatementKind.CARBON_CAPTURE).rule())
    }

    @Test fun whenBuiltTheMeasureCutsTheFacilitysEmissions() {
        open()
        val before = fx.state.inventory.facility(FacilityId(1))!!.dailyDirect
        start(AbatementKind.CARBON_CAPTURE).value()
        fx.run(6, 400, plants)
        val p = fx.state.abatement.projects().single()
        assertEquals(AbatementStatus.OPERATING, p.status)
        val record = fx.state.inventory.facility(FacilityId(1))!!
        assertEquals(1, record.effects.size)
        assertTrue(record.dailyDirect.raw < before.raw / 5)
        assertTrue(fx.eventsOf<CarbonEvent.AbatementDone>().any { it.kind == AbatementKind.CARBON_CAPTURE })
    }

    @Test fun theGovernmentPaysItsShareOfTheCostFromTheGreenFund() {
        open()
        fx.state.addToFund(uk, Money.units(1_000_000))
        start(AbatementKind.FUEL_SWITCH).value()
        fx.run(6, 120, plants)
        val p = fx.state.abatement.projects().single()
        assertEquals(Money.units(18_000), p.subsidy)
        assertEquals(Money.units(982_000), fx.state.greenFund(uk))
        val grant = fx.payments.single { it.reason == ReasonCode.SUBSIDY }
        assertEquals(acme.value, grant.payee.id)
        assertEquals(Money.units(18_000), grant.total)
    }

    @Test fun anEmptyGreenFundPaysNoSubsidy() {
        open()
        start(AbatementKind.FUEL_SWITCH).value()
        fx.run(6, 120, plants)
        assertEquals(Money.ZERO, fx.state.abatement.projects().single().subsidy)
    }

    @Test fun theSubsidyNeverExceedsWhatTheTreasuryHolds() {
        open()
        fx.state.addToFund(uk, Money.units(1_000_000))
        fx.treasury[uk] = Money.units(5_000)
        start(AbatementKind.FUEL_SWITCH).value()
        fx.run(6, 120, plants)
        assertEquals(Money.units(5_000), fx.state.abatement.projects().single().subsidy)
    }

    @Test fun aGreenHookAddsToTheSubsidy() {
        val hooked = CarbonService(fx.state, fx.env, fx.service.calculator, fx.service.random, greenHook = GreenSubsidyHook { _, _, _, capex -> Money(capex.raw / 10) })
        hooked.installRegime(capAndTrade(), Tick(0)).value()
        fx.own(*plants.toTypedArray())
        for (d in 0L..5L) hooked.advance(Tick(d), plants)
        fx.state.addToFund(uk, Money.units(1_000_000))
        hooked.execute(CarbonCommand.StartAbatement(acme, FacilityId(1), AbatementKind.FUEL_SWITCH), Tick(5)).value()
        for (d in 6L..120L) hooked.advance(Tick(d), plants)
        assertEquals(Money.units(36_000), fx.state.abatement.projects().single().subsidy)
    }

    @Test fun runningCostsArePaidEveryMonthOnceOperating() {
        open()
        start(AbatementKind.FUEL_SWITCH).value()
        fx.run(6, 250, plants)
        val running = fx.payments.filter { it.reason == ReasonCode.FEE && it.key.contains(":abate:") }
        assertTrue(running.size >= 4)
        assertEquals(Money.units(57_600 / 12), running.first().total)
        assertEquals(Money(Money.units(57_600).raw / 12) * running.size.toLong(), fx.state.abatement.projects().single().opexPaid)
    }

    @Test fun cancellingWhileBuildingRecoversPartOfTheMoney() {
        open()
        start(AbatementKind.FUEL_SWITCH).value()
        val id = fx.state.abatement.projects().single().id
        val e = fx.exec(CarbonCommand.CancelAbatement(acme, id), 20).value()
        assertEquals(AbatementStatus.CANCELLED, fx.state.abatement.project(id)!!.status)
        assertEquals(Money.units(72_000), e.settlement.payments.single().total)
        assertEquals(1, e.settlement.adjustments.size)
        fx.run(6, 200, plants)
        assertTrue(fx.state.inventory.facility(FacilityId(1))!!.effects.isEmpty())
    }

    @Test fun onlyTheOwnerCanCancel() {
        open()
        start(AbatementKind.FUEL_SWITCH).value()
        val id = fx.state.abatement.projects().single().id
        assertEquals(Rules.NOT_OWNER, fx.exec(CarbonCommand.CancelAbatement(borea, id), 20).rule())
        assertEquals(Rules.UNKNOWN_ENTITY, fx.exec(CarbonCommand.CancelAbatement(acme, AbatementId(77)), 20).rule())
    }

    @Test fun anOperatingMeasureCanBeDecommissionedWithoutRefund() {
        open()
        start(AbatementKind.CARBON_CAPTURE).value()
        fx.run(6, 300, plants)
        val id = fx.state.abatement.projects().single().id
        val e = fx.exec(CarbonCommand.CancelAbatement(acme, id), 301).value()
        assertTrue(e.settlement.isEmpty)
        assertEquals(AbatementStatus.RETIRED, fx.state.abatement.project(id)!!.status)
        assertTrue(fx.state.inventory.facility(FacilityId(1))!!.effects.isEmpty())
        assertEquals(Rules.WRONG_STATE, fx.exec(CarbonCommand.CancelAbatement(acme, id), 302).rule())
    }

    @Test fun aMeasureRetiresAtTheEndOfItsLife() {
        open()
        start(AbatementKind.EFFICIENCY_RETROFIT).value()
        val p = fx.state.abatement.projects().single()
        fx.run(6, p.endsAt.value + 1, listOf(plant))
        assertEquals(AbatementStatus.RETIRED, p.status)
        assertTrue(fx.eventsOf<CarbonEvent.AbatementRetired>().isNotEmpty())
    }

    @Test fun theCurveOfAFirmShowsOptionsAcrossItsFacilities() {
        open()
        fx.run(6, 10, plants)
        val curve = fx.views.macCurve(acme, uk)
        assertEquals(3, curve.points.size)
        assertTrue(curve.totalAbatement.isPositive)
        assertEquals(3, fx.views.options(acme).size)
        assertTrue(fx.views.options(CarbonTestSupport.cyrus).isEmpty())
    }
}
