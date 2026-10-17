package app.many.economytycoon.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionGoodDetailTest {
    private val repo get() = ExpansionFixture.repo
    private val goods get() = ExpansionFixture.expansion.flatMap { it.goods }

    @Test fun twoHundredFiftyTwoNewGoods() {
        assertEquals(252, goods.size)
    }

    @Test fun categoriesAreRawIntermediateIndustrialOrConsumer() {
        val allowed = setOf(GoodCategory.RAW, GoodCategory.INTERMEDIATE, GoodCategory.INDUSTRIAL, GoodCategory.CONSUMER)
        assertTrue(goods.all { it.category in allowed })
    }

    @Test fun rawGoodsAreTheFourDepositsAndMedicinalHerbs() {
        assertEquals(setOf("sulfur_ore", "potash_ore", "gypsum", "titanium_ore", "medicinal_herbs"), goods.filter { it.category == GoodCategory.RAW }.map { it.id.key }.toSet())
    }

    @Test fun everyGoodIsTradableAndStorable() {
        assertTrue(goods.all { it.tradable && it.storable })
    }

    @Test fun spoilageIsAFractionOfOnePercentAtMost() {
        for (g in goods) assertTrue(g.id.key, g.spoilagePpm in 0L..50_000L)
    }

    @Test fun spoilingGoodsAreMedicinesOrFood() {
        val spoil = goods.filter { it.spoilagePpm > 0L }.map { it.id.key }
        assertTrue(spoil.toString(), spoil.size <= 40)
    }

    @Test fun volatilityIsNeverSpeculative() {
        assertTrue(goods.none { it.volatility == VolatilityClass.SPECULATIVE })
    }

    @Test fun rawDepositsAreCheaperThanTheirProducts() {
        for (g in goods.filter { it.category == GoodCategory.RAW }) {
            for (r in repo.recipesConsuming(g.id)) {
                val out = r.outputs.minOf { repo.good(it.good).referencePrice.perUnit.raw }
                assertTrue("${g.id.key} -> ${r.id.key}", g.referencePrice.perUnit.raw <= out * 3L)
            }
        }
    }

    @Test fun goodNamesAreCapitalised() {
        assertTrue(goods.all { it.name.first().isUpperCase() })
    }

    @Test fun priceSpreadIsWide() {
        val prices = goods.map { it.referencePrice.perUnit.raw }
        assertTrue(prices.max() > prices.min() * 10_000L)
    }

    @Test fun everyCategoryHasGoods() {
        for (c in listOf(GoodCategory.RAW, GoodCategory.INTERMEDIATE, GoodCategory.INDUSTRIAL, GoodCategory.CONSUMER)) assertTrue(c.name, goods.any { it.category == c })
    }

    @Test fun intermediatesAreConsumedByRecipes() {
        for (g in goods.filter { it.category == GoodCategory.INTERMEDIATE }) assertTrue(g.id.key, ExpansionFixture.flows.consumersOf(g.id).isNotEmpty() || repo.demandProfile(g.id) != null)
    }

    @Test fun consumerGoodsAreNotUsedAsIngredientsMoreThanTwice() {
        for (g in goods.filter { it.category == GoodCategory.CONSUMER }) assertTrue(g.id.key, ExpansionFixture.flows.consumersOf(g.id).size <= 6)
    }

    @Test fun industrialGoodsAreEitherUsedOrBought() {
        for (g in goods.filter { it.category == GoodCategory.INDUSTRIAL }) {
            assertTrue(g.id.key, ExpansionFixture.flows.consumersOf(g.id).isNotEmpty() || repo.demandProfile(g.id) != null)
        }
    }

    @Test fun lowerCaseIdsMatchTheirNames() {
        for (g in goods) assertTrue(g.id.key, g.name.isNotBlank() && g.id.key == g.id.key.lowercase())
    }
}
