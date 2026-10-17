package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.emitter
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbatementTest {
    private val calc = EmissionCalculator(StandardFactors.table)
    private val catalog = AbatementCatalog()
    private val buildCost = Money.units(2_000_000)

    private fun record(coal: Long = 100, electricity: Long = 0, facility: Long = 1): FacilityRecord {
        val book = InventoryBook()
        val a = emitter(facility, acme, coalUnits = coal, electricity = Quantity.units(electricity))
        book.record(calc.facilityDay(a), Tick(0), 2000, true, Rate(1_000_000L))
        return book.facility(a.facility)!!
    }

    private fun option(kind: AbatementKind, r: FacilityRecord = record()) = catalog.optionsFor(r, buildCost).first { it.kind == kind }

    @Test fun aCoalBurnerGetsAllThreeOptions() {
        assertEquals(setOf(AbatementKind.EFFICIENCY_RETROFIT, AbatementKind.FUEL_SWITCH, AbatementKind.CARBON_CAPTURE), catalog.optionsFor(record(), buildCost).map { it.kind }.toSet())
    }

    @Test fun aSmallEmitterGetsNone() {
        assertTrue(catalog.optionsFor(record(coal = 1), buildCost).isEmpty())
    }

    @Test fun theRetrofitCutsAShareOfFuelAndPaysBackInSavings() {
        val o = option(AbatementKind.EFFICIENCY_RETROFIT)
        assertEquals(Money.units(300_000), o.capex)
        assertEquals(Tonnes.of(720), o.annualAbatement)
        assertEquals(Money.units(-43_200), o.opexPerYear)
        assertTrue(o.costPerTonne.raw < 0L)
        assertTrue(o.worthwhileAt(Money.ZERO))
    }

    @Test fun theFuelSwitchReplacesTheDominantFuel() {
        val o = option(AbatementKind.FUEL_SWITCH)
        assertEquals("coal", o.fuelKey)
        assertEquals(Tonnes.of(3_960), o.annualAbatement)
        assertEquals(Money.units(180_000), o.capex)
        assertEquals(Money.units(57_600), o.opexPerYear)
        assertEquals(550_000L, o.effectReductionPpm)
    }

    @Test fun noFuelSwitchWhereThereIsNothingToSwitchFrom() {
        val r = record()
        val noFuel = catalog.optionsFor(record(coal = 0).also { }, buildCost)
        assertTrue(noFuel.none { it.kind == AbatementKind.FUEL_SWITCH })
        assertNotNull(r)
    }

    @Test fun captureTakesMostOfTheEmissionsAtARunningCostPerTonne() {
        val o = option(AbatementKind.CARBON_CAPTURE)
        assertEquals(Tonnes.of(6_120), o.annualAbatement)
        assertEquals(Tonnes.of(6_120).cost(Money.units(12)), o.opexPerYear)
        assertEquals(Money.units(560_000), o.capex)
        assertEquals(850_000L, o.effectReductionPpm)
    }

    @Test fun optionsAlreadyInstalledAreNotOfferedAgain() {
        val r = record()
        r.installed += AbatementEffect(AbatementKind.CARBON_CAPTURE, 850_000L)
        assertTrue(catalog.optionsFor(r, buildCost).none { it.kind == AbatementKind.CARBON_CAPTURE })
    }

    @Test fun costPerTonneIsAnnualisedCostOverAbatement() {
        val o = option(AbatementKind.FUEL_SWITCH)
        val expected = (Money.units(180_000).raw / 20 + Money.units(57_600).raw) * 1_000.0 / Tonnes.of(3_960).raw
        assertTrue(Math.abs(o.costPerTonne.raw - expected) <= 1.0)
    }

    @Test fun worthwhileOnlyBelowThePrice() {
        val o = option(AbatementKind.FUEL_SWITCH)
        assertFalse(o.worthwhileAt(Money.units(5)))
        assertTrue(o.worthwhileAt(Money.units(40)))
    }

    @Test fun paybackIsInWholeYearsOrNeverAtALowPrice() {
        val o = option(AbatementKind.FUEL_SWITCH)
        assertNull(o.paybackYears(Money.units(5)))
        val years = o.paybackYears(Money.units(60))!!
        assertTrue(years in 1..10)
    }

    @Test fun theMacCurveIsSortedCheapestFirstAndCumulative() {
        val options = catalog.optionsFor(record(), buildCost) + catalog.optionsFor(record(coal = 50, facility = 2), buildCost)
        val curve = MacCurve(options)
        val costs = curve.points.map { it.cost }
        assertEquals(costs.sortedBy { it.raw }, costs)
        assertEquals(Tonnes.sum(options.map { it.annualAbatement }), curve.totalAbatement)
        assertEquals(curve.points.last().cumulative, curve.totalAbatement)
        for (i in 1 until curve.points.size) assertTrue(curve.points[i].cumulative > curve.points[i - 1].cumulative)
    }

    @Test fun theCurveReadsAcrossAtAPriceAndUpAtATarget() {
        val curve = MacCurve(catalog.optionsFor(record(), buildCost))
        assertEquals(1, curve.worthwhileAt(Money.ZERO).size)
        assertEquals(Tonnes.of(720), curve.abatableAt(Money.ZERO))
        assertEquals(3, curve.worthwhileAt(Money.units(100)).size)
        val target = Tonnes.of(1_000)
        val chosen = curve.cheapestFor(target)
        assertTrue(Tonnes.sum(chosen.map { it.annualAbatement }) >= target)
        assertEquals(curve.points.first { it.cumulative >= target }.cost, curve.marginalCostFor(target))
        assertNull(curve.marginalCostFor(Tonnes.of(1_000_000)))
        assertNull(curve.marginalCostFor(Tonnes.ZERO))
        assertTrue(curve.cheapestFor(Tonnes.ZERO).isEmpty())
    }

    @Test fun annualCostCountsSavingsFromNegativeCostMeasures() {
        val curve = MacCurve(catalog.optionsFor(record(), buildCost))
        val first = curve.points.first().option
        assertEquals(first.annualisedCost, curve.annualCostFor(Tonnes.of(100)))
        assertTrue(first.annualisedCost.raw < 0L)
    }

    @Test fun theBookKeepsProjectsAndFindsLiveOnesByFacility() {
        val book = AbatementBook()
        val o = option(AbatementKind.CARBON_CAPTURE)
        val p = AbatementProject(AbatementId(1), acme, FacilityId(1), o.kind, o.capex, o.opexPerYear, o.effect, o.annualAbatement, Tick(0), Tick(240), Tick(9_000))
        book.put(p)
        assertEquals(listOf(p), book.liveAt(FacilityId(1)))
        p.status = AbatementStatus.CANCELLED
        assertTrue(book.liveAt(FacilityId(1)).isEmpty())
        assertEquals(listOf(p), book.projectsOf(acme))
        assertEquals(p, book.project(AbatementId(1)))
    }
}
