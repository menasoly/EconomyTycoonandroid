package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.cyrus
import app.many.economytycoon.carbon.CarbonTestSupport.emitter
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportingTest {
    private fun statement(
        company: CompanyId = acme, year: Int = 2001, direct: Long = 1_000, electricity: Long = 0, output: Long = 100_000, intensity: Long = 5_000L,
        offsets: Long = 0, change: Long? = null, status: ComplianceStatus = ComplianceStatus.COMPLIANT,
    ) = AnnualStatement(
        company, uk, year, Tonnes.of(direct), Tonnes.of(electricity), Tonnes.ZERO, Tonnes.of(direct), Tonnes.ZERO, Money.units(output), intensity,
        IndustryFamily.METALS, RegimeKind.CAP_AND_TRADE, status, Tonnes.of(direct), Tonnes.of(direct - offsets), Tonnes.of(offsets), Tonnes.ZERO, Money.ZERO, Tonnes.ZERO, change,
    )

    private val bench = IntensityBenchmark(IndustryFamily.METALS, 5_000L, 3_000L, 4)

    private fun factor(r: CarbonRating, label: String) = r.factors.first { it.label == label }

    @Test fun gradesStepDownByScore() {
        assertEquals(CarbonGrade.AAA, CarbonGrade.of(1_000))
        assertEquals(CarbonGrade.AAA, CarbonGrade.of(900))
        assertEquals(CarbonGrade.AA, CarbonGrade.of(899))
        assertEquals(CarbonGrade.BBB, CarbonGrade.of(600))
        assertEquals(CarbonGrade.CCC, CarbonGrade.of(250))
        assertEquals(CarbonGrade.D, CarbonGrade.of(249))
        assertEquals(CarbonGrade.D, CarbonGrade.of(0))
    }

    @Test fun betterGradesNarrowTheBorrowingSpread() {
        val spreads = CarbonGrade.values().map { it.spreadBp }
        assertEquals(spreads.sorted(), spreads)
        assertTrue(CarbonGrade.AAA.spreadBp < 0 && CarbonGrade.D.spreadBp > 0)
    }

    @Test fun theSpreadAdjustmentIsInPpmOfPrincipal() {
        val r = CarbonRating(acme, 2001, 100, CarbonGrade.D, emptyList())
        assertEquals(CarbonGrade.D.spreadBp * 100L, r.spreadAdjustmentPpm)
    }

    @Test fun aCleanCompanyThatReportsEveryYearGetsFullMarks() {
        val r = RatingModel.rate(statement(intensity = 2_000L, change = -150_000L), bench, 0, 3)
        assertEquals(1_000, r.score)
        assertEquals(CarbonGrade.AAA, r.grade)
    }

    @Test fun intensityScoresLinearlyAgainstTheMedian() {
        assertEquals(400, factor(RatingModel.rate(statement(intensity = 2_500L), bench, 0, 1), "Intensity").points)
        assertEquals(267, factor(RatingModel.rate(statement(intensity = 5_000L), bench, 0, 1), "Intensity").points)
        assertEquals(0, factor(RatingModel.rate(statement(intensity = 10_000L), bench, 0, 1), "Intensity").points)
    }

    @Test fun withoutPeersIntensityScoresHalf() {
        assertEquals(200, factor(RatingModel.rate(statement(), null, 0, 1), "Intensity").points)
        assertEquals(200, factor(RatingModel.rate(statement(output = 0), bench, 0, 1), "Intensity").points)
    }

    @Test fun trendRewardsFallingEmissions() {
        assertEquals(150, factor(RatingModel.rate(statement(change = -200_000L), bench, 0, 1), "Trend").points)
        assertEquals(75, factor(RatingModel.rate(statement(change = 0L), bench, 0, 1), "Trend").points)
        assertEquals(0, factor(RatingModel.rate(statement(change = 300_000L), bench, 0, 1), "Trend").points)
        assertEquals(75, factor(RatingModel.rate(statement(change = null), bench, 0, 1), "Trend").points)
    }

    @Test fun eachYearInShortfallCostsComplianceMarks() {
        assertEquals(250, factor(RatingModel.rate(statement(), bench, 0, 1), "Compliance").points)
        assertEquals(125, factor(RatingModel.rate(statement(), bench, 1, 1), "Compliance").points)
        assertEquals(0, factor(RatingModel.rate(statement(), bench, 5, 1), "Compliance").points)
    }

    @Test fun relianceOnOffsetsLowersTheOffsetMarks() {
        assertEquals(100, factor(RatingModel.rate(statement(), bench, 0, 1), "Offsets").points)
        assertEquals(0, factor(RatingModel.rate(statement(direct = 1_000, offsets = 600), bench, 0, 1), "Offsets").points)
        val half = factor(RatingModel.rate(statement(direct = 1_000, offsets = 250), bench, 0, 1), "Offsets").points
        assertEquals(50, half)
    }

    @Test fun firstTimeReportersGetHalfTheDisclosureMarks() {
        assertEquals(50, factor(RatingModel.rate(statement(), bench, 0, 0), "Disclosure").points)
        assertEquals(100, factor(RatingModel.rate(statement(), bench, 0, 2), "Disclosure").points)
    }

    @Test fun theScoreIsTheSumOfItsFactorsAndStaysInRange() {
        val r = RatingModel.rate(statement(intensity = 9_999_999L, change = 900_000L), bench, 9, 0)
        assertEquals(r.factors.sumOf { it.points }.coerceIn(0, 1_000), r.score)
        assertTrue(r.score in 0..1_000)
        assertEquals(5, r.factors.size)
        assertTrue(r.factors.all { it.points in 0..it.maximum })
    }

    // ---- benchmarks ----

    private fun yearOf(vararg plants: FacilityActivity): List<CompanyYear> {
        val calc = EmissionCalculator(StandardFactors.table)
        val book = InventoryBook()
        for (a in plants) book.record(calc.facilityDay(a), Tick(0), 2000, true, Rate(1_000_000L))
        return book.companiesIn(uk, 2000)
    }

    @Test fun benchmarksNeedEnoughPeersInAnIndustry() {
        val years = yearOf(emitter(1, acme, coalUnits = 100), emitter(2, borea, coalUnits = 50), emitter(3, cyrus, coalUnits = 10, family = IndustryFamily.ENERGY))
        val list = Benchmarks.compute(years)
        assertEquals(listOf(IndustryFamily.METALS), list.map { it.family })
        assertEquals(2, list.single().peers)
        assertEquals(1, Benchmarks.compute(years, minPeers = 1).count { it.family == IndustryFamily.ENERGY })
    }

    @Test fun theMedianOfAnEvenGroupIsTheMeanOfTheMiddleTwo() {
        val years = yearOf(emitter(1, acme, coalUnits = 100), emitter(2, borea, coalUnits = 50))
        val b = Benchmarks.compute(years).single()
        assertEquals((5_000L + 2_500L) / 2, b.medianGrams)
        assertEquals(2_500L, b.bestGrams)
    }

    @Test fun companiesWithNoOutputAreLeftOutOfBenchmarks() {
        val years = yearOf(emitter(1, acme), emitter(2, borea, output = Money.ZERO), emitter(3, cyrus))
        assertEquals(2, Benchmarks.compute(years).single().peers)
    }

    @Test fun ratioToTheMedianIsInPpm() {
        assertEquals(1_000_000L, bench.ratioPpm(5_000L))
        assertEquals(500_000L, bench.ratioPpm(2_500L))
        assertEquals(1_000_000L, IntensityBenchmark(IndustryFamily.METALS, 0L, 0L, 2).ratioPpm(10L))
        assertNull(Benchmarks.forFamily(emptyList(), IndustryFamily.METALS))
        assertNotNull(Benchmarks.forFamily(listOf(bench), IndustryFamily.METALS))
    }

    // ---- the national summary ----

    @Test fun theNationalSummaryShowsCoverageChangeAndTopFamilies() {
        val calc = EmissionCalculator(StandardFactors.table)
        val book = InventoryBook()
        val one = Rate(1_000_000L)
        book.record(calc.facilityDay(emitter(1, acme, coalUnits = 100)), Tick(0), 2000, true, one)
        book.record(calc.facilityDay(emitter(2, borea, coalUnits = 50, family = IndustryFamily.ENERGY)), Tick(0), 2000, false, one)
        book.record(calc.facilityDay(emitter(1, acme, coalUnits = 200)), Tick(0), 2001, true, one)
        val s = NationalReports.summarise(book, uk, 2001)!!
        assertEquals(Tonnes.of(40), s.direct)
        assertEquals(1_000_000L, s.coveragePpm)
        assertEquals(333_333L, s.changePpm)
        assertEquals(s.changePpm, s.sinceBaselinePpm)
        assertEquals(IndustryFamily.METALS, s.topFamilies.first().first)
        assertNull(NationalReports.summarise(book, uk, 1990))
        val first = NationalReports.summarise(book, uk, 2000)!!
        assertNull(first.changePpm)
        assertNull(first.sinceBaselinePpm)
        assertEquals(666_667L, first.coveragePpm)
    }

    @Test fun theReportBookKeepsStatementsInOrderAndPrunesOldOnes() {
        val book = ReportBook()
        book.file(statement(year = 2003)); book.file(statement(year = 2001)); book.file(statement(company = borea, year = 2002))
        assertEquals(listOf(2001, 2003), book.statementsOf(acme).map { it.year })
        assertEquals(3, book.statements().size)
        book.prune(2005, 3)
        assertEquals(listOf(2003), book.statementsOf(acme).map { it.year })
        assertNull(book.statement(acme, uk, 2001))
        book.rate(CarbonRating(acme, 2003, 500, CarbonGrade.BB, emptyList()))
        assertEquals(CarbonGrade.BB, book.rating(acme)!!.grade)
        assertEquals(1, book.ratings().size)
    }
}
