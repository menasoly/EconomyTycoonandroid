package app.many.economytycoon.content.packs

import app.many.economytycoon.content.ContentPack
import app.many.economytycoon.content.EffectKind
import app.many.economytycoon.content.FacilityClass
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.content.SkillBand
import app.many.economytycoon.content.TechCategory
import app.many.economytycoon.content.TransportMode
import app.many.economytycoon.content.VolatilityClass
import app.many.economytycoon.content.contentPack

object FoodPack {
    val pack: ContentPack = contentPack("food", 1, 0) {
        industry("milling", "Milling and baking", IndustryFamily.FOOD, "Flour, bread and pasta")
        industry("dairy", "Dairy processing", IndustryFamily.FOOD, "Milk products")
        industry("meat", "Meat processing", IndustryFamily.FOOD, "Slaughter and packing")
        industry("beverages", "Beverages", IndustryFamily.FOOD, "Soft drinks, coffee, tea, wine")
        industry("confectionery", "Confectionery", IndustryFamily.FOOD, "Sugar, chocolate and snacks")
        industry("preserved", "Preserved food", IndustryFamily.FOOD, "Canning and frozen")

        good("flour", "Flour", GoodCategory.INTERMEDIATE, 28.0, spoilagePpm = 500)
        good("sugar", "Sugar", GoodCategory.INTERMEDIATE, 34.0)
        good("vegetable_oil", "Vegetable oil", GoodCategory.INTERMEDIATE, 45.0, spoilagePpm = 500)
        good("rice_polished", "Polished rice", GoodCategory.INTERMEDIATE, 42.0)
        good("beef", "Beef", GoodCategory.INTERMEDIATE, 70.0, spoilagePpm = 30000)
        good("pork", "Pork", GoodCategory.INTERMEDIATE, 55.0, spoilagePpm = 30000)
        good("chicken_meat", "Chicken meat", GoodCategory.INTERMEDIATE, 38.0, spoilagePpm = 35000)
        good("hides", "Hides", GoodCategory.INTERMEDIATE, 20.0, spoilagePpm = 2000)
        good("cocoa_mass", "Cocoa mass", GoodCategory.INTERMEDIATE, 120.0)
        good("coffee_roasted", "Roasted coffee", GoodCategory.INTERMEDIATE, 130.0, spoilagePpm = 500)
        good("grape_must", "Grape must", GoodCategory.INTERMEDIATE, 50.0, spoilagePpm = 10000)
        good("fruit_juice_conc", "Juice concentrate", GoodCategory.INTERMEDIATE, 48.0, spoilagePpm = 3000)
        good("bread", "Bread", GoodCategory.CONSUMER, 6.0, spoilagePpm = 40000)
        good("pasta", "Pasta", GoodCategory.CONSUMER, 5.0)
        good("cheese", "Cheese", GoodCategory.CONSUMER, 24.0, spoilagePpm = 6000)
        good("butter", "Butter", GoodCategory.CONSUMER, 20.0, spoilagePpm = 5000)
        good("yogurt", "Yogurt", GoodCategory.CONSUMER, 8.0, spoilagePpm = 20000)
        good("fresh_milk", "Bottled milk", GoodCategory.CONSUMER, 6.0, spoilagePpm = 50000)
        good("sausages", "Sausages", GoodCategory.CONSUMER, 15.0, spoilagePpm = 20000)
        good("canned_vegetables", "Canned vegetables", GoodCategory.CONSUMER, 5.0, spoilagePpm = 100)
        good("canned_fruit", "Canned fruit", GoodCategory.CONSUMER, 6.0, spoilagePpm = 100)
        good("frozen_meals", "Frozen meals", GoodCategory.CONSUMER, 10.0, spoilagePpm = 500)
        good("chocolate", "Chocolate", GoodCategory.CONSUMER, 14.0, spoilagePpm = 500)
        good("candy", "Candy", GoodCategory.CONSUMER, 7.0, spoilagePpm = 200)
        good("potato_chips", "Potato chips", GoodCategory.CONSUMER, 6.0, spoilagePpm = 1500)
        good("soft_drinks", "Soft drinks", GoodCategory.CONSUMER, 3.0)
        good("bottled_juice", "Bottled juice", GoodCategory.CONSUMER, 5.0, spoilagePpm = 6000)
        good("wine", "Wine", GoodCategory.CONSUMER, 25.0)
        good("coffee_packaged", "Packaged coffee", GoodCategory.CONSUMER, 18.0, spoilagePpm = 500)
        good("tea_packaged", "Packaged tea", GoodCategory.CONSUMER, 12.0, spoilagePpm = 300)
        good("breakfast_cereal", "Breakfast cereal", GoodCategory.CONSUMER, 7.0, spoilagePpm = 200)
        good("cooking_oil_bottled", "Bottled cooking oil", GoodCategory.CONSUMER, 9.0, spoilagePpm = 200)
        good("packaged_rice", "Packaged rice", GoodCategory.CONSUMER, 8.0)

        facility("flour_mill", "Flour mill", FacilityClass.FACTORY, IndustryFamily.FOOD) {
            cost = 480000.0; buildTicks = 50; lines = 3; storage = 40000.0; upkeep = 260.0
            energyPerLine = 20.0
            staff("operator", 1)
        }
        facility("bakery", "Industrial bakery", FacilityClass.FACTORY, IndustryFamily.FOOD) {
            cost = 360000.0; buildTicks = 40; lines = 4; storage = 12000.0; upkeep = 220.0
            energyPerLine = 25.0
            staff("cook", 1)
        }
        facility("dairy_plant", "Dairy plant", FacilityClass.FACTORY, IndustryFamily.FOOD) {
            cost = 640000.0; buildTicks = 55; lines = 3; storage = 18000.0; upkeep = 330.0
            energyPerLine = 35.0
            staff("operator", 1)
        }
        facility("slaughterhouse", "Meat packing plant", FacilityClass.FACTORY, IndustryFamily.FOOD) {
            cost = 700000.0; buildTicks = 55; lines = 3; storage = 25000.0; upkeep = 360.0
            energyPerLine = 40.0
            staff("operator", 1)
        }
        facility("sugar_refinery", "Sugar refinery", FacilityClass.FACTORY, IndustryFamily.FOOD) {
            cost = 800000.0; buildTicks = 60; lines = 2; storage = 50000.0; upkeep = 380.0
            energyPerLine = 60.0
            staff("operator", 2)
        }
        facility("oil_press", "Oil press", FacilityClass.FACTORY, IndustryFamily.FOOD) {
            cost = 450000.0; buildTicks = 45; lines = 2; storage = 20000.0; upkeep = 240.0
            energyPerLine = 30.0
            staff("operator", 1)
        }
        facility("cannery", "Cannery", FacilityClass.FACTORY, IndustryFamily.FOOD) {
            cost = 520000.0; buildTicks = 45; lines = 3; storage = 30000.0; upkeep = 270.0
            energyPerLine = 28.0
            staff("operator", 1)
        }
        facility("food_factory", "Food factory", FacilityClass.FACTORY, IndustryFamily.FOOD) {
            cost = 600000.0; buildTicks = 50; lines = 4; storage = 25000.0; upkeep = 300.0
            energyPerLine = 30.0
            staff("operator", 1)
        }
        facility("confectionery_plant", "Confectionery plant", FacilityClass.FACTORY, IndustryFamily.FOOD) {
            cost = 580000.0; buildTicks = 50; lines = 3; storage = 20000.0; upkeep = 290.0
            energyPerLine = 28.0
            staff("operator", 1)
        }
        facility("bottling_plant", "Bottling plant", FacilityClass.FACTORY, IndustryFamily.FOOD) {
            cost = 620000.0; buildTicks = 50; lines = 4; storage = 30000.0; upkeep = 310.0
            energyPerLine = 32.0
            staff("operator", 1)
            staff("technician", 1)
        }
        facility("winery", "Winery", FacilityClass.FACTORY, IndustryFamily.FOOD) {
            cost = 540000.0; buildTicks = 55; lines = 2; storage = 30000.0; upkeep = 280.0
            energyPerLine = 12.0
            staff("cook", 1)
        }
        facility("roastery", "Coffee roastery", FacilityClass.FACTORY, IndustryFamily.FOOD) {
            cost = 360000.0; buildTicks = 40; lines = 2; storage = 15000.0; upkeep = 210.0
            energyPerLine = 14.0
            staff("operator", 1)
        }

        recipe("mill_flour", "Mill flour", "milling", "flour_mill", 3) {
            input("wheat", 120.0)
            input("packaging", 4.0)
            output("flour", 130.0)
            labor("operator", 2)
            energy = 18.0
        }
        recipe("press_oil_soy", "Press soybean oil", "milling", "oil_press", 4) {
            input("soybeans", 160.0)
            input("packaging", 6.0)
            output("vegetable_oil", 140.0)
            labor("operator", 2)
            energy = 22.0
        }
        recipe("polish_rice", "Polish rice", "milling", "flour_mill", 3) {
            input("rice", 110.0)
            input("packaging", 3.0)
            output("rice_polished", 110.0)
            labor("operator", 2)
            energy = 12.0
        }
        recipe("bake_bread", "Bake bread", "milling", "bakery", 2) {
            input("flour", 60.0)
            input("water", 30.0)
            input("packaging", 6.0)
            input("sugar", 3.0)
            output("bread", 500.0)
            labor("cook", 3)
            energy = 30.0
        }
        recipe("make_pasta", "Make pasta", "milling", "food_factory", 2) {
            input("flour", 70.0)
            input("water", 25.0)
            input("packaging", 8.0)
            output("pasta", 630.0)
            labor("operator", 2)
            energy = 26.0
        }
        recipe("make_cereal", "Make breakfast cereal", "milling", "food_factory", 3) {
            input("corn", 90.0)
            input("sugar", 10.0)
            input("packaging", 12.0)
            output("breakfast_cereal", 450.0)
            labor("operator", 2)
            energy = 28.0
        }
        recipe("package_rice", "Package rice", "milling", "food_factory", 1) {
            input("rice_polished", 100.0)
            input("packaging", 12.0)
            output("packaged_rice", 665.0)
            labor("operator", 1)
            labor("laborer", 1)
            energy = 6.0
        }
        recipe("bottle_cooking_oil", "Bottle cooking oil", "milling", "bottling_plant", 1) {
            input("vegetable_oil", 80.0)
            input("packaging", 10.0)
            output("cooking_oil_bottled", 515.0)
            labor("operator", 1)
            labor("laborer", 1)
            energy = 8.0
        }
        recipe("process_milk", "Bottle fresh milk", "dairy", "dairy_plant", 1) {
            input("raw_milk", 400.0)
            input("packaging", 20.0)
            output("fresh_milk", 1525.0)
            labor("operator", 2)
            energy = 22.0
        }
        recipe("make_cheese", "Make cheese", "dairy", "dairy_plant", 6) {
            input("raw_milk", 500.0)
            input("packaging", 12.0)
            output("cheese", 525.0)
            labor("operator", 2)
            energy = 30.0
        }
        recipe("churn_butter", "Churn butter", "dairy", "dairy_plant", 2) {
            input("raw_milk", 420.0)
            input("packaging", 10.0)
            output("butter", 490.0)
            labor("operator", 2)
            energy = 22.0
        }
        recipe("culture_yogurt", "Culture yogurt", "dairy", "dairy_plant", 3) {
            input("raw_milk", 300.0)
            input("sugar", 12.0)
            input("packaging", 25.0)
            output("yogurt", 1000.0)
            labor("operator", 2)
            energy = 24.0
        }
        recipe("slaughter_cattle", "Process cattle", "meat", "slaughterhouse", 3) {
            input("cattle", 20.0)
            input("packaging", 25.0)
            input("water", 200.0)
            output("beef", 155.0)
            output("hides", 25.0)
            waste("organic_waste", 90.0)
            labor("operator", 4)
            energy = 45.0
        }
        recipe("slaughter_hogs", "Process hogs", "meat", "slaughterhouse", 3) {
            input("hogs", 30.0)
            input("packaging", 25.0)
            input("water", 200.0)
            output("pork", 140.0)
            waste("organic_waste", 80.0)
            labor("operator", 4)
            energy = 45.0
        }
        recipe("process_poultry", "Process poultry", "meat", "slaughterhouse", 2) {
            input("poultry", 400.0)
            input("packaging", 30.0)
            input("water", 150.0)
            output("chicken_meat", 130.0)
            waste("organic_waste", 60.0)
            labor("operator", 3)
            energy = 35.0
        }
        recipe("make_sausages", "Make sausages", "meat", "food_factory", 2) {
            input("pork", 70.0)
            input("beef", 20.0)
            input("packaging", 18.0)
            input("water", 20.0)
            output("sausages", 490.0)
            labor("operator", 3)
            energy = 26.0
        }
        recipe("refine_sugar", "Refine sugar", "confectionery", "sugar_refinery", 4) {
            input("sugar_beet", 600.0)
            input("water", 400.0)
            input("packaging", 8.0)
            output("sugar", 325.0)
            waste("organic_waste", 40.0)
            labor("operator", 3)
            energy = 85.0
        }
        recipe("grind_cocoa", "Grind cocoa", "confectionery", "confectionery_plant", 3) {
            input("cocoa_beans", 100.0)
            input("packaging", 4.0)
            output("cocoa_mass", 90.0)
            labor("operator", 2)
            energy = 35.0
        }
        recipe("make_chocolate", "Make chocolate", "confectionery", "confectionery_plant", 3) {
            input("cocoa_mass", 40.0)
            input("sugar", 50.0)
            input("raw_milk", 40.0)
            input("packaging", 20.0)
            output("chocolate", 725.0)
            labor("operator", 3)
            energy = 40.0
        }
        recipe("make_candy", "Make candy", "confectionery", "confectionery_plant", 2) {
            input("sugar", 90.0)
            input("packaging", 18.0)
            output("candy", 630.0)
            labor("operator", 2)
            energy = 22.0
        }
        recipe("fry_chips", "Fry potato chips", "confectionery", "food_factory", 2) {
            input("potatoes", 220.0)
            input("vegetable_oil", 20.0)
            input("packaging", 24.0)
            output("potato_chips", 990.0)
            labor("operator", 3)
            energy = 40.0
        }
        recipe("can_vegetables", "Can vegetables", "preserved", "cannery", 2) {
            input("tomatoes", 220.0)
            input("packaging", 40.0)
            input("water", 60.0)
            output("canned_vegetables", 1570.0)
            labor("operator", 3)
            energy = 30.0
        }
        recipe("can_fruit", "Can fruit", "preserved", "cannery", 2) {
            input("apples", 200.0)
            input("sugar", 20.0)
            input("packaging", 40.0)
            output("canned_fruit", 1205.0)
            labor("operator", 3)
            energy = 30.0
        }
        recipe("make_frozen_meals", "Make frozen meals", "preserved", "food_factory", 3) {
            input("chicken_meat", 40.0)
            input("rice_polished", 40.0)
            input("tomatoes", 40.0)
            input("packaging", 60.0)
            output("frozen_meals", 710.0)
            labor("operator", 3)
            labor("cook", 1)
            energy = 55.0
        }
        recipe("press_juice", "Press fruit juice", "beverages", "bottling_plant", 2) {
            input("apples", 260.0)
            input("water", 40.0)
            output("fruit_juice_conc", 150.0)
            labor("operator", 2)
            energy = 26.0
        }
        recipe("bottle_juice", "Bottle juice", "beverages", "bottling_plant", 1) {
            input("fruit_juice_conc", 60.0)
            input("water", 300.0)
            input("packaging", 60.0)
            output("bottled_juice", 935.0)
            labor("operator", 2)
            energy = 24.0
        }
        recipe("brew_soft_drinks", "Produce soft drinks", "beverages", "bottling_plant", 1) {
            input("sugar", 60.0)
            input("water", 500.0)
            input("packaging", 80.0)
            output("soft_drinks", 1375.0)
            labor("operator", 2)
            energy = 26.0
        }
        recipe("press_grapes", "Press grapes", "beverages", "winery", 4) {
            input("grapes", 260.0)
            input("water", 20.0)
            output("grape_must", 220.0)
            labor("cook", 2)
            energy = 8.0
        }
        recipe("ferment_wine", "Ferment wine", "beverages", "winery", 30) {
            input("grape_must", 60.0)
            input("packaging", 25.0)
            output("wine", 355.0)
            labor("cook", 2)
            energy = 15.0
        }
        recipe("roast_coffee", "Roast coffee", "beverages", "roastery", 2) {
            input("coffee_beans", 150.0)
            input("packaging", 6.0)
            output("coffee_roasted", 125.0)
            waste("organic_waste", 10.0)
            labor("operator", 2)
            energy = 26.0
        }
        recipe("package_coffee", "Package coffee", "beverages", "roastery", 1) {
            input("coffee_roasted", 50.0)
            input("packaging", 30.0)
            output("coffee_packaged", 455.0)
            labor("operator", 1)
            labor("laborer", 1)
            energy = 8.0
        }
        recipe("package_tea", "Package tea", "beverages", "roastery", 2) {
            input("tea_leaves", 120.0)
            input("packaging", 40.0)
            output("tea_packaged", 900.0)
            labor("operator", 2)
            energy = 10.0
        }

        demand("bread", 380L, 320L, 260L, 0.35, incomeElasticity = 0.3, substitutes = listOf("pasta", "packaged_rice"))
        demand("pasta", 150L, 140L, 110L, 0.5, incomeElasticity = 0.4, substitutes = listOf("bread", "packaged_rice"))
        demand("packaged_rice", 160L, 120L, 90L, 0.5, incomeElasticity = 0.3, substitutes = listOf("pasta", "bread"))
        demand("fresh_milk", 260L, 240L, 220L, 0.45, incomeElasticity = 0.4, substitutes = listOf("yogurt"), seasons = mapOf(app.many.economytycoon.core.Season.SUMMER to 1.1))
        demand("cheese", 55L, 75L, 95L, 0.8, incomeElasticity = 0.9, substitutes = listOf("butter"))
        demand("butter", 40L, 48L, 55L, 0.7, incomeElasticity = 0.6, substitutes = listOf("cooking_oil_bottled"))
        demand("yogurt", 70L, 95L, 110L, 0.9, substitutes = listOf("fresh_milk"), seasons = mapOf(app.many.economytycoon.core.Season.SUMMER to 1.2, app.many.economytycoon.core.Season.WINTER to 0.9))
        demand("sausages", 60L, 70L, 75L, 0.9, incomeElasticity = 0.7, substitutes = listOf("frozen_meals"))
        demand("canned_vegetables", 80L, 70L, 55L, 0.6, incomeElasticity = 0.3)
        demand("canned_fruit", 50L, 55L, 55L, 0.8, incomeElasticity = 0.6)
        demand("frozen_meals", 35L, 70L, 95L, 1.1, incomeElasticity = 1.1, substitutes = listOf("canned_vegetables"))
        demand("chocolate", 30L, 55L, 75L, 1.2, incomeElasticity = 1.1, substitutes = listOf("candy"), seasons = mapOf(app.many.economytycoon.core.Season.WINTER to 1.2))
        demand("candy", 55L, 60L, 50L, 1.2, substitutes = listOf("chocolate"))
        demand("potato_chips", 45L, 60L, 55L, 1.2)
        demand("soft_drinks", 220L, 260L, 240L, 1.0, incomeElasticity = 0.6, substitutes = listOf("bottled_juice"), seasons = mapOf(app.many.economytycoon.core.Season.SUMMER to 1.35, app.many.economytycoon.core.Season.WINTER to 0.8))
        demand("bottled_juice", 70L, 120L, 150L, 1.1, incomeElasticity = 1.0, substitutes = listOf("soft_drinks"), seasons = mapOf(app.many.economytycoon.core.Season.SUMMER to 1.25))
        demand("wine", 8L, 30L, 70L, 1.3, incomeElasticity = 1.5, nonConsumers = 0.35)
        demand("coffee_packaged", 35L, 55L, 65L, 0.7, substitutes = listOf("tea_packaged"))
        demand("tea_packaged", 25L, 30L, 32L, 0.8, incomeElasticity = 0.6, substitutes = listOf("coffee_packaged"))
        demand("breakfast_cereal", 45L, 55L, 50L, 1.0, incomeElasticity = 0.7, substitutes = listOf("bread"))
        demand("cooking_oil_bottled", 45L, 50L, 50L, 0.5, incomeElasticity = 0.4, substitutes = listOf("butter"))
    }
}
