package app.many.economytycoon.content.catalog

import app.many.economytycoon.content.ContentPack
import app.many.economytycoon.content.FacilityClass
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.content.SkillBand
import app.many.economytycoon.content.TechCategory
import app.many.economytycoon.content.contentPack

/**
 * Two small packs with prices chosen so every margin and payback can be worked out by hand.
 * Recipe r1 costs 30 and sells for 50 (margin 66.6%), r2 costs 45 and sells for 60 (33.3%), r3 costs 50 and sells for 44 (a loss, so the sign of a negative margin is covered).
 */
object CatalogFixtures {
    val first: ContentPack = contentPack("mini-a", 1, 0) {
        industry("milling", "Milling", IndustryFamily.TEXTILES, "Raw to flour")
        industry("baking", "Baking", IndustryFamily.TEXTILES, "Flour to bread")
        good("grain", "Grain", GoodCategory.RAW, 10.0)
        good("flour", "Flour", GoodCategory.INTERMEDIATE, 25.0)
        good("bread", "Bread", GoodCategory.CONSUMER, 60.0)
        role("worker", "Worker", SkillBand.UNSKILLED, 10.0)
        facility("mill", "Mill", FacilityClass.FACTORY, IndustryFamily.TEXTILES) {
            cost = 36_000.0; buildTicks = 10; lines = 2; storage = 1_000.0; upkeep = 10.0
        }
        facility("bakery", "Bakery", FacilityClass.FACTORY, IndustryFamily.TEXTILES) {
            cost = 18_000.0; buildTicks = 10; lines = 1; storage = 1_000.0; upkeep = 5.0
        }
        recipe("mill_grain", "Mill grain", "milling", "mill", 1) {
            input("grain", 2.0); output("flour", 2.0); labor("worker", 1)
        }
        recipe("bake_bread", "Bake bread", "baking", "bakery", 2) {
            input("flour", 1.0); output("bread", 1.0); labor("worker", 1)
        }
        tech("milling_tech", "Milling technology", TechCategory.PRODUCTION, 1, 1_000.0, 10) { unlocksRecipe("mill_grain") }
        demand("bread", 100, 200, 300, 1.0)
    }

    val second: ContentPack = contentPack("mini-b", 1, 0) {
        industry("pastry", "Pastry", IndustryFamily.TEXTILES, "Fancier bread")
        good("cake", "Cake", GoodCategory.CONSUMER, 44.0)
        machine("mixer", "Mixer", 1_000.0, tech = "pastry_tech")
        facility("patisserie", "Patisserie", FacilityClass.FACTORY, IndustryFamily.TEXTILES) {
            cost = 40_000.0; buildTicks = 10; lines = 1; storage = 1_000.0; upkeep = 0.0
        }
        recipe("bake_cake", "Bake cake", "pastry", "patisserie", 1) {
            input("flour", 1.0); input("grain", 1.5); output("cake", 1.0); labor("worker", 0 + 1); machine("mixer")
        }
        tech("pastry_tech", "Pastry technology", TechCategory.PRODUCTION, 2, 2_000.0, 20) { requires("milling_tech"); unlocksRecipe("bake_cake"); unlocksMachine("mixer") }
        demand("cake", 50, 100, 150, 1.2)
    }

    val packs: List<ContentPack> get() = listOf(first, second)
}
