package app.many.economytycoon.content

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionDemandTest {
    private val repo get() = ExpansionFixture.repo
    private val demands get() = ExpansionFixture.expansion.flatMap { it.demandProfiles }

    @Test fun everyFinishedGoodHasSomeDemandOrAConsumer() {
        for (k in ExpansionFixture.allExpansionGoods) {
            val id = app.many.economytycoon.core.GoodId(k)
            assertTrue(k, repo.demandProfile(id) != null || ExpansionFixture.flows.consumersOf(id).isNotEmpty())
        }
    }

    @Test fun demandProfilesNameExpansionOrBaseGoods() {
        for (d in demands) assertNotNull(d.good.key, repo.goodOrNull(d.good))
    }

    @Test fun elasticityIsInASaneRange() {
        for (d in demands) assertTrue("${d.good.key} ${d.elasticityPpm}", d.elasticityPpm in 100_000L..4_000_000L)
    }

    @Test fun incomeElasticityIsNotNegative() {
        for (d in demands) assertTrue(d.good.key, d.incomeElasticityPpm >= 0L)
    }

    @Test fun nonConsumersShareIsAProperFraction() {
        for (d in demands) assertTrue(d.good.key, d.nonConsumersPpm in 0L..1_000_000L)
    }

    @Test fun substitutesExist() {
        for (d in demands) for (s in d.substitutes) assertNotNull("${d.good.key} -> ${s.key}", repo.goodOrNull(s))
    }

    @Test fun substitutesAreNotTheGoodItself() {
        for (d in demands) assertTrue(d.good.key, d.substitutes.none { it == d.good })
    }

    @Test fun everyConsumerGoodOfTheExpansionIsBought() {
        val consumer = ExpansionFixture.expansion.flatMap { it.goods }.filter { it.category == GoodCategory.CONSUMER }
        assertTrue(consumer.size >= 60)
        for (g in consumer) assertNotNull(g.id.key, repo.demandProfile(g.id))
    }

    @Test fun seasonalityMultipliersArePositive() {
        for (d in demands) assertTrue(d.good.key, d.seasonality.values.all { it > 0L })
    }

    @Test fun noDemandIsDeclaredTwice() {
        val goods = demands.map { it.good }
        assertTrue(goods.size == goods.toSet().size)
    }
}
