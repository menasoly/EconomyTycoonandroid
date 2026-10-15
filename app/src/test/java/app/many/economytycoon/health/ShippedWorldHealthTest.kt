package app.many.economytycoon.health

import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the shipped content from breaking the economy in new ways. Rival companies running dry and many
 * markets sitting low are known and measured; anything else appearing here is a regression.
 */
class ShippedWorldHealthTest {
    private val known = setOf("ai.broke", "ai.strained", "ai.idle", "price.floor")
    private val results = BalanceRun(SimTestSupport.content, rivals = 6).runAll(listOf(21L, 22L), 360L, 120L)

    @Test fun noWorldBreaksALedgerInvariant() {
        for (r in results) assertEquals("seed ${r.seed}", emptyList<String>(), r.problems)
    }

    @Test fun moneyIsOnlyCreatedByMinting() {
        for (r in results) {
            val supply = r.samples.map { it.moneySupply }
            assertTrue("seed ${r.seed}: $supply", supply.zipWithNext().all { (a, b) -> b >= a })
            assertTrue("seed ${r.seed}: money grew faster than a year's allowance", !r.has("money.growth"))
        }
    }

    @Test fun householdsAlwaysHaveSomethingToSpend() {
        for (r in results) assertTrue("seed ${r.seed}", r.samples.all { it.householdCash > 0L })
    }

    @Test fun noNewKindOfProblemAppears() {
        for (r in results) {
            val unknown = r.issues.filter { it.code !in known }
            assertEquals("seed ${r.seed}: ${BalanceFormat.result(r)}", emptyList<HealthIssue>(), unknown)
        }
    }

    @Test fun noMarketRunsAwayUpward() {
        for (r in results) assertTrue("seed ${r.seed}", r.last.dearShare < 100_000L)
    }
}
