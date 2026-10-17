package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.de
import app.many.economytycoon.carbon.CarbonTestSupport.emitter
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryTest {
    private val calc = EmissionCalculator(StandardFactors.table)
    private val book = InventoryBook()
    private val full = Rate(1_000_000L)

    private fun day(a: FacilityActivity, year: Int = 2000, covered: Boolean = true, tick: Long = 0) {
        book.record(calc.facilityDay(a), Tick(tick), year, covered, full)
        book.markDay(a.company, a.country, year)
    }

    @Test fun aDayIsBookedIntoTheCompanyAndTheNationalInventory() {
        day(emitter(1, acme, coalUnits = 100, electricity = Quantity.units(100)))
        val cy = book.company(acme, uk, 2000)!!
        assertEquals(Tonnes.of(20), cy.direct)
        assertEquals(Tonnes.ofKg(2_500), cy.indirect)
        assertEquals(Tonnes.of(20), cy.covered)
        assertEquals(Tonnes.ofKg(22_500), cy.footprint)
        assertEquals(1, cy.days)
        assertEquals(cy.direct, book.national(uk, 2000)!!.direct)
    }

    @Test fun uncoveredEmissionsCountInTheTotalButNotAsCovered() {
        day(emitter(1, acme), covered = false)
        val cy = book.company(acme, uk, 2000)!!
        assertEquals(Tonnes.of(20), cy.direct)
        assertEquals(Tonnes.ZERO, cy.covered)
        assertEquals(Tonnes.ZERO, book.national(uk, 2000)!!.covered)
    }

    @Test fun theNationalTotalIsTheSumOfTheCompanies() {
        day(emitter(1, acme, coalUnits = 100))
        day(emitter(2, borea, coalUnits = 50))
        day(emitter(3, acme, country = de, coalUnits = 10))
        assertEquals(Tonnes.of(30), book.national(uk, 2000)!!.direct)
        assertEquals(Tonnes.of(2), book.national(de, 2000)!!.direct)
        assertEquals(book.national(uk, 2000)!!.direct, Tonnes.sum(book.companiesIn(uk, 2000).map { it.direct }))
    }

    @Test fun companiesAreKeptSeparatelyPerCountryAndYear() {
        day(emitter(1, acme), year = 2000)
        day(emitter(1, acme), year = 2001)
        day(emitter(2, acme, country = de))
        assertEquals(listOf(2000, 2001), book.companyYearsOf(acme).filter { it.country == uk }.map { it.year })
        assertEquals(Tonnes.of(40), book.totalFootprint(acme, 2000))
    }

    @Test fun sourcesAreBrokenDownByKindAndKey() {
        day(emitter(1, acme, coalUnits = 100))
        assertEquals(Tonnes.of(20), book.company(acme, uk, 2000)!!.sources()["FUEL:coal"])
    }

    @Test fun theDominantFamilyIsTheOneThatReleasesMost() {
        day(emitter(1, acme, coalUnits = 10, family = IndustryFamily.METALS))
        day(emitter(2, acme, coalUnits = 90, family = IndustryFamily.ENERGY))
        assertEquals(IndustryFamily.ENERGY, book.company(acme, uk, 2000)!!.dominantFamily())
    }

    @Test fun intensityIsGramsPerUnitOfOutput() {
        day(emitter(1, acme, coalUnits = 100, output = Money.units(4_000)))
        assertEquals(5_000L, book.company(acme, uk, 2000)!!.intensityGramsPerUnit)
        day(emitter(2, borea, coalUnits = 100, output = Money.ZERO))
        assertEquals(0L, book.company(borea, uk, 2000)!!.intensityGramsPerUnit)
    }

    @Test fun electricityProducedFeedsTheNationalFigure() {
        day(emitter(1, acme, family = IndustryFamily.ENERGY, produced = Quantity.units(500)))
        val n = book.national(uk, 2000)!!
        assertEquals(Quantity.units(500), n.electricityProduced)
        assertEquals(Tonnes.of(20), n.power)
    }

    @Test fun theFacilityRecordSmoothsItsDailyFigures() {
        val alpha = Rate(500_000L)
        book.record(calc.facilityDay(emitter(1, acme, coalUnits = 100)), Tick(0), 2000, true, alpha)
        book.record(calc.facilityDay(emitter(1, acme, coalUnits = 0)), Tick(1), 2000, true, alpha)
        val r = book.facility(FacilityId(1))!!
        assertEquals(Tonnes.of(10), r.dailyDirect)
        assertEquals(2, r.samples)
        assertEquals(Tick(1), r.lastSeen)
        assertEquals("coal", r.dominantFuel()!!.first)
    }

    @Test fun theFirstSampleSetsTheRateOutright() {
        book.record(calc.facilityDay(emitter(1, acme, coalUnits = 100)), Tick(0), 2000, true, Rate(100_000L))
        assertEquals(Tonnes.of(20), book.facility(FacilityId(1))!!.dailyDirect)
        assertEquals(Tonnes.of(7_200), book.facility(FacilityId(1))!!.annualDirect)
    }

    @Test fun anOwnerChangeFollowsTheLatestDay() {
        day(emitter(1, acme))
        day(emitter(1, borea), tick = 1)
        assertEquals(borea, book.facility(FacilityId(1))!!.owner)
        assertEquals(listOf(FacilityId(1)), book.facilitiesOf(borea).map { it.facility })
        assertTrue(book.facilitiesOf(acme).isEmpty())
    }

    @Test fun closingMarksYearsAndPruningDropsOldOnes() {
        day(emitter(1, acme), year = 2000)
        day(emitter(1, acme), year = 2005)
        book.close(2000)
        assertTrue(book.company(acme, uk, 2000)!!.closed)
        assertTrue(!book.company(acme, uk, 2005)!!.closed)
        book.prune(2006, 3)
        assertNull(book.company(acme, uk, 2000))
        assertNull(book.national(uk, 2000))
        assertTrue(book.company(acme, uk, 2005) != null)
    }

    @Test fun anInactiveFacilityDoesNotAddEmissions() {
        day(emitter(1, acme).copy(operating = false))
        assertEquals(Tonnes.ZERO, book.company(acme, uk, 2000)!!.direct)
    }

    @Test fun nationalHistoryIsInYearOrder() {
        day(emitter(1, acme), year = 2003); day(emitter(1, acme), year = 2001)
        assertEquals(listOf(2001, 2003), book.nationalHistory(uk).map { it.year })
    }
}
