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

object AgriculturePack {
    val pack: ContentPack = contentPack("agriculture", 1, 0) {
        industry("crops", "Crop farming", IndustryFamily.AGRICULTURE, "Field crops, fruit and vegetables")
        industry("livestock", "Livestock", IndustryFamily.AGRICULTURE, "Cattle, hogs, poultry and dairy")
        industry("forestry", "Forestry", IndustryFamily.AGRICULTURE, "Timber and managed woodland")
        industry("plantation", "Plantations", IndustryFamily.AGRICULTURE, "Coffee, cocoa, tea and cotton")

        good("wheat", "Wheat", GoodCategory.RAW, 20.0)
        good("corn", "Corn", GoodCategory.RAW, 16.0)
        good("rice", "Rice", GoodCategory.RAW, 30.0)
        good("soybeans", "Soybeans", GoodCategory.RAW, 28.0)
        good("sugar_beet", "Sugar beet", GoodCategory.RAW, 12.0, spoilagePpm = 3000)
        good("potatoes", "Potatoes", GoodCategory.RAW, 14.0, spoilagePpm = 4000)
        good("tomatoes", "Tomatoes", GoodCategory.RAW, 25.0, spoilagePpm = 15000)
        good("apples", "Apples", GoodCategory.RAW, 22.0, spoilagePpm = 8000)
        good("grapes", "Grapes", GoodCategory.RAW, 35.0, spoilagePpm = 12000)
        good("cotton", "Raw cotton", GoodCategory.RAW, 40.0, VolatilityClass.VOLATILE)
        good("coffee_beans", "Coffee beans", GoodCategory.RAW, 90.0, VolatilityClass.VOLATILE)
        good("cocoa_beans", "Cocoa beans", GoodCategory.RAW, 85.0, VolatilityClass.VOLATILE)
        good("tea_leaves", "Tea leaves", GoodCategory.RAW, 70.0, spoilagePpm = 2000)
        good("raw_milk", "Raw milk", GoodCategory.RAW, 18.0, spoilagePpm = 40000)
        good("eggs", "Eggs", GoodCategory.RAW, 12.0, spoilagePpm = 10000)
        good("cattle", "Cattle", GoodCategory.RAW, 400.0)
        good("hogs", "Hogs", GoodCategory.RAW, 160.0)
        good("poultry", "Poultry", GoodCategory.RAW, 8.0)
        good("wool", "Raw wool", GoodCategory.RAW, 55.0)
        good("logs", "Logs", GoodCategory.RAW, 35.0)
        good("animal_feed", "Animal feed", GoodCategory.INTERMEDIATE, 15.0, spoilagePpm = 1000)
        good("fertilizer", "Fertilizer", GoodCategory.INDUSTRIAL, 25.0)
        good("seeds", "Seed stock", GoodCategory.INDUSTRIAL, 40.0, VolatilityClass.STABLE)

        facility("grain_farm", "Grain farm", FacilityClass.FARM, IndustryFamily.AGRICULTURE) {
            cost = 250000.0; buildTicks = 45; lines = 4; storage = 40000.0; upkeep = 150.0
            energyPerLine = 2.0
            staff("farmhand", 2)
        }
        facility("vegetable_farm", "Vegetable farm", FacilityClass.FARM, IndustryFamily.AGRICULTURE) {
            cost = 220000.0; buildTicks = 40; lines = 4; storage = 25000.0; upkeep = 140.0
            energyPerLine = 2.0
            staff("farmhand", 2)
        }
        facility("orchard", "Orchard", FacilityClass.FARM, IndustryFamily.AGRICULTURE) {
            cost = 300000.0; buildTicks = 60; lines = 3; storage = 25000.0; upkeep = 160.0
            energyPerLine = 1.0
            staff("farmhand", 2)
        }
        facility("vineyard", "Vineyard", FacilityClass.FARM, IndustryFamily.AGRICULTURE) {
            cost = 340000.0; buildTicks = 60; lines = 3; storage = 20000.0; upkeep = 170.0
            energyPerLine = 1.0
            staff("farmhand", 2)
        }
        facility("plantation", "Plantation", FacilityClass.FARM, IndustryFamily.AGRICULTURE) {
            cost = 380000.0; buildTicks = 70; lines = 3; storage = 25000.0; upkeep = 190.0
            energyPerLine = 1.0
            staff("farmhand", 3)
        }
        facility("dairy_farm", "Dairy farm", FacilityClass.FARM, IndustryFamily.AGRICULTURE) {
            cost = 420000.0; buildTicks = 60; lines = 3; storage = 30000.0; upkeep = 260.0
            energyPerLine = 3.0
            staff("farmhand", 3)
        }
        facility("cattle_ranch", "Cattle ranch", FacilityClass.FARM, IndustryFamily.AGRICULTURE) {
            cost = 450000.0; buildTicks = 60; lines = 3; storage = 50000.0; upkeep = 240.0
            energyPerLine = 1.0
            staff("farmhand", 3)
        }
        facility("hog_farm", "Hog farm", FacilityClass.FARM, IndustryFamily.AGRICULTURE) {
            cost = 330000.0; buildTicks = 50; lines = 3; storage = 30000.0; upkeep = 210.0
            energyPerLine = 2.0
            staff("farmhand", 3)
        }
        facility("poultry_farm", "Poultry farm", FacilityClass.FARM, IndustryFamily.AGRICULTURE) {
            cost = 280000.0; buildTicks = 45; lines = 3; storage = 20000.0; upkeep = 200.0
            energyPerLine = 3.0
            staff("farmhand", 2)
        }
        facility("sheep_ranch", "Sheep ranch", FacilityClass.FARM, IndustryFamily.AGRICULTURE) {
            cost = 260000.0; buildTicks = 45; lines = 2; storage = 20000.0; upkeep = 150.0
            energyPerLine = 1.0
            staff("farmhand", 2)
        }
        facility("forestry_camp", "Forestry camp", FacilityClass.FARM, IndustryFamily.AGRICULTURE) {
            cost = 310000.0; buildTicks = 50; lines = 3; storage = 60000.0; upkeep = 180.0
            energyPerLine = 1.0
            staff("laborer", 2)
        }
        facility("greenhouse", "Greenhouse complex", FacilityClass.FARM, IndustryFamily.AGRICULTURE) {
            cost = 520000.0; buildTicks = 55; lines = 4; storage = 20000.0; upkeep = 300.0
            energyPerLine = 6.0
            staff("farmhand", 2)
            staff("technician", 1)
        }

        recipe("grow_wheat", "Grow wheat", "crops", "grain_farm", 60) {
            input("fertilizer", 40.0)
            input("water", 300.0)
            input("diesel", 25.0)
            input("seeds", 20.0)
            output("wheat", 725.0)
            labor("farmhand", 3)
        }
        recipe("grow_corn", "Grow corn", "crops", "grain_farm", 70) {
            input("fertilizer", 45.0)
            input("water", 350.0)
            input("diesel", 25.0)
            input("seeds", 18.0)
            output("corn", 1040.0)
            labor("farmhand", 3)
        }
        recipe("grow_rice", "Grow rice", "crops", "grain_farm", 80) {
            input("fertilizer", 35.0)
            input("water", 900.0)
            input("diesel", 20.0)
            input("seeds", 25.0)
            output("rice", 820.0)
            labor("farmhand", 4)
        }
        recipe("grow_soybeans", "Grow soybeans", "crops", "grain_farm", 65) {
            input("fertilizer", 25.0)
            input("water", 280.0)
            input("diesel", 22.0)
            input("seeds", 20.0)
            output("soybeans", 535.0)
            labor("farmhand", 3)
        }
        recipe("grow_sugar_beet", "Grow sugar beet", "crops", "grain_farm", 75) {
            input("fertilizer", 50.0)
            input("water", 400.0)
            input("diesel", 28.0)
            input("seeds", 15.0)
            output("sugar_beet", 1480.0)
            labor("farmhand", 3)
        }
        recipe("grow_potatoes", "Grow potatoes", "crops", "vegetable_farm", 55) {
            input("fertilizer", 40.0)
            input("water", 300.0)
            input("diesel", 22.0)
            input("seeds", 30.0)
            output("potatoes", 1000.0)
            labor("farmhand", 3)
        }
        recipe("grow_tomatoes", "Grow tomatoes", "crops", "vegetable_farm", 50) {
            input("fertilizer", 35.0)
            input("water", 450.0)
            input("diesel", 15.0)
            input("seeds", 20.0)
            output("tomatoes", 505.0)
            labor("farmhand", 3)
        }
        recipe("grow_greenhouse_tomatoes", "Greenhouse tomatoes", "crops", "greenhouse", 40) {
            input("fertilizer", 30.0)
            input("water", 350.0)
            input("seeds", 20.0)
            output("tomatoes", 530.0)
            labor("farmhand", 2)
            labor("technician", 1)
            energy = 120.0
        }
        recipe("harvest_apples", "Harvest apples", "crops", "orchard", 60) {
            input("fertilizer", 20.0)
            input("water", 250.0)
            input("diesel", 15.0)
            output("apples", 585.0)
            labor("farmhand", 3)
        }
        recipe("harvest_grapes", "Harvest grapes", "crops", "vineyard", 65) {
            input("fertilizer", 20.0)
            input("water", 220.0)
            input("diesel", 15.0)
            output("grapes", 395.0)
            labor("farmhand", 3)
        }
        recipe("grow_cotton", "Grow cotton", "plantation", "plantation", 80) {
            input("fertilizer", 45.0)
            input("water", 500.0)
            input("diesel", 25.0)
            input("seeds", 20.0)
            output("cotton", 605.0)
            labor("farmhand", 4)
        }
        recipe("grow_coffee", "Grow coffee", "plantation", "plantation", 90) {
            input("fertilizer", 30.0)
            input("water", 400.0)
            input("diesel", 15.0)
            output("coffee_beans", 280.0)
            labor("farmhand", 4)
        }
        recipe("grow_cocoa", "Grow cocoa", "plantation", "plantation", 90) {
            input("fertilizer", 30.0)
            input("water", 450.0)
            input("diesel", 15.0)
            output("cocoa_beans", 295.0)
            labor("farmhand", 4)
        }
        recipe("grow_tea", "Grow tea", "plantation", "plantation", 70) {
            input("fertilizer", 28.0)
            input("water", 380.0)
            input("diesel", 12.0)
            output("tea_leaves", 220.0)
            labor("farmhand", 3)
        }
        recipe("raise_cattle", "Raise cattle", "livestock", "cattle_ranch", 120) {
            input("animal_feed", 500.0)
            input("water", 700.0)
            input("diesel", 20.0)
            output("cattle", 85.0)
            labor("farmhand", 3)
        }
        recipe("raise_hogs", "Raise hogs", "livestock", "hog_farm", 90) {
            input("animal_feed", 420.0)
            input("water", 500.0)
            output("hogs", 160.0)
            labor("farmhand", 3)
            energy = 40.0
        }
        recipe("raise_poultry", "Raise poultry", "livestock", "poultry_farm", 45) {
            input("animal_feed", 260.0)
            input("water", 260.0)
            output("poultry", 1400.0)
            labor("farmhand", 2)
            energy = 60.0
        }
        recipe("collect_eggs", "Collect eggs", "livestock", "poultry_farm", 10) {
            input("animal_feed", 90.0)
            input("water", 80.0)
            output("eggs", 265.0)
            labor("farmhand", 2)
            energy = 25.0
        }
        recipe("milk_cows", "Milk cows", "livestock", "dairy_farm", 10) {
            input("animal_feed", 120.0)
            input("water", 160.0)
            output("raw_milk", 260.0)
            labor("farmhand", 3)
            energy = 45.0
        }
        recipe("shear_sheep", "Shear sheep", "livestock", "sheep_ranch", 30) {
            input("animal_feed", 90.0)
            input("water", 120.0)
            output("wool", 100.0)
            labor("farmhand", 2)
        }
        recipe("fell_timber", "Fell timber", "forestry", "forestry_camp", 30) {
            input("diesel", 30.0)
            output("logs", 225.0)
            waste("organic_waste", 8.0)
            labor("laborer", 4)
        }
        recipe("mix_feed_corn", "Mix feed from corn", "livestock", "dairy_farm", 5) {
            input("corn", 90.0)
            input("soybeans", 30.0)
            input("water", 20.0)
            output("animal_feed", 210.0)
            labor("farmhand", 1)
            energy = 18.0
        }
        recipe("breed_seeds", "Breed seed stock", "crops", "grain_farm", 20) {
            input("fertilizer", 30.0)
            input("water", 120.0)
            input("diesel", 8.0)
            output("seeds", 160.0)
            labor("farmhand", 2)
            labor("technician", 1)
            energy = 10.0
        }
    }
}
