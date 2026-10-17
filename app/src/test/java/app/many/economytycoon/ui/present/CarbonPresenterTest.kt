package app.many.economytycoon.ui.present

import app.many.economytycoon.carbon.AbatementKind
import app.many.economytycoon.carbon.CarbonCommand
import app.many.economytycoon.carbon.CarbonGrade
import app.many.economytycoon.carbon.CarbonTestSupport
import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.carbon.ComplianceStatus
import app.many.economytycoon.carbon.OffsetKind
import app.many.economytycoon.carbon.Tonnes
import app.many.economytycoon.core.FacilityId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbonPresenterTest {
    private fun desk(company: app.many.economytycoon.core.CompanyId = acme, day: Long = 470, days: Long = 470): CarbonDeskFixture =
        CarbonDeskFixture(CarbonTestSupport.Fixture().also { CarbonTestSupport.busy(it, days) }, company, day)

    @Test fun aBusyCompanyGetsEveryPanel() {
        val m = CarbonPresenter.build(desk())
        assertEquals(4, m.tiles.size)
        assertTrue(m.accounts.isNotEmpty())
        assertTrue(m.offsets.size == OffsetKind.values().size)
        assertNull(m.empty)
    }

    @Test fun accountsNameTheirCountryAndRegime() {
        val m = CarbonPresenter.build(desk())
        val uk = m.accounts.first { it.country == uk }
        assertEquals("Country 1", uk.title)
        assertEquals("Cap and trade", uk.regime)
        assertTrue(uk.canBuy)
        assertTrue(uk.facts.any { it.label == "Permits held" })
    }

    @Test fun aTaxCountryOffersNothingToTrade() {
        val m = CarbonPresenter.build(desk())
        val de = m.accounts.first { it.country.value == 2L }
        assertFalse(de.canBuy)
        assertTrue(de.facts.none { it.label == "Permits held" })
    }

    @Test fun tonnesAreWholeAndGrouped() {
        assertEquals("12,345 t", CarbonPresenter.tonnes(Tonnes.of(12_345)))
        assertEquals("0 t", CarbonPresenter.tonnes(Tonnes.ZERO))
        assertEquals("-3 t", CarbonPresenter.tonnes(Tonnes.of(-3)))
    }

    @Test fun gradesColourFromGoodToBad() {
        assertEquals(Tone.GOOD, CarbonPresenter.gradeTone(CarbonGrade.AAA))
        assertEquals(Tone.GOOD, CarbonPresenter.gradeTone(CarbonGrade.A))
        assertEquals(Tone.WARN, CarbonPresenter.gradeTone(CarbonGrade.BBB))
        assertEquals(Tone.WARN, CarbonPresenter.gradeTone(CarbonGrade.B))
        assertEquals(Tone.BAD, CarbonPresenter.gradeTone(CarbonGrade.CCC))
        assertEquals(Tone.BAD, CarbonPresenter.gradeTone(CarbonGrade.D))
    }

    @Test fun everyComplianceStatusHasATone() {
        for (s in ComplianceStatus.values()) assertNotNull(CarbonPresenter.statusTone(s))
        assertEquals(Tone.BAD, CarbonPresenter.statusTone(ComplianceStatus.SHORTFALL))
        assertEquals(Tone.GOOD, CarbonPresenter.statusTone(ComplianceStatus.COMPLIANT))
    }

    @Test fun optionsListCheapestFirstWithAMacLabel() {
        val m = CarbonPresenter.build(desk())
        assertTrue(m.options.size <= 8)
        for (o in m.options) assertTrue(o.cost.endsWith("a tonne"))
        assertTrue(m.options.all { it.detail.startsWith("Saves ") })
    }

    @Test fun statementsComeNewestFirst() {
        val rows = CarbonPresenter.build(desk()).statements
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.first().title.startsWith("2000"))
    }

    @Test fun projectsListBothKinds() {
        val m = CarbonPresenter.build(desk())
        assertTrue(m.projects.any { it.title.contains("Fuel switch") })
        assertTrue(m.projects.any { it.title.contains("Clean cooking") })
    }

    @Test fun aCompanyWithoutFootprintSeesTheEmptyState() {
        val fx = CarbonTestSupport.Fixture()
        val d = CarbonDeskFixture(fx, acme, 5)
        val m = CarbonPresenter.build(d)
        assertEquals(CarbonPresenter.EMPTY, m.empty)
        assertTrue(m.accounts.isEmpty())
        assertEquals("None yet", m.tiles[2].value)
    }

    @Test fun theRatingExplainsItsFactors() {
        val m = CarbonPresenter.build(desk(borea))
        assertTrue(m.rating.isNotEmpty())
        assertEquals("Score", m.rating.first().label)
        assertTrue(m.rating.drop(1).all { it.value.contains(" of ") })
    }

    @Test fun buyingOffsetsSubmitsACommand() {
        val d = desk()
        assertNull(CarbonPresenter.buyOffsets(d, OffsetKind.METHANE_CAPTURE, " 25 "))
        val c = d.submitted.single() as CarbonCommand.BuyOffsets
        assertEquals(Tonnes.of(25), c.quantity)
        assertEquals(acme, c.company)
    }

    @Test fun badNumbersNeverReachTheService() {
        val d = desk()
        for (text in listOf("", "abc", "0", "-4", "1.5")) {
            assertEquals("Enter a number of tonnes above zero.", CarbonPresenter.buyOffsets(d, OffsetKind.METHANE_CAPTURE, text))
        }
        assertTrue(d.submitted.isEmpty())
    }

    @Test fun refusalsAreReturnedAsText() {
        val d = desk()
        d.refusal = "Not enough cash."
        assertEquals("Not enough cash.", CarbonPresenter.surrender(d, uk, "10"))
    }

    @Test fun startingAMeasureUsesTheRowsFacilityAndKind() {
        val d = desk(day = 5, days = 5)
        val row = CarbonPresenter.build(d).options.first()
        CarbonPresenter.startAbatement(d, row)
        val c = d.submitted.single() as CarbonCommand.StartAbatement
        assertEquals(row.facility, c.facility)
        assertEquals(row.kind, c.kind)
    }

    @Test fun policyChangesPassThrough() {
        val d = desk()
        assertNull(CarbonPresenter.setPolicy(d, uk, autoOffsets = false, autoBorrow = true))
        val c = d.submitted.single() as CarbonCommand.SetPolicy
        assertFalse(c.autoOffsets)
        assertTrue(c.autoBorrow)
    }

    @Test fun deadlinesReadInDays() {
        val m = CarbonPresenter.build(desk(day = 360 + 10, days = 360 + 10))
        val texts = m.accounts.mapNotNull { it.deadline }
        assertTrue(texts.all { it.contains("falls due") || it.contains("settled") })
    }

    @Test fun insightsCarryTonesFromPriority() {
        val i = app.many.economytycoon.advisor.Insight("x", app.many.economytycoon.advisor.Topic.FINANCE, 90, "t", "d")
        assertEquals(Tone.BAD, CarbonPresenter.insight(i).tone)
        assertEquals(Tone.WARN, CarbonPresenter.insight(i.copy(priority = 60)).tone)
        assertEquals(Tone.INFO, CarbonPresenter.insight(i.copy(priority = 10)).tone)
    }

    @Test fun buildingTwiceGivesTheSameModel() {
        val d = desk()
        assertEquals(CarbonPresenter.build(d), CarbonPresenter.build(d))
    }

    @Test fun facilityNamesComeFromTheDesk() {
        val d = desk(day = 5, days = 5)
        val row = CarbonPresenter.build(d).options.first()
        assertTrue(row.title.contains("Plant ${row.facility.value}"))
        assertEquals(FacilityId(row.facility.value), row.facility)
        assertTrue(AbatementKind.values().any { row.title.startsWith(it.label) })
    }
}
