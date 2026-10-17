package app.many.economytycoon.ip

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretRulesTest {
    private val p = SecretParams()

    @Test fun protectionSaturates() {
        assertEquals(0L, SecretRules.protectionFor(p, Money.ZERO))
        assertEquals(450_000L, SecretRules.protectionFor(p, Money.units(20_000)))
        assertTrue(SecretRules.protectionFor(p, Money.units(100_000_000)) < p.maxProtectionPpm)
        assertTrue(SecretRules.protectionFor(p, Money.units(100_000_000)) > 899_000L)
    }

    @Test fun leaksGrowWithKnowersAndShrinkWithProtection() {
        assertEquals(60L, SecretRules.dailyLeakPpm(p, 0, 0))
        assertEquals(60L + 25L * 10, SecretRules.dailyLeakPpm(p, 10, 0))
        assertEquals(155L, SecretRules.dailyLeakPpm(p, 10, 500_000))
        assertEquals(0L, SecretRules.dailyLeakPpm(p, 10, 1_000_000))
    }

    @Test fun reverseEngineeringNeedsAVisibleProduct() {
        assertEquals(0L, SecretRules.dailyReverseEngineerPpm(p, 0, 0))
        assertEquals(300L, SecretRules.dailyReverseEngineerPpm(p, 1_000_000, 0))
        assertEquals(150L, SecretRules.dailyReverseEngineerPpm(p, 1_000_000, 1_000_000))
    }

    @Test fun hazardAddsBothAndIsCapped() {
        assertEquals(360L, SecretRules.dailyHazardPpm(p, 0, 1_000_000, 0))
        assertEquals(1_000_000L, SecretRules.dailyHazardPpm(SecretParams(baseLeakPpmPerDay = 5_000_000L), 0, 0, 0))
    }

    @Test fun survivalFallsGeometrically() {
        assertEquals(1_000_000L, SecretRules.survivalPpm(5_000, 0))
        assertEquals(995_000L, SecretRules.survivalPpm(5_000, 1))
        assertTrue(Math.abs(SecretRules.survivalPpm(5_000, 2) - 990_025L) <= 2)
        assertTrue(SecretRules.survivalPpm(5_000, 360) in 160_000L..170_000L)
        assertEquals(0L, SecretRules.survivalPpm(1_000_000, 3))
    }

    @Test fun lifeIsTheInverseOfTheHazard() {
        assertEquals(1_000L, SecretRules.expectedLifeDays(1_000))
        assertTrue(SecretRules.expectedLifeDays(0) > 1_000_000_000L)
    }

    @Test fun espionageIsHarderAgainstGoodProtection() {
        val open = SecretRules.espionageSuccessPpm(p, 0, 1_000_000)
        val guarded = SecretRules.espionageSuccessPpm(p, 800_000, 1_000_000)
        assertEquals(250_000L, open)
        assertEquals(50_000L, guarded)
        assertEquals(500_000L, SecretRules.espionageSuccessPpm(p, 0, 2_000_000))
        assertEquals(950_000L, SecretRules.espionageSuccessPpm(p, 0, 100_000_000))
    }

    @Test fun failedAttemptsAreEasierToSpot() {
        assertTrue(SecretRules.detectionPpm(p, 0, false) > SecretRules.detectionPpm(p, 0, true))
        assertTrue(SecretRules.detectionPpm(p, 900_000, true) > SecretRules.detectionPpm(p, 0, true))
        assertEquals(950_000L, SecretRules.detectionPpm(p, 1_000_000, false))
    }
}
