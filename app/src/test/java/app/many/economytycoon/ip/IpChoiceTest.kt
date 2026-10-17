package app.many.economytycoon.ip

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IpChoiceTest {
    private val base = ChoiceInput(
        annualAdvantage = Money.units(500_000), grantChancePpm = 700_000L, grantedBreadthPpm = 550_000L, patentTermYears = 20,
        examinationDays = 240, patentLifetimeCost = Money.units(30_000), knowers = 3, exposurePpm = 0L, protectionPpm = 0L, discountRatePpm = 100_000L,
    )
    private val sp = SecretParams()
    private val op = OfficeParams()

    @Test fun aHiddenTechnologyKnownToFewFavoursSecrecy() {
        val advice = IpChoice.advise(base.copy(grantChancePpm = 500_000L, grantedBreadthPpm = 300_000L), sp, op)
        assertEquals(Protection.SECRET, advice.choice)
        assertTrue(advice.secretValue > advice.patentValue)
    }

    @Test fun aVisibleProductFavoursAPatent() {
        val advice = IpChoice.advise(base.copy(exposurePpm = 1_000_000L, knowers = 40), sp, op)
        assertEquals(Protection.PATENT, advice.choice)
        assertTrue(advice.reasons.any { it.contains("worked out") })
        assertTrue(advice.reasons.any { it.contains("Many people") })
    }

    @Test fun worthlessAdvantageIsNotWorthProtecting() {
        val advice = IpChoice.advise(base.copy(annualAdvantage = Money.ZERO), sp, op)
        assertEquals(Protection.NEITHER, advice.choice)
    }

    @Test fun patentValueFallsWithLowGrantChance() {
        val high = IpChoice.patentValue(base, sp)
        val low = IpChoice.patentValue(base.copy(grantChancePpm = 100_000L), sp)
        assertTrue(low < high)
    }

    @Test fun patentValueFallsWithFees() {
        assertEquals(Money.units(70_000), IpChoice.patentValue(base, sp) - IpChoice.patentValue(base.copy(patentLifetimeCost = Money.units(100_000)), sp))
    }

    @Test fun patentValueFallsWhenTheWaitIsLong() {
        assertTrue(IpChoice.patentValue(base.copy(examinationDays = 1_500), sp) < IpChoice.patentValue(base, sp))
    }

    @Test fun broaderClaimsAreWorthMore() {
        assertTrue(IpChoice.patentValue(base.copy(grantedBreadthPpm = 800_000L), sp) > IpChoice.patentValue(base.copy(grantedBreadthPpm = 300_000L), sp))
    }

    @Test fun secretValueFallsAsTheHazardRises() {
        val calm = IpChoice.secretValue(base, 100L, sp)
        val risky = IpChoice.secretValue(base, 2_000L, sp)
        assertTrue(risky < calm)
        assertEquals(Money.ZERO, IpChoice.secretValue(base, 1_000_000L, sp))
    }

    @Test fun protectionImprovesTheSecretCase() {
        val open = IpChoice.advise(base, sp, op)
        val guarded = IpChoice.advise(base.copy(protectionPpm = 800_000L), sp, op)
        assertTrue(guarded.secretValue > open.secretValue)
        assertTrue(guarded.secretHalfLifeDays > open.secretHalfLifeDays)
    }

    @Test fun shortHalfLivesAreFlagged() {
        val advice = IpChoice.advise(base.copy(knowers = 200, exposurePpm = 1_000_000L), sp, op)
        assertTrue(advice.reasons.any { it.contains("within a year") })
    }

    @Test fun refusalRiskIsExplained() {
        val advice = IpChoice.advise(base.copy(grantChancePpm = 200_000L), sp, op)
        assertTrue(advice.reasons.any { it.contains("refuse") })
    }
}
