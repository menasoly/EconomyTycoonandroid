package app.many.economytycoon.achievements

import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.scenarios.Medal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementCatalogTest {
    @Test fun theShippedListIsSound() {
        assertEquals(emptyList<String>(), AchievementCatalog.problems())
    }

    @Test fun idsAndTitlesAreUnique() {
        assertEquals(AchievementCatalog.all.size, AchievementCatalog.all.map { it.id }.toSet().size)
        assertEquals(AchievementCatalog.all.size, AchievementCatalog.all.map { it.title }.toSet().size)
    }

    @Test fun everyCategoryHasSomethingToEarn() {
        for (c in Category.values()) assertTrue(c.name, AchievementCatalog.inCategory(c).isNotEmpty())
    }

    @Test fun everyTierIsUsedAndPointsRiseWithIt() {
        for (t in Tier.values()) assertTrue(t.name, AchievementCatalog.all.any { it.tier == t })
        assertTrue(Tier.BRONZE.points < Tier.SILVER.points && Tier.SILVER.points < Tier.GOLD.points)
        assertEquals(AchievementCatalog.all.sumOf { it.tier.points }, AchievementCatalog.maxPoints)
    }

    @Test fun lookupFindsOrRefuses() {
        assertNotNull(AchievementCatalog.byId("first-million"))
        assertNull(AchievementCatalog.byId("nope"))
        var failed = false
        try { AchievementCatalog.require("nope") } catch (e: NoSuchElementException) { failed = true }
        assertTrue(failed)
    }

    @Test fun everyStatCanBeEarnedTowardSomething() {
        val used = AchievementCatalog.all.mapNotNull { (it.check as? Check.Total)?.stat }.toSet()
        val unused = Stat.values().toSet() - used
        // Won and lost scenario counts feed the medal checks and the profile screen instead.
        assertEquals(setOf(Stat.SCENARIOS_WON, Stat.SCENARIOS_LOST), unused)
    }

    @Test fun thresholdsWithinAKindRiseWithTier() {
        val byKind = AchievementCatalog.all.mapNotNull { a -> (a.check as? Check.Meter)?.let { it.kind to (a.tier to it.target) } }.groupBy({ it.first }, { it.second })
        for ((kind, list) in byKind) {
            val sorted = list.sortedBy { it.second }
            assertEquals(kind.name, sorted.map { it.first.points }, sorted.map { it.first.points }.sorted())
        }
        assertTrue(byKind.containsKey(GoalKind.NET_WORTH))
    }

    @Test fun aBrokenListIsReported() {
        val bad = listOf(
            Achievement("Bad Id", "x", " ", Category.WEALTH, Tier.BRONZE, Check.Medals(Medal.NONE, 0)),
            Achievement("dup", "Same", "d", Category.WEALTH, Tier.BRONZE, Check.Meter(GoalKind.CASH, 0)),
            Achievement("dup", "Same", "d", Category.WEALTH, Tier.BRONZE, Check.Total(Stat.DAYS_PLAYED, -1)),
        )
        val problems = AchievementCatalog.problemsIn(bad)
        assertTrue(problems.any { it.contains("lowercase") })
        assertTrue(problems.any { it.contains("title must be") })
        assertTrue(problems.any { it.contains("needs a description") })
        assertTrue(problems.any { it.contains("medal count out of range") })
        assertTrue(problems.any { it.contains("real medal") })
        assertTrue(problems.any { it.contains("meter target") })
        assertTrue(problems.any { it.contains("total target") })
        assertTrue(problems.any { it.startsWith("dup appears 2 times") })
        assertTrue(problems.any { it.contains("title 'Same' appears 2 times") })
    }
}
